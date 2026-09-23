package app.ninho.android

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StudyBackupJob : JobService() {
    private var active: AtomicBoolean? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val token = AtomicBoolean(true)
        active = token
        Thread({
            try { if (token.get()) StudyStore(applicationContext).load() }
            catch (_: Exception) { /* Canonical data is untouched on read failure; a later save/visit retries. */ }
            finally { if (token.getAndSet(false)) jobFinished(params, false) }
        }, "Ninho daily backup").start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { active?.set(false); return true }

    companion object {
        private const val JOB_ID = 731
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, StudyBackupJob::class.java))
                .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(6))
                .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).setPersisted(true).build())
        }
    }
}
