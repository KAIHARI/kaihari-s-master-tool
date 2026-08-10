package com.kaiharimoto.mastertool.core.scene

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.render.Face

/**
 * A rectangular box in the mat's own frame — the only shape the room is made of.
 *
 * ## Why this is not `CardSolid.slab`
 *
 * It very nearly is, and a test holds the two to agreeing wherever both can
 * answer. The difference is one assumption. A slab is a *card*: a flat plate in
 * some pose, with a thickness that hangs straight down the stage's z because
 * that is a fact about the felt the card is resting on rather than about which
 * way the card is pointing. That assumption is load-bearing for every pile on
 * the table and it is exactly wrong for a wall, which is a plate whose thickness
 * runs along y and whose extent runs up z. Rotating a slab to stand it up would
 * leave its thickness still hanging downward, so the wall would come out as a
 * sheared parallelepiped leaning into the room.
 *
 * The honest generalisation is not a rotated slab but an axis-aligned box, and
 * that is all a room needs. A desk is a box wide in x and y and thin in z; a
 * wall is a box wide in x and z and thin in y; a lamp is three small boxes.
 * Nothing in a bedroom seen from a chair needs to be at an angle to the desk it
 * is standing on, and the moment something does, it will be worth the rotation
 * it costs rather than being paid for by everything that does not.
 *
 * Axis-aligned also means the normals are exact rather than derived. `CardSolid`
 * has to read a face's normal off its corners because a leaning pile shears
 * them, and a fourteen-degree error there is the difference between drawing a
 * deck's wall and drawing a hole. Nothing here shears, so the six normals are
 * the six unit axes and cannot be anything else.
 *
 * [min] and [max] are corners, not a corner and a size, because every question
 * the room asks — is this piece clear of the mat, how high does the wall stand,
 * which face is this — is a question about where its sides are.
 */
data class SceneBox(val min: Vec3, val max: Vec3) {

    val centre: Vec3 get() = (min + max) * 0.5f

    /**
     * The six faces, outward-facing, ready for `CardSolid.visible` and the rig.
     *
     * Returned in the same order [CardSolid.slab] uses — back, far, right, near,
     * left, front — so that a reader moving between the two files is not
     * comparing two orders as well as two shapes. Nothing downstream depends on
     * it: the renderer culls by normal and paints by depth, both of which are
     * properties of a face rather than of its position in a list.
     */
    fun faces(): List<Face> = listOf(
        Face(
            listOf(
                Vec3(min.x, max.y, min.z), Vec3(max.x, max.y, min.z),
                Vec3(max.x, min.y, min.z), Vec3(min.x, min.y, min.z),
            ),
            Vec3(0f, 0f, -1f),
        ),
        Face(
            listOf(
                Vec3(max.x, min.y, max.z), Vec3(min.x, min.y, max.z),
                Vec3(min.x, min.y, min.z), Vec3(max.x, min.y, min.z),
            ),
            Vec3(0f, -1f, 0f),
        ),
        Face(
            listOf(
                Vec3(max.x, max.y, max.z), Vec3(max.x, min.y, max.z),
                Vec3(max.x, min.y, min.z), Vec3(max.x, max.y, min.z),
            ),
            Vec3(1f, 0f, 0f),
        ),
        Face(
            listOf(
                Vec3(min.x, max.y, max.z), Vec3(max.x, max.y, max.z),
                Vec3(max.x, max.y, min.z), Vec3(min.x, max.y, min.z),
            ),
            Vec3(0f, 1f, 0f),
        ),
        Face(
            listOf(
                Vec3(min.x, min.y, max.z), Vec3(min.x, max.y, max.z),
                Vec3(min.x, max.y, min.z), Vec3(min.x, min.y, min.z),
            ),
            Vec3(-1f, 0f, 0f),
        ),
        Face(
            listOf(
                Vec3(min.x, min.y, max.z), Vec3(max.x, min.y, max.z),
                Vec3(max.x, max.y, max.z), Vec3(min.x, max.y, max.z),
            ),
            Vec3(0f, 0f, 1f),
        ),
    )

    /**
     * All eight of them, for the questions that are about the whole box.
     *
     * Depth under `StagePlane.project` is linear in x, y and z, so the nearest
     * and furthest points of a box are always corners of it — which is what
     * makes sorting a room by "how close does this object come" a walk over
     * eight points rather than an argument about which face to trust.
     */
    fun corners(): List<Vec3> = listOf(
        Vec3(min.x, min.y, min.z), Vec3(max.x, min.y, min.z),
        Vec3(max.x, max.y, min.z), Vec3(min.x, max.y, min.z),
        Vec3(min.x, min.y, max.z), Vec3(max.x, min.y, max.z),
        Vec3(max.x, max.y, max.z), Vec3(min.x, max.y, max.z),
    )

    /** Whether this box overlaps [other] when both are flattened onto the felt. */
    fun overlapsOnFelt(other: SceneBox): Boolean =
        min.x < other.max.x && max.x > other.min.x &&
            min.y < other.max.y && max.y > other.min.y

    companion object {
        /**
         * A box from a rectangle on the felt and a range of heights.
         *
         * The way every piece of furniture in this room is actually described:
         * a footprint, and how far off the table it reaches. [top] above
         * [bottom] is the caller's job and the constructor's numbers are taken
         * as given — a box inside out would draw with every normal inverted and
         * vanish, which is loud enough not to need a guard.
         */
        fun standing(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            floor: Float,
            ceiling: Float,
        ) = SceneBox(
            min = Vec3(left, top, floor),
            max = Vec3(right, bottom, ceiling),
        )

        /**
         * The smallest box that holds a solid that is not one.
         *
         * `core/render` builds meshes and knows nothing about this type, on
         * purpose — `core/scene` depends on `core/render` and the arrow may only
         * point one way. So the lathe returns faces and this measures them, and
         * the seam between the two is one loop over the corners.
         *
         * An empty list gives a box at the origin with no extent, which is what
         * "nothing, in a room" should be: it overlaps nothing, it stands over
         * nothing, and it sorts wherever the origin does.
         */
        fun around(faces: List<Face>): SceneBox {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            faces.forEach { face ->
                face.corners.forEach { corner ->
                    if (corner.x < minX) minX = corner.x
                    if (corner.x > maxX) maxX = corner.x
                    if (corner.y < minY) minY = corner.y
                    if (corner.y > maxY) maxY = corner.y
                    if (corner.z < minZ) minZ = corner.z
                    if (corner.z > maxZ) maxZ = corner.z
                }
            }
            if (minX > maxX) return SceneBox(Vec3.Zero, Vec3.Zero)
            return SceneBox(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
        }
    }
}


/**
 * Everything [ScenePainter] needs to know about where a box is, in one pass over
 * its eight corners.
 *
 * Here rather than at each call site because there are three of them — the
 * renderer and two tests — and a paint order tested against a different
 * silhouette than the one drawn is a test that agrees with itself and with
 * nothing else. `ScenePainter` still has no idea a projection exists; this is
 * the seam between the two, and it is deliberately one function long.
 *
 * The silhouette comes off `flatten` rather than `project`, because `flatten` is
 * what the renderer actually draws with — a point's true screen position, run
 * through a canvas that has no z.
 */
fun StagePlane.reachOf(box: SceneBox): ScenePainter.Reach {
    var depth = Float.NEGATIVE_INFINITY
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    box.corners().forEach { corner ->
        val here = project(corner).depth
        if (here > depth) depth = here
        val flat = flatten(corner)
        if (flat.x < left) left = flat.x
        if (flat.x > right) right = flat.x
        if (flat.y < top) top = flat.y
        if (flat.y > bottom) bottom = flat.y
    }
    return ScenePainter.Reach(depth, left, top, right, bottom)
}
