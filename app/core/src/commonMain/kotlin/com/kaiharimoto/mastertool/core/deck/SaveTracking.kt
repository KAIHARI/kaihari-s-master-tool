package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.Deck

/**
 * What is on disk, if anything is.
 *
 * Everything a save writes has to be in here. A field the writer sends but the
 * snapshot forgets is a field that reports itself saved the moment it changes,
 * which is worse than not tracking it at all.
 */
data class SavedSnapshot(val deck: Deck, val name: String, val notes: String)

/**
 * Where the work stands relative to what has been written down.
 *
 * Four states rather than a dirty flag, because "nothing to lose yet" and "this
 * has never been saved" look identical to a boolean and are completely different
 * things to tell somebody.
 */
enum class SaveStatus {
    /** An empty deck nobody has touched. Saying anything about it would be noise. */
    UNTOUCHED,

    /** Real work that has never been written down. The only alarming state. */
    NEVER_SAVED,

    /** On disk and matching. */
    SAVED,

    /** On disk, but the copy in front of you has moved on. */
    UNSAVED_CHANGES,
}

/**
 * When the program is allowed to save without being asked.
 *
 * The rule is deliberately narrow: a deck that has been saved once keeps itself
 * saved from then on, and a deck that has never been saved is never written
 * anywhere on its own. The alternative — autosaving everything — fills somebody's
 * library with a row of Untitled Decks they have to go and delete, which is a
 * worse thing to do to a person than asking them to press save the first time.
 *
 * The consequence is that the first save is the only one that matters, so the
 * program has to be clear about when it has not happened. That is what
 * [SaveStatus.NEVER_SAVED] is for.
 */
object SaveTracking {

    fun status(
        current: Deck,
        name: String,
        notes: String,
        saved: SavedSnapshot?,
    ): SaveStatus = when {
        saved == null && current.isEmpty() && notes.isEmpty() -> SaveStatus.UNTOUCHED
        saved == null -> SaveStatus.NEVER_SAVED
        current == saved.deck && name == saved.name && notes == saved.notes -> SaveStatus.SAVED
        else -> SaveStatus.UNSAVED_CHANGES
    }

    /**
     * Whether an edit should start the clock on an automatic save.
     *
     * Takes the id rather than deriving it from the snapshot: a deck can have an
     * id before its first save has landed, and saving twice is harmless where
     * not saving at all is not.
     */
    fun shouldAutosave(deckId: String?, status: SaveStatus): Boolean =
        deckId != null && status == SaveStatus.UNSAVED_CHANGES

    private fun Deck.isEmpty(): Boolean =
        main.isEmpty() && extra.isEmpty() && side.isEmpty()
}
