package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a third finger is allowed to do, which today is nothing.
 *
 * Every gesture the mat speaks is spoken with one finger or two, and a tablet
 * held in two hands has a third contact on the glass most of the time — a palm,
 * a thumb hooked over the bezel, the hand that is only steadying it. The machine
 * used to answer all of them. A finger resting motionless was adopted the moment
 * one of the two doing the work lifted, and could then turn a card to defence; a
 * three-finger tap delivered in one batch was counted as two and flipped a card;
 * three fingers held still opened the card menu. Until there is a gesture that
 * *means* three fingers, the honest answer to the third one is silence, and
 * these are the tests that hold the mat to it.
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
    fun aThirdFingerSeenDuringAPressIsNeitherAFlipNorATap() {
        // The other half of the same problem: one pointer is pressed, and the
        // count of what the event carried is the only evidence of the other two.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), seen = 3)
        h.up()

        assertFalse(h.has<MatEvent.Flipped>(), "a third finger flipped a card")
        assertFalse(h.has<MatEvent.Tapped>(), "a third finger tapped one")
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
    fun threeFingersOnTheMatAreInert() {
        // Inert means the third contact cannot make the mat do anything, in any
        // phase, however it is used. It does not mean the machine can tell a
        // deliberate three-finger sweep from two fingers panning with a palm
        // down — nothing here does, and the count of pointers an event carried
        // cannot be made to, because a resting palm inflates it and gating a
        // live drag or twist on it kills the gesture the other two fingers are
        // visibly making. That distinction is the third phase's to draw.
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
        assertClose(200f, h.machine.focus.y, "the card stopped following the finger:")
    }
}
