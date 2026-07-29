package com.kaiharimoto.mastertool.core.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DragTrailTest {

    /** Walks a pointer along a path, one sample every [everyMs]. */
    private fun trail(
        vararg steps: Pair<Float, Float>,
        startMs: Long = 1_000,
        everyMs: Long = 16,
    ): Pair<DragTrail, Long> {
        var trail = DragTrail.EMPTY
        var now = startMs
        steps.forEach { (x, y) ->
            trail = trail.at(x, y, now)
            now += everyMs
        }
        return trail to now
    }

    @Test
    fun anUntouchedTrailSaysNothingHappened() {
        assertEquals(Drift.NONE, DragTrail.EMPTY.drift(1_000))
        assertEquals(0, DragTrail.EMPTY.stillMs(1_000))
        assertEquals(Placement.ATTACK, DragTrail.EMPTY.placementOnRelease(1_000))
    }

    @Test
    fun driftIsTheLastMomentsAndNotTheWholeDrag() {
        // Carried a long way to the right, then eased down into the zone. The
        // journey is not the gesture.
        val (trail, now) = trail(
            0f to 0f,
            200f to 0f,
            400f to 0f,
            600f to 0f,
            600f to 6f,
            600f to 12f,
            everyMs = 40,
        )

        val drift = trail.drift(now)
        assertEquals(0f, drift.dx, "nothing sideways in the last breath")
        assertTrue(drift.dy > 0f)
        assertEquals(Placement.ATTACK, trail.placementOnRelease(now))
    }

    @Test
    fun aSlowFlickIsRead() {
        val (trail, now) = trail(
            0f to 0f,
            0f to 40f,
            0f to 78f,
            20f to 80f,
            50f to 81f,
            85f to 82f,
        )

        assertEquals(Placement.DEFENSE, trail.placementOnRelease(now))
    }

    @Test
    fun aFlickRightAtTheEndOfAFastApproachIsAlsoRead() {
        // The one that needed a second window. Over ninety milliseconds this
        // path is mostly vertical -- the approach drowns the flick -- and it
        // reads as an ordinary drop. Over sixty it is plainly sideways.
        val (trail, now) = trail(
            0f to 0f,
            0f to 40f,
            0f to 80f,
            30f to 84f,
            70f to 86f,
        )

        assertEquals(Placement.DEFENSE, trail.placementOnRelease(now))
    }

    @Test
    fun stillnessIsMeasuredToTheReleaseNotToTheLastEvent() {
        // The case a naive reading gets wrong: a pointer that has stopped sends
        // nothing at all, so the silence is the whole hold.
        val (trail, lastEvent) = trail(0f to 0f, 40f to 0f, 80f to 0f)

        assertTrue(trail.stillMs(lastEvent) < DropGesture.HOLD_MS)
        assertTrue(trail.stillMs(lastEvent + 400) >= DropGesture.HOLD_MS)
        assertEquals(Placement.SET, trail.placementOnRelease(lastEvent + 400))
    }

    @Test
    fun aHandRestingOnGlassIsStill() {
        // Sub-pixel jitter under a fingertip must not read as movement, or a
        // hold would be impossible on a touchscreen.
        var trail = DragTrail.EMPTY
        var now = 1_000L
        repeat(24) {
            trail = trail.at(if (it % 2 == 0) 100f else 101.5f, 50f, now)
            now += 16
        }

        assertTrue(
            trail.stillMs(now) >= DropGesture.HOLD_MS,
            "a jittering finger is a finger that has stopped",
        )
        assertEquals(Placement.SET, trail.placementOnRelease(now))
    }

    @Test
    fun aQuickDropIsAttack() {
        val (trail, now) = trail(0f to 0f, 4f to 30f, 6f to 62f)

        assertEquals(Placement.ATTACK, trail.placementOnRelease(now))
    }

    @Test
    fun theTrailForgetsWhatIsTooOldToMatter() {
        var trail = DragTrail.EMPTY
        var now = 1_000L
        repeat(200) {
            trail = trail.at(it.toFloat(), 0f, now)
            now += 16
        }

        val lastSample = trail.points.last().atMs
        assertTrue(trail.points.size < 60, "a long drag does not accumulate forever")
        assertTrue(
            lastSample - trail.points.first().atMs <= DragTrail.MEMORY_MS,
            "nothing older than the memory is kept",
        )
        assertTrue(
            lastSample - trail.points.first().atMs >= DropGesture.HOLD_MS,
            "and what is kept still covers a whole hold",
        )
    }

    @Test
    fun asingleSampleIsNotAFlick() {
        val trail = DragTrail.EMPTY.at(400f, 0f, 1_000)

        assertEquals(Drift.NONE, trail.drift(1_000), "one point has not moved from anywhere")
        assertEquals(Drift.NONE, trail.drift(1_000, windowMs = 60))
        assertEquals(Placement.ATTACK, trail.placementOnRelease(1_000))
    }
}
