package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckKeysTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val nib = CardId(27204311)

    /** What the grid actually keys a tile by. */
    private fun keys(ids: List<CardId>) =
        ids.zip(DeckKeys.occurrences(ids)) { id, copy -> "${id.value}-$copy" }

    @Test
    fun copiesOfTheSameCardAreNumberedInOrder() {
        assertEquals(listOf(0, 0, 1, 2), DeckKeys.occurrences(listOf(maxx, ash, ash, ash)))
    }

    @Test
    fun noTwoPositionsShareAKey() {
        val ids = listOf(ash, maxx, ash, nib, ash, maxx)
        val identities = keys(ids)

        assertEquals(identities.size, identities.toSet().size, "duplicate key in $identities")
    }

    @Test
    fun insertingACardLeavesEveryOtherKeyAlone() {
        // The whole point. With positional keys this assertion fails for every
        // tile after the insertion, and nothing can be animated moving.
        val before = listOf(ash, maxx, nib)
        val after = listOf(ash, CardId(99), maxx, nib)

        val kept = keys(before).toSet() intersect keys(after).toSet()
        assertEquals(keys(before).toSet(), kept)
    }

    @Test
    fun removingACardLeavesEveryOtherKeyAlone() {
        val before = listOf(ash, maxx, nib, ash)
        val after = listOf(ash, nib, ash)

        assertTrue(keys(after).toSet().all { it in keys(before).toSet() })
    }

    @Test
    fun reorderingKeepsTheSameSetOfKeys() {
        // A sort is a permutation, so every tile must survive it and merely move.
        val before = listOf(ash, maxx, nib, ash, ash)
        val after = listOf(ash, ash, ash, maxx, nib)

        assertEquals(keys(before).toSet(), keys(after).toSet())
    }

    @Test
    fun removingTheFirstOfThreeCopiesRetiresTheLastKey() {
        // Copies are identical, so it does not matter which of the three the grid
        // treats as having left — only that two remain and neither is new.
        val before = listOf(ash, ash, ash)
        val after = listOf(ash, ash)

        assertEquals(listOf("14558127-0", "14558127-1"), keys(after))
        assertTrue(keys(after).all { it in keys(before) })
    }

    @Test
    fun anEmptySectionHasNoKeys() {
        assertEquals(emptyList(), DeckKeys.occurrences(emptyList()))
    }

    @Test
    fun theAnswerDependsOnlyOnTheList() {
        val ids = listOf(ash, maxx, ash, ash, nib)
        assertEquals(DeckKeys.occurrences(ids), DeckKeys.occurrences(ids))
    }
}
