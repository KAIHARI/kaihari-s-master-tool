package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakTrackingTest {

    private val a = CardId(1)
    private val b = CardId(2)
    private val c = CardId(3)
    private val d = CardId(4)
    private val e = CardId(5)
    private val f = CardId(6)

    private val six = listOf(a, b, c, d, e, f)

    /** Three and three. */
    private val split = Breaks(setOf(3))

    private fun follow(before: List<CardId>, after: List<CardId>, breaks: Breaks = split) =
        BreakTracking.follow(breaks, before, after).before

    @Test
    fun aSectionWithNoGapsHasNothingToFollow() {
        assertEquals(emptySet(), follow(six, listOf(a, b), Breaks.NONE))
    }

    @Test
    fun nothingHappeningChangesNothing() {
        assertEquals(setOf(3), follow(six, six))
    }

    @Test
    fun aCardAppendedLeavesTheGapAlone() {
        assertEquals(setOf(3), follow(six, six + a))
    }

    @Test
    fun aCardDroppedIntoTheFirstGroupPushesTheGapAlong() {
        assertEquals(setOf(4), follow(six, listOf(a, f, b, c, d, e, f)))
    }

    @Test
    fun aCardDroppedIntoTheSecondGroupDoesNot() {
        assertEquals(setOf(3), follow(six, listOf(a, b, c, d, a, e, f)))
    }

    @Test
    fun aCardCutFromTheFirstGroupPullsTheGapBack() {
        assertEquals(setOf(2), follow(six, listOf(a, c, d, e, f)))
    }

    @Test
    fun aCardCutFromTheSecondGroupLeavesTheGapWhereItIs() {
        assertEquals(setOf(3), follow(six, listOf(a, b, c, e, f)))
    }

    @Test
    fun awholePlaysetArrivingAtOnceMovesTheGapByThree() {
        assertEquals(setOf(6), follow(six, listOf(a, f, f, f, b, c, d, e, f)))
    }

    @Test
    fun aCardDraggedForwardAcrossTheGapMovesItBetweenTheGroups() {
        // a goes to the end: two and four.
        val after = listOf(b, c, d, e, f, a)

        assertEquals(listOf(2, 4), Breaks(follow(six, after)).groups(6).map { it.count() })
    }

    @Test
    fun aCardDraggedBackAcrossTheGapMovesItTheOtherWay() {
        // f comes to the front: four and two.
        val after = listOf(f, a, b, c, d, e)

        assertEquals(listOf(4, 2), Breaks(follow(six, after)).groups(6).map { it.count() })
    }

    @Test
    fun aCardDraggedInsideItsOwnGroupLeavesTheGapAlone() {
        assertEquals(setOf(3), follow(six, listOf(b, c, a, d, e, f)))
        assertEquals(setOf(3), follow(six, listOf(a, b, c, e, f, d)))
    }

    @Test
    fun aSortIsNotSomethingItPretendsToUnderstand() {
        // Same length, not one card moving. The gaps stay where they were --
        // odd, but one tap from being fixed, and a deleted gap is gone.
        assertEquals(setOf(3), follow(six, listOf(f, e, d, c, b, a)))
    }

    @Test
    fun aGapPastTheEndOfWhatIsLeftIsDropped() {
        assertEquals(emptySet(), follow(six, listOf(a, b), Breaks(setOf(5))))
    }

    @Test
    fun emptyingASectionTakesItsGapsWithIt() {
        assertEquals(emptySet(), follow(six, emptyList()))
    }

    @Test
    fun aSectionFilledFromEmptyStartsWithoutGaps() {
        assertEquals(emptySet(), follow(emptyList(), six, Breaks.NONE))
    }

    @Test
    fun severalGapsAllFollowTheSameEdit() {
        val many = Breaks(setOf(2, 4))

        assertEquals(setOf(3, 5), follow(six, listOf(a, f, b, c, d, e, f), many))
        assertEquals(setOf(1, 3), follow(six, listOf(b, c, d, e, f), many))
    }
}
