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

    /**
     * One column count for several grids stacked one above another.
     *
     * The whole deck at once, which is a different question from three sections
     * each sized on their own: the Main, Extra and Side share a card size, so
     * they share a column count, and the answer is the fewest columns that lets
     * *everything* fit. Sizing them separately would give the Side deck's
     * fifteen cards a different card size from the Main's forty, and a deck laid
     * out at two scales does not read as one deck.
     *
     * [gaps] is whatever space sits between the blocks, since it comes out of
     * the same height budget the cards are competing for.
     */
    fun fitAll(
        counts: List<Int>,
        availableWidth: Float,
        availableHeight: Float,
        spacing: Float,
        gaps: Float,
        aspectRatio: Float,
        minColumns: Int,
        maxColumns: Int,
    ): GridFit {
        val floor = minColumns.coerceAtLeast(1)
        val ceiling = maxColumns.coerceAtLeast(floor)

        val present = counts.filter { it > 0 }
        if (present.isEmpty()) return GridFit(floor, fits = true)
        if (availableWidth <= 0f || availableHeight <= 0f || aspectRatio <= 0f) {
            return GridFit(floor, fits = false)
        }

        val budget = availableHeight - gaps
        if (budget <= 0f) return GridFit(ceiling, fits = false)

        for (columns in floor..ceiling) {
            val total = present.sumOf {
                requiredHeight(it, columns, availableWidth, spacing, aspectRatio).toDouble()
            }
            if (total <= budget) return GridFit(columns, fits = true)
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

        val cardWidth = cardWidth(availableWidth, columns, spacing)
        if (cardWidth <= 0f) return Float.MAX_VALUE

        val rows = ceil(count.toDouble() / columns).toInt()
        return rows * (cardWidth / aspectRatio) + spacing * (rows - 1)
    }

    /**
     * How wide one card is once [columns] of them share [availableWidth].
     *
     * Pulled out because two things need it and they must agree exactly: this
     * decides whether a section fits, and the empty pane draws its slots at the
     * size cards will actually be. A second copy of the formula would show up as
     * ghost outlines that the first real card does not sit inside.
     */
    fun cardWidth(availableWidth: Float, columns: Int, spacing: Float): Float =
        if (columns <= 0) 0f else (availableWidth - spacing * (columns - 1)) / columns
}
