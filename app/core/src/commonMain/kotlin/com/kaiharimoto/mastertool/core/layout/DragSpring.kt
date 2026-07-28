package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A card being carried, catching up with the hand carrying it.
 *
 * A dragged card pinned exactly to the pointer is the one place the illusion
 * breaks: nothing with mass tracks your hand perfectly, so a card that does
 * reads as a cursor with a picture on it. Letting it lag by a few pixels and
 * swing into the direction of travel costs one spring and is most of the
 * difference between dragging an icon and carrying a card.
 *
 * Critically damped by default — [damping] is derived from [stiffness] rather
 * than set independently, because the useful behaviour here is "arrives quickly
 * and does not wobble". A card that oscillated around the pointer would be
 * worse than one that never lagged at all.
 *
 * Integrated semi-implicitly: velocity is updated first and the new velocity
 * moves the position. Explicit Euler at these stiffnesses gains energy and
 * throws the card off screen within a second or two.
 */
class DragSpring(
    private val stiffness: Float = DEFAULT_STIFFNESS,
    dampingRatio: Float = 1f,
) {
    private val damping = 2f * dampingRatio * sqrt(stiffness)

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    private var velocityX = 0f
    private var velocityY = 0f

    /** How far behind the target the card currently is. */
    val lagX: Float get() = trailX
    val lagY: Float get() = trailY

    private var trailX = 0f
    private var trailY = 0f

    /** Puts the card exactly on the pointer and still, for the start of a drag. */
    fun snapTo(targetX: Float, targetY: Float) {
        x = targetX
        y = targetY
        velocityX = 0f
        velocityY = 0f
        trailX = 0f
        trailY = 0f
    }

    /**
     * Advances by [seconds] towards the target.
     *
     * The timestep is capped rather than trusted. A frame gap from the app being
     * backgrounded would otherwise integrate one enormous step, and a spring
     * given a large enough step is not merely inaccurate, it diverges — the card
     * would leave the screen and never come back.
     */
    fun step(targetX: Float, targetY: Float, seconds: Float) {
        if (seconds <= 0f) return
        val dt = seconds.coerceAtMost(MAX_STEP)

        velocityX += (-stiffness * (x - targetX) - damping * velocityX) * dt
        velocityY += (-stiffness * (y - targetY) - damping * velocityY) * dt
        x += velocityX * dt
        y += velocityY * dt

        trailX = targetX - x
        trailY = targetY - y

        // Below a fraction of a pixel there is nothing left to see, and stopping
        // keeps a stationary card from jittering on floating-point noise.
        if (abs(trailX) < REST && abs(velocityX) < REST) {
            x = targetX
            velocityX = 0f
            trailX = 0f
        }
        if (abs(trailY) < REST && abs(velocityY) < REST) {
            y = targetY
            velocityY = 0f
            trailY = 0f
        }
    }

    companion object {
        const val DEFAULT_STIFFNESS = 420f
        const val MAX_STEP = 1f / 30f
        const val REST = 0.05f
    }
}

/**
 * How a carried card is angled and squashed by its own lag.
 *
 * All of it comes from how far behind the pointer the card is, so there is no
 * second source of truth about how fast the drag is going.
 */
object DragPhysics {

    /** Beyond this much lag the card is already as angled as it is going to get. */
    const val LAG_AT_FULL_TILT = 90f

    const val MAX_SWING_DEGREES = 9f

    /**
     * Rotation, in degrees, for a card trailing [lagX] pixels behind the pointer.
     *
     * Signed so the card trails the direction of travel: dragging right leaves
     * the card behind and to the left, and it hangs back from the corner being
     * pulled — which is what a card held loosely between two fingers does.
     */
    fun swingDegrees(lagX: Float): Float =
        (lagX / LAG_AT_FULL_TILT).coerceIn(-1f, 1f) * MAX_SWING_DEGREES

    /**
     * A resting angle for a card that is being held still.
     *
     * Not zero: a card set down by hand is never quite square, and the original
     * used a fixed three degrees for exactly this reason.
     */
    const val REST_DEGREES = 3f

    /** The total angle: the resting tilt, plus however far the drag is swinging it. */
    fun angleFor(lagX: Float): Float = REST_DEGREES + swingDegrees(lagX)
}
