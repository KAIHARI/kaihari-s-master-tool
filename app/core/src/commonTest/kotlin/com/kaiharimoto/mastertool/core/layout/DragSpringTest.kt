package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DragSpringTest {

    private val frame = 1f / 60f

    private fun settle(spring: DragSpring, targetX: Float, targetY: Float, frames: Int = 240) {
        repeat(frames) { spring.step(targetX, targetY, frame) }
    }

    @Test
    fun aCardCatchesUpWithThePointer() {
        val spring = DragSpring()
        spring.snapTo(0f, 0f)
        settle(spring, 300f, 200f)

        assertEquals(300f, spring.x, 0.5f)
        assertEquals(200f, spring.y, 0.5f)
    }

    @Test
    fun itLagsWhileTheHandIsStillMoving() {
        // The whole point. A card pinned exactly to the pointer reads as a
        // cursor with a picture on it.
        val spring = DragSpring()
        spring.snapTo(0f, 0f)

        var pointer = 0f
        repeat(20) {
            pointer += 25f
            spring.step(pointer, 0f, frame)
        }

        assertTrue(spring.x < pointer, "the card kept up exactly, which is the bug")
        assertTrue(spring.lagX > 1f, "lag was only ${spring.lagX}")
    }

    @Test
    fun itStopsLaggingOnceTheHandStops() {
        val spring = DragSpring()
        spring.snapTo(0f, 0f)
        repeat(20) { spring.step(500f, 0f, frame) }
        settle(spring, 500f, 0f)

        assertEquals(0f, spring.lagX, 0.001f)
    }

    @Test
    fun itDoesNotWobbleAroundThePointer() {
        // Critically damped: a card that oscillated around your finger would be
        // worse than one that never lagged.
        val spring = DragSpring()
        spring.snapTo(0f, 0f)

        var overshoot = 0f
        repeat(240) {
            spring.step(100f, 0f, frame)
            if (spring.x > 100f) overshoot = maxOf(overshoot, spring.x - 100f)
        }

        assertTrue(overshoot < 1f, "overshot by $overshoot")
    }

    @Test
    fun itNeverMovesAwayFromAStationaryTarget() {
        val spring = DragSpring()
        spring.snapTo(0f, 0f)

        var previous = Float.MAX_VALUE
        repeat(120) {
            spring.step(100f, 0f, frame)
            val distance = abs(100f - spring.x)
            assertTrue(distance <= previous + 0.01f, "distance grew to $distance")
            previous = distance
        }
    }

    @Test
    fun aLongFrameGapDoesNotThrowTheCardOffScreen() {
        // A spring given a large enough timestep does not merely lose accuracy,
        // it diverges. Backgrounding the app must not launch the card.
        val spring = DragSpring()
        spring.snapTo(0f, 0f)

        spring.step(100f, 0f, seconds = 5f)
        repeat(10) { spring.step(100f, 0f, seconds = 2f) }

        assertTrue(spring.x.isFinite(), "position went to ${spring.x}")
        assertTrue(abs(spring.x) < 1000f, "position ran away to ${spring.x}")
    }

    @Test
    fun itSettlesAtThirtyFramesASecondToo() {
        // Desktop and a tablet do not agree on frame rate, and a spring tuned at
        // one that rings at the other is a bug nobody would reproduce.
        val spring = DragSpring()
        spring.snapTo(0f, 0f)
        repeat(120) { spring.step(240f, 0f, 1f / 30f) }

        assertEquals(240f, spring.x, 0.5f)
    }

    @Test
    fun aStepOfNoTimeChangesNothing() {
        val spring = DragSpring()
        spring.snapTo(10f, 10f)
        spring.step(500f, 500f, 0f)

        assertEquals(10f, spring.x)
        assertEquals(10f, spring.y)
    }

    @Test
    fun snappingPutsTheCardOnThePointerAndStopsIt() {
        val spring = DragSpring()
        spring.snapTo(0f, 0f)
        repeat(10) { spring.step(400f, 400f, frame) }

        spring.snapTo(50f, 60f)
        assertEquals(50f, spring.x)
        assertEquals(60f, spring.y)
        assertEquals(0f, spring.lagX)

        // Still, so the next step does not carry the old velocity into a new drag.
        spring.step(50f, 60f, frame)
        assertEquals(50f, spring.x, 0.001f)
    }
}

class DragPhysicsTest {

    @Test
    fun aCardHeldStillSitsSlightlyOffSquare() {
        // Never quite zero: a card set down by hand is never square, and the
        // original picked three degrees for the same reason.
        assertEquals(DragPhysics.REST_DEGREES, DragPhysics.angleFor(0f), 0.001f)
    }

    @Test
    fun theCardTrailsTheDirectionOfTravel() {
        // Dragging right leaves the card behind on the left, so lag is positive
        // and it hangs back from the corner being pulled.
        assertTrue(DragPhysics.swingDegrees(60f) > 0f)
        assertTrue(DragPhysics.swingDegrees(-60f) < 0f)
    }

    @Test
    fun theSwingIsCapped() {
        // A drag flung across the screen must not spin the card.
        assertEquals(DragPhysics.MAX_SWING_DEGREES, DragPhysics.swingDegrees(10_000f), 0.001f)
        assertEquals(-DragPhysics.MAX_SWING_DEGREES, DragPhysics.swingDegrees(-10_000f), 0.001f)
    }

    @Test
    fun theSwingGrowsWithTheLag() {
        var previous = -100f
        (0..10).forEach { step ->
            val angle = DragPhysics.swingDegrees(step * 10f)
            assertTrue(angle >= previous, "angle fell at lag ${step * 10}")
            previous = angle
        }
    }
}
