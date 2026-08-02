package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class Rot3Test {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-3f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    private fun assertClose(expected: Vec3, actual: Vec3, note: String = "") {
        assertClose(expected.x, actual.x, note = "$note x:")
        assertClose(expected.y, actual.y, note = "$note y:")
        assertClose(expected.z, actual.z, note = "$note z:")
    }

    // ---- the conventions, which everything else is built on ---------------------

    @Test
    fun aPositiveTiltBringsTheBottomEdgeForward() {
        // The same sentence StagePlane opens with, held to here as well —
        // if these two ever disagree, a card's shadow leaves its card.
        val bottom = Rot3.rotateX(Vec3(0f, 100f, 0f), 30f)

        assertTrue(bottom.z > 0f, "the near edge should come toward the viewer, was ${bottom.z}")
        assertTrue(bottom.y in 1f..100f, "and stay below the centre, was ${bottom.y}")
    }

    @Test
    fun aPositiveTurnPushesTheRightEdgeAway() {
        val right = Rot3.rotateY(Vec3(100f, 0f, 0f), 30f)

        assertTrue(right.z < 0f, "the right edge should go away, was ${right.z}")
    }

    @Test
    fun halfATurnShowsTheBack() {
        val normal = Rot3.normal(Pose3(rotY = 180f))

        assertClose(Vec3(0f, 0f, -1f), normal)
        assertTrue(Rot3.facing(Pose3(rotY = 180f)) < 0f)
        assertTrue(Rot3.facing(Pose3()) > 0f)
    }

    @Test
    fun aCardLyingFlatFacesTheViewer() {
        assertClose(Vec3(0f, 0f, 1f), Rot3.normal(Pose3()))
        assertClose(1f, Rot3.facing(Pose3()))
    }

    @Test
    fun aQuarterTurnInThePlaneDoesNotChangeWhichWayTheCardFaces() {
        // Setting a monster in defence turns it about z, which is a rotation
        // *within* the card's own plane: the face is still the face.
        assertClose(Vec3(0f, 0f, 1f), Rot3.normal(Pose3(rotZ = -90f)))
        assertClose(Vec3(0f, 0f, 1f), Rot3.normal(Pose3(rotZ = 90f)))
    }

    // ---- the order, which is only observable on a set defender ------------------

    @Test
    fun theOrderIsTheOneComposeUses() {
        // A card set in defence: half a turn about Y and a quarter about Z.
        // Compose builds Rx·Ry·Rz, so Z happens first. Applying them the other
        // way round gives a different point for the same card, and this is the
        // one combination the app actually produces.
        val pose = Pose3(rotY = 180f, rotZ = -90f)
        val corner = Vec3(10f, 20f, 0f)

        val ours = Rot3.rotate(pose, corner)
        val zFirst = Rot3.rotateY(Rot3.rotateZ(corner, -90f), 180f)
        val yFirst = Rot3.rotateZ(Rot3.rotateY(corner, 180f), -90f)

        assertClose(zFirst, ours, "z first is what Compose does:")
        assertTrue(
            abs(ours.y - yFirst.y) > 1f,
            "the two orders should differ here, or this test proves nothing",
        )
    }

    // ---- placing ------------------------------------------------------------------

    @Test
    fun aCardsOwnCentreIsWhereTheCardIs() {
        val pose = Pose3(position = Vec3(300f, 200f, 40f), rotX = 20f, rotY = -35f, rotZ = 12f)

        assertClose(pose.position, Rot3.place(pose, Vec3.Zero))
    }

    @Test
    fun scaleGrowsTheCardAboutItsOwnCentre() {
        val pose = Pose3(position = Vec3(100f, 100f, 0f), scale = 2f)

        assertClose(Vec3(120f, 100f, 0f), Rot3.place(pose, Vec3(10f, 0f, 0f)))
    }

    @Test
    fun rightAndDownAreTheCardsOwnDirections() {
        val flat = Pose3()

        assertClose(Vec3(1f, 0f, 0f), Rot3.right(flat))
        assertClose(Vec3(0f, 1f, 0f), Rot3.down(flat))

        // Set in defence, the card's own "down" points across the table: the
        // quarter turn the app uses lays the card's top edge to the left.
        val turned = Rot3.down(Pose3(rotZ = -90f))
        assertClose(1f, turned.x, note = "a turned card's down points right:")
        assertClose(-1f, Rot3.right(Pose3(rotZ = -90f)).y, note = "and its right points up:")
    }

    @Test
    fun theThreeAxesStayPerpendicularHoweverTheCardIsHeld() {
        // Cheap, and it catches any typo in a rotation matrix that a
        // single-axis test would sail past.
        val pose = Pose3(rotX = 37f, rotY = -114f, rotZ = 63f)
        val right = Rot3.right(pose)
        val down = Rot3.down(pose)
        val normal = Rot3.normal(pose)

        assertClose(1f, right.length, note = "right is a unit vector:")
        assertClose(1f, down.length, note = "down is a unit vector:")
        assertClose(1f, normal.length, note = "the normal is a unit vector:")
        assertClose(0f, right dot down, note = "right vs down:")
        assertClose(0f, right dot normal, note = "right vs normal:")
        assertClose(0f, down dot normal, note = "down vs normal:")
    }

    @Test
    fun turningByNothingChangesNothing() {
        val v = Vec3(3f, -4f, 5f)

        assertClose(v, Rot3.rotate(Pose3(), v))
    }
}
