package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreaksTest {

    @Test
    fun aSectionWithNoGapsIsOneRun() {
        assertEquals(listOf(0 until 10), Breaks.NONE.groups(10))
        assertTrue(Breaks.NONE.isEmpty)
    }

    @Test
    fun anEmptySectionHasNoRunsAtAll() {
        assertEquals(emptyList(), Breaks.NONE.groups(0))
        assertEquals(emptyList(), Breaks(setOf(3)).groups(0))
    }

    @Test
    fun gapsCutTheSectionIntoRuns() {
        assertEquals(
            listOf(0 until 3, 3 until 7, 7 until 10),
            Breaks(setOf(3, 7)).groups(10),
        )
    }

    @Test
    fun aGapAtEitherEndIsNotAGap() {
        // Both happen naturally as cards come and go, and both mean nothing
        // rather than being wrong -- so they are dropped on the way out.
        assertEquals(listOf(0 until 5), Breaks(setOf(0, 5, 9)).groups(5))
        assertEquals(Breaks.NONE, Breaks(setOf(0, 5, 12)).clampedTo(5))
    }

    @Test
    fun aGapIsPutInAndTakenAwayByTheSameGesture() {
        val once = Breaks.NONE.toggledAt(4)
        assertEquals(setOf(4), once.before)
        assertEquals(Breaks.NONE, once.toggledAt(4))
    }

    @Test
    fun appendingToASectionNeverDisturbsAGap() {
        // Which is most of what happens to a deck being built.
        val breaks = Breaks(setOf(3, 7))

        assertEquals(breaks, breaks.afterInsert(at = 10))
    }

    @Test
    fun insertingBeforeAGapCarriesItAlong() {
        assertEquals(setOf(4, 8), Breaks(setOf(3, 7)).afterInsert(at = 1).before)
        assertEquals(setOf(3, 8), Breaks(setOf(3, 7)).afterInsert(at = 5).before)
    }

    @Test
    fun insertingSeveralAtOnceMovesTheGapByThatMuch() {
        assertEquals(setOf(6, 10), Breaks(setOf(3, 7)).afterInsert(at = 0, count = 3).before)
    }

    @Test
    fun aCardDroppedOnAGapJoinsTheGroupBeforeIt() {
        // Either answer is defensible; this one is the one that makes appending
        // to the end of a section free, which is the common case.
        assertEquals(setOf(4), Breaks(setOf(3)).afterInsert(at = 3).before)
    }

    @Test
    fun removingACardPullsLaterGapsBack() {
        assertEquals(setOf(2, 6), Breaks(setOf(3, 7)).afterRemove(at = 0).before)
        assertEquals(setOf(3, 6), Breaks(setOf(3, 7)).afterRemove(at = 5).before)
    }

    @Test
    fun aGapSurvivesTheLastCardOfItsGroupBeingCut() {
        // The gap sat before card 3. Card 2 is cut, so what was card 3 is now
        // card 2 -- and the gap has to come with it or the two groups run
        // together.
        assertEquals(setOf(2), Breaks(setOf(3)).afterRemove(at = 2).before)
    }

    @Test
    fun aGapImmediatelyBeforeARemovedCardStaysPut() {
        assertEquals(setOf(3), Breaks(setOf(3)).afterRemove(at = 3).before)
    }

    @Test
    fun draggingACardPastAGapMovesTheGapNotTheGroups() {
        // Nine cards, gap before the fourth. The first card is dragged to the
        // end: everything before the gap shuffles up by one, so the gap does.
        assertEquals(setOf(2), Breaks(setOf(3)).afterShift(from = 0, to = 8).before)
    }

    @Test
    fun draggingWithinAGroupLeavesTheGapsAlone() {
        assertEquals(setOf(5), Breaks(setOf(5)).afterShift(from = 0, to = 3).before)
        assertEquals(setOf(5), Breaks(setOf(5)).afterShift(from = 9, to = 7).before)
    }

    @Test
    fun draggingNowhereChangesNothing() {
        val breaks = Breaks(setOf(3, 7))

        assertEquals(breaks, breaks.afterShift(from = 4, to = 4))
    }

    @Test
    fun draggingAcrossAGapMovesTheCardBetweenTheGroups() {
        // Six cards, gap before the fourth: three and three. The first card is
        // dragged into the second group, so it becomes two and four.
        val moved = Breaks(setOf(3)).afterShift(from = 0, to = 4)

        assertEquals(listOf(2, 4), moved.groups(6).map { it.count() })
    }

    @Test
    fun runsOfGivenLengthsBecomeTheGapsBetweenThem() {
        assertEquals(setOf(9, 15), Breaks.betweenRuns(listOf(9, 6, 25)).before)
    }

    @Test
    fun oneRunNeedsNoGaps() {
        assertEquals(Breaks.NONE, Breaks.betweenRuns(listOf(40)))
        assertEquals(Breaks.NONE, Breaks.betweenRuns(emptyList()))
    }

    @Test
    fun theGapsFromRunsCutTheSectionBackIntoThoseRuns() {
        // The property that makes a tidy's gaps mean what the tidy meant.
        val lengths = listOf(4, 7, 2, 9)
        val breaks = Breaks.betweenRuns(lengths)

        assertEquals(lengths, breaks.groups(lengths.sum()).map { it.count() })
    }
}
