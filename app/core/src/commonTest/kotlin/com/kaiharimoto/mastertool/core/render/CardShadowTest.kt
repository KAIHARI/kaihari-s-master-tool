package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardShadowTest {

    private val width = 120f
    private val height = 175f
    private val key = StageRig.Key

    private fun at(z: Float, pose: Pose3 = Pose3()) = pose.copy(
        position = Vec3(600f, 400f, z),
    )

    private fun shadow(z: Float, pose: Pose3 = Pose3()) =
        Shadows.cast(at(z, pose), width, height, key, cardHeight = height)!!

    private fun centre(corners: List<Vec3>) = corners
        .fold(Vec3.Zero) { sum, c -> sum + c } / corners.size.toFloat()

    // ---- resting -------------------------------------------------------------------

    @Test
    fun aCardOnTheTableCastsItsShadowUnderItself() {
        val resting = shadow(0f)
        val middle = centre(resting.corners)

        assertTrue(abs(middle.x - 600f) < 0.01f, "x was ${middle.x}")
        assertTrue(abs(middle.y - 400f) < 0.01f, "y was ${middle.y}")
        resting.corners.forEach { assertEquals(0f, it.z) }
    }

    @Test
    fun aRestingCardIsDarkAndTightAndTouching() {
        val resting = shadow(0f)

        assertTrue(resting.alpha > 0.6f, "a contact shadow is dark: ${resting.alpha}")
        assertTrue(resting.spread < height * 0.05f, "and tight: ${resting.spread}")
        assertTrue(resting.contact > 0.95f, "and touching: ${resting.contact}")
        assertEquals(0f, resting.height)
    }

    // ---- lifted ----------------------------------------------------------------------

    @Test
    fun theShadowLeavesTheCardAsTheCardLeavesTheTable() {
        // The one perception the whole shadow exists to produce. At the angle
        // this table is seen from, the scale change from lifting is a few per
        // cent and unreadable; the separation is not.
        val resting = centre(shadow(0f).corners)
        val held = centre(shadow(height * 0.55f).corners)

        val travelled = (held - resting).length
        assertTrue(travelled > width * 0.2f, "the shadow barely moved: $travelled")
    }

    @Test
    fun itGoesTheWayTheLightPushesIt() {
        val moved = centre(shadow(100f).corners) - centre(shadow(0f).corners)

        assertTrue(moved.x > 0f, "the key is off to the left, so the shadow goes right")
        assertTrue(moved.y > 0f, "and toward the player")
    }

    @Test
    fun itSoftensAndFadesWithHeight() {
        val heights = listOf(0f, 40f, 90f, 180f).map { shadow(it) }

        assertEquals(
            heights.map { it.spread }.sorted(),
            heights.map { it.spread },
            "a shadow only ever gets softer as the card rises",
        )
        assertEquals(
            heights.map { it.alpha }.sortedDescending(),
            heights.map { it.alpha },
            "and only ever fainter",
        )
    }

    @Test
    fun theTightDarknessUnderneathLetsGoQuicklyAndTheCastShadowDoesNot() {
        // Two different things: ambient occlusion is a contact phenomenon and
        // should vanish the instant a card is picked up, while the cast shadow
        // has to survive the whole lift or the card looks like it left the room.
        val nudged = shadow(height * 0.12f)

        assertTrue(nudged.contact < 0.5f, "contact should be nearly gone: ${nudged.contact}")
        assertTrue(nudged.alpha > 0.4f, "the cast shadow should not be: ${nudged.alpha}")
    }

    // ---- tilted ---------------------------------------------------------------------

    @Test
    fun aTiltedCardsShadowIsNotAnOffsetCopyOfIt() {
        // The reason every corner is projected separately rather than the
        // whole rectangle being slid sideways. A card banked in the air throws
        // a shadow that is a different *shape* — narrower, and no longer
        // square — and that difference is most of what tells you it is banked.
        val flat = shadow(120f)
        val banked = shadow(120f, Pose3(rotY = 35f))

        fun span(shadow: CardShadow) = (shadow.corners[1] - shadow.corners[0]).length
        fun squareness(shadow: CardShadow): Float {
            val across = (shadow.corners[1] - shadow.corners[0]).normalised()
            val down = (shadow.corners[3] - shadow.corners[0]).normalised()
            return abs(across dot down)
        }

        assertTrue(abs(span(flat) - width) < 0.5f, "a flat card's shadow is the card")
        assertTrue(squareness(flat) < 0.01f, "with its corners still square")

        assertTrue(span(banked) < width * 0.85f, "a banked card's is narrower: ${span(banked)}")
        assertTrue(squareness(banked) > 0.2f, "and sheared: ${squareness(banked)}")
    }

    @Test
    fun aCardStandingOnEdgeStillCastsSomething() {
        val edgeOn = Shadows.cast(at(80f, Pose3(rotY = 90f)), width, height, key, height)

        assertTrue(edgeOn != null)
        edgeOn.corners.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite(), "corner off to infinity: $it")
        }
    }

    // ---- refusing to fall over --------------------------------------------------------

    @Test
    fun aLightThatNeverReachesTheTableCastsNothing() {
        val sideways = Light(direction = Vec3(1f, 0f, 0f))

        assertNull(Shadows.cast(at(100f), width, height, sideways, height))
    }

    @Test
    fun aCardBelowTheSurfaceDoesNotInvertItsShadow() {
        // A bouncy spring overshoots on landing, so a card really does dip
        // below zero for a frame or two.
        val dipped = shadow(-6f)

        assertTrue(dipped.height == 0f, "height cannot go negative: ${dipped.height}")
        assertTrue(dipped.alpha > 0.6f, "and it is still a contact shadow")
        assertTrue(dipped.spread > 0f)
    }

    @Test
    fun aCardWithNoSizeStillAnswers() {
        val nothing = Shadows.cast(Pose3(), 0f, 0f, key, cardHeight = 0f)

        assertTrue(nothing != null)
        assertTrue(nothing.spread.isFinite() && nothing.alpha.isFinite())
    }
}
