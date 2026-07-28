package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckSorterTest {

    private val pool = TestCards.all
    private val byId = pool.associateBy { it.id }
    private fun lookup(id: CardId): Card? = byId[id]

    private fun names(ids: List<CardId>) = ids.map { byId[it]?.name ?: "?${it.value}" }

    /** Ash (Lv3 FIRE), Nibiru (Lv11), Maxx "C" (Lv2), Pot (Spell), Imperm (Trap). */
    private val mixed = listOf(
        TestCards.pot.id,
        TestCards.nibiru.id,
        TestCards.infiniteImpermanence.id,
        TestCards.ashBlossom.id,
        TestCards.maxxC.id,
    )

    @Test
    fun manualLeavesTheOrderAlone() {
        assertEquals(mixed, DeckSorter.sort(mixed, ::lookup, SortMode.MANUAL))
    }

    @Test
    fun byTypePutsMonstersThenSpellsThenTraps() {
        val sorted = names(DeckSorter.sort(mixed, ::lookup, SortMode.TYPE))

        assertEquals(
            listOf(
                "Nibiru, the Primal Being",      // Level 11
                "Ash Blossom & Joyous Spring",   // Level 3
                "Maxx \"C\"",                    // Level 2
                "Pot of Prosperity",
                "Infinite Impermanence",
            ),
            sorted,
        )
    }

    @Test
    fun byNameIsAlphabetical() {
        val sorted = names(DeckSorter.sort(mixed, ::lookup, SortMode.NAME))
        assertEquals(sorted.sorted(), sorted)
    }

    @Test
    fun byAtkIsDescending() {
        val sorted = DeckSorter.sort(mixed, ::lookup, SortMode.ATK)
        assertEquals(TestCards.nibiru.id, sorted.first())
    }

    @Test
    fun byBanlistPutsTheForbiddenCardFirst() {
        val sorted = names(DeckSorter.sort(mixed, ::lookup, SortMode.BANLIST))

        assertEquals("Maxx \"C\"", sorted.first())             // Forbidden
        assertEquals("Ash Blossom & Joyous Spring", sorted[1]) // Limited
    }

    @Test
    fun cardsWithoutTheStatSortLastRatherThanAsZero() {
        // Accesscode Talker is a Link monster: no level, no DEF. Treating a
        // missing stat as 0 would rank it below a Level 1, which it is not.
        val ids = listOf(TestCards.accesscode.id, TestCards.maxxC.id)
        val sorted = DeckSorter.sort(ids, ::lookup, SortMode.LEVEL)

        assertEquals(listOf(TestCards.maxxC.id, TestCards.accesscode.id), sorted)
    }

    @Test
    fun unknownPasscodesSinkToTheEndAndKeepTheirOrder() {
        val first = CardId(90000001)
        val second = CardId(90000002)
        val ids = listOf(first, TestCards.pot.id, second, TestCards.ashBlossom.id)

        val sorted = DeckSorter.sort(ids, ::lookup, SortMode.NAME)

        assertEquals(listOf(first, second), sorted.takeLast(2))
    }

    @Test
    fun sortingIsStableForEqualCards() {
        // Three copies of one card plus another: repeated sorting must not churn.
        val ids = List(3) { TestCards.ashBlossom.id } + TestCards.pot.id
        val once = DeckSorter.sort(ids, ::lookup, SortMode.TYPE)

        assertEquals(once, DeckSorter.sort(once, ::lookup, SortMode.TYPE))
    }

    /**
     * The property that protects export fidelity: whatever a sort does to the
     * order, the multiset of cards has to come back untouched.
     */
    @Test
    fun everySortIsAPermutationOfTheInput() {
        val ids = List(3) { TestCards.ashBlossom.id } +
            List(2) { TestCards.maxxC.id } +
            listOf(TestCards.pot.id, TestCards.nibiru.id, CardId(90000001)) +
            TestCards.infiniteImpermanence.id

        val expected = ids.groupingBy { it }.eachCount()

        SortMode.entries.forEach { mode ->
            val sorted = DeckSorter.sort(ids, ::lookup, mode)
            assertEquals(ids.size, sorted.size, "size changed under $mode")
            assertEquals(expected, sorted.groupingBy { it }.eachCount(), "contents changed under $mode")
        }
    }

    @Test
    fun sortingAnEmptyOrSingleSectionIsSafe() {
        assertTrue(DeckSorter.sort(emptyList(), ::lookup, SortMode.TYPE).isEmpty())
        assertEquals(
            listOf(TestCards.pot.id),
            DeckSorter.sort(listOf(TestCards.pot.id), ::lookup, SortMode.TYPE),
        )
    }
}
