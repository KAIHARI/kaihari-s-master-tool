package com.kaiharimoto.mastertool.core.layout

import kotlin.math.ceil

/**
 * One section, as it wants to be drawn: a row width the user chose, and the
 * capacity it should be sized for even before it holds that many cards.
 *
 * [chromeHeight] is everything in the pane that is not grid — its header, its
 * padding, the gap under the header. It is passed in rather than assumed
 * because only the UI knows how tall its own header is, and the whole point of
 * this fitter is that the number it returns leaves nothing overflowing.
 */
data class SectionFitRequest(
    val count: Int,
    val columns: Int,
    /** Size as though the section already held this many — see [DeckFitter]. */
    val baselineCount: Int,
    val collapsed: Boolean = false,
    val chromeHeight: Float = 0f,
)

/** A section's grid, in pixels: what to draw and how much room it takes. */
data class SectionFit(
    val columns: Int,
    val rows: Int,
    val cardWidth: Float,
    val cardHeight: Float,
    /** Width of the grid itself, which is narrower than the pane when scaled. */
    val gridWidth: Float,
    val gridHeight: Float,
    /** Grid plus chrome: the height the whole pane should be given. */
    val paneHeight: Float,
)

/**
 * The three panes, sized so all of them are on screen at once.
 *
 * [scale] is 1 when the sections fill the column's width; below 1 the deck is
 * taller than the space and every card shrank by the same factor.
 */
data class DeckFit(
    val scale: Float,
    val sections: List<SectionFit>,
    val totalHeight: Float,
    /** False when even [DeckFitter.MIN_SCALE] does not get it into the space. */
    val fits: Boolean,
)

/**
 * Sizes the whole deck column in one pass.
 *
 * [GridFitter] answers a different question — "how many columns make this
 * section fit on its own" — and answering it per pane is what put cards out of
 * bounds: three panes each sized against a height they were given by a divider
 * drag cannot agree on a total, and the main deck lost. Here the row widths are
 * fixed (ten for the main deck, fifteen for the extra and side, which is how
 * decklists are read), the row *counts* follow from the deck, and the one free
 * variable is how big a card is. Solving for that once, across all three
 * sections together, is what makes "everything visible, nothing clipped" a
 * property of the layout rather than something a drag can break.
 *
 * Card size is linear in the scale, so this is one division rather than a
 * search: at full width the stack needs a known height, and the scale is
 * whatever fraction of that the space allows.
 *
 * Sections keep their own column counts, so a main-deck card stays half again
 * the size of an extra-deck one — the hierarchy of the list, drawn rather than
 * labelled. The baseline is why an empty deck is not drawn as one enormous
 * card: the main deck is sized for forty from the start, so nothing reflows
 * until the deck actually outgrows it.
 */
object DeckFitter {

    /** Below this the cards are unreadable, so the column scrolls instead. */
    const val MIN_SCALE = 0.3f

    fun plan(
        requests: List<SectionFitRequest>,
        availableWidth: Float,
        availableHeight: Float,
        spacing: Float,
        /** Card width divided by card height. */
        aspectRatio: Float,
        /** Space between two panes — the dividers. */
        paneGap: Float = 0f,
        minScale: Float = MIN_SCALE,
    ): DeckFit {
        if (requests.isEmpty()) return DeckFit(1f, emptyList(), 0f, fits = true)

        val chrome = requests.sumOf { it.chromeHeight.toDouble() }.toFloat() +
            paneGap * (requests.size - 1)

        if (availableWidth <= 0f || aspectRatio <= 0f) {
            return DeckFit(1f, requests.map { EMPTY_SECTION }, chrome, fits = false)
        }

        // Full-width geometry first: rows are a property of the deck, not of the
        // space, so they are decided before anything is scaled.
        val rows = requests.map { request ->
            if (request.collapsed) {
                0
            } else {
                val columns = request.columns.coerceAtLeast(1)
                val capacity = maxOf(request.count, request.baselineCount)
                ceil(capacity.toDouble() / columns).toInt()
            }
        }
        val fullCardWidths = requests.map { request ->
            val columns = request.columns.coerceAtLeast(1)
            ((availableWidth - spacing * (columns - 1)) / columns).coerceAtLeast(0f)
        }
        // Only the cards scale. The gaps between rows are a constant of the
        // design — spacing that shrank with the cards would close up exactly
        // when a dense grid needs it most — so they come off the budget first
        // and the division is over what is left.
        val gaps = rows.sumOf { (spacing * maxOf(it - 1, 0)).toDouble() }.toFloat()
        val cardBudget = rows.indices
            .sumOf { (rows[it] * fullCardWidths[it] / aspectRatio).toDouble() }
            .toFloat()
        val scale = when {
            cardBudget <= 0f -> 1f
            else -> ((availableHeight - chrome - gaps) / cardBudget).coerceIn(minScale, 1f)
        }

        val sections = requests.mapIndexed { i, request ->
            val cardWidth = fullCardWidths[i] * scale
            val cardHeight = cardWidth / aspectRatio
            val columns = request.columns.coerceAtLeast(1)
            val gridHeight = gridHeight(rows[i], cardHeight, spacing)
            SectionFit(
                columns = columns,
                rows = rows[i],
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                gridWidth = columns * cardWidth + spacing * (columns - 1),
                gridHeight = gridHeight,
                paneHeight = gridHeight + request.chromeHeight,
            )
        }

        // Summed from what the panes were actually given, so the total is the
        // one the caller can lay out to rather than one it has to trust.
        val total = chrome + sections.sumOf { it.gridHeight.toDouble() }.toFloat()
        // A hair of tolerance: these are float pixels, and a total that lands a
        // thousandth over the space is not a layout that failed.
        return DeckFit(scale, sections, total, fits = total <= availableHeight + 0.5f)
    }

    private fun gridHeight(rows: Int, cardHeight: Float, spacing: Float): Float =
        if (rows <= 0) 0f else rows * cardHeight + spacing * (rows - 1)

    private val EMPTY_SECTION = SectionFit(
        columns = 1,
        rows = 0,
        cardWidth = 0f,
        cardHeight = 0f,
        gridWidth = 0f,
        gridHeight = 0f,
        paneHeight = 0f,
    )
}
