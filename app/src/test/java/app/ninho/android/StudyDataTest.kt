package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StudyDataTest {
    @Test fun changingWallClockDoesNotChangeActiveTimer() {
        val timer = FocusTimer().resumed(1000, 100_000, 8)
        assertEquals(30_000, timer.elapsed(31_000, 9_000_000, 8))
        assertEquals(30_000, timer.elapsed(31_000, 10_000, 8))
    }
    @Test fun rebootDetectedEvenWhenNewUptimeIsLonger() {
        val timer = FocusTimer().resumed(1000, 100_000, 8)
        assertEquals(800_000, timer.elapsed(31_000, 900_000, 9))
    }
    @Test fun timerIncludesBackgroundTimeWithoutTicks() {
        val timer = FocusTimer(targetSeconds = 1500).resumed(2000, 100_000)
        assertEquals(300_000, timer.elapsed(302_000, 400_000))
        assertEquals(1_500_000, timer.elapsed(3_002_000, 3_100_000))
    }
    @Test fun pauseResumeDoesNotCountPausedTime() {
        val paused = FocusTimer(stopwatch = true).resumed(1000, 50_000).paused(61_000, 110_000)
        assertEquals(60_000, paused.elapsed(181_000, 230_000))
        val resumed = paused.resumed(181_000, 230_000)
        assertEquals(90_000, resumed.elapsed(211_000, 260_000))
    }
    @Test fun rebootUsesPersistedWallTime() {
        val timer = FocusTimer(targetSeconds = 1500).resumed(500_000, 1_000_000)
        assertEquals(120_000, timer.elapsed(1000, 1_120_000))
    }
    @Test fun stopwatchHasBoundedDuration() {
        val timer = FocusTimer(stopwatch = true).resumed(1000, 1000)
        assertEquals(86_400_000, timer.elapsed(172_801_000, 172_801_000))
    }
    @Test fun recentFailedQuestionTakesPriority() {
        val first = Subject("a", "Matemática"); val second = Subject("b", "História")
        val data = StudyData(subjects = listOf(first, second), sessions = listOf(StudySession(subjectId="a",seconds=1500,at=1000,rating=3,note="")), attempts = listOf(Attempt("q","a",false,2000)))
        val suggestion = StudyCoach.suggestions(data,3000).first()
        assertEquals("a",suggestion.subject.id)
        assertTrue(suggestion.reason.contains("questão"))
    }
    @Test fun reviewIntervalUsesActualFeedback() {
        val data = StudyData(subjects=listOf(Subject("a","Física")),sessions=listOf(StudySession(subjectId="a",seconds=1500,at=1000,rating=1,note="")))
        assertEquals(86_401_000,StudyCoach.suggestions(data,1000).single().due)
    }
    @Test fun totalsUseLocalDateAtMidnight() {
        val now = Instant.parse("2026-09-23T02:00:00Z").toEpochMilli()
        val old = Instant.parse("2026-09-22T03:00:00Z").toEpochMilli()
        val data = StudyData(sessions=listOf(StudySession(subjectId="a",seconds=90,at=now,rating=2,note=""), StudySession(subjectId="a",seconds=600,at=old,rating=2,note="")))
        assertEquals(90,StudyCoach.secondsToday(data,now,ZoneId.of("America/New_York")))
    }
}
