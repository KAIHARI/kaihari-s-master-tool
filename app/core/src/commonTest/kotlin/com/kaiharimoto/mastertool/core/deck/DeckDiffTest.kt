package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckDiffTest {

    private val ash = 14558127
    private val maxx = 23434538
    private val pot = 55144522
    private val main = DeckSection.MAIN

    // A list rather than a vararg: Kotlin will not take a value class as one,
    // and CardId is a value class so that a passcode costs nothing to pass.
    private fun deck(vararg ids: Int) = Deck(main = ids.map(::CardId))

    @Test
    fun aListComparedWithItselfHasNothingToSay() {
        val list = deck(ash, ash, maxx)

        val diff = DeckDiff.between(list, list)

        assertTrue(diff.isEmpty)
        assertTrue(diff.sameOrder)
        assertFalse(diff.reorderedOnly)
    }

    @Test
    fun aCardThatArrivedIsListedWhereItNowSits() {
        val diff = DeckDiff.between(deck(ash, ash), deck(ash, ash, pot))

        assertEquals(listOf(CardId(pot)), diff[main].added.map { it.id })
        assertEquals(0, diff[main].added.first().before)
        assertEquals(1, diff[main].added.first().after)
        assertTrue(diff[main].removed.isEmpty())
    }

    @Test
    fun aCopyCutIsAChangeInCountRatherThanACardLeaving() {
        // Three Bonfire becoming two is the commonest edit there is, and calling
        // it "removed" would be wrong about the deck.
        val diff = DeckDiff.between(deck(ash, ash, ash), deck(ash, ash))

        val change = diff[main].removed.single()
        assertEquals(3, change.before)
        assertEquals(2, change.after)
        assertEquals(-1, change.delta)
    }

    @Test
    fun theCountsAddUpTheWayAPersonWouldSayThem() {
        // Two out, one in: nobody says "minus two came out".
        val diff = DeckDiff.between(deck(ash, ash, maxx), deck(ash, pot))

        assertEquals(1, diff.cardsAdded)
        assertEquals(2, diff.cardsRemoved)
        assertFalse(diff[main].isBalanced)
    }

    @Test
    fun anEvenSwapSaysSo() {
        val diff = DeckDiff.between(deck(ash, maxx), deck(ash, pot))

        assertTrue(diff[main].isBalanced)
        assertEquals(1, diff.cardsAdded)
        assertEquals(1, diff.cardsRemoved)
    }

    @Test
    fun theSameCardsInADifferentOrderIsNotNoChange() {
        // The whole program treats the arrangement as the player's own work, so
        // a comparison that called these identical would contradict it.
        val diff = DeckDiff.between(deck(ash, maxx, pot), deck(pot, ash, maxx))

        assertTrue(diff.isEmpty)
        assertFalse(diff.sameOrder)
        assertTrue(diff.reorderedOnly)
    }

    @Test
    fun everySectionIsCompared() {
        val before = Deck(
            main = listOf(CardId(ash)),
            extra = listOf(CardId(maxx)),
            side = listOf(CardId(pot)),
        )
        val after = Deck(
            main = listOf(CardId(ash)),
            extra = emptyList(),
            side = listOf(CardId(pot), CardId(pot)),
        )

        assertTrue(before.let { DeckDiff.between(it, after) }[DeckSection.MAIN].isEmpty)
        assertEquals(1, DeckDiff.between(before, after)[DeckSection.EXTRA].cardsRemoved)
        assertEquals(1, DeckDiff.between(before, after)[DeckSection.SIDE].cardsAdded)
    }

    @Test
    fun aDeckComparedWithNothingIsEverythingArriving() {
        val diff = DeckDiff.between(Deck.EMPTY, deck(ash, ash, maxx))

        assertEquals(3, diff.cardsAdded)
        assertEquals(0, diff.cardsRemoved)
        assertEquals(listOf(CardId(ash), CardId(maxx)), diff[main].added.map { it.id })
    }

    @Test
    fun everythingLeavingIsTheOtherWayRound() {
        val diff = DeckDiff.between(deck(ash, ash, maxx), Deck.EMPTY)

        assertEquals(0, diff.cardsAdded)
        assertEquals(3, diff.cardsRemoved)
    }

    @Test
    fun aCardIsNeverInBothColumns() {
        // The property: a count went up or it went down, and a card that appears
        // in both lists is a comparison somebody would have to reconcile by hand.
        val before = deck(ash, ash, ash, maxx, pot)
        val after = deck(ash, maxx, maxx, pot, pot)

        val diff = DeckDiff.between(before, after)
        val added = diff[main].added.map { it.id }.toSet()
        val removed = diff[main].removed.map { it.id }.toSet()

        assertTrue((added intersect removed).isEmpty())
        assertEquals(setOf(CardId(maxx), CardId(pot)), added)
        assertEquals(setOf(CardId(ash)), removed)
    }

    @Test
    fun theTotalsAgreeWithTheDeckSizes() {
        val before = deck(ash, ash, ash, maxx)
        val after = deck(ash, pot, pot)

        val diff = DeckDiff.between(before, after)

        assertEquals(
            after.main.size - before.main.size,
            diff.cardsAdded - diff.cardsRemoved,
        )
    }
}
