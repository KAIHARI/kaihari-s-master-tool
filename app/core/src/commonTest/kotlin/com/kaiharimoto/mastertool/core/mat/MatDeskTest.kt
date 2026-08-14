package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two hands on one table, and the one-handed table still behaving.
 *
 * The board here is two cards and a lot of felt, named with strings — the router
 * compares tokens with `==` and has no idea what a card is, which is what lets
 * the whole of two-handed play be tested without a screen.
 *
 * ```
 *        0     100    200    300    400    500    600
 *   100  ·······[ A ]·······················[ B ]·······
 * ```
 */
class MatDeskTest {

    private val limits = GestureThresholds()

    /** Card A around x=100, card B around x=500, felt everywhere else. */
    private fun board(): (Vec2) -> String? = { at ->
        when {
            (at - Vec2(100f, 100f)).length < 40f -> "A"
            (at - Vec2(500f, 100f)).length < 40f -> "B"
            else -> null
        }
    }

    private class Desk(hit: (Vec2) -> String?, limits: GestureThresholds) {
        val desk = MatDesk(hit, limits)
        var now = 1_000L
        val events = mutableListOf<LaneEvent<String>>()

        fun frame(vararg touches: Pair<Long, Vec2>, after: Long = 16L, seen: Int = touches.size) {
            now += after
            events += desk.onFrame(
                TouchFrame(now, touches.map { Touch(it.first, it.second) }, seen),
            )
        }

        fun released(vararg touches: Pair<Long, Vec2>, after: Long = 16L, seen: Int = touches.size) {
            now += after
            events += desk.onFrame(
                TouchFrame(
                    timeMillis = now,
                    touches = emptyList(),
                    seen = seen,
                    released = touches.map { Touch(it.first, it.second) },
                ),
            )
        }

        fun tick(after: Long) {
            now += after
            events += desk.onTick(now)
        }

        fun up(after: Long = 16L) = frame(after = after)

        /** Every lane that said this, in the order they first said it. */
        inline fun <reified T : MatEvent> lanesSaying(): List<Int> =
            events.filter { it.event is T }.map { it.lane }.distinct()

        inline fun <reified T : MatEvent> count() = events.count { it.event is T }

        fun tokensSaying(): List<String?> =
            events.filter { it.event is MatEvent.LiftedCard }.map { it.token }
    }

    private fun desk() = Desk(board(), limits)

    private fun at(x: Float, y: Float) = Vec2(x, y)

    // ---- what kai asked for ----------------------------------------------------

    @Test
    fun oneFingerOnEachOfTwoCardsIsTwoDrags() {
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        // Both hands move at once, which is the whole point of the feature.
        d.frame(1L to at(100f, 300f), 2L to at(500f, 300f))

        assertEquals(
            listOf(0, 1),
            d.lanesSaying<MatEvent.LiftedCard>(),
            "two cards were dragged and only one lane picked anything up",
        )
        assertEquals(listOf("A", "B"), d.tokensSaying(), "the lanes grabbed the wrong cards")
    }

    @Test
    fun eachHandDropsItsOwnCardWhereItLetGo() {
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        d.frame(1L to at(100f, 300f), 2L to at(500f, 300f))

        // The right hand lets go first, and the left carries on alone.
        d.frame(1L to at(100f, 400f))
        assertEquals(1, d.count<MatEvent.Dropped>(), "the second hand did not land its card")

        d.up()
        assertEquals(2, d.count<MatEvent.Dropped>(), "the first hand did not land its card")
    }

    @Test
    fun aSecondHandDoesNotDisturbTheFirstOnesGesture() {
        // The failure this is really about: one lane's events leaking into
        // another's. The left hand is mid-drag; the right hand lands, drags and
        // lifts entirely inside that, and the left must not notice.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 300f))

        d.frame(1L to at(100f, 300f), 2L to at(500f, 100f))
        d.frame(1L to at(100f, 300f), 2L to at(500f, 300f))
        d.frame(1L to at(100f, 300f))

        assertEquals(MatPhase.DRAG_CARD, d.desk.phaseOf(0), "the first hand lost its card")
        assertEquals(at(100f, 300f), d.desk.focusOf(0), "the first hand's card moved on its own")
    }

    // ---- and what must not become two-handed -----------------------------------

    @Test
    fun twoFingersOnOneCardIsOneGesture() {
        // A twist, a menu and a set are all two fingers on one card, and every
        // one of them would be broken by a second lane opening under the second
        // finger. The tokens are equal, so there is nothing to open.
        val d = desk()
        d.frame(1L to at(90f, 100f))
        d.frame(1L to at(90f, 100f), 2L to at(115f, 100f))

        assertEquals(MatPhase.TWO_UNDECIDED, d.desk.phaseOf(0))
        assertEquals(MatPhase.IDLE, d.desk.phaseOf(1), "a second lane opened on one card")
    }

    @Test
    fun aSteadyingFingerOnBareFeltJoinsTheGestureItIsBeside() {
        // The second finger of every two-finger gesture, in practice: it lands
        // *next* to the card rather than on it. Bare felt is nothing, so the
        // nearest gesture takes it — and a card already in the air must not find
        // a second drag starting beside it.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 250f))
        d.frame(1L to at(100f, 250f), 2L to at(180f, 250f))
        d.frame(1L to at(100f, 350f), 2L to at(180f, 350f))

        assertEquals(MatPhase.IDLE, d.desk.phaseOf(1), "the steadying hand started its own drag")
        assertEquals(1, d.count<MatEvent.LiftedCard>(), "the card was picked up twice")
    }

    @Test
    fun aSecondFingerOnFeltJoinsTheCameraRatherThanDoublingIt() {
        // A pinch is two fingers doing the one thing the gesture already is, and
        // there is no arrangement in which two independent cameras would mean
        // anything at all.
        val d = desk()
        d.frame(1L to at(300f, 400f))
        d.desk.claimForCamera(0)
        d.frame(1L to at(300f, 400f), 2L to at(360f, 400f))
        d.frame(1L to at(280f, 400f), 2L to at(380f, 400f))

        assertEquals(MatPhase.CAMERA, d.desk.phaseOf(0))
        assertEquals(MatPhase.IDLE, d.desk.phaseOf(1), "the pinch opened a second camera")
        assertTrue(d.events.filter { it.event is MatEvent.CameraMoved }.all { it.lane == 0 })
    }

    @Test
    fun aThirdFingerJoinsAGestureRatherThanOpeningOne() {
        // Three fingers is a hand being put down, and every rule about that
        // lives inside the machine. The router's job is only to make sure the
        // third contact reaches one, so that it can be ignored there.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f), 3L to at(300f, 600f))

        assertEquals(MatPhase.IDLE, d.desk.phaseOf(2), "a third lane opened")
    }

    // ---- the one-handed table, unchanged ---------------------------------------

    @Test
    fun oneFingerStillTapsAndOneFingerStillDrags() {
        val tapped = desk()
        tapped.frame(1L to at(100f, 100f))
        tapped.frame(1L to at(102f, 101f))
        tapped.up()
        assertEquals(1, tapped.count<MatEvent.Tapped>())
        assertEquals(0, tapped.count<MatEvent.LiftedCard>())

        val dragged = desk()
        dragged.frame(1L to at(100f, 100f))
        dragged.frame(1L to at(100f, 400f))
        dragged.up()
        assertEquals(1, dragged.count<MatEvent.LiftedCard>())
        assertEquals(1, dragged.count<MatEvent.Dropped>())
        assertEquals(0, dragged.count<MatEvent.Tapped>())
    }

    @Test
    fun aHeldFingerStillPeeksOnTheClock() {
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.tick(limits.longPressMillis + 20L)

        assertEquals(listOf(0), d.lanesSaying<MatEvent.PeekBegan>())
        assertEquals("A", d.events.first { it.event is MatEvent.PeekBegan }.token)
    }

    @Test
    fun eachHandGetsItsOwnClock() {
        // The reason the tick goes to every lane. Two fingers held still on two
        // cards are two people reading two cards, and a clock that only ever ran
        // for the first gesture would leave the second inert.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        d.tick(limits.longPressMillis + 20L)

        assertEquals(listOf(0, 1), d.lanesSaying<MatEvent.PeekBegan>())
    }

    @Test
    fun aFullyBatchedTwoFingerTapIsStillAFlip() {
        // Android batches hard enough that a brisk two-finger tap arrives as one
        // event with both fingers already gone. There is no landing to make a
        // lane from, so the release itself has to open one.
        val d = desk()
        d.released(1L to at(100f, 100f), 2L to at(130f, 100f), seen = 2)

        assertEquals(1, d.count<MatEvent.Flipped>())
        assertEquals("A", d.events.first { it.event is MatEvent.Flipped }.token)
    }

    @Test
    fun aRestingFingerCannotStartTheNextGesture() {
        // The furniture rule, one level up from the machine's own. A gesture
        // only ever starts on a *fresh* landing, so a hand left on the mat when
        // its lane ended is unroutable until it lifts and lands again.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 400f))
        // The finger that lifted the card goes; a palm stays where it landed.
        d.frame(1L to at(100f, 400f), 2L to at(600f, 600f))
        d.frame(2L to at(600f, 600f))

        val settled = d.events.size
        d.frame(2L to at(600f, 700f))
        d.frame(2L to at(600f, 800f))

        assertEquals(settled, d.events.size, "the resting hand started a gesture")
    }

    @Test
    fun cancellingTakesEveryHandWithIt() {
        val d = desk()
        d.frame(1L to at(100f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        d.frame(1L to at(100f, 300f), 2L to at(500f, 300f))

        val cancelled = d.desk.cancel()

        assertEquals(listOf(0, 1), cancelled.map { it.lane })
        assertTrue(cancelled.all { it.event is MatEvent.Cancelled })
        assertFalse(d.desk.locked)
    }

    @Test
    fun theDeskIsLockedWhileEitherHandOwnsItsPointers() {
        // What the adapter consumes on, and it has to be either rather than the
        // first: a drag in the right hand must stop an ancestor stealing the
        // pointers while the left is merely pressed.
        val d = desk()
        d.frame(1L to at(100f, 100f))
        assertFalse(d.desk.locked, "a bare press is not yet ours")

        d.frame(1L to at(100f, 100f), 2L to at(500f, 100f))
        d.frame(1L to at(100f, 100f), 2L to at(500f, 400f))
        assertTrue(d.desk.locked)
    }
}
