package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StudyIndexTest {
    @Test fun largeCollectionKeepsEveryQuestionAndItsHistory() {
        val data = StudyPerformanceTest.fixture(5000)
        val index = StudyIndex.build(data)
        assertEquals(5000, index.questions.size)
        assertEquals(data.sessions.size, index.sessionsBySubject.values.sumOf { it.count })
        assertEquals(data.sessions.sumOf { it.seconds }, index.sessionsBySubject.values.sumOf { it.seconds })
        assertEquals(data.attempts.size, index.attemptsByQuestion.values.sumOf { it.count })
        assertEquals(data.attempts.count { it.correct }, index.attemptsBySubject.values.sumOf { it.correct })
        for (question in data.questions) {
            val n = question.id.removePrefix("q-").toInt()
            val expectedCorrect = (0..3).count { (n + it * 5000) % 3 != 0 }
            assertEquals(AttemptTotals(4, expectedCorrect), index.attemptsByQuestion[question.id])
            assertSame(question, index.questions[question.id])
        }
        val suggestions = StudyCoach.suggestions(data, StudyPerformanceTest.now, index)
        assertEquals(data.subjects.size, suggestions.size)
        assertTrue(suggestions.zipWithNext().all { (a, b) -> a.due <= b.due })
    }

    @Test fun unsortedRecordsAndTiedTimesKeepOriginalRecommendationSemantics() {
        val data = StudyData(subjects = listOf(Subject("one", "Um"), Subject("two", "Dois"), Subject("new", "Novo")),
            sessions = listOf(StudySession("a", "one", 60, 5000, 1, ""), StudySession("b", "one", 90, 5000, 3, ""), StudySession("c", "one", 100, 1000, 2, "")),
            attempts = listOf(Attempt("q", "two", false, 3000), Attempt("q", "two", true, 3000), Attempt("q", "two", true, 1000)))
        val index = StudyIndex.build(data)
        assertEquals("a", index.latestSession["one"]?.id)
        assertEquals(false, index.latestAttempt["two"]?.correct)
        val suggestions = StudyCoach.suggestions(data, 8000, index)
        assertEquals(listOf("two", "new", "one"), suggestions.map { it.subject.id })
        assertEquals(listOf(3000L, 8000L, 86_405_000L), suggestions.map { it.due })
        assertEquals(SessionTotals(3, 250), index.sessionsBySubject["one"])
    }

    @Test fun unrelatedAutosavesReuseIndexButChangedStudyCollectionsInvalidateIt() {
        val data = StudyPerformanceTest.fixture(71)
        val index = StudyIndex.build(data)
        assertTrue(index.matches(data.copy(profile = data.profile.copy(name = "Novo nome"), timer = data.timer.copy(running = true),
            usage = listOf(AppUsageDay("2026-09-23", "reviews", visits = 1)), settings = data.settings.copy(sound = true))))
        assertFalse(index.matches(data.copy(attempts = data.attempts + Attempt("q-0", "subject-0", true, StudyPerformanceTest.now))))
        assertFalse(index.matches(data.copy(questions = data.questions.dropLast(1))))
        assertFalse(index.matches(data.copy(subjects = data.subjects.reversed())))
        assertFalse(index.matches(data.copy(sessions = emptyList())))
    }

    @Test fun totalsPreserveDaylightSavingBoundariesAndExcludeFutureOrZeroTime() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-11-01T07:00:00Z").toEpochMilli()
        val times = listOf("2026-11-01T03:59:59Z", "2026-11-01T04:00:00Z", "2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z", "2026-11-01T07:00:01Z")
        val data = StudyData(sessions = times.mapIndexed { n, value -> StudySession("$n", "subject", 60, Instant.parse(value).toEpochMilli(), 2, "") } + StudySession("zero", "subject", 0, now, 2, ""))
        assertEquals(180L, StudyCoach.secondsToday(data, now, zone))
    }
}
