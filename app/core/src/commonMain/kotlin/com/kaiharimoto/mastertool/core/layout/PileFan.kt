package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One card of a pile that has been spread out, and where it lies. */
data class FanCard(
    /** Its position in the pile, top first — the index the domain already takes. */
    val index: Int,
    val x: Float,
    val y: Float,
    val row: Int,
)

/**
 * A pile, spread out to be read.
 *
 * [bounds] is everything the spread covers, which is what says where the fan
 * stops and the table starts: a press inside it is about the fan even when it
 * lands between two cards, and a press outside it is the felt and belongs to
 * the camera as it always did.
 */
data class FanSpread(
    val cards: List<FanCard>,
    val rows: Int,
    val perRow: Int,
    /** How far apart two neighbours sit, in mat pixels. */
    val step: Float,
    val bounds: Slot,
)

/**
 * A card being carried over an open spread, so the spread can make room for it.
 *
 * kai's first report ends *"the fan needs to dynamically move and make space for
 * wherever the hovered card is going"*, and the reason is legibility rather than
 * arithmetic: a spread is laid out over `layout.field`, which is the seven-by-
 * three grid, so up to forty cards are drawn over the zone you are aiming at.
 * The resolver knows perfectly well which zone that is; you cannot see it.
 *
 * [x] and [y] are where the card will **land**, in mat pixels — not where the
 * finger is. A carried card is drawn up-table of the finger holding it, so a
 * window opened under the finger would open under the wrong thing.
 */
data class FanParting(
    val x: Float,
    val y: Float,
    /**
     * The slot this card came out of, which does not move.
     *
     * The hole a card leaves is what "put it back" is aimed at, and `FanHome`
     * fixes that target at the lift — deliberately, because a target that walks
     * away as you approach it is not a target. So the one slot the parting must
     * not slide is the one whose position something else has already written
     * down. It is not drawn either, being the card in your hand, so nothing is
     * lost by leaving it where it was.
     */
    val holding: Int? = null,
)

/**
 * Spreading a pile out across the table so a card can be taken out of the middle
 * of it.
 *
 * The one thing nothing in this app could do. `PlayField` has been able to play
 * an arbitrary card out of any pile since it was written — `playFromDeck(index,
 * …)` and its three siblings all take an index — and `DragOrigin.Pile` has
 * carried one end to end. But the hit test returned `Pile(slot, 0)` and nothing
 * else, so no gesture could name anything but the top card, and searching your
 * deck is the most common thing anybody does in this game.
 *
 * Geometry, solved in one pass, the way `DeckFit` and `BoardLayouter` are and
 * for the same reason: it is arithmetic that can be wrong, so it lives where a
 * test can hold it rather than inside a composable where it cannot.
 *
 * **The cards never change size.** A search shows you the same cards that are on
 * the table, so the only free variables are how far apart they sit and how many
 * rows they take. A deck that shrank its own cards to fit would be a picture of
 * a deck rather than the deck.
 */
object PileFan {

    /**
     * The most of a card's width between one card and the next, as a share of it.
     *
     * Just under three quarters. It was just over a half, and a half is enough to
     * *recognise* a card and not enough to read one: the name band and the
     * attribute survive, and everything below them — the art, the type line, the
     * numbers — is under the next card along. kai asked for a wider fan and this
     * is the number that answers it.
     *
     * Still short of one, because the overlap is what says these were a pile a
     * moment ago; a row of separate cards with gaps between them is a display,
     * not a search.
     */
    private const val MAX_STEP = 0.72f

    /**
     * The most cards in a row of a fan.
     *
     * Ten, which is a decklist row — the same ten the main deck pane is drawn at,
     * and for the same reason: it is how the list is read. It is also what makes
     * a forty-card deck spread as four rows rather than as two, which is what kai
     * asked for and which the comfort rule below could never have reached on its
     * own: at three rows the step is already at [MAX_STEP] and nothing is
     * uncomfortable enough to trigger a fourth.
     */
    private const val PER_ROW = 10

    /**
     * And the most rows, so a sixty-card deck spreads the same way a forty does.
     *
     * Four. Above it the rows are more overlapped than the cards — [ROW_STEP] is
     * the tighter of the two — so a fifth row costs more legibility than the
     * shorter rows buy. A pile that will not fit in four still gets more, but
     * only because the board made it: the escalation below is about the board
     * running out of width, and it is allowed to overrule this.
     */
    private const val MAX_ROWS = 4

    /**
     * How little of a card may show before another row is worth having instead.
     *
     * A third. Below that you are looking at a colour and a sliver of frame, and
     * the whole point of spreading a pile is to be able to pick a card out of it
     * by sight.
     */
    private const val COMFORT_STEP = 0.33f

    /**
     * And the floor, for a pile so large that no number of rows will hold it at
     * a comfortable step. Squeezing is better than running off the table.
     */
    private const val MIN_STEP = 0.14f

    /**
     * How far down the next row starts, as a share of a card's height.
     *
     * Less than one, so rows overlap too — a spread pile on a real table is not
     * a grid with alleys between it, and the overlap keeps a sixty-card deck
     * inside the felt instead of walking off the near edge.
     */
    private const val ROW_STEP = 0.58f

    /**
     * How much clear felt a parted window leaves round the card, as a share of
     * one — so a window is a card and a bit rather than exactly a card.
     */
    private const val PART_MARGIN = 0.12f

    /** How far in y a row has to be before it stops caring, in card heights. */
    private const val PART_ROW = 0.75f

    /**
     * [count] cards, laid out to fill [over] without leaving it.
     *
     * Two rules, in this order. **Ten to a row, four rows at most** — so a
     * graveyard of six is one row, a fifteen-card extra deck is two, and a deck
     * is four whether it holds forty cards or sixty. Then, underneath that, rows
     * are still *added* one at a time while the row before them is squeezed past
     * [COMFORT_STEP], which is what happens when the board is too narrow to hold
     * the shape the first rule asked for.
     *
     * The cards stay in the order they were given, which for the deck is the
     * order it is actually in.
     */
    fun spread(
        count: Int,
        over: Slot,
        cardWidth: Float,
        cardHeight: Float,
        parting: FanParting? = null,
    ): FanSpread {
        if (count <= 0 || cardWidth <= 0f || cardHeight <= 0f) {
            return FanSpread(emptyList(), 0, 0, 0f, Slot(over.centerX, over.centerY, 0f, 0f))
        }

        val rowStep = cardHeight * ROW_STEP
        val room = max(cardWidth, over.width)
        // How many rows the space could hold at all, whatever the pile wants.
        val ceilingRows = max(1, floor((over.height - cardHeight) / rowStep).toInt() + 1)

        fun stepFor(perRow: Int): Float =
            if (perRow <= 1) cardWidth * MAX_STEP
            else min(cardWidth * MAX_STEP, (room - cardWidth) / (perRow - 1))

        fun perRowFor(rows: Int): Int = ceil(count.toFloat() / rows).toInt().coerceAtLeast(1)

        // The shape asked for, then the shape the board will take. Escalation
        // still runs from there, because a board narrower than this expects is
        // the one case a row count chosen by taste has to give way.
        val wanted = min(MAX_ROWS, ceil(count.toFloat() / PER_ROW).toInt())
        var rows = wanted.coerceIn(1, ceilingRows)
        while (rows < ceilingRows && stepFor(perRowFor(rows)) < cardWidth * COMFORT_STEP) rows++

        val perRow = perRowFor(rows)
        val step = max(cardWidth * MIN_STEP, stepFor(perRow))
        val rowsUsed = ceil(count.toFloat() / perRow).toInt().coerceAtLeast(1)

        // Centred on the area, block and rows alike — a last row of three under
        // a first row of twenty is centred under it rather than left-aligned,
        // because a pile pushed together by a hand does not left-align.
        val block = cardHeight + (rowsUsed - 1) * rowStep
        val firstRowY = over.centerY - block / 2f + cardHeight / 2f

        fun widthOf(row: Int): Float {
            val n = min(perRow, count - row * perRow)
            return cardWidth + (n - 1) * step
        }

        val cards = (0 until count).map { index ->
            val row = index / perRow
            val place = index % perRow
            val left = over.centerX - widthOf(row) / 2f + cardWidth / 2f
            FanCard(index, left + place * step, firstRowY + row * rowStep, row)
        }

        val parted = parting?.let { part(cards, it, over, cardWidth, cardHeight, step) } ?: cards

        val widest = (0 until rowsUsed).maxOf { widthOf(it) }
        return FanSpread(
            cards = parted,
            rows = rowsUsed,
            perRow = perRow,
            step = step,
            // The **unparted** extent, on purpose. This is what says where the
            // fan stops and the table starts, and a footprint that breathed
            // every time a card moved over it would let go of a gesture the
            // moment the spread got out of that gesture's way.
            bounds = Slot(
                left = over.centerX - widest / 2f,
                top = firstRowY - cardHeight / 2f,
                width = widest,
                height = block,
            ),
        )
    }

    /**
     * The same cards, with a window opened where one is about to land.
     *
     * Row by row, and only for rows the carried card is actually over. Within a
     * row the cards split into the ones left of the window and the ones right of
     * it, and each side moves outward by the least that clears the window —
     * which it pays for in two currencies, in this order:
     *
     * 1. **Slack.** A row is centred in [over] and usually narrower than it, so
     *    there is room to slide before anything runs off the table. For a
     *    six-card graveyard that is three whole card widths and the window opens
     *    without anything else happening.
     * 2. **Overlap.** A full deck's rows fill the width and have almost no slack
     *    — a forty-card spread leaves 0.18 of a card — so the side squeezes its
     *    own cards closer together instead, down to [MIN_STEP]. That is what a
     *    hand does to a fanned pile, and it is why the window opens at all on
     *    the case that needs it most.
     *
     * Nothing here can reorder a row: both currencies only ever move a side
     * *away* from the window, and the compression is applied from the outer end
     * inward, so the order and the row's own extent are preserved.
     */
    private fun part(
        cards: List<FanCard>,
        parting: FanParting,
        over: Slot,
        cardWidth: Float,
        cardHeight: Float,
        step: Float,
    ): List<FanCard> {
        val half = cardWidth * (0.5f + PART_MARGIN)
        val minStep = cardWidth * MIN_STEP
        val moved = cards.toMutableList()

        cards.groupBy { it.row }.forEach { (_, row) ->
            if (row.isEmpty()) return@forEach
            if (abs(row.first().y - parting.y) > cardHeight * PART_ROW) return@forEach

            val movable = row.filter { it.index != parting.holding }
            if (movable.isEmpty()) return@forEach

            val rowLeft = row.minOf { it.x }
            val rowRight = row.maxOf { it.x }
            val slack = max(0f, (over.width - (rowRight - rowLeft) - cardWidth) / 2f)

            // Both sides are one piece of arithmetic read in a mirror: [dir] is
            // the way this side moves, and every comparison is written through
            // it so neither half can be fixed without the other.
            listOf(-1f, 1f).forEach { dir ->
                val side = movable.filter { if (dir < 0f) it.x <= parting.x else it.x > parting.x }
                if (side.isEmpty()) return@forEach

                // The card of this side nearest the window, and how far inside
                // the window it currently sits.
                val innerX = if (dir < 0f) side.maxOf { it.x } else side.minOf { it.x }
                val need = max(0f, half - dir * (innerX - parting.x))
                if (need <= 0f) return@forEach

                val shift = min(need, slack)
                val steps = (side.size - 1).coerceAtLeast(1)
                val closer = min(max(0f, need - shift) / steps, max(0f, step - minStep))

                // Outermost first, so the rank counts inward from the end that
                // takes the whole shift and none of the squeeze. The row's outer
                // edge therefore moves by exactly the slack it was allowed and
                // no further, and the spacing never falls below [MIN_STEP], so
                // no pair of cards can change places.
                side.sortedBy { -dir * it.x }.forEachIndexed { rank, card ->
                    moved[card.index] = card.copy(x = card.x + dir * (shift + rank * closer))
                }
            }
        }
        return moved
    }

    /**
     * Which card of the fan is under ([x], [y]), or null for the space between.
     *
     * Backwards, because a fan overlaps and the card *on top* is the one you are
     * pointing at — the same reason the mat is hit-tested in reverse. Later
     * cards are drawn over earlier ones, and later rows over earlier rows, so
     * the last one whose footprint covers the point is the one a finger means.
     */
    fun cardAt(
        spread: FanSpread,
        x: Float,
        y: Float,
        cardWidth: Float,
        cardHeight: Float,
    ): Int? {
        val halfWidth = cardWidth / 2f
        val halfHeight = cardHeight / 2f
        return spread.cards.lastOrNull {
            x >= it.x - halfWidth && x <= it.x + halfWidth &&
                y >= it.y - halfHeight && y <= it.y + halfHeight
        }?.index
    }
}
