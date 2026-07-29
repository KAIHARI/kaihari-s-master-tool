package com.kaiharimoto.mastertool.core.export

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.siding.SidingPattern
import com.kaiharimoto.mastertool.core.siding.SidingSwap

/**
 * The siding sheet, which is the thing the original tool actually delivered.
 *
 * Not a screen. A piece of paper that sits beside a deck box between rounds,
 * read in the thirty seconds a player has after the die roll — which decides
 * everything about it. One matchup per block, going first above going second,
 * out on the left and in on the right, and a count on each side because the one
 * mistake that costs a game loss is siding four out and three in.
 *
 * Names are looked up rather than stored, because a plan holds passcodes and a
 * sheet is useless without words. A passcode the pool has never heard of prints
 * as its number, which is worth more than a blank line: it is still enough to
 * find the card.
 *
 * Copies are printed one per line rather than as "3x", the way the original's
 * sheets read. Siding out two of three is a different decision from siding out
 * all three, and a count hides which one was meant.
 */
object SidingSheet {

    fun render(
        deckName: String,
        patterns: List<SidingPattern>,
        nameOf: (CardId) -> String?,
    ): ByteArray = Pdf.document(pages(deckName, patterns, nameOf))

    private fun pages(
        deckName: String,
        patterns: List<SidingPattern>,
        nameOf: (CardId) -> String?,
    ): List<Pdf.Page> {
        val pages = ArrayList<Pdf.Page>()
        var page = Pdf.Page()
        var y = title(page, deckName, patterns.size)
        pages.add(page)

        patterns.forEach { pattern ->
            val needed = height(pattern)
            // A matchup split across a page break is a matchup you have to turn
            // the page to finish reading, thirty seconds into a round.
            if (y + needed > BOTTOM && y > FIRST_BLOCK) {
                page = Pdf.Page()
                pages.add(page)
                y = title(page, deckName, patterns.size, continued = true)
            }
            y = block(page, y, pattern, nameOf)
        }

        if (patterns.isEmpty()) {
            page.text(
                MARGIN,
                y,
                "No siding plans recorded for this deck.",
                size = 11f,
                grey = 0.35f,
            )
        }

        return pages
    }

    private fun title(page: Pdf.Page, deckName: String, count: Int, continued: Boolean = false): Float {
        page.rect(MARGIN, MARGIN, WIDTH, 30f, grey = 0.90f)
        // Fitted, like every other hand-typed name this writer puts on a page.
        val heading = Pdf.fit(deckName.ifBlank { "Untitled Deck" }, WIDTH - 20f)
        page.text(MARGIN + 10f, MARGIN + 8f, heading.text, size = heading.size, bold = true)
        page.text(
            MARGIN + 10f,
            MARGIN + 38f,
            if (continued) "SIDING SHEET, CONTINUED" else "SIDING SHEET  ·  $count MATCHUP${if (count == 1) "" else "S"}",
            size = 8f,
            grey = 0.40f,
        )
        return FIRST_BLOCK
    }

    /** One matchup: its name, then both sides of the die roll. */
    private fun block(
        page: Pdf.Page,
        top: Float,
        pattern: SidingPattern,
        nameOf: (CardId) -> String?,
    ): Float {
        var y = top

        page.line(MARGIN, y, MARGIN + WIDTH, y, width = 1f, grey = 0.25f)
        y += 6f
        val matchup = Pdf.fit(pattern.deckName.ifBlank { "Unnamed matchup" }, WIDTH, largest = 12f)
        page.text(MARGIN, y, matchup.text, size = matchup.size, bold = true)
        y += 20f

        y = half(page, y, "GOING FIRST", pattern.goingFirst, nameOf)
        y = half(page, y, "GOING SECOND", pattern.goingSecond, nameOf)

        return y + BLOCK_GAP
    }

    private fun half(
        page: Pdf.Page,
        top: Float,
        label: String,
        swap: SidingSwap,
        nameOf: (CardId) -> String?,
    ): Float {
        var y = top

        page.text(MARGIN, y, label, size = 8f, bold = true, grey = 0.35f)
        // The count that stops a game loss: four out and three in is a deck of
        // thirty-nine, and nothing else on the sheet would show it.
        page.text(
            MARGIN + WIDTH - 90f,
            y,
            "${swap.cardsOut.size} OUT / ${swap.cardsIn.size} IN" +
                if (swap.isBalanced) "" else "   UNEVEN",
            size = 8f,
            bold = !swap.isBalanced,
            grey = if (swap.isBalanced) 0.35f else 0f,
        )
        y += 13f

        if (swap.isEmpty) {
            page.text(MARGIN + 8f, y, "No changes.", size = 9.5f, grey = 0.45f)
            return y + ROW + HALF_GAP
        }

        val rows = maxOf(swap.cardsOut.size, swap.cardsIn.size)
        repeat(rows) { row ->
            // A faint stripe every other row, which is the cheapest way to keep
            // an eye on the correct line across a wide page.
            if (row % 2 == 1) page.rect(MARGIN, y - 2f, WIDTH, ROW, grey = 0.96f)

            swap.cardsOut.getOrNull(row)?.let {
                page.text(MARGIN + 8f, y, "-  " + label(it, nameOf), size = 9.5f)
            }
            swap.cardsIn.getOrNull(row)?.let {
                page.text(MARGIN + COLUMN, y, "+  " + label(it, nameOf), size = 9.5f)
            }
            y += ROW
        }

        return y + HALF_GAP
    }

    /** A card's name, or its passcode when the pool has never heard of it. */
    private fun label(id: CardId, nameOf: (CardId) -> String?): String =
        nameOf(id)?.takeIf { it.isNotBlank() } ?: id.value.toString()

    /** How tall a matchup will be, so it is not split across a page. */
    private fun height(pattern: SidingPattern): Float {
        fun halfHeight(swap: SidingSwap): Float {
            val rows = maxOf(swap.cardsOut.size, swap.cardsIn.size, 1)
            return 13f + rows * ROW + HALF_GAP
        }
        return 26f + halfHeight(pattern.goingFirst) + halfHeight(pattern.goingSecond) + BLOCK_GAP
    }

    private const val MARGIN = 36f
    private const val WIDTH = Pdf.PAGE_WIDTH - MARGIN * 2
    private const val COLUMN = WIDTH / 2 + 8f
    private const val ROW = 13f
    private const val HALF_GAP = 10f
    private const val BLOCK_GAP = 14f
    private const val FIRST_BLOCK = 86f
    private const val BOTTOM = Pdf.PAGE_HEIGHT - MARGIN
}
