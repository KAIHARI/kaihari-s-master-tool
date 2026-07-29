package com.kaiharimoto.mastertool.core.board

import kotlin.math.hypot

data class TrailPoint(val x: Float, val y: Float, val atMs: Long)

/** Movement over a window of time, in whatever units the pointer reports. */
data class Drift(val dx: Float, val dy: Float) {
    companion object {
        val NONE = Drift(0f, 0f)
    }
}

/**
 * The last moments of a drag, so that letting go can be read.
 *
 * [DropGesture] knows what a flick and a hold *mean*; this is what turns a
 * stream of pointer positions into the two numbers it wants. Keeping them apart
 * matters because the hard part is not the thresholds, it is deciding *which*
 * movement the thresholds apply to.
 *
 * Not the movement since the drag began. A card carried from the hand to the far
 * spell zone has travelled a long way and means nothing by it; what says
 * "defence" is a flick in the last breath before release. And not the time since
 * the drag began either, because a careful drag across the board takes longer
 * than a hold and would set every card face-down.
 *
 * So: movement over the last [WINDOW_MS], and stillness measured backwards from
 * the release. A pointer that stops reports nothing at all, which is exactly the
 * case a naive "time since the last event" gets right by accident and everything
 * else gets wrong.
 */
data class DragTrail(val points: List<TrailPoint> = emptyList()) {

    /** Records a position, forgetting anything too old to matter. */
    fun at(x: Float, y: Float, atMs: Long): DragTrail {
        val next = points + TrailPoint(x, y, atMs)
        // Always keep the newest, even if the clock jumped.
        return DragTrail(next.filterIndexed { i, p -> i == next.lastIndex || atMs - p.atMs <= MEMORY_MS })
    }

    /** How far the pointer moved in the [windowMs] before [atMs]. */
    fun drift(atMs: Long, windowMs: Long = WINDOWS.last()): Drift {
        val last = points.lastOrNull() ?: return Drift.NONE
        val from = points.firstOrNull { atMs - it.atMs <= windowMs } ?: last
        return Drift(last.x - from.x, last.y - from.y)
    }

    /**
     * How long the pointer has been sitting still.
     *
     * Measured to [atMs] rather than to the last event, because a pointer that
     * has stopped is a pointer that is not sending events — the silence *is* the
     * hold, and reading only the timestamps would see none of it.
     */
    fun stillMs(atMs: Long): Long {
        val last = points.lastOrNull() ?: return 0
        val movedAt = points.lastOrNull { hypot(last.x - it.x, last.y - it.y) > STILL_SLOP }
        return atMs - (movedAt?.atMs ?: points.first().atMs)
    }

    /**
     * What the release means: attack, defence, or face-down.
     *
     * Two windows rather than one, and a flick in either counts. Found by a
     * test: a card brought quickly down to a zone and then flicked reads as a
     * drop if the window is long enough to include the approach, because the
     * approach is mostly vertical and drowns the flick. Shortening the window
     * instead loses the slower flick, which is the one most people actually
     * make. Neither length works alone; both together do, and "did it flick in
     * the last breath, or in the last two" is a fair description of the
     * question anyway.
     */
    fun placementOnRelease(atMs: Long): Placement {
        if (stillMs(atMs) >= DropGesture.HOLD_MS) return Placement.SET
        val flicked = WINDOWS.any { window ->
            val drift = drift(atMs, window)
            DropGesture.isFlick(drift.dx, drift.dy)
        }
        return if (flicked) Placement.DEFENSE else Placement.ATTACK
    }

    companion object {
        val EMPTY = DragTrail()

        /**
         * The windows whose movement counts as "the way you let go".
         *
         * The short one catches a flick made right at the end of a fast
         * approach; the long one catches a slower flick that a short window
         * would see only part of.
         */
        val WINDOWS = listOf(45L, 90L)

        /** Longer than a hold, so a hold is always still inside what is kept. */
        const val MEMORY_MS = 600L

        /** Below this, a pointer is not moving — it is a hand resting on glass. */
        const val STILL_SLOP = 3f
    }
}
