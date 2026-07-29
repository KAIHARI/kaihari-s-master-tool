package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveTrackingTest {

    private fun deck(vararg values: Int) =
        Deck(main = values.map(::CardId), extra = emptyList(), side = emptyList())

    private fun status(
        current: Deck,
        name: String = "Deck",
        notes: String = "",
        saved: SavedSnapshot? = null,
        extended: JsonObject? = null,
    ) = SaveTracking.status(current, name, notes, saved, extended)

    private fun snapshot(
        deck: Deck,
        name: String = "Deck",
        notes: String = "",
        extended: JsonObject? = null,
    ) = SavedSnapshot(deck, name, notes, extended)

    private fun payload(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun anUntouchedDeckHasNothingToSay() {
        // "Nothing to lose yet" and "this has never been saved" look the same to
        // a dirty flag and are completely different things to tell somebody.
        assertEquals(SaveStatus.UNTOUCHED, status(Deck.EMPTY))
    }

    @Test
    fun workThatHasNeverBeenWrittenDownIsTheAlarmingState() {
        assertEquals(SaveStatus.NEVER_SAVED, status(deck(1)))
    }

    @Test
    fun matchingWhatIsOnDiskIsSaved() {
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.SAVED,
            status(stored, "Deck", saved = snapshot(stored)),
        )
    }

    @Test
    fun aChangedDeckIsUnsaved() {
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(deck(1, 2), "Deck", saved = snapshot(stored)),
        )
    }

    @Test
    fun rearrangingCountsAsAChange() {
        // The order is the deck in this program, so the file has to follow it.
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(deck(3, 2, 1), "Deck", saved = snapshot(stored)),
        )
    }

    @Test
    fun renamingCountsAsAChange() {
        val stored = deck(1)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(stored, "Renamed", saved = snapshot(stored)),
        )
    }

    @Test
    fun emptyingASavedDeckIsStillAChange() {
        // Not UNTOUCHED: there is something on disk, and the empty list in front
        // of you is a deletion waiting to be written.
        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(Deck.EMPTY, "Deck", saved = snapshot(deck(1, 2))),
        )
    }

    @Test
    fun autosaveOnlyEverContinuesWhatSomebodyStarted() {
        // A deck saved once keeps itself saved. A deck never saved is never
        // written anywhere on its own, because the alternative fills a library
        // with Untitled Decks somebody then has to delete.
        assertTrue(SaveTracking.shouldAutosave("id", SaveStatus.UNSAVED_CHANGES))

        assertFalse(SaveTracking.shouldAutosave(null, SaveStatus.NEVER_SAVED))
        assertFalse(SaveTracking.shouldAutosave(null, SaveStatus.UNSAVED_CHANGES))
    }

    @Test
    fun thereIsNothingToAutosaveWhenNothingChanged() {
        assertFalse(SaveTracking.shouldAutosave("id", SaveStatus.SAVED))
        assertFalse(SaveTracking.shouldAutosave("id", SaveStatus.UNTOUCHED))
    }

    @Test
    fun editingTheNotesIsAChangeLikeAnyOther() {
        // The notes are written by the same save, so a snapshot that forgot them
        // would report the deck saved the moment somebody typed a note.
        val stored = deck(1)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(stored, "Deck", notes = "side out Nibiru", saved = snapshot(stored)),
        )
        assertEquals(
            SaveStatus.SAVED,
            status(stored, "Deck", notes = "plan", saved = snapshot(stored, notes = "plan")),
        )
    }

    @Test
    fun aNoteOnAnEmptyDeckIsStillWork() {
        assertEquals(SaveStatus.NEVER_SAVED, status(Deck.EMPTY, notes = "matchup plan"))
    }

    @Test
    fun everyStatusIsReachable() {
        // A state nothing can produce is a state the UI has a branch for and
        // will never show.
        val produced = setOf(
            status(Deck.EMPTY),
            status(deck(1)),
            status(deck(1), "Deck", saved = snapshot(deck(1))),
            status(deck(2), "Deck", saved = snapshot(deck(1))),
        )

        assertEquals(SaveStatus.entries.toSet(), produced)
    }

    // ---- what travels beside the decklist ----------------------------------

    @Test
    fun aGapNobodyHasWrittenDownCountsAsAChange() {
        // The bug this exists to stop: the arrangement, the notes on individual
        // cards and the mat all live in the payload, and none of them was in the
        // snapshot -- so every one of them reported itself saved the instant it
        // changed and the autosave clock never started. The one thing this
        // program exists to keep was the thing least likely to be written down.
        val stored = deck(1, 2, 3)
        val was = payload("""{"arrangement":{"main":[1]}}""")
        val now = payload("""{"arrangement":{"main":[2]}}""")

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(stored, saved = snapshot(stored, extended = was), extended = now),
        )
    }

    @Test
    fun aPayloadThatMatchesIsStillSaved() {
        val stored = deck(1, 2, 3)
        val same = payload("""{"arrangement":{"main":[1]},"notes":{"cards":{"1":"x"}}}""")

        assertEquals(
            SaveStatus.SAVED,
            status(stored, saved = snapshot(stored, extended = same), extended = same),
        )
    }

    @Test
    fun aDeckThatCarriesNothingAndSavedNothingIsSaved() {
        val stored = deck(1, 2, 3)

        assertEquals(SaveStatus.SAVED, status(stored, saved = snapshot(stored)))
    }

    @Test
    fun dressingAPlainDeckIsAChange() {
        // A `.ydk` with no payload at all, given a mat.
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(
                stored,
                saved = snapshot(stored),
                extended = payload("""{"look":{"mat":"wine"}}"""),
            ),
        )
    }

    @Test
    fun takingTheLastGapOutIsAChangeToo() {
        // Back to no payload at all, which is not the same as never having had
        // one -- and would have been the easy case to get wrong by treating null
        // as "nothing to compare".
        val stored = deck(1, 2, 3)
        val was = payload("""{"arrangement":{"main":[1]}}""")

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(stored, saved = snapshot(stored, extended = was), extended = null),
        )
    }
}
