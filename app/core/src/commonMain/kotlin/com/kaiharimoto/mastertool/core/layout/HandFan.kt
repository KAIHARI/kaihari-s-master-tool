package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.board.MatPoint
import kotlin.math.min

/**
 * The hand as it is actually drawn: one entry per place, left to right.
 *
 * A hand index, or null for a place being **held open** — somewhere a card is
 * about to land. That second kind is the whole of kai's fourth report,
 * *"placing cards back into the fan and hand also needs to be more dynamic and
 * visually intuitive"*: a caret painted in a gap says where a card will go, and
 * a gap that is actually there shows you.
 *
 * It is one value rather than three arguments because the pose, the hit box, the
 * insert index and the indicator are four readings of the same row and they were
 * reconstructing it separately — which is exactly how they came to disagree. The
 * hand was *drawn* with a place for the card in the air and *measured* as though
 * that place had closed up, so every gap the caret named was half a step out:
 * 0.37 of a card width at the shipped tuning, which is most of the width of the
 * thing it was pointing at.
 */
data class HandRow(val places: List<Int?>) {

    /** How many places there are, open ones included. */
    val size: Int get() = places.size

    /** The hand indices actually drawn, in order. */
    val cards: List<Int> get() = places.filterNotNull()

    /** Which places are being held open. */
    val open: List<Int> get() = places.indices.filter { places[it] == null }

    /** Where hand card [handIndex] is drawn, or null while it is in the air. */
    fun placeOf(handIndex: Int): Int? = places.indexOf(handIndex).takeIf { it >= 0 }

    /**
     * The gap, in the full hand's numbering, after [ahead] of the drawn cards.
     *
     * Which is the index of the next drawn card — "before that one" — or the end
     * of the hand when there is no next one. That is the number `reorderHand`
     * and `handInsert` take.
     */
    fun gapAfter(ahead: Int, count: Int): Int = cards.getOrNull(ahead) ?: count

    /**
     * The place being held open for gap [at], if this row is holding one.
     *
     * Asked as "every drawn card before it belongs before [at], and every drawn
     * card after it belongs after" rather than by naming the gap arithmetically,
     * because two gap numbers can be the same physical place: with card 0 in the
     * air, gaps 0 and 1 are both "the front of the row", and `reorderHand`
     * refuses both as no move at all. A predicate answers for either spelling.
     */
    fun openingFor(at: Int, count: Int): Int? = open.firstOrNull { place ->
        at in 0..count &&
            places.take(place).all { it == null || it < at } &&
            places.drop(place + 1).all { it == null || it >= at }
    }

    companion object {
        /** A plain hand of [count] cards with nothing in the air and nothing open. */
        fun of(count: Int): HandRow = HandRow((0 until count.coerceAtLeast(0)).toList())
    }
}

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
 * solved layout, four readings, none of them able to disagree — the argument
 * `DeckFit` makes for the builder's panes, at the scale of a hand.
 *
 * **And it drifted anyway, in the one way that KDoc did not cover.** Every
 * reader took a `count`, and two different numbers were being passed for it: the
 * renderer drew `hand.size` places while a card was in the air, and [insertAt]
 * measured against `hand.size - 1` as though the row had closed up behind it.
 * Both were defensible; they were not the same row. [HandRow] is the row itself,
 * so the question "how many places are there" has one answer.
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

    /**
     * The row a hand is drawn as, given what is in the air and what is landing.
     *
     * @param count how many cards the hand holds, in the field's own numbering
     * @param lifted the hand indices currently being carried — up to ten of them,
     *   because the table has ten lanes. Their places close up: taking a card out
     *   of your hand and the rest closing behind it is what a hand does.
     * @param opening the gaps, in the full hand's numbering, that a card is about
     *   to land in. One place is held open for each, and a card dragged along its
     *   own hand therefore sees its place travel with it rather than a caret
     *   appearing in a row that never moved.
     */
    fun row(
        count: Int,
        lifted: Set<Int> = emptySet(),
        opening: List<Int> = emptyList(),
    ): HandRow {
        val shown = (0 until count.coerceAtLeast(0)).filter { it !in lifted }
        if (opening.isEmpty()) return HandRow(shown)

        val gaps = opening.sorted()
        val places = mutableListOf<Int?>()
        var next = 0
        shown.forEach { index ->
            while (next < gaps.size && gaps[next] <= index) {
                places += null
                next++
            }
            places += index
        }
        while (next < gaps.size) {
            places += null
            next++
        }
        return HandRow(places)
    }

    /** The centre of place [place] of [places], in mat pixels. */
    fun centreOf(
        band: Slot,
        cardWidth: Float,
        place: Int,
        places: Int,
        stepFraction: Float,
    ): Float {
        val step = step(band, cardWidth, places, stepFraction)
        val spread = cardWidth + step * (places - 1)
        return band.left + (band.width - spread) / 2f + cardWidth / 2f + place * step
    }

    /**
     * Which position in the hand a card released at [x] is asking to take.
     *
     * The inverse of [centreOf], answering in *gaps* rather than in cards: zero
     * is before everything, `count` is after everything, and a card released over
     * the middle of the third drawn card is asking to become the third card. That
     * is the number `List.add(index, …)` takes, which is the whole reason it is
     * phrased this way — an answer in "which card is nearest" needs a
     * before-or-after decision at every call site, and one of them would get it
     * wrong.
     *
     * **Counted rather than divided**, and that is what makes it the true inverse
     * of the row it is measuring. The old form was `((x - first) / step)
     * .roundToInt()` plus a correction for the card in the air, which describes a
     * row of evenly spaced cards — and a row with a place held open in it is not
     * evenly spaced. Counting how many drawn cards lie left of the finger is
     * exact for any arrangement of places, needs no correction, and settles in a
     * single pass: the gap it names is the gap the row is already holding open.
     */
    fun insertAt(
        band: Slot,
        cardWidth: Float,
        row: HandRow,
        count: Int,
        x: Float,
        stepFraction: Float,
    ): Int {
        if (cardWidth <= 0f || row.size == 0) return 0
        var ahead = 0
        row.places.forEachIndexed { place, card ->
            if (card != null && centreOf(band, cardWidth, place, row.size, stepFraction) < x) ahead++
        }
        return row.gapAfter(ahead, count)
    }

    /**
     * Where place [place] of [places] sits, in the mat's own fractions.
     *
     * The stage's reading, kept in the shape the pose builder and the hit box
     * already use: a [MatPoint] against `layout.field`, so a hand card is a
     * point on the same plane as everything else on the table even though the
     * band it is laid out in sits below the field.
     */
    fun pointFor(
        layout: BoardLayout,
        place: Int,
        places: Int,
        stepFraction: Float,
    ): MatPoint {
        val band = layout.hand
        val x = centreOf(band, layout.cardWidth, place, places, stepFraction)

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
