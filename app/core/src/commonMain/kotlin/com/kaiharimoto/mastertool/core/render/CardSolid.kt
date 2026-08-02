package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.exp

/**
 * One flat face of a solid: its corners in stage space, and which way it looks.
 *
 * The corners come round in order, so a renderer can walk them straight into a
 * path without knowing anything about what it is drawing.
 */
data class Face(
    val corners: List<Vec3>,
    val normal: Vec3,
) {
    /** How square-on the face is to an eye looking along [eye]. Negative is away. */
    fun facing(eye: Vec3 = Vec3.Toward): Float = normal dot eye

    val centre: Vec3
        get() = corners.fold(Vec3.Zero) { sum, c -> sum + c } / corners.size.toFloat()
}

/**
 * A card as a solid rather than a picture of one.
 *
 * A playing card is a slab: two printed faces and four white edges of card
 * stock between them. Nothing in the app drew those edges, and their absence
 * is most of why a stack of cards used to read as several rectangles printed
 * on the felt rather than as a pile of things.
 *
 * The slab is built in the card's own coordinates with **the printed face at
 * z = 0 and the body hanging behind it**, so `Rot3.place(pose, Vec3.Zero)` is
 * still exactly where the composable draws the card and the geometry can be
 * added underneath an existing card without moving it by a pixel.
 */
object CardSolid {

    /**
     * A card's thickness, as a share of its width.
     *
     * 0.3 mm over 59 mm, which is a real card measured. Kept as a ratio so it
     * survives every card size the fitter can solve for.
     */
    const val THICKNESS_RATIO = 0.00508f

    /** How much a pile is exaggerated near the bottom of the curve. */
    const val PILE_EXAGGERATION = 3.0f

    /** The tallest a pile is ever drawn, as a share of a card's width. */
    const val PILE_CEILING = 0.22f

    /** The thickness of one card, in pixels, for a card drawn [cardWidth] wide. */
    fun thickness(cardWidth: Float): Float = cardWidth * THICKNESS_RATIO

    /**
     * How tall a pile of [count] cards stands off the surface under it.
     *
     * Not `count × thickness`, and the reason is the tilt. Everything with a
     * height on this table projects to `z·sin 15°`, which divides it by about
     * four: a true three-card pile is a millimetre, so a quarter of a
     * millimetre of screen, so nothing. A pile's height is therefore notation
     * — but notation that stays *honest at both ends*, which a flat multiplier
     * is not. Exaggerate by three and a sixty-card deck stands half a card
     * tall; cap it flat and every pile past twenty looks identical.
     *
     * So the two are one curve: exponential saturation toward [PILE_CEILING],
     * which is very nearly the flat exaggeration while a pile is small and
     * bends over as it grows. Monotonic, so a pile never fails to grow when a
     * card is added to it, and it never becomes a tower.
     */
    fun pileDepth(count: Int, cardWidth: Float): Float {
        if (count <= 0 || cardWidth <= 0f) return 0f
        val ceiling = cardWidth * PILE_CEILING
        val unbounded = thickness(cardWidth) * count * PILE_EXAGGERATION
        return ceiling * (1f - exp(-unbounded / ceiling))
    }

    /**
     * The four corners of the printed face, clockwise from the top-left.
     *
     * Clockwise on screen, where +y is down — so this is the order a path
     * wants, not the order a maths textbook would give.
     */
    fun face(pose: Pose3, width: Float, height: Float, atDepth: Float = 0f): List<Vec3> {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        return listOf(
            Vec3(-halfWidth, -halfHeight, -atDepth),
            Vec3(halfWidth, -halfHeight, -atDepth),
            Vec3(halfWidth, halfHeight, -atDepth),
            Vec3(-halfWidth, halfHeight, -atDepth),
        ).map { Rot3.place(pose, it) }
    }

    /**
     * The whole slab: the printed face, the four edges, and the back.
     *
     * [depth] is how far the body extends behind the face — one card's
     * thickness for a card, a pile's worth for a pile. Faces come back in
     * back-to-front order for a viewer in front of the card, so a renderer that
     * simply draws them all in sequence gets a correct-looking solid even
     * before it culls anything.
     */
    fun slab(pose: Pose3, width: Float, height: Float, depth: Float): List<Face> {
        val front = face(pose, width, height, atDepth = 0f)
        val back = face(pose, width, height, atDepth = depth)

        fun side(a: Int, b: Int, normal: Vec3) = Face(
            // Round the loop: along the front edge, down the body, back along
            // the rear edge. A quad, always, so it can never self-intersect.
            corners = listOf(front[a], front[b], back[b], back[a]),
            normal = Rot3.rotate(pose, normal),
        )

        return listOf(
            Face(back.reversed(), Rot3.rotate(pose, Vec3(0f, 0f, -1f))),
            side(0, 1, Vec3(0f, -1f, 0f)),   // the far edge
            side(1, 2, Vec3(1f, 0f, 0f)),    // the right edge
            side(3, 2, Vec3(0f, 1f, 0f)),    // the near edge
            side(0, 3, Vec3(-1f, 0f, 0f)),   // the left edge
            Face(front, Rot3.rotate(pose, Rot3.FaceNormal)),
        )
    }

    /**
     * How square-on a face has to be before it is worth drawing at all.
     *
     * Not a tolerance for taste — a floating-point one. A card turned over is
     * `rotY = 180`, `sin` of which is about minus ten-to-the-eight rather than
     * zero, so all four of its edges come out a hair on the visible side of
     * exactly edge-on. Culling on a bare `> 0` then draws four zero-width
     * slivers of card stock around a face-down card, which is one of those
     * artefacts that looks like a rendering bug because it is one.
     */
    private const val EDGE_ON = 1e-4f

    /**
     * Only the faces an eye at [eye] can see.
     *
     * Back-face culling, which for a slab is not an optimisation but the whole
     * of the effect: draw all six and the edges on the far side paint over the
     * near ones, and the pile turns inside out.
     */
    fun visible(faces: List<Face>, eye: Vec3 = Vec3.Toward): List<Face> =
        faces.filter { it.facing(eye) > EDGE_ON }
}
