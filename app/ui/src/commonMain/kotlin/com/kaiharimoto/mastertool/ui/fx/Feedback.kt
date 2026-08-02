package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.kaiharimoto.mastertool.core.haptics.Haptic
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
 * One gate for everything the app does to a hand, so a single toggle silences
 * it and a missing player, a missing motor or an effect that failed to load all
 * degrade to nothing.
 *
 * Sound and haptics are one feature with two outputs rather than two features.
 * The app has exactly one idea of when the user's hand did something, and both
 * of these are answers to it — which is why the preference has always been
 * called "sound and haptics" and why [play] takes both at once. Where a device
 * can only manage one of them, the haptic is the one that survives: it arrives
 * on a muted tablet, which is how most of this app is used.
 */
class Feedback(
    private val player: SoundPlayer?,
    private val haptics: HapticEngine?,
    private val enabled: () -> Boolean,
) {
    val isEnabled: Boolean get() = enabled()

    fun play(effect: SoundEffect) {
        if (enabled()) player?.play(effect)
    }

    /** A sound and the buzz that goes with it, named together at the call site. */
    fun play(effect: SoundEffect, haptic: Haptic?) {
        if (!enabled()) return
        player?.play(effect)
        haptic?.let { haptics?.play(it) }
    }

    /**
     * A buzz with nothing to hear.
     *
     * For the things that have no sound because they make none: crossing a
     * rotation detent, a card tilting up under a held finger. A table where
     * only the audible events are felt has a vocabulary with holes in it.
     */
    fun feel(haptic: Haptic) {
        if (enabled()) haptics?.play(haptic)
    }

    companion object {
        val SILENT = Feedback(player = null, haptics = null, enabled = { false })
    }
}

val LocalFeedback = staticCompositionLocalOf { Feedback.SILENT }

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberFeedback(enabled: () -> Boolean): Feedback {
    val player = remember { runCatching { createSoundPlayer() }.getOrNull() }
    val haptics = rememberHapticEngine()

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        SoundEffect.entries.forEach { effect ->
            runCatching { player.load(effect, Res.readBytes(effect.path)) }
        }
    }

    return remember(player, haptics) { Feedback(player, haptics, enabled) }
}
