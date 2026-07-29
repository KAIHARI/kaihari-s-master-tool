package com.kaiharimoto.mastertool.core.visual

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Whether writing can be read off what is behind it.
 *
 * Here because it kept being decided by eye, and twice by eye *after* the fact:
 * a deck can be laid out on cloth lighter than the application is wearing, and
 * both times a caption came out the colour of the surface it was sitting on.
 * Looking at a screenshot found it; nothing would have found it again.
 *
 * The relative-luminance formula is the one every accessibility guideline uses,
 * and its two constants are the whole of it — 0.03928 is where the transfer
 * curve stops being linear, and the weights are how much of perceived brightness
 * each channel carries. Neither is a number to invent, so both are transcribed.
 */
object Contrast {

    /**
     * Perceived brightness of a colour, 0 for black and 1 for white.
     *
     * Not the average of the channels: green carries most of what the eye reads
     * as brightness and blue carries almost none, which is why a pure blue
     * background takes white text and a pure green one does not.
     */
    fun relativeLuminance(argb: Int): Double {
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * How far apart two colours are, from 1 (identical) to 21 (black on white).
     *
     * Order does not matter, which is what the max and min are for — "text on
     * background" and "background on text" are the same question.
     */
    fun ratio(a: Int, b: Int): Double {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    /** Body text, at the level a guideline would call passing. */
    const val READABLE = 4.5

    /** Large or incidental text — a count, a label, a caption beside one. */
    const val READABLE_LARGE = 3.0

    private fun channel(value: Int): Double {
        val scaled = value / 255.0
        return if (scaled <= 0.03928) scaled / 12.92 else ((scaled + 0.055) / 1.055).pow(2.4)
    }
}
