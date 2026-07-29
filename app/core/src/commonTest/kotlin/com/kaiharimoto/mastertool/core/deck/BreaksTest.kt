package com.kaiharimoto.mastertool.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

class GroupAtTest {

    @Test
    fun aCardInTheMiddlePileFindsThatPile() {
        val breaks = Breaks(setOf(3, 7))

        assertEquals(3..6, breaks.groupAt(5, size = 10))
    }

    @Test
    fun theCardAGapSitsBeforeStartsTheNextPile() {
        val breaks = Breaks(setOf(3, 7))

        assertEquals(3..6, breaks.groupAt(3, size = 10))
        assertEquals(0..2, breaks.groupAt(2, size = 10))
    }

    @Test
    fun aSectionWithNoGapsIsOnePile() {
        assertEquals(0..9, Breaks().groupAt(4, size = 10))
    }

    @Test
    fun aPositionOffTheEndBelongsToNothing() {
        // A cursor left pointing past the end of a section it used to fit.
        assertNull(Breaks(setOf(3)).groupAt(10, size = 10))
        assertNull(Breaks(setOf(3)).groupAt(-1, size = 10))
        assertNull(Breaks().groupAt(0, size = 0))
    }

    @Test
    fun everyPositionBelongsToExactlyOnePile() {
        val breaks = Breaks(setOf(1, 4, 9, 12))

        (0 until 15).forEach { index ->
            val group = assertNotNull(breaks.groupAt(index, size = 15), "$index")
            assertEquals(1, breaks.groups(15).count { index in it }, "$index is in two piles")
            assertTrue(index in group)
        }
    }
}

/**
 * A name on a pile, and the one property that makes it worth having: it stays on
 * the pile it was put on, through everything that moves the pile.
 */
class NamedPileTest {

    private val engine = Breaks(before = setOf(9, 15))
        .named(0, "Engine")
        .named(9, "Handtraps")
        .named(15, "Board breakers")

    @Test
    fun aPileIsNamedByWhereItStarts() {
        assertEquals("Engine", engine.nameOf(0))
        assertEquals("Handtraps", engine.nameOf(9))
        assertNull(engine.nameOf(4), "a position inside a pile is not where it starts")
    }

    @Test
    fun aBlankNameIsNoNameRatherThanTheNameBlank() {
        assertNull(engine.named(9, "   ").nameOf(9))
    }

    @Test
    fun aNameIsTrimmed() {
        assertEquals("Handtraps", engine.named(9, "  Handtraps ").nameOf(9))
    }

    @Test
    fun addingCardsAboveAPileCarriesItsNameDown() {
        // Three copies appended to the engine. Everything below moves by three
        // and its names go with it -- a name left at 9 would now be on a card
        // still in the engine.
        val after = engine.afterInsert(at = 5, count = 3)

        assertEquals(setOf(12, 18), after.before)
        assertEquals("Handtraps", after.nameOf(12))
        assertEquals("Board breakers", after.nameOf(18))
        assertNull(after.nameOf(9))
    }

    @Test
    fun theFrontOfTheSectionDoesNotMoveWhenSomethingIsPutInFrontOfIt() {
        // Zero is not a gap, it is where the section starts, and it starts there
        // whatever arrives before it.
        val after = engine.afterInsert(at = 0, count = 2)

        assertEquals("Engine", after.nameOf(0))
    }

    @Test
    fun cuttingACardCarriesTheNamesBackUp() {
        val after = engine.afterRemove(at = 2)

        assertEquals(setOf(8, 14), after.before)
        assertEquals("Handtraps", after.nameOf(8))
        assertEquals("Engine", after.nameOf(0))
    }

    @Test
    fun takingAGapOutTakesTheNameOfThePileItStarted() {
        // That pile no longer exists -- it has been folded into the one above.
        // Leaving the name behind would put it on a pile nobody named.
        val after = engine.toggledAt(9)

        assertEquals(setOf(15), after.before)
        assertNull(after.nameOf(9))
        assertEquals("Engine", after.nameOf(0))
        assertEquals("Board breakers", after.nameOf(15))
    }

    @Test
    fun aNewGapStartsAnUnnamedPile() {
        val after = engine.toggledAt(4)

        assertNull(after.nameOf(4))
        assertEquals("Engine", after.nameOf(0), "and does not take the name of the one it split")
    }

    @Test
    fun aNameOnAPileThatIsNoLongerThereIsDropped() {
        // Cut the deck down to eight and the gap at nine is gone, so the pile it
        // started is gone with it.
        val after = engine.clampedTo(12)

        assertEquals(setOf(9), after.before)
        assertEquals("Handtraps", after.nameOf(9))
        assertNull(after.nameOf(15))
    }

    @Test
    fun aSectionWithNoGapsCarriesNoNames() {
        // One pile is the section, and naming it says nothing its own heading
        // does not already say.
        val after = engine.clampedTo(4)

        assertTrue(after.before.isEmpty())
        assertTrue(after.names.isEmpty())
    }

    @Test
    fun aCardDraggedAcrossAGapMovesTheNamesWithTheCardsRatherThanTheOtherWay() {
        // From the engine into the handtraps: the engine loses one, so every
        // pile below starts one earlier, and its name arrives there too.
        val after = engine.afterShift(from = 3, to = 11)

        assertEquals("Handtraps", after.nameOf(8))
        assertEquals("Engine", after.nameOf(0))
    }
}
