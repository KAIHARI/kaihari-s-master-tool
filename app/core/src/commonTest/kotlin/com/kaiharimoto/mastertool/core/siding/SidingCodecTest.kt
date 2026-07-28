package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SidingCodecTest {

    private val json = Json

    private fun payload(text: String) = json.parseToJsonElement(text) as JsonObject

    /** Shaped like the real thing, extra keys and all. Trimmed from `lab.ydkx`. */
    private val realWorld = payload(
        """
        {
          "version": "1.0",
          "notes": { "cards": {}, "pairs": {} },
          "sidingPatterns": {
            "Fiendsmith Radiant Typhoon": {
              "deckName": "Fiendsmith Radiant Typhoon",
              "associatedYdk": "Fiendsmith Radiant Typhoon.ydk",
              "backgroundColor": "linear-gradient(135deg, #ea580c 0%, #c2410c 100%)",
              "thumbnails": ["85315450", "12197223", "16922142"],
              "sourceDeckIds": ["85315450", "80538047"],
              "category": "uncategorized",
              "tags": [],
              "lastUsed": null,
              "goingFirst": {
                "in": ["49238328", "14558127", "49238328"],
                "out": ["30748475", "30748475", "60990740"]
              },
              "goingSecond": { "in": ["9822220"], "out": ["30748475"] }
            }
          }
        }
        """,
    )

    @Test
    fun readsARealPattern() {
        val patterns = SidingCodec.read(realWorld)
        val pattern = patterns.getValue("Fiendsmith Radiant Typhoon")

        assertEquals("Fiendsmith Radiant Typhoon", pattern.deckName)
        assertEquals("Fiendsmith Radiant Typhoon.ydk", pattern.associatedYdk)
        assertEquals(listOf(85315450, 12197223, 16922142).map(::CardId), pattern.thumbnails)
        assertEquals(3, pattern.goingFirst.cardsIn.size)
        assertEquals(listOf(CardId(9822220)), pattern.goingSecond.cardsIn)
    }

    @Test
    fun keepsRepeatedCopiesRatherThanCounting() {
        // Siding out two of three is a different decision from siding out all
        // three, so the duplicate passcodes are the data, not noise.
        val swap = SidingCodec.read(realWorld).values.first().goingFirst

        assertEquals(listOf(CardId(49238328), CardId(14558127), CardId(49238328)), swap.cardsIn)
        assertEquals(2, swap.cardsOut.count { it == CardId(30748475) })
    }

    // The promise this codec exists to keep.

    @Test
    fun writingBackKeepsEveryKeyThisAppDoesNotUnderstand() {
        val patterns = SidingCodec.read(realWorld)
        val after = SidingCodec.write(realWorld, patterns)

        val pattern = after["sidingPatterns"]!!.jsonObject["Fiendsmith Radiant Typhoon"]!!.jsonObject

        assertEquals(
            "linear-gradient(135deg, #ea580c 0%, #c2410c 100%)",
            pattern["backgroundColor"]!!.jsonPrimitive.content,
        )
        assertEquals("uncategorized", pattern["category"]!!.jsonPrimitive.content)
        assertTrue(pattern.containsKey("sourceDeckIds"))
        assertTrue(pattern.containsKey("tags"))
        assertTrue(pattern.containsKey("lastUsed"))
    }

    @Test
    fun writingBackKeepsTheRestOfThePayload() {
        val after = SidingCodec.write(realWorld, SidingCodec.read(realWorld))

        assertEquals("1.0", after["version"]!!.jsonPrimitive.content)
        assertTrue(after.containsKey("notes"))
    }

    @Test
    fun aPatternSurvivesAFullRoundTrip() {
        val once = SidingCodec.read(realWorld)
        val twice = SidingCodec.read(SidingCodec.write(realWorld, once))

        assertEquals(once, twice)
    }

    @Test
    fun editingOnePatternLeavesTheOthersAlone() {
        val patterns = SidingCodec.read(realWorld).toMutableMap()
        patterns["Snake-Eye"] = SidingPattern(
            deckName = "Snake-Eye",
            goingFirst = SidingSwap(cardsIn = listOf(CardId(1)), cardsOut = listOf(CardId(2))),
        )

        val after = SidingCodec.read(SidingCodec.write(realWorld, patterns))

        assertEquals(2, after.size)
        assertEquals("Fiendsmith Radiant Typhoon.ydk", after.getValue("Fiendsmith Radiant Typhoon").associatedYdk)
        assertEquals(listOf(CardId(1)), after.getValue("Snake-Eye").goingFirst.cardsIn)
    }

    @Test
    fun aPatternDroppedFromTheMapIsDroppedFromTheFile() {
        val after = SidingCodec.read(SidingCodec.write(realWorld, emptyMap()))
        assertTrue(after.isEmpty())
    }

    // Permissive, for the same reason YdkCodec is: a file that is a little wrong
    // should still open.

    @Test
    fun aPayloadWithNoPatternsReadsAsNone() {
        assertTrue(SidingCodec.read(null).isEmpty())
        assertTrue(SidingCodec.read(payload("""{"version":"1.0"}""")).isEmpty())
    }

    @Test
    fun aPatternWithNothingUsableStillOpens() {
        val broken = payload(
            """
            {"sidingPatterns": {
              "Broken": {
                "thumbnails": ["not-a-passcode", null, 12345, {"nested": 1}],
                "goingFirst": "this should have been an object"
              }
            }}
            """,
        )
        val pattern = SidingCodec.read(broken).getValue("Broken")

        // Only the one entry that was a passcode survives; nothing throws.
        assertEquals(listOf(CardId(12345)), pattern.thumbnails)
        assertTrue(pattern.goingFirst.isEmpty)
    }

    @Test
    fun aPatternWithNoNameOfItsOwnBorrowsItsKey() {
        val unnamed = payload("""{"sidingPatterns": {"Branded Despia": {"thumbnails": []}}}""")

        assertEquals("Branded Despia", SidingCodec.read(unnamed).getValue("Branded Despia").deckName)
    }

    @Test
    fun anEntryThatIsNotAnObjectIsSkipped() {
        val odd = payload("""{"sidingPatterns": {"Fine": {}, "Odd": ["not", "an", "object"]}}""")
        val patterns = SidingCodec.read(odd)

        assertEquals(setOf("Fine"), patterns.keys)
    }

    @Test
    fun anUnlinkedPatternHasNoFileName() {
        val unlinked = payload("""{"sidingPatterns": {"Kashtira": {"associatedYdk": ""}}}""")

        assertNull(SidingCodec.read(unlinked).getValue("Kashtira").associatedYdk)
    }

    @Test
    fun writingIntoAnEmptyPayloadCreatesTheBlock() {
        val written = SidingCodec.write(null, mapOf("Ryzeal" to SidingPattern(deckName = "Ryzeal")))

        assertEquals("Ryzeal", SidingCodec.read(written).getValue("Ryzeal").deckName)
    }

    @Test
    fun passcodesAreWrittenAsStringsTheWayTheFileHasThem() {
        // The desktop tool reads these back; a bare number would be a new dialect.
        val written = SidingCodec.write(
            null,
            mapOf("X" to SidingPattern(thumbnails = listOf(CardId(14558127)))),
        )
        val thumbnails = written["sidingPatterns"]!!.jsonObject["X"]!!.jsonObject["thumbnails"]!!

        assertTrue(thumbnails.toString().contains("\"14558127\""), "was $thumbnails")
    }
}
