package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardNotesTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)

    @Test
    fun aNoteIsKeptAgainstTheCard() {
        val notes = CardNotes.NONE.with(ash, "only starter that plays through Ash")

        assertEquals("only starter that plays through Ash", notes[ash])
        assertTrue(notes.has(ash))
        assertNull(notes[maxx])
    }

    @Test
    fun writingOverANoteReplacesIt() {
        val notes = CardNotes.NONE.with(ash, "first").with(ash, "second")

        assertEquals("second", notes[ash])
        assertEquals(1, notes.size)
    }

    @Test
    fun clearingTheBoxIsHowANoteIsDeleted() {
        // No second gesture to find, and no file accumulating empty strings
        // against cards nobody has anything to say about.
        val notes = CardNotes.NONE.with(ash, "something").with(ash, "   ")

        assertTrue(notes.isEmpty)
    }

    @Test
    fun deletingSomethingThatWasNeverThereChangesNothing() {
        assertEquals(CardNotes.NONE, CardNotes.NONE.with(ash, ""))
    }

    @Test
    fun surroundingSpaceIsNotPartOfWhatWasWritten() {
        assertEquals("handtrap", CardNotes.NONE.with(ash, "  handtrap \n")[ash])
    }

    @Test
    fun aNoteIsNeverSweptForBelongingToACardThatLeft() {
        // There is no tidy, on purpose: taking the last copy out and putting it
        // back happens constantly while building, and autosave *is* writing the
        // file, so there is no later moment at which sweeping would be safe.
        val notes = CardNotes.NONE.with(ash, "a").with(maxx, "b")

        assertEquals(setOf(ash, maxx), notes.byCard.keys)
    }
}

class CardNotesCodecTest {

    private val ash = CardId(14558127)
    private val maxx = CardId(23434538)

    private fun payload(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun nothingInThePayloadIsNoNotes() {
        assertEquals(CardNotes.NONE, CardNotesCodec.read(null))
        assertEquals(CardNotes.NONE, CardNotesCodec.read(payload("""{"version":"1.0"}""")))
        assertEquals(CardNotes.NONE, CardNotesCodec.read(payload("""{"notes":{"pairs":{}}}""")))
    }

    @Test
    fun notesComeBackOutTheWayTheyWentIn() {
        val notes = CardNotes.NONE.with(ash, "plays through Ash").with(maxx, "third copy is a brick")

        assertEquals(notes, CardNotesCodec.read(CardNotesCodec.write(null, notes)))
    }

    @Test
    fun theOtherHalfOfTheNotesObjectSurvives() {
        // The payload rule, one level down. `pairs` is the original tool's idea
        // and a codec that rebuilt `notes` from what it understood would delete
        // it -- which is harder to notice than losing an unknown top-level key,
        // because this one looks like a key we own.
        val before = payload("""{"notes":{"pairs":{"1|2":"combo"},"somethingElse":7},"version":"1.0"}""")

        val after = CardNotesCodec.write(before, CardNotes.NONE.with(ash, "note"))
        val notes = after["notes"] as JsonObject

        assertTrue("pairs" in notes, "the pair notes were destroyed")
        assertTrue("somethingElse" in notes)
        assertTrue("version" in after, "a top-level key was destroyed")
        assertEquals("note", CardNotesCodec.read(after)[ash])
    }

    @Test
    fun aFileWithNothingToSayGrowsNoEmptyObject() {
        val out = CardNotesCodec.write(payload("""{"version":"1.0"}"""), CardNotes.NONE)

        assertTrue("notes" !in out, "every file this app touched would gain an empty notes object")
    }

    @Test
    fun takingEveryNoteAwayLeavesTheOtherHalfBehind() {
        val before = payload("""{"notes":{"cards":{"14558127":"x"},"pairs":{"a":"b"}}}""")

        val after = CardNotesCodec.write(before, CardNotes.NONE)

        assertEquals(CardNotes.NONE, CardNotesCodec.read(after))
        assertTrue("pairs" in (after["notes"] as JsonObject))
    }

    @Test
    fun aKeyThatIsNotAPasscodeIsIgnoredRatherThanCrashing() {
        // Files are edited by hand and written by other programs.
        val out = CardNotesCodec.read(
            payload("""{"notes":{"cards":{"not-a-number":"x","14558127":"kept","23434538":null}}}"""),
        )

        assertEquals(1, out.size)
        assertEquals("kept", out[ash])
    }

    @Test
    fun anEmptyNoteInAFileIsNotReadBackAsANote() {
        val out = CardNotesCodec.read(payload("""{"notes":{"cards":{"14558127":"   "}}}"""))

        assertTrue(out.isEmpty)
    }

    @Test
    fun theKeysComeOutInAStableOrder() {
        // A file that reorders its own keys between saves is a file that looks
        // like it changed.
        val notes = CardNotes.NONE.with(maxx, "b").with(ash, "a")

        val written = (CardNotesCodec.write(null, notes)["notes"] as JsonObject)["cards"] as JsonObject
        assertEquals(listOf("14558127", "23434538"), written.keys.toList())
    }
}
