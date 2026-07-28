package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckIdentityTest {

    private fun ids(vararg values: Int) = values.map(::CardId)

    private fun deck(
        main: List<CardId> = emptyList(),
        extra: List<CardId> = emptyList(),
        side: List<CardId> = emptyList(),
    ) = Deck(main = main, extra = extra, side = side)

    @Test
    fun theMostCopiedCardsSpeakForTheDeck() {
        // Three copies of a card is the strongest statement a decklist makes.
        val result = DeckIdentity.faces(deck(main = ids(1, 2, 2, 2, 3, 3, 4)))

        assertEquals(ids(2, 3, 1), result)
    }

    @Test
    fun aTieGoesToWhicheverWasPutFirst() {
        // Not arbitrary: the order is authored in this program, and the front of
        // the Main deck is where the cards somebody cares about end up.
        val result = DeckIdentity.faces(deck(main = ids(7, 8, 9, 7, 8, 9)))

        assertEquals(ids(7, 8, 9), result)
    }

    @Test
    fun rearrangingTheDeckCanChangeTheFace() {
        // The consequence of the rule above, stated on purpose: moving a card to
        // the front is a way of saying it is the one that matters.
        val before = DeckIdentity.faces(deck(main = ids(1, 2, 3)), count = 1)
        val after = DeckIdentity.faces(deck(main = ids(3, 1, 2)), count = 1)

        assertEquals(ids(1), before)
        assertEquals(ids(3), after)
    }

    @Test
    fun noCardIsShownTwice() {
        val result = DeckIdentity.faces(deck(main = ids(5, 5, 5, 6, 6, 6, 7, 7, 7)))

        assertEquals(3, result.size)
        assertEquals(result.size, result.toSet().size)
    }

    @Test
    fun aShortDeckGivesWhatItHas() {
        assertEquals(ids(1, 2), DeckIdentity.faces(deck(main = ids(1, 2))))
        assertEquals(ids(1), DeckIdentity.faces(deck(main = ids(1, 1, 1))))
    }

    @Test
    fun anEmptyDeckHasNoFace() {
        assertTrue(DeckIdentity.faces(deck()).isEmpty())
    }

    @Test
    fun aDeckWithOnlyAnExtraFallsBackToIt() {
        // A stub somebody is partway through. Showing its Link monsters beats
        // showing nothing.
        assertEquals(ids(9), DeckIdentity.faces(deck(extra = ids(9, 9))))
    }

    @Test
    fun aDeckWithOnlyASideFallsBackToThat() {
        assertEquals(ids(4), DeckIdentity.faces(deck(side = ids(4))))
    }

    @Test
    fun theMainDeckWinsWhenThereIsOne() {
        assertEquals(ids(1), DeckIdentity.faces(deck(main = ids(1), extra = ids(9, 9, 9))))
    }

    @Test
    fun askingForNoneGivesNone() {
        assertTrue(DeckIdentity.faces(deck(main = ids(1, 2, 3)), count = 0).isEmpty())
        assertTrue(DeckIdentity.faces(deck(main = ids(1, 2, 3)), count = -1).isEmpty())
    }

    @Test
    fun askingForMoreThanThereIsIsFine() {
        assertEquals(ids(1, 2), DeckIdentity.faces(deck(main = ids(1, 2)), count = 10))
    }

    @Test
    fun aRealisticDeckPicksItsEngine() {
        // Three starters, three hand traps, one boss. The engine should show,
        // not the singleton.
        val main = ids(11, 11, 11, 22, 22, 22, 33, 44, 44, 55)
        assertEquals(ids(11, 22, 44), DeckIdentity.faces(deck(main = main)))
    }
}
