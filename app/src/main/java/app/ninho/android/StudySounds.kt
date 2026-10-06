package app.ninho.android

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class StudySoundCue(val durationMillis: Int, val notes: List<Double>) {
    NAVIGATION(70, listOf(720.0)),
    SAVE(140, listOf(659.25, 783.99)),
    REVIEW(200, listOf(523.25, 659.25)),
    COMPLETE(340, listOf(659.25, 783.99, 1046.50)),
    FOCUS_DONE(480, listOf(523.25, 659.25, 880.0)),
}

object StudySounds {
    const val SAMPLE_RATE = 22050
    fun samples(cue: StudySoundCue): ShortArray {
        val count = SAMPLE_RATE * cue.durationMillis / 1000
        return ShortArray(count) { index ->
            val position = index.toDouble() / (count - 1)
            val notePosition = position * cue.notes.size
            val noteIndex = notePosition.toInt().coerceAtMost(cue.notes.lastIndex)
            val envelope = sin(PI * (notePosition - noteIndex).coerceAtMost(1.0)).pow(2)
            val time = index.toDouble() / SAMPLE_RATE
            val frequency = cue.notes[noteIndex]
            val wave = sin(2 * PI * frequency * time) + .12 * sin(4 * PI * frequency * time)
            val amplitude = if (cue == StudySoundCue.NAVIGATION) .10 else .16
            (wave * envelope * amplitude * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}

class NavigationSoundGate {
    private var lastNavigation: Long? = null
    fun allows(now: Long, playing: Boolean) = !playing && lastNavigation?.let { now - it >= 140 } != false
    fun didPlay(now: Long) { lastNavigation = now }
}
