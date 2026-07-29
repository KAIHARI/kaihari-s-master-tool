package com.kaiharimoto.mastertool.core.library

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibrarySearchTest {

    private data class Saved(val name: String, val deck: Deck)

    private val pool = TestCards.all.associateBy { it.id }
    private fun nameOf(id: CardId): String? = pool[id]?.name

    private val ash = TestCards.ashBlossom.id
    private val maxx = TestCards.maxxC.id
    private val nibiru = TestCards.nibiru.id
    private val pot = TestCards.pot.id
    private val accesscode = TestCards.accesscode.id

    private val snakeEye = Saved(
        "Snake-Eye Fire King",
        Deck(main = List(3) { ash } + List(2) { pot }, extra = List(1) { accesscode }),
    )
    private val labrynth = Saved(
        "Labrynth",
        Deck(main = List(3) { maxx } + List(1) { nibiru }),
    )
    private val ryzeal = Saved(
        "Ryzeal",
        Deck(main = List(1) { ash } + List(3) { nibiru }),
    )

    private val shelf = listOf(snakeEye, labrynth, ryzeal)

    private fun find(query: String, decks: List<Saved> = shelf) =
        LibrarySearch.run(query, decks, Saved::name, Saved::deck, ::nameOf)

    @Test
    fun anEmptyQueryIsTheWholeShelfInTheOrderItArrived() {
        val out = find("")

        assertEquals(shelf, out.map { it.deck })
        assertTrue(out.all { it.cards.isEmpty() }, "nothing was searched for, so nothing matched")
    }

    @Test
    fun aDeckIsFoundByItsName() {
        val out = find("labrynth")

        assertEquals(listOf(labrynth), out.map { it.deck })
        assertTrue(out.single().byName)
    }

    @Test
    fun aDeckIsFoundByACardInIt() {
        // The question this exists for: which of these plays Maxx "C".
        val out = find("maxx")

        assertEquals(listOf(labrynth), out.map { it.deck })
        assertFalse(out.single().byName)
        assertEquals("Maxx \"C\"", out.single().cards.single().name)
    }

    @Test
    fun itSaysHowManyCopies() {
        val out = find("ash blossom")

        val copies = out.associate { it.deck.name to it.cards.single().copies }
        assertEquals(3, copies["Snake-Eye Fire King"])
        assertEquals(1, copies["Ryzeal"])
    }

    @Test
    fun aDeckCalledItComesBeforeADeckThatMerelyPlaysIt() {
        // "Nibiru" is both a deck on this shelf and a card in two others. The
        // deck by that name is the answer somebody typing it meant.
        val named = Saved("Nibiru", Deck(main = List(3) { pot }))
        val out = find("nibiru", shelf + named)

        assertEquals("Nibiru", out.first().deck.name)
        assertTrue(out.first().byName)
        assertTrue(out.drop(1).none { it.byName }, "the rest are card matches")
        assertEquals(3, out.size)
    }

    @Test
    fun twoDecksThatMatchTheSameWayKeepTheOrderTheShelfWasIn() {
        // Ryzeal runs three Nibiru and Labrynth runs one, and that does not
        // reorder them: the shelf arrives most-recently-edited first, and the
        // deck touched yesterday is a better guess at which one was meant than
        // the deck running more copies of the card that was typed.
        val out = find("nibiru")

        assertEquals(listOf(labrynth, ryzeal), out.map { it.deck })
        assertEquals(listOf(1, 3), out.map { it.cards.single().copies })
    }

    @Test
    fun aTwoLetterQueryDoesNotSearchTheCards() {
        // Every card starting with "ma" is most of them, and a shelf where each
        // deck reports forty hits is a shelf with no search on it.
        val out = find("ma")

        assertTrue(out.all { it.cards.isEmpty() })
    }

    @Test
    fun aDeckThatMatchesBothWaysSaysBoth() {
        val out = find("ash", listOf(Saved("Ash Control", Deck(main = List(3) { ash }))))

        val hit = out.single()
        assertTrue(hit.byName)
        assertEquals(3, hit.cards.single().copies)
    }

    @Test
    fun theBestMatchInADeckIsListedFirst() {
        val mixed = Saved(
            "Mixed",
            Deck(main = List(1) { pot } + List(3) { ash }),
        )
        // "Pot" is exact for Pot of Prosperity's first word; Ash Blossom does not
        // contain it at all, so only one card can answer.
        val out = find("pot", listOf(mixed))

        assertEquals("Pot of Prosperity", out.single().cards.first().name)
    }

    @Test
    fun aPasscodeTheDatabaseHasNotHeardOfMatchesNothing() {
        // The pool has not loaded, not "the deck does not contain it". Matching
        // on the digits would answer a question nobody asked.
        val stranger = Saved("Untitled", Deck(main = List(3) { CardId(99999999) }))
        val out = find("999", listOf(stranger))

        assertTrue(out.isEmpty())
    }

    @Test
    fun aDeckMatchingNothingIsLeftOut() {
        assertTrue(find("dinomorphia").isEmpty())
    }

    @Test
    fun punctuationDoesNotHaveToBeTyped() {
        // Deck names are typed by hand and remembered approximately, and card
        // names are full of punctuation nobody reproduces.
        assertEquals(listOf(snakeEye), find("snake eye fire").map { it.deck })
        assertEquals(listOf(labrynth), find("maxx c").map { it.deck })
    }

    @Test
    fun theExtraAndSideDecksAreSearchedToo() {
        // A card can only be in the Side deck and still be the thing you are
        // looking for -- more so, since the Side is what changes between events.
        val sided = Saved("Sided", Deck(main = List(40) { pot }, side = List(2) { ash }))
        val out = find("ash blossom", listOf(sided))

        assertEquals(2, out.single().cards.single().copies)
        assertEquals(listOf(snakeEye), find("accesscode", shelf).map { it.deck })
    }

    @Test
    fun copiesCountEverySectionAtOnce() {
        val split = Saved("Split", Deck(main = List(2) { ash }, side = List(1) { ash }))

        assertEquals(3, find("ash blossom", listOf(split)).single().cards.single().copies)
    }
}
