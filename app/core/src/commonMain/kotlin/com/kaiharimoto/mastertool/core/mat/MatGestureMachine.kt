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
 * The single most consequential decision here is that there is **one** of
 * these, driven by **one** `pointerInput` on the mat — not one per card.
 * Compose hit-tests each pointer independently, so per-card detectors let one
 * finger start a drag on one card while a second finger starts a *separate*
 * drag on another. That is the two-competing-drags bug exactly, and no amount
 * of event consumption fixes it, because by then there are already two gesture
 * loops that each believe they are in charge.
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
 *                PEEK ──slop──▶ DRAG_CARD                  TWIST   DRAG_STACK
 *
 * PRESS ──2nd finger──▶ TWO_UNDECIDED ──held──▶ MENU
 *                              └──up before the hold──▶ Flipped
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

    /** Set by the adapter from the keyboard modifiers: shift-drag takes the pile. */
    var stackModifier: Boolean = false

    private var owned: List<Long> = emptyList()
    private var previous: Map<Long, Vec2> = emptyMap()
    private var previousTime = 0L
    private var pressPoint = Vec2.Zero
    private var pressedAt = 0L
    private var secondAt = 0L
    private var pan = Vec2.Zero
    private var peakFingers = 0
    private var carried = false
    private var detent = 0
    private val speed = VelocitySampler()

    fun onFrame(frame: TouchFrame): List<MatEvent> {
        val events = mutableListOf<MatEvent>()
        val here = frame.touches.associate { it.id to it.at }

        // Ownership: the first two fingers to land, in order, and no others. A
        // resting palm or a steadying thumb must not be able to start or
        // corrupt anything.
        owned = owned.filter { it in here }
        for (touch in frame.touches) {
            if (owned.size >= 2) break
            if (touch.id !in owned) owned = owned + touch.id
        }

        val fingers = owned.mapNotNull { here[it] }
        val mine = here.filterKeys { it in owned }
        peakFingers = maxOf(peakFingers, frame.seen)

        if (fingers.isEmpty()) {
            events += settle()
            reset()
            return events
        }

        val dt = if (previousTime == 0L) 0L else frame.timeMillis - previousTime
        val moved = TwoFinger.pan(previous, mine)
        speed.add(moved, dt)

        val hadTwo = phase == MatPhase.TWO_UNDECIDED || phase == MatPhase.TWIST ||
            phase == MatPhase.DRAG_STACK || phase == MatPhase.MENU

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
                    events += if (stackModifier) {
                        MatEvent.LiftedStack(focus)
                    } else {
                        MatEvent.LiftedCard(focus)
                    }
                    events += MatEvent.Moved(focus, moved, speed.value)
                }
            }

            MatPhase.PEEK -> {
                focus = fingers[0]
                // A peek can slide into a drag; it must not be a dead end.
                if ((fingers[0] - pressPoint).length > limits.touchSlop * 2f) {
                    events += MatEvent.PeekEnded
                    phase = MatPhase.DRAG_CARD
                    events += MatEvent.LiftedCard(focus)
                }
            }

            MatPhase.DRAG_CARD -> {
                focus = fingers[0]
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
                        phase = MatPhase.DRAG_STACK
                        events += MatEvent.LiftedStack(focus)
                        events += MatEvent.Moved(focus, moved, speed.value)
                    }
                }
            }

            MatPhase.DRAG_STACK -> {
                focus += moved
                events += MatEvent.Moved(focus, moved, speed.value)
            }

            MatPhase.TWIST -> {
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

            MatPhase.MENU, MatPhase.IDLE -> Unit
        }

        events += checkTimers(frame.timeMillis)
        previous = mine
        previousTime = frame.timeMillis
        return events
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
        phase == MatPhase.PRESS && now - pressedAt >= limits.longPressMillis -> {
            phase = MatPhase.PEEK
            listOf(MatEvent.PeekBegan(focus))
        }
        // `carried` is the second finger that arrived to steady the tablet
        // during a drag. It must never open a menu or flip anything.
        phase == MatPhase.TWO_UNDECIDED && !carried &&
            now - secondAt >= limits.longPressMillis -> {
            phase = MatPhase.MENU
            listOf(MatEvent.MenuRequested(focus))
        }
        else -> emptyList()
    }

    private fun settle(): List<MatEvent> = when (phase) {
        MatPhase.PRESS -> when {
            // Both fingers landed and left inside one dispatched event.
            peakFingers >= 2 -> listOf(MatEvent.Flipped(focus))
            (focus - pressPoint).length <= limits.touchSlop -> listOf(MatEvent.Tapped(focus))
            else -> emptyList()
        }
        MatPhase.PEEK -> listOf(MatEvent.PeekEnded)
        MatPhase.DRAG_CARD, MatPhase.DRAG_STACK -> listOf(MatEvent.Dropped(focus, speed.value))
        MatPhase.TWO_UNDECIDED ->
            if (carried) listOf(MatEvent.Dropped(focus, speed.value)) else listOf(MatEvent.Flipped(focus))
        MatPhase.TWIST -> listOf(MatEvent.TwistCommitted(quarterTurns))
        MatPhase.MENU, MatPhase.IDLE -> emptyList()
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
        peakFingers = 0
        carried = false
        stackModifier = false
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
