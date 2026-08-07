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

    // ---- a solid resting on the table ------------------------------------------------
    //
    // The bug these were written for shipped in v1.2.19 and was visible from
    // across a room. A pile's pose is its *top card* — that is the arrangement
    // the whole stage depends on, because it is what puts the top of a deck on
    // top of the deck — so casting from the pose casts from forty cards up in
    // the air. The deck walked away from its own shadow and left its white
    // edges standing where the card was not.

    @Test
    fun aDeckShadowsFromTheFeltAndNotFromItsTopCard() {
        val deck = CardSolid.pileDepth(40, width)
        val top = at(deck)

        val asPile = Shadows.cast(top, width, height, key, height, bodyDepth = deck)!!
        val asCard = Shadows.cast(top, width, height, key, height)!!

        val under = centre(asPile.corners)
        assertTrue(
            abs(under.x - 600f) < 1f && abs(under.y - 400f) < 1f,
            "a deck's shadow belongs under the deck, was $under",
        )

        val floating = centre(asCard.corners)
        assertTrue(
            (floating - under).length > width * 0.2f,
            "the test is worthless unless the two readings differ; they were $floating and $under",
        )
    }

    @Test
    fun aDeckIsPressedOntoTheTableRatherThanHeldAboveIt() {
        val deck = CardSolid.pileDepth(40, width)
        val resting = Shadows.cast(at(deck), width, height, key, height, bodyDepth = deck)!!
        val lying = shadow(0f)

        // The three numbers that say "resting" rather than "held": as dark, as
        // tight, and as much contact as a single card lying on the felt.
        assertTrue(abs(resting.alpha - lying.alpha) < 1e-3f, "as dark: ${resting.alpha}")
        assertTrue(abs(resting.spread - lying.spread) < 1e-3f, "as tight: ${resting.spread}")
        assertTrue(resting.contact > 0.98f, "and touching: ${resting.contact}")
    }

    @Test
    fun aCarriedCardStillReadsAsBeingInTheAir() {
        // The same rule must not flatten the one case the whole shadow model was
        // built for. A carried card's body is one card thick, so telling the
        // caster about it moves nothing that matters.
        val lift = height * 0.55f
        val body = CardSolid.thickness(width)
        val held = Shadows.cast(at(lift), width, height, key, height, bodyDepth = body)!!

        assertTrue(held.contact < 0.05f, "a held card has let go of the table: ${held.contact}")
        assertTrue(held.spread > height * 0.15f, "and its shadow has softened: ${held.spread}")
        assertTrue(
            (centre(held.corners) - Vec3(600f, 400f, 0f)).length > width * 0.3f,
            "and separated from underneath it",
        )
    }

    @Test
    fun aCardLeanedOnItsBottomEdgeKeepsEveryCornerAboveTheTable() {
        // The hand's lean. Rotating about the centre puts the near edge below
        // the felt, and a corner below the surface travels a *negative* distance
        // along the light — the shadow quad folds back through itself. Lifting
        // by (h/2)·sin θ is exactly what stops that, for any lean.
        val lean = -24f
        val lift = height / 2f * kotlin.math.sin(abs(lean) * (kotlin.math.PI.toFloat() / 180f))

        val sunk = CardSolid.face(at(0f, Pose3(rotX = lean)), width, height)
        assertTrue(sunk.any { it.z < -1f }, "the test needs the broken case to be broken")

        val standing = CardSolid.face(at(lift, Pose3(rotX = lean)), width, height)
        standing.forEach {
            assertTrue(it.z > -0.01f, "every corner is on or above the felt, found z = ${it.z}")
        }
        assertTrue(
            standing.maxOf { it.z } > height * 0.3f,
            "and the top edge is properly up in the air: ${standing.maxOf { it.z }}",
        )
    }

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

    // ---- a light with a size, and a light with a place ---------------------------

    private val board = com.kaiharimoto.mastertool.core.layout.BoardLayouter
        .solve(1600f, 856f, 59f / 86f, perspectiveGrowth = 1.2f)
    private val mat = com.kaiharimoto.mastertool.core.scene.Scenery.mat(board)
    private val lamp = com.kaiharimoto.mastertool.core.scene.Scenery.lightingFor(
        com.kaiharimoto.mastertool.core.scene.Scene.DESK,
        com.kaiharimoto.mastertool.core.scene.TimeOfDay.NIGHT,
        board,
    ).key
    private val window = com.kaiharimoto.mastertool.core.scene.Scenery.lightingFor(
        com.kaiharimoto.mastertool.core.scene.Scene.DESK,
        com.kaiharimoto.mastertool.core.scene.TimeOfDay.DAY,
        board,
    ).key

    private fun spread(light: Light, z: Float, x: Float = mat.centerX, y: Float = mat.centerY) =
        Shadows.cast(
            Pose3(position = Vec3(x, y, z)),
            width,
            height,
            light,
            cardHeight = height,
        )!!.spread

    @Test
    fun aLampWithNoSizeCastsExactlyTheEdgeItAlwaysDid() {
        // The golden records this number, so it is guarded twice: here to the
        // bit, and there as a recording. A light with no stated size has no
        // angle to derive a penumbra from, and the heuristic it falls back on is
        // the expression that shipped, character for character.
        for (step in 0..40) {
            val lift = step / 40f
            val z = lift * height
            val expected = height * (0.018f + lift * 0.34f)
            val cast = Shadows.cast(at(z), width, height, key, cardHeight = height)!!
            assertEquals(expected, cast.spread, "at a lift of $lift")
            assertEquals(0f, cast.umbra, "a sizeless light has no umbra")
        }
    }

    @Test
    fun aPointLampThrowsItsShadowsOutwardFromWhereItStands() {
        // The visible prize, and the one thing no directional light can do: the
        // shadows of two cards on opposite sides of the table point in different
        // directions, because they point away from the same lamp.
        val near = Shadows.cast(
            Pose3(position = Vec3(mat.right - 60f, mat.top + 60f, 90f)),
            width, height, lamp, cardHeight = height,
        )!!
        val far = Shadows.cast(
            Pose3(position = Vec3(mat.left + 60f, mat.bottom - 60f, 90f)),
            width, height, lamp, cardHeight = height,
        )!!

        fun swing(cast: CardShadow, from: Vec3): Vec3 {
            val middle = cast.corners.fold(Vec3.Zero) { sum, corner -> sum + corner } /
                cast.corners.size.toFloat()
            return middle - from
        }
        val a = swing(near, Vec3(mat.right - 60f, mat.top + 60f, 0f)).normalised()
        val b = swing(far, Vec3(mat.left + 60f, mat.bottom - 60f, 0f)).normalised()
        val apart = a dot b
        assertTrue(apart < 0.8f, "the two shadows run parallel: $a and $b")

        // And under the rig that shipped, they are exactly parallel.
        val flatNear = Shadows.cast(
            Pose3(position = Vec3(mat.right - 60f, mat.top + 60f, 90f)),
            width, height, StageLighting.DeskNight.key, cardHeight = height,
        )!!
        val flatFar = Shadows.cast(
            Pose3(position = Vec3(mat.left + 60f, mat.bottom - 60f, 90f)),
            width, height, StageLighting.DeskNight.key, cardHeight = height,
        )!!
        val flatA = swing(flatNear, Vec3(mat.right - 60f, mat.top + 60f, 0f)).normalised()
        val flatB = swing(flatFar, Vec3(mat.left + 60f, mat.bottom - 60f, 0f)).normalised()
        assertEquals(1f, flatA dot flatB, 1e-3f, "the shipped rig is not parallel after all")
    }

    @Test
    fun aBiggerSourceMakesASofterEdgeAndTheWindowIsBiggerThanTheLamp() {
        // The second-loudest difference between the two rooms, and it costs one
        // field: at the height a card is carried at, daylight's shadow edge is
        // more than twice as soft as lamplight's.
        listOf(0.55f * height, 0.88f * height, 1.35f * height).forEach { z ->
            assertTrue(
                spread(window, z) > spread(lamp, z) * 1.5f,
                "at $z the window (${spread(window, z)}) is not softer than the lamp " +
                    "(${spread(lamp, z)})",
            )
        }
    }

    @Test
    fun aRestingCardsShadowIsHardWhateverTheSourceIs() {
        // The one shadow that must stay crisp, because it is the whole cue that
        // says a card is on the table. An area source big enough to soften a
        // held card must not smear this one.
        val floor = height * 0.018f
        listOf(key, lamp, window).forEach { light ->
            assertEquals(floor, spread(light, 0f), floor * 0.01f, "a resting card under $light")
        }
    }

    @Test
    fun daylightLosesItsUmbraLongBeforeLamplightDoes() {
        // What makes day and night two rooms rather than two colour grades: the
        // height at which a shadow's soft edge is as wide as the card itself.
        fun dissolvesAt(light: Light): Float {
            var z = 0f
            while (z < height * 4f) {
                if (2f * spread(light, z) >= width) return z / height
                z += height / 200f
            }
            return Float.MAX_VALUE
        }
        val sky = dissolvesAt(window)
        val bulb = dissolvesAt(lamp)
        // Under a card height for the window: daylight has lost its edge before a
        // card is even properly off the table.
        assertTrue(sky < 1f, "daylight stays hard until $sky card heights")
        // And the lamp holds an edge for most of a card height longer — measured
        // at 0.67 against 1.24, a ratio of 1.8. It is not the 2.4 the widths
        // alone suggest, because both spreads share an antialiasing floor that
        // dominates near the felt and compresses the difference; the bar is set
        // at where it actually lands rather than at where the ratio of the two
        // radii would put it.
        assertTrue(bulb > sky * 1.6f, "the two sources are the same size: $sky and $bulb")
    }

    @Test
    fun aCardRestingOnTheFeltStillCastsUnderAPlacedLamp() {
        // The trap. `CardSolid.face(..., atDepth)` puts a resting solid's base
        // one thickness *below* the felt, so the ray to the surface legitimately
        // runs backwards — and a guard against that would return null for every
        // card on the board at once.
        val thickness = CardSolid.thickness(width)
        listOf(key, lamp, window).forEach { light ->
            val cast = Shadows.cast(
                Pose3(position = Vec3(mat.centerX, mat.centerY, 0f)),
                width,
                height,
                light,
                cardHeight = height,
                bodyDepth = thickness,
            )
            assertTrue(cast != null, "a card lying on the felt casts nothing under $light")
        }
    }

    @Test
    fun theApertureAndTheCardAreCastByTheSameArithmetic() {
        // One function, two consumers — a window's opening thrown onto a desk is
        // a card's outline thrown onto the felt with different numbers.
        val pose = Pose3(position = Vec3(mat.centerX, mat.centerY, 140f))
        val corners = CardSolid.face(pose, width, height)
        val direct = Shadows.landOn(corners, lamp)!!
        val cast = Shadows.cast(pose, width, height, lamp, cardHeight = height)!!
        direct.corners.zip(cast.corners).forEach { (a, b) ->
            assertEquals(a.x, b.x, 1e-3f)
            assertEquals(a.y, b.y, 1e-3f)
        }
    }

    @Test
    fun aLightParallelToTheTableStillCastsNothing() {
        val sideways = Light(direction = Vec3(1f, 0f, 0f))
        assertNull(Shadows.landOn(CardSolid.face(at(40f), width, height), sideways))
    }
}
