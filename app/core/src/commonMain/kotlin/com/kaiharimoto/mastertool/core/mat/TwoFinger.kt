package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.PI
import kotlin.math.atan2

/**
 * The two-pointer arithmetic, written here rather than imported.
 *
 * Compose ships `calculateRotation()` and `calculatePan()` and they would work.
 * They are rewritten for three reasons: written here they are testable in
 * commonTest, where every other hard problem in this project already lives; the
 * sign convention is pinned to what `graphicsLayer.rotationZ` means rather than
 * assumed and later discovered; and the minimum-span guard has to sit *inside*
 * the accumulation rather than around it, which an imported function cannot do.
 */
object TwoFinger {

    private const val TO_DEGREES = 180f / PI.toFloat()

    /**
     * Signed degrees the segment between two fingers turned since last frame.
     *
     * Positive is clockwise on screen, which is what `rotationZ` means, so the
     * number goes straight to the card without a sign flip nobody will
     * remember. Screen y grows downward, so a segment along +x turning to +y is
     * a quarter turn clockwise and this returns +90.
     *
     * One `atan2` of the cross and dot products, rather than subtracting two
     * `atan2` calls: the difference form jumps by a full turn whenever the
     * segment crosses due west, and a card that spins right round mid-twist is
     * the bug that form always eventually produces.
     */
    fun twistDegrees(prev0: Vec2, prev1: Vec2, cur0: Vec2, cur1: Vec2): Float {
        val a = prev1 - prev0
        val b = cur1 - cur0
        val cross = a.x * b.y - a.y * b.x
        val dot = a.x * b.x + a.y * b.y
        if (cross == 0f && dot == 0f) return 0f
        return atan2(cross, dot) * TO_DEGREES
    }

    fun span(a: Vec2, b: Vec2): Float = (b - a).length

    /**
     * Translation that survives a finger arriving or leaving.
     *
     * The mean movement of only those pointers present in *both* frames. A
     * pointer in just one of them contributes nothing, so on the frame a second
     * finger lands the card does not move at all — which is the entire reason
     * this is not `centroid(now) - centroid(before)`. That form jumps by half
     * the finger separation the instant the second finger touches down, and
     * again when it lifts, and it is the single most common cause of "the card
     * teleports when I try to turn it".
     */
    fun pan(previous: Map<Long, Vec2>, current: Map<Long, Vec2>): Vec2 {
        var sum = Vec2.Zero
        var counted = 0

        for ((id, now) in current) {
            val before = previous[id] ?: continue
            sum += now - before
            counted++
        }

        return if (counted == 0) Vec2.Zero else sum / counted.toFloat()
    }
}
