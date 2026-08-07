package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
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

    /**
     * How much a pile is exaggerated near the bottom of the curve.
     *
     * Doubled from three, and the reason is a complaint rather than a
     * calculation: at three the arithmetic was *right* — a forty-card deck stood
     * within a pixel of its true height — and the table still read as a diagram,
     * because a true deck seen from fifteen degrees above is four millimetres of
     * screen. Honesty about the height of a stack of cardboard is not the thing
     * this stage is for. What it is for is telling you, at a glance, that the
     * graveyard has eleven cards in it and the deck has thirty, and that is a
     * question about *notation* — which is why the curve exaggerates at all.
     */
    const val PILE_EXAGGERATION = 6.0f

    /**
     * The tallest a pile is ever drawn, as a share of a card's width.
     *
     * Half a card width for a deck nobody could exhaust, against a real deck's
     * fifth of one. Raised with the exaggeration and for the same reason, and
     * raised by more than it, because the ceiling is what decides whether the
     * curve is still nearly linear where every pile anybody makes lives: a
     * ceiling that bites at three cards makes two and three the same height
     * again, which is the exact complaint the exaggeration was raised to answer.
     */
    const val PILE_CEILING = 0.5f

    /**
     * How close together two ruled card edges may be drawn, in screen pixels.
     *
     * A pile's side is not a smooth white band — it is a stack of individual
     * cards, and the lines between them are most of what says so. But the band
     * is a few pixels tall at a shallow camera and there is no honest way to
     * rule forty lines across it: past the point where two lines are closer than
     * this the renderer draws fewer of them, which is the same bargain the
     * height curve makes and is made in the same place so that both are visible
     * at once.
     */
    const val LAYER_MIN_SPACING = 1.7f

    /**
     * How many card edges to rule across a pile's side, given how long that side
     * comes out on screen.
     *
     * Zero for a single card: one card has no seam in it. Never more than the
     * pile has cards, and never so many that they merge into the band they are
     * drawn on.
     */
    fun layerLines(count: Int, edgePixels: Float): Int {
        if (count <= 1 || edgePixels <= 0f) return 0
        val room = (edgePixels / LAYER_MIN_SPACING).toInt()
        return minOf(count - 1, room).coerceAtLeast(0)
    }

    /** The thickness of one card, in pixels, for a card drawn [cardWidth] wide. */
    fun thickness(cardWidth: Float): Float = cardWidth * THICKNESS_RATIO

    /**
     * How tall a pile of [count] cards stands off the surface under it.
     *
     * Not `count × thickness`, and the reason is the tilt. Everything with a
     * height on this table projects to `z·sin θ`, which divides it by about
     * three: a true three-card pile is a millimetre, so a third of a
     * millimetre of screen, so nothing. A pile's height is therefore notation
     * — but notation that stays *honest at both ends*, which a flat multiplier
     * is not. Exaggerate and a sixty-card deck stands a card wide; cap it flat
     * and every pile past twenty looks identical.
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
     *
     * [atDepth] lowers those corners **straight down the stage's z**, and that
     * word straight is the whole of a bug that shipped twice. It used to lower
     * them along the card's own local −z and then rotate, which is a different
     * axis the moment the card is not lying flat and face up — and a face-down
     * card is turned a half circle about Y, which maps local −z to world *+z*.
     * So every face-down pile built its body **upward into the air**: a
     * forty-card deck drew a fifty-eight pixel slab standing on top of the card
     * it was supposed to be underneath, with its near-white sides splayed out
     * by [pileLean]. The graveyard and the banished pile are face-up and were
     * always right, which is exactly why it read as "the deck is broken".
     *
     * It got worse before it got better. `Shadows.cast` was taught to cast from
     * a solid's base by asking for this face at `atDepth = bodyDepth` — correct
     * reasoning, applied to an axis that pointed the wrong way, so the deck's
     * shadow moved from one pile height in the air to two.
     *
     * Which way a body hangs is a fact about **the surface the solid is resting
     * on**, not about which way its printed side happens to point. Those are two
     * questions and this function used to answer the second one when it was
     * asked the first.
     *
     * For a single card the two axes differ by the card's own tilt, which over
     * one card's thickness is a fraction of a pixel — so nothing about a card
     * lying, leaning or held changes, and the only visible difference is the one
     * that was wrong.
     */
    fun face(pose: Pose3, width: Float, height: Float, atDepth: Float = 0f): List<Vec3> {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val corners = listOf(
            Vec3(-halfWidth, -halfHeight, 0f),
            Vec3(halfWidth, -halfHeight, 0f),
            Vec3(halfWidth, halfHeight, 0f),
            Vec3(-halfWidth, halfHeight, 0f),
        ).map { Rot3.place(pose, it) }

        // Straight down in the *stage's* z, not along the card's own. See below.
        return if (atDepth == 0f) corners else corners.map { it - Vec3(0f, 0f, atDepth) }
    }

    /**
     * Which way a pile slouches, and how far, for a body [depth] deep.
     *
     * Nobody has ever squared up a graveyard. A stack of cards on a table is
     * pushed together by a hand and ends up a few millimetres out of true, and
     * that misalignment is *the* thing that reads as "several cards" from
     * directly above — which is the one camera angle where a pile's height, the
     * other cue, has been foreshortened to nothing.
     *
     * One direction for every pile on the table rather than a jitter per pile,
     * and this is a decision rather than laziness: cards on one table were
     * pushed about by one pair of hands, and stacks that each lean a different
     * way read as a rendering effect instead of as a room. Mostly toward the
     * player, because that is the edge the camera is on and the only edge whose
     * slouch anybody will see.
     *
     * It saturates almost at once. A lean that grew with the pile would make the
     * deck the messiest object on the table, and a deck is the one thing anybody
     * *does* square up; capped, every pile leans by the same few millimetres and
     * only the ones you can count are affected by it.
     */
    fun pileLean(depth: Float, cardWidth: Float): Vec3 {
        val reach = minOf(depth, cardWidth * LEAN_CEILING) * LEAN_RATIO
        return Vec3(0.45f * reach, 0.89f * reach, 0f)
    }

    /** How far out of true a pile goes, as a share of the height it stands. */
    private const val LEAN_RATIO = 0.38f

    /** And the height past which leaning any further would look like a mess. */
    private const val LEAN_CEILING = 0.12f

    /**
     * The whole slab: the printed face, the four edges, and the back.
     *
     * [depth] is how far the body extends behind the face — one card's
     * thickness for a card, a pile's worth for a pile. Faces come back in
     * back-to-front order for a viewer in front of the card, so a renderer that
     * simply draws them all in sequence gets a correct-looking solid even
     * before it culls anything.
     *
     * [lean] slides the *back* of the body sideways, which turns the four edges
     * from rectangles into parallelograms and is how a pile is told to slouch.
     * The face normals are left as the un-leaned solid's, deliberately: they are
     * used to light the faces and to cull them, and at the few degrees a
     * [pileLean] actually reaches, the error in the shading is far smaller than
     * the error of having every pile on the table stand to attention. A lean of
     * a quarter of the body's depth is about fourteen degrees.
     */
    fun slab(
        pose: Pose3,
        width: Float,
        height: Float,
        depth: Float,
        lean: Vec3 = Vec3.Zero,
    ): List<Face> {
        val front = face(pose, width, height, atDepth = 0f)
        val back = face(pose, width, height, atDepth = depth).map { it + lean }

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
     * How much screen a face has to cover before it is worth drawing, in square
     * pixels.
     *
     * [visible] culls a face the eye is *behind*. It cannot cull one the eye is
     * very nearly level with, because that face is still, technically, facing
     * you — and a quad seen from three degrees off its own plane is a hundred
     * and thirty-five pixels long and less than one pixel tall. Filled with card
     * stock at full brightness, that is not an edge. It is a bright white line
     * lying across whatever is behind it, and on a fanned hand it is five of
     * them, each running half a card past its own card onto its neighbour.
     *
     * The honest reading is that a surface you are level with presents no area
     * and should therefore contribute nothing. Area is also the only test that
     * catches this: the face's *normal* says it is comfortably visible, and the
     * disagreement between what the normal promises and what the projection
     * delivers is precisely the artefact.
     *
     * Two square pixels, which is a quad that could at best be a faint mark and
     * at worst is an aliasing accident.
     *
     * **What this does not do, said plainly, because it was once claimed to.**
     * Two square pixels only catches a face within about a quarter of a degree
     * of edge-on, which [EDGE_ON] very nearly caught already; the hairlines
     * that shipped in v1.2.19 measured a hundred and ten. Raising the number
     * would not fix them either — at a turned seat a card's genuinely visible
     * sides cover eighty to two hundred and forty, so any threshold high enough
     * to cull the artefact culls the geometry. What actually cured a leaned
     * card's edges landing away from its own picture was [Homography]: the
     * picture is now drawn through the same corners the edges are. This stays
     * as what it honestly is — a guard against a quad with no area at all, and
     * the degeneracy test that map needs before it is asked for.
     */
    const val MIN_DRAWN_AREA = 2f

    /**
     * The area a flattened polygon covers, by the shoelace formula.
     *
     * Unsigned, because the caller wants to know whether there is anything there
     * and not which way round it was wound.
     *
     * Measured in the mat's own coordinates, which is where its callers already
     * have their points and is *not* quite screen pixels — the mat's layer
     * magnifies by a further ten to fifteen per cent near the camera. For a
     * near-zero test that difference cannot change the answer; for anything
     * that wanted a true pixel count it would.
     */
    fun flatArea(points: List<Vec2>): Float {
        if (points.size < 3) return 0f
        var twice = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twice += a.x * b.y - b.x * a.y
        }
        return abs(twice) / 2f
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
