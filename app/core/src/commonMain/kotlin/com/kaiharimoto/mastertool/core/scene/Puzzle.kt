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
    fun rest(layout: BoardLayout): Pose3 {
        val foot = foot(layout)
        return Pose3(position = Vec3(foot.x, foot.y, tall(layout)))
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
        val at = rest(layout)
        return at.copy(
            position = at.position + Vec3(0f, 0f, layout.cardWidth * LIFT * lifted),
            rotZ = turns * TURN,
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
        val foot = foot(layout)
        // The diagonal, because a square turned forty-five degrees is that much
        // wider than its side.
        val half = width(layout) * 0.7072f
        val top = tall(layout) + layout.cardWidth * (LIFT + 2f * (BAIL + BAIL_WIRE))
        return SceneBox.standing(
            left = foot.x - half,
            top = foot.y - half,
            right = foot.x + half,
            bottom = foot.y + half,
            floor = 0f,
            ceiling = top,
        )
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
            Part(SceneBox.around(body), body),
            Part(SceneBox.around(ring), ring),
        )
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
