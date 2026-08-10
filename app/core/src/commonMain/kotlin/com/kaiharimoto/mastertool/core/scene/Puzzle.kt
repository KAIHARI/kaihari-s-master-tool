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
 * It does cost the paint order something, and the version that did not is the
 * one this replaced. Standing alone on the bare left of the desk, it looked as
 * though nothing on the stage could be both in front of it and behind something
 * else, so it could simply be painted last — which quietly assumed a camera in
 * front of the table. Yaw here is free: walk past about 145° and you are behind
 * the room's own wall, where the header above the window really is nearer than
 * the desk, and the puzzle drew straight through it. It joins `ScenePainter`'s
 * sort as a box instead.
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
     * Balanced on its point, an inverted pyramid **shows you nothing but its
     * base**: from anywhere above, each flank slopes inward and away and is
     * occluded by the top face's own edge, so what reaches the screen is a flat
     * gold square with a ring lying on it. Every part of the object that says
     * *pyramid* — the four flanks, the chamfer round the top, the bail standing
     * proud — is in the twelve pixels the top face does not cover.
     *
     * Propped, all four read. It is no more balanced than it was — the point it
     * used to stand on is seven per cent of a card wide — and it is the way a
     * person actually leaves a pendant on a desk.
     *
     * A `rotX` and not a `rotZ`, applied **after** the spin in `Rot3`'s
     * Z-then-Y-then-X order, so a nudge turns it about its own leaned axis and
     * the silhouette swings rather than the object rocking.
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
     * Where it sits when nothing has touched it, which is the first of the poses
     * it can be nudged into rather than a case of its own.
     *
     * It used to be a height: the pose is the top face and the body hangs off it,
     * so the point rested when the pose stood a whole [tall] above the desk. That
     * was arithmetic about an object standing square on its own point, and it
     * stopped being true the moment it was propped — see [LEAN] and [standing].
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
     * The pose is the plane of the **top** face, so the lathe hangs below it and
     * every height in the profile is negative or zero. Nothing adds [tall] back:
     * what puts the solid on the desk is [standing], which builds the shape,
     * measures its lowest corner and drops it — see there for why that has to be
     * per pose.
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
     * is deliberately the *worst* case: every turn, at both ends of the lift. A
     * prop that clears the felt only while it happens to be still is a prop that
     * stands over a card the first time somebody touches it.
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
     * The two parts it is drawn as, **in paint order, nearest the camera last.**
     *
     * Two rather than one because the ring is a torus and the body is a pyramid,
     * and the renderer orders the faces *inside* one part by the depth of their
     * own centres — the painter's algorithm, which is right for a convex solid
     * and has no meaning across two of them.
     *
     * ## Why the order is decided here and not by `ScenePainter`
     *
     * Because the painter would decline. It orders boxes by a separating axis,
     * and these two share every one: the bail sits in the middle of the top face
     * and is wholly inside the leaned pyramid's own bounding box, so `behind`
     * has no opinion about the pair and the sort falls through to a depth
     * comparison nobody has proved anything about. It was written here as
     * "z-disjoint by construction", which was true of the object standing upright
     * and stopped being true the day it was propped — the kind of comment that
     * goes on being read long after it stopped being a fact.
     *
     * ## And why one boolean is exact
     *
     * The two solids *are* separated — not by an axis but by a **plane**, the
     * plane of the body's own top face, which the bail stands on and the body
     * lies entirely under. For two convex bodies either side of a plane the
     * painter's order is decided by which side the eye is on, and nothing else,
     * so the whole question is whether the top face is turned toward the camera.
     *
     * It is not always. At the envelope's steepest pitch the propped top face
     * turns away by about five degrees, and at that seat the body is in front of
     * the ring and has to be painted after it. Asserting "the bail is always on
     * top" would have been wrong at exactly one corner of the camera's range,
     * which is the sort of thing that ships.
     */
    fun parts(layout: BoardLayout, pose: Pose3, eyeAt: Vec3): List<Part> {
        val body = solid(layout, pose)
        val ring = bail(layout, pose)
        val front = Part(SceneBox.around(ring), ring)
        val back = Part(SceneBox.around(body), body)
        // The top cap is the last face `Turned` emits, and its plane is the one
        // that separates the two.
        val towardTheRing = body.last().facingFrom(eyeAt) > 0f
        return if (towardTheRing) listOf(back, front) else listOf(front, back)
    }

    /** One convex piece of it: what to sort it by, and what to draw. */
    data class Part(val box: SceneBox, val faces: List<Face>)

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
