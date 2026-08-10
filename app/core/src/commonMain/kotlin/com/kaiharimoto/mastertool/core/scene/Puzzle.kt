package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face
import com.kaiharimoto.mastertool.core.render.Ring
import com.kaiharimoto.mastertool.core.render.Rot3
import com.kaiharimoto.mastertool.core.render.Turned
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one thing on this desk that answers a finger.
 *
 * ## Why it is not a `ScenePiece`
 *
 * Everything else in the room is furniture: solved once for a board size and a
 * scene, remembered, and never touched again. This moves, and a moving object
 * cannot live in a value that is deliberately recomputed twice a day. So it is
 * built from a **pose** the way a card is, and the screen holds that pose in the
 * same plain object a card's pose lives in.
 *
 * It costs nothing in the paint order either. It stands well clear of the mat on
 * the opposite side of the desk from the lamp, so nothing on the stage can be
 * both in front of it and behind something else — which means it can simply be
 * painted last, and `ScenePainter` never has to have an opinion about a shape
 * that is not a box.
 *
 * ## What it is made of
 *
 * Two parts, both off the same lathe. The **body** is a four-sided turn — which
 * is a pyramid, apex down — with a chamfer round its top edge, phased a half
 * segment so its flats face the room rather than its corners. The **bail** is
 * the ring the chain goes through: a torus, turned about an axis lying in the
 * desk, standing on top of the body.
 *
 * It was one `CardSolid.slab` call before, and the difference between the two is
 * not the count of faces. A slab hangs its body straight down the *stage's* z
 * whatever pose it is given, because that is a fact about the felt a card is
 * resting on; so the puzzle could be spun and could never be tipped, and the
 * chamfer and the ring had nowhere to live. `Turned` poses every vertex, so the
 * shape is the shape.
 *
 * ## Why it still may only spin
 *
 * Not arithmetic any more — taste, and a hit test. It turns a third of a turn
 * per nudge because a square has four-fold symmetry, and a thing that rose and
 * *tumbled* would be performing rather than answering. `docs/DESIGN.md` §11 is
 * where that gets argued if it ever should change; what used to be written there
 * as an arithmetic limit is now only a decision.
 */
object Puzzle {

    /**
     * Which rooms it is in, which is one of them.
     *
     * A line in core rather than a `when` in the composable that draws it,
     * because it is not a rendering decision: `docs/DESIGN.md` §11 grants the
     * desk scenes the right to hold things that are there because they are nice,
     * and grants it to them *only*. [Scene.MINIMAL] is the handbook's stage and
     * an easter egg standing on it would be the first piece of decoration ever
     * to reach it.
     */
    fun standsIn(scene: Scene): Boolean = scene == Scene.DESK

    /** How far past the mat's left edge it stands, in card widths. */
    const val OUT = 1.08f

    /** And how far down the mat's depth, as a fraction of it. */
    const val ALONG = 0.12f

    /** The base, in card widths, and how far the point hangs below it. */
    const val WIDE = 1.0f
    const val TALL = 1.15f

    /**
     * How much of the base the bottom face keeps.
     *
     * Not zero. A true point is arithmetically fine — the side quads become
     * triangles and the normals stay exact — but it is a single pixel balancing
     * an object a hand wide, and the real thing has a small flat there too.
     */
    const val APEX = 0.07f

    /**
     * A square turn is described by its circumcircle and drawn on its flats, so
     * every half-width here is a radius divided by this.
     */
    const val DIAGONAL = 1.41421356f

    /**
     * The chamfer round the top edge: how far down it starts, in card widths,
     * and how much of the width it takes in.
     *
     * Four pixels of bevel at the reference size, and it is the cheapest thing
     * on the object. An edge where two flats meet at nothing is what a shape
     * modelled out of paper has; a real cast or hammered thing has a facet
     * there, and that facet catches a different amount of light from either side
     * of it, which is the only reason an eye reads the join as an edge at all.
     */
    const val CHAMFER = 0.04f
    const val CHAMFER_IN = 0.06f

    /**
     * The bail: how far out the ring's centre line runs, and how thick its wire
     * is, both in card widths.
     *
     * Small. It is the one part of this object that is not the pyramid, and its
     * whole job is silhouette — it says *pendant* from across the room and is
     * about eleven pixels across at the reference size. Bigger, it reads as a
     * handle and the thing becomes a bucket.
     */
    const val BAIL = 0.115f
    const val BAIL_WIRE = 0.032f

    /**
     * How far back it is propped, in degrees.
     *
     * Balanced on its point, an inverted pyramid **hides its own sides**: from
     * anywhere above, each flank slopes inward and away and is occluded by the
     * top face's own edge. At the seat this stage opens at, the four faces that
     * carry everything worth carving are a twelve-pixel strip and the Eye of
     * Wdjat drawn on one of them is six pixels of scratch. That was measured
     * rather than feared — it was drawn there first.
     *
     * So it is propped back, which turns the near flank up toward the room and
     * lays the top face away from it. It is no more balanced than it was — the
     * point it used to stand on is seven per cent of a card wide — and it is the
     * way a person actually leaves a pendant on a desk.
     *
     * A `rotX` and not a `rotZ`, applied **after** the spin in `Rot3`'s
     * Z-then-Y-then-X order, so a nudge turns it about its own leaned axis and
     * the eye swings out of the room and back rather than the object rocking.
     */
    const val LEAN = 34f

    /** How far it rises when it is nudged, in card widths. */
    const val LIFT = 0.42f

    /**
     * How far it turns per nudge.
     *
     * Not a quarter turn, and that is the one number here with a reason rather
     * than a taste behind it: a square pyramid has four-fold symmetry, so a
     * quarter turn of it is the identity and a nudge would do nothing anybody
     * could see. A third of a turn moves the silhouette every time.
     */
    const val TURN = 120f

    /** Where it stands, in mat pixels. */
    fun foot(layout: BoardLayout): Vec2 {
        val mat = Scenery.mat(layout)
        return Vec2(
            x = mat.left - layout.cardWidth * OUT,
            y = mat.top + mat.height * ALONG,
        )
    }

    fun width(layout: BoardLayout): Float = layout.cardWidth * WIDE

    fun tall(layout: BoardLayout): Float = layout.cardWidth * TALL

    /**
     * Where it sits when nothing has touched it.
     *
     * The pose is the **top** face, because that is the face `CardSolid.slab`
     * builds from and the body hangs down off it. So the point rests on the desk
     * exactly when the pose stands a whole height above it.
     */
    fun rest(layout: BoardLayout): Pose3 = stirred(layout, turns = 0, lifted = 0f)

    /**
     * The same pose, standing on the desk.
     *
     * However it is leaned and however far it has been turned, the lowest corner
     * of the solid touches z = 0 — solved by building it, measuring it and
     * dropping it, rather than by a height somebody worked out once. It has to
     * be per *pose* and not per object: `Rot3` spins about the object's own axis
     * before tipping it, so which corner is lowest changes with the turn, and
     * dropping by one number sank the puzzle three quarters of a pixel into the
     * wood on every nudge — which a test caught and a picture would not have.
     */
    private fun standing(layout: BoardLayout, pose: Pose3): Pose3 {
        val under = solid(layout, pose).minOf { face -> face.corners.minOf { it.z } }
        return pose.copy(position = pose.position + Vec3(0f, 0f, -under))
    }

    /**
     * The pose for a puzzle that has been nudged [turns] times and is [lifted]
     * of the way up.
     *
     * A pure function of two numbers the screen owns, so that "where the puzzle
     * is" has exactly one definition and the thing being drawn, the thing being
     * hit-tested and the thing casting a shadow cannot come apart.
     */
    fun stirred(layout: BoardLayout, turns: Int, lifted: Float): Pose3 {
        val foot = foot(layout)
        val turned = Pose3(
            position = Vec3(foot.x, foot.y, 0f),
            rotX = LEAN,
            rotZ = turns * TURN,
        )
        val down = standing(layout, turned)
        return down.copy(
            position = down.position + Vec3(0f, 0f, layout.cardWidth * LIFT * lifted),
        )
    }

    /**
     * The body: a square turn hanging apex-down off the pose, with a chamfer.
     *
     * The pose is still the **top** face, so the lathe stands a whole height
     * below it and the profile runs upward from the point — which is why every
     * height here is negative before [tall] is added back.
     *
     * A square turn's `radius` is its *circumcircle*, so the half-width of a
     * flat is `radius / sqrt(2)`: the numbers below are divided accordingly and
     * the shape is the shape the slab drew, to a pixel, apart from the chamfer.
     * Phased forty-five degrees because at four sides that is the difference
     * between a flat facing the room and an edge facing it, and a pyramid seen
     * corner-on has no face to put an eye on.
     */
    fun solid(layout: BoardLayout, pose: Pose3): List<Face> {
        val half = width(layout) / 2f * DIAGONAL
        val body = tall(layout)
        val chamfer = layout.cardWidth * CHAMFER
        return Turned.solid(
            pose = pose,
            profile = listOf(
                Ring(half * APEX, -body),
                Ring(half, -chamfer),
                Ring(half * (1f - CHAMFER_IN), 0f),
            ),
            sides = 4,
            phase = 45f,
        )
    }

    /**
     * The ring the chain goes through, standing on the body's top face.
     *
     * A torus, and it is turned about an axis that lies **in the desk** rather
     * than up out of it — `rotX = 90` takes the lathe's own vertical onto the
     * stage's y, so the hole faces the player. That is only expressible because
     * `Turned` poses every vertex; the slab this used to be could not have held
     * it at any angle.
     *
     * It is drawn as part of the puzzle rather than beside it, and it is not a
     * `ScenePiece.mesh`, because a torus is not convex. At this size — a
     * seventh of a card across — its own far side is a couple of pixels behind
     * its near one, which is the whole reason that is affordable here and would
     * not be on a lampshade.
     */
    fun bail(layout: BoardLayout, pose: Pose3): List<Face> {
        val card = layout.cardWidth
        val ring = card * BAIL
        val wire = card * BAIL_WIRE
        // Laid down and then turned, which is `rotY` and not `rotZ`: the lathe's
        // own vertical goes to `(sin b, -cos b, 0)` once `rotX` has tipped it a
        // quarter turn, so the *second* angle in the order Z-then-Y-then-X is
        // the one that swings a horizontal axis round. Given `rotZ` instead, the
        // ring faces the player and stays facing the player while the body under
        // it turns, which is a bail somebody has glued on.
        val stand = Pose3(
            position = Rot3.place(pose, Vec3(0f, 0f, ring + wire)),
            rotX = 90f,
            rotY = pose.rotZ,
        )
        val turns = 8
        return Turned.solid(
            pose = stand,
            profile = (0 until turns).map { step ->
                val around = step * (2f * PI.toFloat() / turns)
                Ring(radius = ring + wire * cos(around), height = wire * sin(around))
            },
            sides = 10,
            closed = true,
        )
    }

    /**
     * Everything it could ever occupy, as a box.
     *
     * Only ever asked whether it clears the mat and the rest of the room, so it
     * is deliberately the *worst* case: turned to its diagonal and lifted all the
     * way. A prop that clears the felt only while it happens to be still is a
     * prop that stands over a card the first time somebody touches it.
     */
    fun reach(layout: BoardLayout): SceneBox {
        // Solved rather than derived, now that the object leans: the extent of a
        // square pyramid propped thirty-four degrees back and spun to an
        // arbitrary angle is not a closed form anybody should be writing by
        // hand, and the version that *was* written by hand — the square's own
        // diagonal — is exactly the kind of thing that stays true until the day
        // a shape changes underneath it.
        //
        // Every turn at both ends of the lift, which is the worst case in one
        // pass, because the pose is linear in the lift and periodic in the turn.
        // It costs a dozen small solids once per board size.
        var box: SceneBox? = null
        (0 until 12).forEach { turns ->
            listOf(0f, 1f).forEach { lifted ->
                val pose = stirred(layout, turns, lifted)
                val here = SceneBox.around(solid(layout, pose) + bail(layout, pose))
                box = box?.let {
                    SceneBox(
                        min = Vec3(
                            minOf(it.min.x, here.min.x),
                            minOf(it.min.y, here.min.y),
                            minOf(it.min.z, here.min.z),
                        ),
                        max = Vec3(
                            maxOf(it.max.x, here.max.x),
                            maxOf(it.max.y, here.max.y),
                            maxOf(it.max.z, here.max.z),
                        ),
                    )
                } ?: here
            }
        }
        val solved = box ?: return SceneBox(Vec3.Zero, Vec3.Zero)
        // Down to the desk, because it stands on it and a box that floats a
        // thousandth above the wood is a box the floor can sort in front of.
        return SceneBox(min = Vec3(solved.min.x, solved.min.y, 0f), max = solved.max)
    }

    /**
     * The two parts it is drawn as, nearest the desk first.
     *
     * Two rather than one because the ring is a torus and the body is a
     * pyramid, and the renderer orders the faces *inside* one part by the depth
     * of their own centres — the painter's algorithm, which is right for a
     * convex solid and wrong across two of them. Handed the whole object as one
     * list, a face of the ring at the back of the hole sorts in front of the top
     * of the pyramid it is standing on.
     *
     * They are z-disjoint by construction — the ring rests on the body's top
     * face — so `ScenePainter` has an axis that separates them from any seat,
     * which is the same thing the room's own pieces rely on and the same reason.
     */
    fun parts(layout: BoardLayout, pose: Pose3): List<Part> {
        val body = solid(layout, pose)
        val ring = bail(layout, pose)
        return listOf(
            Part(SceneBox.around(body), body, marked = body.getOrNull(EYE_FACE)),
            Part(SceneBox.around(ring), ring),
        )
    }

    /**
     * Which facet the Eye of Wdjat is cast into.
     *
     * [Turned.solid] emits its bands first, in profile order, `sides` at a time
     * — so face zero is the first flank of the first band, which is the long one
     * running from the point up to the chamfer. And with `sides = 4` and a
     * forty-five degree phase, that facet's normal points along **+y**, which on
     * this stage is straight at the player. So the eye faces the room when the
     * puzzle has not been touched, and a nudge takes it away and brings it back
     * — which is the best thing a third of a turn could do.
     */
    const val EYE_FACE = 0

    /** One convex piece of it: what to sort it by, what to draw, and what is on it. */
    data class Part(
        val box: SceneBox,
        val faces: List<Face>,
        /** The face carrying the eye, by identity, or null on a part with none. */
        val marked: Face? = null,
    )

    /**
     * Where on the eye's facet the eye actually goes.
     *
     * How far down the flank the mark starts and ends, and how far in from each
     * sloping side, as fractions of the facet.
     */
    const val EYE_FROM = 0.03f
    const val EYE_TO = 0.72f
    const val EYE_INSET = 0.04f

    /**
     * The quad the unit square is mapped onto, clockwise from its own top left.
     *
     * **Not the facet's own four corners**, and that was the first version. A
     * flank of this pyramid runs from a full-width top edge down to the seven
     * per cent flat at the point, and a homography onto a trapezoid that nearly
     * degenerates does what a homography is supposed to do: it crushes the
     * square toward the narrow end, so the eye came out as a six-pixel smudge
     * sitting on the tip. A projective map's midline is where the *projective*
     * midpoint is, which on a 1:0.07 taper is nowhere near the middle.
     *
     * So the eye gets its own patch, cut out of the flank by walking down the
     * two sloping edges. It still tapers, because the face does and an engraving
     * on a pyramid does too — just not to nothing.
     *
     * The corner order is the other half of this and it is not guessable.
     * `Turned` winds a band's face as low@side, low@next, high@next, high@side,
     * so 0 and 1 are at the point and 2 and 3 at the top — and *which of each
     * pair is on the left* comes from the phase: at forty-five degrees `side`
     * sits at 45° and `next` at 135°, which in this frame puts `side` on the
     * right. Off by one and the eye is upside down; the pair the wrong way round
     * and it is mirrored, which is the failure `docs/LOOP.md` iteration 3 found
     * on the deck's own count.
     */
    fun eyeQuad(face: Face): List<Vec3> {
        if (face.corners.size < 4) return emptyList()
        // Down the left edge (top-left to bottom-left) and the right edge.
        fun down(top: Vec3, point: Vec3, t: Float) = top + (point - top) * t
        fun across(left: Vec3, right: Vec3, t: Float) = left + (right - left) * t
        val topLeft = down(face.corners[2], face.corners[1], EYE_FROM)
        val topRight = down(face.corners[3], face.corners[0], EYE_FROM)
        val lowLeft = down(face.corners[2], face.corners[1], EYE_TO)
        val lowRight = down(face.corners[3], face.corners[0], EYE_TO)
        return listOf(
            across(topLeft, topRight, EYE_INSET),
            across(topRight, topLeft, EYE_INSET),
            across(lowRight, lowLeft, EYE_INSET),
            across(lowLeft, lowRight, EYE_INSET),
        )
    }

    /**
     * Whether a finger landed on it.
     *
     * The test is against the **flattened silhouette** rather than the
     * footprint, and the difference is the whole reason this function exists: a
     * solid a hand tall does not appear on screen where its base is, and a hit
     * test against the base would miss it by its own height at every seat.
     *
     * It works because [StagePlane.flatten] answers in the mat's own coordinates
     * — the point on the felt that will *look* like a point in the air once the
     * camera has run — which is precisely the frame `MatInput` has already
     * unprojected the finger into. So both sides of the comparison are in the
     * one frame the whole stage computes in, and no screen coordinate is
     * involved on either side.
     *
     * Only the faces the camera can see are tested, because a solid's silhouette
     * is the union of exactly those.
     */
    fun holds(layout: BoardLayout, pose: Pose3, plane: StagePlane, eye: Vec3, at: Vec2): Boolean {
        val eyeAt = plane.eyePoint(eye)
        return CardSolid.visible(solid(layout, pose), eyeAt).any { face ->
            val outline = face.corners.map { corner ->
                val flat = plane.flatten(corner)
                Vec2(flat.x, flat.y)
            }
            encloses(outline, at)
        }
    }

    /** The crossing count, which is the whole of a point-in-polygon test. */
    private fun encloses(outline: List<Vec2>, at: Vec2): Boolean {
        var inside = false
        var previous = outline.size - 1
        outline.indices.forEach { index ->
            val a = outline[index]
            val b = outline[previous]
            if ((a.y > at.y) != (b.y > at.y) &&
                at.x < (b.x - a.x) * (at.y - a.y) / (b.y - a.y) + a.x
            ) {
                inside = !inside
            }
            previous = index
        }
        return inside
    }
}
