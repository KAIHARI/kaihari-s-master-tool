package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveTrackingTest {

    private fun deck(vararg values: Int) =
        Deck(main = values.map(::CardId), extra = emptyList(), side = emptyList())

    private fun status(current: Deck, name: String = "Deck", saved: SavedSnapshot? = null) =
        SaveTracking.status(current, name, saved)

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
            status(stored, "Deck", SavedSnapshot(stored, "Deck")),
        )
    }

    @Test
    fun aChangedDeckIsUnsaved() {
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(deck(1, 2), "Deck", SavedSnapshot(stored, "Deck")),
        )
    }

    @Test
    fun rearrangingCountsAsAChange() {
        // The order is the deck in this program, so the file has to follow it.
        val stored = deck(1, 2, 3)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(deck(3, 2, 1), "Deck", SavedSnapshot(stored, "Deck")),
        )
    }

    @Test
    fun renamingCountsAsAChange() {
        val stored = deck(1)

        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(stored, "Renamed", SavedSnapshot(stored, "Deck")),
        )
    }

    @Test
    fun emptyingASavedDeckIsStillAChange() {
        // Not UNTOUCHED: there is something on disk, and the empty list in front
        // of you is a deletion waiting to be written.
        assertEquals(
            SaveStatus.UNSAVED_CHANGES,
            status(Deck.EMPTY, "Deck", SavedSnapshot(deck(1, 2), "Deck")),
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
    fun everyStatusIsReachable() {
        // A state nothing can produce is a state the UI has a branch for and
        // will never show.
        val produced = setOf(
            status(Deck.EMPTY),
            status(deck(1)),
            status(deck(1), "Deck", SavedSnapshot(deck(1), "Deck")),
            status(deck(2), "Deck", SavedSnapshot(deck(1), "Deck")),
        )

        assertEquals(SaveStatus.entries.toSet(), produced)
    }
}
