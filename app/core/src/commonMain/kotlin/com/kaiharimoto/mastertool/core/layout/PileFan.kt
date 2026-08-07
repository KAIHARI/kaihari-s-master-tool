package com.kaiharimoto.mastertool.core.layout

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
     * Just over half, so a fanned pile reads as *cards overlapping* rather than
     * as a row of separate cards with gaps: enough of each one shows to know
     * what it is, and the overlap is what says these were one pile a moment ago.
     */
    private const val MAX_STEP = 0.52f

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
     * [count] cards, laid out to fill [over] without leaving it.
     *
     * Rows are added one at a time and only when the row before them has been
     * squeezed past [COMFORT_STEP] — so a graveyard of six is one row, a
     * fifteen-card extra deck is one row, and a forty-card deck is two. The
     * cards stay in the order they were given, which for the deck is the order
     * it is actually in.
     */
    fun spread(count: Int, over: Slot, cardWidth: Float, cardHeight: Float): FanSpread {
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

        var rows = 1
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

        val widest = (0 until rowsUsed).maxOf { widthOf(it) }
        return FanSpread(
            cards = cards,
            rows = rowsUsed,
            perRow = perRow,
            step = step,
            bounds = Slot(
                left = over.centerX - widest / 2f,
                top = firstRowY - cardHeight / 2f,
                width = widest,
                height = block,
            ),
        )
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
