package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckGroupingTest {

    private val ash = TestCards.ashBlossom.id
    private val pot = TestCards.pot.id
    private val maxx = TestCards.maxxC.id

    @Test
    fun countsCopies() {
        val stacks = DeckGrouping.stacks(listOf(ash, pot, ash, maxx, ash))

        assertEquals(3, stacks.single { it.id == ash }.count)
        assertEquals(1, stacks.single { it.id == pot }.count)
    }

    @Test
    fun ordersByFirstAppearance() {
        // Not by count, and not by id: a stacked view has to list the deck in the
        // order the deck is in, or it stops being recognisable as the same deck.
        val stacks = DeckGrouping.stacks(listOf(pot, ash, ash, maxx))

        assertEquals(listOf(pot, ash, maxx), stacks.map { it.id })
    }

    @Test
    fun reportsWhereTheFirstCopySits() {
        val stacks = DeckGrouping.stacks(listOf(pot, ash, ash, maxx))

        assertEquals(0, stacks[0].firstIndex)
        assertEquals(1, stacks[1].firstIndex)
        assertEquals(3, stacks[2].firstIndex)
    }

    @Test
    fun countsAddUpToTheSection() {
        val section = listOf(ash, pot, ash, maxx, ash, pot)
        assertEquals(section.size, DeckGrouping.stacks(section).sumOf { it.count })
    }

    @Test
    fun anEmptySectionHasNoStacks() {
        assertTrue(DeckGrouping.stacks(emptyList()).isEmpty())
    }

    // ---- the gaps, once the copies are collapsed ---------------------------

    private fun over(section: List<CardId>, breaks: Breaks) =
        DeckGrouping.breaksOverStacks(breaks, DeckGrouping.stacks(section), section.size)

    @Test
    fun aGapBetweenTwoCardsIsStillBetweenTheirTwoStacks() {
        // Three cards, three stacks, a gap before the third: nothing collapsed,
        // so nothing moved.
        val section = listOf(ash, pot, maxx)

        assertEquals(setOf(2), over(section, Breaks(setOf(2))).before)
    }

    @Test
    fun aGapMovesBackByHoweverManyCopiesWereCollapsedBeforeIt() {
        // Three Ash, then Pot, with the gap between them. Stacked, Pot is the
        // second tile rather than the fourth.
        val section = listOf(ash, ash, ash, pot)

        assertEquals(setOf(1), over(section, Breaks(setOf(3))).before)
    }

    @Test
    fun aGapInsideARunOfCopiesMovesToTheNextSeamThereIs() {
        // Between the second and third Ash. Once those three are one tile the
        // gap has no seam of its own, so it goes to the next one -- a mark that
        // vanished when the density was turned down would read as the
        // arrangement being lost.
        val section = listOf(ash, ash, ash, pot)

        assertEquals(setOf(1), over(section, Breaks(setOf(2))).before)
    }

    @Test
    fun aGapInsideTheLastRunOfCopiesHasNothingAfterItAndIsNotDrawn() {
        // The same rule the end of a section already follows: a gap with no
        // cards after it is not a gap.
        val section = listOf(pot, ash, ash, ash)

        assertEquals(Breaks.NONE, over(section, Breaks(setOf(3))))
    }

    @Test
    fun twoGapsThatCollapseOntoTheSameSeamBecomeOne() {
        val section = listOf(ash, ash, ash, pot)

        assertEquals(setOf(1), over(section, Breaks(setOf(2, 3))).before)
    }

    @Test
    fun aDeckWithNoGapsStaysThatWay() {
        assertEquals(Breaks.NONE, over(listOf(ash, pot), Breaks.NONE))
        assertEquals(Breaks.NONE, over(emptyList(), Breaks(setOf(1))))
    }

    @Test
    fun aGapPastTheEndOfTheSectionIsNotDrawnEither() {
        val section = listOf(ash, pot)

        assertEquals(Breaks.NONE, over(section, Breaks(setOf(0, 2, 9))))
    }

    @Test
    fun everyGapLandsSomewhereARealSeamExists() {
        // The property: whatever comes out has to be a boundary between two
        // tiles that are actually drawn, or it is a mark hanging in space.
        val section = listOf(ash, ash, pot, maxx, ash, pot, maxx, maxx)
        val stacks = DeckGrouping.stacks(section)

        (0..section.size).forEach { cut ->
            over(section, Breaks(setOf(cut))).before.forEach { at ->
                assertTrue(at in 1 until stacks.size, "cut $cut landed at $at")
            }
        }
    }
}
