package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rotation `graphicsLayer` applies, written down so the rest of the app can
 * reason about it.
 *
 * Everything that makes the stage look three-dimensional — where a card's
 * shadow falls, which of its edges you can see, where the light pools on its
 * face — is a question about the card's *orientation in space*. Compose knows
 * that orientation, because it is about to rasterise the card with it, and
 * Compose will not tell anyone: a `graphicsLayer` is a write-only sink of three
 * Euler angles. So the same three angles are interpreted here, and the two
 * agree because they are the same arithmetic rather than because someone
 * eyeballed a shadow once.
 *
 * Two facts about that arithmetic, both load-bearing and neither obvious:
 *
 * **The order is X, then Y, then Z, applied to the point in reverse.** Compose
 * builds its layer matrix as `Rx · Ry · Rz` (Skia's `Sk3DView` pre-concatenates
 * on Android; the skiko layer post-multiplies the same three in the same
 * sequence), so a point is turned about Z first and about X last. With one
 * angle set at a time — which is nearly every card, nearly always — the order
 * cannot be observed at all. It becomes visible exactly when a card is *set in
 * defence*: face-down is a half turn about Y and sideways is a quarter turn
 * about Z, and getting the order backwards puts the shadow of that card on the
 * wrong side of it.
 *
 * **The rotations are right-handed about axes where +y points down.** That is
 * Compose's frame (see [Vec3]) and it means a positive `rotationX` brings the
 * *bottom* edge toward the viewer — the same sentence `StagePlane` opens with,
 * because it is the same rotation.
 */
object Rot3 {

    private const val DEGREES = PI.toFloat() / 180f

    /** A card's face, in its own coordinates, points at the viewer. */
    val FaceNormal = Vec3(0f, 0f, 1f)

    /** Turns [local] by [pose]'s three angles. No translation, no scale. */
    fun rotate(pose: Pose3, local: Vec3): Vec3 =
        rotateX(rotateY(rotateZ(local, pose.rotZ), pose.rotY), pose.rotX)

    /**
     * Where a point drawn at [local] on the card actually is.
     *
     * The card's own coordinates have their origin at its centre, +x toward its
     * right edge and +y toward its bottom one — the same directions the screen
     * uses, which is what makes a corner at `(-w/2, -h/2, 0)` the top-left one.
     */
    fun place(pose: Pose3, local: Vec3): Vec3 =
        pose.position + rotate(pose, local * pose.scale)

    /**
     * The way the card's face is pointing.
     *
     * Turned over, this points away from the viewer, which is the whole of the
     * test for "am I looking at the back" and is why nothing else in the app
     * needs to special-case a flip.
     */
    fun normal(pose: Pose3): Vec3 = rotate(pose, FaceNormal)

    /** The card's own rightward direction, in stage space. */
    fun right(pose: Pose3): Vec3 = rotate(pose, Vec3(1f, 0f, 0f))

    /** The card's own downward direction, in stage space. */
    fun down(pose: Pose3): Vec3 = rotate(pose, Vec3(0f, 1f, 0f))

    /**
     * Whether the viewer is looking at the face rather than the back.
     *
     * The dot product of the face normal with the eye direction, which the
     * renderer also uses as the strength of everything that only happens to a
     * surface turned toward the light.
     */
    fun facing(pose: Pose3, eye: Vec3 = Vec3.Toward): Float = normal(pose) dot eye

    // ---- one axis at a time ----------------------------------------------------

    fun rotateX(v: Vec3, degrees: Float): Vec3 {
        if (degrees == 0f) return v
        val r = degrees * DEGREES
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }

    fun rotateY(v: Vec3, degrees: Float): Vec3 {
        if (degrees == 0f) return v
        val r = degrees * DEGREES
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    fun rotateZ(v: Vec3, degrees: Float): Vec3 {
        if (degrees == 0f) return v
        val r = degrees * DEGREES
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
    }
}
