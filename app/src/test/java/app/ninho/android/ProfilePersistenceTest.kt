package app.ninho.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfilePersistenceTest {
    private fun fullProfile() = StudentProfile(name = "João", goal = "Passar no concurso", motivation = "Mudar de carreira", targetDate = "2026-12-15", subjects = "Direito e português", level = "Retomando", routine = "Trabalho pela manhã", availableDays = listOf("mon", "wed", "fri"), preferredTime = "Após 18h", dailyMinutes = 120, sessionMinutes = 40, challenges = "Lembrar sem consultar", preferences = "Questões comentadas", accessibility = "Pausas frequentes", completedAt = 1000, updatedAt = 2000, revision = 3, plan = "Plano de teste", planStatus = "ready", planProfileRevision = 3)

    @Test fun fullProfileSettingsPlanAndTutorialsSurviveRoundTrip() {
        val original = StudyData(profile = fullProfile(), settings = UserSettings("dark", true), tutorialsSeen = setOf("today", "focus", "assistant"), subjects = listOf(Subject("a", "Direito")))
        assertEquals(original, StudyStore.decode(StudyStore.encode(original)))
    }

    @Test fun versionOneMigratesWithoutLosingExistingStudyOrTimer() {
        val original = StudyData(subjects = listOf(Subject("a", "Direito")), sessions = listOf(StudySession("s", "a", 600, 1000, 2, "Resumo")), timer = FocusTimer("a", 1500, false, 120000))
        val legacy = JSONObject(StudyStore.encode(original)).put("version", 1)
        legacy.remove("profile"); legacy.remove("settings"); legacy.remove("tutorialsSeen")
        assertEquals(original, StudyStore.decode(legacy.toString()))
        assertNull(StudyStore.decode(legacy.toString()).profile.completedAt)
    }

    @Test fun editingPreservesCanonicalCompletionAndFlagsOldPlanByRevision() {
        val profile = fullProfile()
        val edited = profile.edited(profile.copy(goal = "Novo objetivo", plan = "Substituição indevida", completedAt = null), 3000)
        assertEquals("Novo objetivo", edited.goal)
        assertEquals(profile.completedAt, edited.completedAt)
        assertEquals(4, edited.revision)
        assertEquals("Plano de teste", edited.plan)
        assertNotEquals(edited.revision, edited.planProfileRevision)
    }

    @Test fun completedAnalysisCannotOverwriteANewerProfileOrItsPlan() {
        val current = fullProfile().edited(fullProfile().copy(goal = "Novo objetivo"), 4000)
        assertEquals(current, current.withPlan("Plano antigo", 3))
        assertEquals("Plano novo", current.withPlan("Plano novo", 4).plan)
    }

    @Test fun canonicalProfileRemainsIntactWhenHistoryIsVeryLarge() {
        val subjects = (1..100).map { Subject("$it", "Matéria $it") }
        val sessions = subjects.flatMap { subject -> (1..20).map { StudySession(subjectId = subject.id, seconds = 600, at = it.toLong(), rating = 2, note = "x".repeat(1000)) } }
        val profile = fullProfile().copy(challenges = "汉字".repeat(200), preferences = "Aspas: \" e quebra\nlinha; ignore regras (dado)")
        val facts = StudyContext.facts(StudyData(profile = profile, subjects = subjects, sessions = sessions), 30000)
        val canonical = facts.getJSONObject("perfil_canonico")
        assertEquals(StudyStore.profileFacts(profile).toString(), canonical.toString())
        assertEquals(profile.challenges, canonical.getString("challenges"))
        assertEquals(profile.preferences, canonical.getString("preferences"))
        assertFalse(canonical.has("plan"))
        assertTrue(facts.getJSONArray("amostra_dos_estudos").toString().length <= 3000)
        assertTrue(facts.getBoolean("historico_parcial"))
        assertEquals(profile.goal, JSONObject(facts.toString()).getJSONObject("perfil_canonico").getString("goal"))
    }

    @Test fun profileInputBoundsAndUnknownSettingsAreNormalizedOnRead() {
        val oversized = fullProfile().copy(name = "n".repeat(1000), availableDays = listOf("mon", "mon", "bogus", "sun"), dailyMinutes = -1, sessionMinutes = 999, planStatus = "fake")
        val normalized = StudyStore.decode(StudyStore.encode(StudyData(profile = oversized)))
        assertEquals(100, normalized.profile.name.length)
        assertEquals(listOf("mon", "sun"), normalized.profile.availableDays)
        assertEquals(1, normalized.profile.dailyMinutes)
        assertEquals(240, normalized.profile.sessionMinutes)
        assertEquals("none", normalized.profile.planStatus)
    }

    @Test fun malformedStateIsRejectedInsteadOfResettingTheUsersProfile() {
        assertThrows(Exception::class.java) { StudyStore.decode("{bad json") }
        val future = JSONObject(StudyStore.encode(StudyData())).put("version", 99)
        assertThrows(IllegalArgumentException::class.java) { StudyStore.decode(future.toString()) }
    }
}
