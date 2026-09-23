package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StudyStreakTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = time("2026-09-23T16:00:00Z")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun session(value: String, seconds: Long = 1200) = StudySession(subjectId = "s", seconds = seconds, at = time(value), rating = 2, note = "")

    @Test fun currentDayExtendsConsecutiveDaysAndCountsDuplicatesOnce() {
        val data = StudyData(sessions = listOf(session("2026-09-21T20:00:00Z"), session("2026-09-22T20:00:00Z"), session("2026-09-23T12:00:00Z"), session("2026-09-23T14:00:00Z")))
        assertEquals(3, StudyStreak.summary(data, now, zone).days)
        assertTrue(StudyStreak.summary(data, now, zone).studiedToday)
    }
    @Test fun yesterdayPreservesTheStreakUntilTodayEnds() {
        val data = StudyData(sessions = listOf(session("2026-09-21T20:00:00Z"), session("2026-09-22T20:00:00Z")))
        assertEquals(2, StudyStreak.summary(data, now, zone).days)
        assertFalse(StudyStreak.summary(data, now, zone).studiedToday)
        assertEquals(0, StudyStreak.summary(data, time("2026-09-24T04:00:00Z"), zone).days)
    }
    @Test fun gapBreaksTheStreakWithoutDeletingHistory() {
        val data = StudyData(sessions = listOf(session("2026-09-20T12:00:00Z"), session("2026-09-21T12:00:00Z"), session("2026-09-23T12:00:00Z")))
        assertEquals(1, StudyStreak.summary(data, now, zone).days)
        assertEquals(3, StudyStreak.summary(data, now, zone).recentDays.count { it })
    }
    @Test fun futureZeroDurationAndNavigationDoNotCountAsStudy() {
        val data = StudyData(sessions = listOf(session("2026-09-23T19:00:00Z"), session("2026-09-23T12:00:00Z", 0)),
            timer = FocusTimer(running = true), usage = listOf(AppUsageDay("2026-09-23", "today", 300, 4, 0)))
        assertEquals(0, StudyStreak.summary(data, now, zone).days)
    }
    @Test fun answeringAQuestionCountsEvenWhenItIsIncorrect() {
        val data = StudyData(attempts = listOf(Attempt("q", "s", false, now)))
        assertEquals(1, StudyStreak.summary(data, now, zone).days)
    }
    @Test fun localMidnightAndTimezoneAreRespected() {
        val data = StudyData(sessions = listOf(session("2026-09-22T03:30:00Z"), session("2026-09-23T03:30:00Z")))
        val instant = time("2026-09-23T04:30:00Z")
        assertEquals(2, StudyStreak.summary(data, instant, zone).days)
        assertFalse(StudyStreak.summary(data, instant, zone).studiedToday)
        assertTrue(StudyStreak.summary(data, instant, ZoneId.of("UTC")).studiedToday)
    }
    @Test fun daylightSavingUsesCalendarDaysRatherThan24HourBuckets() {
        val data = StudyData(sessions = listOf(session("2026-03-07T17:00:00Z"), session("2026-03-08T16:00:00Z"), session("2026-03-09T16:00:00Z")))
        assertEquals(3, StudyStreak.summary(data, time("2026-03-09T17:00:00Z"), zone).days)
    }
    @Test fun snapshotRollsOverWithoutInferenceAndMatchesHomeStreak() {
        val data = StudyData(subjects = listOf(Subject("s", "História")), sessions = listOf(session("2026-09-23T12:00:00Z")), profile = StudentProfile(completedAt = now, dailyMinutes = 30))
        val snapshot = WidgetSnapshot.from(data, now, zone)
        assertEquals(StudyStreak.summary(data, now, zone), snapshot.streak(now, zone))
        assertEquals(1200L, snapshot.secondsToday(now, zone))
        assertEquals(0L, snapshot.secondsToday(time("2026-09-24T12:00:00Z"), zone))
        assertEquals(0, snapshot.reviewsDue(now))
        assertEquals(1, snapshot.reviewsDue(time("2026-09-26T12:00:00Z")))
    }
    @Test fun snapshotDoesNotExposeUnstudiedSubjectsAsOverdueReviews() {
        assertEquals(0, WidgetSnapshot.from(StudyData(subjects = listOf(Subject("s", "História"))), now, zone).reviewsDue(now))
    }
}
