package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StudyMonthTest {
    @Test fun calendarKeepsHistoryWhenTheCurrentStreakIsBroken() {
        val days = setOf(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-14"), LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-01"))
        val month = StudyMonth.from(YearMonth.of(2026, 8), days)
        assertEquals(0, StudyStreak.summary(days, LocalDate.parse("2026-09-23")).days)
        assertEquals(3, month.studied.size)
        assertTrue(LocalDate.parse("2026-08-01") in month.studied)
        assertFalse(LocalDate.parse("2026-09-01") in month.studied)
        assertEquals(31, month.cells.filterNotNull().size)
        assertEquals(42, month.cells.size)
        assertNull(month.cells[0])
        assertEquals(LocalDate.parse("2026-08-01"), month.cells[5])
    }

    @Test fun leapYearAndYearBoundaryHaveCorrectMondayFirstSlots() {
        val leap = StudyMonth.from(YearMonth.of(2024, 2), emptySet())
        assertEquals(29, leap.cells.filterNotNull().size)
        assertEquals(LocalDate.parse("2024-02-01"), leap.cells[3])
        assertEquals(LocalDate.parse("2024-02-29"), leap.cells[31])
        assertEquals(35, leap.cells.size)
        val january = StudyMonth.from(YearMonth.of(2026, 12).plusMonths(1), emptySet())
        assertEquals(YearMonth.of(2027, 1), january.month)
        assertEquals(LocalDate.parse("2027-01-01"), january.cells[4])
    }

    @Test fun calendarAndHeaderShareLocalDatesAndRejectInvalidActivity() {
        val zone = ZoneId.of("America/New_York")
        fun time(value: String) = Instant.parse(value).toEpochMilli()
        val now = time("2026-09-01T04:30:00Z")
        val data = StudyData(
            sessions = listOf(
                StudySession(subjectId = "s", seconds = 600, at = time("2026-09-01T03:30:00Z"), rating = 2, note = ""),
                StudySession(subjectId = "s", seconds = 0, at = now, rating = 2, note = ""),
                StudySession(subjectId = "s", seconds = 600, at = now + 60_000, rating = 2, note = "")
            ),
            attempts = listOf(Attempt("q", "s", false, now), Attempt("q", "s", true, now), Attempt("", "s", true, now))
        )
        val days = StudyStreak.studyDays(data, now, zone)
        assertEquals(setOf(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-01")), days)
        assertEquals(2, StudyStreak.summary(data, now, zone).days)
        assertEquals(setOf(LocalDate.parse("2026-08-31")), StudyMonth.from(YearMonth.of(2026, 8), days).studied)
        assertEquals(setOf(LocalDate.parse("2026-09-01")), StudyMonth.from(YearMonth.of(2026, 9), days).studied)
    }
}
