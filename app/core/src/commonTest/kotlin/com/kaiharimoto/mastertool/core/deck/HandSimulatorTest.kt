package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandSimulatorTest {

    private val deck = (1..40).map { CardId(it) }

    @Test
    fun goingFirstDealsFiveAndGoingSecondDealsSix() {
        assertEquals(5, HandSimulator.deal(deck, goingFirst = true, Random(1)).size)
        assertEquals(6, HandSimulator.deal(deck, goingFirst = false, Random(1)).size)
    }

    @Test
    fun theRestOfTheDeckIsUnderTheHand() {
        val hand = HandSimulator.deal(deck, goingFirst = true, Random(7))

        assertEquals(35, hand.remaining.size)
        assertEquals(
            deck.sortedBy { it.value },
            (hand.cards + hand.remaining).sortedBy { it.value },
            "a shuffle must not invent or lose a card",
        )
    }

    @Test
    fun noCardIsDealtTwice() {
        val hand = HandSimulator.deal(deck, goingFirst = false, Random(3))
        assertEquals(hand.cards.size, hand.cards.toSet().size)
    }

    @Test
    fun copiesOfACardCanBothBeDealt() {
        // A deck is a multiset. Three copies of one card must be able to arrive
        // in the same hand, which is the whole reason opening-hand odds exist.
        val threeOfEach = List(3) { CardId(1) } + List(37) { CardId(2) }
        val hands = (1..200).map { HandSimulator.deal(threeOfEach, true, Random(it)) }

        assertTrue(hands.any { it.cards.count { card -> card == CardId(1) } >= 2 })
    }

    @Test
    fun theSameSeedDealsTheSameHand() {
        // Not a nicety: it is what makes every one of these tests possible.
        assertEquals(
            HandSimulator.deal(deck, goingFirst = true, Random(42)).cards,
            HandSimulator.deal(deck, goingFirst = true, Random(42)).cards,
        )
    }

    @Test
    fun differentSeedsDealDifferentHands() {
        val first = HandSimulator.deal(deck, goingFirst = true, Random(1)).cards
        val second = HandSimulator.deal(deck, goingFirst = true, Random(2)).cards

        assertTrue(first != second)
    }

    @Test
    fun aDeckShorterThanAHandDealsWhatItHas() {
        // Decks get edited while this is open, and a half-built one is exactly
        // when somebody asks this question.
        val small = listOf(CardId(1), CardId(2))
        val hand = HandSimulator.deal(small, goingFirst = true, Random(1))

        assertEquals(2, hand.size)
        assertTrue(hand.remaining.isEmpty())
    }

    @Test
    fun anEmptyDeckDealsNothing() {
        val hand = HandSimulator.deal(emptyList(), goingFirst = true, Random(1))
        assertEquals(0, hand.size)
    }

    @Test
    fun drawingTakesTheNextCardFromUnderneath() {
        val hand = HandSimulator.deal(deck, goingFirst = true, Random(5))
        val next = hand.remaining.first()
        val drawn = HandSimulator.drawOne(hand)

        assertEquals(6, drawn.size)
        assertEquals(next, drawn.cards.last())
        assertEquals(hand.remaining.size - 1, drawn.remaining.size)
    }

    @Test
    fun drawingFromAnEmptyDeckIsNotAnError() {
        // Running out is a real answer to "what if this goes long".
        val hand = HandSimulator.deal(listOf(CardId(1)), goingFirst = true, Random(1))
        assertEquals(hand, HandSimulator.drawOne(hand))
    }

    @Test
    fun drawingKeepsEveryCardAccountedFor() {
        var hand = HandSimulator.deal(deck, goingFirst = true, Random(9))
        repeat(10) { hand = HandSimulator.drawOne(hand) }

        assertEquals(
            deck.sortedBy { it.value },
            (hand.cards + hand.remaining).sortedBy { it.value },
        )
    }
}

class HandTallyTest {

    @Test
    fun nothingJudgedHasNoBrickRate() {
        // Null rather than zero: "no bricks yet" and "not asked yet" are
        // different things to put on screen.
        assertNull(HandTally().brickRate)
        assertEquals(0, HandTally().total)
    }

    @Test
    fun judgingCounts() {
        val tally = HandTally()
            .judged(playable = true)
            .judged(playable = false)
            .judged(playable = true)

        assertEquals(2, tally.playable)
        assertEquals(1, tally.bricks)
        assertEquals(3, tally.total)
    }

    @Test
    fun theBrickRateIsTheShareOfHandsThatBricked() {
        val tally = (1..10).fold(HandTally()) { acc, i -> acc.judged(playable = i > 2) }

        assertEquals(0.2, tally.brickRate!!, 0.0001)
    }

    @Test
    fun aTallyIsNeverMutatedInPlace() {
        val start = HandTally()
        start.judged(playable = true)

        assertEquals(0, start.total)
    }
}
