package app.ninho.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.edit

object TimerReminderPolicy {
    fun key(timer: FocusTimer) = "${timer.startedWall}:${timer.startedMonotonic}:${timer.elapsedMillis}:${timer.targetSeconds}:${timer.subjectId}"
    fun remaining(timer: FocusTimer, mono: Long, wall: Long, boot: Int): Long? =
        if (!timer.running || timer.stopwatch || timer.subjectId.isBlank()) null
        else (timer.targetSeconds * 1000 - timer.elapsed(mono, wall, boot)).coerceAtLeast(0)
    fun canDeliver(timer: FocusTimer, requestedKey: String, lastDelivered: String, enabled: Boolean, mono: Long, wall: Long, boot: Int) =
        enabled && requestedKey == key(timer) && requestedKey != lastDelivered && remaining(timer, mono, wall, boot) == 0L
}

class TimerReminder(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val delivery = context.getSharedPreferences("timer-notices", Context.MODE_PRIVATE)
    private fun intent() = Intent(context, TimerReminderReceiver::class.java).setAction(ACTION)
    private fun pending(key: String = "") = PendingIntent.getBroadcast(context, 410, intent().putExtra("timer_key", key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun sync() = synchronized(alarmLock) {
        // Reread under the alarm lock so delayed callbacks cannot replace a newer timer.
        val data = StudyStore(context).load()
        alarms.cancel(pending())
        if (!data.settings.timerReminder || !canNotify()) { notifications.cancel(410); return@synchronized }
        if (delivery.getString("last-delivered", "") == TimerReminderPolicy.key(data.timer)) return@synchronized
        notifications.cancel(410)
        val mono = SystemClock.elapsedRealtime()
        val remaining = TimerReminderPolicy.remaining(data.timer, mono, System.currentTimeMillis(), bootCount(context)) ?: return@synchronized
        // An inexact alarm needs no special exact-alarm permission. Android may defer it in Doze.
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, mono + remaining.coerceAtLeast(1000), pending(TimerReminderPolicy.key(data.timer)))
    }
    fun canNotify() = notifications.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    @android.annotation.SuppressLint("MissingPermission") // Checked immediately before notify; a concurrent revocation is caught.
    fun deliver(key: String) = synchronized(alarmLock) {
        val data = StudyStore(context).load()
        if (!canNotify() || !TimerReminderPolicy.canDeliver(data.timer, key, delivery.getString("last-delivered", "").orEmpty(), data.settings.timerReminder, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount(context))) return@synchronized
        val channel = NotificationChannel(CHANNEL, "Foco concluído — silencioso", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false); description = "Avisos silenciosos do temporizador. Não interrompem sua música." }
        notifications.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(context, 411, Intent(context, MainActivity::class.java).putExtra("open_focus", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_timer_notification).setContentTitle("Seu tempo de foco terminou")
            .setContentText("Volte ao Ninho para registrar a sessão e fazer uma pausa.").setContentIntent(open)
            .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try { notifications.notify(410, notification); delivery.edit { putString("last-delivered", key) } } catch (_: SecurityException) { /* Permission can be revoked after the check. */ }
    }
    companion object {
        private val alarmLock = Any()
        const val ACTION = "app.ninho.android.FOCUS_FINISHED"
        private const val CHANNEL = "focus-silent-v1"
        fun bootCount(context: Context) = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    }
}

class TimerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread({
            try {
                // Read only: writing from this separate receiver could race the foreground store.
                val reminder = TimerReminder(context)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) reminder.sync()
                else if (intent.action == TimerReminder.ACTION) reminder.deliver(intent.getStringExtra("timer_key").orEmpty())
            } catch (_: Exception) { /* Leave saved data intact; the next foreground sync can retry. */ }
            finally { result.finish() }
        }, "Ninho timer notice").start()
    }
}
