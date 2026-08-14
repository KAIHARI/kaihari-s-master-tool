package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.scene.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a lamp with a place does, and — first — what a lamp without one does not.
 *
 * Half of these are about the no-op. `GoldenStageTest` is the acceptance
 * criterion for this whole release and it renders the minimal stage against
 * lamps that have no position, so every new term has to be invisible to it. A
 * golden going red would say *something* moved; these say which thing, and they
 * say it without a tolerance.
 */
class LightPositionTest {

    private val layout: BoardLayout =
        BoardLayouter.solve(1600f, 856f, 59f / 86f, perspectiveGrowth = 1.2f)

    private val mat = Scenery.mat(layout)
    private val night = Scenery.lightingFor(Scene.DESK, TimeOfDay.NIGHT, layout)
    private val day = Scenery.lightingFor(Scene.DESK, TimeOfDay.DAY, layout)
    private val lamp = Scenery.lamp(layout)

    private val everywhere = listOf(
        Vec3(mat.left, mat.top, 0f),
        Vec3(mat.right, mat.top, 0f),
        Vec3(mat.left, mat.bottom, 0f),
        Vec3(mat.right, mat.bottom, 0f),
        Vec3(mat.centerX, mat.centerY, 0f),
        Vec3(mat.centerX, mat.centerY, 200f),
        Vec3(0f, 0f, 0f),
        Vec3(-500f, 900f, -60f),
    )

    private val normals = listOf(
        Vec3(0f, 0f, 1f),
        Vec3(0f, 0f, -1f),
        Vec3(1f, 0f, 0f),
        Vec3(-1f, 0f, 0f),
        Vec3(0f, 1f, 0f),
        Vec3(0f, -1f, 0f),
        Vec3(0.5f, 0.5f, 0.7f).normalised(),
        Vec3(-0.6f, 0.3f, 0.74f).normalised(),
    )

    private val eyes = listOf(StageRig.eye(5f), StageRig.eye(21f), StageRig.eye(34f, 45f))

    // ---- the no-op ------------------------------------------------------------------

    @Test
    fun aLampWithNoPositionIsTheLampThatShipped() {
        // Identity rather than equality on `toLightFrom`, and no tolerance at
        // all on the attenuation. An eager `let` or an elvis whose right-hand
        // side is evaluated anyway would pass an equality check and fail this.
        listOf(StageRig.Key, StageRig.Bounce, StageRig.Rim).forEach { light ->
            everywhere.forEach { point ->
                assertSame(light.toLight, light.toLightFrom(point), "$light at $point")
                assertSame(light.travel, light.travelFrom(point), "$light at $point")
                assertEquals(1f, light.attenuation(point), "$light at $point")
                assertEquals(0f, light.angularRadius(point), "$light at $point")
            }
        }
    }

    @Test
    fun passingASurfacePointToAnUnpositionedRigChangesNothing() {
        // The golden's guarantee, stated where it fails fast and by name.
        normals.forEach { normal ->
            eyes.forEach { eye ->
                val without = StageRig.lit(normal, eye, StageLighting.Minimal)
                everywhere.forEach { point ->
                    assertEquals(
                        without,
                        StageRig.lit(normal, eye, StageLighting.Minimal, at = point),
                        "$normal from $point",
                    )
                }
            }
        }
    }

    @Test
    fun aPositionedLampIsIgnoredWhenNobodyOffersASurface() {
        // The branch is on the *position*, not on the point. That is what keeps
        // every existing single-argument call meaningful rather than silently
        // lit from the origin.
        val unplaced = night.copy(key = night.key.copy(position = null))
        normals.forEach { normal ->
            eyes.forEach { eye ->
                assertEquals(
                    StageRig.lit(normal, eye, unplaced),
                    StageRig.lit(normal, eye, night),
                    "$normal",
                )
            }
        }
    }

    @Test
    fun aFaceIsLitFromItsOwnCentreAndNotFromTheOrigin() {
        // `Face` has carried its own centre since it was written and the rig was
        // discarding it. Forgetting to pass it is a silent no-op everywhere
        // except under a placed lamp, which is exactly where it matters.
        val faces = Scenery.of(Scene.DESK, TimeOfDay.NIGHT, layout, 1600f, 856f)
            .pieces.single { it.name == "desk" }.box.faces()
        val eye = StageRig.eye(21f)

        val top = faces.single { it.normal.z > 0.99f }
        assertEquals(StageRig.lit(top.normal, eye, night, at = top.centre), StageRig.face(top, eye, night))
        assertTrue(
            StageRig.face(top, eye, night) != StageRig.lit(top.normal, eye, night),
            "the face is lit from nowhere in particular",
        )
    }

    // ---- what a placed lamp actually does --------------------------------------------

    @Test
    fun theLampIsBrightestDirectlyBeneathItselfAndNeverBrighterThanThat() {
        // The clamp that stops a `(d0/d)^2` form from flat-topping the near half
        // of the table to white. Checked over a grid, and over cards lifted
        // toward the bulb, which is the one place the light may run out of
        // headroom.
        val foot = Vec3(lamp.x, lamp.y, 0f)
        assertEquals(1f, night.key.attenuation(foot), 1e-5f, "not brightest under itself")

        for (column in 0..40) {
            for (row in 0..40) {
                val at = Vec3(
                    x = mat.left + mat.width * column / 40f,
                    y = mat.top + mat.height * row / 40f,
                    z = 0f,
                )
                assertTrue(night.key.attenuation(at) <= 1f, "over one at $at")
                assertTrue(night.key.attenuation(at.copy(z = 220f)) <= 1f, "over one lifted at $at")
            }
        }
    }

    @Test
    fun theFallOffIsMonotonicAwayFromTheLamp() {
        val foot = Vec3(lamp.x, lamp.y, 0f)
        listOf(Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f))
            .forEach { along ->
                var previous = Float.MAX_VALUE
                for (step in 0..30) {
                    val here = night.key.attenuation(foot + along * (step * 60f))
                    assertTrue(here <= previous + 1e-6f, "the light rises again along $along")
                    previous = here
                }
            }
    }

    @Test
    fun theFallOffLandsOnTheTableAndNeverOnTheAmbient() {
        // `StageLighting.NIGHT_FLOOR`'s guarantee under a placed lamp. This is
        // the test that fails the day somebody multiplies the ambient by the
        // attenuation, which is the obvious and wrong way to make a room darker.
        listOf(night, day, StageLighting.Minimal).forEach { rig ->
            normals.forEach { normal ->
                eyes.forEach { eye ->
                    everywhere.forEach { point ->
                        assertTrue(
                            StageRig.lit(normal, eye, rig, at = point).amount >= rig.key.ambient,
                            "$normal at $point fell below the ambient",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theFarCornerOfTheMatIsDimmerThanTheNearOne() {
        // Fails by construction on the release that shipped: the ratio is
        // exactly 1.000, because a direction has no near and no far.
        val eye = StageRig.eye(21f)
        val under = StageRig.lit(Vec3(0f, 0f, 1f), eye, night, at = Vec3(mat.right, mat.top, 0f))
        val away = StageRig.lit(Vec3(0f, 0f, 1f), eye, night, at = Vec3(mat.left, mat.bottom, 0f))
        val swing = under.amount / away.amount
        assertTrue(swing in 1.15f..1.9f, "the table's light swings by $swing")

        val flat = StageRig.lit(Vec3(0f, 0f, 1f), eye, StageLighting.DeskNight)
        assertEquals(1f, flat.amount / flat.amount, "the shipped rig is flat, as expected")
    }

    @Test
    fun noCardOnTheMatIsDarkerThanAReadableCard() {
        // The other half of the ambient floor's job. A lamp that reaches one
        // corner of the table and not the other is a room; a lamp that leaves a
        // card unreadable is a bug, and this application exists to read cards.
        val eye = StageRig.eye(21f)
        for (column in 0..20) {
            for (row in 0..20) {
                val at = Vec3(
                    x = mat.left + mat.width * column / 20f,
                    y = mat.top + mat.height * row / 20f,
                    z = 0f,
                )
                val lit = StageRig.lit(Vec3(0f, 0f, 1f), eye, night, at = at)
                assertTrue(lit.amount > 0.55f, "a card at $at comes back at ${lit.amount}")
            }
        }
    }

    // ---- the pool and the sheen -------------------------------------------------------

    @Test
    fun aRoomWithNoLampInItHasNoPoolAndNoSheen() {
        val eye = StageRig.eye(21f)
        val eyeAt = StageSeat.TABLE.pose.planeFor(1600f, 856f).eyePoint(eye)
        assertNull(StageRig.pool(StageLighting.Minimal, eye))
        assertNull(StageRig.pool(day, eye), "the window is not a fixture with a foot")
        assertNull(StageRig.sheen(StageRig.Key, eyeAt))
        assertEquals(0f, StageRig.sheenRadius(StageRig.Key, eyeAt, 0.36f))
    }

    @Test
    fun theFeltAndTheCardsAgreeAboutWhereTheLampIs() {
        // The pool is the rig, sampled — not a second lighting model, which is
        // exactly what the hand-placed gradient it replaces was.
        val eye = StageRig.eye(21f)
        val pool = assertNotNull(StageRig.pool(night, eye))
        assertEquals(night.key.position!!.x, pool.foot.x, 1e-3f)
        assertEquals(night.key.position!!.y, pool.foot.y, 1e-3f)
        assertEquals(
            StageRig.lit(Vec3(0f, 0f, 1f), eye, night, at = Vec3(pool.foot.x, pool.foot.y, 0f)),
            pool.stops.first(),
        )
        assertEquals(StageRig.POOL_STOPS, pool.stops.size)
        assertTrue(pool.radius > mat.width / 2f, "the pool does not reach across the mat")
    }

    @Test
    fun thePoolFadesOutwardAndNeverBrightensAgain() {
        val pool = assertNotNull(StageRig.pool(night, StageRig.eye(21f)))
        pool.stops.zipWithNext().forEach { (near, far) ->
            assertTrue(far.amount <= near.amount + 1e-6f, "the pool brightens outward")
        }
    }

    @Test
    fun theMirrorOfTheLampSlidesTowardThePlayerAsYouSitDown() {
        // The thing most likely to be noticed first, and the reason the felt's
        // highlight is a sheen rather than a diffuse pool: it is a reflection,
        // so it moves when the head does. The shipped pool is pinned to a point
        // computed from a direction and cannot move at all.
        val centres = StageSeat.entries.map { seat ->
            val plane = seat.pose.planeFor(1600f, 856f)
            val eyeAt = plane.eyePoint(StageRig.eye(plane.tiltDegrees, plane.yawDegrees))
            assertNotNull(StageRig.sheen(night.key, eyeAt), "no sheen at ${seat.label}")
        }
        centres.zipWithNext().forEach { (higher, lower) ->
            assertTrue(lower.y > higher.y, "the highlight did not move: $centres")
        }
        centres.forEach {
            assertTrue(it.x in mat.left..mat.right, "the highlight is off the mat: $it")
        }

        // And it stays on the felt for as long as you are looking down at it.
        //
        // This used to say "every seat", and that was a fact about the seats
        // rather than about the light: all three of them looked down at the
        // table from more than fifty degrees of elevation, and a mirror image
        // seen from up there lands inside the mat. `StageSeat.POV` is a head at
        // a desk, and from there the lamp's reflection has slid off the near
        // edge — which is the *point* of the slide, not a failure of it. A
        // reflection is allowed to be past the end of the table; what is drawn
        // is the half of the highlight that is still on it.
        StageSeat.entries.zip(centres).forEach { (seat, centre) ->
            if (seat == StageSeat.POV) {
                assertTrue(
                    centre.y > mat.bottom,
                    "the reflection has not left the near edge at the POV seat: $centre",
                )
            } else {
                assertTrue(centre.y in mat.top..mat.bottom, "the highlight is off the mat: $centre")
            }
        }
    }

    @Test
    fun aRougherFeltSpreadsTheHighlightWider() {
        val plane = StageSeat.TABLE.pose.planeFor(1600f, 856f)
        val eyeAt = plane.eyePoint(StageRig.eye(plane.tiltDegrees, plane.yawDegrees))
        val polished = StageRig.sheenRadius(night.key, eyeAt, roughness = 0.05f)
        val matte = StageRig.sheenRadius(night.key, eyeAt, roughness = 0.36f)
        assertTrue(matte > polished * 2f, "roughness does nothing: $polished vs $matte")
    }

    // ---- the window is a size, not a place -------------------------------------------

    @Test
    fun theWindowKeepsItsDirectionAndGainsOnlyItsSize() {
        // Three constructions were tried for a positioned window and all three
        // fail on this wall — see `Scenery.lightingFor`. So daylight's shading
        // and every daylight shadow's *position* are what they were, and the
        // only thing that changes is how soft the edge is.
        assertNull(day.key.position, "the window was given a place")
        assertEquals(StageLighting.DeskDay.key.direction, day.key.direction)
        assertTrue(day.key.radius > 0f && day.key.distance > 0f, "the window has no size")
        assertTrue(
            day.key.angularRadius(null) > night.key.angularRadius(Vec3(mat.centerX, mat.centerY, 0f)),
            "the sky is not bigger than a lampshade",
        )
    }
}
