package com.kaiharimoto.mastertool.core.layout

import kotlin.math.ceil

/**
 * How many columns to use, and whether that was enough.
 *
 * [fits] is load-bearing beyond layout: a pane whose contents fit has nothing to
 * scroll, so a drag starting on a card there cannot be confused with a scroll and
 * can begin the moment the finger moves.
 */
data class GridFit(val columns: Int, val fits: Boolean)

/**
 * Sizes a card grid so the whole section is visible at once.
 *
 * A deck is a fixed, small, known quantity — at most sixty cards — and the useful
 * default is to see all of it. Choosing a column count by hand means re-choosing
 * it every time the deck grows past a row, which is a chore the numbers can do:
 * take the fewest columns, and so the largest cards, that still let everything
 * fit in the space available.
 *
 * Falls back to the most columns allowed when nothing fits, which is the closest
 * it can get; the caller learns that from [GridFit.fits] rather than by
 * re-deriving it.
 */
object GridFitter {

    fun fit(
        count: Int,
        availableWidth: Float,
        availableHeight: Float,
        spacing: Float,
        /** Card width divided by card height. */
        aspectRatio: Float,
        minColumns: Int,
        maxColumns: Int,
    ): GridFit {
        val floor = minColumns.coerceAtLeast(1)
        val ceiling = maxColumns.coerceAtLeast(floor)

        if (count <= 0) return GridFit(floor, fits = true)
        if (availableWidth <= 0f || availableHeight <= 0f || aspectRatio <= 0f) {
            return GridFit(floor, fits = false)
        }

        for (columns in floor..ceiling) {
            if (requiredHeight(count, columns, availableWidth, spacing, aspectRatio) <= availableHeight) {
                return GridFit(columns, fits = true)
            }
        }

        return GridFit(ceiling, fits = false)
    }

    /** Height a [columns]-wide grid of [count] cards would take. */
    fun requiredHeight(
        count: Int,
        columns: Int,
        availableWidth: Float,
        spacing: Float,
        aspectRatio: Float,
    ): Float {
        if (count <= 0 || columns <= 0) return 0f

        val cardWidth = (availableWidth - spacing * (columns - 1)) / columns
        if (cardWidth <= 0f) return Float.MAX_VALUE

        val rows = ceil(count.toDouble() / columns).toInt()
        return rows * (cardWidth / aspectRatio) + spacing * (rows - 1)
    }
}
