package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec2

/**
 * Is this point inside that four-sided shape.
 *
 * ## Why this did not exist and now has to
 *
 * Everything on this stage was hit-tested against an **axis-aligned rectangle**,
 * and for most of the table that is exactly right: a card lying on the felt is a
 * rectangle in mat coordinates and `abs(dx) <= halfWidth` is the whole answer.
 *
 * A card that is *leaned* is not. The hand's cards pivot on their bottom edge
 * and stand up, so what you see is `CardSolid.face(pose)` put through
 * `StagePlane.flatten` — four corners, at two different heights, arriving on the
 * felt as a **quad**. The rectangle at the card's nominal point overlaps that
 * quad only near the pivot, which is why the hand could be grabbed by its bottom
 * edge and not by its top. `StagePlane.raise` is the fix for the *fan*, where
 * every card is flat at one height and the inverse is exact; it says in its own
 * KDoc that it is not the fix for a lean, and it is not.
 *
 * So the hit test becomes the same expression as the picture: the renderer draws
 * `flatten(face(pose))` and the hit test asks whether the finger is inside
 * `flatten(face(pose))`. Two things that must agree, written once.
 *
 * ## The test is the sign of four cross products
 *
 * For a convex quad wound consistently, a point is inside when it is on the same
 * side of all four edges. Sign rather than magnitude, so there is no divide and
 * nothing to normalise, and `>= 0` on both branches so a finger exactly on an
 * edge is inside — a card's edge belongs to the card.
 *
 * **Winding is not assumed.** `CardSolid.face` returns its corners clockwise in
 * the mat's frame, but `flatten` is a projection and a card seen from behind
 * comes back wound the other way. Accepting either is one `||` and removes a
 * whole class of "it works until you walk round the table".
 *
 * It does not check convexity. Every caller is a card, a card is convex, and a
 * test that could fail on a shape nobody can construct is a test nobody can read.
 */
object Quad {

    /**
     * Whether ([x], [y]) is inside the quad [corners], edges included.
     *
     * @param corners four points in order round the shape, either winding.
     *   Anything other than four points is not a quad and is not inside.
     */
    fun contains(corners: List<Vec2>, x: Float, y: Float): Boolean {
        if (corners.size != 4) return false

        var positive = true
        var negative = true
        // Whether this shape has any area at all from where the point is
        // standing. Without it a *collapsed* quad — four corners at one point,
        // which is what `StagePlane.flatten` makes of a card seen exactly
        // edge-on — leaves every cross product at zero, both accumulators true,
        // and the answer "inside" for every point on the table.
        var sided = false

        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            // Which side of the edge a→b the point falls on. Zero is *on* it,
            // and stays true for both accumulators so an edge counts as inside
            // whichever way the quad happens to be wound.
            val side = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            if (side < 0f) positive = false
            if (side > 0f) negative = false
            if (side != 0f) sided = true
            if (!positive && !negative) return false
        }
        return sided && (positive || negative)
    }
}
