package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.board.MatPoint
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the cards in your hand sit, and which gap between them a finger is over.
 *
 * A row that fans rather than an arc that curls: an arc is lovely at six cards
 * and unreadable at fourteen, and a combo line routinely holds fourteen. The
 * cards overlap only as far as they must, so the common case still bows.
 *
 * It is in core, and it did not used to be. [pointFor] lived beside the stage's
 * pose builder with a KDoc worrying that its two readers — the pose and the hit
 * box — could drift apart, and defaulting its step so that adding a third reader
 * would be a compile error. The third reader arrived: dragging a card *within*
 * the hand needs the inverse, [insertAt], and an inverse written against a
 * remembered copy of a layout is exactly the drift that KDoc was afraid of. One
 * solved layout, three readings, none of them able to disagree — the argument
 * `DeckFit` makes for the builder's panes, at the scale of a hand.
 */
object HandFan {

    /**
     * How far apart two neighbours sit, in mat pixels.
     *
     * The step is the only free variable: the band and the card size are given,
     * and the count is whatever is in your hand. Capped at [stepFraction] of a
     * card so a hand of two does not fling its cards to opposite ends of the
     * band, and squeezed below it only when the band runs out of room.
     */
    fun step(band: Slot, cardWidth: Float, count: Int, stepFraction: Float): Float =
        if (count <= 1) 0f else min(cardWidth * stepFraction, (band.width - cardWidth) / (count - 1))

    /** The centre of hand card [index] of [count], in mat pixels. */
    fun centreOf(
        band: Slot,
        cardWidth: Float,
        index: Int,
        count: Int,
        stepFraction: Float,
    ): Float {
        val step = step(band, cardWidth, count, stepFraction)
        val spread = cardWidth + step * (count - 1)
        return band.left + (band.width - spread) / 2f + cardWidth / 2f + index * step
    }

    /**
     * Which position in the hand a card released at [x] is asking to take.
     *
     * The inverse of [centreOf], answering in *gaps* rather than in cards: zero
     * is before everything, [count] is after everything, and a card released
     * over the middle of the third card is asking to become the third card. That
     * is the number `List.add(index, …)` takes, which is the whole reason it is
     * phrased this way — an answer in "which card is nearest" needs a
     * before-or-after decision at every call site, and one of them would get it
     * wrong.
     *
     * [moving] is the index of the card being dragged, when the drag started in
     * the hand. It is left out of the arithmetic on purpose: a hand of five with
     * the second card in the air is a hand of four, drawn as four, and asking
     * where the finger is against the five slots the card came from would put
     * every answer half a gap out. Null when the card is arriving from somewhere
     * else, which is the case where the hand really does have [count] cards in
     * it and is about to have one more.
     */
    fun insertAt(
        band: Slot,
        cardWidth: Float,
        count: Int,
        x: Float,
        stepFraction: Float,
        moving: Int? = null,
    ): Int {
        val shown = if (moving != null) count - 1 else count
        if (shown <= 0 || cardWidth <= 0f) return 0

        val step = step(band, cardWidth, shown, stepFraction)
        val first = centreOf(band, cardWidth, 0, shown, stepFraction)

        // Half a step off the first card's centre is the boundary before it, so
        // the arithmetic is "how many boundaries have I passed" and rounding is
        // the whole of it. With one card in the row the step is zero and there
        // is nothing to divide by: the two answers are before it and after it.
        val gap = if (step <= 0f) {
            if (x < first) 0 else 1
        } else {
            ((x - first) / step).roundToInt()
        }

        val among = gap.coerceIn(0, shown)
        // Back into the full hand's numbering. A card moving to a gap after its
        // own old position lands one further along than the gap it was aimed at,
        // because everything past it shifted down when it left.
        return if (moving != null && among >= moving) among + 1 else among
    }

    /**
     * Where hand card [index] of [count] sits, in the mat's own fractions.
     *
     * The stage's reading, kept in the shape the pose builder and the hit box
     * already use: a [MatPoint] against `layout.field`, so a hand card is a
     * point on the same plane as everything else on the table even though the
     * band it is laid out in sits below the field.
     */
    fun pointFor(
        layout: BoardLayout,
        index: Int,
        count: Int,
        stepFraction: Float,
    ): MatPoint {
        val band = layout.hand
        val x = centreOf(band, layout.cardWidth, index, count, stepFraction)

        return MatPoint(
            x = if (layout.field.width > 0f) (x - layout.field.left) / layout.field.width else 0.5f,
            y = if (layout.field.height > 0f) {
                (band.centerY - layout.field.top) / layout.field.height
            } else {
                1f
            },
        )
    }
}
