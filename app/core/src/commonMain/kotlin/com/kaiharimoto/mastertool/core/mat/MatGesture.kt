package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2

/** One pressed pointer, in the mat's own coordinates. */
data class Touch(val id: Long, val at: Vec2)

/**
 * Everything the arbiter knows at one instant.
 *
 * [seen] counts every pointer in the delivering event, *including ones that
 * went up in it*. Android batches motion: a brisk two-finger tap can arrive as
 * a single dispatched event in which both fingers are already released, and
 * without [seen] that gesture is indistinguishable from one slow finger — the
 * flip is silently lost and the user is told to tap more slowly.
 */
data class TouchFrame(
    val timeMillis: Long,
    val touches: List<Touch>,
    val seen: Int = touches.size,
    /**
     * Where pointers that went up *in this event* were when they left.
     *
     * The other half of the batching problem. Knowing that two fingers were
     * seen is no use for a flip if there is no position to flip at, and a
     * fully batched two-finger tap arrives with nothing pressed at all.
     */
    val released: List<Touch> = emptyList(),
)

/** Every number a gesture is judged against. Mat pixels, degrees, milliseconds. */
data class GestureThresholds(
    val touchSlop: Float = 18f,
    /** A two-finger pan should be surer of itself than a one-finger one. */
    val stackSlopFactor: Float = 1.6f,
    val twistSlopDegrees: Float = 7f,
    /**
     * Below this finger separation, rotation is noise.
     *
     * Two fingers ten pixels apart generate tens of degrees from one pixel of
     * jitter. Every twist gesture that feels haunted is missing this guard.
     */
    val minTwistSpan: Float = 64f,
    val longPressMillis: Long = 420L,
    /**
     * How long the gesture waits for the rest of the hand after its first
     * finger lifts.
     *
     * The two fingers of a tap do not leave in the same frame. Long enough that
     * a deliberate two-finger tap always reads as one; short enough that a palm
     * left on the mat cannot hold a gesture open behind the user's back.
     */
    val releaseGraceMillis: Long = 140L,
    /** Where a live twist changes its mind between attack and defence. */
    val detentDegrees: Float = 45f,
)

enum class MatPhase {
    IDLE,
    PRESS,
    PEEK,
    DRAG_CARD,
    TWO_UNDECIDED,
    DRAG_STACK,
    TWIST,
    MENU,

    /**
     * The fingers landed on bare felt, so they are moving the camera.
     *
     * Claimed by the host on the press — see [MatGestureMachine.claimForCamera]
     * — because what is under a finger is a question about the board and this
     * file is deliberately ignorant of boards. Once claimed, none of the card
     * gestures can start: a hand sweeping the table is not going to
     * accidentally set a card, and the peek timer never runs.
     */
    CAMERA,
    ;

    /** Locked means the gesture is ours, and every pointer gets consumed. */
    val locked: Boolean get() = this != IDLE && this != PRESS && this != TWO_UNDECIDED
}

/**
 * What the mat is being told, by a finger or by a mouse or by a key.
 *
 * This is the app's gesture vocabulary. Touch and pointer are two producers of
 * one language rather than two parallel implementations — which is how the
 * standing "every gesture ships with both idioms" rule is kept structurally
 * instead of by discipline. There is exactly one code path that flips a card,
 * and a mouse cannot reach one a finger cannot.
 */
sealed interface MatEvent {
    /** A finger landed. The host hit-tests here and remembers what it grabbed. */
    data class Pressed(val at: Vec2) : MatEvent
    data class Tapped(val at: Vec2) : MatEvent

    /** Held still with one finger: show the card big without moving it. */
    data class PeekBegan(val at: Vec2) : MatEvent
    data object PeekEnded : MatEvent

    data class LiftedCard(val at: Vec2) : MatEvent
    data class LiftedStack(val at: Vec2) : MatEvent
    data class Moved(val at: Vec2, val delta: Vec2, val velocity: Vec2) : MatEvent
    data class Dropped(val at: Vec2, val velocity: Vec2) : MatEvent

    data class Flipped(val at: Vec2) : MatEvent

    /**
     * A carried card held still long enough to mean "push it under this one".
     *
     * The four two-finger gestures are all spoken for — tap, twist, drag and
     * hold — so the only channel left during a one-finger drag is time. It is
     * also the honest one: tucking a card underneath another is the slow,
     * deliberate version of dropping it on top, and doing it by pausing reads
     * that way. Moving again undoes it, which is why there is no matching
     * "stopped dwelling" event: [Moved] already says so.
     */
    data class Dwelled(val at: Vec2) : MatEvent

    /** Live and uncommitted: [degrees] is what to draw, [quarterTurns] what it means. */
    data class Twisting(val degrees: Float, val quarterTurns: Int) : MatEvent

    /** Crossed a detent: tick the haptics and swap the attack/defence indicator. */
    data class Detent(val quarterTurns: Int) : MatEvent
    data class TwistCommitted(val quarterTurns: Int) : MatEvent

    data class MenuRequested(val at: Vec2) : MatEvent
    data object Cancelled : MatEvent

    /**
     * The fingers on the felt want the camera somewhere else.
     *
     * It carries a count and no distances, and that omission is the design. The
     * arbiter works in the mat's own coordinates, and the mat's own coordinates
     * are being turned by the very camera this gesture is turning — drive a yaw
     * from them and the table curves away under your finger, accelerating,
     * because every degree it turns changes what the next frame's delta means.
     * What an orbit wants is the glass, which the host has and this does not.
     *
     * So the arbiter says *that* the camera is being moved and with how many
     * fingers — one is an orbit, two is an orbit and a pinch — and the host
     * says by how much.
     */
    data class CameraMoved(val fingers: Int) : MatEvent

    /** The hand left the felt. Nothing to commit; the camera is already there. */
    data object CameraEnded : MatEvent
}
