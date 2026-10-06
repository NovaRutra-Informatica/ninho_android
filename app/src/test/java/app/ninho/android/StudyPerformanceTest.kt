package app.ninho.android

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import java.time.ZoneId
import kotlin.math.ceil

class StudyPerformanceTest {
    @Test fun measureSyntheticCollections() {
        assumeTrue(System.getenv("NINHO_PERFORMANCE") == "1")
        println("NINHO_PERFORMANCE JVM=${System.getProperty("java.version")} warmups=12 samples=30 zone=UTC; synthetic, not UI frame timings")
        for (size in listOf(71, 1000, 5000)) {
            val data = fixture(size)
            val index = StudyIndex.build(data)
            println("DATA questions=$size subjects=${data.subjects.size} sessions=${data.sessions.size} attempts=${data.attempts.size}")
            measure("recommendations", size) { StudyCoach.suggestions(data, now) }
            measure("build-index", size) { StudyIndex.build(data) }
            measure("recommendations-cached-index", size) { StudyCoach.suggestions(data, now, index) }
            measure("review-first-8", size) {
                data.questions.take(8).map { question ->
                    val attempts = index.attemptsByQuestion[question.id] ?: AttemptTotals()
                    Triple(index.subjects[question.subjectId]?.name, attempts.count, attempts.correct)
                }
            }
            measure("review-all-rows", size) {
                data.questions.map { question ->
                    val attempts = index.attemptsByQuestion[question.id] ?: AttemptTotals()
                    Triple(index.subjects[question.subjectId]?.name, attempts.count, attempts.correct)
                }
            }
            measure("assistant-analysis", size) { LocalAnalytics.analyze(data, now, ZoneId.of("UTC")) }
            measure("encode-decode", size) { StudyStore.decode(StudyStore.encode(data)) }
            measure("compact-backup", size) { StudyBackupCodec.encode(data, now) }
        }
    }

    private fun measure(name: String, size: Int, block: () -> Any) {
        repeat(12) { sink = block() }
        val meter = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (meter?.isThreadAllocatedMemorySupported == true) meter.isThreadAllocatedMemoryEnabled = true
        val times = ArrayList<Long>()
        val bytes = ArrayList<Long>()
        repeat(30) {
            val before = meter?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: 0
            val start = System.nanoTime()
            sink = block()
            times += System.nanoTime() - start
            bytes += (meter?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: 0) - before
        }
        val ordered = times.sorted()
        val median = ordered[ordered.size / 2] / 1_000_000.0
        val p95 = ordered[ceil(ordered.size * .95).toInt() - 1] / 1_000_000.0
        println("PERF $name records=$size medianMs=%.3f p95Ms=%.3f allocatedKiB=%.1f".format(java.util.Locale.ROOT, median, p95, bytes.sorted()[bytes.size / 2] / 1024.0))
    }

    companion object {
        const val now = 1_790_164_800_000L
        @Volatile private var sink: Any? = null
        fun fixture(size: Int): StudyData {
            val subjects = List(maxOf(12, size / 20)) { Subject("subject-$it", "Matéria $it") }
            val questions = List(size) { Question("q-$it", subjects[it % subjects.size].id, "Pergunta de estudo $it", "Explicação da resposta $it") }
            return StudyData(subjects = subjects, questions = questions,
                sessions = List(size * 4) { StudySession("session-$it", subjects[it % subjects.size].id, 1500, now - it * 60_000L, it % 3 + 1, "Anotação de estudo $it") },
                attempts = List(size * 4) { Attempt(questions[it % size].id, questions[it % size].subjectId, it % 3 != 0, now - it * 120_000L, "Resposta $it") },
                profile = StudentProfile(name = "Perfil sintético", goal = "Medir desempenho", completedAt = now))
        }
    }
}
