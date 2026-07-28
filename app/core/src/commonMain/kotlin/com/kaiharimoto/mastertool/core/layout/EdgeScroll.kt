package com.kaiharimoto.mastertool.core.layout

/**
 * How fast a pane should scroll while a card is held near its edge.
 *
 * A dragged card can only be dropped somewhere on screen, so a section taller
 * than its pane is unreachable below the fold without this: you would have to
 * put the card down, scroll, and pick it up again, which is not a thing you do
 * to a card you are holding.
 *
 * The ramp is squared rather than linear. Linear means the pane is already
 * creeping the moment the card enters the zone, which fights you while you are
 * aiming at the last row; squared leaves most of the zone nearly still and puts
 * the speed in the final stretch, where wanting to scroll is unambiguous.
 */
object EdgeScroll {

    /** How deep the sensitive band is at each edge, as a fraction of the pane. */
    const val ZONE_FRACTION = 0.18f

    /** Deepest the band ever gets, so a tall Main pane does not scroll from its middle. */
    const val MAX_ZONE = 96f

    /**
     * Pixels per second, negative towards the start of the list.
     *
     * [pointerY] is in the same space as [top] and [bottom]. A pointer past
     * either edge scrolls at full speed rather than faster than full speed:
     * dragging towards the next pane should not accelerate this one.
     */
    fun velocity(
        pointerY: Float,
        top: Float,
        bottom: Float,
        maxVelocity: Float,
    ): Float {
        val height = bottom - top
        if (height <= 0f || maxVelocity <= 0f) return 0f

        // Never more than a third of the pane per edge, or the two zones would
        // meet in a short pane and leave nowhere neutral to aim at.
        val zone = minOf(height * ZONE_FRACTION, MAX_ZONE, height / 3f)
        if (zone <= 0f) return 0f

        val fromTop = pointerY - top
        val fromBottom = bottom - pointerY

        return when {
            fromTop < zone -> -maxVelocity * ramp((zone - fromTop) / zone)
            fromBottom < zone -> maxVelocity * ramp((zone - fromBottom) / zone)
            else -> 0f
        }
    }

    private fun ramp(depth: Float): Float {
        val clamped = depth.coerceIn(0f, 1f)
        return clamped * clamped
    }
}
