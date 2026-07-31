package com.kaiharimoto.mastertool.core.hand

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpeningHandTest {

    private val deck = List(40) { CardId(1000 + it) }

    @Test
    fun sameSeedDealsTheSameHand() {
        val a = OpeningHand.deal(deck, seed = 7, handSize = 5)
        val b = OpeningHand.deal(deck, seed = 7, handSize = 5)
        assertEquals(a.hand, b.hand)
        assertEquals(a.library, b.library)

        // And a different seed almost surely does not.
        assertNotEquals(a.hand, OpeningHand.deal(deck, seed = 8, handSize = 5).hand)
    }

    @Test
    fun theShuffleIsAPermutation() {
        val dealt = OpeningHand.deal(deck, seed = 42, handSize = 5)
        assertEquals(deck.sortedBy { it.value }, dealt.library.sortedBy { it.value })
        assertEquals(5, dealt.hand.size)
        assertEquals(35, dealt.remaining.size)
    }

    @Test
    fun drawingComesOffTheTopAndStopsAtEmpty() {
        var hand = OpeningHand.deal(deck.take(6), seed = 1, handSize = 5)
        hand = hand.drawOne()
        assertEquals(6, hand.hand.size)
        assertEquals(0, hand.remaining.size)
        // Nothing left: drawing again is a no-op, not a crash.
        assertEquals(hand, hand.drawOne())
    }

    @Test
    fun mulliganKeepsTheSessionReplayableAndWholeDeckIntact() {
        val first = OpeningHand.deal(deck, seed = 5, handSize = 5)
        val second = first.mulligan(handSize = 5)

        assertEquals(1, second.mulligans)
        assertEquals(5, second.hand.size)
        // The full deck is still all there.
        assertEquals(deck.sortedBy { it.value }, second.library.sortedBy { it.value })
        // Replaying the same sequence from the same seed gives the same result.
        val replay = OpeningHand.deal(deck, seed = 5, handSize = 5).mulligan(handSize = 5)
        assertEquals(second.hand, replay.hand)
    }

    @Test
    fun tallyCountsByGroupWithUngroupedAsNull() {
        val groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g", "Starters", 0, 0))
            .assign(deck[0], "g")
            .assign(deck[1], "g")

        // Force a known hand: two grouped cards and one not.
        val hand = OpeningHand(
            seed = 0,
            library = deck,
            hand = listOf(deck[0], deck[1], deck[5]),
            drawn = 3,
            mulligans = 0,
        )
        val tally = hand.tally(groups)
        assertEquals(2, tally["g"])
        assertEquals(1, tally[null])
        assertTrue("missing" !in tally)
    }
}
