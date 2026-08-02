package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A point after projection: where it lands on screen, and how big it got. */
data class Projected(val x: Float, val y: Float, val scale: Float, val depth: Float)

/**
 * A point with a height, rewritten as a point on the mat that will look like it.
 *
 * [scale] is the correction that goes with it: the mat's own projection will
 * size whatever is drawn here by however much it sizes that part of the felt,
 * which is not the same as how much a thing standing off the felt should grow.
 * Multiply by this and it is.
 */
data class Flattened(val x: Float, val y: Float, val scale: Float)

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
     * Where to draw a point *on the mat* so that it appears to be [z] above it.
     *
     * The mat is one `graphicsLayer`, and everything drawn inside it gets the
     * plane's projection applied on the way out. That is exactly what you want
     * for the felt and for cards lying on it, and exactly what you cannot use
     * for anything with a height: a shadow thrown by a card in the air, the
     * white edge of a pile standing off the table, a card's own thickness. All
     * of those are geometry *at a z*, drawn by a canvas that has no z.
     *
     * So: project the point properly, then ask where on the flat mat that
     * screen position came from. The plane's own transform then undoes the
     * second step and the point lands where the first step put it. Both halves
     * are already here and exact, so this is not an approximation of the
     * projection — it is the projection, run through a canvas that only speaks
     * two dimensions.
     *
     * At z = 0 it is the identity, which is the property worth holding on to:
     * geometry that touches the mat is drawn exactly where it was computed.
     *
     * Position is not the whole answer, so [Flattened.scale] comes with it. The
     * plane will size anything drawn at the flattened point by however much it
     * sizes the *felt* there, and a card sitting on top of a forty-card deck is
     * closer to the camera than the felt under it by rather more than that. For
     * a polygon whose every vertex has been flattened the correction is already
     * baked in and can be ignored; for a whole card drawn at one point it is
     * the difference between a deck and a picture of one.
     */
    fun flatten(x: Float, y: Float, z: Float): Flattened {
        if (z == 0f) return Flattened(x, y, 1f)

        val raised = project(x, y, z)
        val onPlane = unproject(raised.x, raised.y)
        val asFelt = project(onPlane.x, onPlane.y, 0f).scale

        return Flattened(
            x = onPlane.x,
            y = onPlane.y,
            scale = if (asFelt <= MIN_GAP / cameraDistance) 1f else raised.scale / asFelt,
        )
    }

    fun flatten(point: Vec3): Flattened = flatten(point.x, point.y, point.z)

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
        /**
         * How far the table is laid back from the camera.
         *
         * Raised from eleven degrees, which was a tilt you could measure and
         * not one you could see. Everything with a height on this stage —
         * a pile's edge, a lifted card's parallax, the gap between a card and
         * its shadow — projects to `z·sin θ`, so at eleven degrees a forty-card
         * deck stood a pixel and a half proud of the felt and the whole table
         * read as a diagram of a table. Fifteen is half again as much depth for
         * about three per cent of card size, and it stops short of the angle
         * where a card's own text starts to keystone.
         */
        const val TILT = 15f
        private const val MIN_GAP = 1f

        /**
         * The lens, solved from the growth wanted at the near edge:
         * `s = d / (d − (h/2)·sinθ)`.
         *
         * Shortened along with the tilt. A camera nearly two screen-heights
         * back is an almost orthographic one, which is a fine choice for a
         * diagram and the wrong one for a table you are sitting at: it is what
         * made a card raised into the hand grow by four per cent and read as
         * having not moved. At `1.45·h` and fifteen degrees the near edge grows
         * about ten per cent, the fitter is told so, and a lifted card is
         * visibly closer than the felt it left.
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
            cameraDistance = 1.45f * max(height, width * 0.55f),
        )
    }
}
