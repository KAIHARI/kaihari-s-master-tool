package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
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

    // ---- which way a body hangs ----------------------------------------------------
    //
    // Every test in this file used an unrotated pose, so every one of them
    // agreed with a body extruded along the card's own local axis and with one
    // extruded toward the table — the two are identical when the card is lying
    // flat and face up. A face-down card is turned half a circle about Y, which
    // maps local -z to world +z, so the deck and the extra deck built their
    // bodies upward into the air and shipped that way twice.

    @Test
    fun aFaceDownPileHangsDownwardLikeEveryOtherPile() {
        val deck = CardSolid.pileDepth(40, width)
        val faceUp = CardSolid.face(Pose3(position = Vec3(0f, 0f, deck)), width, height, deck)
        val faceDown =
            CardSolid.face(Pose3(position = Vec3(0f, 0f, deck), rotY = 180f), width, height, deck)

        faceUp.forEach { assertClose(0f, it.z, note = "a face-up pile reaches the felt:") }
        faceDown.forEach { assertClose(0f, it.z, note = "and so does a face-down one:") }
    }

    @Test
    fun turningAPileOverDoesNotMoveTheCardOnTopOfIt() {
        // The property the whole stage is pinned to: the printed face is where
        // the composable draws, so nothing about the body may shift it.
        val deck = CardSolid.pileDepth(40, width)
        val upright = CardSolid.face(Pose3(position = Vec3(500f, 400f, deck)), width, height)
        val turned = CardSolid
            .face(Pose3(position = Vec3(500f, 400f, deck), rotY = 180f), width, height)

        upright.forEach { assertClose(deck, it.z, note = "face-up top card:") }
        turned.forEach { assertClose(deck, it.z, note = "face-down top card:") }
    }

    @Test
    fun aFaceDownDeckShadowsFromTheFeltAndNotFromTwiceItsOwnHeight() {
        // The regression this pair exists for. `Shadows.cast` asks for the base
        // by depth, so an upward body put the deck's shadow at two pile heights
        // — further from the truth than the bug it was written to fix.
        val deck = CardSolid.pileDepth(40, width)
        val pose = Pose3(position = Vec3(500f, 400f, deck), rotY = 180f)
        val shadow = Shadows.cast(pose, width, height, StageRig.Key, height, bodyDepth = deck)!!

        val centre = shadow.corners.fold(Vec3.Zero) { sum, c -> sum + c } / 4f
        assertTrue(
            abs(centre.x - 500f) < 1f && abs(centre.y - 400f) < 1f,
            "a face-down deck's shadow belongs under the deck, was $centre",
        )
        assertTrue(shadow.contact > 0.98f, "and it is touching the felt: ${shadow.contact}")
    }

    @Test
    fun aSetCardIsAsThickAsAnUnsetOne() {
        val one = CardSolid.pileDepth(1, width)
        val up = CardSolid.slab(Pose3(position = Vec3(0f, 0f, one)), width, height, one)
        val down = CardSolid
            .slab(Pose3(position = Vec3(0f, 0f, one), rotY = 180f), width, height, one)

        fun span(faces: List<Face>) = faces.flatMap { it.corners }.let { it.maxOf { c -> c.z } - it.minOf { c -> c.z } }
        assertClose(span(up), span(down), note = "turning a card over is not a change of thickness:")
    }

    // ---- faces too edge-on to be worth drawing --------------------------------------

    @Test
    fun aQuadSeenEdgeOnCoversNoArea() {
        // 135 pixels long and under one tall is what a leaned hand card's far
        // edge came out as — drawn in full-brightness card stock, which is a
        // white line across whatever is behind it rather than an edge.
        val hairline = listOf(
            Vec2(0f, 0f), Vec2(135f, 0f), Vec2(135f, 0.88f), Vec2(0f, 0.88f),
        )
        assertTrue(
            CardSolid.flatArea(hairline) > CardSolid.MIN_DRAWN_AREA,
            "the sanity check: 119 square pixels is genuinely drawable",
        )

        val degenerate = listOf(
            Vec2(0f, 0f), Vec2(135f, 0f), Vec2(135f, 0.01f), Vec2(0f, 0.01f),
        )
        assertTrue(
            CardSolid.flatArea(degenerate) < CardSolid.MIN_DRAWN_AREA,
            "and a quad with no thickness at all is not: ${CardSolid.flatArea(degenerate)}",
        )
    }

    @Test
    fun theAreaOfASquareIsItsArea() {
        val square = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f), Vec2(0f, 10f))
        assertClose(100f, CardSolid.flatArea(square))
        assertClose(100f, CardSolid.flatArea(square.reversed()), note = "winding does not matter:")
        assertEquals(0f, CardSolid.flatArea(listOf(Vec2(0f, 0f), Vec2(1f, 1f))))
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

    /**
     * An eye standing off in [direction], at about the distance the stage's own
     * lens sits at.
     *
     * A *place*, because that is what [CardSolid.visible] asks for now and what
     * the projection always meant. Straight out from the card under test unless
     * a test says otherwise, so the claims below are about the card's own
     * geometry rather than about where on the table it happens to be — which is
     * a separate claim, and has its own test.
     */
    private fun eyeAt(
        direction: Vec3 = Vec3.Toward,
        from: Vec3 = flat.position,
        distance: Float = 1450f,
    ): Vec3 = from + direction * distance

    @Test
    fun aCardFlatOnTheTableShowsItsFaceAndNothingElse() {
        // Straight on, the four edges are exactly edge-on and the back is
        // hidden: one visible face, which is what a card on a table looks like.
        val visible = CardSolid.visible(CardSolid.slab(flat, width, height, 20f), eyeAt())

        assertEquals(1, visible.size)
        assertClose(1f, visible.single().normal.z)
    }

    @Test
    fun aCardOffToOneSideShowsTheWallTurnedTowardTheCamera() {
        // The bug this replaced, stated as the property that was missing. Asked
        // as a *direction*, a flat card's left and right walls score exactly
        // zero and are culled — so at every unturned camera, every object on the
        // board lost both of them, and the deck, which is the tallest thing on
        // the table and sits in the right-hand column, was a hole you could see
        // the felt through.
        //
        // The eye is where it is for the whole board, and the card is where the
        // deck is: off to the right of it. Its left wall is therefore turned
        // toward the camera and has to be drawn.
        val eye = eyeAt(from = Vec3(500f, 400f, 0f))
        val deck = flat.copy(position = Vec3(1050f, 400f, 0f))
        val visible = CardSolid.visible(CardSolid.slab(deck, width, height, 55f), eye)

        assertTrue(
            visible.any { it.normal.x < -0.5f },
            "the wall on the camera's side of an off-centre pile is drawn",
        )
        assertTrue(
            visible.none { it.normal.x > 0.5f },
            "and the wall on the far side of it is not",
        )
    }

    @Test
    fun aPileSeenFromAboveTheNearEdgeShowsThatEdge() {
        // Which is the whole point of the solid. The table is tilted, so the
        // eye is up and toward the player, and the near edge of a pile comes
        // into view — that white band is what says "these are objects".
        val visible = CardSolid.visible(
            CardSolid.slab(flat, width, height, 30f),
            eyeAt(StageRig.eye(15f)),
        )

        assertEquals(2, visible.size, "the face and the near edge")
        assertTrue(visible.any { it.normal.y > 0.5f }, "the near edge is visible")
        assertTrue(visible.none { it.normal.y < -0.5f }, "the far edge is not")
    }

    @Test
    fun aLeaningPileIsCulledByTheWayItActuallyLeans() {
        // Normals used to be the un-leaned solid's, rotated by the pose, and a
        // forty-card deck leans about fourteen degrees. Fourteen degrees is the
        // difference between drawing a wall and not drawing it, on a solid whose
        // two printed faces this path never draws at all.
        val depth = 55f
        val lean = CardSolid.pileLean(depth, width)
        val leaned = CardSolid.slab(flat, width, height, depth, lean)
        val upright = CardSolid.slab(flat, width, height, depth)

        val near = leaned[3]
        assertTrue(
            near.normal.z > 1e-3f,
            "a pile slouching toward the player tips its near wall up at the camera: ${near.normal}",
        )
        assertClose(
            0f,
            upright[3].normal.z,
            note = "and an upright one's near wall does not:",
        )
    }

    @Test
    fun aCardTurnedOverShowsItsBackAndNotItsFace() {
        val visible = CardSolid.visible(
            CardSolid.slab(flat.copy(rotY = 180f), width, height, 2f),
            eyeAt(),
        )

        assertEquals(1, visible.size)
        assertClose(1f, visible.single().normal.z, note = "the back is now pointing at us:")
    }

    @Test
    fun aCardTiltedInTheAirShowsOneOfItsLongEdges() {
        val held = flat.copy(rotY = -22f)
        val visible = CardSolid.visible(CardSolid.slab(held, width, height, 4f), eyeAt())

        assertTrue(visible.any { abs(it.normal.x) > 0.3f }, "a side edge should appear")
    }

    @Test
    fun everyWallOfASlabPointsOutOfIt() {
        // The winding, which nothing had ever checked and two faces got wrong.
        // A normal read off the corners is only the *outward* one if every face
        // goes round the same way; the far and right edges did not, and it went
        // unnoticed because a convex quad fills identically either way and the
        // declared normals were quietly correcting for it.
        val faces = CardSolid.slab(flat, width, height, depth = 20f)
        val middle = Vec3(flat.position.x, flat.position.y, -10f)

        val outward = listOf(
            Vec3(0f, 0f, -1f),   // back
            Vec3(0f, -1f, 0f),   // far
            Vec3(1f, 0f, 0f),    // right
            Vec3(0f, 1f, 0f),    // near
            Vec3(-1f, 0f, 0f),   // left
            Vec3(0f, 0f, 1f),    // front
        )

        faces.forEachIndexed { index, face ->
            val expected = outward[index]
            assertClose(expected.x, face.normal.x, note = "face $index x:")
            assertClose(expected.y, face.normal.y, note = "face $index y:")
            assertClose(expected.z, face.normal.z, note = "face $index z:")
            assertTrue(
                face.normal dot (face.centre - middle) > 0f,
                "face $index points away from the middle of the solid",
            )
        }
    }

    @Test
    fun aFacesCentreIsWhereItIs() {
        val faces = CardSolid.slab(flat, width, height, depth = 20f)

        assertClose(500f, faces.last().centre.x)
        assertClose(400f, faces.last().centre.y)
    }
}
