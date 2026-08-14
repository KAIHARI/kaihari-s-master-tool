package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a third contact may do, and what it may not take away.
 *
 * Every gesture the mat speaks is spoken with one finger or two, and a tablet
 * held in two hands has a third contact on the glass most of the time — a palm,
 * a thumb hooked over the bezel, the hand that is only steadying it. The machine
 * used to answer all of them. A finger resting motionless was adopted the moment
 * one of the two doing the work lifted, and could then turn a card to defence; a
 * three-finger tap delivered in one batch was counted as two and flipped a card;
 * three fingers held still opened the card menu. Until there is a gesture that
 * *means* three fingers, the honest answer to the third one is silence.
 *
 * Silence about it, though, and not silence *because* of it. The first attempt
 * at these tests counted every pointer an event carried, which is a count of the
 * hand holding the tablet: a one-finger press beside it could no longer read the
 * card under it or tap it, and a drag whose owner lifted was taken away from the
 * finger still dragging it. So half of what follows is the third contact being
 * refused, and half is a gesture the user plainly made surviving it.
 */
class MatThreeFingerTest {

    private val limits = GestureThresholds()

    private class Hand(limits: GestureThresholds) {
        val machine = MatGestureMachine(limits)
        val events = mutableListOf<MatEvent>()
        var now = 1000L

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

        /** What the mat *did*. A press is only the host being asked to hit-test. */
        fun consequences(): List<MatEvent> = events.filterNot { it is MatEvent.Pressed }

        inline fun <reified T : MatEvent> has() = events.filterIsInstance<T>().isNotEmpty()
        inline fun <reified T : MatEvent> last() = events.filterIsInstance<T>().last()
    }

    private fun hand() = Hand(limits)

    private fun at(x: Float, y: Float) = Vec2(x, y)

    /** Degrees of card: half of one is far inside the detent this feeds. */
    private fun assertClose(expected: Float, actual: Float, note: String = "") {
        assertTrue(abs(expected - actual) < 0.5f, "$note expected $expected, was $actual")
    }

    // ---- a finger the gesture never asked for ----------------------------------

    @Test
    fun aRestingFingerIsNotAdoptedWhenASlotOpensMidGesture() {
        // Two fingers turning a card and a third resting motionless throughout.
        // When one of the two lifted, the gesture silently re-paired to the
        // resting finger, and the next thing that finger did — a hand shifting
        // its weight is enough — went on turning the card.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(400f, 400f))
        h.frame(1L to at(100f, 100f), 2L to at(190f, 60f), 3L to at(400f, 400f))
        assertEquals(MatPhase.TWIST, h.machine.phase)
        assertClose(-23.96f, h.machine.twist, "the turn the fingers actually made:")

        h.frame(1L to at(100f, 100f), 3L to at(400f, 400f))
        h.frame(1L to at(100f, 100f), 3L to at(400f, 200f))

        assertFalse(h.has<MatEvent.Detent>(), "a resting finger turned the card to defence")
        assertEquals(0, h.last<MatEvent.TwistCommitted>().quarterTurns)
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase, "phase was ${h.machine.phase}")
    }

    // ---- three fingers batched into one event -----------------------------------

    @Test
    fun aBatchedThreeFingerTapIsNotAFlip() {
        // The same batching that makes a two-finger tap arrive with nothing
        // pressed makes a three-finger one arrive that way, and counting *at
        // least* two turned every one of them into a flip.
        val h = hand()
        h.now += 16L
        h.events += h.machine.onFrame(
            TouchFrame(
                timeMillis = h.now,
                touches = emptyList(),
                seen = 3,
                released = listOf(
                    Touch(1L, at(100f, 100f)),
                    Touch(2L, at(180f, 100f)),
                    Touch(3L, at(260f, 100f)),
                ),
            ),
        )

        assertFalse(h.has<MatEvent.Flipped>(), "three fingers delivered whole flipped a card")
        assertEquals(MatPhase.IDLE, h.machine.phase)
    }

    @Test
    fun aThirdContactSeenDuringAPressIsNotASecondFinger() {
        // The other half of the same problem: one pointer is pressed, and the
        // count of what the event carried is the only evidence of the other two.
        // Two is the flip, and a pair is not what this event describes.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), seen = 3)
        h.up()

        assertFalse(h.has<MatEvent.Flipped>(), "a third contact flipped a card")
    }

    @Test
    fun aPressReadsACardThroughContactsOnlyTheEventCounted() {
        // The finger the user pressed is still pressed, whatever else the event
        // carried. Counting those contacts against it made the press inert for
        // as long as it lasted — no peek and no tap, with no way back inside the
        // same press, because a count of what an event carried only ever grows.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), seen = 3)
        h.tick(limits.longPressMillis + 20L)

        assertTrue(h.has<MatEvent.PeekBegan>(), "a held finger could not read the card under it")
        assertEquals(MatPhase.PEEK, h.machine.phase)
    }

    // ---- three fingers held still -------------------------------------------------

    @Test
    fun holdingThreeFingersDoesNotOpenTheCardMenu() {
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(300f, 300f))
        h.tick(limits.longPressMillis + 20L)

        assertFalse(h.has<MatEvent.MenuRequested>(), "a hand resting on the mat opened the menu")
        assertEquals(MatPhase.TWO_UNDECIDED, h.machine.phase)
    }

    // ---- silence ------------------------------------------------------------------

    @Test
    fun threeFingersCommitNothingOnReleaseAndNothingOnBeingHeld() {
        // Which is what the four sub-cases below actually establish, and all
        // they establish: every gesture the mat commits by letting go or by
        // holding still refuses to fire once a third finger is in the press,
        // and a third contact moving about during a two-finger gesture changes
        // nothing. What it does *not* cover is a three-finger sweep, which the
        // mat still reads as a stack drag — see the test below, which pins it.
        val pressed = hand()
        pressed.frame(1L to at(100f, 100f))
        pressed.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(300f, 300f))
        pressed.up()
        assertEquals(
            emptyList<MatEvent>(),
            pressed.consequences(),
            "pressing three fingers said ${pressed.consequences()}",
        )

        val dragged = hand()
        dragged.frame(1L to at(100f, 100f))
        dragged.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(300f, 300f))
        dragged.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(500f, 500f))
        dragged.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(700f, 200f))
        dragged.up()
        assertEquals(
            emptyList<MatEvent>(),
            dragged.consequences(),
            "a third finger dragged across the mat and it said ${dragged.consequences()}",
        )

        val held = hand()
        held.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(300f, 300f))
        held.tick(limits.longPressMillis + 20L)
        held.tick(limits.longPressMillis)
        held.up()
        assertEquals(
            emptyList<MatEvent>(),
            held.consequences(),
            "holding three fingers said ${held.consequences()}",
        )

        val batched = hand()
        batched.now += 16L
        batched.events += batched.machine.onFrame(
            TouchFrame(
                timeMillis = batched.now,
                touches = emptyList(),
                seen = 3,
                released = listOf(
                    Touch(1L, at(100f, 100f)),
                    Touch(2L, at(180f, 100f)),
                    Touch(3L, at(260f, 100f)),
                ),
            ),
        )
        assertEquals(
            emptyList<MatEvent>(),
            batched.consequences(),
            "a batched three-finger tap said ${batched.consequences()}",
        )
    }

    @Test
    fun aThreeFingerSweepStillTakesTheStack() {
        // The known gap, pinned so that it is a decision rather than a surprise.
        // Telling three deliberate fingers from two fingers with a palm down
        // needs to know how soon after the second finger the third one landed —
        // an arrival window, which is also the entry condition for the phase
        // that will one day *mean* three fingers. Building that twice is waste,
        // so until it exists the mat reads this as its first two fingers
        // panning and sets the card. The phase that claims three fingers has
        // to come here and change this test on purpose.
        val h = hand()
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(300f, 100f))
        h.frame(1L to at(100f, 200f), 2L to at(200f, 200f), 3L to at(300f, 200f))
        h.frame(1L to at(100f, 300f), 2L to at(200f, 300f), 3L to at(300f, 300f))
        h.up()

        assertTrue(h.has<MatEvent.LiftedSet>(), "three fingers swept and nothing moved")
        assertEquals(at(100f, 300f), h.last<MatEvent.Dropped>().at)
    }

    // ---- what a resting hand may not take away ---------------------------------------

    /**
     * A mat with a hand already resting on it, and inert.
     *
     * A gesture ran, the finger that owned it lifted, and what is left is the
     * hand that was only holding the tablet: [count] contacts the machine has
     * already refused, sitting exactly where they were. This is the state a
     * landscape tablet held in two hands spends most of its life in, so every
     * one-finger gesture has to work from here.
     */
    private fun matWithAHandRestingOnIt(count: Int): Hand {
        val h = hand()
        val resting = listOf(8L to at(600f, 600f), 9L to at(660f, 640f)).take(count)
        h.frame(1L to at(100f, 100f))
        h.frame(*(listOf(1L to at(100f, 100f)) + resting).toTypedArray())
        h.frame(*resting.toTypedArray())
        h.tick(limits.releaseGraceMillis + 16L)
        assertEquals(MatPhase.IDLE, h.machine.phase, "the resting hand kept the gesture")
        h.events.clear()
        return h
    }

    @Test
    fun aFingerPressedBesideARestingHandStillReadsTheCardUnderIt() {
        val h = matWithAHandRestingOnIt(count = 2)
        h.frame(8L to at(600f, 600f), 9L to at(660f, 640f), 2L to at(100f, 100f))
        h.tick(limits.longPressMillis + 20L)

        assertTrue(h.has<MatEvent.PeekBegan>(), "the resting hand swallowed the peek")
        assertEquals(at(100f, 100f), h.last<MatEvent.PeekBegan>().at)
    }

    @Test
    fun aFingerTappedBesideARestingHandStillTapsTheCardUnderIt() {
        val h = matWithAHandRestingOnIt(count = 2)
        h.frame(8L to at(600f, 600f), 9L to at(660f, 640f), 2L to at(100f, 100f))
        h.frame(8L to at(600f, 600f), 9L to at(660f, 640f))

        assertTrue(h.has<MatEvent.Tapped>(), "the resting hand swallowed the tap")
        assertEquals(at(100f, 100f), h.last<MatEvent.Tapped>().at)
    }

    @Test
    fun aSinglePalmIsNotTheSecondFingerOfAFlip() {
        // One contact resting rather than two, which is the same hand held a
        // little differently — and the count of what the event carried cannot
        // tell it from the second finger of a tap. Tapping a card next to it
        // turned the card face down.
        val h = matWithAHandRestingOnIt(count = 1)
        h.frame(8L to at(600f, 600f), 2L to at(100f, 100f))
        h.frame(8L to at(600f, 600f))

        assertFalse(h.has<MatEvent.Flipped>(), "a resting palm was counted as a finger")
        assertTrue(h.has<MatEvent.Tapped>(), "the tap beside a resting palm said nothing")
    }

    // ---- the finger still holding the card ------------------------------------------

    @Test
    fun theFingerLeftHoldingACardKeepsItPastTheGraceWindow() {
        // The primary lifting out of a three-finger contact opens the grace
        // window, and nothing but a reset ever closes it. The twist hands the
        // card to the finger still down — deliberately, so it does not go dead
        // — and then the window closed a seventh of a second later and dropped
        // the card out of a hand that was still dragging it.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f), 3L to at(400f, 400f))
        h.frame(1L to at(100f, 100f), 2L to at(190f, 60f), 3L to at(400f, 400f))
        assertEquals(MatPhase.TWIST, h.machine.phase)

        h.frame(2L to at(190f, 60f), 3L to at(400f, 400f))
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase, "the card went dead under the finger")

        h.frame(2L to at(190f, 200f), 3L to at(400f, 400f), after = limits.releaseGraceMillis + 16L)

        assertFalse(h.has<MatEvent.Dropped>(), "the card was dropped out of a hand dragging it")
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
        assertEquals(200f, h.machine.focus.y, "the card stopped following the finger")
    }

    @Test
    fun aSetDragLandsWhenItsOwnFingerLiftsRatherThanChangingHands() {
        // The same lift out of the same three contacts, with a card being *set*
        // instead of carried by one finger. Two fingers that start together are
        // a set now, and a set is a `DRAG_CARD` — so it obeys that phase's rule
        // and lands where the primary finger let go.
        //
        // This test used to assert the opposite, because a two-finger pan was a
        // pile drag: the pile was held by the pan of *both* fingers, so either
        // could go on carrying it, and the grace window killed it a seventh of a
        // second in. That exemption goes with the gesture. Handing a set over to
        // the survivor would teleport the card by however far apart the two
        // fingers are — a hundred mat pixels here — which is the exact hazard
        // `DRAG_CARD` lands the card to avoid.
        val h = hand()
        h.frame(1L to at(100f, 200f))
        h.frame(1L to at(100f, 200f), 2L to at(200f, 200f))
        h.frame(1L to at(100f, 200f), 2L to at(200f, 200f), 3L to at(600f, 600f))
        h.frame(1L to at(100f, 260f), 2L to at(200f, 260f), 3L to at(600f, 600f))
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
        assertTrue(h.has<MatEvent.LiftedSet>(), "two fingers panning did not set the card")

        h.frame(2L to at(200f, 320f), 3L to at(600f, 600f))

        assertEquals(
            at(100f, 260f),
            h.last<MatEvent.Dropped>().at,
            "the card did not land where the finger holding it let go",
        )
        assertEquals(MatPhase.IDLE, h.machine.phase)

        // And what is left on the glass owns nothing, third contact included.
        val settled = h.events.size
        h.frame(2L to at(200f, 380f), 3L to at(600f, 660f))
        assertEquals(settled, h.events.size, "the hand left on the mat went on dragging")
    }

    @Test
    fun aCarriedCardLandsWhereItsOwnFingerLetGoOfIt() {
        // The other way a drag can be holding two fingers: one carrying a card
        // and one steadying the tablet. Handing over would teleport the card to
        // the far side of the mat, where the steadying hand happens to rest, so
        // the card lands instead — and it lands now, at the point the user let
        // go of it, rather than a seventh of a second later wherever the other
        // hand has since dragged it to.
        val locked = hand()
        locked.frame(1L to at(100f, 100f))
        locked.frame(1L to at(100f, 200f))
        assertEquals(MatPhase.DRAG_CARD, locked.machine.phase)
        locked.frame(1L to at(100f, 200f), 2L to at(400f, 500f))
        locked.frame(1L to at(100f, 260f), 2L to at(400f, 560f))
        assertEquals(MatPhase.DRAG_CARD, locked.machine.phase, "the steadying finger took the pile")

        locked.frame(2L to at(400f, 560f))
        assertEquals(at(100f, 260f), locked.last<MatEvent.Dropped>().at)
        assertEquals(MatPhase.IDLE, locked.machine.phase)

        // The steadying finger is furniture from here on: it cannot pick the
        // card back up by moving, having never landed since the gesture ended.
        val before = locked.events.size
        locked.frame(2L to at(400f, 620f))
        locked.frame(2L to at(400f, 680f))
        assertEquals(before, locked.events.size, "the steadying finger started a gesture of its own")

        // Same lift, but before the pair had moved far enough to decide what
        // they were: still the card, still landed rather than handed over.
        val undecided = hand()
        undecided.frame(1L to at(100f, 100f))
        undecided.frame(1L to at(100f, 200f))
        undecided.frame(1L to at(100f, 200f), 2L to at(400f, 500f))
        undecided.frame(2L to at(400f, 500f))

        assertEquals(at(100f, 200f), undecided.last<MatEvent.Dropped>().at)
        assertEquals(MatPhase.IDLE, undecided.machine.phase)
    }
}
