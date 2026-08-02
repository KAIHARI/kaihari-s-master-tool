package com.kaiharimoto.mastertool.core.haptics

/**
 * One buzz: how hard, how long, and how long of a silence after it.
 *
 * [amplitude] is 0..1 rather than a motor level, because the two platforms
 * disagree about what a motor level is and neither of them is a fact about the
 * gesture. Something happening at half strength is.
 */
data class HapticPulse(
    val amplitude: Float,
    val durationMs: Int,
    val gapMs: Int = 0,
) {
    /** 0..255, which is the range Android's waveform API wants. */
    val motorLevel: Int get() = (amplitude.coerceIn(0f, 1f) * 255f).toInt()
}

/**
 * A shape a card makes against your hand.
 *
 * Kept as data rather than as calls into a platform API so the vocabulary can
 * be read, tested and argued about in one place. Two devices with completely
 * different actuators should be reproducing the same *rhythm*; the part that
 * differs is only how each of them gets there.
 */
data class HapticPattern(val pulses: List<HapticPulse>) {

    val totalMillis: Int get() = pulses.sumOf { it.durationMs + it.gapMs }

    /**
     * Durations for Android's `VibrationEffect.createWaveform`, index-aligned
     * with [motorLevels]: a pulse, then its gap as a zero-amplitude entry.
     */
    fun timings(): List<Long> = buildList {
        pulses.forEach { pulse ->
            add(pulse.durationMs.toLong())
            if (pulse.gapMs > 0) add(pulse.gapMs.toLong())
        }
    }

    fun motorLevels(): List<Int> = buildList {
        pulses.forEach { pulse ->
            add(pulse.motorLevel)
            if (pulse.gapMs > 0) add(0)
        }
    }

    companion object {
        fun single(amplitude: Float, durationMs: Int) =
            HapticPattern(listOf(HapticPulse(amplitude, durationMs)))
    }
}

/**
 * The haptic vocabulary: everything the table can say to a hand.
 *
 * It follows the sounds, on the same toggle, for the same reason — the app has
 * exactly one idea of when the user's hand did something, and sound and
 * vibration are two ways of answering it rather than two separate features.
 * Where a sound and a haptic disagree, the haptic wins: it is the one that
 * arrives even when the tablet is muted, which is how most people play.
 *
 * [crispness] is the axis the modern Android API actually exposes — a tick is
 * sharp, a thud is soft — so it is stored rather than inferred. Platforms
 * without that API fall back to [pattern], which describes the same event as
 * durations and amplitudes and is what every actuator can do.
 *
 * Two rules the list obeys, and both are about restraint. Nothing here is
 * longer than a card sound (a haptic you can still feel after you have stopped
 * thinking about the gesture reads as a malfunction), and nothing fires on
 * anything the *app* did — only on things the hand did. A table that buzzes
 * when a turn counter increments is a phone, not a table.
 */
enum class Haptic(
    val strength: Float,
    val crispness: Float,
    val pattern: HapticPattern,
) {
    /** A card comes off the felt: the softest thing in the set. */
    LIFT(
        strength = 0.34f,
        crispness = 0.2f,
        pattern = HapticPattern.single(0.34f, 18),
    ),

    /** It goes back down. Firmer than the lift — landing is the event. */
    LAND(
        strength = 0.68f,
        crispness = 0.85f,
        pattern = HapticPattern.single(0.68f, 22),
    ),

    /** It lands on top of another card, so there is a second surface. */
    STACK(
        strength = 0.6f,
        crispness = 0.7f,
        pattern = HapticPattern(
            listOf(HapticPulse(0.6f, 16, gapMs = 22), HapticPulse(0.26f, 12)),
        ),
    ),

    /** Sliding, turning in place: felt continuously, so barely felt at all. */
    SLIDE(
        strength = 0.22f,
        crispness = 0.45f,
        pattern = HapticPattern.single(0.22f, 12),
    ),

    /**
     * Crossing a rotation detent.
     *
     * The sharpest and shortest, because it is the only haptic in the set that
     * is reporting a *boundary* rather than a contact — and a boundary you can
     * feel is how a twist gesture becomes something you can do without looking.
     */
    DETENT(
        strength = 0.5f,
        crispness = 1f,
        pattern = HapticPattern.single(0.5f, 9),
    ),

    /** Turning a card over: the two ends of one motion. */
    FLIP(
        strength = 0.42f,
        crispness = 0.6f,
        pattern = HapticPattern(
            listOf(HapticPulse(0.3f, 10, gapMs = 26), HapticPulse(0.5f, 14)),
        ),
    ),

    /** A card off the top of the deck. */
    DEAL(
        strength = 0.46f,
        crispness = 0.9f,
        pattern = HapticPattern.single(0.46f, 13),
    ),

    /**
     * A riffle: five taps that speed up and fade.
     *
     * The one deliberately long pattern, and it earns the exception by being
     * the one gesture that is genuinely long. A shuffle that answered with a
     * single tap would feel like the deck had refused.
     */
    SHUFFLE(
        strength = 0.5f,
        crispness = 0.8f,
        pattern = HapticPattern(
            listOf(
                HapticPulse(0.50f, 10, gapMs = 34),
                HapticPulse(0.44f, 9, gapMs = 28),
                HapticPulse(0.38f, 8, gapMs = 23),
                HapticPulse(0.30f, 8, gapMs = 19),
                HapticPulse(0.22f, 7),
            ),
        ),
    ),

    /** A card held up to be read — it leaves the table under your finger. */
    PEEK(
        strength = 0.28f,
        crispness = 0.15f,
        pattern = HapticPattern.single(0.28f, 24),
    ),
}

/**
 * Which haptic an event on the mat deserves, in one place.
 *
 * The mapping lives here rather than at each call site because the interesting
 * failure is not "the wrong buzz played", it is *drift*: half the table saying
 * [LAND] on a release and the other half saying [SLIDE], which nobody notices
 * one call at a time and everybody feels as the table being inconsistent.
 */
object HapticScore {

    /** A release, given whether it landed on something. */
    fun landing(ontoCard: Boolean): Haptic = if (ontoCard) Haptic.STACK else Haptic.LAND
}
