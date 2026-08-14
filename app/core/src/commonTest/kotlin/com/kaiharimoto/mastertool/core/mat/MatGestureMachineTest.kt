package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MatGestureMachineTest {

    private val limits = GestureThresholds()

    /** A machine plus a clock, so a test reads as a sequence of moments. */
    private class Hand(limits: GestureThresholds) {
        val machine = MatGestureMachine(limits)
        var now = 1_000L
        val events = mutableListOf<MatEvent>()

        /** One frame [after] milliseconds later, with these fingers down. */
        fun frame(vararg touches: Pair<Long, Vec2>, after: Long = 16L, seen: Int = touches.size) {
            now += after
            events += machine.onFrame(
                TouchFrame(now, touches.map { Touch(it.first, it.second) }, seen),
            )
        }

        fun tick(after: Long) {
            now += after
            events += machine.onTick(now)
        }

        fun up(after: Long = 16L) = frame(after = after)

        inline fun <reified T : MatEvent> has() = events.filterIsInstance<T>().isNotEmpty()
        inline fun <reified T : MatEvent> count() = events.filterIsInstance<T>().size
        inline fun <reified T : MatEvent> last() = events.filterIsInstance<T>().last()
    }

    private fun hand() = Hand(limits)

    private fun at(x: Float, y: Float) = Vec2(x, y)

    // ---- one finger ----------------------------------------------------------

    @Test
    fun aTouchAndReleaseIsATap() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(102f, 101f))
        h.up()

        assertTrue(h.has<MatEvent.Tapped>())
        assertFalse(h.has<MatEvent.LiftedCard>())
        assertEquals(MatPhase.IDLE, h.machine.phase)
    }

    @Test
    fun movingPastTheSlopLiftsTheCard() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f + limits.touchSlop + 5f, 100f))

        assertTrue(h.has<MatEvent.LiftedCard>())
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
    }

    @Test
    fun aDragEndsInADropAndNotATap() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(200f, 100f))
        h.up()

        assertTrue(h.has<MatEvent.Dropped>())
        assertFalse(h.has<MatEvent.Tapped>())
    }

    @Test
    fun aFingerHeldStillStartsAPeek() {
        // Driven by the tick rather than by events, because a finger that is
        // not moving generates no pointer events at all. Without the tick this
        // never fires, which is the classic silent long-press bug.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.tick(limits.longPressMillis + 20L)

        assertTrue(h.has<MatEvent.PeekBegan>())
        assertEquals(MatPhase.PEEK, h.machine.phase)
    }

    @Test
    fun aPeekCanSlideIntoADragRatherThanBeingADeadEnd() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.tick(limits.longPressMillis + 20L)
        h.frame(1L to at(100f + limits.touchSlop * 3f, 100f))

        assertTrue(h.has<MatEvent.PeekEnded>())
        assertTrue(h.has<MatEvent.LiftedCard>())
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
    }

    @Test
    fun aPeekThatIsSimplyReleasedEnds() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.tick(limits.longPressMillis + 20L)
        h.up()

        assertEquals(1, h.count<MatEvent.PeekEnded>())
        assertFalse(h.has<MatEvent.Tapped>())
    }

    // ---- two fingers ----------------------------------------------------------

    @Test
    fun twoFingersDownAndUpIsAFlip() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        h.up()

        assertTrue(h.has<MatEvent.Flipped>())
    }

    @Test
    fun aBriskTwoFingerTapDeliveredAsOneEventIsStillAFlip() {
        // Android batches motion. Both fingers can land and leave inside a
        // single dispatched event, so the machine never sees two pressed
        // pointers — and counting only pressed ones loses the flip entirely.
        val h = hand()
        h.frame(1L to at(100f, 100f), seen = 2)
        h.up()

        assertTrue(h.has<MatEvent.Flipped>())
        assertFalse(h.has<MatEvent.Tapped>())
    }

    @Test
    fun holdingTwoFingersOpensTheMenu() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        h.tick(limits.longPressMillis + 20L)

        assertTrue(h.has<MatEvent.MenuRequested>())
        assertEquals(MatPhase.MENU, h.machine.phase)
    }

    @Test
    fun theTapWindowEndsExactlyWhereTheHoldBegins() {
        // One number, no dead gap: any two-finger release before the hold is a
        // flip, and at the hold the menu fires on the timer rather than on
        // release.
        val early = hand()
        early.frame(1L to at(100f, 100f))
        early.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        early.tick(limits.longPressMillis - 40L)
        early.up()
        assertTrue(early.has<MatEvent.Flipped>())
        assertFalse(early.has<MatEvent.MenuRequested>())

        val late = hand()
        late.frame(1L to at(100f, 100f))
        late.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        late.tick(limits.longPressMillis + 10L)
        late.up()
        assertTrue(late.has<MatEvent.MenuRequested>())
        assertFalse(late.has<MatEvent.Flipped>())
    }

    @Test
    fun twoFingersPanningSetsTheCardFaceDown() {
        // kai's trade, and it cost nothing. This used to take the whole pile —
        // a gesture that did exactly what one finger already did, because
        // `PlayField.moveOnMat` slides a card *and its pile*, and the flag it
        // set was written at three call sites and read at none. Setting a card
        // happens several times a turn; taking a stack somewhere a plain drag
        // could not reach happens never.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        val far = limits.touchSlop * limits.stackSlopFactor + 20f
        h.frame(1L to at(100f + far, 100f), 2L to at(180f + far, 100f))

        assertTrue(h.has<MatEvent.LiftedSet>())
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
    }

    @Test
    fun aSetIsPickedUpOnceAndFollowsTheFingerThatStartedIt() {
        // The lift is `LiftedSet` *instead of* `LiftedCard`, not as well as —
        // two lifts would put the card in the air twice — and from the frame it
        // locks the card sits under the primary finger. Left on the accumulated
        // centroid it would jump by however far the two fingers had diverged,
        // once, on the frame after the gesture locked.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        val far = limits.touchSlop * limits.stackSlopFactor + 20f
        h.frame(1L to at(100f + far, 100f), 2L to at(180f + far, 100f))

        assertEquals(1, h.count<MatEvent.LiftedSet>())
        assertEquals(0, h.count<MatEvent.LiftedCard>())
        assertEquals(at(100f + far, 100f), h.machine.focus)
    }

    @Test
    fun twoFingersTurningTwistsTheCard() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        // Rotate the second finger a quarter turn about the first.
        h.frame(1L to at(100f, 100f), 2L to at(100f, 200f))

        assertEquals(MatPhase.TWIST, h.machine.phase)
        assertTrue(abs(h.machine.twist - 90f) < 1f, "twist was ${h.machine.twist}")
    }

    @Test
    fun aTwistCommitsOnRelease() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(100f, 200f))
        h.up()

        assertEquals(1, h.machine.let { h.last<MatEvent.TwistCommitted>().quarterTurns })
    }

    @Test
    fun onceLockedATwistNeverSlidesBackIntoAPan() {
        // A twist that can re-classify feels, in the words of everyone who has
        // used one, slippery.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(140f, 190f))
        assertEquals(MatPhase.TWIST, h.machine.phase)

        // Now drag both fingers a long way; it must stay a twist.
        h.frame(1L to at(400f, 400f), 2L to at(440f, 490f))
        assertEquals(MatPhase.TWIST, h.machine.phase)
        assertFalse(h.has<MatEvent.LiftedSet>())
    }

    @Test
    fun fingersTooCloseTogetherAreNotATwist() {
        // Two fingers ten pixels apart produce tens of degrees from a pixel of
        // jitter. Every haunted-feeling twist is missing this guard.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(120f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(100f, 120f))

        assertTrue(h.machine.phase != MatPhase.TWIST, "phase was ${h.machine.phase}")
    }

    // ---- the second finger that only meant to steady the tablet ---------------

    @Test
    fun aSecondFingerArrivingMidDragDoesNotFlipAnything() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(300f, 100f))          // a committed one-finger drag
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)

        h.frame(1L to at(300f, 100f), 2L to at(380f, 100f))
        h.up()

        assertFalse(h.has<MatEvent.Flipped>(), "steadying the tablet flipped a card")
        assertTrue(h.has<MatEvent.Dropped>())
    }

    @Test
    fun aSecondFingerArrivingMidDragDoesNotOpenTheMenu() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(300f, 100f))
        h.frame(1L to at(300f, 100f), 2L to at(380f, 100f))
        h.tick(limits.longPressMillis + 50L)

        assertFalse(h.has<MatEvent.MenuRequested>())
    }

    @Test
    fun theCardDoesNotJumpWhenTheSecondFingerLands() {
        // The reason translation is the mean movement of pointers present in
        // both frames rather than a centroid difference: a centroid jumps by
        // half the finger separation the instant the second finger touches.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(300f, 100f))
        val before = h.machine.focus

        h.frame(1L to at(300f, 100f), 2L to at(500f, 100f))

        assertTrue((h.machine.focus - before).length < 1f, "focus jumped to ${h.machine.focus}")
    }

    @Test
    fun theCardDoesNotJumpWhenAFingerLifts() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(300f, 100f))
        h.frame(1L to at(140f, 100f), 2L to at(340f, 100f))
        val before = h.machine.focus

        h.frame(1L to at(140f, 100f))

        assertTrue((h.machine.focus - before).length < 1f, "focus jumped to ${h.machine.focus}")
    }

    // ---- palms ----------------------------------------------------------------

    @Test
    fun onlyTheFirstTwoFingersAreAdopted() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        // A third pointer lands and moves a long way; it must change nothing.
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(900f, 900f))
        val phase = h.machine.phase
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(20f, 20f))

        assertEquals(phase, h.machine.phase)
        assertFalse(h.has<MatEvent.LiftedSet>())
    }

    // ---- the shape of a gesture ------------------------------------------------

    @Test
    fun everyGestureEndsBackAtIdle() {
        val starts: List<(Hand) -> Unit> = listOf(
            { h -> h.frame(1L to at(100f, 100f)) },
            { h -> h.frame(1L to at(100f, 100f)); h.frame(1L to at(400f, 100f)) },
            { h ->
                h.frame(1L to at(100f, 100f))
                h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
            },
        )

        starts.forEach { start ->
            val h = hand()
            start(h)
            h.up()
            assertEquals(MatPhase.IDLE, h.machine.phase)
        }
    }

    @Test
    fun cancellingLeavesNothingBehind() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(400f, 100f))

        assertEquals(listOf(MatEvent.Cancelled), h.machine.cancel())
        assertEquals(MatPhase.IDLE, h.machine.phase)
        assertTrue(h.machine.cancel().isEmpty(), "cancelling twice is not two cancellations")
    }

    @Test
    fun aLockedGestureOwnsItsPointers() {
        // What the adapter consumes on. Undecided means "not yet ours", so an
        // ancestor can still take a scroll; locked means it cannot.
        assertFalse(MatPhase.IDLE.locked)
        assertFalse(MatPhase.PRESS.locked)
        assertFalse(MatPhase.TWO_UNDECIDED.locked)
        assertTrue(MatPhase.DRAG_CARD.locked)
        assertTrue(MatPhase.TWIST.locked)
    }

    // ---- quarter turns ----------------------------------------------------------

    @Test
    fun aTwistBecomesADefencePositionAtTheDetent() {
        val detent = limits.detentDegrees

        assertEquals(0, MatGestureMachine.quarterTurnsOf(0f, detent))
        assertEquals(0, MatGestureMachine.quarterTurnsOf(44f, detent))
        assertEquals(1, MatGestureMachine.quarterTurnsOf(46f, detent))
        assertEquals(1, MatGestureMachine.quarterTurnsOf(90f, detent))
        assertEquals(-1, MatGestureMachine.quarterTurnsOf(-46f, detent))
        assertEquals(2, MatGestureMachine.quarterTurnsOf(180f, detent))
    }

    @Test
    fun aCardSpunRoundDoesNotOweThreeTurnsOfUnwinding() {
        assertEquals(2, MatGestureMachine.quarterTurnsOf(2000f, limits.detentDegrees))
        assertEquals(-2, MatGestureMachine.quarterTurnsOf(-2000f, limits.detentDegrees))
    }

    @Test
    fun aTurnedCardKeepsTheSignItWasTurnedIn() {
        // So a card twisted anticlockwise stays there rather than springing the
        // long way round.
        assertTrue(MatGestureMachine.quarterTurnsOf(-90f, limits.detentDegrees) < 0)
    }

    // ---- the mouse speaks the same language --------------------------------------

    @Test
    fun theWheelTwistsTheSameWayFingersDo() {
        val machine = MatGestureMachine(limits)

        machine.wheel(50f)
        assertEquals(MatPhase.TWIST, machine.phase)
        assertEquals(1, machine.quarterTurns)

        val committed = machine.commitWheel()
        assertEquals(listOf(MatEvent.TwistCommitted(1)), committed)
        assertEquals(MatPhase.IDLE, machine.phase)
    }

    @Test
    fun crossingADetentAnnouncesItself() {
        val machine = MatGestureMachine(limits)

        val first = machine.wheel(50f)
        assertTrue(first.filterIsInstance<MatEvent.Detent>().isNotEmpty())

        // Still inside the same quarter turn: nothing new to say.
        val second = machine.wheel(5f)
        assertTrue(second.filterIsInstance<MatEvent.Detent>().isEmpty())
    }


    // ---- velocity ------------------------------------------------------------------

    @Test
    fun aFlickCarriesSpeedIntoTheDrop() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        repeat(6) { i -> h.frame(1L to at(100f + (i + 1) * 60f, 100f)) }
        h.up()

        val dropped = h.last<MatEvent.Dropped>()
        assertTrue(dropped.velocity.x > 100f, "velocity was ${dropped.velocity}")
    }

    @Test
    fun aSlowDragArrivesWithoutMuchSpeed() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        repeat(6) { i -> h.frame(1L to at(100f + (i + 1) * 25f, 100f), after = 100L) }
        h.up(after = 100L)

        val fast = h.last<MatEvent.Dropped>()
        assertTrue(fast.velocity.length < 400f, "velocity was ${fast.velocity}")
    }

    // ---- the first frame ------------------------------------------------------------

    @Test
    fun theFirstFrameAnnouncesThePressSoTheHostCanHitTest() {
        val h = hand()
        h.frame(1L to at(123f, 456f))

        val pressed = h.events.first()
        assertIs<MatEvent.Pressed>(pressed)
        assertEquals(at(123f, 456f), pressed.at)
    }
}
