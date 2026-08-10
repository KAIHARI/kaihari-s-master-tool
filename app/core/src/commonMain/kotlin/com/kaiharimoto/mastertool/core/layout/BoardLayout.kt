package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.board.FieldZone
import kotlin.math.min

/** A rectangle on the table, in pixels from the surface's top-left corner. */
data class Slot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** The same rectangle grown by [by] on every side. */
    fun inflated(by: Float) = Slot(left - by, top - by, width + by * 2f, height + by * 2f)
}

/**
 * Somewhere on one side of the table a card can be.
 *
 * The five piles are separate from the field zones because [FieldZone] is the
 * part the rules care about — a card *in* a zone is on the field — and a deck,
 * a graveyard or a banish pile is somewhere cards are kept rather than played.
 * The game draws that line itself; this follows it rather than inventing a
 * second one.
 */
sealed interface BoardSlot {
    data class Zone(val zone: FieldZone) : BoardSlot
    data object Deck : BoardSlot
    data object ExtraDeck : BoardSlot
    data object Graveyard : BoardSlot
    data object Banished : BoardSlot
}

/**
 * Where everything on one side of the table sits.
 *
 * [fits] is false when the surface is too small to draw a readable card, which
 * is the one case the caller has to decide about; every other case is simply
 * smaller.
 */
data class BoardLayout(
    val cardWidth: Float,
    val cardHeight: Float,
    val gap: Float,
    val slots: Map<BoardSlot, Slot>,
    /** The band the hand is fanned across. Not a slot: a fan is not a grid. */
    val hand: Slot,
    /** A line above the hand, for saying what is in it. */
    val readout: Slot,
    /** The three rows of zones, without the hand — what the table's felt covers. */
    val field: Slot,
    val fits: Boolean,
) {
    /**
     * Everything the table occupies, field and hand together.
     *
     * `this.field` rather than `field`, and it has to stay that way: inside a
     * property accessor `field` is the soft keyword for the backing field, so
     * the unqualified name silently means something else here than it does
     * three lines up.
     */
    val bounds: Slot
        get() = Slot(
            left = this.field.left,
            top = this.field.top,
            width = this.field.width,
            height = hand.bottom - this.field.top,
        )

    operator fun get(slot: BoardSlot): Slot? = slots[slot]

    operator fun get(zone: FieldZone): Slot? = slots[BoardSlot.Zone(zone)]

    /**
     * Which slot a point lands in, for a drop.
     *
     * Each slot claims the gap around it, so the lanes between zones are not
     * dead space a card can be dropped into and lost. Ties cannot happen: the
     * inflation is half the gap, so two neighbours meet exactly on the midline
     * and the first match in a fixed order wins it.
     */
    fun slotAt(x: Float, y: Float): BoardSlot? {
        slots.entries.firstOrNull { it.value.contains(x, y) }?.let { return it.key }
        return slots.entries.firstOrNull { it.value.inflated(gap / 2f).contains(x, y) }?.key
    }
}

/**
 * The duel table's geometry, solved the way the deck panes are.
 *
 * The same discipline as `DeckFit`, for the same reason. The columns are the
 * input — seven of them, because that is the layout printed on every official
 * mat and the one already in the player's head — the rows follow from the game,
 * and the single free variable is the size a card is drawn at. Negotiating any
 * of that inside a composable is what put deck cards out of their box twice,
 * and a table has four times as many places to get it wrong.
 *
 * The hand is part of the budget rather than something drawn afterwards over
 * whatever is left. That is the same lesson as the lens bar: space the layout
 * spends without telling the solver is space the cards were promised and did
 * not get.
 *
 * ```
 *                   [EMZ]        [EMZ]            [Banished]
 *   [Field spell]  [M1][M2][M3][M4][M5]           [Graveyard]
 *   [Extra deck]   [S1][S2][S3][S4][S5]           [Deck]
 *                  2 Starter · 1 Handtrap · 2 loose
 *                       — the hand, fanned —
 * ```
 */
object BoardLayouter {

    /** Field spell / extra deck, five zones, graveyard / deck. */
    const val COLUMNS = 7

    /** Extra monster zones, monsters, spells and traps. */
    const val ROWS = 3

    /**
     * The lane between two zones, as a fraction of a card's width.
     *
     * Chosen to look like a mat rather than to prevent overlap. A monster set
     * in defence is a card turned on its side, and a turned card is half its
     * own height wider than the zone holding it — no gap short of absurd keeps
     * it clear of its neighbours. Real mats do not try: defenders overlap, and
     * that overlap is one of the ways a board reads at a glance.
     */
    private const val GAP_FRACTION = 0.11f

    /** How far the hand sits below the field, in gaps. Off the table, not on it. */
    private const val HAND_GAP_FRACTION = 2.5f

    /**
     * The band that says what the hand is, as a fraction of a card's height.
     *
     * In the budget rather than drawn into whatever happened to be left over.
     * That rule has now been learned twice — once by the deck panes and once by
     * the lens bar — and it is cheaper to obey it the first time here.
     */
    private const val READOUT_FRACTION = 0.30f

    /**
     * Below this a card is a coloured rectangle rather than a card.
     *
     * The same stance the deck fitter takes: report that it does not fit and
     * let the caller decide, rather than drawing something unreadable and
     * calling it a layout.
     */
    const val MIN_CARD_WIDTH = 28f

    /** Which columns the two extra monster zones sit above: over M2 and M4. */
    private val EXTRA_MONSTER_COLUMNS = listOf(2, 4)

    /**
     * @param perspectiveGrowth how much larger the near edge will be once the
     *   table is tilted, as a multiplier. A tilted plane is drawn bigger at the
     *   bottom than it was laid out, so a layout solved to exactly fill the
     *   surface would spill off it the moment perspective was switched on. The
     *   caller doing the tilting knows the number; the solver is simply handed
     *   less room. Pass 1 for a flat table.
     */
    fun solve(
        width: Float,
        height: Float,
        aspectRatio: Float,
        perspectiveGrowth: Float = 1f,
        /**
         * The fraction of the stage's height kept clear above the board, for
         * the room to be seen in.
         *
         * Zero — every caller before the desk became a place — means the board
         * fills the stage vertically, and that is why there were six pixels of
         * wall at the table seat and no window anybody has ever seen. Nothing
         * behind the desk can be looked at until something declines to use the
         * space, and the board is the only thing big enough to be asked.
         *
         * It costs card size, and there is no arrangement where it does not:
         * the board is height-constrained on every device this ships to, so a
         * band reserved at the top comes straight off the cards. That is the
         * trade, stated rather than hidden — a room you can see, for cards a
         * few per cent smaller.
         */
        roomAbove: Float = 0f,
    ): BoardLayout {
        val ratio = if (aspectRatio > 0f) aspectRatio else 1f
        val growth = perspectiveGrowth.coerceAtLeast(1f)
        // Clamped rather than trusted: a caller asking for most of the screen
        // would solve a board of nothing and report that it fits.
        val reserved = roomAbove.coerceIn(0f, 0.4f)
        val usable = height * (1f - reserved)

        // Width:  7 cards + 6 lanes.
        // Height: 3 rows of card + 2 lanes, then the hand's own lane and a
        //         fourth card's worth of band for the fan to sit in.
        val byWidth = (width / growth) / (COLUMNS + (COLUMNS - 1) * GAP_FRACTION)
        val byHeight = (usable / growth) / (
            (ROWS + 1 + READOUT_FRACTION) / ratio +
                ((ROWS - 1) + HAND_GAP_FRACTION + 1) * GAP_FRACTION
            )

        val cardWidth = min(byWidth, byHeight).coerceAtLeast(0f)
        val cardHeight = cardWidth / ratio
        val gap = cardWidth * GAP_FRACTION

        val pitchX = cardWidth + gap
        val pitchY = cardHeight + gap

        // Centred in whatever is left, so a table on a wider screen sits in the
        // middle of it rather than pinned to a corner — the same answer the
        // deck stack gives to the same question.
        val fieldWidth = COLUMNS * cardWidth + (COLUMNS - 1) * gap
        val fieldHeight = ROWS * cardHeight + (ROWS - 1) * gap
        val handHeight = cardHeight
        val readoutHeight = cardHeight * READOUT_FRACTION
        val handGap = gap * HAND_GAP_FRACTION
        val stackHeight = fieldHeight + handGap + readoutHeight + gap + handHeight
        val originX = (width - fieldWidth) / 2f
        // Centred in what is left, then pushed down past the reserved band —
        // so the room is above the desk, which is where a room is.
        val originY = height * reserved + ((usable - stackHeight) / 2f).coerceAtLeast(0f)
        val readoutTop = originY + fieldHeight + handGap

        fun slot(column: Int, row: Int) = Slot(
            left = originX + column * pitchX,
            top = originY + row * pitchY,
            width = cardWidth,
            height = cardHeight,
        )

        val slots = buildMap {
            EXTRA_MONSTER_COLUMNS.forEachIndexed { index, column ->
                put(BoardSlot.Zone(FieldZone.ExtraMonster(index)), slot(column, 0))
            }
            put(BoardSlot.Banished, slot(COLUMNS - 1, 0))

            put(BoardSlot.Zone(FieldZone.FieldSpell), slot(0, 1))
            repeat(5) { put(BoardSlot.Zone(FieldZone.Monster(it)), slot(it + 1, 1)) }
            put(BoardSlot.Graveyard, slot(COLUMNS - 1, 1))

            put(BoardSlot.ExtraDeck, slot(0, 2))
            repeat(5) { put(BoardSlot.Zone(FieldZone.SpellTrap(it)), slot(it + 1, 2)) }
            put(BoardSlot.Deck, slot(COLUMNS - 1, 2))
        }

        return BoardLayout(
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            gap = gap,
            slots = slots,
            hand = Slot(
                left = originX,
                top = readoutTop + readoutHeight + gap,
                width = fieldWidth,
                height = handHeight,
            ),
            readout = Slot(
                left = originX,
                top = readoutTop,
                width = fieldWidth,
                height = readoutHeight,
            ),
            field = Slot(originX, originY, fieldWidth, fieldHeight),
            fits = cardWidth >= MIN_CARD_WIDTH,
        )
    }
}
