package app.ninho.android

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

data class StreakSummary(val days: Int, val studiedToday: Boolean, val recentDays: List<Boolean>)

object StudyStreak {
    fun studyDays(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
        val sessionTimes = data.sessions.asSequence().filter { it.seconds > 0 && it.at in 1..now }.map { it.at }
        val answers = data.attempts.asSequence().filter { it.at in 1..now && it.questionId.isNotBlank() && it.subjectId.isNotBlank() }.map { it.at }
        return (sessionTimes + answers).map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    }
    fun summary(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()) =
        summary(studyDays(data, now, zone), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())

    fun summary(studied: Set<LocalDate>, today: LocalDate): StreakSummary {
        val todayDone = today in studied
        var cursor = if (todayDone) today else today.minusDays(1)
        var count = 0
        while (cursor in studied) { count++; cursor = cursor.minusDays(1) }
        return StreakSummary(count, todayDone, (6 downTo 0).map { today.minusDays(it.toLong()) in studied })
    }
}

/** Calendar columns start on Monday. */
data class StudyMonth(val month: YearMonth, val cells: List<LocalDate?>, val studied: Set<LocalDate>) {
    companion object {
        fun from(month: YearMonth, studyDays: Set<LocalDate>): StudyMonth {
            val leading = month.atDay(1).dayOfWeek.value - 1
            val count = ((leading + month.lengthOfMonth() + 6) / 7) * 7
            val cells = List(count) { index ->
                val day = index - leading + 1
                if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
            }
            return StudyMonth(month, cells, studyDays.filterTo(mutableSetOf()) { YearMonth.from(it) == month })
        }
    }
}

data class WidgetSnapshot(
    val zone: String, val capturedAt: Long, val ready: Boolean,
    val studyDays: Set<LocalDate>, val secondsByDay: Map<LocalDate, Long>,
    val dailyMinutes: Int, val dueAt: List<Long>, val focusMinutes: Int,
    val focusRunning: Boolean, val stopwatch: Boolean,
) {
    fun streak(now: Long, timeZone: ZoneId) = StudyStreak.summary(studyDays, Instant.ofEpochMilli(now).atZone(timeZone).toLocalDate())
    fun secondsToday(now: Long, timeZone: ZoneId) = secondsByDay[Instant.ofEpochMilli(now).atZone(timeZone).toLocalDate()] ?: 0L
    fun reviewsDue(now: Long) = dueAt.count { it <= now }
    companion object {
        fun from(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()): WidgetSnapshot {
            val sessions = data.sessions.filter { it.seconds > 0 && it.at in 1..now }
            val attempts = data.attempts.filter { it.at in 1..now }
            val activeSubjects = (sessions.map { it.subjectId } + attempts.map { it.subjectId }).toSet()
            val valid = data.copy(sessions = sessions, attempts = attempts, subjects = data.subjects.filter { it.id in activeSubjects })
            return WidgetSnapshot(zone.id, now, data.profile.completedAt != null, StudyStreak.studyDays(data, now, zone),
                sessions.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.mapValues { (_, values) -> values.sumOf { it.seconds.coerceIn(0, 86_400) }.coerceAtMost(86_400) },
                data.profile.dailyMinutes.coerceIn(1, 1440), StudyCoach.suggestions(valid, now).map { it.due },
                (data.timer.targetSeconds / 60).toInt().coerceIn(1, 240), data.timer.running, data.timer.stopwatch)
        }
    }
}
