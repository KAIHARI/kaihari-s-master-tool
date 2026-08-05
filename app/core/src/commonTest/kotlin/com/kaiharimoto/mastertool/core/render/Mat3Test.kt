package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Composing two orientations, and pulling the answer back apart.
 *
 * The stage needs this once, and the once is load-bearing: a card held up to be
 * read has to end up square to the reader, the reader is wherever the camera is,
 * and the card's angles are in a frame the camera is about to turn. So "face me"
 * is not an angle — it is whatever undoes the camera — and finding it means
 * multiplying two rotations and asking what three Euler angles reproduce the
 * product.
 *
 * Which has a singularity in it, exactly where a card spends two frames of every
 * flip.
 */
class Mat3Test {

    /**
     * A thousandth of a degree, and a thousandth of a unit vector.
     *
     * Tight, because everything here is exact arithmetic on unit rotations —
     * anything looser would hide a genuine sign error behind "close enough".
     */
    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-3f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    private fun assertSame(expected: Mat3, actual: Mat3, note: String = "") {
        listOf(
            expected.m00 to actual.m00, expected.m01 to actual.m01, expected.m02 to actual.m02,
            expected.m10 to actual.m10, expected.m11 to actual.m11, expected.m12 to actual.m12,
            expected.m20 to actual.m20, expected.m21 to actual.m21, expected.m22 to actual.m22,
        ).forEachIndexed { i, (e, a) -> assertClose(e, a, note = "$note entry $i:") }
    }

    /** Every pose worth checking, including the ones the app actually produces. */
    private val poses = listOf(
        Pose3(),
        Pose3(rotY = 180f),                        // face down
        Pose3(rotZ = -90f),                        // defence
        Pose3(rotY = 180f, rotZ = -90f),           // set in defence
        Pose3(rotX = 13f, rotY = -27f, rotZ = 6f), // a card banked in the air
        Pose3(rotX = -41f, rotY = 118f, rotZ = -73f),
        Pose3(rotX = 90f),
        Pose3(rotY = 90f),                         // edge on: the singularity
        Pose3(rotY = -90f),
    )

    // ---- the matrix agrees with the thing it is a matrix of -----------------------

    @Test
    fun theMatrixTurnsAPointExactlyAsTheAnglesDo() {
        // The one that keeps Mat3 honest against Rot3. If these two ever drift,
        // a card's shadow and the card itself are computed from different
        // rotations and nobody can see why.
        val points = listOf(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), Vec3(37f, -12f, 5f))

        poses.forEach { pose ->
            val matrix = Rot3.matrixOf(pose)
            points.forEach { p ->
                val byAngles = Rot3.rotate(pose, p)
                val byMatrix = matrix * p
                assertClose(byAngles.x, byMatrix.x, note = "x for $p under $pose:")
                assertClose(byAngles.y, byMatrix.y, note = "y for $p under $pose:")
                assertClose(byAngles.z, byMatrix.z, note = "z for $p under $pose:")
            }
        }
    }

    @Test
    fun turningByNothingIsTheIdentity() {
        assertSame(Mat3.Identity, Rot3.matrixOf(Pose3()))
    }

    @Test
    fun multiplyingByTheIdentityChangesNothing() {
        poses.forEach {
            val m = Rot3.matrixOf(it)
            assertSame(m, m * Mat3.Identity, "right:")
            assertSame(m, Mat3.Identity * m, "left:")
        }
    }

    // ---- taking the angles back out -------------------------------------------------

    @Test
    fun everyOrientationCanBeWrittenBackAsThreeAngles() {
        // Not the same claim as "the angles come back": at a quarter turn about
        // Y two of them stop being separable, so what has to survive is the
        // *rotation*, not the numbers.
        poses.forEach { pose ->
            val wanted = Rot3.matrixOf(pose)
            val (x, y, z) = Rot3.eulerOf(wanted)
            val rebuilt = Rot3.matrixOf(Pose3(rotX = x, rotY = y, rotZ = z))

            assertSame(wanted, rebuilt, "for $pose, read back as ($x, $y, $z):")
        }
    }

    @Test
    fun aCardExactlyEdgeOnDoesNotSnapFlat() {
        // Gimbal lock, and it is not hypothetical: a card passes through exactly
        // ninety degrees twice on every flip. `atan2(0, 0)` is zero, so an
        // untreated decomposition throws away the other rotation for one frame
        // and the card jumps — which reads as a dropped frame, not as a bug.
        val edgeOn = Pose3(rotY = 90f, rotZ = -90f)
        val (x, y, z) = Rot3.eulerOf(Rot3.matrixOf(edgeOn))

        assertSame(
            Rot3.matrixOf(edgeOn),
            Rot3.matrixOf(Pose3(rotX = x, rotY = y, rotZ = z)),
            "a set card turned edge-on came back as something else:",
        )
        assertClose(90f, y, note = "it is still edge-on:")
    }

    @Test
    fun theAnswerDoesNotJumpOnTheWayThroughEdgeOn() {
        // The property that actually matters on screen. Sweeping a card through
        // the singularity, the *normal* it ends up with has to move smoothly,
        // whatever the three numbers do.
        var previous: Vec3? = null
        var worst = 0f
        var at = 0f

        for (step in 0..180) {
            val turn = 80f + step * 0.1f      // 80 to 98 degrees, straight through
            val pose = Pose3(rotX = 20f, rotY = turn, rotZ = -35f)
            val (x, y, z) = Rot3.eulerOf(Rot3.matrixOf(pose))
            val normal = Rot3.normal(Pose3(rotX = x, rotY = y, rotZ = z))

            previous?.let {
                val jump = (normal - it).length
                if (jump > worst) {
                    worst = jump
                    at = turn
                }
            }
            previous = normal
        }

        assertTrue(worst < 0.01f, "the card jumped by $worst at $at degrees")
    }

    // ---- undoing the camera -----------------------------------------------------------

    @Test
    fun aCardToldToFaceTheViewerFacesTheViewer() {
        // Inside the mat's layer, "square to the reader" is whatever cancels the
        // camera. Compose it back with the camera and it has to come out flat.
        listOf(0f to 0f, 15f to 0f, 34f to 45f, 58f to -137f, 5f to 180f).forEach { (pitch, yaw) ->
            val (x, y, z) = Rot3.facingViewer(pitch, yaw)
            val camera = Mat3.rotationX(pitch) * Mat3.rotationZ(-yaw)
            val together = camera * Rot3.matrixOf(Pose3(rotX = x, rotY = y, rotZ = z))

            assertSame(Mat3.Identity, together, "at pitch $pitch yaw $yaw:")
        }
    }

    @Test
    fun aTableSquareOnAsksTheCardForNothing() {
        val (x, y, z) = Rot3.facingViewer(0f, 0f)

        assertClose(0f, x)
        assertClose(0f, y)
        assertClose(0f, z)
    }

    @Test
    fun composingTwoRotationsIsDoingOneThenTheOther() {
        val outer = Pose3(rotX = 22f, rotY = -8f, rotZ = 41f)
        val inner = Pose3(rotX = -13f, rotY = 96f, rotZ = -30f)
        val (x, y, z) = Rot3.compose(outer, inner)

        assertSame(
            Rot3.matrixOf(outer) * Rot3.matrixOf(inner),
            Rot3.matrixOf(Pose3(rotX = x, rotY = y, rotZ = z)),
        )
    }

    // ---- the eye ------------------------------------------------------------------------

    @Test
    fun theEyeIsWhereUndoingTheCameraPutsIt() {
        // The eye and "face the viewer" are two answers to the same question, so
        // they had better agree: the direction the card's face ends up pointing
        // when told to face the viewer *is* the direction the eye is in.
        listOf(0f to 0f, 15f to 0f, 34f to 45f, 58f to -137f).forEach { (pitch, yaw) ->
            val eye = StageRig.eye(pitch, yaw)
            val (x, y, z) = Rot3.facingViewer(pitch, yaw)
            val facing = Rot3.normal(Pose3(rotX = x, rotY = y, rotZ = z))

            assertClose(1f, eye dot facing, note = "at pitch $pitch yaw $yaw:")
        }
    }

    @Test
    fun aTableSquareOnIsLookedAtStraightOn() {
        val eye = StageRig.eye(0f, 0f)

        assertClose(0f, eye.x)
        assertClose(0f, eye.y)
        assertClose(1f, eye.z)
    }

    @Test
    fun turningTheTableMovesTheViewerRoundIt() {
        // At yaw zero the viewer is toward the mat's near edge; a quarter turn
        // later they are off to one side of it, and never underneath it.
        val square = StageRig.eye(30f, 0f)
        val quarter = StageRig.eye(30f, 90f)

        assertTrue(square.y > 0.4f, "the viewer should be toward the near edge, was ${square.y}")
        assertClose(0f, square.x, note = "and not off to the side:")
        assertTrue(abs(quarter.x) > 0.4f, "a quarter turn should move them round, was ${quarter.x}")
        assertClose(square.z, quarter.z, note = "without changing how high they are:")
        assertClose(1f, quarter.length, note = "the eye is a unit direction:")
    }
}
