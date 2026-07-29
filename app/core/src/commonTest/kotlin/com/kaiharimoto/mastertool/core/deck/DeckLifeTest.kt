package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckLifeTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val droll = CardId(94145021)
    private val nibiru = CardId(27204311)

    private val versions = listOf(
        Deck(main = List(3) { ash } + List(3) { maxx }),
        Deck(main = List(3) { ash } + List(2) { maxx } + droll),
        Deck(main = List(3) { ash } + List(3) { droll }),
    )

    @Test
    fun theCardsThatNeverLeftAreTheDeck() {
        assertEquals(listOf(ash), DeckLife.core(versions))
    }

    @Test
    fun andTheOnesThatCameAndWentAreWhatIsStillBeingDecided() {
        assertEquals(setOf(maxx, droll), DeckLife.changing(versions).toSet())
    }

    @Test
    fun copiesDoNotCountTwiceWithinAVersion() {
        // The question is whether a card was in the deck that week, not how many.
        val standing = DeckLife.across(versions).single { it.id == ash }

        assertEquals(3, standing.kept)
        assertEquals(3, standing.of)
        assertTrue(standing.always)
    }

    @Test
    fun aCardThatMovedToTheSideDeckIsStillACardYouKept() {
        // Presence anywhere. Where it sat that week is a different question, and
        // the history sheet already answers it version by version.
        val moved = listOf(
            Deck(main = List(3) { ash }),
            Deck(main = List(2) { ash }, side = listOf(ash)),
            Deck(side = List(3) { ash }),
        )

        assertEquals(listOf(ash), DeckLife.core(moved))
    }

    @Test
    fun twoVersionsAreNotEnoughToSayAnything() {
        // A sample size of two, which is a coincidence rather than a habit.
        val two = versions.take(2)

        assertTrue(DeckLife.core(two).isEmpty())
        assertTrue(DeckLife.changing(two).isEmpty())
    }

    @Test
    fun theSteadiestComesFirst() {
        assertEquals(ash, DeckLife.across(versions).first().id)
    }

    @Test
    fun everyCardAnyVersionHeldIsAccountedFor() {
        assertEquals(setOf(ash, maxx, droll), DeckLife.across(versions).map { it.id }.toSet())
        assertTrue(nibiru !in DeckLife.across(versions).map { it.id })
    }

    @Test
    fun nothingAtAllIsNothingAtAll() {
        assertTrue(DeckLife.across(emptyList()).isEmpty())
        assertTrue(DeckLife.core(emptyList()).isEmpty())
    }

    @Test
    fun aRunOfIdenticalVersionsIsAllCore() {
        val same = List(4) { Deck(main = List(3) { ash } + maxx) }

        assertEquals(setOf(ash, maxx), DeckLife.core(same).toSet())
        assertTrue(DeckLife.changing(same).isEmpty())
    }
}
