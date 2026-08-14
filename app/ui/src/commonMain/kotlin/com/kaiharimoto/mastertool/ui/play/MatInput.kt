package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropCommit
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.cameFrom
import com.kaiharimoto.mastertool.core.board.fanCardAt
import com.kaiharimoto.mastertool.core.board.fanSource
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.tune.StageTuning
import com.kaiharimoto.mastertool.core.haptics.Haptic
import com.kaiharimoto.mastertool.core.haptics.HapticScore
import com.kaiharimoto.mastertool.core.board.toMat
import com.kaiharimoto.mastertool.core.board.toPixels
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.FanSpread
import com.kaiharimoto.mastertool.core.layout.MatControl
import com.kaiharimoto.mastertool.core.layout.MatControls
import com.kaiharimoto.mastertool.core.layout.PileFan
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.mat.MatEvent
import com.kaiharimoto.mastertool.core.mat.MatGestureMachine
import com.kaiharimoto.mastertool.core.mat.Touch
import com.kaiharimoto.mastertool.core.mat.TouchFrame
import com.kaiharimoto.mastertool.core.mat.TwoFinger
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.ui.fx.Feedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import kotlin.math.abs
import kotlin.math.max

/**
 * The only part of the gesture system that knows what Compose is.
 *
 * It does three things and nothing else: turn a `PointerEvent` into a
 * [TouchFrame] in the mat's own coordinates, hand that to the arbiter, and
 * carry out whatever the arbiter says happened. Every rule about what a gesture
 * *means* lives in `MatGestureMachine`, in core, where it is tested — because
 * disambiguating four gestures that all start identically is exactly the part
 * that is otherwise only verifiable by hand on a tablet.
 *
 * One `pointerInput`, over the whole stage. Per-card detectors are what let one
 * finger start a drag on one card while a second starts a separate drag on
 * another, and no amount of consumption fixes that after the fact.
 *
 * Coordinates are unprojected here rather than relying on Compose to invert the
 * tilted layer's matrix, so twist angles are angles on the felt rather than
 * angles on the glass, and the arithmetic is the same `StagePlane` the renderer
 * uses.
 */
/**
 * What both of the mat's clocks report to.
 *
 * The arbiter is driven from two places — pointer events, and the frame loop
 * that gives a motionless finger a way to become a long press — and both of
 * them produce events that have to be carried out against *the same* memory of
 * what the press landed on. Left inside the `pointerInput` closure, that memory
 * is unreachable from the frame loop, and every gesture the clock decides
 * (peek, the two-finger menu, a hand left resting on the mat) is computed and
 * then dropped on the floor.
 *
 * [layout] and [onMenu] are refreshed by the composable rather than captured,
 * because this outlives any one composition of it.
 */
@Stable
internal class MatPilot(
    private val machine: MatGestureMachine,
    private val play: PlayState,
    private val feedback: Feedback,
    var layout: BoardLayout,
    /**
     * The live tuning, refreshed by the screen rather than captured — exactly
     * the arrangement [layout] uses, and for a sharper version of the same
     * reason. The gesture loop is `pointerInput(layout)` and must stay that way:
     * putting the tuning in that key would tear down the single arbiter's event
     * stream on every frame a slider moved, while the machine went on believing
     * the gesture was live. Captured instead of read, it would go stale and the
     * hit boxes would drift away from the cards they belong to.
     */
    var tune: StageTuning = StageTuning.DEFAULT,
) {
    var onMenu: (DragOrigin) -> Unit = {}

    /**
     * The camera, and how the hand moved on the *glass* since the last frame.
     *
     * The felt is the camera's surface. A press that lands on nothing claims the
     * gesture for the camera outright — [MatGestureMachine.claimForCamera] —
     * and from there one finger orbits and two pan and pinch. That claim is what
     * makes the whole control scheme sayable in one line, which is the test a
     * gesture vocabulary has to pass: *fingers on a card move the card, fingers
     * on the felt move the camera.*
     *
     * Both numbers are in screen pixels rather than the mat's, and that is not
     * fussiness: the mat's own coordinates are being turned by the very camera
     * the gesture is turning, so driving yaw from them is a feedback loop that
     * curves under your finger. What you want is the glass.
     *
     * [screenDelta] is the mean movement of the pointers present in *both*
     * frames, the way `TwoFinger.pan` computes it and for the same reason: a
     * centroid jumps by half the finger separation on the frame a second finger
     * lands, and a camera that lurches when you go to pinch it is a camera
     * nobody trusts. [spanRatio] is 1 whenever there is nothing to compare.
     */
    var camera: StageCameraState? = null
    var screenDelta: Vec2 = Vec2.Zero
    var spanRatio: Float = 1f

    /**
     * Whether the felt is allowed to claim a gesture at all.
     *
     * Refreshed by the screen rather than captured, exactly as [layout] and
     * [tune] are, and for the same reason: this outlives any one composition.
     *
     * Switched off, the claim below simply is not made, and that is the whole
     * implementation. A press on felt then stays in `MatPhase.PRESS` and dies
     * where it stands: `LiftedCard` and `Moved` with nothing grabbed are already
     * no-ops, and the one thing a felt press still does — a tap beside an open
     * fan squaring the pile back up — is handled off `Tapped`, which a press
     * claimed for the camera could never reach. So locking the camera does not
     * cost the table a single other gesture; it gives one back.
     */
    var cameraTouch: Boolean = false

    /** What the press landed on. The one thing that has to survive both clocks. */
    private var grabbed: DragOrigin? = null

    /**
     * The control the press landed on, if it landed on one rather than on a card.
     *
     * A second thing to remember across the two clocks, and it is deliberately
     * *not* folded into [grabbed]: `DragOrigin` names a card in the domain, and
     * a shuffle mark is not a card. Widening a domain type so the input layer
     * can remember something is how a domain stops meaning anything.
     */
    private var pressedControl: MatControl? = null

    /**
     * How far the hand has swept while the camera has had the gesture.
     *
     * A press on bare felt is claimed for the camera immediately and can never
     * become a tap again — that is what makes the control scheme sayable in one
     * line, and it is not worth giving up. But it also means the most obvious way
     * out of an open search, *touching the table beside it*, produced nothing at
     * all: the fan sat there while the camera turned by a pixel.
     *
     * So the camera answers the question afterwards. A gesture that turned the
     * table is a camera move; a gesture that did not is a tap that happened to
     * land on felt, and with a pile spread out that means put it back.
     */
    private var cameraTravel = 0f

    /** Whether the carried card is going *under* what it is over, not on top. */
    private var attaching = false

    /** True while the secondary mouse button is down, so the menu opens once. */
    private var secondaryDown = false

    /** And the same edge for the pointer's pan, which is a drag rather than a click. */
    private var panningDown = false

    /**
     * When the frame being carried out happened, and how fast the orbit is going.
     *
     * The arbiter's `CameraMoved` carries a finger count and no distances — by
     * design, because the mat's own coordinates are being turned by the very
     * camera the gesture is turning — so it carries no *time* either. The flick
     * needs one: `CameraRig.coast` takes degrees per second, because a rate per
     * frame runs a flick down twice as fast on a 120Hz tablet as on a 60Hz
     * desktop, and that is the bug this shape of arithmetic always eventually is.
     *
     * So the host supplies it, from the same `TouchFrame` the arbiter was handed.
     *
     * The rates are a running average rather than the last frame's delta. One
     * frame of a real hand is noisy enough that the difference between a firm
     * throw and a twitch at the moment of lifting is mostly luck, and the failure
     * that produces — a table that occasionally spins when you meant to stop it —
     * is the exact thing that makes people say inertia fights them. Blending also
     * gives the other half for free: a finger that comes to rest before it lifts
     * blends its rate down to nothing, so *stopping* and then letting go does not
     * throw the table.
     */
    private var frameMillis = 0L
    private var lastFlyMillis = 0L
    private var yawRate = 0f
    private var pitchRate = 0f

    fun frame(frame: TouchFrame) {
        frameMillis = frame.timeMillis
        carryOut(machine.onFrame(frame))
    }

    fun tick(timeMillis: Long) {
        frameMillis = timeMillis
        carryOut(machine.onTick(timeMillis))
    }

    /**
     * The mouse's way of coming closer to the table.
     *
     * The pointer half of the pinch, and the last unspoken-for channel a mouse
     * has. It goes straight to the camera rather than through the arbiter, on
     * the same grounds the secondary button does: the arbiter exists to
     * disambiguate contacts that all begin identically, and a wheel notch is not
     * ambiguous about anything. Nothing about it can start, join or end a
     * gesture, so there is nothing for the arbiter to arbitrate.
     *
     * [notches] is positive scrolling away from you, which is out.
     */
    fun wheel(notches: Float) {
        val state = camera ?: return
        if (notches == 0f) return
        state.rig.nudge(deltaYaw = 0f, deltaPitch = 0f, dollyBy = notches * DOLLY_PER_NOTCH)
        state.refresh()
    }

    /**
     * The mouse's way of aiming somewhere else — the pointer half of the two-finger pan.
     *
     * Taken before the arbiter, exactly as the wheel and the secondary button
     * are, and for the arbiter's own reason: it exists to disambiguate contacts
     * that all begin identically, and a middle button is not ambiguous about
     * anything. [MatGestureMachine.cancel] on the rising edge, because on a
     * platform where the middle button also reports a pointer down the arbiter
     * will already have started asking what that press meant.
     *
     * Alt and the primary button do the same thing, and the redundancy is
     * deliberate rather than untidy: most laptop trackpads have no middle button
     * at all, and "you cannot aim the camera unless you own a three-button mouse"
     * is not a control scheme. Shift was the obvious alternative and is spoken
     * for — it takes the whole pile rather than the top card.
     */
    fun panning(down: Boolean, delta: Vec2) {
        if (down && !panningDown) {
            machine.cancel()
            camera?.rig?.halt()
        }
        panningDown = down
        if (down) aimBy(delta)
    }

    /**
     * The mouse's way of asking for the card menu.
     *
     * On touch that is the two-finger hold. A mouse has no second finger, and
     * holding the one button still is already spoken for by the peek, so the
     * secondary button is the whole of the pointer idiom — without it the menu
     * is simply unreachable on a desktop.
     */
    fun secondary(down: Boolean, at: Vec2) {
        if (down == secondaryDown) return
        secondaryDown = down
        if (!down) return

        machine.cancel()
        whatIsUnder(
            play.field, layout, at, play.fanned, tune.hand.stepFraction,
            camera?.plane, fanLift,
        )?.let(onMenu)
    }

    private fun carryOut(events: List<MatEvent>) {
        events.forEach { event ->
            when (event) {
                is MatEvent.Pressed, is MatEvent.Dropped -> attaching = false
                is MatEvent.Dwelled -> attaching = true
                // Only movement worth the name undoes it. A card held still is
                // still being held still through the jitter of a real finger.
                is MatEvent.Moved -> if (event.delta.length > ROUSE) attaching = false
                else -> Unit
            }

            grabbed = handle(
                event, grabbed, play, layout, feedback, onMenu, attaching,
                tune.hand.stepFraction, camera?.plane, fanLift,
            )

            // The press has been hit-tested by now — `handle` is what does it —
            // so this is the first moment anything knows whether the finger
            // landed on a card. Nothing under it means the felt, and the felt
            // is the camera's.
            //
            // Two things are neither a card nor the felt, and both have to be
            // taken out before that claim is made, because a claimed gesture can
            // never become a tap again: a shuffle mark, and the space *inside* an
            // open fan. Miss either and pressing there starts orbiting the table
            // instead.
            //
            // There was a third — the puzzle standing on the desk — and it is
            // gone with the prop. What it left behind is the shape: anything in
            // the room that ever answers a finger is asked **after** the mat's
            // own affordances, because everything the table itself offers wins
            // over an ornament beside it.
            if (event is MatEvent.Pressed) {
                cameraTravel = 0f
                // Catch whatever the table is still doing. A hand reaching for a
                // coasting table has to win — a flick you cannot stop is the
                // thing that makes people describe inertia as fighting them —
                // and it is right for *any* press, not only one that lands on
                // felt: reaching for a card on a moving table is worse than
                // reaching for the table.
                camera?.rig?.halt()
                yawRate = 0f
                pitchRate = 0f
                lastFlyMillis = frameMillis
                pressedControl =
                    if (grabbed == null) MatControls.at(layout, event.at.x, event.at.y) else null
                if (cameraTouch && grabbed == null && pressedControl == null && !onFan(event.at)) {
                    machine.claimForCamera()
                }
            }

            // A tap on a mark shuffles the pile it is under. A tap on the fan's
            // own backdrop — inside it, but between the cards — squares the pile
            // back up, which is the way out of a search you have changed your
            // mind about.
            if (event is MatEvent.Tapped) {
                val control = pressedControl
                when {
                    control != null ->
                        if (play.shuffle(control.pile)) {
                            feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
                        }
                    grabbed == null && play.fanned != null ->
                        if (play.closeFan()) {
                            feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
                        } else {
                            feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                        }
                }
                pressedControl = null
            }
            if (event is MatEvent.CameraMoved) {
                cameraTravel += screenDelta.length
                fly(event.fingers)
            }
            if (event is MatEvent.CameraEnded) {
                // Touched the felt beside an open fan and did not turn anything:
                // that is a change of mind, and the pile goes back the way it was.
                if (cameraTravel < STILL && play.fanned != null) {
                    if (play.closeFan()) {
                        feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
                    } else {
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    }
                }
                release()
            }
        }
    }

    /**
     * A hand sweeping the felt moves the camera round the table.
     *
     * Across for yaw and up-down for pitch, which is the idiom every
     * three-dimensional viewer has used for thirty years and therefore the one
     * nobody has to be taught; a second finger adds the pinch, which is the
     * idiom every *touch* viewer has used since there were two of them. The
     * rates are per *screen height* rather than per pixel so the same sweep
     * turns the table by the same amount on a phone and on a desk monitor — the
     * same reason `CardDynamics` scales its bank by the card rather than by
     * pixels.
     *
     * The pinch is inverted on the way in and that is the whole of it being
     * right: fingers spreading is a request to be *closer*, and closer is a
     * smaller `distance`. Expressed as a ratio rather than a number of pixels
     * because a pinch is a scale — the same spread means the same zoom whether
     * it started with the fingers an inch apart or a hand's width.
     */
    /**
     * Whether a point is inside an open fan, cards or the gaps between them.
     *
     * The fan is a *mode*, and its footprint is the extent of it: a press there
     * is about the spread even when it lands on felt showing through, or the
     * table would start turning under a finger reaching for a card. Outside it,
     * the felt is the camera's exactly as it always was — so the board can still
     * be looked at from another angle while a pile is open, which is the whole
     * reason a search is not a sheet.
     */
    private fun onFan(at: Vec2): Boolean {
        val slot = play.fanned ?: return false
        val onFan = fanPointFor(at, camera?.plane, fanLift)
        return fanOf(play.field, layout, slot).bounds.contains(onFan.x, onFan.y)
    }

    /**
     * How far a spread pile floats, in mat pixels, at the tuning in force.
     *
     * Read from the same two knobs `seatsFor` multiplies together, and by the
     * same card height, so a person moving "Spread height" on the tuning panel
     * moves the cards and the hit boxes as one thing. A copy of that arithmetic
     * would be a fan you can see and cannot touch, one slider later.
     */
    private val fanLift: Float
        get() = layout.cardHeight * tune.cards.carryLift * tune.cards.fanLiftRatio

    /**
     * One finger orbits. Two pan, and also pinch.
     *
     * The control scheme still fits on one line — *fingers on a card move the
     * card, fingers on the felt move the camera* — and the second half of it now
     * reads "one orbits, two pan and pinch", which is the idiom every
     * three-dimensional viewer has used for as long as trackpads have had two
     * fingers on them. It needs no teaching, which is the bar a gesture on this
     * table has to clear.
     *
     * Two fingers used to orbit *as well*, which meant the pan of the hand did
     * two jobs at once and there was no way to aim the camera at anything but the
     * middle of the mat. Splitting them costs nothing: the arbiter already
     * reports how many fingers are down, and both signals — the centroid's travel
     * and the pinch — were already being measured every frame.
     */
    private fun fly(fingers: Int) {
        val state = camera ?: return
        val across = state.rig.width
        val down = state.rig.height
        if (across <= 0f || down <= 0f) return

        if (fingers >= 2) {
            // The pinch first, so the pan is measured against the plane the
            // dolly leaves behind rather than the one it arrived on. Half a
            // frame's worth of difference, and free.
            val dolly = if (spanRatio > 0.01f) 1f / spanRatio - 1f else 0f
            if (dolly != 0f) {
                state.rig.nudge(deltaYaw = 0f, deltaPitch = 0f, dollyBy = dolly)
                state.refresh()
            }
            aimBy(screenDelta)
            return
        }

        val deltaYaw = screenDelta.x / across * YAW_PER_SWEEP
        val deltaPitch = -screenDelta.y / down * PITCH_PER_SWEEP
        sample(deltaYaw, deltaPitch)
        state.rig.nudge(deltaYaw = deltaYaw, deltaPitch = deltaPitch)
        state.refresh()
    }

    /**
     * Aim the camera so that the felt under the hand stays under the hand.
     *
     * Solved through the projection's own inverse rather than by scaling the
     * screen delta by something. A pan is a promise — the table follows your
     * fingers — and the exchange rate between a glass pixel and a mat pixel is
     * not one number: the plane is tilted, so a pixel across and a pixel away
     * cover different distances, and the ratio changes with depth because of the
     * perspective divide. Any single factor is right at one row of the screen and
     * wrong everywhere else, and wrong in the way that reads as the table
     * sliding out from under you.
     *
     * `unproject` at the middle of the glass *is* the camera's target, exactly —
     * that is what a target means — so the second call is the honest way to say
     * "and where it was", and it costs four multiplications.
     */
    private fun aimBy(delta: Vec2) {
        val state = camera ?: return
        if (delta == Vec2.Zero) return
        val plane = state.plane
        val governing = max(state.rig.height, state.rig.width * 0.55f)
        if (governing <= 0f) return

        val to = plane.unproject(plane.centreX + delta.x, plane.centreY + delta.y)
        val from = plane.unproject(plane.centreX, plane.centreY)
        state.rig.nudge(
            deltaYaw = 0f,
            deltaPitch = 0f,
            // Backwards, and that is the whole of it being right: dragging the
            // table to the right means aiming the camera to the left of what it
            // was aimed at.
            deltaPanX = -(to.x - from.x) / governing,
            deltaPanY = -(to.y - from.y) / governing,
        )
        state.refresh()
    }

    /** One frame of the orbit, folded into the running rate the flick is thrown at. */
    private fun sample(deltaYaw: Float, deltaPitch: Float) {
        val since = (frameMillis - lastFlyMillis) / 1000f
        lastFlyMillis = frameMillis
        // A frame that took no time divides by nothing, and one that took a
        // tenth of a second is a gesture that paused: neither is a rate, and
        // dropping both leaves the average holding what it already had — which
        // for the pause is exactly right, because the next real frame will blend
        // it down.
        if (since < 0.001f || since > 0.1f) return
        yawRate += (deltaYaw / since - yawRate) * FLICK_BLEND
        pitchRate += (deltaPitch / since - pitchRate) * FLICK_BLEND
    }

    /**
     * Let go, and a turn that was going somewhere keeps going.
     *
     * ## What used to be here, and why it is not
     *
     * `CameraFit`, run on every release, dollying the camera back until
     * `layout.field` was on the glass. It was written for a real problem — a
     * turned table can walk its own corners off the screen — and it was the whole
     * of kai's *"it locks me out from getting closer past the close limit"*: you
     * pinch in, let go, and the table slides away from you. Nothing about that
     * reads as a safety rail. It reads as the tool refusing.
     *
     * The refusal was also inconsistent, which is the tell. The mouse wheel never
     * went through the arbiter, so it never called this, so a desktop user could
     * already dolly past where a finger was allowed to stop.
     *
     * `CameraFit` is not gone. It is on the **seat buttons** now — the one place
     * a correction is what was actually asked for, because pressing "Table" means
     * *put the board back where I can see it*, and it is the way home from
     * anywhere free flight can reach. `PlayScreen` does that; the felt no longer
     * argues with the hand that just let go of it.
     *
     * ## And a flick keeps its momentum
     *
     * `docs/AAA.md` #7, and the rest of what a release means now. The rate has
     * been accumulating all through the gesture (see [sample]); below
     * `CameraRig.COAST_FLOOR` this does nothing at all, which is what keeps a
     * slow drag that merely ended from drifting on afterwards.
     */
    private fun release() {
        val state = camera ?: return
        state.rig.coast(yawRate, pitchRate)
        yawRate = 0f
        pitchRate = 0f
    }

    private companion object {
        /** Mat pixels of travel in one frame that count as having moved on. */
        const val ROUSE = 2.5f

        /**
         * Degrees for a finger dragged the whole way across, and the whole way
         * down.
         *
         * Both were far too fast — two hundred and twenty degrees across meant a
         * casual four-hundred-pixel drag spun the table fifty-five degrees, and
         * ninety down crossed the envelope's entire useful range in two thirds
         * of a screen. A control you cannot stop in the middle of does not read
         * as imprecise, it reads as *broken*, because every attempt to make a
         * small correction overshoots and you conclude the thing is not
         * listening to you.
         *
         * Yaw is still the more generous of the two, because a table is worth
         * walking round and its range is unbounded, where pitch has fifty-four
         * degrees to spend in total.
         */
        const val YAW_PER_SWEEP = 110f
        const val PITCH_PER_SWEEP = 50f

        /**
         * How much of the distance to the table one wheel notch is worth.
         *
         * Eight per cent, which crosses the envelope's whole range in about
         * fifteen notches. A wheel is the one camera control with no way to say
         * "a bit less" halfway through, so it is set where a single notch is
         * clearly a step and a flick of the finger is not a teleport.
         */
        const val DOLLY_PER_NOTCH = 0.08f

        /**
         * How much of one frame goes into the running rate a flick is thrown at.
         *
         * A third, which is a time constant of about three frames at sixty a
         * second — long enough that one noisy sample cannot decide where the
         * table goes, short enough that the rate is about the *end* of the drag
         * rather than an average of the whole of it. Somebody who sweeps across
         * the table and then slows to a stop before lifting has said they want it
         * to stop, and at a third that is what they get after five frames.
         */
        const val FLICK_BLEND = 1f / 3f
    }
}

@Composable
internal fun MatInput(
    pilot: MatPilot,
    machine: MatGestureMachine,
    layout: BoardLayout,
    camera: StageCameraState,
) {
    Box(
        Modifier
            .fillMaxSize()
            // Keyed on the layout alone. A camera in this key list would restart
            // the whole loop on every frame it moved, tearing down the single
            // arbiter's event stream mid-gesture while the machine went on
            // believing the gesture was live — which would read as the orbit
            // dying after one frame rather than as a keying mistake.
            .pointerInput(layout) {
                awaitPointerEventScope {
                    // Where every pressed pointer was last frame, on the glass.
                    // A map rather than one position because the camera's two
                    // measurements — the hand's travel and the pinch — are both
                    // about pointers that were there *before*, and a pointer
                    // that has only just landed must contribute to neither.
                    var lastOnGlass: Map<Long, Vec2> = emptyMap()
                    while (true) {
                        // Main, not Initial: this is the deepest interactive node
                        // on the stage, and consuming here is what stops an
                        // ancestor pager or drawer from stealing a drag.
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        // The live camera, read now rather than captured: this
                        // loop outlives any pose the stage happens to be in.
                        val plane = camera.plane

                        // The wheel, before anything else looks at the event. It
                        // carries no contact and starts no gesture, so handing
                        // it to the arbiter would only give the arbiter a frame
                        // in which nothing it understands happened.
                        if (event.type == PointerEventType.Scroll) {
                            pilot.wheel(event.changes.firstOrNull()?.scrollDelta?.y ?: 0f)
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        // Shift takes the whole pile rather than the top card:
                        // the pointer idiom for the two-finger drag, and the one
                        // the arbiter has always been able to act on and never
                        // once been told about.
                        machine.stackModifier = event.keyboardModifiers.isShiftPressed

                        fun onFelt(change: PointerInputChange): Touch {
                            // Held below the horizon on the way in. Past about
                            // seventy degrees of pitch the table's horizon is on
                            // the glass, and a finger above it is pointing at no
                            // table at all — `StagePlane.belowHorizon` has the
                            // argument for clamping rather than refusing.
                            val y = plane.belowHorizon(change.position.y)
                            val onPlane = plane.unproject(change.position.x, y)
                            return Touch(change.id.value, Vec2(onPlane.x, onPlane.y))
                        }

                        // How the hand moved on the glass, which is the frame
                        // the camera wants and the mat is not. Both numbers are
                        // computed for every event and read only by a camera
                        // gesture; measuring them unconditionally is a handful
                        // of subtractions and means there is no state to get
                        // into step with when one begins.
                        val onGlass = event.changes
                            .filter { it.pressed }
                            .associate { it.id.value to Vec2(it.position.x, it.position.y) }

                        pilot.screenDelta = TwoFinger.pan(lastOnGlass, onGlass)
                        pilot.spanRatio = spanRatio(lastOnGlass, onGlass)
                        lastOnGlass = onGlass

                        // The mouse's way of aiming somewhere else, before the
                        // gesture machine sees anything: the middle button, or
                        // alt and the primary one for the great many pointing
                        // devices that have no middle button. Taken here for the
                        // same reason the wheel and the right button are — the
                        // arbiter disambiguates presses that all begin alike, and
                        // these do not begin alike.
                        val panning = event.buttons.isTertiaryPressed ||
                            (event.buttons.isPrimaryPressed && event.keyboardModifiers.isAltPressed)
                        pilot.panning(panning, pilot.screenDelta)
                        if (panning) {
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        // The mouse's right button: it is a request for a menu,
                        // never the start of a drag.
                        val secondary = event.buttons.isSecondaryPressed
                        event.changes.firstOrNull()?.let {
                            pilot.secondary(secondary, onFelt(it).at)
                        }
                        if (secondary) {
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val frame = TouchFrame(
                            timeMillis = event.changes.firstOrNull()?.uptimeMillis ?: 0L,
                            touches = event.changes.filter { it.pressed }.map(::onFelt),
                            // Every pointer the event carried, including any that
                            // went up inside it: Android batches, and a brisk
                            // two-finger tap can arrive with both already lifted.
                            seen = event.changes.size,
                            // And where those lifted ones were when they left, or
                            // a fully batched tap has nothing to flip *at*.
                            released = event.changes.filterNot { it.pressed }.map(::onFelt),
                        )

                        pilot.frame(frame)

                        if (machine.phase.locked) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    )
}

/**
 * How much wider the hand opened since last frame, as a ratio.
 *
 * One when there are not two pointers that were down in both frames, which is
 * the answer that means "no pinch" rather than a special case: a ratio of one
 * dollies by nothing. The two pointers are picked by id order rather than by
 * event order, because the order changes hand between platforms and a pinch
 * that reverses when a driver reorders its pointers is a very bad afternoon.
 */
private fun spanRatio(before: Map<Long, Vec2>, now: Map<Long, Vec2>): Float {
    val both = now.keys.filter { it in before }.sorted()
    if (both.size < 2) return 1f

    val was = TwoFinger.span(before.getValue(both[0]), before.getValue(both[1]))
    val is0 = TwoFinger.span(now.getValue(both[0]), now.getValue(both[1]))
    // Two fingers all but touching turn a pixel of jitter into a large ratio,
    // which is the same hazard `minTwistSpan` exists for and gets the same
    // answer: below it, there is no gesture here to measure.
    if (was < MIN_PINCH_SPAN) return 1f
    return is0 / was
}

/** Below this separation on the glass, a pinch is noise. */
private const val MIN_PINCH_SPAN = 48f

/**
 * How far the hand may sweep and still count as not having moved the camera.
 *
 * A real finger never lands and lifts from the same pixel, and this is a tablet:
 * the whole travel of a firm tap is a few pixels across. Generous, because the
 * cost of being wrong in one direction is a fan that will not close and in the
 * other is a fan that closes when you meant to nudge the table by a hair — and
 * reopening it costs one tap.
 */
private const val STILL = 24f

/**
 * Carries out one thing the arbiter says happened.
 *
 * Returns what is currently grabbed, which only [MatEvent.Pressed] changes —
 * the press is where the stage looks under the finger, and everything after it
 * acts on what was found there.
 */
private fun handle(
    event: MatEvent,
    grabbed: DragOrigin?,
    play: PlayState,
    layout: BoardLayout,
    feedback: Feedback,
    onMenu: (DragOrigin) -> Unit,
    attaching: Boolean,
    /**
     * How far apart the hand's cards are, so the hit boxes land where they are
     * drawn. `handPointFor` has two readers on purpose — the pose and this —
     * because two readings of one pure function cannot drift and a value passed
     * between them can. A tuning that reached one and not the other would be a
     * card you can see and cannot pick up.
     */
    handStep: Float,
    /** Where an open fan floats, and through what — see [whatIsUnder]. */
    fanPlane: StagePlane?,
    fanLift: Float,
): DragOrigin? {
    fun mat(at: Vec2): MatPoint = layout.toMat(at.x to at.y)

    when (event) {
        is MatEvent.Pressed ->
            return whatIsUnder(
                play.field, layout, event.at, play.fanned, handStep, fanPlane, fanLift,
            )

        is MatEvent.Tapped -> {
            when (val what = grabbed) {
                // A pile opens. Tapping the deck used to draw from it, which is
                // the one thing a deck does that already had a better idiom —
                // dragging the top card off it, which is what taking a card off
                // a deck actually is — and it spent the only gesture a pile has
                // on the one question a pile cannot otherwise answer: *what is
                // in it*. Every pile now spreads out when you tap it, and a card
                // in a spread pile goes to your hand when you tap that.
                is DragOrigin.Pile ->
                    if (play.fanned == what.fanSource) {
                        takeFromFan(play, what, feedback)
                    } else if (play.field.pile(what.pile).isNotEmpty()) {
                        play.fan(what)
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    }

                // A card on the mat with anything under it is a stack, and a
                // stack is searched exactly like a pile — which is the whole of
                // what makes an Xyz monster's materials reachable. A card with
                // nothing under it has nothing to spread, so it does the one
                // thing a tap has always done to a card: comes to the top.
                is DragOrigin.Mat ->
                    if (play.fanned == what) {
                        // Its own spread is open, and the card is still sitting
                        // where it was: tapping it again puts everything back.
                        play.fan(null)
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    } else if (play.field.under(what.id).size > 1) {
                        play.fan(what)
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    } else if (play.move { it.bringToFront(what.id) }) {
                        feedback.play(SoundEffect.LIFT, Haptic.LIFT)
                    }

                is DragOrigin.Buried -> takeFromFan(play, what, feedback)

                else -> Unit
            }
        }

        is MatEvent.LiftedCard -> {
            grabbed?.let {
                play.lift(
                    it, mat(event.at), layout, whole = false,
                    cameOutOf = fanSlotOf(play, layout, it, fanPlane, fanLift),
                )
                feedback.play(SoundEffect.LIFT, Haptic.LIFT)
            }
        }

        is MatEvent.LiftedStack -> {
            grabbed?.let {
                play.lift(
                    it, mat(event.at), layout, whole = true,
                    cameOutOf = fanSlotOf(play, layout, it, fanPlane, fanLift),
                )
                feedback.play(SoundEffect.LIFT, Haptic.LIFT)
            }
        }

        is MatEvent.Moved -> play.carryTo(mat(event.at), layout, attaching)

        // The card has been held still over another one long enough to mean it
        // is going underneath. Re-resolving with the same point is what changes
        // the indicator from "stack" to "attach", so the user is told before
        // they let go rather than after.
        is MatEvent.Dwelled -> {
            if (play.carry != null) {
                play.carryTo(mat(event.at), layout, attaching = true)
                feedback.play(SoundEffect.LIFT, Haptic.SLIDE)
            }
        }

        is MatEvent.Dropped -> {
            // Asked before the release, because the release is what clears it.
            // Landing on another card is two surfaces meeting and the hand can
            // tell; landing on felt is one.
            val onto = play.carry?.intent
            val stacked = onto is DropIntent.Stack || onto is DropIntent.Attach
            val from = play.carry?.from
            // Put back in the gap it came out of. `release` runs anyway, because
            // it is what lets go of the card; it simply commits nothing, which
            // is what makes this the one drop that is exactly reversible. The
            // sound is the card sliding home rather than landing, and the fan
            // stays open, because nothing about the search has finished.
            if (onto == DropIntent.Cancel) feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
            if (play.carry != null && play.release()) {
                feedback.play(SoundEffect.SNAP, HapticScore.landing(stacked))
                // You searched, you found it, you are done — and the deck gets
                // shuffled on the way out, which is what `closeFan` is for.
                //
                // Through `cameFrom` rather than by hand. This read
                // `from.pile == play.fanned`, a `BoardSlot` against a
                // `DragOrigin`, which compiles and is always false: the fan
                // never closed on a drop at all, and only the tap route
                // (`takeFromFan`) ever squared a pile back up. It also only ever
                // asked about `Pile`, so a card dragged out of a spread *stack*
                // was never going to close one either.
                if (from != null && from.cameFrom(play.fanned)) {
                    if (play.closeFan()) {
                        feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
                    } else {
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    }
                }
            }
        }

        is MatEvent.Flipped -> {
            val what = grabbed
            when {
                // Mid-carry, the card turns in the air and lands set. Putting
                // it down face-up and flipping it after would have shown the
                // table the one card the player meant to hide.
                play.carry != null ->
                    if (play.turnCarry()) feedback.play(SoundEffect.SLIDE, Haptic.FLIP)
                what is DragOrigin.Mat ->
                    if (play.move { it.flip(what.id) }) {
                        feedback.play(SoundEffect.SLIDE, Haptic.FLIP)
                    }
                else -> Unit
            }
        }

        is MatEvent.Twisting -> play.twistCarry(event.quarterTurns)

        // The one event with nothing to hear, because crossing a notch makes no
        // sound. It is also the sharpest thing the table can say, and a twist
        // gesture you can feel the detents of is one you can do without looking.
        is MatEvent.Detent -> feedback.feel(Haptic.DETENT)

        is MatEvent.TwistCommitted -> {
            val what = grabbed
            if (play.carry != null) {
                play.release()
            } else if (what is DragOrigin.Mat && event.quarterTurns % 2 != 0) {
                if (play.move { it.rotate(what.id) }) {
                    feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                }
            }
        }

        is MatEvent.MenuRequested -> grabbed?.let(onMenu)

        MatEvent.Cancelled -> play.cancelCarry()

        // Looking, not moving: the card rises and turns toward the reader and
        // the field is untouched, which is what a held finger on a table means.
        is MatEvent.PeekBegan -> grabbed?.let { what ->
            // Everything is peekable except the deck. The graveyard, the
            // banished pile and the extra deck are all open information you are
            // allowed to read; the top of your own deck is the one card a
            // goldfish is only honest without.
            val deck = what is DragOrigin.Pile && what.pile == BoardSlot.Deck
            if (!deck) {
                play.peek(what)
                feedback.play(SoundEffect.LIFT, Haptic.PEEK)
            }
        }

        MatEvent.PeekEnded -> play.peek(null)

        // The camera's two, which are answered in `MatPilot` rather than here:
        // this function's whole vocabulary is the field, and the camera is the
        // one gesture that changes nothing about it. Listed rather than swept
        // into an `else` so that adding an event to the language still fails to
        // compile here until somebody has said what the table does about it.
        is MatEvent.CameraMoved, MatEvent.CameraEnded -> Unit
    }

    return grabbed
}

/**
 * What is under the finger, in mat-plane pixels.
 *
 * Order matters and is the same order the eye uses: the topmost card on the mat
 * first — `mat` is ordered back to front, so the search runs backwards — then
 * the hand, then the bare piles. A pile is only reachable when no card is
 * sitting on it, which is right: the card you can see is the card you meant.
 */
private fun whatIsUnder(
    field: PlayField,
    layout: BoardLayout,
    at: Vec2,
    fanned: DragOrigin? = null,
    handStep: Float = StageTuning.DEFAULT.hand.stepFraction,
    /**
     * The camera's plane, and how far a spread pile floats above the felt.
     *
     * A fanned card is *drawn* through `StagePlane.flatten`, so it appears some
     * way from the mat coordinates `PileFan` gave it — tens of pixels at the
     * table seat, against a fan whose cards may sit a third of a card apart. The
     * finger arrives on the felt. Comparing the two is comparing two different
     * places, and the card you got was reliably not the card you pointed at.
     *
     * So the finger is lifted onto the fan's own plane first, with
     * `StagePlane.raise` — [flatten]'s exact inverse, and exact here because a
     * fanned card is flat and at one height. Null plane means "no camera yet",
     * which is the frame between the first composition and the `SideEffect` that
     * hands one over; the old behaviour is what it falls back to.
     */
    fanPlane: StagePlane? = null,
    fanLift: Float = 0f,
): DragOrigin? {
    val halfWidth = layout.cardWidth / 2f
    val halfHeight = layout.cardHeight / 2f

    // A spread pile is over the board, so it is asked first — and inside its
    // own footprint it is asked *instead*, or a finger reaching for a card in
    // the fan would come back holding whatever happens to be on the board
    // underneath it.
    //
    // This is the whole of the search feature at the input end, and it is one
    // line of it: `DragOrigin.Pile` has carried an index since it was written,
    // `DropCommit` dispatches it straight to `playFromDeck(index, …)`, and the
    // only reason nothing in this app could ask for a card that was not on top
    // of a pile is that this function returned a hard-coded zero.
    if (fanned != null) {
        val spread = fanOf(field, layout, fanned)
        val onFan = fanPointFor(at, fanPlane, fanLift)
        val index = PileFan.cardAt(spread, onFan.x, onFan.y, layout.cardWidth, layout.cardHeight)
        if (index != null) return fanned.fanCardAt(index)
        if (spread.bounds.contains(onFan.x, onFan.y)) return null
    }

    fun covers(centre: Pair<Float, Float>): Boolean =
        abs(at.x - centre.first) <= halfWidth && abs(at.y - centre.second) <= halfHeight

    for (placed in field.mat.asReversed()) {
        if (covers(layout.toPixels(placed.at))) return DragOrigin.Mat(placed.id)
    }

    field.hand.indices.reversed().forEach { index ->
        val point = handPointFor(layout, index, field.hand.size, handStep)
        if (covers(layout.toPixels(point))) return DragOrigin.Hand(index)
    }

    listOf(
        BoardSlot.Deck to field.deck.size,
        BoardSlot.ExtraDeck to field.extraDeck.size,
        BoardSlot.Graveyard to field.graveyard.size,
        BoardSlot.Banished to field.banished.size,
    ).forEach { (slot, count) ->
        // A pile that is spread out has no cards at its own slot — they are all
        // on the table below. Left in, its empty square would still answer for
        // the top card, so tapping the gap the deck used to be in would silently
        // take a card out of the fan.
        if (fanned == DragOrigin.Pile(slot, 0)) return@forEach
        val rect = layout[slot] ?: return@forEach
        if (count > 0 && rect.holds(at.x, at.y)) return DragOrigin.Pile(slot, 0)
    }

    return null
}

/**
 * Where a spread pile's cards are, asked the same way the renderer asks it.
 *
 * `PileFan` is pure and cheap, so both the hit test and `seatsFor` call it
 * rather than one of them being handed the other's answer. Two readings of one
 * function cannot drift; a value passed between them can, and the thing that
 * would drift is *which card you are pointing at*.
 */
private fun fanOf(field: PlayField, layout: BoardLayout, what: DragOrigin): FanSpread =
    PileFan.spread(field.fanOf(what).size, layout.field, layout.cardWidth, layout.cardHeight)

/**
 * Where the card being picked up was lying in the open spread, **on the felt**.
 *
 * The gap it came out of, which is what "put it back" means. Null unless the
 * drag started in the fan that is still open, which is the whole of when the
 * question has an answer.
 *
 * The flatten is the load-bearing half. A spread floats at [lift] above the
 * felt, so `PileFan`'s coordinates are the fan's own plane, while the carried
 * card's position is a finger unprojected onto the felt — the same two places
 * `StagePlane.raise` exists to reconcile at the hit test. This is the other
 * direction of that same reconciliation: exact for a flat thing at one height,
 * which a fanned card is, and the identity when nothing is floating.
 *
 * The index arithmetic is `fanCardAt` run backwards, and it has to be: a stack's
 * spread leaves the card on top out of it, so every card in one is `Buried` at
 * one more than its place in the fan. The top card of an open stack has no slot
 * in the spread at all and gets null — dragging it away takes the whole pile,
 * and there is nothing to put back into.
 */
private fun fanSlotOf(
    play: PlayState,
    layout: BoardLayout,
    what: DragOrigin,
    plane: StagePlane?,
    lift: Float,
): MatPoint? {
    val fanned = play.fanned ?: return null
    if (!what.cameFrom(fanned)) return null

    val index = when (what) {
        is DragOrigin.Pile -> what.index
        is DragOrigin.Buried -> what.index - 1
        is DragOrigin.Mat, is DragOrigin.Hand -> return null
    }

    val card = fanOf(play.field, layout, fanned).cards.getOrNull(index) ?: return null
    if (plane == null || lift == 0f) return layout.toMat(card.x to card.y)

    val onFelt = plane.flatten(card.x, card.y, lift)
    return layout.toMat(onFelt.x to onFelt.y)
}

/**
 * The finger, on the plane an open fan floats on.
 *
 * One place, so that the hit test and [MatPilot.onFan] — which decides whether
 * the camera gets the gesture at all — cannot disagree about where the fan is.
 * They did not disagree before only because both were wrong in the same way.
 */
private fun fanPointFor(at: Vec2, plane: StagePlane?, lift: Float): Vec2 {
    if (plane == null || lift == 0f) return at
    val raised = plane.raise(at.x, at.y, lift)
    return Vec2(raised.x, raised.y)
}

/**
 * A card tapped in a spread pile goes to the hand, and the pile squares up.
 *
 * The common case by a distance — you search for a card in order to hold it —
 * and the one place it is written, so the deck, the graveyard, the extra deck
 * and a stack on the mat all do the same thing. Anywhere else is a drag, which
 * costs nothing extra because every drop rule on this table already works from
 * a pile.
 */
private fun takeFromFan(play: PlayState, what: DragOrigin, feedback: Feedback) {
    if (!play.move { field -> DropCommit.commit(field, what, DropIntent.Hand) }) return
    // Closing the deck's fan shuffles it, which is the point at which a search
    // is over — so the sound is whichever of the two actually happened.
    if (play.closeFan()) {
        feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
    } else {
        play.fan(null)
        feedback.play(SoundEffect.DEAL, Haptic.DEAL)
    }
}
