package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a scene is allowed to change about the light, and what it is not.
 *
 * A preset is a set of numbers somebody chose by eye, so most of what could be
 * written here would be a recording rather than a claim — `GoldenStageTest`
 * already owns that job and says why it should stay the only one. What is left
 * is the handful of things that are *true of every preset there will ever be*,
 * and those are worth pinning because the next scene will be added by copying
 * one of these and changing the numbers.
 */
class StageLightingTest {

    private val presets = listOf(
        "minimal" to StageLighting.Minimal,
        "day" to StageLighting.DeskDay,
        "night" to StageLighting.DeskNight,
    )

    // ---- the minimal scene is the stage that already shipped -----------------------

    @Test
    fun theMinimalRigIsTheVeryObjectsTheGoldenWasRecordedAgainst() {
        // Identity rather than equality, on purpose. Equal numbers typed twice
        // drift; the same object cannot.
        assertSame(StageRig.Key, StageLighting.Minimal.key)
        assertSame(StageRig.Bounce, StageLighting.Minimal.bounce)
        assertSame(StageRig.Rim, StageLighting.Minimal.rim)
    }

    @Test
    fun passingTheMinimalRigExplicitlyIsTheSameAsNotPassingOne() {
        // The whole safety of the refactor in one assertion: every call site
        // that used to read the singleton now passes a value, and for the scene
        // that already shipped the two must be indistinguishable.
        normals().forEach { normal ->
            val eye = StageRig.eye(21f)
            assertEquals(
                StageRig.lit(normal, eye),
                StageRig.lit(normal, eye, StageLighting.Minimal),
                "$normal",
            )
        }
    }

    // ---- what every preset owes ----------------------------------------------------

    @Test
    fun noPresetTakesTheAmbientBelowTheFloorToneCanShadeArtAt() {
        presets.forEach { (name, lighting) ->
            assertTrue(
                lighting.key.ambient >= StageLighting.NIGHT_FLOOR,
                "$name asks for an ambient of ${lighting.key.ambient}; " +
                    "Tone.veil stops being honest about dark card art below " +
                    "${StageLighting.NIGHT_FLOOR}",
            )
        }
    }

    @Test
    fun everyLampPointsSomewhere() {
        presets.forEach { (name, lighting) ->
            listOf("key" to lighting.key, "bounce" to lighting.bounce, "rim" to lighting.rim)
                .forEach { (lamp, light) ->
                    val length = light.direction.length
                    assertTrue(abs(length - 1f) < 1e-3f, "$name's $lamp is $length long")
                }
        }
    }

    @Test
    fun everyPresetSplitsTheKeyAndTheBounceAcrossWhite() {
        // The rule StageRig.Bounce argues for: nothing anybody has ever played
        // on is lit by one colour from both sides, and a difference across a
        // single object is what carries.
        presets.forEach { (name, lighting) ->
            assertTrue(
                lighting.key.warmth * lighting.bounce.warmth < 0f,
                "$name lights both sides from the same end of the spectrum",
            )
        }
    }

    @Test
    fun aSurfaceNoLampReachesComesBackNeutral() {
        // The rule the whole temperature model rests on: the room is achromatic,
        // so colour appears in exact proportion to how much *directional* light
        // landed. A face lit by ambient alone is grey in every scene.
        val away = Vec3(0f, 0f, -1f)
        presets.forEach { (name, lighting) ->
            val lit = StageRig.lit(away, Vec3.Toward, lighting)
            assertEquals(0f, lit.warmth, 1e-4f, "$name tints its ambient")
            assertEquals(lit.amount, lit.red, 1e-4f, name)
            assertEquals(lit.amount, lit.blue, 1e-4f, name)
        }
    }

    // ---- what makes day and night two different rooms ------------------------------

    @Test
    fun theWindowIsCoolAndTheLampIsWarm() {
        assertTrue(StageLighting.DeskDay.key.warmth < 0f, "the window is not daylight")
        assertTrue(StageLighting.DeskNight.key.warmth > 0f, "the lamp is not tungsten")
    }

    @Test
    fun theLampIsTheWarmestKeyInTheApp() {
        assertTrue(StageLighting.DeskNight.key.warmth > StageLighting.Minimal.key.warmth)
    }

    @Test
    fun daylightIsFlatterThanLamplight() {
        // Not a stylistic choice: a room lit through a window receives light
        // from the whole sky and every wall, and its shadows are shallow because
        // of it. The ambient is where that fact lives.
        assertTrue(StageLighting.DeskDay.key.ambient > StageLighting.DeskNight.key.ambient)
    }

    @Test
    fun switchingBetweenTheDeskScenesSwingsEveryShadowAcrossTheTable() {
        // The one cue nobody has to be told about, and it is a consequence of
        // the two keys sitting on opposite sides rather than of anything drawn.
        val pose = Pose3(position = Vec3(100f, 100f, 40f))
        val day = Shadows.cast(pose, 60f, 88f, StageLighting.DeskDay.key)
        val night = Shadows.cast(pose, 60f, 88f, StageLighting.DeskNight.key)
        assertTrue(day != null && night != null, "a lifted card casts in both scenes")

        val dayOffset = day.corners.first().x - pose.position.x
        val nightOffset = night.corners.first().x - pose.position.x
        assertTrue(
            dayOffset * nightOffset < 0f,
            "both scenes throw the shadow the same way ($dayOffset, $nightOffset)",
        )
    }

    @Test
    fun theTwoDeskScenesDisagreeAboutEveryFaceTheyLight() {
        // A preset that changed the colour and not the geometry would be a
        // colour grade wearing a rig's clothes.
        val eye = StageRig.eye(21f)
        val differing = normals().count { normal ->
            val day = StageRig.lit(normal, eye, StageLighting.DeskDay)
            val night = StageRig.lit(normal, eye, StageLighting.DeskNight)
            abs(day.amount - night.amount) > 1e-3f
        }
        assertEquals(normals().size, differing, "some faces are lit identically by both")
    }

    /** A spread of surfaces: the felt, a card on edge, and the four walls of a pile. */
    private fun normals(): List<Vec3> = listOf(
        Vec3(0f, 0f, 1f),
        Vec3(0f, 0f, -1f),
        Vec3(1f, 0f, 0f),
        Vec3(-1f, 0f, 0f),
        Vec3(0f, 1f, 0f),
        Vec3(0f, -1f, 0f),
        Vec3(0.5f, 0.5f, 0.7f).normalised(),
        Vec3(-0.6f, 0.3f, 0.74f).normalised(),
    )
}
