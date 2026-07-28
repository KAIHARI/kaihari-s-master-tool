package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DealAnimationTest {

    private val deck = 40

    @Test
    fun theFirstCardLeavesImmediately() {
        assertEquals(0f, DealAnimation.progressFor(0, deck, 0f), 0.001f)
        assertTrue(DealAnimation.progressFor(0, deck, 0.05f) > 0f)
    }

    @Test
    fun everyCardHasLandedByTheEnd() {
        // Whatever the stagger, the run must finish with nothing still moving --
        // otherwise the last cards snap into place when the animation stops.
        (0 until deck).forEach { index ->
            assertEquals(
                1f,
                DealAnimation.progressFor(index, deck, 1f),
                0.001f,
                "card $index was still in the air",
            )
        }
    }

    @Test
    fun nothingHasMovedBeforeItStarts() {
        (0 until deck).forEach { index ->
            assertEquals(0f, DealAnimation.progressFor(index, deck, 0f), 0.001f)
        }
    }

    @Test
    fun theLastCardIsAlwaysBehindTheFirst() {
        // What makes it a deal rather than a fade.
        listOf(0.2f, 0.5f, 0.8f).forEach { overall ->
            val first = DealAnimation.progressFor(0, deck, overall)
            val last = DealAnimation.progressFor(deck - 1, deck, overall)
            assertTrue(last < first, "at $overall the last card was not behind")
        }
    }

    @Test
    fun severalCardsAreInTheAirAtOnce() {
        // A stagger that only ever had one card moving would read as a ripple,
        // not as dealing.
        val moving = (0 until deck).count { index ->
            DealAnimation.progressFor(index, deck, 0.5f) in 0.001f..0.999f
        }

        assertTrue(moving > 3, "only $moving cards were in flight")
    }

    @Test
    fun aCardOnlyEverMovesForwards() {
        (0 until deck step 7).forEach { index ->
            var previous = -1f
            (0..40).forEach { step ->
                val progress = DealAnimation.progressFor(index, deck, step / 40f)
                assertTrue(progress >= previous, "card $index went backwards")
                previous = progress
            }
        }
    }

    @Test
    fun aSingleCardStillAnimates() {
        // Dividing by count - 1 is zero here, which is exactly where a stagger
        // implementation divides by zero.
        assertEquals(0f, DealAnimation.progressFor(0, 1, 0f), 0.001f)
        assertEquals(1f, DealAnimation.progressFor(0, 1, 1f), 0.001f)
        assertTrue(DealAnimation.progressFor(0, 1, 0.5f) > 0f)
    }

    @Test
    fun anEmptySectionAsksNothing() {
        assertEquals(1f, DealAnimation.progressFor(0, 0, 0f), 0.001f)
    }

    @Test
    fun aCardStartsAboveItsPlaceAndArrivesOnIt() {
        // Dropped, not faded in: a card that arrives by becoming opaque has not
        // come from anywhere.
        assertEquals(DealAnimation.RISE, DealAnimation.riseFor(0f), 0.001f)
        assertEquals(0f, DealAnimation.riseFor(1f), 0.001f)
        assertTrue(DealAnimation.riseFor(0.5f) < DealAnimation.riseFor(0.2f))
    }

    @Test
    fun progressOutsideTheRunIsClamped() {
        // The clock is not guaranteed to stop exactly at 1.
        assertEquals(1f, DealAnimation.progressFor(5, deck, 4f), 0.001f)
        assertEquals(0f, DealAnimation.progressFor(5, deck, -2f), 0.001f)
        assertEquals(0f, DealAnimation.riseFor(3f), 0.001f)
    }
}
