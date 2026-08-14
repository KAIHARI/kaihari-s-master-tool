package com.kaiharimoto.mastertool.core.mat

import com.kaiharimoto.mastertool.core.motion.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The felt is the camera's, and a card is nobody else's.
 *
 * The whole control rework is one sentence — *fingers on a card move the card,
 * fingers on the felt move the camera* — and the arbiter is where that sentence
 * is either true or a slogan. What is being pinned down here is the two halves
 * of it separately: once the camera has claimed a gesture nothing can turn it
 * back into a card gesture, and a claim can never be made over a gesture that
 * already has a card in the air.
 */
class MatCameraGestureTest {

    private val limits = GestureThresholds()

    private class Hand(limits: GestureThresholds) {
        val machine = MatGestureMachine(limits)
        var now = 1_000L
        val events = mutableListOf<MatEvent>()

        fun frame(vararg touches: Pair<Long, Vec2>, after: Long = 16L) {
            now += after
            events += machine.onFrame(
                TouchFrame(now, touches.map { Touch(it.first, it.second) }),
            )
        }

        fun tick(after: Long) {
            now += after
            events += machine.onTick(now)
        }

        fun up(after: Long = 16L) = frame(after = after)

        /** Press, then claim it for the camera the way the host does. */
        fun pressOnFelt(at: Vec2) {
            frame(1L to at)
            machine.claimForCamera()
        }

        inline fun <reified T : MatEvent> has() = events.filterIsInstance<T>().isNotEmpty()
        inline fun <reified T : MatEvent> last() = events.filterIsInstance<T>().last()
    }

    private fun hand() = Hand(limits)

    private fun at(x: Float, y: Float) = Vec2(x, y)

    // ---- claiming --------------------------------------------------------------

    @Test
    fun aPressOnBareFeltBecomesACameraGesture() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))

        assertEquals(MatPhase.CAMERA, h.machine.phase)
    }

    @Test
    fun aCameraGestureReportsEveryFrameWithHowManyFingersAreOnTheGlass() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.frame(1L to at(430f, 400f))

        assertEquals(1, h.last<MatEvent.CameraMoved>().fingers)

        h.frame(1L to at(430f, 400f), 2L to at(560f, 400f))
        h.frame(1L to at(410f, 400f), 2L to at(580f, 400f))

        assertEquals(2, h.last<MatEvent.CameraMoved>().fingers)
    }

    @Test
    fun aSecondFingerDoesNotSendTheCameraOffToDecideBetweenATwistAndAPile() {
        // The bug this guards is silent rather than loud: without CAMERA on the
        // hadTwo list the pinch would arrive as TWO_UNDECIDED, lock into a set
        // against a card that is not there, and the table would simply
        // stop responding to the second finger.
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.frame(1L to at(400f, 400f), 2L to at(600f, 400f))
        h.frame(1L to at(340f, 400f), 2L to at(660f, 400f))

        assertEquals(MatPhase.CAMERA, h.machine.phase)
        assertFalse(h.has<MatEvent.LiftedSet>())
        assertFalse(h.has<MatEvent.Twisting>())
    }

    // ---- what it refuses to become ---------------------------------------------

    @Test
    fun aHandSweepingTheFeltNeverPicksUpACard() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        repeat(6) { step -> h.frame(1L to at(400f + step * 40f, 400f + step * 12f)) }
        h.up()

        assertFalse(h.has<MatEvent.LiftedCard>())
        assertFalse(h.has<MatEvent.Moved>())
        assertFalse(h.has<MatEvent.Dropped>())
        assertTrue(h.has<MatEvent.CameraEnded>())
    }

    @Test
    fun aFingerRestingOnTheFeltIsNotAPeek() {
        // A peek is a question about a card, and there is no card here. Held
        // still on the felt used to run the press timer and fire PeekBegan at
        // the host, which then had nothing to peek at and did nothing — an
        // invisible failure, but it also meant the gesture was over: the phase
        // had left PRESS and a sweep from there could no longer orbit.
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.tick(limits.longPressMillis + 50L)

        assertFalse(h.has<MatEvent.PeekBegan>())
        assertEquals(MatPhase.CAMERA, h.machine.phase)
    }

    @Test
    fun aTapOnTheFeltCommitsNothing() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.up()

        assertFalse(h.has<MatEvent.Tapped>())
        assertEquals(MatPhase.IDLE, h.machine.phase)
    }

    @Test
    fun theCameraCannotBeClaimedOutOfAGestureThatIsAlreadyCarryingACard() {
        // The claim is made on the press, by which time the host has hit-tested.
        // It is guarded anyway, because "only ever called at the right moment"
        // is not a property — it is a promise, and this is the one thing whose
        // breaking would take a card out of somebody's hand mid-drag.
        val h = hand()
        h.frame(1L to at(100f, 100f))
        h.frame(1L to at(100f + limits.touchSlop + 5f, 100f))

        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
        h.machine.claimForCamera()
        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
    }

    // ---- letting go ------------------------------------------------------------

    @Test
    fun oneFingerOfAPinchLiftingLeavesTheOtherStillMovingTheCamera() {
        // A pinch is held by both fingers, so either may carry on alone as an
        // orbit — the same rule the pile drag and the twist already obey, and
        // for the same reason: the grace window is for gestures that are still
        // waiting to find out what they are, and this one knows.
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.frame(1L to at(400f, 400f), 2L to at(600f, 400f))
        h.frame(2L to at(620f, 400f))
        h.frame(2L to at(640f, 400f))

        assertEquals(MatPhase.CAMERA, h.machine.phase)
        assertEquals(1, h.last<MatEvent.CameraMoved>().fingers)
        assertFalse(h.has<MatEvent.CameraEnded>())
    }

    @Test
    fun theLastFingerLeavingEndsIt() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.frame(1L to at(450f, 400f))
        h.up()

        assertTrue(h.has<MatEvent.CameraEnded>())
        assertEquals(MatPhase.IDLE, h.machine.phase)
    }

    @Test
    fun theNextPressStartsCleanAndCanStillTakeACard() {
        val h = hand()
        h.pressOnFelt(at(400f, 400f))
        h.frame(1L to at(500f, 430f))
        h.up()

        // A fresh finger, landing on a card this time. The id is new because a
        // pointer that was down when the last gesture ended is furniture.
        h.frame(2L to at(200f, 200f))
        h.frame(2L to at(200f + limits.touchSlop + 5f, 200f))

        assertEquals(MatPhase.DRAG_CARD, h.machine.phase)
        assertTrue(h.has<MatEvent.LiftedCard>())
    }
}
