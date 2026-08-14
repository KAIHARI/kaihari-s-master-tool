package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * How fast the gesture is moving, for the flick heuristic.
 *
 * Smoothed, because a card thrown at the graveyard should not be judged on the
 * last two samples of a finger that was already slowing down as it lifted.
 */
private class VelocitySampler {
    var value: Vec2 = Vec2.Zero
        private set

    fun add(delta: Vec2, dtMillis: Long) {
        if (dtMillis <= 0L) return
        value = value * 0.7f + (delta * (1000f / dtMillis)) * 0.3f
    }

    fun reset() {
        value = Vec2.Zero
    }
}

/**
 * One arbiter for the whole mat, deciding what the fingers on it mean.
 *
 * The single most consequential decision here is that these are driven by
 * **one** `pointerInput` on the mat and routed by **one** arbiter — not one per
 * card. Compose hit-tests each pointer independently, so per-card detectors let
 * one finger start a drag on one card while a second finger starts a *separate*
 * drag on another. That is the two-competing-drags bug exactly, and no amount
 * of event consumption fixes it, because by then there are already two gesture
 * loops that each believe they are in charge.
 *
 * There may now be **two** of these, and that is not a retreat from the rule
 * above: `MatDesk` owns them, decides at the instant a finger lands which one it
 * belongs to, and hands each a frame containing only its own pointers. Two
 * competing drags were a bug when nothing decided between them; two deliberate
 * drags are two-handed play. Everything in this file goes on being about one
 * gesture and knows nothing about the other, which is what keeps it testable.
 *
 * The second is that this is pure Kotlin in core, with the composable doing
 * nothing but translating pointer events into [TouchFrame] and handing the
 * resulting [MatEvent]s back. Disambiguating four gestures that all begin
 * identically is the expensive part to get wrong, and it is exactly the part
 * that would otherwise only be verifiable by hand on a tablet, one release at
 * a time.
 *
 * ```
 * IDLE ──down──▶ PRESS ──slop──▶ DRAG_CARD ──2nd finger──▶ TWO_UNDECIDED(carried)
 *                  │                 │                        │        │
 *             hold │              up │                  twist │    pan │
 *                  ▼                 ▼                        ▼        ▼
 *                PEEK ──slop──▶ DRAG_CARD                  TWIST   DRAG_CARD
 *
 * PRESS ──2nd finger──▶ TWO_UNDECIDED ──held──▶ MENU
 *                              │  └──up before the hold──▶ Flipped
 *                              └──pan──▶ DRAG_CARD, and set face-down
 * ```
 */
class MatGestureMachine(private val limits: GestureThresholds = GestureThresholds()) {

    var phase: MatPhase = MatPhase.IDLE
        private set

    /** Where the gesture is pointing — what the placement resolver is asked about. */
    var focus: Vec2 = Vec2.Zero
        private set

    /** The live, uncommitted twist in degrees. */
    var twist: Float = 0f
        private set

    val quarterTurns: Int get() = quarterTurnsOf(twist, limits.detentDegrees)

    /**
     * The press landed on nothing, so this gesture belongs to the camera.
     *
     * Called by the host on the frame it hit-tests [MatEvent.Pressed] and finds
     * bare felt. It is a claim rather than a suggestion — from here the gesture
     * cannot become a drag, a peek, a twist or a menu, whatever the fingers go
     * on to do — and it is deliberately only reachable out of [MatPhase.PRESS],
     * so nothing can take a card out of a hand that is already carrying one.
     *
     * The split it draws is the whole of the control rework: **fingers on a card
     * move the card, fingers on the felt move the camera.** Before it, two
     * fingers on empty felt went off into the two-finger card gestures and
     * silently did nothing at all — there was no card for them to twist, stack
     * or open a menu about — which is a third of the gesture vocabulary spent on
     * the most-touched surface on the screen to no effect.
     */
    fun claimForCamera() {
        if (phase == MatPhase.PRESS) phase = MatPhase.CAMERA
    }

    private var owned: List<Long> = emptyList()
    /**
     * Pointers down last frame, so a gesture only ever starts on a fresh landing.
     *
     * This is also the whole record of what is furniture. A contact that was
     * down last frame is in here, and the adoption loop skips it, so anything
     * left resting when a gesture ends can never begin the next one — it is only
     * adoptable again after lifting and landing afresh. A gesture that ends
     * mid-frame re-seeds this from the pointers still on the glass, which is why
     * there is no second set of "stale" ids: a second set is a second thing to
     * keep in step, and the one that got forgotten would be the one deciding.
     */
    private var lastSeen: Set<Long> = emptySet()
    private var previous: Map<Long, Vec2> = emptyMap()
    private var previousTime = 0L
    private var pressPoint = Vec2.Zero
    private var pressedAt = 0L
    private var secondAt = 0L
    /** When the finger that started the gesture lifted, with others still down. */
    private var orphanedAt = 0L
    private var pan = Vec2.Zero
    /** Where a carried card stopped, and when, for the dwell. */
    private var stillAt = Vec2.Zero
    private var stillSince = 0L
    private var dwelled = false
    /**
     * How many fingers the user made this gesture with.
     *
     * Counted as pointers that *landed* — appeared having not been down the
     * frame before — at or after the frame the gesture began. Everything the
     * question "is this a three-finger gesture?" is asked about is decided by
     * this number, and the reasoning is worth stating because the next phase
     * will lean on it:
     *
     * A raw count of the pointers an event carried cannot answer it. A tablet
     * held in two hands has a palm, a bezel thumb or a forearm on the glass
     * most of the time; those are pressed pointers, they arrive in every event,
     * and a count of them is a count of the furniture in the room. Gating on it
     * made a one-finger press inert — no peek, no tap — for as long as the hand
     * that was holding the tablet stayed where it was, with no way back inside
     * the same press. A contact that was already resting when the press began
     * is not part of the press, however many of them there are.
     *
     * What is left is monotonic within a gesture, deliberately: a finger that
     * joins and leaves still counts, because that is a gesture the user made
     * with more fingers than one. It cannot be inflated by furniture, only by
     * fingers actually arriving, which is why a live drag or twist may be
     * judged on it safely where the old count would have killed the gesture the
     * other two fingers were visibly making.
     */
    private var landedFingers = 0
    /**
     * Contacts an event carried that were never seen pressed at all.
     *
     * Android batches motion hard enough that a brisk two-finger tap arrives as
     * one event with the second finger already gone, so [TouchFrame.seen] is
     * the only evidence it existed. But `seen` also counts the pointers that
     * were pressed and the one that just lifted, and both of those are already
     * accounted for — subtracting them is what keeps a resting palm from
     * reading as the second finger of a flip, which turned an ordinary tap
     * beside a resting hand into a flipped card.
     */
    private var passingContacts = 0
    private var carried = false
    private var detent = 0
    private val speed = VelocitySampler()

    fun onFrame(frame: TouchFrame): List<MatEvent> {
        val events = mutableListOf<MatEvent>()
        val here = frame.touches.associate { it.id to it.at }

        // A twist the scroll wheel left latched is not a touch gesture. A finger
        // arriving ends it, or the mat would be inert until something called
        // commitWheel.
        if (phase == MatPhase.TWIST && owned.isEmpty()) reset()

        // What this event says about how many fingers are involved. A pointer
        // that lifted in it was counted while it was down; what is left over
        // after the pressed ones and that one are subtracted is a contact that
        // came and went inside a single event, and nothing else ever sees it.
        landedFingers += here.keys.count { it !in lastSeen }
        val lifted = lastSeen.count { it !in here }
        passingContacts = maxOf(passingContacts, frame.seen - here.size - lifted)

        // The fingers this gesture owns, minus any that have lifted.
        val survivors = owned.filter { it in here }

        // The gesture belongs to the finger that started it — the one the host
        // hit-tested, and so the only one that knows what is being held. A
        // second pointer is a modifier, never an heir.
        //
        // But it cannot end the instant that finger lifts, because the two
        // fingers of a tap almost never leave in the same frame: the second is
        // a beat behind, and ending on the first would make the flip fire only
        // when the OS happened to batch them together. So the primary lifting
        // opens a grace window, and what fills it decides the gesture. The
        // other finger lifting inside it is a tap. Nothing at all is a hand
        // resting on the mat, and it gets the gesture taken away from it.
        //
        // That window is for gestures still waiting to find out what they are.
        // A gesture with something already in the air has nothing to wait for,
        // and waiting is how it used to die: a seventh of a second after the
        // primary lifted, the timer settled a drag that the other finger was
        // still visibly making, and dropped the pile wherever it had got to.
        // Which of the two fingers happened to lift first decided whether the
        // drag survived — so the answer is given here, once, for all of them.
        if (phase != MatPhase.IDLE && orphanedAt == 0L && survivors.isNotEmpty() &&
            owned.isNotEmpty() && owned[0] !in here
        ) {
            when {
                // The twist is held by both fingers together, so the survivor is
                // already carrying it: it keeps it, and no window opens. It says
                // so out loud a few lines below by committing the turn and
                // handing the card over. A pinch is the same shape — both
                // fingers are moving the camera, so either of them may go on
                // doing it alone as an orbit.
                phase == MatPhase.TWIST || phase == MatPhase.CAMERA -> Unit
                // A carried card is held by the one finger that picked it up,
                // and that finger has let go. The other came along to steady
                // the tablet and never held the card, so handing over would
                // teleport it to wherever that hand happens to rest. Land it
                // where the user let go of it, now rather than a seventh of a
                // second later, and leave what is still on the glass inert.
                phase == MatPhase.DRAG_CARD ||
                    (phase == MatPhase.TWO_UNDECIDED && carried) -> {
                    events += settle(frame, complete = true)
                    reset()
                    lastSeen = here.keys
                    return events
                }
                else -> orphanedAt = frame.timeMillis
            }
        }
        if (phase != MatPhase.IDLE && survivors.isEmpty()) {
            events += settle(frame, complete = true)
            reset()
            lastSeen = here.keys
            return events
        }

        owned = survivors
        for (touch in frame.touches) {
            if (owned.size >= 2) break
            if (touch.id in owned) continue
            // A two-finger gesture that has already decided what it is keeps the
            // two fingers it decided with. This is the other half of the rule a
            // few lines above, and without it that rule has a hole: the twist is
            // exempt from the grace window *because* the survivor is one of the
            // fingers that started it, and a hand landing afterwards would
            // quietly become the other one. The twist would then follow a resting
            // palm with no timer left that can end it.
            //
            // A carried card is deliberately not on this list. The second finger
            // arriving mid-carry is the flip — it is how a card already in the
            // air is turned over — so DRAG_CARD has to go on listening for one.
            if (phase == MatPhase.TWIST) break
            // A gesture only ever takes a finger that just landed. One already
            // resting when the last gesture ended is furniture — and so is one
            // resting through this one, which is what the guard used to miss by
            // asking only while idle. Mid-gesture a slot opens every time one
            // of the two working fingers lifts, and the palm sitting motionless
            // an inch away was adopted into it on the next frame; from there
            // the weight of a hand shifting turned a card to defence.
            if (touch.id in lastSeen) continue
            owned = owned + touch.id
        }

        val fingers = owned.mapNotNull { here[it] }
        val mine = here.filterKeys { it in owned }

        if (fingers.isEmpty()) {
            events += settle(frame, complete = true)
            reset()
            lastSeen = here.keys
            return events
        }

        val dt = if (previousTime == 0L) 0L else frame.timeMillis - previousTime
        val moved = TwoFinger.pan(previous, mine)
        speed.add(moved, dt)

        // Phases a second finger must not re-open the two-finger question for.
        // CAMERA is on the list because a pinch is *two* fingers doing the one
        // thing it already is; without it the second finger of a pinch would
        // send the gesture off to decide between a twist and a pile drag.
        val hadTwo = phase == MatPhase.TWO_UNDECIDED || phase == MatPhase.TWIST ||
            phase == MatPhase.MENU || phase == MatPhase.CAMERA

        if (phase == MatPhase.IDLE) {
            phase = MatPhase.PRESS
            pressPoint = fingers[0]
            focus = fingers[0]
            pressedAt = frame.timeMillis
            events += MatEvent.Pressed(fingers[0])
        } else if (fingers.size == 2 && !hadTwo) {
            // The second finger arrives. Nothing moves on this frame, because
            // `pan` is built only from pointers present in both — which is
            // exactly why it is built that way.
            carried = phase == MatPhase.DRAG_CARD
            if (phase == MatPhase.PEEK) events += MatEvent.PeekEnded
            phase = MatPhase.TWO_UNDECIDED
            secondAt = frame.timeMillis
            pan = Vec2.Zero
            twist = 0f
            detent = 0
        }

        when (phase) {
            MatPhase.PRESS -> {
                focus = fingers[0]
                if ((fingers[0] - pressPoint).length > limits.touchSlop) {
                    phase = MatPhase.DRAG_CARD
                    holdStill(focus, frame.timeMillis)
                    events += MatEvent.LiftedCard(focus)
                    events += MatEvent.Moved(focus, moved, speed.value)
                }
            }

            MatPhase.PEEK -> {
                focus = fingers[0]
                // A peek can slide into a drag; it must not be a dead end.
                if ((fingers[0] - pressPoint).length > limits.touchSlop * 2f) {
                    events += MatEvent.PeekEnded
                    phase = MatPhase.DRAG_CARD
                    holdStill(focus, frame.timeMillis)
                    events += MatEvent.LiftedCard(focus)
                }
            }

            MatPhase.DRAG_CARD -> {
                focus = fingers[0]
                // Any real movement restarts the clock and takes the dwell back.
                if ((focus - stillAt).length > limits.touchSlop) holdStill(focus, frame.timeMillis)
                events += MatEvent.Moved(focus, moved, speed.value)
            }

            MatPhase.TWO_UNDECIDED -> {
                pan += moved
                focus += moved
                accumulateTwist(fingers, guardSpan = true)
                when {
                    // Whichever crosses first locks, and nothing re-classifies
                    // afterwards. A twist that can slide back into a pan feels
                    // broken in a way people describe as slippery.
                    abs(twist) > limits.twistSlopDegrees -> {
                        phase = MatPhase.TWIST
                        detent = quarterTurns
                        events += MatEvent.Twisting(twist, detent)
                    }
                    pan.length > limits.touchSlop * limits.stackSlopFactor -> {
                        // Both branches end in DRAG_CARD now, and they still are
                        // not the same thing. `carried` is a second finger that
                        // arrived to steady the tablet **during** a drag, and it
                        // must change nothing at all; two fingers that started
                        // together are a set, and the card turns over in the air.
                        //
                        // Onto the primary finger on the way in, because that is
                        // what DRAG_CARD will read from the next frame onward.
                        // Left on the accumulated centroid it would jump by
                        // however far the two fingers had diverged, once, on the
                        // frame after the gesture locked.
                        phase = MatPhase.DRAG_CARD
                        focus = fingers[0]
                        holdStill(focus, frame.timeMillis)
                        if (!carried) events += MatEvent.LiftedSet(focus)
                        events += MatEvent.Moved(focus, moved, speed.value)
                    }
                }
            }

            MatPhase.TWIST -> {
                if (fingers.size < 2) {
                    // One finger lifted mid-twist. The turn is finished — but
                    // the other finger is still holding the card, so it keeps
                    // it rather than the card going dead under it. Both fingers
                    // were turning the card, so either of them is entitled to
                    // carry on with it; no grace window was opened over this
                    // lift, and none may be, or a seventh of a second later it
                    // would drop the card out of a hand that never let go.
                    events += MatEvent.TwistCommitted(quarterTurns)
                    phase = MatPhase.DRAG_CARD
                    holdStill(fingers[0], frame.timeMillis)
                    twist = 0f
                    detent = 0
                    focus = fingers[0]
                    events += MatEvent.Moved(focus, moved, speed.value)
                    previous = mine
                    previousTime = frame.timeMillis
                    lastSeen = here.keys
                    return events
                }
                // The span guard is off once locked: fingers may drift together
                // mid-twist and the rotation is still what is meant.
                accumulateTwist(fingers, guardSpan = false)
                val turns = quarterTurns
                if (turns != detent) {
                    detent = turns
                    events += MatEvent.Detent(turns)
                }
                events += MatEvent.Twisting(twist, turns)
            }

            // One event a frame, with the count on it, and no positions: the
            // host is holding the glass coordinates this gesture is actually
            // made of. Emitted even on a frame nothing moved, because the host
            // is the only thing that knows whether anything did — and a nudge
            // by zero is free.
            MatPhase.CAMERA -> {
                focus = fingers[0]
                events += MatEvent.CameraMoved(fingers.size)
            }

            MatPhase.MENU, MatPhase.IDLE -> Unit
        }

        events += checkTimers(frame.timeMillis)
        previous = mine
        previousTime = frame.timeMillis
        lastSeen = here.keys
        return events
    }

    /**
     * Restarts the dwell clock.
     *
     * Called at every way in to [MatPhase.DRAG_CARD], not from inside its own
     * branch: `when (phase)` picks its branch before the transition happens, so
     * the frame that *starts* a drag runs the branch it came from and never the
     * one it went to. A clock started only from inside DRAG_CARD would sit at
     * zero for that frame — and a drag that ends there, which is most of the
     * short ones, would never have had a clock at all.
     */
    private fun holdStill(at: Vec2, now: Long) {
        stillAt = at
        stillSince = now
        dwelled = false
    }

    private fun accumulateTwist(fingers: List<Vec2>, guardSpan: Boolean) {
        if (fingers.size < 2 || owned.size < 2) return
        if (guardSpan && TwoFinger.span(fingers[0], fingers[1]) < limits.minTwistSpan) return
        val before0 = previous[owned[0]] ?: return
        val before1 = previous[owned[1]] ?: return
        twist += TwoFinger.twistDegrees(before0, before1, fingers[0], fingers[1])
    }

    /**
     * The clock, turned by the mat's frame loop.
     *
     * A finger held perfectly still generates no pointer events at all, so a
     * long press driven only by [onFrame] never fires — the classic silent
     * long-press bug. The mat already runs one `withFrameNanos` loop for the
     * springs, so it ticks this too. Both callers are on the UI thread, which
     * is the only reason one machine can be driven from two places without a
     * lock; anyone moving the pointer handling off it has to revisit this.
     */
    fun onTick(timeMillis: Long): List<MatEvent> =
        if (phase == MatPhase.IDLE) emptyList() else checkTimers(timeMillis)

    private fun checkTimers(now: Long): List<MatEvent> = when {
        // The grace window closed with the rest of the hand still down. Land
        // anything in the air, claim nothing that needed a release, and make
        // what is left on the glass inert — it cannot start the next gesture
        // either, or a palm would become a permanent phantom finger.
        orphanedAt != 0L && now - orphanedAt >= limits.releaseGraceMillis -> {
            val out = settle(TouchFrame(now, emptyList()), complete = false)
            // [lastSeen] survives the reset on purpose: it is what makes the
            // hand still on the glass furniture rather than a phantom finger
            // waiting to start the next gesture.
            reset()
            out
        }
        // Held still over one spot with a card in hand: tuck it under rather
        // than drop it on top. Driven from here as well as from onFrame,
        // because a finger that has stopped moving stops producing events —
        // which is precisely the condition being detected.
        phase == MatPhase.DRAG_CARD && !dwelled && stillSince != 0L &&
            now - stillSince >= limits.longPressMillis -> {
            dwelled = true
            listOf(MatEvent.Dwelled(focus))
        }
        // Three fingers landing inside one event leave the press holding two of
        // them, and a press held is a peek. A hand put down flat is not asking
        // to read a card either — but a hand that was already resting when the
        // press began is furniture, and one finger pressed on a card still
        // means read it, however much of the user is leaning on the mat.
        phase == MatPhase.PRESS && landedFingers < 3 &&
            now - pressedAt >= limits.longPressMillis -> {
            phase = MatPhase.PEEK
            listOf(MatEvent.PeekBegan(focus))
        }
        // `carried` is the second finger that arrived to steady the tablet
        // during a drag. It must never open a menu or flip anything. Nor may a
        // third finger: two fingers held still is a menu, three is a hand being
        // put down, and answering the hand opens a menu nobody asked for.
        phase == MatPhase.TWO_UNDECIDED && !carried && landedFingers < 3 &&
            now - secondAt >= limits.longPressMillis -> {
            phase = MatPhase.MENU
            listOf(MatEvent.MenuRequested(focus))
        }
        else -> emptyList()
    }

    /**
     * Everything this gesture can account for: fingers that landed, plus the
     * ones only a batched event ever mentioned. What "two fingers" means to the
     * gestures that commit on release.
     */
    private val contacts: Int get() = landedFingers + passingContacts

    /**
     * The gesture is over; say what it was.
     *
     * [complete] is false when the gesture ended because the finger that owned
     * it lifted while something else — the other hand, a palm — is still down.
     * Anything already in the air still has to land, but the gestures that are
     * defined by *letting go* cannot be claimed by a hand that never did.
     */
    private fun settle(frame: TouchFrame, complete: Boolean): List<MatEvent> = when (phase) {
        // Nothing was ever pressed, but the event carried two pointers that had
        // already lifted: a two-finger tap delivered whole. The positions come
        // from where they left, because there is no other record of them. The
        // pointers that lifted are counted rather than the pointers the event
        // carried, and exactly two of them: batching does not care how many
        // fingers it hands over at once, so *at least* two reads a hand put
        // down flat as a flip — and a raw count of the event would read a palm
        // resting through a two-finger tap as a third finger and lose it.
        MatPhase.IDLE -> when {
            frame.released.size == 2 ->
                listOf(MatEvent.Flipped(frame.released.first().at))
            else -> emptyList()
        }
        MatPhase.PRESS -> when {
            !complete -> emptyList()
            // Three fingers is not a loud two, and it is not a tap either. The
            // mat has nothing that means three, so it says nothing.
            landedFingers >= 3 -> emptyList()
            // A pair, which is a different claim from the one above rather than
            // a looser form of it: two fingers that landed, or the one that
            // landed and one more that only a batched event ever mentioned. A
            // contact that was resting before any of this began is neither, and
            // counting it here turned an ordinary tap beside a hand holding the
            // tablet into a card flipped face down.
            contacts == 2 -> listOf(MatEvent.Flipped(focus))
            (focus - pressPoint).length <= limits.touchSlop -> listOf(MatEvent.Tapped(focus))
            else -> emptyList()
        }
        MatPhase.PEEK -> listOf(MatEvent.PeekEnded)
        MatPhase.DRAG_CARD -> listOf(MatEvent.Dropped(focus, speed.value))
        MatPhase.TWO_UNDECIDED -> when {
            carried -> listOf(MatEvent.Dropped(focus, speed.value))
            complete && contacts == 2 -> listOf(MatEvent.Flipped(focus))
            else -> emptyList()
        }
        MatPhase.TWIST -> listOf(MatEvent.TwistCommitted(quarterTurns))
        MatPhase.MENU -> emptyList()
        // Nothing to claim on release and nothing to undo: the camera was moved
        // as the fingers moved, and it is already where it was left. Said out
        // loud anyway, because the host has per-gesture state — the last glass
        // positions a delta is measured against — that has to be let go of.
        MatPhase.CAMERA -> listOf(MatEvent.CameraEnded)
    }

    /** The composable left, or an ancestor took the gesture away. */
    fun cancel(): List<MatEvent> {
        val was = phase
        reset()
        return if (was == MatPhase.IDLE) emptyList() else listOf(MatEvent.Cancelled)
    }

    /** A desktop scroll wheel, feeding the same twist accumulator. */
    fun wheel(degrees: Float): List<MatEvent> {
        if (phase != MatPhase.TWIST) {
            phase = MatPhase.TWIST
            twist = 0f
            detent = 0
        }
        twist += degrees

        val turns = quarterTurns
        val events = mutableListOf<MatEvent>()
        if (turns != detent) {
            detent = turns
            events += MatEvent.Detent(turns)
        }
        events += MatEvent.Twisting(twist, turns)
        return events
    }

    /** Called a beat after the wheel stops, since a wheel has no "up". */
    fun commitWheel(): List<MatEvent> {
        if (phase != MatPhase.TWIST) return emptyList()
        val turns = quarterTurns
        reset()
        return listOf(MatEvent.TwistCommitted(turns))
    }

    private fun reset() {
        phase = MatPhase.IDLE
        owned = emptyList()
        previous = emptyMap()
        previousTime = 0L
        pan = Vec2.Zero
        twist = 0f
        detent = 0
        stillAt = Vec2.Zero
        stillSince = 0L
        dwelled = false
        landedFingers = 0
        passingContacts = 0
        carried = false
        orphanedAt = 0L
        speed.reset()
    }

    companion object {
        /**
         * The nearest quarter turn, with the boundary at [detent] degrees.
         *
         * Clamped, so a card spun round several times cannot end up owing three
         * turns of unwinding. The model only cares whether the count is odd — a
         * card at an odd quarter turn is in defence — while the view keeps the
         * sign, so a card turned to minus ninety stays there instead of
         * springing the long way round to plus ninety.
         */
        fun quarterTurnsOf(degrees: Float, detent: Float): Int {
            val turns = floor((abs(degrees) + (90f - detent)) / 90f).toInt()
            return (turns * sign(degrees).toInt()).coerceIn(-2, 2)
        }
    }
}
