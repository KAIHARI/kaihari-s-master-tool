package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable

/**
 * A desk has no motor in it.
 *
 * Null rather than a no-op implementation, so [Feedback] can tell "the user
 * turned it off" from "there was never anything here" — and so nothing on the
 * desktop side pays for a vocabulary it cannot speak.
 */
@Composable
actual fun rememberHapticEngine(): HapticEngine? = null
