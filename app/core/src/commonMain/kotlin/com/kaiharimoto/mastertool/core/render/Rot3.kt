package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rotation, written out.
 *
 * Nine floats rather than a `FloatArray`, so it is a value that compares and
 * prints like every other small type in core. The only thing that ever builds
 * one is [Rot3], and the only thing that ever reads one is [Rot3.eulerOf] —
 * this exists because composing two orientations and pulling the result back
 * apart cannot be done in three angles without going through a matrix, and
 * pretending otherwise is how sign errors get in.
 */
data class Mat3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float,
) {
    operator fun times(o: Mat3) = Mat3(
        m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
        m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
        m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
        m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
        m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
        m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22,
    )

    /** The direction this rotation sends [v]. */
    operator fun times(v: Vec3) = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z,
    )

    companion object {
        private const val DEGREES = PI.toFloat() / 180f

        val Identity = Mat3(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

        fun rotationX(degrees: Float): Mat3 {
            val r = degrees * DEGREES
            val c = cos(r)
            val s = sin(r)
            return Mat3(1f, 0f, 0f, 0f, c, -s, 0f, s, c)
        }

        fun rotationY(degrees: Float): Mat3 {
            val r = degrees * DEGREES
            val c = cos(r)
            val s = sin(r)
            return Mat3(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
        }

        fun rotationZ(degrees: Float): Mat3 {
            val r = degrees * DEGREES
            val c = cos(r)
            val s = sin(r)
            return Mat3(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)
        }
    }
}

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

    // ---- composing two orientations ---------------------------------------------

    /**
     * Two rotations, one after the other, back as the three angles Compose takes.
     *
     * The stage needs this in one place and it is a place that matters: a card
     * held up to be read has to end up square to the *reader*, and the reader is
     * now somewhere the camera decides. The card's angles are in the mat's frame
     * — the mat is a `graphicsLayer` and everything inside it is turned by the
     * camera on the way out — so "face me" is not an angle, it is *whatever
     * undoes the camera*, and that is a rotation composed with another rotation.
     *
     * Composing them is trivial. Getting the answer back out as three Euler
     * angles is the part with a trap in it, and the trap is [eulerOf]'s.
     */
    fun compose(outer: Pose3, inner: Pose3): Triple<Float, Float, Float> =
        eulerOf(matrixOf(outer) * matrixOf(inner))

    /** The rotation that, applied inside a stage at this pitch and yaw, faces the viewer. */
    fun facingViewer(pitchDegrees: Float, yawDegrees: Float): Triple<Float, Float, Float> {
        // The mat's own layer is rotationZ = -yaw then rotationX = pitch, which
        // is Rx(pitch)·Rz(-yaw). Undoing it is that inverted, and the inverse of
        // a product is the product of the inverses, backwards.
        val undo = Mat3.rotationZ(yawDegrees) * Mat3.rotationX(-pitchDegrees)
        return eulerOf(undo)
    }

    fun matrixOf(pose: Pose3): Mat3 =
        Mat3.rotationX(pose.rotX) * Mat3.rotationY(pose.rotY) * Mat3.rotationZ(pose.rotZ)

    /**
     * The three angles that reproduce this rotation, in Compose's order.
     *
     * Derived rather than looked up, because the convention has to be *this*
     * app's. Writing out `Rx(a)·Ry(b)·Rz(c)` gives
     * `m02 = sin b`, `m12 = -sin a·cos b`, `m22 = cos a·cos b`,
     * `m00 = cos b·cos c` and `m01 = -cos b·sin c`, so each angle falls out of
     * one `asin` and two `atan2`.
     *
     * **The trap is `cos b = 0`.** At a quarter turn about Y — a card exactly
     * edge-on — four of those five entries are zero, both `atan2`s are asking
     * `atan2(0, 0)`, and the two remaining angles stop being separable: only
     * their sum or difference is determined. That is gimbal lock, and it is not
     * a hypothetical here. A card *is* exactly edge-on, twice, every single time
     * one is turned over. Untreated, `atan2(0, 0)` returns zero on both, the
     * card snaps flat for one frame in the middle of the flip, and it reads as
     * a dropped frame rather than as a maths error.
     *
     * So the degenerate branch pins `c` at zero and takes `a` from the entries
     * that survive. Any `(a, c)` pair with the right sum is correct there — the
     * orientation is genuinely the same — and pinning one of them is what makes
     * the answer continuous through the singularity instead of arbitrary.
     */
    fun eulerOf(m: Mat3): Triple<Float, Float, Float> {
        val b = asin(m.m02.coerceIn(-1f, 1f))
        val cosB = cos(b)

        if (abs(cosB) < GIMBAL) {
            val sign = if (m.m02 >= 0f) 1f else -1f
            val a = atan2(sign * m.m10, m.m11)
            return Triple(a / DEGREES, b / DEGREES, 0f)
        }

        return Triple(
            atan2(-m.m12, m.m22) / DEGREES,
            b / DEGREES,
            atan2(-m.m01, m.m00) / DEGREES,
        )
    }

    /** Close enough to edge-on that the other two angles have stopped being separable. */
    private const val GIMBAL = 1e-4f

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
