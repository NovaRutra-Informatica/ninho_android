package app.ninho.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class StudySoundPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val navigation = NavigationSoundGate()
    private val samples = mutableMapOf<StudySoundCue, ShortArray>()
    private var track: AudioTrack? = null
    private var playingUntil = 0L

    fun play(cue: StudySoundCue, enabled: Boolean) {
        if (!enabled || audio.isMusicActive || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) { stop(); return }
        val now = SystemClock.elapsedRealtime()
        if (cue == StudySoundCue.NAVIGATION && !navigation.allows(now, now < playingUntil)) return
        stop()
        try {
            val pcm = samples.getOrPut(cue) { StudySounds.samples(cue) }
            val next = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(StudySounds.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track = next
            check(next.write(pcm, 0, pcm.size) == pcm.size)
            next.setVolume(if (cue == StudySoundCue.NAVIGATION) .24f else .42f)
            next.play()
            playingUntil = now + cue.durationMillis
            if (cue == StudySoundCue.NAVIGATION) navigation.didPlay(now)
            handler.postDelayed({ if (track === next) stop() }, cue.durationMillis.toLong() + 30)
        } catch (_: Exception) { stop() }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val previous = track
        track = null
        playingUntil = 0
        try { previous?.stop() } catch (_: IllegalStateException) { }
        previous?.release()
    }
}
