package com.kaiharimoto.mastertool.core.egg

import kotlin.math.abs

/** The box a sprite may move in, in whatever units the caller is using. */
data class Bounds(val width: Float, val height: Float)

/**
 * Something loose on the screen.
 *
 * One type for the chibi and for everything it throws — they differ only in the
 * numbers fed to [SpritePhysics.step].
 */
data class Sprite(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float = 0f,
    val spin: Float = 0f,
    /** Half the sprite's width and height, for bouncing off the walls. */
    val radius: Float = 0f,
    /** Seconds it has been alive, so short-lived things can be reaped. */
    val age: Float = 0f,
)

/**
 * The motion behind the easter egg.
 *
 * Physics is arithmetic, and arithmetic can be tested — so it lives here rather
 * than inside an animation loop where the only way to check it is to watch. The
 * things worth being sure of are unglamorous: a sprite never escapes the screen,
 * a bounce loses energy rather than gaining it, and stepping by a longer frame
 * does not teleport anything.
 */
object SpritePhysics {

    /** Fraction of speed kept after hitting a wall. */
    const val BOUNCE = 0.72f

    /** The longest frame that is integrated at once, in seconds. */
    private const val MAX_STEP = 1f / 30f

    /**
     * Advances one sprite.
     *
     * [dt] is clamped, because a frame delayed by a garbage collection or a
     * backgrounded app would otherwise move a sprite half a screen in one go and
     * straight through a wall.
     */
    fun step(
        sprite: Sprite,
        dt: Float,
        bounds: Bounds,
        gravity: Float = 0f,
        drag: Float = 0f,
        bounce: Boolean = true,
    ): Sprite {
        val step = dt.coerceIn(0f, MAX_STEP)
        if (step <= 0f) return sprite

        val decay = (1f - drag * step).coerceIn(0f, 1f)
        var vx = sprite.vx * decay
        var vy = (sprite.vy + gravity * step) * decay

        var x = sprite.x + vx * step
        var y = sprite.y + vy * step

        if (bounce) {
            val left = sprite.radius
            val right = bounds.width - sprite.radius
            val top = sprite.radius
            val bottom = bounds.height - sprite.radius

            // Guarded so a box smaller than the sprite parks it rather than
            // trapping it in a corner it can never satisfy.
            if (right > left) {
                if (x < left) {
                    x = left
                    vx = abs(vx) * BOUNCE
                } else if (x > right) {
                    x = right
                    vx = -abs(vx) * BOUNCE
                }
            } else {
                x = bounds.width / 2f
            }

            if (bottom > top) {
                if (y < top) {
                    y = top
                    vy = abs(vy) * BOUNCE
                } else if (y > bottom) {
                    y = bottom
                    vy = -abs(vy) * BOUNCE
                }
            } else {
                y = bounds.height / 2f
            }
        }

        return sprite.copy(
            x = x,
            y = y,
            vx = vx,
            vy = vy,
            rotation = sprite.rotation + sprite.spin * step,
            age = sprite.age + step,
        )
    }

    /** True once a sprite has fallen out of the world or outlived its welcome. */
    fun isSpent(sprite: Sprite, bounds: Bounds, maxAge: Float): Boolean =
        sprite.age >= maxAge || sprite.y - sprite.radius > bounds.height
}
