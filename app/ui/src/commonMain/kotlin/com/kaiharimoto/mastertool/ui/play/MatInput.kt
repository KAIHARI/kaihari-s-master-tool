package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropCommit
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlayField
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
import com.kaiharimoto.mastertool.core.layout.CameraFit
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.mat.MatEvent
import com.kaiharimoto.mastertool.core.mat.MatGestureMachine
import com.kaiharimoto.mastertool.core.mat.Touch
import com.kaiharimoto.mastertool.core.mat.TouchFrame
import com.kaiharimoto.mastertool.core.mat.TwoFinger
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.ui.fx.Feedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import kotlin.math.abs

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
) {
    var onMenu: (DragOrigin) -> Unit = {}

    /**
     * The camera, and how the hand moved on the *glass* since the last frame.
     *
     * The felt is the camera's surface. A press that lands on nothing claims the
     * gesture for the camera outright — [MatGestureMachine.claimForCamera] —
     * and from there one finger orbits and two also pinch. That claim is what
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

    /** Whether the carried card is going *under* what it is over, not on top. */
    private var attaching = false

    /** True while the secondary mouse button is down, so the menu opens once. */
    private var secondaryDown = false

    fun frame(frame: TouchFrame) = carryOut(machine.onFrame(frame))

    fun tick(timeMillis: Long) = carryOut(machine.onTick(timeMillis))

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
        whatIsUnder(play.field, layout, at, play.fanned)?.let(onMenu)
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

            grabbed = handle(event, grabbed, play, layout, feedback, onMenu, attaching)

            // The press has been hit-tested by now — `handle` is what does it —
            // so this is the first moment anything knows whether the finger
            // landed on a card. Nothing under it means the felt, and the felt
            // is the camera's.
            //
            // Two things are neither a card nor the felt, and both of them have
            // to be taken out before that claim is made, because a claimed
            // gesture can never become a tap again: a shuffle mark, and the
            // space *inside* an open fan. Miss either and pressing between two
            // cards of a spread deck starts orbiting the table.
            if (event is MatEvent.Pressed) {
                pressedControl =
                    if (grabbed == null) MatControls.at(layout, event.at.x, event.at.y) else null
                if (grabbed == null && pressedControl == null && !onFan(event.at)) {
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
            if (event is MatEvent.CameraMoved) fly(event.fingers)
            if (event is MatEvent.CameraEnded) settle()
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
        return fanOf(play.field, layout, slot).bounds.contains(at.x, at.y)
    }

    private fun fly(fingers: Int) {
        val state = camera ?: return
        val across = state.rig.width
        val down = state.rig.height
        if (across <= 0f || down <= 0f) return

        val dolly = if (fingers >= 2 && spanRatio > 0.01f) 1f / spanRatio - 1f else 0f
        state.rig.nudge(
            deltaYaw = screenDelta.x / across * YAW_PER_SWEEP,
            deltaPitch = -screenDelta.y / down * PITCH_PER_SWEEP,
            dollyBy = dolly,
        )
        state.refresh()
    }

    /**
     * Let go, and the table comes back onto the glass.
     *
     * [CameraFit] has existed, tested, since the camera was written, and has
     * never once been called: the comment in `PlayScreen` promising "CameraFit
     * dollies back instead" was describing an intention. What actually kept the
     * board on screen was [com.kaiharimoto.mastertool.core.layout.CameraEnvelope]'s
     * distance floor, which is a guard against the *mat crossing the lens* and
     * not a guarantee about anything staying visible — so a turned table could
     * simply walk its own corners off the screen, and the pinch that now ships
     * is the shortest route to doing it.
     *
     * On release rather than per frame, which is what the fitter was written
     * for: sixteen projections of four points is nothing once, and a correction
     * applied *during* a drag is a camera fighting the finger holding it.
     * Sprung rather than assigned, so it reads as the table settling.
     */
    private fun settle() {
        val state = camera ?: return
        val rig = state.rig
        if (rig.width <= 0f || rig.height <= 0f) return

        val fitted = CameraFit.fit(
            wanted = rig.pose,
            bounds = layout.bounds,
            envelope = rig.envelope,
            surfaceWidth = rig.width,
            surfaceHeight = rig.height,
            plane = { it.planeFor(rig.width, rig.height) },
        )
        if (fitted != rig.pose) rig.aimAt(fitted)
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
                            val onPlane = plane.unproject(change.position.x, change.position.y)
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

                        // The mouse's right button, before the gesture machine
                        // sees anything: it is a request for a menu, never the
                        // start of a drag.
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
): DragOrigin? {
    fun mat(at: Vec2): MatPoint = layout.toMat(at.x to at.y)

    when (event) {
        is MatEvent.Pressed -> return whatIsUnder(play.field, layout, event.at, play.fanned)

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
                    if (play.fanned == what.pile) {
                        if (play.move { field ->
                                DropCommit.commit(field, what, DropIntent.Hand)
                            }
                        ) {
                            play.fan(null)
                            feedback.play(SoundEffect.DEAL, Haptic.DEAL)
                        }
                    } else if (play.field.pile(what.pile).isNotEmpty()) {
                        play.fan(what.pile)
                        feedback.play(SoundEffect.SLIDE, Haptic.SLIDE)
                    }

                is DragOrigin.Mat ->
                    if (play.move { it.bringToFront(what.id) }) {
                        feedback.play(SoundEffect.LIFT, Haptic.LIFT)
                    }
                else -> Unit
            }
        }

        is MatEvent.LiftedCard -> {
            grabbed?.let {
                play.lift(it, mat(event.at), layout, whole = false)
                feedback.play(SoundEffect.LIFT, Haptic.LIFT)
            }
        }

        is MatEvent.LiftedStack -> {
            grabbed?.let {
                play.lift(it, mat(event.at), layout, whole = true)
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
            if (play.carry != null && play.release()) {
                feedback.play(SoundEffect.SNAP, HapticScore.landing(stacked))
                // You searched, you found it, you are done — and the deck gets
                // shuffled on the way out, which is what `closeFan` is for.
                if (from is DragOrigin.Pile && from.pile == play.fanned) {
                    if (play.closeFan()) feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
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
    fanned: BoardSlot? = null,
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
        val index = PileFan.cardAt(spread, at.x, at.y, layout.cardWidth, layout.cardHeight)
        if (index != null) return DragOrigin.Pile(fanned, index)
        if (spread.bounds.contains(at.x, at.y)) return null
    }

    fun covers(centre: Pair<Float, Float>): Boolean =
        abs(at.x - centre.first) <= halfWidth && abs(at.y - centre.second) <= halfHeight

    for (placed in field.mat.asReversed()) {
        if (covers(layout.toPixels(placed.at))) return DragOrigin.Mat(placed.id)
    }

    field.hand.indices.reversed().forEach { index ->
        val point = handPointFor(layout, index, field.hand.size)
        if (covers(layout.toPixels(point))) return DragOrigin.Hand(index)
    }

    listOf(
        BoardSlot.Deck to field.deck.size,
        BoardSlot.ExtraDeck to field.extraDeck.size,
        BoardSlot.Graveyard to field.graveyard.size,
        BoardSlot.Banished to field.banished.size,
    ).forEach { (slot, count) ->
        // The pile that is spread out has no cards at its own slot — they are
        // all on the table below. Left in, its empty square would still answer
        // for the top card, so tapping the gap the deck used to be in would
        // silently take a card out of the fan.
        if (slot == fanned) return@forEach
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
private fun fanOf(field: PlayField, layout: BoardLayout, slot: BoardSlot): FanSpread =
    PileFan.spread(field.pile(slot).size, layout.field, layout.cardWidth, layout.cardHeight)
