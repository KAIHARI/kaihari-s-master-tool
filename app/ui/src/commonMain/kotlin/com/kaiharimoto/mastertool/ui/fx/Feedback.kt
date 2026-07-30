package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.kaiharimoto.mastertool.ui.art.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The audible half of tactility: five quiet, short card sounds, triggered
 * only by things the user's hand does — lift, set down, slide, shuffle,
 * deal. Nothing decorative, nothing ambient.
 *
 * The samples are synthesised in-repo (see the sounds directory) and read as
 * plain 44.1 kHz 16-bit mono WAV so both platform players can stay trivial.
 */
enum class SoundEffect(val path: String) {
    LIFT("files/sounds/lift.wav"),
    SNAP("files/sounds/snap.wav"),
    SLIDE("files/sounds/slide.wav"),
    SHUFFLE("files/sounds/shuffle.wav"),
    DEAL("files/sounds/deal.wav"),
}

interface SoundPlayer {
    fun load(effect: SoundEffect, wav: ByteArray)
    fun play(effect: SoundEffect)
}

/** Platform audio out. Android plays via AudioTrack, desktop via javax.sound. */
expect fun createSoundPlayer(): SoundPlayer

/** What "feedback on" should mean before the user has said: tablets yes, desks no. */
expect fun defaultFeedbackEnabled(): Boolean

/**
 * One gate for every sound the app makes, so a single toggle silences it and
 * a missing player (or an effect that failed to load) degrades to nothing.
 */
class Feedback(
    private val player: SoundPlayer?,
    private val enabled: () -> Boolean,
) {
    val isEnabled: Boolean get() = enabled()

    fun play(effect: SoundEffect) {
        if (enabled()) player?.play(effect)
    }

    companion object {
        val SILENT = Feedback(player = null, enabled = { false })
    }
}

val LocalFeedback = staticCompositionLocalOf { Feedback.SILENT }

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberFeedback(enabled: () -> Boolean): Feedback {
    val player = remember { runCatching { createSoundPlayer() }.getOrNull() }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        SoundEffect.entries.forEach { effect ->
            runCatching { player.load(effect, Res.readBytes(effect.path)) }
        }
    }

    return remember(player) { Feedback(player, enabled) }
}
