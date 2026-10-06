package app.ninho.android

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

class StudyDevicePerformance(private val context: Context) {
    private var sink: Any? = null
    private val report = StringBuilder()

    fun run(): String {
        val root = File(context.cacheDir, "performance-${UUID.randomUUID()}").apply { check(mkdirs()) }
        check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        report.appendLine("ART performance: API ${Build.VERSION.SDK_INT}, ${Build.SUPPORTED_ABIS.first()}, ${Build.MODEL}; 5 warmups/15 samples. CPU/storage workload, not UI frame rate.")
        try {
            for (size in listOf(71, 1000, 5000)) {
                val data = fixture(size)
                val index = StudyIndex.build(data)
                check(oldSuggestions(data) == StudyCoach.suggestions(data, NOW, index))
                report.appendLine("DATA questions=$size subjects=${data.subjects.size} sessions=${data.sessions.size} attempts=${data.attempts.size}")
                measure("recommendations-before", size) { oldSuggestions(data) }
                measure("recommendations-cold-index", size) { StudyCoach.suggestions(data, NOW) }
                measure("recommendations-cached-index", size) { StudyCoach.suggestions(data, NOW, index) }
                measure("review-all-rows-before", size) {
                    data.questions.map { question ->
                        val attempts = data.attempts.filter { it.questionId == question.id }
                        Triple(data.subjects.find { it.id == question.subjectId }?.name, attempts.size, attempts.count { it.correct })
                    }
                }
                measure("review-all-rows-after", size) {
                    data.questions.map { question ->
                        val attempts = index.attemptsByQuestion[question.id] ?: AttemptTotals()
                        Triple(index.subjects[question.subjectId]?.name, attempts.count, attempts.correct)
                    }
                }
                measure("assistant-analysis", size) { LocalAnalytics.analyze(data, NOW, ZoneId.of("UTC")) }
                val isolated = object : ContextWrapper(context) {
                    override fun getFilesDir() = File(root, "$size/files").apply { mkdirs() }
                    override fun getNoBackupFilesDir() = File(root, "$size/no-backup").apply { mkdirs() }
                }
                val store = StudyStore(isolated)
                store.save(data, immediateBackup = true)
                check(store.load(refreshDailyBackup = false) == data)
                measure("auxiliary-load-before", size) { store.load() }
                measure("auxiliary-load-after", size) { store.load(refreshDailyBackup = false) }
                measure("atomic-save", size) { store.save(data); data }
                report.appendLine("STATE bytes=${File(isolated.filesDir, "study-v1.json").length()} backupBytes=${File(isolated.filesDir, "compact-backup/latest.ninho-backup.gz").length()}")
            }
            return report.toString()
        } finally {
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            check(root.deleteRecursively())
        }
    }

    private fun measure(name: String, size: Int, action: () -> Any) {
        repeat(5) { sink = action() }
        val values = List(15) {
            val start = System.nanoTime()
            sink = action()
            System.nanoTime() - start
        }.sorted()
        report.appendLine("PERF $name records=$size medianMs=%.3f p95Ms=%.3f".format(java.util.Locale.ROOT,
            values[values.size / 2] / 1_000_000.0, values[ceil(values.size * .95).toInt() - 1] / 1_000_000.0))
    }

    private fun oldSuggestions(data: StudyData): List<StudySuggestion> = data.subjects.map { subject ->
        val last = data.sessions.filter { it.subjectId == subject.id }.maxByOrNull { it.at }
        val attempt = data.attempts.filter { it.subjectId == subject.id }.maxByOrNull { it.at }
        when {
            attempt != null && !attempt.correct && attempt.at >= (last?.at ?: 0) -> StudySuggestion(subject, "Sua última questão precisa de revisão. Retome a explicação e tente novamente.", attempt.at)
            last == null -> StudySuggestion(subject, "Você ainda não registrou uma sessão. Comece com 15 minutos para conhecer o conteúdo.", NOW)
            else -> {
                val days = when (last.rating) { 1 -> 1; 2 -> 3; else -> 7 }
                val due = last.at + days * 86_400_000L
                val reason = if (due <= NOW) "Última sessão há ${(NOW - last.at) / 86_400_000L} dias. Sua avaliação sugere uma revisão a cada $days dias."
                    else "Sua última avaliação sugere revisar em ${((due - NOW) / 86_400_000L + 1)} dia(s). Até lá, pratique a lembrança sem consultar."
                StudySuggestion(subject, reason, due)
            }
        }
    }.sortedBy { it.due }

    private fun fixture(size: Int): StudyData {
        val subjects = List(maxOf(12, size / 20)) { Subject("subject-$it", "Matéria $it") }
        val questions = List(size) { Question("q-$it", subjects[it % subjects.size].id, "Pergunta de estudo $it", "Explicação da resposta $it") }
        return StudyData(subjects = subjects, questions = questions,
            sessions = List(size * 4) { StudySession("session-$it", subjects[it % subjects.size].id, 1500, NOW - it * 60_000L, it % 3 + 1, "Anotação de estudo $it") },
            attempts = List(size * 4) { Attempt(questions[it % size].id, questions[it % size].subjectId, it % 3 != 0, NOW - it * 120_000L, "Resposta $it") },
            profile = StudentProfile(name = "Perfil sintético", goal = "Medir desempenho", completedAt = NOW))
    }

    companion object { private const val NOW = 1_790_164_800_000L }
}
