package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cases the machine got wrong.
 *
 * Every test here began life as a bug, found by an adversarial pass over the
 * state machine *after* its own suite was already green — which is the whole
 * argument for the machine being core code rather than something written inline
 * in a composable. Each of these would otherwise have been discovered by
 * someone holding a tablet, one release at a time, if at all: a steadying
 * finger silently grabbing a whole pile, a card going dead under your thumb,
 * a resting palm inheriting a gesture, a flip lost to event batching, and a
 * scroll wheel leaving the mat unable to accept a touch.
 */
class MatGestureEdgeTest {

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

        inline fun <reified T : MatEvent> has() = events.filterIsInstance<T>().isNotEmpty()
    }

    private fun at(x: Float, y: Float) = Vec2(x, y)

    @Test
    fun aSteadyingFingerDoesNotGrabTheWholePile() {
        // Put a second finger down mid-drag to brace the tablet, keep dragging
        // as anyone would, and the card you were carrying must still be the
        // only thing you are carrying.
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 140f))
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)

        h.frame(1L to at(100f, 140f), 2L to at(200f, 140f))
        h.frame(1L to at(100f, 190f), 2L to at(200f, 190f))
        h.frame(1L to at(100f, 240f), 2L to at(200f, 240f))

        assertFalse(h.has<MatEvent.LiftedStack>(), "a carried drag became a stack drag")
    }

    @Test
    fun liftingOneFingerOutOfATwistDoesNotKillTheCard() {
        // The turn is finished, but the other finger is still holding the card.
        // It used to go dead underneath it.
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(200f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(190f, 60f))
        assertEquals(MatPhase.TWIST, h.machine.phase)

        val before = h.machine.focus
        h.frame(1L to at(300f, 400f))
        h.frame(1L to at(400f, 500f))

        assertTrue(h.has<MatEvent.TwistCommitted>(), "the turn should commit when a finger goes")
        assertTrue(
            (h.machine.focus - before).length > 100f,
            "the card stopped following the finger still holding it",
        )
    }

    @Test
    fun aPalmThatOutlivesTheFingersInheritsNothing() {
        // A palm resting on the glass when the gesture ends is furniture, not
        // the start of the next one.
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 9L to at(600f, 600f))
        h.frame(9L to at(600f, 600f))

        assertFalse(h.has<MatEvent.Flipped>(), "the palm flipped a card")
        assertFalse(
            h.events.filterIsInstance<MatEvent.Pressed>().any { it.at.x > 500f },
            "the palm started a gesture of its own",
        )
    }

    @Test
    fun aTwoFingerTapDeliveredWholeIsStillAFlip() {
        // Android batches motion hard enough that both fingers can land and
        // leave inside one dispatched event, with nothing pressed by the time
        // it arrives. The positions then have to come from where they left.
        val h = Hand(limits)
        h.now += 16L
        h.events += h.machine.onFrame(
            TouchFrame(
                timeMillis = h.now,
                touches = emptyList(),
                seen = 2,
                released = listOf(Touch(1L, at(100f, 100f)), Touch(2L, at(180f, 100f))),
            ),
        )

        assertTrue(h.has<MatEvent.Flipped>(), "the batched tap emitted nothing")
    }

    @Test
    fun aWheelTwistDoesNotLatchTheMatAgainstTouch() {
        // The wheel has no "up", so its twist stays live until something
        // commits it. A finger arriving is that something.
        val h = Hand(limits)
        h.machine.wheel(30f)
        assertEquals(MatPhase.TWIST, h.machine.phase)

        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 200f))

        assertTrue(h.has<MatEvent.LiftedCard>(), "a finger after a wheel tick could not drag")
    }

    @Test
    fun holdingACarriedCardStillMeansTuckItUnder() {
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 160f))
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
        assertFalse(h.has<MatEvent.Dwelled>(), "the dwell fired the moment the drag began")

        // Stop. The clock, not the pointer, is what notices.
        h.events += h.machine.onTick(h.now + limits.longPressMillis + 1L)
        assertTrue(h.has<MatEvent.Dwelled>(), "holding still over a card said nothing")

        val once = h.events.filterIsInstance<MatEvent.Dwelled>().size
        h.events += h.machine.onTick(h.now + limits.longPressMillis * 3L)
        assertEquals(once, h.events.filterIsInstance<MatEvent.Dwelled>().size, "the dwell repeated")
    }

    @Test
    fun movingAgainTakesTheDwellBack() {
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 160f))
        h.events += h.machine.onTick(h.now + limits.longPressMillis + 1L)
        val after = h.events.filterIsInstance<MatEvent.Dwelled>().size

        // Move off, then stop somewhere else: that is a second decision, and it
        // has to be able to happen. Sticking at one dwell per drag would mean a
        // card could only ever be tucked under the first thing it paused over.
        h.now += limits.longPressMillis + 1L
        h.frame(1L to at(300f, 400f))
        h.events += h.machine.onTick(h.now + limits.longPressMillis + 1L)

        assertEquals(
            after + 1,
            h.events.filterIsInstance<MatEvent.Dwelled>().size,
            "pausing somewhere new did not count",
        )
    }

    @Test
    fun aTwoFingerTapWhoseFingersLeaveAFrameApartIsStillAFlip() {
        // Two fingers never lift on the same frame. If the gesture ended on the
        // first one, the flip would only fire when the OS happened to batch
        // them together — which is the same bug as never firing at all.
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 2L to at(180f, 100f))
        h.frame(2L to at(180f, 100f))
        h.frame()

        assertTrue(h.has<MatEvent.Flipped>(), "a staggered two-finger tap did not flip")
    }

    @Test
    fun aHandLeftOnTheMatLosesTheGestureItNeverFinished() {
        // The same shape as the tap above, distinguished only by never letting
        // go. Once the grace window closes the gesture is taken away, and what
        // is still on the glass cannot start the next one either.
        val h = Hand(limits)
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f, 100f), 9L to at(600f, 600f))
        h.frame(9L to at(600f, 600f))
        val after = h.events.size

        // Past the grace window, and then some.
        h.frame(9L to at(600f, 600f), after = limits.releaseGraceMillis + 16L)
        assertEquals(MatPhase.IDLE, h.machine.phase)
        assertFalse(h.has<MatEvent.Flipped>(), "the resting hand claimed a flip")

        h.frame(9L to at(610f, 600f))
        h.frame(9L to at(620f, 600f))
        assertEquals(after, h.events.size, "a resting palm produced events")
        assertEquals(MatPhase.IDLE, h.machine.phase)
    }
}
