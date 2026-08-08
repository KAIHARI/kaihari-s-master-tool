package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The same convex shape, a fixed distance further out all the way round.
 *
 * This is what a soft shadow's rings are made of, and until now it was done by
 * pushing every corner *away from the shape's centre* — which is not an offset
 * at all. A corner of a 59:86 card sits 52.1 half-units from the middle while
 * its long edges sit at 43 and its short edges at 29.5, so moving the corners
 * out by `w` moves the long edges out by `0.825w` and the short ones by
 * `0.566w`. Every card's penumbra was therefore **1.46 times wider above and
 * below than at the sides**, for no physical reason whatever: a disc light
 * throws the same penumbra on every edge of a rectangle.
 *
 * The right answer is the textbook one and is barely more code. Push each
 * *edge* out along its own normal and intersect the neighbours — no angles, no
 * bisector formula to get the wrong way round, and it is exact for every convex
 * polygon rather than only for rectangles. (The bisector form, `w / sin(θ/2)`
 * along the outward bisector, is the same answer; it is easier to write down
 * and much easier to write down with `cos` where `sin` belongs.)
 *
 * Convex is the whole domain: these are card faces, projected. A projective map
 * takes a convex quad to a convex quad, so it stays true however the camera
 * moves.
 */
object Outset {

    /**
     * [corners], moved out by [by] on every side. Negative shrinks.
     *
     * Returns the shape unchanged when [by] is zero, and — the case that
     * matters — when the offset would turn the shape inside out. A ring
     * inset past its own inradius is a self-intersecting bow-tie, and the
     * previous guard could not catch it because it measured the half-diagonal:
     * on a card that is 52.1 where the inradius is 29.5, so an inset was
     * allowed to eat 1.6 times the shape's short half-axis.
     */
    fun of(corners: List<Vec2>, by: Float): List<Vec2> {
        if (corners.size < 3) return corners
        if (by == 0f) return corners
        if (by < 0f && -by >= inradius(corners)) return corners

        return List(corners.size) { i ->
            val previous = corners[(i + corners.size - 1) % corners.size]
            val here = corners[i]
            val next = corners[(i + 1) % corners.size]
            offsetVertex(previous, here, next, by, winding(corners))
        }
    }

    /**
     * The largest inset the shape survives: the distance from its centroid to
     * the nearest *edge*, not to the nearest corner.
     *
     * Not the true inradius of an arbitrary polygon — that is the largest
     * inscribed circle and needs a search — but it is exact for anything
     * centrally symmetric, which a card face is, and it is a lower bound
     * everywhere else. A lower bound is the safe side of this guard.
     */
    fun inradius(corners: List<Vec2>): Float {
        if (corners.size < 3) return 0f
        val middle = centroid(corners)
        var nearest = Float.MAX_VALUE
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val edge = Vec2(b.x - a.x, b.y - a.y)
            val length = sqrt(edge.x * edge.x + edge.y * edge.y)
            if (length < EPSILON) continue
            // Distance from the centroid to the line through a and b.
            val cross = abs(edge.x * (a.y - middle.y) - edge.y * (a.x - middle.x))
            nearest = minOf(nearest, cross / length)
        }
        return if (nearest == Float.MAX_VALUE) 0f else nearest
    }

    /** Twice the signed area. Positive one way round the shape, negative the other. */
    fun signedArea(corners: List<Vec2>): Float {
        var total = 0f
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            total += a.x * b.y - b.x * a.y
        }
        return total / 2f
    }

    fun centroid(corners: List<Vec2>): Vec2 {
        var x = 0f
        var y = 0f
        corners.forEach { x += it.x; y += it.y }
        return Vec2(x / corners.size, y / corners.size)
    }

    // ---- the arithmetic ----------------------------------------------------------

    /** +1 if the corners run anticlockwise in a y-down frame, -1 if clockwise. */
    private fun winding(corners: List<Vec2>): Float = if (signedArea(corners) >= 0f) 1f else -1f

    /**
     * Where the two offset edge lines meeting at [here] cross.
     *
     * Both edges are pushed out along their own normals and the new vertex is
     * their intersection, which is the definition of an offset polygon. When the
     * two edges are very nearly parallel their lines meet a long way off, so
     * that case falls back to the shared normal — the limit the intersection is
     * approaching, and the only branch that keeps a nearly-degenerate quad from
     * flinging a corner to infinity. A card seen almost edge-on produces exactly
     * that quad, twice per flip.
     */
    private fun offsetVertex(previous: Vec2, here: Vec2, next: Vec2, by: Float, winding: Float): Vec2 {
        val into = normal(previous, here, winding)
        val outOf = normal(here, next, winding)
        if (into == null || outOf == null) return here

        // The bisector of the two edge normals, and how far along it the two
        // offset lines actually cross: `by / cos(half the angle between them)`.
        val bx = into.x + outOf.x
        val by2 = into.y + outOf.y
        val length = sqrt(bx * bx + by2 * by2)
        if (length < EPSILON) return here // a spike: the two edges double back

        val bisector = Vec2(bx / length, by2 / length)
        // cos of the half-angle, which is exactly the bisector projected onto
        // either normal — no trigonometry needed to get it.
        val cosHalf = bisector.x * into.x + bisector.y * into.y
        val reach = if (cosHalf < MIN_COS_HALF) by / MIN_COS_HALF else by / cosHalf
        return Vec2(here.x + bisector.x * reach, here.y + bisector.y * reach)
    }

    /** The outward unit normal of the edge from [a] to [b]. Null if it has no length. */
    private fun normal(a: Vec2, b: Vec2, winding: Float): Vec2? {
        val ex = b.x - a.x
        val ey = b.y - a.y
        val length = sqrt(ex * ex + ey * ey)
        if (length < EPSILON) return null
        // Rotate the edge a quarter turn; the winding says which quarter is out.
        return Vec2(ey / length * winding, -ex / length * winding)
    }

    /**
     * How sharp a corner may be before its offset is capped.
     *
     * A vertex whose two edges meet at less than about sixteen degrees would
     * otherwise be thrown out by seven times the offset distance and leave a
     * spike. Capping is the standard miter limit and it is what keeps a
     * near-degenerate projected quad from producing a shadow with a whisker.
     */
    private const val MIN_COS_HALF = 0.14f

    private const val EPSILON = 1e-4f
}
