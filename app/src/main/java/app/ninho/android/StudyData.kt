package app.ninho.android

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

data class Subject(val id: String = UUID.randomUUID().toString(), val name: String)
data class StudySession(val id: String = UUID.randomUUID().toString(), val subjectId: String, val seconds: Long, val at: Long, val rating: Int, val note: String)
data class Question(val id: String = UUID.randomUUID().toString(), val subjectId: String, val prompt: String, val answer: String)
data class Attempt(val questionId: String, val subjectId: String, val correct: Boolean, val at: Long, val response: String = "")

/** Store timestamps, never ticks: sleep, backgrounding and process recreation do not lose time. */
data class FocusTimer(
    val subjectId: String = "", val targetSeconds: Long = 25 * 60, val stopwatch: Boolean = false,
    val elapsedMillis: Long = 0, val running: Boolean = false,
    val startedMonotonic: Long = 0, val startedWall: Long = 0,
    val bootCount: Int = -1,
) {
    fun elapsed(nowMonotonic: Long, nowWall: Long, currentBootCount: Int = -1): Long {
        val since = if (!running) 0 else {
            // elapsedRealtime includes deep sleep. A different boot requires the wall-clock fallback.
            val sameBoot = if (bootCount >= 0 && currentBootCount >= 0) bootCount == currentBootCount
                else nowMonotonic >= startedMonotonic && abs((nowWall - nowMonotonic) - (startedWall - startedMonotonic)) < 10_000
            if (sameBoot) nowMonotonic - startedMonotonic else (nowWall - startedWall).coerceAtLeast(0)
        }
        return (elapsedMillis + since).coerceIn(0, if (stopwatch) 86_400_000 else targetSeconds * 1000)
    }
    fun paused(nowMonotonic: Long, nowWall: Long, currentBootCount: Int = -1) = copy(elapsedMillis = elapsed(nowMonotonic, nowWall, currentBootCount), running = false)
    fun resumed(nowMonotonic: Long, nowWall: Long, currentBootCount: Int = -1) = copy(running = true, startedMonotonic = nowMonotonic, startedWall = nowWall, bootCount = currentBootCount)
}

data class StudyData(
    val subjects: List<Subject> = emptyList(), val sessions: List<StudySession> = emptyList(),
    val questions: List<Question> = emptyList(), val attempts: List<Attempt> = emptyList(),
    val timer: FocusTimer = FocusTimer(),
    val profile: StudentProfile = StudentProfile(), val settings: UserSettings = UserSettings(),
    val tutorialsSeen: Set<String> = emptySet(),
    val usage: List<AppUsageDay> = emptyList(),
)

data class StudySuggestion(val subject: Subject, val reason: String, val due: Long)

object StudyCoach {
    private const val DAY = 86_400_000L
    fun suggestions(data: StudyData, now: Long): List<StudySuggestion> = data.subjects.map { subject ->
        val last = data.sessions.filter { it.subjectId == subject.id }.maxByOrNull { it.at }
        val attempt = data.attempts.filter { it.subjectId == subject.id }.maxByOrNull { it.at }
        when {
            attempt != null && !attempt.correct && attempt.at >= (last?.at ?: 0) ->
                StudySuggestion(subject, "Sua última questão precisa de revisão. Retome a explicação e tente novamente.", attempt.at)
            last == null -> StudySuggestion(subject, "Você ainda não registrou uma sessão. Comece com 15 minutos para conhecer o conteúdo.", now)
            else -> {
                val days = when (last.rating) { 1 -> 1; 2 -> 3; else -> 7 }
                val due = last.at + days * DAY
                val reason = if (due <= now) "Última sessão há ${(now - last.at) / DAY} dias. Sua avaliação sugere uma revisão a cada $days dias."
                    else "Sua última avaliação sugere revisar em ${((due - now) / DAY + 1)} dia(s). Até lá, pratique a lembrança sem consultar."
                StudySuggestion(subject, reason, due)
            }
        }
    }.sortedBy { it.due }

    fun secondsToday(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return data.sessions.filter { it.seconds > 0 && it.at in 1..now && Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == date }.sumOf { it.seconds }
    }
}
