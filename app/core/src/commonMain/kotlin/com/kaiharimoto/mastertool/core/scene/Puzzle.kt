package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.Face

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
 * ## Why it is a truncated pyramid, apex down
 *
 * `CardSolid.slab` hangs a solid's body straight down the stage's z, which is a
 * fact about the surface a thing is resting on rather than about the thing. For
 * a card that is invisible; here it is the entire shape. A large square face at
 * the top and a nearly-vanished one at the bottom *is* an inverted pyramid, with
 * no machinery beyond one trailing parameter — see `CardSolid.slab`'s
 * `backScale`, and the note there about why the normals stay exact all the way
 * down to a point.
 *
 * ## Why it may only spin
 *
 * The same hanging body is why. A rotation about the stage's own vertical axis
 * commutes with a translation along it, so a solid spun that way is *bit-exactly*
 * the rotated solid — no shear at all. Tip it instead, about x or y, and the
 * body goes on hanging vertically while the face turns: a card's four
 * thousandths of a millimetre of that is a fraction of a pixel, and a hand's
 * width of pyramid is the whole silhouette. So it turns and it rises, and it
 * does not tumble. What would buy a tumble is a posed box in core with its own
 * eight corners, and nothing has yet needed one.
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

    /** Its six faces, for a pose. */
    fun solid(layout: BoardLayout, pose: Pose3): List<Face> = CardSolid.slab(
        pose = pose,
        width = width(layout),
        height = width(layout),
        depth = tall(layout),
        backScale = APEX,
    )

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
        val top = tall(layout) + layout.cardWidth * LIFT
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
