package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidingDiffTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val nib = CardId(27204311)
    private val droll = CardId(94145021)
    private val imperm = CardId(10045474)

    private fun deck(main: List<CardId>, side: List<CardId> = emptyList()) =
        Deck(main = main, extra = emptyList(), side = side)

    @Test
    fun aSwapIsWhatTheMainDeckGainedAndLost() {
        val before = deck(listOf(ash, maxx, nib), listOf(droll))
        val after = deck(listOf(ash, droll, nib), listOf(maxx))

        val swap = SidingDiff.between(before, after)

        assertEquals(listOf(droll), swap.cardsIn)
        assertEquals(listOf(maxx), swap.cardsOut)
    }

    @Test
    fun copiesAreCountedOneByOne() {
        // "Two of three" and "all three" are different decisions, and a count
        // would flatten them.
        val before = deck(listOf(maxx, maxx, maxx, ash))
        val after = deck(listOf(maxx, ash, droll, droll))

        val swap = SidingDiff.between(before, after)

        assertEquals(2, swap.cardsOut.count { it == maxx })
        assertEquals(2, swap.cardsIn.count { it == droll })
    }

    @Test
    fun rearrangingIsNotSiding() {
        // The arrangement is a separate thing. A plan that recorded "moved card
        // seven to slot twelve" would be recording the wrong edit entirely.
        val before = deck(listOf(ash, maxx, nib))
        val after = deck(listOf(nib, ash, maxx))

        assertTrue(SidingDiff.between(before, after).isEmpty)
    }

    @Test
    fun anUntouchedDeckHasNoPlan() {
        val deck = deck(listOf(ash, maxx, nib))
        assertTrue(SidingDiff.between(deck, deck).isEmpty)
    }

    @Test
    fun theSideDeckIsNotComparedSeparately() {
        // Cards that left the Main deck went to the Side by definition. Saying
        // it twice would let the two halves contradict each other.
        val before = deck(listOf(ash, maxx), side = listOf(droll, imperm))
        val after = deck(listOf(ash, droll), side = listOf(maxx, imperm, nib))

        val swap = SidingDiff.between(before, after)

        assertEquals(listOf(droll), swap.cardsIn)
        assertEquals(listOf(maxx), swap.cardsOut)
    }

    @Test
    fun aRecordedSwapReadsInDeckOrder() {
        // Two cards leave. They are listed in the order the old deck held them,
        // so a recorded plan reads the way the deck reads rather than in
        // whatever order a hash map produced.
        val before = deck(listOf(ash, maxx, nib, imperm))
        val after = deck(listOf(ash, droll, nib, droll))

        val swap = SidingDiff.between(before, after)

        assertEquals(listOf(maxx, imperm), swap.cardsOut)
        assertEquals(listOf(droll, droll), swap.cardsIn)
    }

    @Test
    fun acardThatOnlyMovedDidNotLeave() {
        // The same rule as rearranging, mixed in with a real swap: maxx changes
        // slot while imperm is genuinely replaced, and only imperm is recorded.
        val before = deck(listOf(ash, maxx, nib, imperm))
        val after = deck(listOf(ash, droll, nib, maxx))

        val swap = SidingDiff.between(before, after)

        assertEquals(listOf(imperm), swap.cardsOut)
        assertEquals(listOf(droll), swap.cardsIn)
    }

    @Test
    fun aRecordedSwapCanBeAppliedBackAndGiveTheSameDeck() {
        // The property that makes recording worth anything: what comes out of
        // the diff has to be a plan that reproduces what was done.
        val before = deck(listOf(ash, maxx, maxx, nib), side = listOf(droll, droll, imperm))
        val after = deck(listOf(ash, droll, droll, nib), side = listOf(maxx, maxx, imperm))

        val recorded = SidingDiff.between(before, after)
        val replayed = SidingEngine.apply(before, recorded)

        assertTrue(replayed.isClean, "unexpected problems: ${replayed.problems}")
        assertEquals(
            after.main.sortedBy { it.value },
            replayed.deck.main.sortedBy { it.value },
        )
        assertEquals(
            after.side.sortedBy { it.value },
            replayed.deck.side.sortedBy { it.value },
        )
    }

    @Test
    fun aRecordedSwapIsBalancedWhenTheDeckSizeDidNotChange() {
        val before = deck(listOf(ash, maxx, nib), listOf(droll))
        val after = deck(listOf(ash, droll, nib), listOf(maxx))

        assertTrue(SidingDiff.between(before, after).isBalanced)
    }

    @Test
    fun aDeckThatGotShorterRecordsAnUnbalancedSwap() {
        // Legal, and almost always a slip -- worth being able to see on the plan
        // rather than discovering from a thirty-nine card deck.
        val before = deck(listOf(ash, maxx, nib))
        val after = deck(listOf(ash, nib))

        assertTrue(!SidingDiff.between(before, after).isBalanced)
    }

    @Test
    fun movingACardAcrossTheLineIsASwap() {
        val before = deck(listOf(ash, maxx, nib), listOf(droll))
        val after = deck(listOf(ash, droll, nib), listOf(maxx))

        assertTrue(SidingDiff.isSwap(before, after))
    }

    @Test
    fun rearrangingIsASwapOfNothingAtAll() {
        val before = deck(listOf(ash, maxx, nib))
        val after = deck(listOf(nib, ash, maxx))

        assertTrue(SidingDiff.isSwap(before, after))
    }

    @Test
    fun bringingInACardYouDidNotRegisterIsNotSiding() {
        val before = deck(listOf(ash, maxx), listOf(droll))
        val after = deck(listOf(ash, maxx, imperm), listOf(droll))

        assertFalse(SidingDiff.isSwap(before, after))
    }

    @Test
    fun takingACardOutOfTheBuildingIsNotSidingEither() {
        // The one the Main deck alone cannot tell apart from a swap: the Main
        // deck lost a card in both cases, and only the Side deck says which.
        val before = deck(listOf(ash, maxx), listOf(droll))
        val sided = deck(listOf(ash, droll), listOf(maxx))
        val cut = deck(listOf(ash, droll), listOf())

        assertTrue(SidingDiff.isSwap(before, sided))
        assertFalse(SidingDiff.isSwap(before, cut))
    }

    @Test
    fun anExtraDeckSwapCountsToo() {
        val before = Deck(main = listOf(ash), extra = listOf(maxx), side = listOf(nib))
        val after = Deck(main = listOf(ash), extra = listOf(nib), side = listOf(maxx))

        assertTrue(SidingDiff.isSwap(before, after))
    }
}
