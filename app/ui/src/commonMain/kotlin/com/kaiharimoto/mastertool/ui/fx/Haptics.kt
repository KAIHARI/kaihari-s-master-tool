package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable
import com.kaiharimoto.mastertool.core.haptics.Haptic

/**
 * The motor under the glass.
 *
 * Deliberately not Compose's `LocalHapticFeedback`, which offers a long-press
 * and a text-handle drag and calls it a vocabulary. A table needs to tell a
 * lift from a landing from a detent, and the difference between those is
 * milliseconds and amplitude — exactly what the platform APIs underneath
 * expose and exactly what the common one hides.
 */
interface HapticEngine {
    fun play(haptic: Haptic)
}

/**
 * The platform's actuator, or null where there is not one.
 *
 * Composable because Android's is reached through a `Context` and there is no
 * good reason to invent a global to avoid asking for it.
 */
@Composable
expect fun rememberHapticEngine(): HapticEngine?
