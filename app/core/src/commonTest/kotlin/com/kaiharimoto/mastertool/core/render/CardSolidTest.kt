package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardSolidTest {

    private val flat = Pose3(position = Vec3(500f, 400f, 0f))
    private val width = 120f
    private val height = 175f

    /**
     * The exchange rate between a height computed here and a pixel on screen.
     *
     * Read off [StagePlane.TILT] rather than written down, because the whole
     * point of the claims below is that they hold *at the angle the table is
     * actually seen from* — pinning a number here would let the tilt move and
     * leave the tests passing about a table nobody is looking at.
     */
    private val SIN_DEFAULT_TILT = sin(StagePlane.TILT * (PI.toFloat() / 180f))

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-3f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    // ---- the face -----------------------------------------------------------------

    @Test
    fun theFaceIsTheCardTheCompositionDraws() {
        // The one property everything else leans on: adding a solid underneath
        // an existing card must not move the card by a pixel.
        val corners = CardSolid.face(flat, width, height)

        assertEquals(4, corners.size)
        assertClose(440f, corners[0].x, note = "top left x:")
        assertClose(312.5f, corners[0].y, note = "top left y:")
        assertClose(560f, corners[2].x, note = "bottom right x:")
        assertClose(487.5f, corners[2].y, note = "bottom right y:")
        corners.forEach { assertClose(0f, it.z, note = "a resting face is on the felt:") }
    }

    @Test
    fun theCornersComeRoundInOrder() {
        // Clockwise on screen, where +y is down — the order a path wants.
        val corners = CardSolid.face(flat, width, height)

        assertTrue(corners[0].x < corners[1].x, "top left is left of top right")
        assertTrue(corners[1].y < corners[2].y, "top right is above bottom right")
        assertTrue(corners[2].x > corners[3].x, "bottom right is right of bottom left")
    }

    @Test
    fun aTiltedCardHasCornersAtDifferentDepths() {
        val tilted = flat.copy(rotX = 25f)
        val corners = CardSolid.face(tilted, width, height)

        assertTrue(corners[3].z > corners[0].z, "the near edge should be nearer")
    }

    // ---- thickness -------------------------------------------------------------------

    @Test
    fun oneCardIsAHairline() {
        // 0.3mm over 59mm: at any card size anyone plays at, under a pixel.
        assertClose(0.6096f, CardSolid.thickness(120f))
    }

    @Test
    fun anEmptyPileHasNoHeight() {
        assertEquals(0f, CardSolid.pileDepth(0, 120f))
        assertEquals(0f, CardSolid.pileDepth(40, 0f))
    }

    @Test
    fun aDeckStandsProudOfTheTableAndAPairBarelyDoes() {
        val deck = CardSolid.pileDepth(40, 120f)
        val pair = CardSolid.pileDepth(2, 120f)

        // Divided by three again by the tilt, so this is a dozen pixels of white
        // edge on screen — a deck, rather than a card with a number.
        assertTrue(deck > 20f, "a forty-card deck should be visible: $deck")
        assertTrue(pair < deck / 5f, "two cards are two cards: $pair")
    }

    @Test
    fun aDeckIsTallEnoughToSeeAtTheAngleTheTableIsSeenFrom() {
        // The claim the exaggeration was raised to make, written as the thing
        // that was actually wrong rather than as the constant that fixed it.
        // Height on this stage reaches the screen multiplied by `sin(tilt)`, so
        // a pile is only as legible as that product — which is why an
        // exaggeration that looked generous in a unit test could still leave a
        // forty-card deck reading as a printed rectangle on the felt.
        val onScreen = CardSolid.pileDepth(40, 120f) * SIN_DEFAULT_TILT

        assertTrue(onScreen > 12f, "a deck should stand a dozen pixels up: $onScreen")
    }

    @Test
    fun twoCardsAreVisiblyMoreThanOne() {
        // The complaint that started this, in one line: a stack has to *look*
        // like a stack. One card's thickness is a third of a pixel however it is
        // drawn — this is not a claim about a single card — but the difference
        // between one and two has to survive the projection, or the second card
        // is a fact only the model knows.
        val step = (CardSolid.pileDepth(2, 120f) - CardSolid.pileDepth(1, 120f)) *
            SIN_DEFAULT_TILT

        assertTrue(step > 1f, "the second card of a stack adds $step pixels")
    }

    // ---- the seams -----------------------------------------------------------------

    @Test
    fun aSingleCardHasNoSeamInIt() {
        assertEquals(0, CardSolid.layerLines(1, 40f))
        assertEquals(0, CardSolid.layerLines(0, 40f))
    }

    @Test
    fun aPileIsRuledIntoAsManyCardsAsItHas() {
        // Three cards, two seams — the lines are between cards, not on them.
        assertEquals(2, CardSolid.layerLines(3, 100f))
    }

    @Test
    fun aPileNeverRulesMoreLinesThanThereIsRoomFor() {
        // Forty cards over eight pixels of edge cannot be forty lines, and
        // drawing them anyway is not honesty — it is a grey band. The same
        // bargain the height curve makes, made in the same place.
        val lines = CardSolid.layerLines(40, 8f)

        assertTrue(lines in 1..5, "forty cards over eight pixels ruled $lines lines")
        assertTrue(
            8f / (lines + 1) >= CardSolid.LAYER_MIN_SPACING * 0.8f,
            "the lines came out closer than they are allowed to be",
        )
    }

    @Test
    fun anEdgeWithNoLengthIsRuledIntoNothing() {
        assertEquals(0, CardSolid.layerLines(40, 0f))
        assertEquals(0, CardSolid.layerLines(40, -1f))
    }

    // ---- the slouch ----------------------------------------------------------------

    @Test
    fun aPileLeansTowardThePlayer() {
        val lean = CardSolid.pileLean(CardSolid.pileDepth(6, width), width)

        assertTrue(lean.y > 0f, "the slouch should come toward the near edge")
        assertTrue(lean.y > lean.x, "and mostly that way rather than sideways")
        assertClose(0f, lean.z, note = "a pile leans on the table, not off it:")
    }

    @Test
    fun aTallPileIsNoUntidierThanAShortOne() {
        // The saturation, stated as the thing it is for: the deck is the one
        // object on this table anybody actually squares up, and a lean that grew
        // with the height would make it the messiest thing on the felt.
        val small = CardSolid.pileLean(CardSolid.pileDepth(6, width), width)
        val deck = CardSolid.pileLean(CardSolid.pileDepth(40, width), width)

        assertTrue(deck.y >= small.y, "a taller pile never leans less")
        assertTrue(deck.y < width * 0.06f, "and never becomes a mess: ${deck.y}")
    }

    @Test
    fun aLeaningPileIsTheSameCardOnTop() {
        // The property everything else rests on. The lean moves the *back* of
        // the body, so the printed face — which is where the card's composable
        // is drawn and where a finger hit-tests — does not move at all.
        val lean = CardSolid.pileLean(40f, width)
        val upright = CardSolid.slab(flat, width, height, 40f)
        val slouched = CardSolid.slab(flat, width, height, 40f, lean)

        upright.last().corners.forEachIndexed { index, corner ->
            val moved = slouched.last().corners[index]
            assertClose(corner.x, moved.x, note = "the top face stays put:")
            assertClose(corner.y, moved.y, note = "the top face stays put:")
        }
    }

    @Test
    fun aPileNeverFailsToGrowWhenACardIsAddedToIt() {
        val heights = (1..60).map { CardSolid.pileDepth(it, 120f) }

        assertEquals(heights.sorted(), heights)
        assertEquals(heights.distinct().size, heights.size, "every count is its own height")
    }

    @Test
    fun aPileNeverBecomesATower() {
        // The other end of the same curve. Three times exaggeration is right
        // for the piles you can barely see and absurd for a sixty-card deck,
        // so the curve bends over instead of being clamped — clamping makes
        // every pile past twenty identical.
        val ceiling = 120f * CardSolid.PILE_CEILING

        listOf(40, 60, 200, 5_000).forEach {
            assertTrue(
                CardSolid.pileDepth(it, 120f) <= ceiling,
                "$it cards stood ${CardSolid.pileDepth(it, 120f)} against a ceiling of $ceiling",
            )
        }
        assertTrue(
            CardSolid.pileDepth(60, 120f) < ceiling,
            "and the tallest pile anyone plays with is still short of it",
        )
    }

    @Test
    fun aSmallPileIsNearlyTheFlatExaggeration() {
        // The saturation is not supposed to be doing anything down here.
        val three = CardSolid.pileDepth(3, 120f)
        val flat = CardSolid.thickness(120f) * 3 * CardSolid.PILE_EXAGGERATION

        assertTrue(three > flat * 0.9f, "$three against $flat")
    }

    // ---- the slab -----------------------------------------------------------------------

    @Test
    fun aSlabIsSixFacesAndTheLastOneIsWhatYouSee() {
        val faces = CardSolid.slab(flat, width, height, depth = 20f)

        assertEquals(6, faces.size)
        faces.forEach { assertEquals(4, it.corners.size, "every face is a quad") }
        // Back to front, so a renderer that draws them in order is already right.
        assertClose(1f, faces.last().normal.z, note = "the printed face is last:")
        assertClose(-1f, faces.first().normal.z, note = "the back is first:")
    }

    @Test
    fun theBodyHangsBehindThePrintedFace() {
        val faces = CardSolid.slab(flat, width, height, depth = 20f)
        val back = faces.first()

        back.corners.forEach { assertClose(-20f, it.z, note = "the back sits below the face:") }
    }

    @Test
    fun theEdgesJoinTheFaceToTheBack() {
        val faces = CardSolid.slab(flat, width, height, depth = 20f)

        // Each side quad has two corners on the face and two on the back.
        faces.drop(1).dropLast(1).forEach { side ->
            assertEquals(2, side.corners.count { abs(it.z) < 1e-3f }, "two on the face")
            assertEquals(2, side.corners.count { abs(it.z + 20f) < 1e-3f }, "two on the back")
        }
    }

    @Test
    fun aCardFlatOnTheTableShowsItsFaceAndNothingElse() {
        // Straight on, the four edges are exactly edge-on and the back is
        // hidden: one visible face, which is what a card on a table looks like.
        val visible = CardSolid.visible(CardSolid.slab(flat, width, height, 20f))

        assertEquals(1, visible.size)
        assertClose(1f, visible.single().normal.z)
    }

    @Test
    fun aPileSeenFromAboveTheNearEdgeShowsThatEdge() {
        // Which is the whole point of the solid. The table is tilted, so the
        // eye is up and toward the player, and the near edge of a pile comes
        // into view — that white band is what says "these are objects".
        val eye = StageRig.eye(15f)
        val visible = CardSolid.visible(CardSolid.slab(flat, width, height, 30f), eye)

        assertEquals(2, visible.size, "the face and the near edge")
        assertTrue(visible.any { it.normal.y > 0.5f }, "the near edge is visible")
        assertTrue(visible.none { it.normal.y < -0.5f }, "the far edge is not")
    }

    @Test
    fun aCardTurnedOverShowsItsBackAndNotItsFace() {
        val visible = CardSolid.visible(CardSolid.slab(flat.copy(rotY = 180f), width, height, 2f))

        assertEquals(1, visible.size)
        assertClose(1f, visible.single().normal.z, note = "the back is now pointing at us:")
    }

    @Test
    fun aCardTiltedInTheAirShowsOneOfItsLongEdges() {
        val held = flat.copy(rotY = -22f)
        val visible = CardSolid.visible(CardSolid.slab(held, width, height, 4f))

        assertTrue(visible.any { abs(it.normal.x) > 0.3f }, "a side edge should appear")
    }

    @Test
    fun aFacesCentreIsWhereItIs() {
        val faces = CardSolid.slab(flat, width, height, depth = 20f)

        assertClose(500f, faces.last().centre.x)
        assertClose(400f, faces.last().centre.y)
    }
}
