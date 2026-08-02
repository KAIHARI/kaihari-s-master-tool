package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadingTest {

    private val key = StageRig.Key
    private val gloss = CardMaterial.Gloss

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 1e-3f, note: String = "") {
        assertTrue(abs(expected - actual) < tolerance, "$note expected $expected, was $actual")
    }

    // ---- the rig -------------------------------------------------------------------

    @Test
    fun theKeyIsAboveTheTableAndOffToOneSide() {
        // Directly overhead casts its shadow directly underneath, which is the
        // same as casting none — and then nothing on the table looks lifted.
        assertTrue(key.direction.z < -0.5f, "the key points down at the felt")
        assertTrue(abs(key.direction.x) > 0.1f, "and not straight down")
        assertClose(1f, key.direction.length, note = "the key is a unit direction:")
        assertClose(1f, key.toLight.length, note = "and so is the way back to it:")
    }

    @Test
    fun theWayBackToTheLightIsTheWayTheLightCame() {
        assertClose(-key.direction.x, key.toLight.x)
        assertClose(-key.direction.z, key.toLight.z)
    }

    @Test
    fun theEyeRidesUpAsTheTableLaysBack() {
        // Seen from the mat's own frame, tilting the table away is the same as
        // the viewer standing up — and this is the number the specular pool on
        // every resting card is placed from.
        val flat = StageRig.eye(0f)
        val tilted = StageRig.eye(15f)

        assertClose(0f, flat.y, note = "a flat table is looked at square on:")
        assertClose(1f, flat.z)
        assertTrue(tilted.y > 0.2f, "the eye moves toward the near edge, was ${tilted.y}")
        assertTrue(tilted.z > 0.9f, "but is still mostly straight on, was ${tilted.z}")
        assertClose(1f, tilted.length, note = "the eye is a unit direction:")
    }

    // ---- diffuse -----------------------------------------------------------------

    @Test
    fun aSurfaceFacingTheLightIsBrighterThanOneFacingAway() {
        val toward = Shading.lit(key.toLight, key)
        val away = Shading.lit(-key.toLight, key)

        assertClose(1f, toward, note = "square into the key is full brightness:")
        assertClose(key.ambient, away, note = "and facing away leaves the ambient:")
        assertTrue(toward > away)
    }

    @Test
    fun nothingEverGoesFullyBlack() {
        // This is a black stage. A card is the brightest object in the frame,
        // and a card that dims to nothing because it tilted reads as a bug
        // rather than as shading.
        listOf(
            Vec3(0f, 0f, 1f),
            Vec3(0f, 0f, -1f),
            Vec3(1f, 0f, 0f),
            Vec3(-1f, -1f, -1f),
        ).forEach { normal ->
            val lit = Shading.lit(normal, key)
            assertTrue(lit >= key.ambient - 1e-4f, "went darker than ambient at $normal: $lit")
            assertTrue(lit <= 1f, "went brighter than white at $normal: $lit")
        }
    }

    @Test
    fun theBounceKeepsACardTurnedFromTheKeyOffTheFloor() {
        // Turned hard away from the key but still looking at the viewer, which
        // is the case that matters: a card angled out of the light is the one
        // the fill exists for.
        val turnedAway = Rot3.normal(Pose3(rotY = 50f))
        val keyOnly = Shading.lit(turnedAway, key)
        val both = StageRig.lit(turnedAway)

        assertTrue(both > keyOnly, "the fill should do something: $both vs $keyOnly")
        assertTrue(both <= 1f)
    }

    @Test
    fun aSurfaceFacingIntoTheTableGetsNothingFromEitherLight() {
        // Both lights are above the felt, so the underside of anything is on
        // ambient alone. Correct, and worth pinning down — a fill that lit the
        // bottom of a card would be a light coming up through the table.
        val downward = Vec3(0f, 0f, -1f)

        assertClose(key.ambient, StageRig.lit(downward), note = "the underside:")
    }

    // ---- the highlight -----------------------------------------------------------

    @Test
    fun aFlatCardsHighlightSitsOnTheSideTheLightIsOn() {
        // The key comes from up and to the left, so the pool does too.
        val shade = Shading.of(Pose3(), gloss, key)

        assertTrue(shade.hotspot.x < 0.5f, "left of centre, was ${shade.hotspot.x}")
        assertTrue(shade.hotspot.y < 0.5f, "above centre, was ${shade.hotspot.y}")
    }

    @Test
    fun tiltingTheCardWalksTheHighlightAcrossIt() {
        // The single perception the whole material model exists to produce: a
        // highlight that *moves* rather than a brightness that changes. It is
        // why you tilt a real card to read the small print.
        val level = Shading.of(Pose3(), gloss, key).hotspot
        val turnedAway = Shading.of(Pose3(rotY = 18f), gloss, key).hotspot
        val turnedToward = Shading.of(Pose3(rotY = -18f), gloss, key).hotspot

        assertTrue(turnedAway.x < level.x, "pushing the right edge back slides it left")
        assertTrue(turnedToward.x > level.x, "and pulling it forward slides it right")
        assertTrue(
            abs(turnedToward.x - turnedAway.x) > 0.25f,
            "and it should be a real journey, not a nudge",
        )
    }

    @Test
    fun theHighlightIsAllowedToLeaveTheCard() {
        // Clamping it to the border would pin a bright pool to the edge of a
        // card that should simply have stopped catching the light.
        val steep = Shading.of(Pose3(rotY = -55f), gloss, key)

        assertTrue(steep.hotspot.x > 1f, "it should be off the edge by now: ${steep.hotspot.x}")
        assertTrue(steep.hotspot.x.isFinite(), "and still a number")
    }

    @Test
    fun aCardTurnedAwayFromTheLightHasNoHighlightAtAll() {
        // Facing the felt: lambert is zero, so the specular term has to be too,
        // or the back of the card glows.
        val shade = Shading.of(Pose3(rotX = 100f), gloss, key)

        assertEquals(0f, shade.specular)
    }

    /**
     * The brightest the material ever gets, found by turning the card until it
     * gets there — rather than by picking one angle and hoping it is near the
     * mirror direction. Foil's highlight is *tighter* as well as brighter, so
     * at an arbitrary angle it can legitimately be the dimmer of the two, and a
     * test that fixed the angle would be asserting the wrong thing.
     */
    private fun peakSpecular(material: CardMaterial): Float =
        (-60..60 step 3).maxOf { turn ->
            (-60..60 step 3).maxOf { tilt ->
                Shading.of(
                    Pose3(rotX = tilt.toFloat(), rotY = turn.toFloat()),
                    material,
                    key,
                ).specular
            }
        }

    @Test
    fun foilCatchesMoreLightThanCardStockAndASleeveCatchesLeast() {
        val foil = peakSpecular(CardMaterial.Foil)
        val card = peakSpecular(CardMaterial.Gloss)
        val sleeve = peakSpecular(CardMaterial.Sleeve)

        assertTrue(foil > card, "foil $foil vs gloss $card")
        assertTrue(card > sleeve, "gloss $card vs sleeve $sleeve")
    }

    @Test
    fun foilsHighlightIsTighterThanCardStocksAsWellAsBrighter() {
        // Which is what makes it read as metallic rather than as a brighter
        // piece of paper: the pool is smaller, so it sweeps rather than glows.
        val across = { material: CardMaterial ->
            (-60..60 step 2).count { turn ->
                Shading.of(Pose3(rotY = turn.toFloat()), material, key).specular > 0.02f
            }
        }

        assertTrue(
            across(CardMaterial.Foil) < across(CardMaterial.Gloss),
            "foil ${across(CardMaterial.Foil)} vs gloss ${across(CardMaterial.Gloss)}",
        )
    }

    @Test
    fun onlyFoilSplitsTheHighlightIntoColour() {
        // The app's colour rule, kept: colour on a card face is light, and it
        // lives inside the specular term or nowhere.
        assertEquals(0f, CardMaterial.Gloss.iridescence)
        assertEquals(0f, CardMaterial.Sleeve.iridescence)
        assertTrue(CardMaterial.Foil.iridescence > 0.5f)
    }

    // ---- which side you are looking at ------------------------------------------

    @Test
    fun aSetCardIsLitAsTheBackItIsShowing() {
        // Not as a face nobody can see. Two cards at the same angle, one set,
        // must not have their highlights in the same place.
        val faceUp = Shading.of(Pose3(rotX = -20f), gloss, key)
        val faceDown = Shading.of(Pose3(rotX = -20f, rotY = 180f), CardMaterial.Sleeve, key)

        assertTrue(faceUp.facing > 0f, "face up is looking at us")
        assertTrue(faceDown.facing < 0f, "and set is not")
        assertTrue(faceDown.diffuse > 0f, "but it is still lit")
    }

    @Test
    fun theHighlightOnASetCardWalksTheRightWay() {
        // Mirrored along with the card. Without that, turning a set card over
        // sends its highlight the opposite way from a face-up one, and the two
        // stop looking like the same object.
        val level = Shading.of(Pose3(rotY = 180f), CardMaterial.Sleeve, key).hotspot
        val turned = Shading.of(Pose3(rotY = 180f + 18f), CardMaterial.Sleeve, key).hotspot

        assertTrue(turned.x < level.x, "was ${turned.x} against ${level.x}")
    }

    // ---- the rim ---------------------------------------------------------------------

    @Test
    fun theRimLightsUpAsACardGoesEdgeOn() {
        val square = Shading.of(Pose3(), gloss, key).fresnel
        val steep = Shading.of(Pose3(rotY = 70f), gloss, key).fresnel

        assertClose(0f, square, note = "a card facing you has no rim:")
        assertTrue(steep > 0.1f, "and one nearly edge-on has plenty: $steep")
    }

    // ---- the edges of a solid ----------------------------------------------------------

    @Test
    fun anEdgeTurnedAwayIsNotLitAtAll() {
        val faces = CardSolid.slab(Pose3(), 100f, 145f, depth = 30f)
        val far = faces.first { it.normal.y < -0.5f }

        assertEquals(0f, StageRig.face(far, StageRig.eye(15f)))
    }

    @Test
    fun theNearEdgeOfAPileIsLitByTheFillBecauseTheKeyCannotReachIt() {
        // The key comes from the player's side, so the one edge of a pile that
        // is ever visible is the one facing away from it. Without the bounce
        // that band is flat ambient, and a deck's white edge is not flat
        // ambient in any room anyone has played in.
        val faces = CardSolid.slab(Pose3(), 100f, 145f, depth = 30f)
        val near = faces.first { it.normal.y > 0.5f }

        val withFill = StageRig.face(near, StageRig.eye(15f))
        val keyOnly = Shading.lit(near.normal, key)

        assertTrue(withFill > keyOnly, "$withFill should beat $keyOnly")
        assertTrue(withFill <= 1f)
    }
}
