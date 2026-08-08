package com.kaiharimoto.mastertool.core.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Claims about the projection's derivative.
 *
 * The first one is the whole test and the rest are its corners: an exact
 * derivative and a finite difference of the same function have to agree, and if
 * they do then every use downstream — the weave's band-limit, level of detail —
 * is standing on something true. A recording of four floats would not have
 * caught the sign error this found.
 */
class PlaneJacobianTest {

    /**
     * What `CameraPose.planeFor` hands a 1600x1000 stage at the table seat:
     * `distance * max(height, width * 0.55)`. A real number rather than a round
     * one, because the perspective term is the whole point of the exercise and
     * it vanishes as the camera recedes.
     */
    private val CAMERA = 1.45f * 1000f

    /** The three shipped seats plus the envelope's steepest legal angle. */
    private val poses = listOf(
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 5f, yawDegrees = 0f),
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 21f, yawDegrees = 0f),
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 34f, yawDegrees = 0f),
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 58f, yawDegrees = 0f),
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 34f, yawDegrees = 37f),
        StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 45f, yawDegrees = 150f, zoom = 1.3f),
    )

    private val samples = listOf(
        300f to 200f, 800f to 500f, 1300f to 820f, 420f to 760f, 1200f to 180f,
    )

    @Test
    fun theDerivativeAgreesWithADifferenceOfTheProjectionItself() {
        val h = 0.05f
        poses.forEach { plane ->
            samples.forEach { (x, y) ->
                val j = plane.jacobian(x, y)

                val px1 = plane.project(x + h, y)
                val px0 = plane.project(x - h, y)
                val py1 = plane.project(x, y + h)
                val py0 = plane.project(x, y - h)

                val fdxdx = (px1.x - px0.x) / (2 * h)
                val fdydx = (px1.y - px0.y) / (2 * h)
                val fdxdy = (py1.x - py0.x) / (2 * h)
                val fdydy = (py1.y - py0.y) / (2 * h)

                // Loose in absolute terms because the values run to several
                // units per unit; what matters is that they agree to well under
                // a per cent, which a wrong sign or a missing term never does.
                fun close(exact: Float, difference: Float, name: String) {
                    val tolerance = 0.004f * maxOf(abs(exact), 1f)
                    assertTrue(
                        abs(exact - difference) <= tolerance,
                        "$name at ($x,$y) tilt=${plane.tiltDegrees} yaw=${plane.yawDegrees}: " +
                            "exact $exact vs difference $difference",
                    )
                }
                close(j.dxdx, fdxdx, "dxdx")
                close(j.dxdy, fdxdy, "dxdy")
                close(j.dydx, fdydx, "dydx")
                close(j.dydy, fdydy, "dydy")
            }
        }
    }

    @Test
    fun lookingStraightDownWithNoSpinTheMapIsJustTheZoom() {
        // The degenerate case, and the one a reader can check by eye: no tilt,
        // no yaw, no perspective, so a mat unit is a screen unit times the zoom
        // and the two axes cannot differ.
        val plane = StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 0f, yawDegrees = 0f, zoom = 1.4f)
        val j = plane.jacobian(600f, 300f)
        assertTrue(abs(j.dxdx - 1.4f) < 1e-3f, "dxdx ${j.dxdx}")
        assertTrue(abs(j.dydy - 1.4f) < 1e-3f, "dydy ${j.dydy}")
        assertTrue(abs(j.dxdy) < 1e-3f && abs(j.dydx) < 1e-3f, "off-diagonal ${j.dxdy} ${j.dydx}")
        assertTrue(abs(j.areaScale - 1.96f) < 1e-2f, "areaScale ${j.areaScale}")
    }

    @Test
    fun theFarHalfOfATiltedTableIsSmallerOnScreenThanTheNearHalf() {
        // The claim the felt's band-limit rests on: the same square of cloth
        // covers fewer pixels at the back of the table than at the front, and by
        // more the lower you sit. If this were ever false the fade would be
        // fading the wrong end.
        val near = 1600f * 0.5f to 900f
        val far = 1600f * 0.5f to 120f
        var previous = 1f
        listOf(5f, 21f, 34f, 58f).forEach { tilt ->
            val plane = StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = tilt, yawDegrees = 0f)
            val ratio = plane.jacobian(near.first, near.second).areaScale /
                plane.jacobian(far.first, far.second).areaScale
            assertTrue(ratio > 1f, "tilt $tilt: near/far area ratio $ratio")
            assertTrue(ratio >= previous, "tilt $tilt did not compress more than the seat above it")
            previous = ratio
        }
    }

    @Test
    fun aTurnedTableSwapsWhichAxisIsCompressed() {
        // Quarter-turn the table and the axis that was running away from you is
        // the one running across. A weave band-limited on one axis only would be
        // correct at one yaw and wrong at the next, which is the reason both
        // axes are reported rather than a single scale.
        val square = StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 40f, yawDegrees = 0f)
        val turned = StagePlane(width = 1600f, height = 1000f, cameraDistance = CAMERA, tiltDegrees = 40f, yawDegrees = 90f)
        val a = square.jacobian(800f, 300f)
        val b = turned.jacobian(800f, 300f)
        assertTrue(a.alongY < a.alongX, "unturned: ${a.alongX} ${a.alongY}")
        assertTrue(b.alongX < b.alongY, "turned: ${b.alongX} ${b.alongY}")
    }
}
