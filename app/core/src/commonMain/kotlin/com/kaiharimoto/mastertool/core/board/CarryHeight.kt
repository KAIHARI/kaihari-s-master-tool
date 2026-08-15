package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.tune.StageTuning

/**
 * How far a card in the air is off the felt, and where it is therefore going.
 *
 * ## Why this is not four multiplications at the call site
 *
 * It was, and they were in two files. `seatsFor` worked out three lifts from the
 * tuning to *pose* the cards; `MatPilot` worked out one of them again to
 * *hit-test* a spread. The one they shared already had a KDoc saying a copy of
 * that arithmetic would be a fan you can see and cannot touch, one slider later
 * — and the two they did not share were exactly that, quietly, for every card
 * being carried: the drawn position came out of one file and the drop out of
 * the other, and nothing compiled them together.
 *
 * ## The offset, and why it is not small
 *
 * Everything with a height reaches the screen through [StagePlane], which is a
 * real perspective divide — so a card raised off the mat is *drawn* up-table of
 * the mat coordinate it nominally occupies. [StagePlane.flatten] is that
 * offset and [StagePlane.raise] is its inverse. A finger, meanwhile, arrives on
 * the glass and unprojects onto the felt, at z = 0, where the offset is zero.
 *
 * Measured on kai's camera — 2960x1848, 41.5 degrees, distance 1.145 — the gap
 * between where a carried card is drawn and where the finger holding it is:
 *
 * | | lift | drawn up-table by |
 * |---|---|---|
 * | a card off the mat | 151 mat px | 78 px, 29% of a card |
 * | a card out of the hand | 330 px | **195 px, 71% of a card** |
 *
 * That second row is the whole of kai's "a set monster lands in attack": you
 * aim the card you can see at a monster zone, the drop resolves most of a card
 * nearer you in the spell/trap row, and `SetPosition` correctly answers
 * "upright" for a spell/trap zone. Nothing in the set logic was ever wrong.
 *
 * So a drop lands where the card is **drawn** rather than under the finger
 * carrying it — [landing] is that one step — and the finger stays where it is,
 * a little below the card, which is where it has always been drawn and is the
 * only reason a card being dragged on a touchscreen is not under a thumb.
 */
object CarryHeight {

    /**
     * How far a card carried out of [from] floats, in mat pixels.
     *
     * A card out of the hand comes up further than one slid across the mat, and
     * that is `HandTune.liftRatio`'s whole job: a hand card is being *played*,
     * and a card being played leaves the row it was in.
     */
    fun of(from: DragOrigin, cardHeight: Float, tune: StageTuning = StageTuning.DEFAULT): Float =
        when (from) {
            is DragOrigin.Hand -> hand(cardHeight, tune)
            else -> mat(cardHeight, tune)
        }

    /** A card slid across the table, or lifted out of a pile. */
    fun mat(cardHeight: Float, tune: StageTuning = StageTuning.DEFAULT): Float =
        cardHeight * tune.cards.carryLift

    /** A card being played out of the hand, which comes up further. */
    fun hand(cardHeight: Float, tune: StageTuning = StageTuning.DEFAULT): Float =
        mat(cardHeight, tune) * tune.hand.liftRatio

    /** How far an open spread floats: over the board, under a hand. */
    fun fan(cardHeight: Float, tune: StageTuning = StageTuning.DEFAULT): Float =
        mat(cardHeight, tune) * tune.cards.fanLiftRatio

    /**
     * The felt point a card drawn at [lift] over ([x], [y]) is standing on.
     *
     * In mat pixels both in and out, because that is the space [StagePlane]
     * speaks and the space a hit test arrives in. The identity when there is no
     * camera to ask and when nothing is off the felt, so a caller never needs to
     * know which of those it is in.
     */
    fun landing(x: Float, y: Float, lift: Float, plane: StagePlane?): Vec2 {
        if (plane == null || lift == 0f) return Vec2(x, y)
        val flat = plane.flatten(x, y, lift)
        return Vec2(flat.x, flat.y)
    }

    /**
     * The inverse: where a card that is going to land at ([x], [y]) is drawn.
     *
     * Not used by the stage today — the carry keeps the finger's own point and
     * the *landing* is the derived one, which is one subtraction cheaper and
     * keeps `Carry.at` meaning what it has always meant. It is here because
     * [landing] without it is a one-way door, and the pair is what a test can
     * hold: `raise(landing(p)) == p`.
     */
    fun drawnAt(x: Float, y: Float, lift: Float, plane: StagePlane?): Vec2 {
        if (plane == null || lift == 0f) return Vec2(x, y)
        val up = plane.raise(x, y, lift)
        return Vec2(up.x, up.y)
    }
}
