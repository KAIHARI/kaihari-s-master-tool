package com.kaiharimoto.mastertool.ui.components

import kotlin.math.roundToInt

/**
 * Formats a 0..1 probability as a percentage with one decimal place.
 *
 * Hand-rolled because `String.format` is a JVM API and this module is common
 * code; one decimal is enough to tell 40 cards from 41 apart, which is the
 * decision these numbers exist to inform.
 */
internal fun percent(value: Double): String {
    val tenths = (value.coerceIn(0.0, 1.0) * 1000).roundToInt()
    return "${tenths / 10}.${tenths % 10}%"
}

/**
 * The same number with no decimal, for somewhere a decimal will not fit.
 *
 * Never rounds to a certainty it does not have: a deck that opens something
 * 99.7% of the time reads `>99%`, not `100%`, because `100%` is a promise and
 * the whole point of putting the number on screen is that it is honest. The
 * same rule at the other end — anything that can happen at all reads `<1%`
 * rather than `0%`.
 */
internal fun percentShort(value: Double): String {
    val clamped = value.coerceIn(0.0, 1.0)
    val whole = (clamped * 100).roundToInt()

    return when {
        clamped > 0.0 && whole == 0 -> "<1%"
        clamped < 1.0 && whole == 100 -> ">99%"
        else -> "$whole%"
    }
}
