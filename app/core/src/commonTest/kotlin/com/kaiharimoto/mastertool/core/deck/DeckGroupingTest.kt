package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
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
}
