package com.kaiharimoto.mastertool.ui.fx

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** A tablet in your hands should feel like cards under them. */
actual fun defaultFeedbackEnabled(): Boolean = true

actual fun createSoundPlayer(): SoundPlayer = AndroidSoundPlayer()

/**
 * One static-mode AudioTrack per effect, loaded once and rewound per play —
 * no per-play allocation, no file round trip, no Context needed. The WAVs are
 * canonical 44-byte-header PCM16 mono, so the "parser" is an offset.
 */
private class AndroidSoundPlayer : SoundPlayer {

    private val tracks = mutableMapOf<SoundEffect, AudioTrack>()

    override fun load(effect: SoundEffect, wav: ByteArray) {
        if (wav.size <= WAV_HEADER_BYTES) return
        val pcm = wav.copyOfRange(WAV_HEADER_BYTES, wav.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size)
            .build()

        if (track.write(pcm, 0, pcm.size) >= 0) {
            tracks[effect]?.release()
            tracks[effect] = track
        } else {
            track.release()
        }
    }

    override fun play(effect: SoundEffect) {
        val track = tracks[effect] ?: return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private companion object {
        const val WAV_HEADER_BYTES = 44
        const val SAMPLE_RATE = 44_100
    }
}
