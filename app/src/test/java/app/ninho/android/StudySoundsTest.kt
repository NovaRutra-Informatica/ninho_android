package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StudySoundsTest {
    @Test fun navigationToneIsShortQuietAndFadesAtBothEnds() {
        val samples = StudySounds.samples(StudySoundCue.NAVIGATION)
        assertEquals(1543, samples.size)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.maxOf { abs(it.toInt()) } < 4000)
        val crossings = samples.asList().zipWithNext().count { (left, right) -> left < 0 && right >= 0 }
        assertTrue(crossings in 49..51)
    }

    @Test fun navigationIsThrottledAndCannotInterruptAnotherCue() {
        val gate = NavigationSoundGate()
        assertTrue(gate.allows(1000, playing = false))
        gate.didPlay(1000)
        assertFalse(gate.allows(1139, playing = false))
        assertTrue(gate.allows(1140, playing = false))
        assertFalse(gate.allows(2000, playing = true))
        assertTrue(gate.allows(2000, playing = false))
    }

    @Test fun playbackCheckDoesNotConsumeThrottleBeforeAudioActuallyStarts() {
        val gate = NavigationSoundGate()
        assertFalse(gate.allows(1000, playing = true))
        assertTrue(gate.allows(1001, playing = false))
    }
}
