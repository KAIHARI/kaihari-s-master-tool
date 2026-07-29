package com.kaiharimoto.mastertool.core.layout

/**
 * How far the cards either side of a drop lean away from it.
 *
 * On a table you do not put a card *onto* a row, you push two cards apart and
 * slide it between them — and you can see the slot before you let go. A marker
 * drawn on a seam says the same thing in software's voice; this says it in the
 * cards' own. The two together are a slot opening rather than a line appearing.
 *
 * Deliberately a shift and not a reflow. At a zero gutter every card in a
 * section touches its neighbours, so genuinely making a card's width of room
 * would push the last card of the row onto the next one and rearrange the whole
 * pane under a hand that is still moving. What the cards need to say is *here*,
 * and a notch a third of a card wide says it.
 */
object MakeRoom {

    /**
     * How wide the notch opens, as a fraction of one card.
     *
     * Roughly a card's printed border either side. Wide enough to read as a gap
     * at a glance, narrow enough that the cards still look like a stack rather
     * than a spread hand.
     */
    const val OPENING = 0.34f

    /** How many cards on each side take part, the nearest leaning furthest. */
    const val REACH = 2

    /**
     * Signed fraction of a card width to move the card at [index].
     *
     * Negative is left, positive is right, zero for everything far enough away.
     * [insertAt] is an insertion point in `0..size`, the same coordinate the drop
     * resolver works in — "before the card currently at this index".
     *
     * Nothing leans off its own row, and a full row never drifts: the two sides
     * take the same reach, so whatever leans right is balanced by something
     * leaning left. Without that, a seam near the start of a row would push its
     * last card out past the edge of the pane, where it would be drawn cut in
     * half.
     */
    fun shiftFor(index: Int, insertAt: Int, columns: Int, size: Int): Float {
        if (columns <= 0 || size <= 0) return 0f
        if (index < 0 || index >= size) return 0f
        if (insertAt < 0 || insertAt > size) return 0f

        val row = insertAt / columns
        val rowStart = row * columns
        val rowEnd = minOf(rowStart + columns, size)
        if (index / columns != row) return 0f

        var before = minOf(REACH, insertAt - rowStart)
        var after = minOf(REACH, rowEnd - insertAt)

        // A row with empty space at the end of it has somewhere to lean into, so
        // the two sides are free to differ. A full one does not.
        if (rowEnd - rowStart == columns) {
            val shared = minOf(before, after)
            before = shared
            after = shared
        }

        val half = OPENING / 2f

        if (index >= insertAt) {
            val distance = index - insertAt
            if (distance >= after) return 0f
            return half * falloff(distance, after)
        }

        val distance = insertAt - 1 - index
        if (distance >= before) return 0f
        return -half * falloff(distance, before)
    }

    /** One at the seam, tapering to nothing at the far end of the reach. */
    private fun falloff(distance: Int, reach: Int): Float =
        if (reach <= 0) 0f else 1f - distance.toFloat() / reach
}
