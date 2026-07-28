package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.ydk.YdkCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The whole siding path, on a file shaped like the real thing.
 *
 * `SidingCodecTest` checks the payload in isolation and `SidingEngineTest` checks
 * the swap in isolation. This is the part neither can see: that a `.ydkx` opened,
 * sided, and written back is still a `.ydkx` the tool that produced it would
 * recognise — which is the only claim a player actually depends on, because the
 * file goes back to the desktop afterwards.
 */
class SidingRoundTripTest {

    /** Trimmed from `lab.ydkx`, keeping its shape and its unmodelled keys. */
    private val file = """
        #created by kai
        #main
        14558127
        14558127
        23434538
        23434538
        27204311
        #extra
        86066372
        !side
        94145021
        94145021
        10045474
        #ydkx-extended
        {
          "version": "1.0",
          "cardPool": ["14558127", "23434538"],
          "configurations": { "deckScale": 10, "scaleTarget": "main" },
          "notes": { "cards": {}, "pairs": {} },
          "sidingPatterns": {
            "Snake-Eye": {
              "deckName": "Snake-Eye",
              "associatedYdk": "Snake-Eye.ydk",
              "backgroundColor": "linear-gradient(135deg, #ea580c 0%, #c2410c 100%)",
              "thumbnails": ["14558127", "23434538", "27204311"],
              "sourceDeckIds": ["14558127", "80538047"],
              "category": "uncategorized",
              "tags": [],
              "lastUsed": null,
              "goingFirst": { "in": ["94145021", "94145021"], "out": ["23434538", "23434538"] },
              "goingSecond": { "in": ["10045474"], "out": ["27204311"] }
            }
          }
        }
    """.trimIndent()

    private fun reopen(text: String) = YdkCodec.parse(text).document

    @Test
    fun aFileOpensWithItsPlansIntact() {
        val document = reopen(file)
        val patterns = SidingCodec.read(document.extended)

        assertTrue(document.isYdkx)
        assertEquals(setOf("Snake-Eye"), patterns.keys)
        assertEquals(2, patterns.getValue("Snake-Eye").goingFirst.cardsIn.size)
    }

    @Test
    fun sidingAndSavingProducesAFileThatOpensTheSameWay() {
        val document = reopen(file)
        val pattern = SidingCodec.read(document.extended).getValue("Snake-Eye")

        val sided = SidingEngine.apply(document.deck, pattern, goingFirst = true)
        assertTrue(sided.isClean, "unexpected problems: ${sided.problems}")

        val written = YdkCodec.write(sided.deck, createdBy = "test", extended = document.extended)
        val reopened = reopen(written)

        // The deck that comes back is the one that was sided.
        assertEquals(sided.deck, reopened.deck)
        // And the plans came with it.
        assertEquals(
            SidingCodec.read(document.extended),
            SidingCodec.read(reopened.extended),
        )
    }

    @Test
    fun theDesktopToolsOwnKeysSurviveTheTrip() {
        // The claim the README makes, end to end: this app does not understand a
        // background gradient or a category and must not eat them.
        val document = reopen(file)
        val patterns = SidingCodec.read(document.extended)

        val written = YdkCodec.write(
            document.deck,
            createdBy = "test",
            extended = SidingCodec.write(document.extended, patterns),
        )
        val payload = reopen(written).extended!!
        val plan = payload["sidingPatterns"]!!.jsonObject["Snake-Eye"]!!.jsonObject

        assertEquals(
            "linear-gradient(135deg, #ea580c 0%, #c2410c 100%)",
            plan["backgroundColor"]!!.jsonPrimitive.content,
        )
        assertEquals("uncategorized", plan["category"]!!.jsonPrimitive.content)
        assertTrue(plan.containsKey("sourceDeckIds"))
        // And the payload's own keys, a level up.
        assertTrue(payload.containsKey("cardPool"))
        assertTrue(payload.containsKey("configurations"))
        assertTrue(payload.containsKey("notes"))
    }

    @Test
    fun sidingSwapsTheRightCardsInTheRightPlaces() {
        val document = reopen(file)
        val pattern = SidingCodec.read(document.extended).getValue("Snake-Eye")
        val sided = SidingEngine.apply(document.deck, pattern, goingFirst = true).deck

        val maxx = CardId(23434538)
        val droll = CardId(94145021)

        assertEquals(0, sided.main.count { it == maxx }, "both copies should have left")
        assertEquals(2, sided.main.count { it == droll })
        assertEquals(2, sided.side.count { it == maxx }, "and arrived in the side deck")
        assertEquals(document.deck.main.size, sided.main.size, "a balanced swap keeps the size")
    }

    @Test
    fun theArrangementIsPreservedAcrossTheSwap() {
        // The deck order is what gets written back to the file, so a swap that
        // reshuffled it would rewrite an arrangement its owner chose.
        val document = reopen(file)
        val pattern = SidingCodec.read(document.extended).getValue("Snake-Eye")
        val sided = SidingEngine.apply(document.deck, pattern, goingFirst = true).deck

        val ash = CardId(14558127)
        val nib = CardId(27204311)

        // The cards that did not move are still where they were.
        assertEquals(listOf(ash, ash), sided.main.take(2))
        assertEquals(nib, sided.main.last())
    }

    @Test
    fun goingSecondIsADifferentFileAgain() {
        val document = reopen(file)
        val pattern = SidingCodec.read(document.extended).getValue("Snake-Eye")

        val first = SidingEngine.apply(document.deck, pattern, goingFirst = true).deck
        val second = SidingEngine.apply(document.deck, pattern, goingFirst = false).deck

        assertTrue(first != second)
        assertTrue(CardId(10045474) in second.main)
    }

    @Test
    fun sidingTwiceFromTheSameStartIsTheSameDeck() {
        // Applying a plan is not supposed to depend on anything but the deck it
        // is applied to.
        val document = reopen(file)
        val pattern = SidingCodec.read(document.extended).getValue("Snake-Eye")

        assertEquals(
            SidingEngine.apply(document.deck, pattern, goingFirst = true).deck,
            SidingEngine.apply(document.deck, pattern, goingFirst = true).deck,
        )
    }

    @Test
    fun aPlainYdkHasNoPlansAndIsNotBroken() {
        // Most files are not .ydkx. Opening one must simply mean no siding.
        val plain = reopen("#main\n14558127\n#extra\n!side\n")

        assertTrue(!plain.isYdkx)
        assertTrue(SidingCodec.read(plain.extended).isEmpty())
    }
}
