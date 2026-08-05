package com.kaiharimoto.mastertool.core.layout

import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.SpringValue
import com.kaiharimoto.mastertool.core.motion.Springs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the camera is: two angles and how far back.
 *
 * Three numbers and deliberately not four. There is no pan — the camera always
 * looks at the middle of the table — because a movable target means the
 * vanishing point stops being the centre of the layer, and then the mat's
 * `graphicsLayer` needs a `transformOrigin` and [StagePlane.unproject] needs an
 * off-axis inverse. That is a great deal of arithmetic to buy something nobody
 * asked for: you orbit a table, you do not pan across one. A camera that leans
 * toward whatever is being played leans by a degree or two of [yawDegrees],
 * which reads the same and costs nothing.
 *
 * [distance] is a multiple of the stage's governing dimension rather than a
 * number of pixels, exactly as [StagePlane.forStage]'s lens is, so a pose means
 * the same thing on a phone and on a desk monitor.
 */
data class CameraPose(
    val yawDegrees: Float = 0f,
    val pitchDegrees: Float = StagePlane.TILT,
    val distance: Float = HOME_DISTANCE,
) {
    companion object {
        /** The lens the stage has always used. See [StagePlane.forStage]. */
        const val HOME_DISTANCE = 1.45f

        val Home = CameraPose()
    }
}

/**
 * Where the camera is allowed to be.
 *
 * Two of these three limits are about not making the table unusable and one is
 * about not making the *user* unusable:
 *
 * - **Pitch has a floor** because a table seen from exactly head-on is a
 *   rectangle with no depth at all, and every one of the shading and shadow
 *   cues that were built for it disappears at once.
 * - **Pitch has a ceiling** well short of the grazing angle, because past about
 *   sixty degrees a card's own printed text starts to keystone badly enough to
 *   be unreadable, and reading cards is what this whole application is for.
 * - **There is no roll, ever.** Not because it is hard — it is one more angle —
 *   but because a horizon that tips is the single most reliable way to make
 *   somebody feel ill, and a table has a horizon.
 */
data class CameraEnvelope(
    val minPitch: Float = 4f,
    val maxPitch: Float = 58f,
    val minDistance: Float = 0.8f,
    val maxDistance: Float = 2.6f,
) {
    /**
     * The nearest pose inside the envelope. Yaw is free — a table turns all the way.
     *
     * The surface has to be passed in because the floor on [CameraPose.distance]
     * is not a constant: see [minDistanceAt].
     */
    fun clamp(pose: CameraPose, width: Float = 0f, height: Float = 0f): CameraPose {
        val pitch = pose.pitchDegrees.coerceIn(minPitch, maxPitch)
        val floor = minDistanceAt(pitch, width, height)
        return pose.copy(
            pitchDegrees = pitch,
            distance = pose.distance.coerceIn(floor, max(floor, maxDistance)),
        )
    }

    /**
     * How close the camera may come at this pitch before the table reaches it.
     *
     * A single minimum distance is wrong, and wrong in a way that only shows up
     * at the extremes — which is where a test found it. Depth on this stage is
     * `y·sin(pitch)`, so the further round the table is laid back the further
     * the far corner travels toward the lens; at fifty-eight degrees and the old
     * flat floor of 0.95, the corner of a sixteen-by-ten mat crossed the camera
     * plane entirely. The projection survives that (it clamps rather than
     * dividing by zero) but a clamp cannot be inverted, so `unproject` silently
     * stopped agreeing with `project` — and with it `flatten`, and with that
     * every pile edge and airborne shadow, at one pose and not the others.
     *
     * So the floor is solved rather than chosen. Requiring the nearest corner to
     * sit no more than [CLEARANCE] of the way to the lens and substituting
     * `zoom = HOME / distance` gives a floor in `distance²`, which is the square
     * root below. It says something sensible in plain terms too: you may come
     * close while you are looking down at the table, and you must step back as
     * you get low. That is also true of a real table.
     */
    fun minDistanceAt(pitchDegrees: Float, width: Float, height: Float): Float {
        val governing = max(height, width * 0.55f)
        if (governing <= 0f) return minDistance

        val halfDiagonal = sqrt((width * width + height * height) / 4f)
        val reach = halfDiagonal * CameraPose.HOME_DISTANCE *
            sin(pitchDegrees.coerceIn(0f, 90f) * (PI.toFloat() / 180f))
        return max(minDistance, sqrt(reach / (CLEARANCE * governing)))
    }

    private companion object {
        /**
         * How far toward the lens the nearest corner of the mat may travel.
         *
         * A half, which caps the keystone at two-to-one across the table: strong
         * enough that the near edge is unmistakably nearer, short enough that a
         * card at the far edge is still a card rather than a sliver.
         */
        const val CLEARANCE = 0.5f
    }
}

/**
 * The three places you can sit.
 *
 * Named poses rather than free flight, and that is a stance rather than a
 * shortcut. A camera you can put anywhere is a camera you have to put
 * somewhere, every time, and the honest observation about free-fly cameras is
 * that they spend most of their life somewhere slightly wrong on the way back
 * to somewhere right. Orbit freely by all means — but there is always one key
 * that puts the table back where it reads best.
 */
enum class StageSeat(val label: String, val pose: CameraPose) {
    /**
     * Nearly overhead, nearly orthographic: the reading seat. Not *flat* — a
     * board with no depth at all loses the pile heights, which are how you count
     * a graveyard at a glance.
     */
    OVERHEAD("Overhead", CameraPose(yawDegrees = 0f, pitchDegrees = 5f, distance = 1.62f)),

    /** The stage as it has always been. The one everything else was tuned at. */
    TABLE("Table", CameraPose(yawDegrees = 0f, pitchDegrees = StagePlane.TILT, distance = 1.45f)),

    /**
     * Low and close, from the player's own chair. Cards keystone here and that
     * is the point: this is the seat for watching a card land, not for reading
     * the board.
     */
    SEATED("Seated", CameraPose(yawDegrees = 0f, pitchDegrees = 34f, distance = 1.34f)),
}

/**
 * Keeping the table on the screen.
 *
 * There are two ways to guarantee that, and picking the wrong one costs a fifth
 * of every card on the board.
 *
 * The obvious way is to hand [BoardLayouter] the worst growth the camera could
 * ever produce, so the layout is small enough to survive any angle. But the
 * worst case is the mat's *diagonal* facing the camera, and on a sixteen-by-ten
 * stage that is nearly twice the distance the bottom edge is — so every card
 * would be drawn about twenty per cent smaller, permanently, to buy an angle
 * that might never be used. On a tool whose entire purpose is reading cards
 * that is the wrong trade, and it also quietly breaks the house rule: the
 * layout is *solved*, and a layout solved against a hypothetical is negotiated.
 *
 * So it is inverted. The layout is solved once, for the seat the stage opens at,
 * and the **camera** is what gives: [fit] takes a pose the user asked for and
 * returns the nearest one that keeps the board's own corners on the glass,
 * dollying back as far as it must. Turning the table forty-five degrees steps
 * you back a little, which is also what it should look like.
 */
object CameraFit {

    /**
     * How far outside the surface a corner may stray before it counts as off.
     *
     * A hair of tolerance, and it is not slop: the projected corner of the board
     * is the corner of the *felt*, and the felt is allowed to run under the edge
     * of the screen the way a real mat runs off the edge of a real table. What
     * must not happen is a zone or a pile leaving the glass, and those sit
     * inside the bounds by a card's width already.
     */
    private const val OVERHANG = 0.02f

    /**
     * Iterations of the search for a distance that fits.
     *
     * Solved by bisection rather than algebra, and on purpose. The constraint is
     * eight inequalities — four corners against four screen edges — each of
     * which is a different rational function of the distance, and a closed form
     * for the binding one would be a page of case analysis that has to be redone
     * the first time the envelope changes. Bisection is obviously correct,
     * costs sixteen projections of four points, and runs when the user lets go
     * of a gesture rather than every frame.
     */
    private const val STEPS = 16

    /** Whether every corner of [bounds] lands on the glass under [plane]. */
    fun holds(plane: StagePlane, bounds: Slot): Boolean {
        val marginX = plane.width * OVERHANG
        val marginY = plane.height * OVERHANG
        for (x in 0..1) {
            for (y in 0..1) {
                val corner = plane.project(
                    if (x == 0) bounds.left else bounds.right,
                    if (y == 0) bounds.top else bounds.bottom,
                )
                if (corner.x < -marginX || corner.x > plane.width + marginX) return false
                if (corner.y < -marginY || corner.y > plane.height + marginY) return false
            }
        }
        return true
    }

    /**
     * The nearest pose to [wanted] that keeps [bounds] on screen.
     *
     * The angles are honoured exactly — if you asked to look at the table from
     * the side you get to look at it from the side — and only the distance
     * moves, because dollying back is the one correction that cannot make the
     * answer worse. Pulling the camera away shrinks the projected board
     * monotonically toward the shape the layout was solved for, which is the
     * property that makes a bisection valid at all and is worth stating: at
     * [CameraEnvelope.maxDistance] the projection is nearly orthographic and the
     * board is very nearly the rectangle the fitter drew.
     */
    fun fit(
        wanted: CameraPose,
        bounds: Slot,
        envelope: CameraEnvelope,
        surfaceWidth: Float,
        surfaceHeight: Float,
        plane: (CameraPose) -> StagePlane,
    ): CameraPose {
        val safe = envelope.clamp(wanted, surfaceWidth, surfaceHeight)
        if (holds(plane(safe), bounds)) return safe

        var near = safe.distance
        var far = envelope.maxDistance
        if (!holds(plane(safe.copy(distance = far)), bounds)) {
            // Even the back of the envelope cannot contain it. Give the user the
            // furthest seat rather than refusing: a board that overhangs slightly
            // is legible, and a camera that silently ignores a gesture is not.
            return safe.copy(distance = far)
        }

        repeat(STEPS) {
            val middle = (near + far) / 2f
            if (holds(plane(safe.copy(distance = middle)), bounds)) far = middle else near = middle
        }
        return safe.copy(distance = far)
    }
}

/**
 * Turning by the short way round.
 *
 * Yaw is an angle on a circle and a spring is not: told to go from 350° to 10°
 * a spring takes the 340° route, and the table spins most of the way round to
 * arrive somewhere it was almost already at. So targets are rewritten into the
 * representative nearest the value the camera currently holds, and yaw is
 * allowed to wander outside 0..360 as far as it likes — nothing reads it except
 * a sine and a cosine, and both are periodic.
 */
object Turns {

    /** The representative of [target] nearest to [current], in degrees. */
    fun nearest(current: Float, target: Float): Float {
        var delta = (target - current) % 360f
        if (delta > 180f) delta -= 360f
        // Half a turn is genuinely ambiguous — both ways are the same distance —
        // so it is settled here rather than left to the sign of a modulo, which
        // would send an identical gesture two different ways on two platforms.
        if (delta <= -180f) delta += 360f
        return current + delta
    }

    /** [degrees] folded to (-180, 180]. For display, never for springing. */
    fun signed(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * The seat this pose is closest to, or null if it is not close to any.
     *
     * The readout uses it, and so does the detent: coming to rest within a few
     * degrees of a named seat should *say* so, because a camera you cannot name
     * the position of is a camera you cannot get back to.
     */
    fun seatAt(pose: CameraPose, tolerance: Float = 2.5f): StageSeat? = StageSeat.entries
        .firstOrNull {
            abs(signed(pose.yawDegrees - it.pose.yawDegrees)) <= tolerance &&
                abs(pose.pitchDegrees - it.pose.pitchDegrees) <= tolerance &&
                abs(pose.distance - it.pose.distance) <= 0.04f
        }

    /** How far round the table you are, for a readout: 0, 45, 90 … */
    fun bearing(pose: CameraPose): Int {
        val folded = signed(pose.yawDegrees).roundToInt()
        return if (folded < 0) folded + 360 else folded
    }
}

/**
 * The camera in motion: a pose that springs toward another one.
 *
 * Held as plain fields rather than snapshot state, and stepped from the play
 * stage's one `withFrameNanos` loop, for the same reason `StageCard` is: this
 * changes sixty times a second and the only things that read it are a
 * `graphicsLayer` and a draw lambda. A camera in snapshot state recomposes the
 * board — sixty cards, every frame — and re-keys the pointer input, which
 * cancels whatever gesture is being made. Both of those are quiet failures that
 * read as physics bugs.
 *
 * The spring itself is the app's own, so a camera move has exactly the weight a
 * card move does. That is not decoration: two objects on one screen easing on
 * two different curves is the thing that makes a scene read as assembled rather
 * than as a place.
 */
class CameraRig(
    val envelope: CameraEnvelope = CameraEnvelope(),
    seat: StageSeat = StageSeat.TABLE,
) {
    /**
     * The surface the stage is being drawn on, refreshed by the screen rather
     * than captured, because this object outlives any one composition of it —
     * the same arrangement `MatPilot` uses for its layout, and for the same
     * reason. It is only read when a pose is clamped, never per frame.
     */
    var width: Float = 0f
    var height: Float = 0f

    var pose: CameraPose = seat.pose
        private set

    var target: CameraPose = seat.pose
        private set

    private var vYaw = 0f
    private var vPitch = 0f
    private var vDistance = 0f
    private var parked = true

    /** True while the camera is still travelling, so the loop can skip it when not. */
    val moving: Boolean get() = !parked

    /** Puts the camera somewhere with no travel — for opening the stage. */
    fun placeAt(pose: CameraPose) {
        val safe = envelope.clamp(pose, width, height)
        this.pose = safe
        target = safe
        vYaw = 0f
        vPitch = 0f
        vDistance = 0f
        parked = true
    }

    /** Sends the camera somewhere. Yaw takes the short way round. */
    fun aimAt(pose: CameraPose) {
        val safe = envelope.clamp(pose, width, height)
        val next = safe.copy(yawDegrees = Turns.nearest(this.pose.yawDegrees, safe.yawDegrees))
        if (next == target) return
        target = next
        parked = false
    }

    fun aimAt(seat: StageSeat) = aimAt(seat.pose)

    /**
     * Moves the camera *by* an amount, for a gesture in progress.
     *
     * Assigned rather than sprung, and that is the same rule a carried card
     * obeys: a spring between a finger and the thing it is moving is lag, and
     * lag on a direct manipulation is what makes it feel like a remote control
     * rather than a hand. The spring is for going *to* a seat, not for being
     * dragged.
     */
    fun nudge(deltaYaw: Float, deltaPitch: Float, dollyBy: Float = 0f) {
        val next = envelope.clamp(
            CameraPose(
                yawDegrees = pose.yawDegrees + deltaYaw,
                pitchDegrees = pose.pitchDegrees + deltaPitch,
                distance = pose.distance * (1f + dollyBy),
            ),
            width,
            height,
        )
        pose = next
        target = next
        vYaw = 0f
        vPitch = 0f
        vDistance = 0f
        parked = true
    }

    /** Springs one frame. Returns whether anything moved. */
    fun step(spec: SpringSpec, dt: Float): Boolean {
        if (parked) return false

        val yaw = Springs.step(
            SpringValue(pose.yawDegrees, vYaw),
            target.yawDegrees,
            spec,
            dt,
        )
        val pitch = Springs.step(
            SpringValue(pose.pitchDegrees, vPitch),
            target.pitchDegrees,
            spec,
            dt,
        )
        val distance = Springs.step(
            SpringValue(pose.distance, vDistance),
            target.distance,
            spec,
            dt,
        )

        vYaw = yaw.velocity
        vPitch = pitch.velocity
        vDistance = distance.velocity
        pose = CameraPose(yaw.value, pitch.value, distance.value)

        val settled = Springs.settled(
            SpringValue(yaw.value, yaw.velocity),
            target.yawDegrees,
            ANGLE_TOLERANCE,
            ANGLE_TOLERANCE,
        ) &&
            Springs.settled(
                SpringValue(pitch.value, pitch.velocity),
                target.pitchDegrees,
                ANGLE_TOLERANCE,
                ANGLE_TOLERANCE,
            ) &&
            Springs.settled(
                SpringValue(distance.value, distance.velocity),
                target.distance,
                DISTANCE_TOLERANCE,
                DISTANCE_TOLERANCE,
            )

        if (settled) {
            pose = target
            vYaw = 0f
            vPitch = 0f
            vDistance = 0f
            parked = true
        }
        return true
    }

    private companion object {
        /**
         * A twentieth of a degree, and a thousandth of a stage height.
         *
         * Tighter than a card's, because a card that stops a pixel early stops a
         * pixel early and a camera that stops a pixel early moves *everything* a
         * pixel. The whole board is downstream of these two numbers.
         */
        const val ANGLE_TOLERANCE = 0.05f
        const val DISTANCE_TOLERANCE = 0.001f
    }
}

/**
 * The plane this pose describes, for a surface of [width] by [height].
 *
 * The one place [CameraPose.distance] is turned into the two numbers the
 * projection needs, and it ties them together the way a real camera does:
 * stepping back both weakens the perspective and makes the subject smaller.
 * `cameraDistance` alone does only the first, which is the trap — a camera
 * built on it can never make the table fit, because at the centre of the plane
 * every distance projects at scale one.
 *
 * Zoom is the ratio to the seat the stage opens at, so the home pose is
 * exactly `1` and every constant tuned against it still means what it meant.
 */
fun CameraPose.planeFor(width: Float, height: Float) = StagePlane(
    width = width,
    height = height,
    tiltDegrees = pitchDegrees,
    cameraDistance = distance * max(height, width * 0.55f),
    yawDegrees = yawDegrees,
    zoom = CameraPose.HOME_DISTANCE / max(distance, 1e-3f),
)
