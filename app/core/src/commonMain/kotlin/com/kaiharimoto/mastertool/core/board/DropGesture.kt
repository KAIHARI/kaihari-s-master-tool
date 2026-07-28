package com.kaiharimoto.mastertool.core.board

import kotlin.math.abs

/**
 * The gesture *is* the orientation.
 *
 * The best idea in the tool this replaces, and nothing else on the market does
 * it. Everywhere else, putting a monster in defence means dropping it and then
 * finding a menu; here the way you let go says which. Drop it and it stands up
 * in attack. Flick it sideways as you let go and it lies down in defence — the
 * gesture is the card turning ninety degrees, which is exactly what it means.
 * Hold it a moment before letting go and it goes face-down, because setting a
 * card is the deliberate one.
 *
 * That is why this is arithmetic and not a menu, and why it lives here behind
 * tests: the thresholds are the whole feature, and a threshold that is wrong by
 * a factor of two turns the best idea in the program into a board that keeps
 * doing something you did not ask for.
 *
 * Thresholds carried over from the original's own drop handler rather than
 * invented, because they had been used and this had not.
 */
object DropGesture {

    /**
     * How much more horizontal than vertical a flick has to be.
     *
     * A drag to a zone two rows down is mostly vertical and must not read as a
     * flick, so the test is a ratio rather than a distance alone — the shape of
     * the movement, not its size.
     */
    const val FLICK_RATIO = 1.5f

    /** Below this the movement is a hand that did not quite stop, not a flick. */
    const val FLICK_DISTANCE = 20f

    /** Long enough to be a decision, short enough not to feel like a wait. */
    const val HOLD_MS = 300L

    /**
     * What a release means.
     *
     * [dx] and [dy] are the movement in the last moments of the drag, not from
     * where the drag began — a card carried across the board and then set down
     * has travelled a long way horizontally and means nothing by it.
     *
     * A hold wins over a flick, and the two agree anyway: a set monster lies in
     * defence, so the deliberate gesture and the sideways one are asking for the
     * same rotation and only differ in whether the card is face-up.
     */
    fun placement(dx: Float, dy: Float, heldMs: Long): Placement = when {
        heldMs >= HOLD_MS -> Placement.SET
        isFlick(dx, dy) -> Placement.DEFENSE
        else -> Placement.ATTACK
    }

    fun isFlick(dx: Float, dy: Float): Boolean =
        abs(dx) > FLICK_RATIO * abs(dy) && abs(dx) > FLICK_DISTANCE
}
