package com.kaiharimoto.mastertool.core.export

import com.kaiharimoto.mastertool.core.deck.DeckGrouping
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection

/**
 * The decklist you hand a judge.
 *
 * A different piece of paper from the siding sheet and almost its opposite. That
 * one is read by its owner in the thirty seconds after a die roll; this one is
 * read by somebody who has never seen the deck, is checking it against the cards
 * in a box, and needs to be able to add it up. So: counts rather than a line per
 * copy, and grouped by card type, because that is the shape every registration
 * sheet has ever had and a judge should not have to hunt.
 *
 * Inside each type the deck's own order is kept. Sorting alphabetically would be
 * marginally faster to read and would throw away the one thing this program
 * exists to preserve — and a judge counts a type block, not a name.
 *
 * The blank fields at the top are the point of printing it at all: player, event
 * and table are filled in by hand at the venue, which is why they are rules
 * rather than text.
 */
object DecklistSheet {

    fun render(
        deckName: String,
        deck: Deck,
        format: String,
        cardOf: (CardId) -> Card?,
    ): ByteArray = Pdf.document(pages(deckName, deck, format, cardOf))

    private fun pages(
        deckName: String,
        deck: Deck,
        format: String,
        cardOf: (CardId) -> Card?,
    ): List<Pdf.Page> {
        val lines = lines(deck, cardOf)

        val pages = ArrayList<Pdf.Page>()
        var page = Pdf.Page()
        pages.add(page)
        var columnTop = heading(page, deckName, format, deck)
        var y = columnTop
        var column = 0
        var index = 0

        // How far down a column may run before the rest goes beside it.
        //
        // Half of what is left, rather than the bottom of the page: a decklist
        // that fits down one side leaves the other side blank, which reads as a
        // sheet somebody forgot to finish. Once there is more than two columns'
        // worth it becomes the bottom of the page anyway, and the balancing
        // happens again on whatever is left over.
        var limit = columnTop + balancePoint(lines, index, columnTop)

        while (index < lines.size) {
            val line = lines[index]

            // A heading at the very bottom of a column belongs to a list that is
            // not there, so it goes over with its list rather than alone.
            val orphaned = line.isHeading && y + HEAD_HEIGHT + ROW > limit
            // Nothing left to gain by moving: a line that will not fit at the top
            // of an empty column will not fit anywhere, and looking for somewhere
            // it does fit is how a page writer ends up in a loop on a deck
            // nobody here will ever see.
            val roomToMove = y > columnTop

            if ((y + line.height > limit || orphaned) && roomToMove) {
                column++
                if (column > 1) {
                    page = Pdf.Page()
                    pages.add(page)
                    columnTop = heading(page, deckName, format, deck, continued = true)
                    column = 0
                }
                y = columnTop
                if (column == 0) limit = columnTop + balancePoint(lines, index, columnTop)
                continue
            }

            draw(page, x = MARGIN + column * COLUMN, y = y, line = line)
            y += line.height
            index++
        }

        return pages
    }

    /** Half of what is left to draw, or the whole page when that is less. */
    private fun balancePoint(lines: List<Line>, from: Int, columnTop: Float): Float {
        val available = BOTTOM - columnTop
        var remaining = 0f
        for (at in from until lines.size) remaining += lines[at].height
        return minOf(available, remaining / 2f + HEAD_HEIGHT)
    }

    /** The band across the top, and the fields somebody writes in by hand. */
    private fun heading(
        page: Pdf.Page,
        deckName: String,
        format: String,
        deck: Deck,
        continued: Boolean = false,
    ): Float {
        page.rect(MARGIN, MARGIN, WIDTH, 30f, grey = 0.90f)

        // A deck name is typed by hand and people put the event and the version
        // in it. Written at a fixed size it ran through the counts on the right
        // and off the page -- on the one sheet in this program that is handed to
        // somebody else.
        val heading = Pdf.fit(deckName.ifBlank { "Untitled Deck" }, NAME_ROOM)
        page.text(MARGIN + 10f, MARGIN + 8f, heading.text, size = heading.size, bold = true)
        page.text(
            MARGIN + WIDTH - 150f,
            MARGIN + 10f,
            "$format   ${deck.main.size} / ${deck.extra.size} / ${deck.side.size}",
            size = 9f,
            bold = true,
            grey = 0.25f,
        )
        page.text(
            MARGIN + 10f,
            MARGIN + 38f,
            if (continued) "DECKLIST, CONTINUED" else "DECKLIST",
            size = 8f,
            grey = 0.40f,
        )

        if (continued) return FIRST_LINE - FIELDS_HEIGHT

        // Written in at the venue, so they are rules with a word under them.
        var x = MARGIN
        listOf("PLAYER" to 0.42f, "EVENT" to 0.34f, "TABLE" to 0.24f).forEach { (label, share) ->
            val width = WIDTH * share - 10f
            page.text(x, MARGIN + 60f, label, size = 7f, grey = 0.45f)
            page.line(x, MARGIN + 80f, x + width, MARGIN + 80f, width = 0.7f, grey = 0.45f)
            x += WIDTH * share
        }

        return FIRST_LINE
    }

    private fun draw(page: Pdf.Page, x: Float, y: Float, line: Line) {
        if (line.isHeading) {
            page.line(x, y + 2f, x + COLUMN - GUTTER, y + 2f, width = 1f, grey = 0.25f)
            page.text(x, y + 8f, line.text, size = 8.5f, bold = true, grey = 0.15f)
            line.count?.let {
                page.text(x + COLUMN - GUTTER - 22f, y + 8f, it.toString(), size = 8.5f, bold = true)
            }
            return
        }

        // Right-aligned by hand: one and two digit counts are the only cases, so
        // nudging the single digits across is the whole of it.
        val count = line.count ?: 0
        page.text(x + if (count >= 10) 0f else 5f, y, count.toString(), size = 9.5f, bold = true)
        page.text(x + 18f, y, line.text, size = 9.5f)
    }

    private fun lines(deck: Deck, cardOf: (CardId) -> Card?): List<Line> = buildList {
        val main = DeckGrouping.stacks(deck[DeckSection.MAIN])
        val typed = listOf(CardCategory.MONSTER, CardCategory.SPELL, CardCategory.TRAP)

        // The three type blocks a registration sheet has always had. Empty ones
        // are still printed with a nought, because a judge reading a sheet with
        // no trap block cannot tell it from a sheet that forgot to have one.
        listOf("MONSTERS", "SPELLS", "TRAPS").forEachIndexed { at, title ->
            val block = main.filter { cardOf(it.id)?.category == typed[at] }
            add(Line.heading(title, block.sumOf { it.count }))
            block.forEach { add(Line.entry(it.count, label(it.id, cardOf))) }
        }

        // Everything the three blocks did not take: a passcode the pool has
        // never heard of, and the handful of things it knows but does not call a
        // monster, spell or trap. They still shuffle and they still count
        // towards forty, and a sheet that quietly left them out would be a sheet
        // that disagrees with the deck box. Written as the complement rather
        // than as a fourth rule, so nothing can fall between the two.
        val rest = main.filter { cardOf(it.id)?.category !in typed }
        if (rest.isNotEmpty()) {
            add(Line.heading("NOT RECOGNISED", rest.sumOf { it.count }))
            rest.forEach { add(Line.entry(it.count, label(it.id, cardOf))) }
        }

        listOf(DeckSection.EXTRA to "EXTRA DECK", DeckSection.SIDE to "SIDE DECK").forEach { (section, title) ->
            val stacks = DeckGrouping.stacks(deck[section])
            add(Line.heading(title, stacks.sumOf { it.count }))
            stacks.forEach { add(Line.entry(it.count, label(it.id, cardOf))) }
        }
    }

    /** A card's name, or its passcode when the pool has never heard of it. */
    private fun label(id: CardId, cardOf: (CardId) -> Card?): String =
        cardOf(id)?.name?.takeIf { it.isNotBlank() } ?: id.value.toString()

    private class Line private constructor(
        val text: String,
        val count: Int?,
        val isHeading: Boolean,
    ) {
        val height: Float get() = if (isHeading) HEAD_HEIGHT else ROW

        companion object {
            fun heading(text: String, count: Int) = Line(text, count, isHeading = true)
            fun entry(count: Int, text: String) = Line(text, count, isHeading = false)
        }
    }

    private const val MARGIN = 36f
    private const val WIDTH = Pdf.PAGE_WIDTH - MARGIN * 2
    private const val GUTTER = 16f
    private const val COLUMN = WIDTH / 2

    /** From the left edge of the name to where the format and counts begin. */
    private const val NAME_ROOM = WIDTH - 160f
    private const val ROW = 12.5f
    private const val HEAD_HEIGHT = 26f
    private const val FIELDS_HEIGHT = 40f
    private const val FIRST_LINE = MARGIN + 102f
    private const val BOTTOM = Pdf.PAGE_HEIGHT - MARGIN
}
