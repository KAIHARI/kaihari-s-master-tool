package com.kaiharimoto.mastertool.core.export

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecklistSheetTest {

    private val pool = TestCards.all.associateBy { it.id }
    private fun cardOf(id: CardId): Card? = pool[id]

    private val ash = TestCards.ashBlossom.id
    private val maxx = TestCards.maxxC.id
    private val pot = TestCards.pot.id
    private val imperm = TestCards.infiniteImpermanence.id
    private val accesscode = TestCards.accesscode.id

    private fun sheet(deck: Deck, name: String = "Snake-Eye", format: String = "TCG") =
        DecklistSheet.render(name, deck, format, ::cardOf).decodeToString()

    private val deck = Deck(
        main = List(3) { ash } + List(3) { maxx } + List(2) { pot } + List(3) { imperm },
        extra = List(2) { accesscode },
        side = List(3) { pot },
    )

    @Test
    fun itIsAPdfAReaderWillOpen() {
        val out = sheet(deck)

        assertTrue(out.startsWith("%PDF"))
        assertTrue(out.trimEnd().endsWith("%%EOF"))
        assertTrue(out.all { it.code in 0..127 }, "the writer keeps the whole file inside ASCII")
    }

    @Test
    fun everyCardInTheDeckIsOnTheSheet() {
        val out = sheet(deck)

        listOf(TestCards.ashBlossom, TestCards.maxxC, TestCards.pot, TestCards.infiniteImpermanence)
            .forEach { assertTrue(it.name in out, "${it.name} is missing") }
        assertTrue(TestCards.accesscode.name in out, "the Extra deck is missing")
    }

    @Test
    fun theCardTypesGetTheBlocksARegistrationSheetHas() {
        val out = sheet(deck)

        listOf("MONSTERS", "SPELLS", "TRAPS", "EXTRA DECK", "SIDE DECK").forEach {
            assertTrue(it in out, "$it block is missing")
        }
    }

    @Test
    fun anEmptyTypeBlockIsStillPrinted() {
        // A judge reading a sheet with no trap block cannot tell it from a sheet
        // that forgot to have one.
        val out = sheet(Deck(main = List(3) { ash }))

        assertTrue("TRAPS" in out)
        assertTrue("SPELLS" in out)
    }

    @Test
    fun theSectionSizesAreOnTheHeader() {
        val out = sheet(deck)

        assertTrue("11 / 2 / 3" in out, "the three counts a judge checks first")
        assertTrue("TCG" in out)
    }

    @Test
    fun copiesAreCountedRatherThanListedOneByOne() {
        // The opposite of the siding sheet, on purpose: three Ash Blossom is one
        // line saying three, because this is read by somebody adding it up.
        val out = sheet(Deck(main = List(3) { ash }))

        assertEquals(1, out.windowed(TestCards.ashBlossom.name.length) {
            it == TestCards.ashBlossom.name
        }.count { it })
    }

    @Test
    fun aPasscodeTheDatabaseHasNeverHeardOfStillPrints() {
        // It shuffles, it counts towards forty, and a sheet that quietly left it
        // out would be a sheet that disagrees with the deck box.
        val stranger = CardId(99999999)
        val out = sheet(Deck(main = List(2) { stranger }))

        assertTrue("99999999" in out)
        assertTrue("NOT RECOGNISED" in out)
    }

    @Test
    fun aCardTheDatabaseKnowsButDoesNotCallAMonsterSpellOrTrapIsNotLost() {
        // The block is the complement of the other three rather than a fourth
        // rule, so nothing can fall between them.
        val out = sheet(Deck(main = listOf(TestCards.token.id)))

        assertTrue(TestCards.token.name in out, "a token in the Main deck vanished")
    }

    @Test
    fun aFullDeckFitsOnOnePage() {
        // Sixty singles is the widest a decklist gets, and the sheet is a thing
        // you hand over -- two pages is two things to lose.
        val many = (1..60).map { CardId(900000 + it) }
        val out = sheet(Deck(main = many, extra = List(15) { accesscode }, side = List(15) { pot }))

        assertEquals(1, out.split("/Type /Page ").size - 1, "the sheet ran onto a second page")
    }

    @Test
    fun aDeckTooLongForOnePageContinuesOnAnother() {
        // Not legal, but reachable: a decklist is edited before it is legal, and
        // a writer that drops the overflow is worse than one that adds a page.
        val many = (1..200).map { CardId(900000 + it) }
        val out = sheet(Deck(main = many))

        assertTrue(out.split("/Type /Page ").size - 1 > 1)
        assertTrue("DECKLIST, CONTINUED" in out)
        assertTrue("900200" in out, "the last card fell off the end")
    }

    @Test
    fun bothColumnsAreUsedRatherThanOneLongOne() {
        // A decklist that fits down one side leaves the other blank, which reads
        // as a sheet somebody forgot to finish. 297.5 is where the second column
        // starts on A4 with the margins this sheet uses.
        val out = sheet(deck)

        assertTrue("1 0 0 1 297.5 " in out, "everything was drawn in the left column")
    }

    @Test
    fun anEmptyDeckStillProducesASheet() {
        val out = sheet(Deck())

        assertTrue(out.startsWith("%PDF"))
        assertTrue("0 / 0 / 0" in out)
    }

    @Test
    fun theFieldsSomebodyFillsInByHandAreThere() {
        val out = sheet(deck)

        listOf("PLAYER", "EVENT", "TABLE").forEach { assertTrue(it in out, "$it field is missing") }
    }

    @Test
    fun aDeckWithNoNameSaysSoRatherThanPrintingNothing() {
        assertTrue("Untitled Deck" in sheet(deck, name = ""))
    }

    @Test
    fun aCardCalledSomethingWithBracketsInItDoesNotBreakTheFile() {
        // "Number 39: Utopia (Assault Mode)" is the shape that ends a PDF string
        // early if it is not escaped.
        val awkward = TestCards.monster(id = 12121212, name = "Utopia (Assault Mode) \\ Ray")
        val out = DecklistSheet.render(
            "Awkward",
            Deck(main = listOf(awkward.id)),
            "TCG",
        ) { if (it == awkward.id) awkward else null }.decodeToString()

        assertTrue(out.startsWith("%PDF"))
        assertTrue("Utopia \\(Assault Mode\\) \\\\ Ray" in out, "the name was not escaped")
    }
}
