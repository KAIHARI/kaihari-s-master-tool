package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairKeyTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)

    @Test
    fun aPairReadsTheSameFromEitherEnd() {
        // Two ways to say one thing. Storing both would mean a note written from
        // one card being invisible from the other.
        assertEquals(PairKey.of(ash, maxx), PairKey.of(maxx, ash))
    }

    @Test
    fun theSmallerPasscodeGoesFirstInTheFile() {
        assertEquals("14558127|23434538", assertNotNull(PairKey.of(maxx, ash)).key)
    }

    @Test
    fun aCardIsNotAPairWithItself() {
        assertNull(PairKey.of(ash, ash))
    }

    @Test
    fun aKeyComesBackOutTheWayItWentIn() {
        val pair = assertNotNull(PairKey.of(ash, maxx))

        assertEquals(pair, PairKey.parse(pair.key))
    }

    @Test
    fun somethingThatIsNotAPairIsIgnoredRatherThanCrashing() {
        // Files are written by other programs and edited by hand.
        listOf("", "14558127", "a|b", "14558127|", "1|2|3", "14558127|14558127").forEach {
            assertNull(PairKey.parse(it), it)
        }
    }
}

class PairNotesTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)
    private val pot = CardId(55144522)

    @Test
    fun aNoteIsFoundFromEitherCard() {
        val notes = PairNotes.NONE.with(ash, maxx, "both, and it is a hand trap war")

        assertEquals("both, and it is a hand trap war", notes.of(ash, maxx))
        assertEquals("both, and it is a hand trap war", notes.of(maxx, ash))
    }

    @Test
    fun clearingTheBoxIsHowAPairNoteIsDeleted() {
        val notes = PairNotes.NONE.with(ash, maxx, "something").with(maxx, ash, "  ")

        assertTrue(notes.isEmpty)
    }

    @Test
    fun aCardPairedWithItselfIsNotWritten() {
        assertTrue(PairNotes.NONE.with(ash, ash, "nonsense").isEmpty)
    }

    @Test
    fun aCardListsEveryPairItIsHalfOf() {
        val notes = PairNotes.NONE
            .with(ash, maxx, "a")
            .with(ash, pot, "b")
            .with(maxx, pot, "c")

        assertEquals(2, notes.about(ash).size)
        assertEquals(listOf("a", "b"), notes.about(ash).map { it.second })
        assertEquals(2, notes.about(maxx).size, "and from the other end of them")
    }

    @Test
    fun aCardWithNoPairsListsNothing() {
        val notes = PairNotes.NONE.with(ash, maxx, "a")

        assertEquals(emptyList(), notes.about(pot))
    }

    @Test
    fun theOtherHalfOfAPairIsWhicheverOneYouDidNotAskAbout() {
        val pair = assertNotNull(PairKey.of(ash, maxx))
        val notes = PairNotes.NONE.with(ash, maxx, "a")

        assertEquals(maxx, notes.other(pair, than = ash))
        assertEquals(ash, notes.other(pair, than = maxx))
    }
}

class PairNotesCodecTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)

    private fun payload(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun notesComeBackOutTheWayTheyWentIn() {
        val notes = PairNotes.NONE.with(ash, maxx, "Poplar into Oak is Apollousa")

        assertEquals(notes, PairNotesCodec.read(PairNotesCodec.write(null, notes)))
    }

    @Test
    fun nothingInThePayloadIsNoNotes() {
        assertEquals(PairNotes.NONE, PairNotesCodec.read(null))
        assertEquals(PairNotes.NONE, PairNotesCodec.read(payload("""{"notes":{"cards":{}}}""")))
    }

    @Test
    fun aFileWithNothingToSayGrowsNoEmptyObject() {
        val out = PairNotesCodec.write(payload("""{"version":"1.0"}"""), PairNotes.NONE)

        assertTrue("notes" !in out)
    }

    @Test
    fun theTwoHalvesOfTheNotesObjectComposeInEitherOrder() {
        // The point of keeping them as separate codecs: each owns one key and
        // preserves the other's, so neither can destroy the half it does not
        // understand -- which is the payload rule, applied to two codecs that
        // now share an object.
        val cards = CardNotes.NONE.with(ash, "handtrap")
        val pairs = PairNotes.NONE.with(ash, maxx, "both is a war")

        val cardsFirst = PairNotesCodec.write(CardNotesCodec.write(null, cards), pairs)
        val pairsFirst = CardNotesCodec.write(PairNotesCodec.write(null, pairs), cards)

        assertEquals(cardsFirst, pairsFirst)
        listOf(cardsFirst, pairsFirst).forEach {
            assertEquals(cards, CardNotesCodec.read(it))
            assertEquals(pairs, PairNotesCodec.read(it))
        }
    }

    @Test
    fun takingEveryPairAwayLeavesTheCardNotesBehind() {
        val both = PairNotesCodec.write(
            CardNotesCodec.write(null, CardNotes.NONE.with(ash, "kept")),
            PairNotes.NONE.with(ash, maxx, "gone"),
        )

        val after = PairNotesCodec.write(both, PairNotes.NONE)

        assertEquals(PairNotes.NONE, PairNotesCodec.read(after))
        assertEquals("kept", CardNotesCodec.read(after)[ash])
    }

    @Test
    fun aKeyThatIsNotAPairIsIgnoredRatherThanCrashing() {
        val out = PairNotesCodec.read(
            payload("""{"notes":{"pairs":{"nonsense":"x","14558127|23434538":"kept"}}}"""),
        )

        assertEquals(1, out.size)
        assertEquals("kept", out.of(ash, maxx))
    }

    @Test
    fun theKeysComeOutInAStableOrder() {
        val notes = PairNotes.NONE
            .with(maxx, CardId(99999999), "b")
            .with(ash, maxx, "a")

        val written = (PairNotesCodec.write(null, notes)["notes"] as JsonObject)["pairs"] as JsonObject
        assertEquals(listOf("14558127|23434538", "23434538|99999999"), written.keys.toList())
    }
}
