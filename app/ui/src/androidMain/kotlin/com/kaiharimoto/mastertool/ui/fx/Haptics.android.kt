package com.kaiharimoto.mastertool.ui.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kaiharimoto.mastertool.core.haptics.Haptic

@Composable
actual fun rememberHapticEngine(): HapticEngine? {
    val context = LocalContext.current
    return remember(context) { runCatching { AndroidHaptics(context) }.getOrNull() }
}

/**
 * Two ways of saying the same thing, and the good one is tried first.
 *
 * `VibrationEffect.Composition` (API 30) hands the *device* a tick or a click
 * and a scale, and the device plays it on whatever actuator it actually has.
 * On the LRA in a modern tablet that is a crisp, short, tuned event; the same
 * thing spelled as "buzz at amplitude 130 for 9 milliseconds" is a smear,
 * because a rotary motor spends most of nine milliseconds spinning up. So
 * crispness picks a primitive where primitives exist.
 *
 * The waveform path is the fallback and it is not a token one: it is what
 * every device below API 30 plays, and it is built from the same pattern data,
 * so the *rhythm* of a riffle survives even where the texture cannot.
 */
private class AndroidHaptics(context: Context) : HapticEngine {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasActuator = vibrator?.hasVibrator() == true

    /**
     * Whether this device can play the primitives, asked once.
     *
     * `areAllPrimitivesSupported` is a real IPC and this is called from the
     * frame loop's neighbourhood — a detent can fire several times a second
     * while a card is being twisted.
     */
    @Suppress("NewApi")
    private val composable: Boolean = hasActuator &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        runCatching {
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            ) == true
        }.getOrDefault(false)

    override fun play(haptic: Haptic) {
        val vibrator = vibrator ?: return
        if (!hasActuator) return

        runCatching {
            if (composable) {
                vibrator.vibrate(compose(haptic))
            } else {
                vibrator.vibrate(waveform(haptic))
            }
        }
    }

    /**
     * The pattern as primitives: one per pulse, delayed by the gap before it.
     *
     * Crispness chooses between the two primitives every device that has any
     * is guaranteed to have — a tick for a boundary, a click for a contact.
     */
    @Suppress("NewApi")
    private fun compose(haptic: Haptic): VibrationEffect {
        val primitive = if (haptic.crispness >= 0.6f) {
            VibrationEffect.Composition.PRIMITIVE_CLICK
        } else {
            VibrationEffect.Composition.PRIMITIVE_TICK
        }

        var composition = VibrationEffect.startComposition()
        var delay = 0
        haptic.pattern.pulses.forEach { pulse ->
            composition = composition.addPrimitive(
                primitive,
                pulse.amplitude.coerceIn(0f, 1f),
                delay,
            )
            delay = pulse.durationMs + pulse.gapMs
        }
        return composition.compose()
    }

    private fun waveform(haptic: Haptic): VibrationEffect {
        val timings = haptic.pattern.timings().toLongArray()
        val amplitudes = haptic.pattern.motorLevels().toIntArray()
        return if (vibrator?.hasAmplitudeControl() == true) {
            VibrationEffect.createWaveform(timings, amplitudes, NO_REPEAT)
        } else {
            // No amplitude control, so the rhythm is all that survives. The
            // zero-amplitude entries are the gaps, and `createWaveform` without
            // amplitudes reads the array as on/off starting with *on* — which
            // is exactly what the pattern already is.
            VibrationEffect.createWaveform(timings, NO_REPEAT)
        }
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}
