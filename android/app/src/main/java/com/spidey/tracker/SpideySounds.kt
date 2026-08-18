package com.spidey.tracker

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Short square-wave blips, synthesised rather than shipped as audio files.
 *
 * A handful of samples generated on the fly costs nothing in the APK and keeps
 * the arcade feel honest: every sound is a tone, not a recording.
 */
object SpideySounds {

    private const val SAMPLE_RATE = 22_050

    enum class Blip(val hz: Double, val ms: Int) {
        TAP(880.0, 45),
        DROP(1320.0, 90),
        CONFIRM(1760.0, 70),
        DENY(220.0, 110),
        SENSE(1046.0, 160),
    }

    /** Plays a blip. Silent and harmless if audio is unavailable. */
    fun play(blip: Blip) {
        runCatching {
            val samples = SAMPLE_RATE * blip.ms / 1000
            val buffer = ShortArray(samples)

            for (i in 0 until samples) {
                val phase = 2.0 * PI * blip.hz * i / SAMPLE_RATE
                // Square wave, with a linear fade so it stops without a click.
                val square = if (sin(phase) >= 0) 1.0 else -1.0
                val fade = 1.0 - i.toDouble() / samples
                buffer[i] = (square * fade * 0.22 * Short.MAX_VALUE).toInt().toShort()
            }

            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC,
            )
            track.write(buffer, 0, buffer.size)
            track.setNotificationMarkerPosition(buffer.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        // Static-mode tracks hold a buffer until released.
                        runCatching { t?.release() }
                    }

                    override fun onPeriodicNotification(t: AudioTrack?) = Unit
                },
            )
            track.play()
        }
    }
}
