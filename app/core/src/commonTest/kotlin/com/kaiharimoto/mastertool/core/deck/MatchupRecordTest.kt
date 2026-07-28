package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchupRecordTest {

    /** [bricks] bad hands out of [of], on the named side. */
    private fun MatchupRecord.run(wentFirst: Boolean, bricks: Int, of: Int): MatchupRecord =
        (1..of).fold(this) { record, n -> record.judged(wentFirst, playable = n > bricks) }

    @Test
    fun theTwoSidesAreNeverMixed() {
        val record = MatchupRecord().run(wentFirst = true, bricks = 3, of = 10)

        assertEquals(10, record.goingFirst.total)
        assertEquals(0, record.goingSecond.total, "a hand on one side is not evidence about the other")
        assertEquals(10, record.total)
    }

    @Test
    fun eachSideKeepsItsOwnRate() {
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 4, of = 10)
            .run(wentFirst = false, bricks = 1, of = 10)

        assertEquals(0.4, record.goingFirst.brickRate!!, 0.0001)
        assertEquals(0.1, record.goingSecond.brickRate!!, 0.0001)
    }

    @Test
    fun theBetterSideIsTheOneThatBricksLess() {
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 4, of = 10)
            .run(wentFirst = false, bricks = 1, of = 10)

        assertEquals(Preference.SECOND, record.preference())
    }

    @Test
    fun itSaysFirstWhenFirstIsBetter() {
        // The answer that is not the default matters most, but the default has
        // to be reachable too or the feature only ever surprises.
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 1, of = 10)
            .run(wentFirst = false, bricks = 5, of = 10)

        assertEquals(Preference.FIRST, record.preference())
    }

    @Test
    fun tooFewHandsIsNoAnswerAtAll() {
        // The case a naive comparison gets wrong: two hands each is not
        // evidence, and answering anyway means confidently recommending going
        // second off one unlucky opening.
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 2, of = 2)
            .run(wentFirst = false, bricks = 0, of = 2)

        assertNull(record.preference())
    }

    @Test
    fun oneSideBeingUntestedIsAlsoNoAnswer() {
        val record = MatchupRecord().run(wentFirst = true, bricks = 0, of = 40)

        assertNull(record.preference())
    }

    @Test
    fun aTieIsNotAPreference() {
        // "It does not matter" is a real answer, and dressing it as a preference
        // would be inventing one.
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 3, of = 10)
            .run(wentFirst = false, bricks = 3, of = 10)

        assertNull(record.preference())
    }

    @Test
    fun theThresholdIsTheCallersToMove() {
        val record = MatchupRecord()
            .run(wentFirst = true, bricks = 0, of = 2)
            .run(wentFirst = false, bricks = 2, of = 2)

        assertNull(record.preference())
        assertEquals(Preference.FIRST, record.preference(minimumEach = 2))
    }

    @Test
    fun anEmptyRecordHasNothingToSay() {
        val record = MatchupRecord()

        assertEquals(0, record.total)
        assertNull(record.preference())
        assertNull(record.goingFirst.brickRate)
    }

    @Test
    fun aRecordIsNeverMutatedInPlace() {
        val start = MatchupRecord()
        start.judged(wentFirst = true, playable = false)

        assertEquals(0, start.total)
    }
}
