package com.kaiharimoto.mastertool.core.ydk

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
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
        val out = DecklistText.parse("2 Extra Foolish Burial", exactly = {
            if (TextMatching.normalize(it) == TextMatching.normalize(foolish.name)) foolish else null
        })

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

/**
 * The other half: writing the text people paste to each other.
 *
 * Every test here is really the same test — that the writer and the reader agree
 * — because a decklist that survives a round trip is the only kind worth handing
 * to somebody.
 */
class DecklistWritingTest {

    private val pool = TestCards.all.associateBy { it.id }
    private fun nameOf(id: CardId): String? = pool[id]?.name
    private fun resolve(name: String): Card? =
        TestCards.all.firstOrNull { TextMatching.normalize(it.name) == TextMatching.normalize(name) }

    private val ash = TestCards.ashBlossom.id
    private val maxx = TestCards.maxxC.id
    private val pot = TestCards.pot.id
    private val accesscode = TestCards.accesscode.id

    private val deck = Deck(
        main = List(3) { ash } + List(2) { maxx } + List(2) { pot },
        extra = List(2) { accesscode },
        side = List(3) { pot },
    )

    private fun written(d: Deck = deck, name: String = "Snake-Eye") =
        DecklistText.write(d, name, ::nameOf)

    private fun List<CardId>.tally(): Map<CardId, Int> = groupingBy { it }.eachCount()

    @Test
    fun itReadsBackAsTheSameDeck() {
        val there = written()
        val back = DecklistText.parse(there, ::resolve).deck

        // As multisets: a counted list cannot promise the arrangement back, and
        // pretending otherwise is what this test exists to not do.
        assertEquals(deck.main.tally(), back.main.tally())
        assertEquals(deck.extra.tally(), back.extra.tally())
        assertEquals(deck.side.tally(), back.side.tally())
    }

    @Test
    fun theSectionsAreNamedTheWayItReadsThemBack() {
        val out = written()

        assertTrue("Main Deck" in out)
        assertTrue("Extra Deck" in out)
        assertTrue("Side Deck" in out)
    }

    @Test
    fun copiesAreCountedRatherThanRepeated() {
        val out = written()

        assertTrue("3 Ash Blossom & Joyous Spring" in out, out)
        assertEquals(1, out.lines().count { it.contains("Ash Blossom") })
    }

    @Test
    fun theDeckNameGoesOutAsAComment() {
        // A name is not part of a decklist, and reading one back in as a card
        // would be absurd.
        val out = written()

        assertTrue(out.startsWith("# Snake-Eye"))
        assertTrue(DecklistText.parse(out, ::resolve).missing.isEmpty())
    }

    @Test
    fun aDeckWithNoNameStillWrites() {
        val out = written(name = "  ")

        assertFalse(out.startsWith("#"))
        assertTrue("Main Deck" in out)
    }

    @Test
    fun anEmptySectionIsLeftOutRatherThanWrittenEmpty() {
        val out = written(Deck(main = List(3) { ash }))

        assertTrue("Main Deck" in out)
        assertFalse("Extra Deck" in out)
        assertFalse("Side Deck" in out)
    }

    @Test
    fun anEmptyDeckIsEmptyText() {
        assertEquals("", written(Deck(), name = ""))
    }

    @Test
    fun aPasscodeThePoolHasNeverHeardOfSurvivesTheRoundTrip() {
        // This is what makes the trip total. A card with no name is written as
        // its number, and a number needs no database to be read back.
        val stranger = CardId(99999999)
        val out = DecklistText.write(Deck(main = List(2) { stranger }), "Odd", ::nameOf)

        assertTrue("2 99999999" in out, out)
        assertEquals(List(2) { stranger }, DecklistText.parse(out, ::resolve).deck.main)
    }

    @Test
    fun aStrangerInTheExtraDeckStaysInTheExtraDeck() {
        // Without the heading there would be nothing to say where it goes: a
        // passcode alone cannot say what frame it has.
        val stranger = CardId(99999999)
        val out = DecklistText.write(Deck(extra = listOf(stranger)), "Odd", ::nameOf)

        assertEquals(listOf(stranger), DecklistText.parse(out, ::resolve).deck.extra)
    }

    @Test
    fun theOrderOfFirstAppearanceIsKept() {
        // As close as a counted list gets to an arrangement: whatever the engine
        // starts on still leads its section.
        val out = DecklistText.write(
            Deck(main = listOf(pot, ash, pot, ash, ash)),
            "Order",
            ::nameOf,
        )

        assertTrue(out.indexOf("Pot of Prosperity") < out.indexOf("Ash Blossom"), out)
    }
}

/**
 * A `.ydk` file opened in a text editor and pasted straight in, which is one of
 * the commonest shapes a decklist is handed over in.
 */
class PastedYdkTest {

    private fun resolve(name: String): Card? =
        TestCards.all.firstOrNull { TextMatching.normalize(it.name) == TextMatching.normalize(name) }

    @Test
    fun theFormatsOwnSectionMarkersAreHeadingsRatherThanComments() {
        // `#main`, `#extra` and `!side` start with the characters this treats as
        // comments everywhere else. Skipped, the Extra deck arrives in the Main.
        val out = DecklistText.parse(
            """
            #created by ...
            #main
            ${TestCards.ashBlossom.id.value}
            ${TestCards.ashBlossom.id.value}
            #extra
            ${TestCards.accesscode.id.value}
            !side
            ${TestCards.pot.id.value}
            """.trimIndent(),
            ::resolve,
        )

        assertEquals(List(2) { TestCards.ashBlossom.id }, out.deck.main)
        assertEquals(listOf(TestCards.accesscode.id), out.deck.extra)
        assertEquals(listOf(TestCards.pot.id), out.deck.side)
        assertTrue(out.missing.isEmpty())
    }

    @Test
    fun andACommentThatMerelyStartsWithAHeadingWordIsStillAComment() {
        val out = DecklistText.parse(
            "#extra copies to cut before the event\n2 Pot of Prosperity",
            ::resolve,
        )

        assertEquals(2, out.deck.main.size, "the note was read as an Extra Deck heading")
        assertTrue(out.deck.extra.isEmpty())
    }
}
