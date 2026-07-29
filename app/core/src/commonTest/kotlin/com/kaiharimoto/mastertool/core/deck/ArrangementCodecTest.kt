package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrangementCodecTest {

    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun aPayloadWithNoArrangementHasNoGaps() {
        assertEquals(emptyMap(), ArrangementCodec.read(null))
        assertEquals(emptyMap(), ArrangementCodec.read(json("""{"somethingElse": 1}""")))
    }

    @Test
    fun gapsAreReadPerSection() {
        val extended = json("""{"arrangement": {"main": [9, 15], "side": [3]}}""")

        assertEquals(
            mapOf(
                DeckSection.MAIN to Breaks(setOf(9, 15)),
                DeckSection.SIDE to Breaks(setOf(3)),
            ),
            ArrangementCodec.read(extended),
        )
    }

    @Test
    fun aSectionWithNoGapsIsNotInTheMap() {
        val extended = json("""{"arrangement": {"main": [], "extra": [0]}}""")

        assertEquals(emptyMap(), ArrangementCodec.read(extended))
    }

    @Test
    fun rubbishInTheFileIsSkippedRatherThanFatal() {
        // A file that is a little wrong should still open, which is the same
        // promise the rest of this payload makes.
        val extended = json(
            """{"arrangement": {"main": [4, "nine", null, -2, 7], "side": "no"}}""",
        )

        assertEquals(mapOf(DeckSection.MAIN to Breaks(setOf(4, 7))), ArrangementCodec.read(extended))
    }

    @Test
    fun writingAndReadingBackGivesTheSameGaps() {
        val breaks = mapOf(
            DeckSection.MAIN to Breaks(setOf(9, 15)),
            DeckSection.EXTRA to Breaks(setOf(6)),
        )

        assertEquals(breaks, ArrangementCodec.read(ArrangementCodec.write(null, breaks)))
    }

    @Test
    fun writingKeepsEveryKeyItDoesNotUnderstand() {
        val extended = json("""{"sidingPatterns": {"Snake-Eye": {}}, "somethingNew": 42}""")

        val written = ArrangementCodec.write(extended, mapOf(DeckSection.MAIN to Breaks(setOf(9))))

        assertTrue("sidingPatterns" in written)
        assertEquals(extended["somethingNew"], written["somethingNew"])
        assertEquals(mapOf(DeckSection.MAIN to Breaks(setOf(9))), ArrangementCodec.read(written))
    }

    @Test
    fun takingEveryGapAwayTakesTheKeyWithIt() {
        val extended = ArrangementCodec.write(null, mapOf(DeckSection.MAIN to Breaks(setOf(9))))

        val cleared = ArrangementCodec.write(extended, mapOf(DeckSection.MAIN to Breaks.NONE))

        assertTrue("arrangement" !in cleared, "a deck nobody arranged carries nothing")
        assertEquals(emptyMap(), ArrangementCodec.read(cleared))
    }

    @Test
    fun theNumbersComeOutInOrderEveryTime() {
        // A file that reorders its own numbers between saves is a file that
        // looks like it changed when it did not.
        val breaks = mapOf(DeckSection.MAIN to Breaks(setOf(15, 3, 9)))

        val once = ArrangementCodec.write(null, breaks).toString()
        val twice = ArrangementCodec.write(null, breaks).toString()

        assertEquals(once, twice)
        assertTrue(once.contains("[3,9,15]"), once)
    }
}
