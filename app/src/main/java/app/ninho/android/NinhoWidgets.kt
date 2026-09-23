package app.ninho.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class NinhoWidgetKind(val title: String, val description: String, val provider: Class<out AppWidgetProvider>, val layout: Int, val route: String) {
    STREAK("Minha sequência", "Seu foguinho e os dias que você cultivou.", StreakWidget::class.java, R.layout.widget_streak, "today"),
    TODAY("Meu dia de estudo", "Minutos da meta e matérias para revisar.", TodayWidget::class.java, R.layout.widget_today, "reviews"),
    FOCUS("Hora de focar", "Seu bloco de estudo a um toque de distância.", FocusWidget::class.java, R.layout.widget_focus, "focus"),
}

object NinhoWidgets {
    private val lock = Any()
    private const val MIDNIGHT = "app.ninho.android.WIDGET_MIDNIGHT"
    private const val MAX_SNAPSHOT_BYTES = 256 * 1024
    private const val MAX_DATES = 10000
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "widget-summary-v1.json"))
    private fun save(context: Context, value: WidgetSnapshot) {
        val target = file(context)
        val stream = target.startWrite()
        try {
            val json = JSONObject().put("version", 1).put("zone", value.zone).put("at", value.capturedAt).put("ready", value.ready)
                .put("days", JSONArray(value.studyDays.map { it.toString() }))
                .put("seconds", JSONObject(value.secondsByDay.mapKeys { it.key.toString() }))
                .put("goal", value.dailyMinutes).put("due", JSONArray(value.dueAt)).put("focus", value.focusMinutes)
                .put("running", value.focusRunning).put("stopwatch", value.stopwatch)
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_SNAPSHOT_BYTES)
            stream.write(bytes); target.finishWrite(stream)
        } catch (error: Exception) { target.failWrite(stream); throw error }
    }
    private fun load(context: Context): WidgetSnapshot? = try {
        val bytes = file(context).openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= MAX_SNAPSHOT_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_SNAPSHOT_BYTES + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= MAX_SNAPSHOT_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val days = json.getJSONArray("days"); val due = json.getJSONArray("due"); val seconds = json.getJSONObject("seconds")
        require(days.length() <= MAX_DATES && due.length() <= MAX_DATES && seconds.length() <= MAX_DATES)
        val zone = ZoneId.of(json.getString("zone"))
        val at = json.getLong("at").also { require(it in 1..253402300799999L) }
        val dateLimit = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        fun date(value: String) = LocalDate.parse(value).also { require(it >= LocalDate.of(1970, 1, 1) && it <= dateLimit) }
        WidgetSnapshot(zone.id, at, json.getBoolean("ready"),
            (0 until days.length()).map { date(days.getString(it)) }.toSet(),
            seconds.keys().asSequence().associate { date(it) to seconds.getLong(it).also { amount -> require(amount in 0..86_400L) } },
            json.getInt("goal").also { require(it in 1..1440) },
            (0 until due.length()).map { due.getLong(it).also { value -> require(value in 1..253402300799999L) } },
            json.getInt("focus").also { require(it in 1..240) }, json.getBoolean("running"), json.getBoolean("stopwatch"))
    } catch (_: Exception) { null }

    /** Call on IO after a relevant durable save; reread inside the lock to avoid stale callbacks. */
    fun refresh(context: Context, rebuild: Boolean = false) = synchronized(lock) {
        val manager = AppWidgetManager.getInstance(context)
        val widgets = NinhoWidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(context, it.provider)) }
        val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault()
        val cached = if (rebuild) null else load(context)
        val snapshot = try {
            if (cached != null && cached.zone == zone.id && cached.capturedAt <= now) cached
            else WidgetSnapshot.from(StudyStore(context).load(), now, zone).also { save(context, it) }
        } catch (_: Exception) { null }
        widgets.forEach { (kind, ids) -> if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, kind, snapshot, now, zone)) }
        scheduleMidnight(context, widgets.values.any { it.isNotEmpty() })
    }

    internal fun views(context: Context, kind: NinhoWidgetKind, data: WidgetSnapshot?, now: Long, zone: ZoneId): RemoteViews {
        val intent = Intent(context, MainActivity::class.java).putExtra("open_route", kind.route)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 700 + kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteViews(context.packageName, kind.layout).apply {
            setOnClickPendingIntent(R.id.widget_root, open)
            val available = data?.ready == true
            if (!available) {
                setTextViewText(R.id.widget_value, if (data == null) "Vamos conferir?" else "Seu ninho espera")
                setTextViewText(R.id.widget_detail, if (data == null) "Abra o Ninho para atualizar seus registros." else "Abra o Ninho e crie seu perfil local.")
                setContentDescription(R.id.widget_root, "${kind.title}. Abrir Ninho")
            } else {
                val snapshot = requireNotNull(data)
                val streak = snapshot.streak(now, zone)
                val minutes = snapshot.secondsToday(now, zone) / 60
                val (value, detail) = when (kind) {
                    NinhoWidgetKind.STREAK -> "${streak.days} ${if (streak.days == 1) "dia" else "dias"}" to
                        if (streak.studiedToday) "Seu estudo de hoje já conta." else if (streak.days > 0) "Um passo hoje mantém o foguinho." else "Todo começo merece um foguinho."
                    NinhoWidgetKind.TODAY -> "$minutes / ${snapshot.dailyMinutes} min" to "${snapshot.reviewsDue(now)} matéria(s) para retomar"
                    NinhoWidgetKind.FOCUS -> (if (snapshot.stopwatch) "Cronômetro" else "${snapshot.focusMinutes} min") to
                        if (snapshot.focusRunning) "Sessão aberta · toque para acompanhar" else "Toque para preparar seu foco"
                }
                setTextViewText(R.id.widget_value, value)
                setTextViewText(R.id.widget_detail, detail)
                setContentDescription(R.id.widget_root, "${kind.title}. $value. $detail")
            }
        }
    }
    private fun scheduleMidnight(context: Context, anyWidgets: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 710, Intent(context, WidgetClockReceiver::class.java).setAction(MIDNIGHT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if (!anyWidgets) return
        val tomorrow = ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.set(AlarmManager.RTC, tomorrow, pending)
    }
    fun requestPin(context: Context, kind: NinhoWidgetKind): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, kind.provider), null, null)
    }
    internal fun handlesClock(action: String?) = action in setOf(MIDNIGHT, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED)
}

abstract class NinhoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_ENABLED,
                AppWidgetManager.ACTION_APPWIDGET_DISABLED, AppWidgetManager.ACTION_APPWIDGET_DELETED,
                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, AppWidgetManager.ACTION_APPWIDGET_RESTORED)) return
        val pending = goAsync()
        Thread({ try { NinhoWidgets.refresh(context) } catch (_: Exception) { /* Retry on the next OS update or foreground save. */ }
            finally { pending.finish() } }, "Ninho widget snapshot").start()
    }
}
class StreakWidget : NinhoWidgetProvider()
class TodayWidget : NinhoWidgetProvider()
class FocusWidget : NinhoWidgetProvider()

class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!NinhoWidgets.handlesClock(intent.action)) return
        val pending = goAsync()
        Thread({ try { NinhoWidgets.refresh(context, rebuild = intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) }
            catch (_: Exception) { /* Widgets must never crash the app on a launcher/alarm failure. */ }
            finally { pending.finish() } }, "Ninho widget date").start()
    }
}
