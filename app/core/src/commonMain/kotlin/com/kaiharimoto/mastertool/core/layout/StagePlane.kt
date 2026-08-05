package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.PI
import kotlin.math.abs
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
 *
 * ## Turning the table
 *
 * [yawDegrees] spins the mat about its own normal — the table turning on the
 * spot — and it is applied **before** the tilt. That ordering is the whole
 * reason this file is still a page long instead of a matrix library.
 *
 * A camera orbiting about the *world's* vertical would compose the other way
 * round, and then [unproject] could no longer be solved in closed form: it
 * would need a ray cast at a plane, which is fine until you remember that
 * [flatten] is defined as projection composed with its own inverse, so an
 * approximate inverse tears every pile edge, card thickness and airborne shadow
 * at the same moment. Spin-then-tilt keeps the inverse exact, because spinning
 * is a rigid rotation you simply undo.
 *
 * And it costs nothing to draw. Compose builds its layer transform as
 * `Rx · Ry · Rz`, which turns a point about Z *first* and about X last — which
 * is precisely spin-then-tilt. So the mat's own `graphicsLayer` reproduces this
 * projection exactly with `rotationZ = -yaw`, `rotationX = tilt` and no third
 * angle at all. Compose's order was already the order we wanted; `Rot3` was
 * written to match it for the card renderer, and it pays a second time here.
 */
data class StagePlane(
    val width: Float,
    val height: Float,
    val tiltDegrees: Float = TILT,
    val cameraDistance: Float,
    /** How far the table has been turned on the spot, about its own normal. */
    val yawDegrees: Float = 0f,
    /**
     * How large the table is drawn, before any perspective.
     *
     * Separate from [cameraDistance], and the separation is not pedantry — it
     * is the thing that is easy to get wrong. In this projection (and in
     * Compose's, which it has to match) `cameraDistance` sets how *strongly*
     * perspective bites, and nothing else: a point at the centre of the plane
     * projects at scale one however far back the camera is. So pulling the
     * camera away does not make the table smaller, and a "dolly out until it
     * fits" built on `cameraDistance` alone converges on nothing at all.
     *
     * A real camera moving back does both at once, which is why [CameraPose]
     * ties them together — but they are two numbers, and the plane is the wrong
     * place to hide that.
     *
     * Applied to the mat's coordinates *before* the spin and the tilt, because
     * that is where Compose applies `scaleX`/`scaleY`: its layer transform is
     * `Rx · Ry · Rz · S`, so the point is scaled first and turned afterwards.
     */
    val zoom: Float = 1f,
) {
    private val theta = tiltDegrees * (PI.toFloat() / 180f)
    private val sinTilt = sin(theta)
    private val cosTilt = cos(theta)

    private val phi = yawDegrees * (PI.toFloat() / 180f)
    private val sinYaw = sin(phi)
    private val cosYaw = cos(phi)

    val centreX: Float get() = width / 2f
    val centreY: Float get() = height / 2f

    /**
     * The spin, and its undo. Kept as two private helpers rather than inlined so
     * that the only place the sign of the yaw is decided is here — the layer
     * that has to agree with it says `rotationZ = -yawDegrees` and nothing else
     * in the app needs an opinion.
     */
    private fun spinX(x: Float, y: Float) = x * cosYaw + y * sinYaw
    private fun spinY(x: Float, y: Float) = -x * sinYaw + y * cosYaw

    /** Where a point on the plane lands, raised [z] along the plane's normal. */
    fun project(x: Float, y: Float, z: Float = 0f): Projected {
        val atX = (x - centreX) * zoom
        val atY = (y - centreY) * zoom
        val up = z * zoom
        val localX = spinX(atX, atY)
        val localY = spinY(atX, atY)
        val depth = localY * sinTilt + up * cosTilt   // toward the viewer
        val flat = localY * cosTilt - up * sinTilt    // down the screen
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
     *
     * The yaw costs one extra step and no accuracy: the tilt is undone exactly
     * as it always was, in the spun frame, and then the spin itself is undone by
     * turning back the other way. A rigid rotation is the one kind of transform
     * whose inverse is free.
     */
    fun unproject(screenX: Float, screenY: Float): Vec3 {
        val localX = screenX - centreX
        val localY = screenY - centreY
        val onPlane = localY * cameraDistance / (cameraDistance * cosTilt + localY * sinTilt)
        val scale = cameraDistance / max(cameraDistance - onPlane * sinTilt, MIN_GAP)
        val spunX = localX / scale
        val safeZoom = if (abs(zoom) < 1e-4f) 1e-4f else zoom

        return Vec3(
            x = centreX + (spunX * cosYaw - onPlane * sinYaw) / safeZoom,
            y = centreY + (spunX * sinYaw + onPlane * cosYaw) / safeZoom,
            z = 0f,
        )
    }

    /** How much bigger something gets by rising [z] off the mat at the centre. */
    fun liftScale(z: Float): Float =
        cameraDistance / max(cameraDistance - z * zoom * cosTilt, MIN_GAP)

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
     * What the nearest corner grows to, which is what [BoardLayouter.solve] wants.
     *
     * The projection reporting on itself, so the fitter and the renderer cannot
     * disagree about how much room the tilt costs. Before this it was a
     * constant someone derived in a comment and would eventually have forgotten
     * to change alongside the tilt.
     *
     * Every corner rather than the bottom edge, because once the table can turn
     * there is no such thing as *the* near edge — at forty-five degrees of yaw
     * the nearest thing to the camera is a corner, and a growth measured down
     * the middle would under-report it and let the table overhang. At yaw zero
     * the two answers are identical, which is why this did not need saying
     * before and why changing it moved no existing number.
     */
    val perspectiveGrowth: Float
        get() {
            var worst = 1f
            for (x in 0..1) {
                for (y in 0..1) {
                    val scale = project(x * width, y * height).scale
                    if (scale > worst) worst = scale
                }
            }
            return worst
        }

    companion object {
        /**
         * How far the table is laid back from the camera.
         *
         * Raised twice now, from eleven to fifteen and from fifteen to
         * twenty-one, and both times for the same reason. Everything with a
         * height on this stage — a pile's edge, a lifted card's parallax, the
         * gap between a card and its shadow — projects to `z·sin θ`, so the
         * tilt is the exchange rate between every height the renderer computes
         * and the pixels anybody sees. At eleven degrees a forty-card deck
         * stood a pixel and a half proud of the felt; at fifteen it stood two,
         * which is a table you can measure and not one you can sit at.
         *
         * Twenty-one buys another forty per cent of that for about three per
         * cent of card size, and it is still a long way short of the angle where
         * a card's own printed text starts to keystone — the seated seat is at
         * thirty-four and is legible. It is deliberately the *default*, not the
         * limit: the camera can be anywhere between four and fifty-eight
         * degrees, and this is only the angle the table opens at.
         */
        const val TILT = 21f
        private const val MIN_GAP = 1f

        /**
         * The lens, solved from the growth wanted at the near edge:
         * `s = d / (d − (h/2)·sinθ)`.
         *
         * Shortened along with the tilt. A camera nearly two screen-heights
         * back is an almost orthographic one, which is a fine choice for a
         * diagram and the wrong one for a table you are sitting at: it is what
         * made a card raised into the hand grow by four per cent and read as
         * having not moved. At `1.45·h` and the default tilt the near edge
         * grows about fourteen per cent, the fitter is told so, and a lifted
         * card is visibly closer than the felt it left.
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
