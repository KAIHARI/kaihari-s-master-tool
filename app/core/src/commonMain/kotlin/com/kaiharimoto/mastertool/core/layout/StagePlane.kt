package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A point after projection: where it lands on screen, and how big it got. */
data class Projected(val x: Float, val y: Float, val scale: Float, val depth: Float)

/**
 * The tilted surface the play stage is drawn on, as arithmetic rather than a guess.
 *
 * One transform described twice. The parent `graphicsLayer` applies it to
 * everything resting on the mat, and [project] applies the identical maths to
 * anything that has left the mat and lives on the flat overlay above it. That
 * the two agree exactly at z = 0 is what lets a card lift off the table without
 * a seam at the moment it is picked up — and it is a property a test can hold
 * this to, rather than an intention someone had once.
 *
 * Compose's `rotationX` is a right-handed rotation about +X with +Y pointing
 * *down* and +Z toward the viewer, so a positive tilt brings the bottom edge
 * forward. Lift is along the plane's **normal** rather than straight at the
 * camera, because that is what taking a card off a table does — and it gives
 * the up-the-screen parallax for free instead of as a second effect.
 */
data class StagePlane(
    val width: Float,
    val height: Float,
    val tiltDegrees: Float = TILT,
    val cameraDistance: Float,
) {
    private val theta = tiltDegrees * (PI.toFloat() / 180f)
    private val sinTilt = sin(theta)
    private val cosTilt = cos(theta)

    val centreX: Float get() = width / 2f
    val centreY: Float get() = height / 2f

    /** Where a point on the plane lands, raised [z] along the plane's normal. */
    fun project(x: Float, y: Float, z: Float = 0f): Projected {
        val localX = x - centreX
        val localY = y - centreY
        val depth = localY * sinTilt + z * cosTilt   // toward the viewer
        val flat = localY * cosTilt - z * sinTilt    // down the screen
        val scale = cameraDistance / max(cameraDistance - depth, MIN_GAP)

        return Projected(
            x = centreX + localX * scale,
            y = centreY + flat * scale,
            scale = scale,
            depth = depth,
        )
    }

    fun project(point: Vec3): Projected = project(point.x, point.y, point.z)

    /**
     * A screen point back onto the mat, at z = 0.
     *
     * Closed form rather than iterated. Projection gives
     * `screenY = y·cosθ · d/(d − y·sinθ)`, which rearranges to
     * `y = screenY·d / (d·cosθ + screenY·sinθ)`.
     *
     * The freeform drag needs this because the card being dragged lives on the
     * flat overlay while the finger arrives in root coordinates — and it means
     * the screen never has to take Compose's word for the inverse of a layer
     * matrix it cannot inspect.
     */
    fun unproject(screenX: Float, screenY: Float): Vec3 {
        val localX = screenX - centreX
        val localY = screenY - centreY
        val onPlane = localY * cameraDistance / (cameraDistance * cosTilt + localY * sinTilt)
        val scale = cameraDistance / max(cameraDistance - onPlane * sinTilt, MIN_GAP)

        return Vec3(centreX + localX / scale, centreY + onPlane, 0f)
    }

    /** How much bigger something gets by rising [z] off the mat at the centre. */
    fun liftScale(z: Float): Float =
        cameraDistance / max(cameraDistance - z * cosTilt, MIN_GAP)

    /**
     * What the near edge grows to, which is what [BoardLayouter.solve] wants.
     *
     * The projection reporting on itself, so the fitter and the renderer cannot
     * disagree about how much room the tilt costs. Before this it was a
     * constant someone derived in a comment and would eventually have forgotten
     * to change alongside the tilt.
     */
    val perspectiveGrowth: Float get() = project(centreX, height).scale

    companion object {
        const val TILT = 11f
        private const val MIN_GAP = 1f

        /**
         * The lens, solved from the growth wanted at the near edge:
         * `s = d / (d − (h/2)·sinθ)`, which at eleven degrees and about six
         * per cent gives `d ≈ 1.7·h`.
         *
         * Taking the larger of height and a share of width keeps an ultrawide
         * window from getting a violent keystone at its left and right edges —
         * on a very wide stage the height is no longer the thing setting the
         * field of view.
         */
        fun forStage(width: Float, height: Float, tilt: Float = TILT) = StagePlane(
            width = width,
            height = height,
            tiltDegrees = tilt,
            cameraDistance = 1.7f * max(height, width * 0.55f),
        )
    }
}
