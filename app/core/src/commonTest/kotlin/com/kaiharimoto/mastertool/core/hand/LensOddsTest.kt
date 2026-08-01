package com.kaiharimoto.mastertool.core.hand

import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGroups
import com.kaiharimoto.mastertool.core.deck.DeckLenses
import com.kaiharimoto.mastertool.core.deck.Lens
import com.kaiharimoto.mastertool.core.deck.LensKeying
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LensOddsTest {

    private val deck = (1..40).map { CardId(it) }

    private fun card(id: Int, type: String = "Effect Monster") = Card(
        id = CardId(id),
        name = "Card $id",
        type = type,
        frameType = "effect",
    )

    private fun rolesWith(starters: Int): LensKeying {
        var groups = DeckGroups.EMPTY.upsert(DeckGroup("g1", "Starter", color = 2, order = 0))
        (0 until starters).forEach { groups = groups.assign(deck[it], "g1") }
        return DeckLenses.key(Lens.ROLES, deck, cards = { null }, groups = groups)
    }

    private fun assertClose(expected: Double, actual: Double, message: String = "") {
        assertTrue(abs(expected - actual) < 1e-9, "$message expected $expected, was $actual")
    }

    // ---- the number is the one the sheet would have given -------------------

    @Test
    fun oneKeyAgreesWithTheSingleCardFormula() {
        // DeckStatistics.openingHandOdds is the same question asked of a bare
        // copy count; the two must never disagree on screen.
        val odds = LensOdds.atLeastOne(rolesWith(12), deckSize = 40, handSize = 5)

        val complement = (0 until 5).fold(1.0) { acc, i -> acc * (28 - i) / (40 - i) }
        assertClose(1.0 - complement, odds.getValue("g1"))
    }

    @Test
    fun everyKeyGetsItsOwnQuestion() {
        // Not the joint probability of all of them at once — that is what a
        // stated goal is for, and conflating the two would quietly answer
        // something nobody asked.
        var groups = DeckGroups.EMPTY
            .upsert(DeckGroup("g1", "Starter", color = 2, order = 0))
            .upsert(DeckGroup("g2", "Handtrap", color = 5, order = 1))
        (0..11).forEach { groups = groups.assign(deck[it], "g1") }
        (12..17).forEach { groups = groups.assign(deck[it], "g2") }

        val keying = DeckLenses.key(Lens.ROLES, deck, cards = { null }, groups = groups)
        val odds = LensOdds.atLeastOne(keying, deckSize = 40)

        assertEquals(setOf("g1", "g2"), odds.keys)
        // Twelve starters is a likelier open than six handtraps, and both are
        // strictly more likely than opening both.
        assertTrue(odds.getValue("g1") > odds.getValue("g2"))
        assertTrue(odds.values.all { it > 0.0 && it < 1.0 })
    }

    // ---- the edges ----------------------------------------------------------

    @Test
    fun aKeyNothingFallsInIsNeverOpened() {
        val keying = rolesWith(starters = 0)

        // The group still exists in the bar, so it still gets a number, and the
        // number is zero rather than absent.
        assertClose(0.0, LensOdds.atLeastOne(keying, deckSize = 40).getValue("g1"))
    }

    @Test
    fun aKeyThatIsTheWholeDeckIsAlwaysOpened() {
        assertClose(1.0, LensOdds.atLeastOne(rolesWith(40), deckSize = 40).getValue("g1"))
    }

    @Test
    fun aBiggerHandNeverLowersTheOdds() {
        val keying = rolesWith(9)
        val five = LensOdds.atLeastOne(keying, 40, handSize = 5).getValue("g1")
        val six = LensOdds.atLeastOne(keying, 40, handSize = 6).getValue("g1")

        assertTrue(six > five, "$six should beat $five")
    }

    @Test
    fun anEmptyLensAsksNothing() {
        assertEquals(emptyMap(), LensOdds.atLeastOne(LensKeying.none(40), 40))
        assertEquals(emptyMap(), LensOdds.atLeastOne(rolesWith(3), deckSize = 0))
    }

    // ---- the counts the bar shows are the counts the odds use ---------------

    @Test
    fun sizesAreCountedOffTheKeyingNotTheDeck() {
        // A number that disagrees with the blocks under it is worse than none.
        val pool = listOf(card(1), card(2, "Spell Card"), card(3, "Trap Card"), card(4))
            .associateBy { it.id }
        val section = listOf(1, 1, 2, 3, 4).map(::CardId)

        val keying = DeckLenses.key(Lens.TYPE, section, pool::get, DeckGroups.EMPTY)
        val sizes = LensOdds.sizesOf(keying)

        assertEquals(3, sizes["monster"])
        assertEquals(1, sizes["spell"])
        assertEquals(1, sizes["trap"])
        assertEquals(section.size, sizes.values.sum())
    }
}
