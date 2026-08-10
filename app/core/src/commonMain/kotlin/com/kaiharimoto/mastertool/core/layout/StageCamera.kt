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
    /**
     * The focal length, as a multiple of the one this stage has always used.
     *
     * ## What it is
     *
     * A **magnification**, and nothing else. It multiplies `zoom` and
     * `cameraDistance` by the same amount, which leaves the angle the table
     * subtends — `zoom · extent / cameraDistance` — exactly where it was. So the
     * board is drawn bigger or smaller and the perspective across it does not
     * move a pixel. That is what a longer lens does: it magnifies, it does not
     * restage.
     *
     * [distance] remains the other half and remains a *position*: stepping back
     * both flattens the perspective and makes the table smaller, which is what
     * walking backwards does. The two are now the pair a photographer expects —
     * where you stand decides the perspective, the lens decides how much of it
     * fills the frame.
     *
     * ## The version that shipped first, and why it was wrong
     *
     * v1.2.38 put the lens on `cameraDistance` alone. That pins the framing and
     * moves the perspective, which is a **dolly zoom** — a real and useful
     * control, and not a focal length. It also meant both dials changed the
     * perspective, so tuning one appeared to undo the other; moving the distance
     * from 1.45 to 2.0 took the field of view from 36 degrees to 26 with the
     * lens number sitting still. kai's word for it was that it reset.
     *
     * ## What it costs, which is less than the dolly zoom did
     *
     * `perspectiveGrowth` no longer moves with it — both terms scale, so the
     * ratio the board was solved against is untouched — which retires the
     * warning that a baked-in lens under 0.947 would turn `StagePlaneTest` red.
     * [CameraEnvelope.minDistanceAt] loses its lens factor for the same reason:
     * zooming cannot bring the table closer to a camera that has not moved.
     *
     * What it does cost is framing. Past `1.0` the board is magnified and can
     * run off the edges of the screen, exactly as a real zoom crops. Nothing
     * catches it, and that is the deal: `CameraFit` moves the *distance*, and
     * calling it here would re-couple the two dials that this exists to separate.
     *
     * ## Last, and defaulted to one, and that is load-bearing twice
     *
     * At `1f` every byte of every projection is what it was, so this landed in a
     * release ahead of anything that moves it and `GoldenStageTest` never
     * noticed — the same move `CardSolid.slab`'s trailing `backScale` made.
     *
     * And it is last because [CameraRig.step] and [CameraRig.nudge] used to
     * build a pose *positionally*. A field inserted before [distance] would have
     * been a silent re-binding rather than a compile error. Both use `copy` now:
     * without it the lens reset to one on every frame the camera sprang or was
     * dragged, so a tuned lens survived exactly until anybody touched the table.
     */
    val lens: Float = HOME_LENS,
) {
    companion object {
        /** The lens the stage has always used. See [StagePlane.forStage]. */
        const val HOME_DISTANCE = 1.45f

        /** The focal length everything in this app was tuned at. */
        const val HOME_LENS = 1f

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
    /**
     * And how far the focal length may travel, in multiples of the shipped one.
     *
     * Longer than 2 is an orthographic diagram, which is a fine thing to look at
     * and not a thing to sit at. The wide end is 0.6 — about twenty millimetres
     * on the dial's own scale — and it is a magnification rather than a field of
     * view, so nothing about it can bend a card at the far edge. What it costs
     * is that the board is drawn small; what stops that being a fisheye is that
     * the lens cancels out of the perspective entirely. See [CameraPose.lens].
     */
    val minLens: Float = 0.6f,
    val maxLens: Float = 2f,
    /**
     * How far toward the lens the nearest corner of the mat may travel.
     *
     * The one number [minDistanceAt] is solved against, and a property rather
     * than a constant because it is the answer to "how close may I sit", which
     * is a question of taste that somebody has to be able to ask on the device.
     * The keystone across the table goes as `1 / (1 − this)`: a half caps it at
     * two to one, and 0.68 at about three, which is a room you can lean into
     * rather than a diagram you are looking down at.
     *
     * **It must stay below one.** At one the corner *is* the lens:
     * [StagePlane.project] clamps rather than dividing by zero, and a clamp
     * cannot be inverted, so
     * `unproject` stops agreeing with `project` and every pile edge and airborne
     * shadow goes with it. That is not a stylistic limit, and it is why the knob
     * that offers this stops at 0.95.
     *
     * It shipped at a half for three releases. Raising it only ever *lowers* a
     * floor, so no pose that was legal has become illegal and no seat has moved.
     *
     * **And it does not go below what shipped**, which is a limit that had to be
     * measured rather than guessed. Tightening it pushes every floor *out*, and
     * at 0.42 on this stage the floor at thirty-four degrees passes 1.34 — which
     * is [StageSeat.SEATED], so the seat on the bar becomes a place the envelope
     * refuses to let you sit. The shipped half is already within a few
     * hundredths of that, so there is no room under it worth offering and one
     * seat to lose by offering it. Everything anybody wants from this knob is in
     * the other direction.
     */
    val clearance: Float = DEFAULT_CLEARANCE,
) {
    /**
     * The nearest pose inside the envelope. Yaw is free — a table turns all the way.
     *
     * The surface has to be passed in because the floor on [CameraPose.distance]
     * is not a constant: see [minDistanceAt].
     */
    fun clamp(pose: CameraPose, width: Float = 0f, height: Float = 0f): CameraPose {
        val pitch = pose.pitchDegrees.coerceIn(minPitch, maxPitch)
        val lens = pose.lens.coerceIn(minLens, max(minLens, maxLens))
        val floor = minDistanceAt(pitch, width, height)
        return pose.copy(
            pitchDegrees = pitch,
            distance = pose.distance.coerceIn(floor, max(floor, maxDistance)),
            lens = lens,
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
     * sit no more than [clearance] of the way to the lens and substituting
     * `zoom = HOME / distance` gives a floor in `distance²`, which is the square
     * root below. It says something sensible in plain terms too: you may come
     * close while you are looking down at the table, and you must step back as
     * you get low. That is also true of a real table.
     */
    /**
     * **The lens does not appear here, and that is a result rather than an
     * oversight.** The clearance constraint is
     * `halfDiagonal · zoom · sin(pitch) ≤ clearance · cameraDistance`, and a
     * focal length multiplies `zoom` and `cameraDistance` by the same amount —
     * so it cancels exactly. Zooming does not move the camera, so it cannot
     * bring the table any closer to it.
     *
     * An earlier draft did carry a lens factor, because the first version of
     * `planeFor` put the focal length on `cameraDistance` alone. That was a
     * dolly zoom wearing a lens's name, and the floor moving with it was one of
     * the several things it got wrong.
     */
    fun minDistanceAt(
        pitchDegrees: Float,
        width: Float,
        height: Float,
    ): Float {
        val governing = max(height, width * 0.55f)
        if (governing <= 0f) return minDistance

        val halfDiagonal = sqrt((width * width + height * height) / 4f)
        val reach = halfDiagonal * CameraPose.HOME_DISTANCE *
            sin(pitchDegrees.coerceIn(0f, 90f) * (PI.toFloat() / 180f))
        return max(minDistance, sqrt(reach / (safeClearance() * governing)))
    }

    /**
     * [clearance], held to its ends and made finite.
     *
     * Enforced here rather than trusted from the caller because this one reaches
     * the arithmetic through a stored preference, and the tuning round-trips
     * through JSON a person edits. A one in this field is a projection that
     * cannot be inverted; a `NaN` is worse, because `coerceIn` passes it
     * straight through — every comparison against it is false — and it then
     * poisons a floor, a pose, and every pile edge downstream of both, without
     * anything throwing. That last one is not hypothetical: it is what the test
     * for this found on the first run.
     */
    fun safeClearance(): Float =
        if (clearance.isFinite()) clearance.coerceIn(MIN_CLEARANCE, MAX_CLEARANCE) else DEFAULT_CLEARANCE

    companion object {
        /**
         * What [clearance] ships at, and the ends the panel and the arithmetic
         * both hold it to. See the field for why neither end is arbitrary.
         */
        const val DEFAULT_CLEARANCE = 0.68f
        const val MIN_CLEARANCE = 0.5f
        const val MAX_CLEARANCE = 0.95f
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
     * A hair of tolerance, and it is not slop: the felt is allowed to run under
     * the edge of the screen the way a real mat runs off the edge of a real
     * table. What must not happen is a zone or a pile leaving the glass.
     *
     * **Which is a statement about what you hand in as [bounds], not about this
     * number.** The play stage passes `layout.field` — the three rows of zones,
     * every pile among them — and deliberately not `layout.bounds`, which adds
     * the hand band along the bottom edge of the stage. The hand is the first
     * thing a push-in costs, and treating that as a reason to refuse capped the
     * camera at 1.47 when the envelope would have allowed 1.05. See
     * `MatInput.settle`.
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
            // [CameraPose.lens] is deliberately **not** compared. A seat is a
            // chair, not a chair and a lens: you do not change focal length by
            // sitting somewhere else, and a readout that refused to name the
            // table seat because the lens had been tuned would be reporting on
            // the wrong thing. [CameraRig.aimAt] carries the lens across for the
            // same reason.
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
    /**
     * Writable, because one of the limits is now a preference.
     *
     * [CameraEnvelope.clearance] is on the tuning panel, and a panel that could
     * not change it would be a slider that does nothing — which is the fault
     * this release exists to fix, not one to reintroduce. Written from the play
     * screen's tuning `SideEffect`, immediately before the pose it goes with,
     * and nowhere else: a rig whose envelope moves between a clamp and the
     * `CameraFit` that reads the same one would be answering two questions.
     *
     * A plain field on purpose, like the pose beside it. Nothing draws from it —
     * it is read when a pose is clamped and when a gesture settles.
     */
    var envelope: CameraEnvelope = CameraEnvelope(),
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

    /**
     * And to a named seat, **keeping whatever lens is on the camera**.
     *
     * A seat is where you sit. The lens is what you are looking through, and
     * nothing about moving to the other side of a table changes it. Taking the
     * seat's own lens instead would mean that tuning a focal length and then
     * tapping "Seated" silently threw the tuning away — which is exactly the
     * failure a person would read as the tool being broken.
     *
     * Every seat declares [CameraPose.HOME_LENS] today, so this is a no-op until
     * something moves one; `CameraLensTest.everySeatIsStillAtTheShippedLens` is
     * the tripwire for that day.
     */
    fun aimAt(seat: StageSeat) = aimAt(seat.pose.copy(lens = pose.lens))

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
        // `copy`, not a fresh pose: a gesture moves three of the four things a
        // camera is, and building a new one from three arguments quietly reset
        // the fourth on every frame of every drag.
        val next = envelope.clamp(
            pose.copy(
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
        // Three springs, four fields. The lens is not sprung — it is not
        // somewhere you travel to — so it has to be carried rather than rebuilt.
        pose = pose.copy(
            yawDegrees = yaw.value,
            pitchDegrees = pitch.value,
            distance = distance.value,
        )

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
    // The lens scales **both** terms, and that is what makes it a focal length
    // rather than a dolly zoom. Perspective across the table is the angle the
    // table subtends — `zoom · extent / cameraDistance` — so multiplying the two
    // together leaves that ratio alone and changes only how big the board is
    // drawn. Which is what a longer lens does: it magnifies, it does not
    // restage. Put it on `cameraDistance` alone and you get the opposite — the
    // framing pinned and the perspective moving, which is a dolly zoom, and
    // which is what shipped in v1.2.38 under the wrong name.
    cameraDistance = distance * max(lens, MIN_LENS) * max(height, width * 0.55f),
    yawDegrees = yawDegrees,
    zoom = max(lens, MIN_LENS) * CameraPose.HOME_DISTANCE / max(distance, 1e-3f),
)

/**
 * A lens of nothing is a division by nothing. Below anything the envelope allows.
 */
private const val MIN_LENS = 1e-3f
