package com.kaiharimoto.mastertool.ui.fx

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/**
 * Off by default at a desk: a mouse is not a hand on a card, and desks have
 * speakers other people can hear. One toggle turns it on.
 */
actual fun defaultFeedbackEnabled(): Boolean = false

actual fun createSoundPlayer(): SoundPlayer = DesktopSoundPlayer()

/** A Clip per play, closed when it stops — the sounds are tens of milliseconds. */
private class DesktopSoundPlayer : SoundPlayer {

    private val data = mutableMapOf<SoundEffect, ByteArray>()

    override fun load(effect: SoundEffect, wav: ByteArray) {
        data[effect] = wav
    }

    override fun play(effect: SoundEffect) {
        val bytes = data[effect] ?: return
        runCatching {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            val clip = AudioSystem.getClip()
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) clip.close()
            }
            clip.open(stream)
            clip.start()
        }
    }
}
