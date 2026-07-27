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
