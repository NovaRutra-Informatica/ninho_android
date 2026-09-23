package app.ninho.android

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.util.UUID

class WidgetSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            var checked = 0
            runOnMainSync {
                val now = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()
                val zone = ZoneId.of("UTC")
                val data = StudyData(subjects = listOf(Subject("s", "Teste")),
                    sessions = listOf(StudySession("one", "s", 600, now, 2, "")), profile = StudentProfile(completedAt = now, dailyMinutes = 30))
                val ready = WidgetSnapshot.from(data, now, zone)
                for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                    val config = Configuration(targetContext.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night }
                    val themed = targetContext.createConfigurationContext(config)
                    for (kind in NinhoWidgetKind.entries) for (snapshot in listOf(ready, ready.copy(ready = false), null)) {
                        val remote = NinhoWidgets.views(themed, kind, snapshot, now, zone)
                        val host = FrameLayout(themed)
                        val view = remote.apply(themed, host)
                        host.addView(view)
                        val pixels = (180 * themed.resources.displayMetrics.density).toInt()
                        host.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY))
                        host.layout(0, 0, pixels, pixels)
                        check(view.findViewById<TextView>(R.id.widget_value).text.isNotBlank())
                        check(view.findViewById<TextView>(R.id.widget_detail).text.isNotBlank())
                        check(view.findViewById<View>(R.id.widget_root).contentDescription.isNotBlank())
                        if (snapshot?.ready == true && kind == NinhoWidgetKind.STREAK) check(view.findViewById<TextView>(R.id.widget_value).text.toString() == "1 dia")
                        checked++
                    }
                }
            }
            val backupChecks = backupSmoke()
            report.putString("stream", "\nPASS: $checked RemoteViews reais + $backupChecks cenários de backup/AtomicFile em cache isolado por UUID, sem alterar os dados canônicos do usuário.\n")
            report.putInt("viewsPassed", checked)
            report.putInt("backupsPassed", backupChecks)
            finish(Activity.RESULT_OK, report)
        } catch (failure: Throwable) {
            report.putString("stream", "\nFAIL: ${failure.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun backupSmoke(): Int {
        val root = File(targetContext.cacheDir, "instrumentation-backup-smoke-${UUID.randomUUID()}").apply { check(mkdirs()) }
        check(root.canonicalPath.startsWith(targetContext.cacheDir.canonicalPath + File.separator))
        fun isolated(name: String) = object : ContextWrapper(targetContext) {
            override fun getFilesDir() = File(root, "$name/files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "$name/no-backup").apply { mkdirs() }
        }
        try {
            val now = System.currentTimeMillis()
            val sample = StudyData(subjects = listOf(Subject("backup-test", "Estudo de teste")),
                profile = StudentProfile(name = "Teste", goal = "Verificar backup", completedAt = now))
            val original = isolated("original")
            val source = StudyStore(original)
            source.save(sample); source.refreshBackupFromDisk()
            val latest = File(original.filesDir, "compact-backup/latest.ninho-backup.gz")
            check(StudyBackupCodec.decode(latest.readBytes()).data == sample)
            val fresh = isolated("fresh-install")
            val delivered = File(fresh.filesDir, "compact-backup/latest.ninho-backup.gz")
            delivered.parentFile!!.mkdirs(); latest.copyTo(delivered)
            check(StudyStore(fresh).load() == sample)
            check(File(fresh.filesDir, "study-v1.json").isFile)
            val current = sample.copy(profile = sample.profile.copy(goal = "Meu objetivo atual"))
            StudyStore(fresh).save(current)
            check(StudyStore(fresh).load() == current)
            val canonical = File(fresh.filesDir, "study-v1.json")
            canonical.writeText("corrupt-canonical-preserve")
            check(runCatching { StudyStore(fresh).load() }.isFailure)
            check(canonical.readText() == "corrupt-canonical-preserve")
            val invalid = isolated("invalid-install")
            val damaged = File(invalid.filesDir, "compact-backup/latest.ninho-backup.gz")
            damaged.parentFile!!.mkdirs(); damaged.writeText("preserve-corrupt-backup")
            check(StudyStore(invalid).load() == StudyData())
            check(!File(invalid.filesDir, "study-v1.json").exists())
            check(damaged.readText() == "preserve-corrupt-backup")
            return 5
        } finally {
            // Instrumentation runs with the target UID. Only this UUID cache tree may be deleted.
            check(root.canonicalPath.startsWith(targetContext.cacheDir.canonicalPath + File.separator))
            root.deleteRecursively()
        }
    }
}
