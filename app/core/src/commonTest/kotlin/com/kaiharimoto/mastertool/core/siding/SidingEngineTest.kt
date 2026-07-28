package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidingEngineTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val nib = CardId(27204311)
    private val droll = CardId(94145021)
    private val imperm = CardId(10045474)

    private fun deck(main: List<CardId>, side: List<CardId>) =
        Deck(main = main, extra = emptyList(), side = side)

    @Test
    fun aSwapExchangesCardsBetweenMainAndSide() {
        val before = deck(main = listOf(ash, maxx, nib), side = listOf(droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(maxx)),
        )

        assertEquals(listOf(ash, droll, nib), result.deck.main)
        assertEquals(listOf(maxx), result.deck.side)
        assertTrue(result.isClean)
    }

    @Test
    fun anArrivingCardTakesThePlaceOfTheOneItReplaces() {
        // The whole point. Appending would be simpler and would quietly destroy
        // an arrangement its owner chose.
        val before = deck(main = listOf(ash, maxx, nib, imperm), side = listOf(droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(nib)),
        )

        assertEquals(listOf(ash, maxx, droll, imperm), result.deck.main)
    }

    @Test
    fun severalSwapsPairUpInDeckOrder() {
        // Pairing follows position in the deck, not the order the plan was typed,
        // so the same plan always lands the same way round.
        val before = deck(main = listOf(ash, maxx, nib, imperm), side = listOf(droll, droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll, droll), cardsOut = listOf(imperm, maxx)),
        )

        assertEquals(listOf(ash, droll, nib, droll), result.deck.main)
    }

    @Test
    fun theSpareCopyLeavesFirst() {
        // Someone who arranged their deck put the copies they think about first.
        val before = deck(main = listOf(maxx, ash, maxx, nib, maxx), side = listOf(droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(maxx)),
        )

        assertEquals(listOf(maxx, ash, maxx, nib, droll), result.deck.main)
    }

    @Test
    fun aBalancedSwapLeavesTheDeckTheSizeItFoundIt() {
        val before = deck(main = List(40) { ash }, side = List(15) { droll })
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = List(5) { droll }, cardsOut = List(5) { ash }),
        )

        assertEquals(40, result.deck.main.size)
        assertEquals(15, result.deck.side.size)
        assertTrue(result.isClean)
    }

    @Test
    fun everyCardIsStillSomewhereAfterwards() {
        // Siding moves cards between two piles. It must never invent or lose one.
        val before = deck(
            main = listOf(ash, ash, maxx, nib, imperm),
            side = listOf(droll, droll, imperm),
        )
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll, imperm), cardsOut = listOf(ash, nib)),
        )

        assertEquals(
            (before.main + before.side).sortedBy { it.value },
            (result.deck.main + result.deck.side).sortedBy { it.value },
        )
    }

    @Test
    fun theExtraDeckIsNeverTouched() {
        val before = Deck(main = listOf(ash), extra = listOf(nib, nib), side = listOf(droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(ash)),
        )

        assertEquals(listOf(nib, nib), result.deck.extra)
    }

    // A plan that no longer fits the deck still has to produce a deck. Between
    // rounds there is a clock running, so swapping what can be swapped and being
    // specific about the rest beats refusing to side at all.

    @Test
    fun takingOutACardThatIsNotThereSaysSoAndSidesTheRest() {
        val before = deck(main = listOf(ash, maxx), side = listOf(droll, nib))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll, nib), cardsOut = listOf(maxx, imperm)),
        )

        assertContains(result.problems, SidingProblem.NotInMain(imperm, wanted = 1, found = 0))
        // The half that was possible still happened.
        assertContains(result.deck.main, droll)
    }

    @Test
    fun bringingInACardThatIsNotInTheSideSaysHowManyWereFound() {
        val before = deck(main = listOf(ash, maxx), side = listOf(droll))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll, droll), cardsOut = listOf(ash, maxx)),
        )

        assertContains(result.problems, SidingProblem.NotInSide(droll, wanted = 2, found = 1))
    }

    @Test
    fun aSlotWithNobodyToFillItClosesUp() {
        // One card out, none available to come in: the deck gets shorter rather
        // than keeping a hole in it.
        val before = deck(main = listOf(ash, maxx, nib), side = emptyList())
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(maxx)),
        )

        assertEquals(listOf(ash, nib), result.deck.main)
        assertEquals(listOf(maxx), result.deck.side)
    }

    @Test
    fun anUnevenPlanIsFlagged() {
        val before = deck(main = listOf(ash, maxx, nib), side = listOf(droll, imperm))
        val result = SidingEngine.apply(
            before,
            SidingSwap(cardsIn = listOf(droll, imperm), cardsOut = listOf(ash)),
        )

        assertContains(result.problems, SidingProblem.Unbalanced(cardsIn = 2, cardsOut = 1))
        assertEquals(4, result.deck.main.size)
    }

    @Test
    fun goingFirstAndGoingSecondAreDifferentPlans() {
        val pattern = SidingPattern(
            deckName = "Snake-Eye",
            goingFirst = SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(nib)),
            goingSecond = SidingSwap(cardsIn = listOf(imperm), cardsOut = listOf(nib)),
        )
        val before = deck(main = listOf(ash, nib), side = listOf(droll, imperm))

        assertContains(SidingEngine.apply(before, pattern, goingFirst = true).deck.main, droll)
        assertContains(SidingEngine.apply(before, pattern, goingFirst = false).deck.main, imperm)
    }

    @Test
    fun anEmptyPlanChangesNothing() {
        val before = deck(main = listOf(ash, maxx), side = listOf(droll))
        val result = SidingEngine.apply(before, SidingSwap())

        assertEquals(before, result.deck)
        assertTrue(result.isClean)
    }
}
