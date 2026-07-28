package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals

class DeckTidyTest {

    private val ash = monster(1, "Ash Blossom & Joyous Spring", level = 3, archetype = null)
    private val maxx = monster(2, "Maxx \"C\"", level = 2, archetype = null)
    private val kashtira = monster(3, "Kashtira Fenrir", level = 7, archetype = "Kashtira")
    private val riseheart = monster(4, "Kashtira Riseheart", level = 4, archetype = "Kashtira")
    private val pot = spell(5, "Pot of Prosperity")
    private val imperm = trap(6, "Infinite Impermanence")

    private val pool = listOf(ash, maxx, kashtira, riseheart, pot, imperm).associateBy { it.id }
    private val lookup: (CardId) -> Card? = { pool[it] }

    private fun tidy(ids: List<CardId>, mode: TidyBy) = DeckTidy.apply(ids, mode, lookup)

    private fun names(ids: List<CardId>) = ids.map { pool.getValue(it).name }

    @Test
    fun cardTypesLandInDecklistOrder() {
        val start = listOf(imperm.id, ash.id, pot.id, maxx.id)

        assertEquals(
            listOf(ash.id, maxx.id, pot.id, imperm.id),
            tidy(start, TidyBy.CATEGORY),
        )
    }

    @Test
    fun theMonstersKeepTheOrderTheyWereInsideTheirGroup() {
        // The whole point. Grouping by type must not also alphabetise, re-level
        // or otherwise re-sort the monsters -- somebody arranged those.
        val start = listOf(kashtira.id, imperm.id, ash.id, maxx.id, riseheart.id)

        assertEquals(
            listOf("Kashtira Fenrir", "Ash Blossom & Joyous Spring", "Maxx \"C\"", "Kashtira Riseheart"),
            names(tidy(start, TidyBy.CATEGORY)).take(4),
        )
    }

    @Test
    fun archetypesStayWhereTheyWerePut() {
        // An engine deliberately placed at the front stays at the front:
        // alphabetising the archetypes would undo that decision silently.
        val start = listOf(kashtira.id, ash.id, riseheart.id, maxx.id)

        assertEquals(listOf(kashtira.id, riseheart.id, ash.id, maxx.id), tidy(start, TidyBy.ARCHETYPE))
    }

    @Test
    fun cardsWithNoArchetypeGoLastInTheirOwnOrder() {
        val start = listOf(maxx.id, kashtira.id, ash.id)

        assertEquals(listOf(kashtira.id, maxx.id, ash.id), tidy(start, TidyBy.ARCHETYPE))
    }

    @Test
    fun gatheringCopiesLeavesEverythingElseAlone() {
        val start = listOf(ash.id, pot.id, ash.id, imperm.id, maxx.id)

        assertEquals(
            listOf(ash.id, ash.id, pot.id, imperm.id, maxx.id),
            tidy(start, TidyBy.COPIES),
        )
    }

    @Test
    fun aCardThePoolHasNeverHeardOfIsNotAnError() {
        // Decks are imported before the pool finishes downloading, and a tidy
        // that dropped the unknown cards would be a data loss dressed as a
        // convenience.
        val stranger = CardId(999)
        val start = listOf(stranger, pot.id, ash.id)

        TidyBy.entries.forEach { mode ->
            assertEquals(
                start.sortedBy { it.value },
                tidy(start, mode).sortedBy { it.value },
                "$mode lost a card",
            )
        }
        assertEquals(stranger, tidy(start, TidyBy.CATEGORY).last())
    }

    @Test
    fun everyTidyIsIdempotent() {
        val start = listOf(imperm.id, kashtira.id, ash.id, pot.id, ash.id, riseheart.id, maxx.id)

        TidyBy.entries.forEach { mode ->
            val once = tidy(start, mode)
            assertEquals(once, tidy(once, mode), "$mode moved cards on a second press")
        }
    }

    @Test
    fun everyTidyIsAPermutation() {
        val start = listOf(imperm.id, kashtira.id, ash.id, pot.id, ash.id, riseheart.id, maxx.id)

        TidyBy.entries.forEach { mode ->
            assertEquals(
                start.sortedBy { it.value },
                tidy(start, mode).sortedBy { it.value },
                "$mode changed the deck",
            )
        }
    }

    @Test
    fun anEmptySectionTidiesToNothing() {
        TidyBy.entries.forEach { mode ->
            assertEquals(emptyList(), tidy(emptyList(), mode))
        }
    }

    private fun monster(id: Int, name: String, level: Int, archetype: String?) = Card(
        id = CardId(id),
        name = name,
        type = "Effect Monster",
        frameType = "effect",
        level = level,
        archetype = archetype,
    )

    private fun spell(id: Int, name: String) =
        Card(id = CardId(id), name = name, type = "Spell Card", frameType = "spell")

    private fun trap(id: Int, name: String) =
        Card(id = CardId(id), name = name, type = "Trap Card", frameType = "trap")
}
