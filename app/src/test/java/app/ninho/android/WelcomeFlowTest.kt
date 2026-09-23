package app.ninho.android

import org.junit.Assert.*
import org.junit.Test

class WelcomeFlowTest {
    @Test fun tenQuestionScreensCoverEveryProfileAnswerExactlyOnce() {
        assertEquals(10, WelcomeFlow.steps.size)
        assertTrue(WelcomeFlow.steps.all { it.questions.isNotEmpty() && it.guide.isNotBlank() })
        val questions = WelcomeFlow.steps.flatMap { it.questions }
        assertEquals(ProfileQuestion.entries.toSet(), questions.toSet())
        assertEquals(questions.size, questions.distinct().size)
    }

    @Test fun completionCannotHappenBeforeTheLastQuestion() {
        val profile = StudentProfile(name = "Ana", goal = "Concluir meu curso")
        (0 until WelcomeFlow.lastIndex).forEach { assertFalse(WelcomeFlow.canFinish(it, profile)) }
        assertTrue(WelcomeFlow.canFinish(WelcomeFlow.lastIndex, profile))
        assertFalse(WelcomeFlow.canFinish(WelcomeFlow.lastIndex, profile.copy(goal = " ")))
    }

    @Test fun resumePreservesAnOptionalQuestionEvenWhenItsAnswerIsEmpty() {
        val profile = StudentProfile(name = "Ana", goal = "Concluir meu curso")
        assertEquals(6, WelcomeFlow.resumeAt(6, profile))
        assertEquals(9, WelcomeFlow.resumeAt(999, profile))
        assertEquals(0, WelcomeFlow.resumeAt(-30, profile))
    }

    @Test fun resumeReturnsToMissingRequiredAnswerAndDoesNotInventCompletion() {
        assertEquals(0, WelcomeFlow.resumeAt(8, StudentProfile()))
        val profile = StudentProfile(name = "Ana")
        assertEquals(1, WelcomeFlow.resumeAt(8, profile))
        assertFalse(WelcomeFlow.canContinue(1, profile))
        assertTrue(WelcomeFlow.canContinue(2, profile))
        assertNull(profile.completedAt)
    }
}
