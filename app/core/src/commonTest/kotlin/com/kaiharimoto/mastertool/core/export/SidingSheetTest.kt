package com.kaiharimoto.mastertool.core.export

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.siding.SidingPattern
import com.kaiharimoto.mastertool.core.siding.SidingSwap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidingSheetTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val droll = CardId(94145021)
    private val nib = CardId(27204311)

    private val names = mapOf(
        ash to "Ash Blossom & Joyous Spring",
        maxx to "Maxx \"C\"",
        droll to "Droll & Lock Bird",
        nib to "Nibiru, the Primal Being",
    )
    private val nameOf: (CardId) -> String? = { names[it] }

    private val snakeEye = SidingPattern(
        deckName = "Snake-Eye",
        goingFirst = SidingSwap(cardsIn = listOf(droll, droll), cardsOut = listOf(maxx, maxx)),
        goingSecond = SidingSwap(cardsIn = listOf(nib), cardsOut = listOf(ash)),
    )

    private fun sheet(
        patterns: List<SidingPattern>,
        deckName: String = "Kashtira",
    ): String = SidingSheet.render(deckName, patterns, nameOf).decodeToString()

    @Test
    fun theSheetNamesTheDeckAndTheMatchups() {
        val out = sheet(listOf(snakeEye))

        assertTrue("(Kashtira) Tj" in out)
        assertTrue("(Snake-Eye) Tj" in out)
    }

    @Test
    fun everyCopyGetsItsOwnLine() {
        // Siding out two of three is a different decision from siding out all
        // three, and a count hides which one was meant.
        val out = sheet(listOf(snakeEye))

        assertEquals(2, Regex("""\(- {2}Maxx""").findAll(out).count())
        assertEquals(2, Regex("""\(\+ {2}Droll""").findAll(out).count())
    }

    @Test
    fun bothSidesOfTheDieRollArePrinted() {
        val out = sheet(listOf(snakeEye))

        assertTrue("(GOING FIRST) Tj" in out)
        assertTrue("(GOING SECOND) Tj" in out)
        assertTrue(
            out.indexOf("(GOING FIRST)") < out.indexOf("(GOING SECOND)"),
            "first is the one you decide about first",
        )
    }

    @Test
    fun theCountsAreOnTheSheet() {
        val out = sheet(listOf(snakeEye))

        assertTrue("(2 OUT / 2 IN) Tj" in out, "the number that stops a game loss")
        assertTrue("(1 OUT / 1 IN) Tj" in out)
    }

    @Test
    fun anUnevenSwapSaysSo() {
        // Four out and three in is a deck of thirty-nine, and nothing else on
        // the sheet would show it.
        val lopsided = SidingPattern(
            deckName = "Runick",
            goingFirst = SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(maxx, ash)),
        )

        val out = sheet(listOf(lopsided))

        assertTrue("UNEVEN" in out, out.substringAfter("stream").take(400))
    }

    @Test
    fun aCardThePoolHasNeverHeardOfPrintsItsPasscode() {
        // Worth more than a blank line: it is still enough to find the card.
        val stranger = CardId(99999999)
        val pattern = SidingPattern(
            deckName = "Unknown",
            goingFirst = SidingSwap(cardsIn = listOf(stranger), cardsOut = listOf(ash)),
        )

        val out = sheet(listOf(pattern))

        assertTrue("99999999" in out)
    }

    @Test
    fun aMatchupWithNoChangesOnOneSideSaysNothingRatherThanShowingNothing() {
        val oneSided = SidingPattern(
            deckName = "Mirror",
            goingFirst = SidingSwap(cardsIn = listOf(droll), cardsOut = listOf(maxx)),
        )

        val out = sheet(listOf(oneSided))

        assertTrue("(No changes.) Tj" in out)
    }

    @Test
    fun aDeckWithNoPlansStillProducesASheetThatSaysSo() {
        // An empty file that opens beats an export button that does nothing.
        val out = sheet(emptyList())

        assertTrue(out.startsWith("%PDF"))
        assertTrue("No siding plans recorded" in out)
    }

    @Test
    fun manyMatchupsRunOntoMorePages() {
        val many = List(12) { snakeEye.copy(deckName = "Matchup $it") }

        val out = sheet(many)

        assertTrue("/Count 1" !in out, "twelve matchups do not fit on one page")
        assertTrue("CONTINUED" in out)
    }

    @Test
    fun aMatchupIsNeverSplitAcrossAPageBreak() {
        // Reading half a plan and turning the page is exactly what a sheet is
        // meant to save you from.
        val big = SidingPattern(
            deckName = "Long",
            goingFirst = SidingSwap(cardsIn = List(15) { droll }, cardsOut = List(15) { maxx }),
            goingSecond = SidingSwap(cardsIn = List(15) { nib }, cardsOut = List(15) { ash }),
        )

        val out = sheet(listOf(big, big))
        val pages = Regex("/Type /Page[ /]").findAll(out).count()

        assertEquals(2, pages)
        // Each page carries one whole matchup, so each has both halves on it.
        assertEquals(2, Regex("""\(GOING SECOND\) Tj""").findAll(out).count())
    }
}
