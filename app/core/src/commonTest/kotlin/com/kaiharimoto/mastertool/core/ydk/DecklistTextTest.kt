package com.kaiharimoto.mastertool.core.ydk

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.search.TextMatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecklistTextTest {

    private val clawsScorpion = TestCards.monster(id = 14898066, name = "8-Claws Scorpion")
    private val completed = TestCards.monster(id = 10858126, name = "7 Completed")

    private val pool = TestCards.all + clawsScorpion + completed
    private val byName = pool.associateBy { TextMatching.normalize(it.name) }

    /** Exact on the normalised name, which is what a pasted list gets right. */
    private fun resolve(name: String): Card? = byName[TextMatching.normalize(name)]

    private fun read(text: String) = DecklistText.parse(text, ::resolve)

    private val ash = TestCards.ashBlossom
    private val maxx = TestCards.maxxC
    private val pot = TestCards.pot
    private val imperm = TestCards.infiniteImpermanence
    private val accesscode = TestCards.accesscode

    @Test
    fun aCountAndANamePerLine() {
        val out = read(
            """
            3 Ash Blossom & Joyous Spring
            2 Maxx "C"
            """.trimIndent(),
        )

        assertEquals(List(3) { ash.id } + List(2) { maxx.id }, out.deck.main)
        assertTrue(out.missing.isEmpty())
    }

    @Test
    fun everyShapeTheCountIsWrittenIn() {
        val out = read(
            """
            3x Ash Blossom & Joyous Spring
            2. Maxx "C"
            Pot of Prosperity x2
            Infinite Impermanence (3)
            """.trimIndent(),
        )

        assertEquals(3, out.deck.copiesOf(ash.id))
        assertEquals(2, out.deck.copiesOf(maxx.id))
        assertEquals(2, out.deck.copiesOf(pot.id))
        assertEquals(3, out.deck.copiesOf(imperm.id))
    }

    @Test
    fun aBareNameIsOneCopy() {
        assertEquals(1, read(ash.name).deck.copiesOf(ash.id))
    }

    @Test
    fun aCardWhoseNameStartsWithANumberIsNotACount() {
        // "8-Claws Scorpion" and "7 Completed" are cards, and they are written
        // in exactly the shape a count is. Asking whether the whole line is
        // already a card is the only thing that can tell them apart.
        val out = read("8-Claws Scorpion\n7 Completed")

        assertEquals(1, out.deck.copiesOf(clawsScorpion.id))
        assertEquals(1, out.deck.copiesOf(completed.id))
        assertTrue(out.missing.isEmpty())
    }

    @Test
    fun andStillTakesACountInFrontOfOne() {
        val out = read("3 8-Claws Scorpion\n2 7 Completed")

        assertEquals(3, out.deck.copiesOf(clawsScorpion.id))
        assertEquals(2, out.deck.copiesOf(completed.id))
    }

    @Test
    fun theExtraDeckIsFoundFromTheCardRatherThanFromAHeading() {
        // Most pasted lists have no headings at all, and a Link monster in the
        // Main deck is a decklist that will not save.
        val out = read("3 Ash Blossom & Joyous Spring\n1 Accesscode Talker")

        assertEquals(listOf(accesscode.id), out.deck.extra)
        assertEquals(3, out.deck.main.size)
    }

    @Test
    fun aHeadingOverridesWhereACardWouldGo() {
        val out = read(
            """
            Side Deck:
            2 Ash Blossom & Joyous Spring
            """.trimIndent(),
        )

        assertEquals(2, out.deck.side.size)
        assertTrue(out.deck.main.isEmpty())
    }

    @Test
    fun theHeadingsARegistrationSheetUsesAllMeanTheMainDeck() {
        val out = read(
            """
            Monsters (5)
            3 Ash Blossom & Joyous Spring
            Spells:
            2 Pot of Prosperity
            Traps
            1 Infinite Impermanence
            """.trimIndent(),
        )

        assertEquals(6, out.deck.main.size)
        assertTrue(out.deck.extra.isEmpty())
        assertTrue(out.deck.side.isEmpty())
    }

    @Test
    fun aCardWhoseNameBeginsWithAHeadingWordIsNotAHeading() {
        // "Extra Foolish Burial" is a card. A heading is one word plus the
        // decoration people put around it, and nothing else.
        val foolish = Card(
            id = CardId(10032958),
            name = "Extra Foolish Burial",
            type = "Spell Card",
            frameType = "spell",
        )
        val out = DecklistText.parse("2 Extra Foolish Burial") {
            if (TextMatching.normalize(it) == TextMatching.normalize(foolish.name)) foolish else null
        }

        assertEquals(2, out.deck.main.size, "it was read as an Extra Deck heading")
    }

    @Test
    fun aNameNothingIsCalledComesBackRatherThanBeingDropped() {
        // Sixty cards arriving as fifty-eight is worse than being told which two
        // did not, because the second one you can do something about.
        val out = read("3 Ash Blossom & Joyous Spring\n2 Dinomorphia Rexterm")

        assertEquals(3, out.deck.totalCards)
        assertEquals(listOf(DecklistText.Missing("Dinomorphia Rexterm", 2)), out.missing)
    }

    @Test
    fun blankLinesAndCommentsAreSkipped() {
        val out = read(
            """
            # Snake-Eye, locals 2026-07
            3 Ash Blossom & Joyous Spring

            // sided out game two
            2 Maxx "C"
            """.trimIndent(),
        )

        assertEquals(5, out.deck.main.size)
        assertTrue(out.missing.isEmpty())
    }

    @Test
    fun trailingCommasComeOffTheEndOfALine() {
        val out = read("3 Ash Blossom & Joyous Spring,\n2 Maxx \"C\",")

        assertEquals(5, out.deck.main.size)
        assertTrue(out.missing.isEmpty())
    }

    @Test
    fun copiesArriveTogetherRatherThanBeingCounted() {
        // The whole program stores a deck as an ordered multiset, and a paste is
        // an arrangement like any other. Three copies are three entries, in a row.
        val out = read("2 Maxx \"C\"\n3 Ash Blossom & Joyous Spring")

        assertEquals(
            List(2) { maxx.id } + List(3) { ash.id },
            out.deck.main,
            "the order the list was written in is the order it arrives in",
        )
    }

    @Test
    fun nothingAtAllIsNothingAtAll() {
        assertTrue(read("").isEmpty)
        assertTrue(read("   \n\n  ").isEmpty)
    }

    @Test
    fun aPageOfProseIsNotOfferedAsADecklist() {
        assertFalse(DecklistText.looksLikeAList(""))
        assertFalse(DecklistText.looksLikeAList("Ash Blossom"))
        assertTrue(DecklistText.looksLikeAList("3 Ash Blossom\n2 Maxx \"C\""))
    }

    @Test
    fun aNameCheckedAgainstWhatThePoolOffered() {
        // The pool's search is forgiving because you are watching it. Nothing is
        // watching sixty lines go past, so what it offers is checked.
        assertTrue(DecklistText.isTheSameName("Maxx \"C\"", "Maxx C"), "punctuation only")
        assertTrue(DecklistText.isTheSameName("Ash Blosom", "Ash Blossom"), "one typo")
        assertTrue(
            DecklistText.isTheSameName("Ash Blossom", "Ash Blossom & Joyous Spring"),
            "a long name cut short by whatever it was copied out of",
        )

        assertFalse(DecklistText.isTheSameName("", "Ash Blossom"))
        assertFalse(
            DecklistText.isTheSameName("Notes", "Nibiru, the Primal Being"),
            "a line that was never a card still has a nearest neighbour",
        )
        assertFalse(
            DecklistText.isTheSameName("Snake", "Snake-Eye Ash"),
            "five characters is the start of too many cards to pick one",
        )
        assertFalse(
            DecklistText.isTheSameName("Pot of Greed", "Pot of Prosperity"),
            "two real cards, not one card typed badly",
        )
    }

    @Test
    fun aCountOfZeroIsNotACount() {
        // "0 Maxx C" in a sideboard note means the card is not in the deck, and
        // reading it as a name of "Maxx C" with no copies would drop it silently.
        // Treated as part of the name instead, which then finds nothing and says so.
        val out = read("0 Maxx \"C\"")

        assertTrue(out.deck.isEmpty)
        assertEquals(1, out.missing.size)
    }
}
