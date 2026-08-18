package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomographyTest {

    private val stage = StagePlane.forStage(1600f, 1000f)

    private val cardWidth = 120f
    private val cardHeight = 175f

    /** A card in the hand: lifted off the felt and leaned back toward the reader. */
    private val handPose = Pose3(
        position = Vec3(760f, 820f, 96f),
        rotX = -24f,
    )

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-2f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    /**
     * A card's four corners, flattened — *the same expression the renderer uses*.
     *
     * Written once here for the same reason it is written once in the
     * composable: the entire claim of this file is that the picture and the
     * geometry come out of one call, and a test that reached for the corners a
     * second way would be testing a second thing.
     */
    private fun quadOf(pose: Pose3, plane: StagePlane = stage): List<Vec2> =
        CardSolid.face(pose, cardWidth, cardHeight).map { corner ->
            val flat = plane.flatten(corner)
            Vec2(flat.x, flat.y)
        }

    private fun mapOf(pose: Pose3, plane: StagePlane = stage): Homography =
        assertNotNull(Homography.rectToQuad(cardWidth, cardHeight, quadOf(pose, plane)))

    // ---- the map is what it was asked for -------------------------------------------

    @Test
    fun theFourCornersOfACardLandOnTheFourCornersItWasGiven() {
        // The definitional property, and the one every other claim rests on.
        val quad = quadOf(handPose)
        val map = mapOf(handPose)

        val local = listOf(
            Vec2(0f, 0f),
            Vec2(cardWidth, 0f),
            Vec2(cardWidth, cardHeight),
            Vec2(0f, cardHeight),
        )

        local.forEachIndexed { i, at ->
            val landed = map.map(at)
            assertClose(quad[i].x, landed.x, note = "corner $i x:")
            assertClose(quad[i].y, landed.y, note = "corner $i y:")
        }
    }

    @Test
    fun theOrderIsTheOneCardSolidHandsItsCornersBackIn() {
        // Not a restatement of the above. `CardSolid.face` returns clockwise
        // from the card's *top-left*, and a Compose layout's local origin is
        // its top-left too. Line those two up wrongly and the test above still
        // passes — against a quad that draws every card a quarter turn out.
        val square = Pose3(position = Vec3(700f, 500f, 0f))
        val map = mapOf(square)

        val topLeftLocal = map.map(0f, 0f)
        val topLeftTrue = stage.flatten(
            Rot3.place(square, Vec3(-cardWidth / 2f, -cardHeight / 2f, 0f)),
        )

        assertClose(topLeftTrue.x, topLeftLocal.x, note = "local (0,0) is the card's top-left, x:")
        assertClose(topLeftTrue.y, topLeftLocal.y, note = "local (0,0) is the card's top-left, y:")
        // And it is to the left of, and above, the card's own centre.
        val centre = map.map(cardWidth / 2f, cardHeight / 2f)
        assertTrue(topLeftLocal.x < centre.x, "the top-left corner is left of the centre")
        assertTrue(topLeftLocal.y < centre.y, "the top-left corner is above the centre")
    }

    // ---- what a homography does that four channels cannot ---------------------------

    @Test
    fun theMiddleOfALeanedCardIsNotTheMiddleOfItsCorners() {
        // The bug, named. A perspective divide taken about the card's own
        // centre is exactly the map for which these two *would* agree, which is
        // why no value of `cameraDistance` ever repaired the disagreement
        // between a leaned card's picture and its own edges.
        val quad = quadOf(handPose)
        val map = mapOf(handPose)

        val centre = map.map(cardWidth / 2f, cardHeight / 2f)
        val averaged = Vec2(
            (quad[0].x + quad[1].x + quad[2].x + quad[3].x) / 4f,
            (quad[0].y + quad[1].y + quad[2].y + quad[3].y) / 4f,
        )

        val apart = (centre - averaged).length
        assertTrue(
            apart > 0.5f,
            "a leaned card's centre is not the average of its corners; they were $apart px apart",
        )
    }

    @Test
    fun aCardHalfwayUpItsOwnFaceIsNotHalfwayUpTheScreen() {
        // Foreshortening, as monotonicity rather than as a number. The card is
        // leaned back, so equal steps up its printed face cover less and less
        // screen the further back they go.
        val map = mapOf(handPose)

        val steps = (0..10).map { map.map(cardWidth / 2f, cardHeight * it / 10f) }
        val gaps = steps.zipWithNext { a, b -> (b - a).length }

        gaps.zipWithNext { near, far ->
            assertTrue(far < near, "steps up a leaned card shorten toward its far end: $gaps")
        }
    }

    @Test
    fun theFourChannelLayerCannotReachTheseCornersAndThisOneCan() {
        // The measurement that made this file necessary, kept reproducible.
        //
        // `oldSprite` is what a `graphicsLayer` produced: the card's own three
        // rotations, then a perspective divide about its centre, then the
        // flattened position and scale. It is a projective 2-D map whose z is
        // spent before the mat's projection ever sees it.
        val flattened = stage.flatten(handPose.position)
        val cameraDistance = cardWidth * 6f

        fun oldSprite(local: Vec3): Vec2 {
            val scaled = local * (flattened.scale * handPose.scale)
            val turned = Rot3.rotate(handPose, scaled)
            val divide = cameraDistance / (cameraDistance - turned.z)
            return Vec2(flattened.x + turned.x * divide, flattened.y + turned.y * divide)
        }

        val locals = listOf(
            Vec3(-cardWidth / 2f, -cardHeight / 2f, 0f),
            Vec3(cardWidth / 2f, -cardHeight / 2f, 0f),
            Vec3(cardWidth / 2f, cardHeight / 2f, 0f),
            Vec3(-cardWidth / 2f, cardHeight / 2f, 0f),
        )
        val truth = quadOf(handPose)

        val errors = locals.mapIndexed { i, local -> (oldSprite(local) - truth[i]).length }

        // Deliberately not pinned to the numbers this happens to produce: those
        // are a card size and a lean, not a fact about arithmetic. What is a
        // fact is that the old map missed by pixels and this one does not.
        assertTrue(
            errors.count { it > 1f } >= 2,
            "the four-channel layer misses the card's true corners: $errors",
        )
        assertTrue(errors.max() > 3f, "and it misses by more than a hairline: $errors")
    }

    // ---- a card lying flat must not move --------------------------------------------

    @Test
    fun aCardLyingFlatOnTheFeltIsStillJustATranslateAndAShear() {
        // A quad that closes exactly gets the affine branch, bit for bit, so a
        // resting board — which is most of the board — acquires no divide it
        // did not have before.
        val parallelogram = listOf(
            Vec2(100f, 100f),
            Vec2(300f, 140f),
            Vec2(340f, 400f),
            Vec2(140f, 360f),
        )
        val map = assertNotNull(Homography.rectToQuad(cardWidth, cardHeight, parallelogram))

        assertEquals(0f, map.m20, "a parallelogram needs no divide in x")
        assertEquals(0f, map.m21, "a parallelogram needs no divide in y")
        assertEquals(1f, map.m22)
    }

    @Test
    fun aCardOnTopOfADeckIsDrawnBiggerThanOneLyingOnTheFelt() {
        // The correction `Flattened.scale` used to carry as a separate term,
        // now carried by the corners themselves. Same card, two heights.
        val onFelt = Pose3(position = Vec3(700f, 500f, 0f))
        val onDeck = onFelt.copy(
            position = onFelt.position + Vec3(0f, 0f, CardSolid.pileDepth(40, cardWidth)),
        )

        assertTrue(
            CardSolid.flatArea(quadOf(onDeck)) > CardSolid.flatArea(quadOf(onFelt)),
            "a card sitting on a forty-card deck is nearer the lens than the felt under it",
        )
    }

    // ---- refusing to fall over ------------------------------------------------------

    @Test
    fun aCardSeenExactlyEdgeOnHasNoMapAtAll() {
        // Four collinear corners. Null, not nine infinities — this is a state
        // every card passes through twice on every flip.
        val collinear = listOf(
            Vec2(100f, 100f),
            Vec2(200f, 150f),
            Vec2(300f, 200f),
            Vec2(400f, 250f),
        )
        assertNull(Homography.squareToQuad(collinear))
        assertNull(Homography.rectToQuad(cardWidth, cardHeight, collinear))
    }

    @Test
    fun aCardOfNoSizeHasNoMapEither() {
        val quad = quadOf(handPose)
        assertNull(Homography.rectToQuad(0f, cardHeight, quad))
        assertNull(Homography.rectToQuad(cardWidth, 0f, quad))
        assertNull(Homography.squareToQuad(quad.take(3)))
    }

    @Test
    fun aQuadTooThinToDrawIsStillAMapMadeOfFiniteNumbers() {
        // The sliver above the point where the caller's own area guard takes
        // over. Deciding not to draw it is `CardSolid.MIN_DRAWN_AREA`'s job;
        // this one's job is only to not produce a NaN on the way there.
        val sliver = listOf(
            Vec2(100f, 100f),
            Vec2(300f, 100.4f),
            Vec2(300f, 101f),
            Vec2(100f, 100.6f),
        )
        val map = assertNotNull(Homography.rectToQuad(cardWidth, cardHeight, sliver))
        listOf(map.m00, map.m01, map.m02, map.m10, map.m11, map.m12, map.m20, map.m21, map.m22)
            .forEach { assertTrue(it.isFinite(), "every term is a number: $map") }
    }

    // ---- which way round it is ------------------------------------------------------

    @Test
    fun aCardShowingItsBackIsMappedThroughAMirror() {
        // `CardFace` un-mirrors the back so it reads the right way round, and
        // that only stays correct if the map itself still reverses orientation
        // the way the parent layer's `rotationY = 180` used to.
        fun signedArea(map: Homography): Float {
            val corners = listOf(
                map.map(0f, 0f),
                map.map(cardWidth, 0f),
                map.map(cardWidth, cardHeight),
                map.map(0f, cardHeight),
            )
            var twice = 0f
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % corners.size]
                twice += a.x * b.y - b.x * a.y
            }
            return twice / 2f
        }

        val faceUp = Pose3(position = Vec3(700f, 500f, 0f))
        val faceDown = faceUp.copy(rotY = 180f)

        assertTrue(
            signedArea(mapOf(faceUp)) * signedArea(mapOf(faceDown)) < 0f,
            "turning a card over turns its map inside out",
        )
    }

    // ---- the sixteen floats Compose reads -------------------------------------------

    @Test
    fun theSixteenFloatsAreInTheOrderComposeReadsThem() {
        val map = mapOf(handPose)
        val values = FloatArray(16)
        map.writeComposeMatrix(values)

        // Compose's `Matrix` companion names these indices, and its own
        // `map(Offset)` reads exactly these nine.
        assertEquals(map.m00, values[0], "ScaleX")
        assertEquals(map.m01, values[4], "SkewX")
        assertEquals(map.m02, values[12], "TranslateX")
        assertEquals(map.m10, values[1], "SkewY")
        assertEquals(map.m11, values[5], "ScaleY")
        assertEquals(map.m12, values[13], "TranslateY")
        assertEquals(map.m20, values[3], "Perspective0")
        assertEquals(map.m21, values[7], "Perspective1")
        assertEquals(map.m22, values[15], "Perspective2")

        // And the six Android's own conversion refuses to carry, which it
        // therefore insists on finding empty, plus the z axis left alone.
        listOf(2, 6, 8, 9, 11, 14).forEach {
            assertEquals(0f, values[it], "index $it must be zero for android.graphics.Matrix")
        }
        assertEquals(1f, values[10], "ScaleZ")
    }

    @Test
    fun aPointPutThroughComposesArithmeticLandsWhereThisMapSaysItWill() {
        // Compose's `Matrix.map(Offset)`, transcribed. Sixteen floats in the
        // wrong order is a map that looks plausible and draws every card
        // transposed, and this is the only place in core that can catch it.
        val map = mapOf(handPose)
        val v = FloatArray(16)
        map.writeComposeMatrix(v)

        fun composeMap(x: Float, y: Float): Vec2 {
            val w = v[3] * x + v[7] * y + v[15]
            val inverse = 1f / w
            return Vec2(
                (v[0] * x + v[4] * y + v[12]) * inverse,
                (v[1] * x + v[5] * y + v[13]) * inverse,
            )
        }

        // Interior points as well as corners: a transposed embedding can agree
        // at the corners of a symmetric quad by coincidence, and never inside.
        listOf(
            Vec2(0f, 0f), Vec2(cardWidth, 0f), Vec2(cardWidth, cardHeight), Vec2(0f, cardHeight),
            Vec2(cardWidth / 2f, cardHeight / 2f), Vec2(17f, 141f), Vec2(119f, 3f),
        ).forEach { at ->
            val want = map.map(at)
            val got = composeMap(at.x, at.y)
            assertClose(want.x, got.x, 1e-2f, "x at $at:")
            assertClose(want.y, got.y, 1e-2f, "y at $at:")
        }
    }

    // ---- and the map that undoes it -------------------------------------------------

    @Test
    fun theInverseTakesAMatPixelBackToTheCardItLandedOn() {
        // The claim the pile's ruled side rests on: a shader handed a point in
        // the mat's frame can say which part of the card is under it. Swept over
        // the interior rather than the corners, because a projective map is
        // *most* nonlinear away from the points that defined it.
        listOf(handPose, Pose3(position = Vec3(700f, 500f, 0f))).forEach { pose ->
            val map = mapOf(pose)
            val back = assertNotNull(map.inverse(), "a card in view has an inverse")

            for (i in 0..4) {
                for (j in 0..4) {
                    val at = Vec2(cardWidth * i / 4f, cardHeight * j / 4f)
                    val there = map.map(at)
                    val home = back.map(there)
                    assertClose(at.x, home.x, 1e-2f, "u at $at on $pose:")
                    assertClose(at.y, home.y, 1e-2f, "v at $at on $pose:")
                }
            }
        }
    }

    @Test
    fun theInverseIsAMapRatherThanNineNumbersAndSoIgnoresItsOwnScale() {
        // A projective map is defined up to scale, which is the whole reason the
        // adjugate is returned without dividing by the determinant. If that is
        // wrong the round trip above still passes — det is a constant factor and
        // `map` divides it out — so this is the claim that actually pins it, and
        // it also pins that nobody may "fix" the missing division later.
        val map = mapOf(handPose)
        val scaled = Homography(
            map.m00 * 8f, map.m01 * 8f, map.m02 * 8f,
            map.m10 * 8f, map.m11 * 8f, map.m12 * 8f,
            map.m20 * 8f, map.m21 * 8f, map.m22 * 8f,
        )

        val a = assertNotNull(map.inverse())
        val b = assertNotNull(scaled.inverse())

        listOf(Vec2(400f, 300f), Vec2(900f, 620f), Vec2(1210f, 88f)).forEach { at ->
            assertClose(a.map(at).x, b.map(at).x, 1e-2f, "x at $at:")
            assertClose(a.map(at).y, b.map(at).y, 1e-2f, "y at $at:")
        }
    }

    @Test
    fun aCardSeenExactlyEdgeOnHasNoInverseEither() {
        // `squareToQuad` already refuses an edge-on card, so the only way to
        // reach a singular matrix through the front door is to build one. Every
        // card passes through this twice per flip, and the answer has to be null
        // rather than nine infinities: a NaN is not a coordinate — it survives
        // every clamp downstream and draws nothing at all.
        val flattened = Homography(
            1f, 2f, 3f,
            2f, 4f, 6f,
            0f, 0f, 1f,
        )
        assertNull(flattened.inverse(), "two proportional rows are not a map")

        assertNull(Homography(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f).inverse(), "nor is nothing")
    }

    @Test
    fun theIdentityUndoesToItself() {
        val back = assertNotNull(Homography.Identity.inverse())
        listOf(Vec2(0f, 0f), Vec2(37f, 512f)).forEach { at ->
            assertClose(at.x, back.map(at).x, 1e-4f, "x at $at:")
            assertClose(at.y, back.map(at).y, 1e-4f, "y at $at:")
        }
    }

    // ---- how fast the map runs, which is what stops a texture aliasing --------------

    @Test
    fun theScaleAtAPointIsTheAreaTheMapOpensThereSquareRooted() {
        // Checked against the map itself rather than against the formula: take a
        // small square around the point, map its corners, and measure the area
        // that comes out. The closed form has to agree with what the homography
        // actually does, or every level of detail derived from it is a plausible
        // number about nothing.
        val quad = listOf(
            Vec2(120f, 40f), Vec2(880f, 90f), Vec2(760f, 520f), Vec2(200f, 470f),
        )
        val h = Homography.rectToQuad(100f, 100f, quad)!!
        listOf(20f to 20f, 50f to 50f, 80f to 30f, 30f to 80f).forEach { (x, y) ->
            val d = 0.05f
            val a = h.map(x - d, y - d)
            val b = h.map(x + d, y - d)
            val c = h.map(x + d, y + d)
            // The shoelace area of the mapped square, over the source area.
            val area = abs(
                (b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y),
            ) / ((2f * d) * (2f * d))
            assertEquals(sqrt(area), h.scaleAt(x, y), sqrt(area) * 0.02f, "at ($x, $y)")
        }
    }

    @Test
    fun aMapThatRunsAwayFromTheCameraRunsSlowerTheFurtherItGoes() {
        // The whole reason this exists. A wall seen from a chair is drawn as a
        // trapezoid, so one end of it packs many more surface units into a pixel
        // than the other — and a texture band-limited on the near end crawls on
        // the far one. Stated as a claim about the map rather than about a wall.
        val trapezoid = listOf(
            Vec2(300f, 100f), Vec2(700f, 100f), Vec2(1000f, 500f), Vec2(0f, 500f),
        )
        val h = Homography.rectToQuad(100f, 100f, trapezoid)!!
        val far = h.scaleAt(50f, 0f)
        val near = h.scaleAt(50f, 100f)
        assertTrue(near > far * 1.5f, "the far end is not smaller: $far against $near")
    }

    @Test
    fun aMapWithNoAreaHasNoScale() {
        // Degenerate rather than merely small: three collinear corners give a
        // determinant of nothing, and a texture on it has no size to be. Zero is
        // the answer a caller can act on; a NaN is not.
        val flat = listOf(Vec2(0f, 0f), Vec2(100f, 0f), Vec2(200f, 0f), Vec2(300f, 0f))
        val h = Homography.rectToQuad(100f, 100f, flat)
        assertTrue(h == null || h.scaleAt(50f, 50f) == 0f, "a flat quad reports a scale")
    }
}
