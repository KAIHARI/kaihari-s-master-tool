package com.kaiharimoto.mastertool.core.motion

import kotlin.math.abs

/** Extra rotation a card takes on because of how it is being moved. */
data class Bank(
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
) {
    operator fun plus(other: Bank) = Bank(rotX + other.rotX, rotY + other.rotY, rotZ + other.rotZ)

    companion object {
        val Level = Bank()
    }
}

/**
 * What a card does because it is being moved, rather than because of where it
 * is going.
 *
 * The standing rule is that a carried card's position is *assigned* from the
 * finger and never sprung toward it, because a spring there is lag and lag is
 * the thing that makes a simulator feel fake. That rule is right and it leaves
 * a hole: a card whose position is assigned has no dynamics at all. Swept
 * across the table at speed it stays perfectly flat and perfectly square, which
 * no object with mass has ever done.
 *
 * This fills the hole without touching the rule. The **position** still comes
 * straight from the finger; only the *attitude* is inferred from how fast that
 * position is changing, and it is fed to the same springs the rotations already
 * use — so the card banks into a sweep over a couple of frames and levels out
 * over a couple more, which is a lag you want, on an axis where nobody is
 * pointing at anything.
 *
 * The signs come from what a real card does, and they are all the same idea:
 * **the leading edge goes away from you.** Sweep a card to the right and it
 * knifes, right edge back. Push it away and the far edge dips. That is partly
 * air and mostly the fact that a hand pivots at the wrist.
 */
object CardDynamics {

    /** Card widths per second at which the bank is fully wound on. */
    private const val SATURATION_SPEED = 5.5f

    /** How far a card banks at full speed, in degrees. */
    private const val BANK_LIMIT = 13f

    /**
     * The twist that comes from dragging a flat thing edge-on.
     *
     * A quarter of the bank, and negative: the card trails the hand rather
     * than leading it. Small enough to be felt rather than seen, which is the
     * correct size for a secondary motion — at any size where you notice it,
     * the card has stopped looking dragged and started looking thrown.
     */
    private const val TRAIL_SHARE = -0.26f

    /** Below this the card is being placed, not swept, and stays square. */
    private const val DEAD_ZONE = 0.04f

    /**
     * How [velocity] (in pixels per second) leans a card [cardWidth] wide.
     *
     * Scaled by the card's own size rather than by pixels, so the same flick of
     * a finger banks the card by the same amount whether the table was solved
     * for a phone or for a desk monitor.
     */
    fun bank(velocity: Vec3, cardWidth: Float, limit: Float = BANK_LIMIT): Bank {
        if (cardWidth <= 0f) return Bank.Level

        val reference = cardWidth * SATURATION_SPEED
        val across = soften(velocity.x / reference)
        val along = soften(velocity.y / reference)
        if (across == 0f && along == 0f) return Bank.Level

        return Bank(
            // Pushed away from the player — up the screen, so a negative y —
            // the far edge leads and goes back, which is a positive rotationX.
            rotX = -along * limit,
            // Swept to the right, the right edge leads and goes back, which is
            // a positive rotationY.
            rotY = across * limit,
            rotZ = across * limit * TRAIL_SHARE,
        )
    }

    /**
     * A dead zone at the bottom and a ceiling at the top.
     *
     * The dead zone is not a tuning nicety: a finger resting on a card produces
     * a pixel or two of tremor every frame, and without it a held card sits
     * there shivering. The ceiling is what stops a fast flick from turning the
     * card edge-on to the camera and making it disappear.
     */
    private fun soften(ratio: Float): Float {
        val magnitude = abs(ratio)
        if (magnitude <= DEAD_ZONE) return 0f
        val scaled = (magnitude - DEAD_ZONE) / (1f - DEAD_ZONE)
        return if (ratio < 0f) -scaled.coerceAtMost(1f) else scaled.coerceAtMost(1f)
    }
}
