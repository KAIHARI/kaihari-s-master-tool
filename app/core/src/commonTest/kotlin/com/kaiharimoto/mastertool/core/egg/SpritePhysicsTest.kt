package com.kaiharimoto.mastertool.core.egg

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpritePhysicsTest {

    private val bounds = Bounds(1000f, 600f)
    private val frame = 1f / 60f

    private fun run(
        sprite: Sprite,
        frames: Int,
        dt: Float = frame,
        gravity: Float = 0f,
        drag: Float = 0f,
        bounce: Boolean = true,
    ): Sprite {
        var current = sprite
        repeat(frames) {
            current = SpritePhysics.step(current, dt, bounds, gravity, drag, bounce)
        }
        return current
    }

    @Test
    fun aSpriteMovesAlongItsVelocity() {
        // 60 units per second, for one sixtieth of a second.
        val moved = SpritePhysics.step(Sprite(x = 100f, y = 100f, vx = 60f, vy = 0f), frame, bounds)
        assertEquals(101f, moved.x, 0.01f)
        assertEquals(100f, moved.y, 0.01f)
    }

    @Test
    fun asecondOfMotionTakesASecondOfFrames() {
        val moved = run(Sprite(x = 100f, y = 100f, vx = 60f, vy = 0f), frames = 60)
        assertEquals(160f, moved.x, 0.01f)
    }

    @Test
    fun gravityPullsDownwards() {
        val fallen = run(Sprite(500f, 100f, 0f, 0f), frames = 60, gravity = 800f)
        assertTrue(fallen.vy > 0f)
        assertTrue(fallen.y > 100f)
    }

    @Test
    fun dragBleedsOffSpeed() {
        val coasting = run(Sprite(100f, 300f, 400f, 0f), frames = 60, drag = 2f)
        assertTrue(abs(coasting.vx) < 400f, "drag should have slowed it")
    }

    @Test
    fun spinAccumulatesIntoRotation() {
        val spun = run(Sprite(0f, 0f, 0f, 0f, rotation = 10f, spin = 90f), frames = 60)
        assertEquals(100f, spun.rotation, 0.01f)
    }

    @Test
    fun aFrameLongerThanTheClampIsIntegratedAsTheClamp() {
        // Not a rounding detail: this is what stops a sprite crossing a wall in
        // one step after the app has been away.
        val long = SpritePhysics.step(Sprite(0f, 300f, 300f, 0f), dt = 5f, bounds = bounds)
        assertEquals(300f / 30f, long.x, 0.01f)
    }

    // Never off screen. The chibi is loose for as long as the egg runs, so a
    // single escape is permanent.

    @Test
    fun aSpriteNeverLeavesTheBox() {
        var sprite = Sprite(500f, 300f, 900f, -700f, radius = 40f)
        repeat(2000) {
            sprite = SpritePhysics.step(sprite, frame, bounds, gravity = 600f)
            assertTrue(sprite.x in 40f..960f, "escaped horizontally at x=${sprite.x}")
            assertTrue(sprite.y in 40f..560f, "escaped vertically at y=${sprite.y}")
        }
    }

    @Test
    fun hittingAWallReversesTheDirection() {
        val movingLeft = Sprite(x = 5f, y = 300f, vx = -400f, vy = 0f, radius = 20f)
        val bounced = SpritePhysics.step(movingLeft, frame, bounds)

        assertTrue(bounced.vx > 0f, "should be travelling right after hitting the left wall")
        assertEquals(20f, bounced.x, 0.01f)
    }

    @Test
    fun aBounceLosesEnergyRatherThanGainingIt() {
        // A sprite that sped up on every wall would eventually be a strobe light.
        val bounced = SpritePhysics.step(
            Sprite(x = 5f, y = 300f, vx = -400f, vy = 0f, radius = 20f),
            frame,
            bounds,
        )
        assertTrue(abs(bounced.vx) < 400f)
        assertEquals(400f * SpritePhysics.BOUNCE, abs(bounced.vx), 0.01f)
    }

    @Test
    fun bouncingSettlesRatherThanRingingForever() {
        var sprite = Sprite(500f, 100f, 0f, 0f, radius = 30f)
        repeat(1200) { sprite = SpritePhysics.step(sprite, frame, bounds, gravity = 900f) }

        // Resting on the floor, not still hopping about.
        assertTrue(abs(sprite.vy) < 200f, "still bouncing hard at vy=${sprite.vy}")
    }

    @Test
    fun aLongFrameCannotTunnelThroughAWall() {
        // A frame lost to a pause used to be enough to move a sprite clean past
        // the edge; the step is clamped so it cannot.
        val fast = Sprite(x = 950f, y = 300f, vx = 100_000f, vy = 0f, radius = 10f)
        val stepped = SpritePhysics.step(fast, dt = 3f, bounds = bounds)

        assertTrue(stepped.x <= 990f, "tunnelled to x=${stepped.x}")
    }

    @Test
    fun aZeroLengthFrameChangesNothing() {
        val sprite = Sprite(100f, 100f, 50f, 50f)
        assertEquals(sprite, SpritePhysics.step(sprite, dt = 0f, bounds = bounds))
    }

    @Test
    fun aBoxTooSmallForTheSpriteParksItInsteadOfTrappingIt() {
        val tiny = Bounds(20f, 20f)
        val stepped = SpritePhysics.step(Sprite(0f, 0f, 100f, 100f, radius = 50f), frame, tiny)

        assertEquals(10f, stepped.x, 0.01f)
        assertEquals(10f, stepped.y, 0.01f)
    }

    @Test
    fun thingsThrownWithoutBouncingKeepGoing() {
        // Cards flung off the screen are meant to leave, not rattle around.
        val thrown = run(Sprite(500f, 300f, 800f, 0f), frames = 120, bounce = false)
        assertTrue(thrown.x > bounds.width)
    }

    @Test
    fun aSpriteIsSpentOnceItIsOldOrGone() {
        assertTrue(SpritePhysics.isSpent(Sprite(0f, 0f, 0f, 0f, age = 5f), bounds, maxAge = 4f))
        assertTrue(SpritePhysics.isSpent(Sprite(0f, 900f, 0f, 0f), bounds, maxAge = 10f))
        assertFalse(SpritePhysics.isSpent(Sprite(0f, 100f, 0f, 0f, age = 1f), bounds, maxAge = 10f))
    }
}
