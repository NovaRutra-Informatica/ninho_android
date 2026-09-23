package app.ninho.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LocalAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()
    private val subject = Subject("math", "Matemática")
    private val profile = StudentProfile(name = "Ana", goal = "Revisar álgebra", dailyMinutes = 30, sessionMinutes = 45, completedAt = 1)
    private fun data() = StudyData(subjects = listOf(subject), profile = profile)

    @Test fun sparseAnswersNeverBecomeCertainOrADeclaredDifficulty() {
        assertEquals(.5, LocalAnalytics.smoothedAccuracy(0, 0), .00001)
        assertTrue(LocalAnalytics.smoothedAccuracy(1, 1) < .7)
        val four = data().copy(attempts = (1..4).map { Attempt("q", subject.id, false, now - it) })
        assertFalse(LocalAnalytics.analyze(four, now, zone).cards.any { it.id == "difficulty" })
        val five = four.copy(attempts = four.attempts + Attempt("q", subject.id, false, now))
        val card = LocalAnalytics.analyze(five, now, zone).cards.first { it.id == "difficulty" }
        assertTrue(card.explanation.contains("0 de 5"))
        assertTrue(card.evidence.contains("22%"))
        assertTrue(card.evidence.contains("não mede domínio"))
    }

    @Test fun recurringHoursRequireSixSessionsAndThreeDistinctDays() {
        fun session(day: Int, index: Int) = StudySession("s$day-$index", subject.id, 1200, now - day * LocalAnalytics.DAY - 2 * 3_600_000, 2, "")
        val concentrated = data().copy(sessions = (1..6).map { session(1, it) })
        assertFalse(LocalAnalytics.analyze(concentrated, now, zone).cards.any { it.id == "schedule" })
        val distributed = data().copy(sessions = (1..3).flatMap { day -> (1..2).map { session(day, it) } })
        assertTrue(LocalAnalytics.analyze(distributed, now, zone).cards.any { it.id == "schedule" && it.evidence.contains("não significa maior produtividade") })
        assertFalse(LocalAnalytics.analyze(distributed.copy(sessions = distributed.sessions.take(5)), now, zone).cards.any { it.id == "schedule" })
    }

    @Test fun futureAndExpiredAttemptsDoNotCreateDifficultyPatterns() {
        val future = (1..8).map { Attempt("q", subject.id, false, now + it) }
        val expired = (1..8).map { Attempt("q", subject.id, false, now - 91 * LocalAnalytics.DAY - it) }
        assertFalse(LocalAnalytics.analyze(data().copy(attempts = future + expired), now, zone).cards.any { it.id == "difficulty" })
        assertFalse(LocalAnalytics.analyze(data().copy(attempts = future), now, zone).cards.any { it.id == "review" })
    }

    @Test fun realLowFeedbackAndPausesAreExplainedWithoutInferringAttention() {
        val records = data().copy(sessions = (1..2).map { StudySession("s$it", subject.id, 600, now - it * LocalAnalytics.DAY, 1, "") }, usage = listOf(AppUsageDay("2026-09-23", "focus", timerPauses = 3)))
        val result = LocalAnalytics.analyze(records, now, zone)
        assertTrue(result.cards.any { it.id == "feedback" && it.explanation.contains("2 sessões") })
        assertTrue(result.cards.any { it.id == "pauses" && it.evidence.contains("não indica desatenção") })
        assertTrue(result.plan.any { it.contains("até 30 minutos") })
    }

    @Test fun includedPlanWorksImmediatelyWithoutAnyLanguageModel() {
        val result = LocalAnalytics.analyze(StudyData(profile = profile), now, zone)
        assertEquals("baseline", result.cards.single().id)
        assertTrue(result.plan.first().contains(profile.goal))
        assertTrue(result.cards.single().evidence.contains("ainda faltam registros"))
    }

    @Test fun usageIsBoundedToNinetyDaysRoutesAndDailyCaps() {
        val records = (0..120).flatMap { days -> UsageLedger.routes.map { AppUsageDay(Instant.ofEpochMilli(now - days * LocalAnalytics.DAY).atZone(zone).toLocalDate().toString(), it, 10, 1) } } + listOf(AppUsageDay("2026-09-24", "today"), AppUsageDay("2026-09-23", "other-app", 1000), AppUsageDay("bad", "today"), AppUsageDay("2026-09-23", "today", Long.MAX_VALUE, Int.MAX_VALUE))
        val result = UsageLedger.prune(records, now, zone)
        assertEquals(540, result.size)
        assertEquals("2026-06-26", result.first().day)
        val today = result.first { it.day == "2026-09-23" && it.route == "today" }
        assertEquals(86_400L, today.activeSeconds)
        assertEquals(10000, today.visits)
        assertEquals(0L, StudyCoach.secondsToday(data().copy(usage = result), now, zone))
    }

    @Test fun foregroundClockCapsIdleTimeAndPreservesFractions() {
        val clock = ActiveRouteClock()
        clock.begin(1000)
        assertEquals(0L, clock.takeSeconds(1500))
        assertEquals(1L, clock.takeSeconds(2100))
        assertEquals(59L, clock.takeSeconds(121000))
        assertEquals(0L, clock.takeSeconds(181000))
        clock.touch(181000)
        assertEquals(2L, clock.takeSeconds(183000))
        clock.begin(200000)
        assertEquals(0L, clock.takeSeconds(200400))
    }

    @Test fun timerReminderUsesRemainingTimestampsAndRejectsPausedOrFreeTimer() {
        val timer = FocusTimer(subject.id, 60, bootCount = 4).resumed(1000, now, 4)
        assertEquals(30_000L, TimerReminderPolicy.remaining(timer, 31000, now + 999999, 4))
        assertEquals(0L, TimerReminderPolicy.remaining(timer, 99999, now + 99999, 4))
        assertEquals(20_000L, TimerReminderPolicy.remaining(timer, 1, now + 40000, 5))
        assertNull(TimerReminderPolicy.remaining(timer.copy(running = false), 31000, now, 4))
        assertNull(TimerReminderPolicy.remaining(timer.copy(stopwatch = true), 31000, now, 4))
        assertNotEquals(TimerReminderPolicy.key(timer), TimerReminderPolicy.key(timer.paused(2000, now, 4).resumed(3000, now + 2000, 4)))
    }

    @Test fun versionThreePersistsPreferencesAndUsageAndVersionTwoMigrates() {
        val full = data().copy(settings = UserSettings("dark", true, true, false), usage = listOf(AppUsageDay("2026-09-23", "focus", 42, 2, 1)))
        assertEquals(full, StudyStore.decode(StudyStore.encode(full)))
        val legacy = JSONObject(StudyStore.encode(full)).put("version", 2)
        legacy.remove("usage")
        legacy.getJSONObject("settings").remove("timerReminder")
        legacy.getJSONObject("settings").remove("appUsage")
        val migrated = StudyStore.decode(legacy.toString())
        assertEquals(full.profile, migrated.profile)
        assertEquals(full.subjects, migrated.subjects)
        assertTrue(migrated.usage.isEmpty())
        assertFalse(migrated.settings.timerReminder)
        assertTrue(migrated.settings.appUsage)
    }

    @Test fun reminderRejectsStaleDuplicateEarlyDisabledAndPausedDelivery() {
        val timer = FocusTimer(subject.id, 60, bootCount = 4).resumed(1000, now, 4)
        val key = TimerReminderPolicy.key(timer)
        fun allowed(current: FocusTimer = timer, requested: String = key, delivered: String = "", enabled: Boolean = true, mono: Long = 61000) = TimerReminderPolicy.canDeliver(current, requested, delivered, enabled, mono, now + 60000, 4)
        assertTrue(allowed())
        assertFalse(allowed(requested = "older-session"))
        assertFalse(allowed(delivered = key))
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(mono = 30000))
        assertFalse(allowed(current = timer.copy(running = false, elapsedMillis = 60000)))
        assertFalse(allowed(current = timer.copy(stopwatch = true)))
        val restarted = timer.paused(61000, now + 60000, 4).copy(elapsedMillis = 0).resumed(62000, now + 61000, 4)
        assertFalse(allowed(current = restarted, mono = 122000))
    }
}
