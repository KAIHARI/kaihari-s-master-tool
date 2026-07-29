package com.kaiharimoto.mastertool.core.deck

/**
 * A version of a deck, as far as the rules about keeping them are concerned.
 *
 * Only two things matter here: when it was kept, and whether somebody asked for
 * it to be. The cards live with the record itself; deciding what to let go of
 * never needs to read them.
 */
data class VersionMark(
    val id: String,
    val atEpochMs: Long,
    /** What it was called, if it was named on purpose. Null for one kept by itself. */
    val name: String? = null,
) {
    /** A named version is one somebody chose. Those are never dropped. */
    val isPinned: Boolean get() = name != null
}

/**
 * What a deck used to be.
 *
 * A deck that has been saved once keeps itself saved from then on, which is the
 * right behaviour and has exactly one cost: there is no yesterday. Open the list
 * you took to a regional, cut a card, and the sixty you registered no longer
 * exist anywhere. That is the worst thing this application can do to somebody's
 * work, and it was introduced by a feature that is otherwise a straight
 * improvement — so the fix belongs next to it rather than in a backlog.
 *
 * Two rules, both here so they can be tested without a database:
 *
 * **When a copy is kept.** Not on every save — autosave writes constantly and a
 * timeline of every keystroke is not a record, it is noise. A copy is kept when
 * the deck is about to be changed after standing still for a while, so what gets
 * kept is *the deck as it stood at the end of the last time you worked on it*.
 * One version per sitting.
 *
 * **What is let go of.** Not the oldest. See [surplus].
 */
object DeckHistory {

    /**
     * How long a deck has to have stood untouched before writing over it keeps a
     * copy of what was there.
     *
     * Six hours, which is not a day: an evening's building and the next morning's
     * are two different sittings, and somebody who edits a list at a venue after
     * building it that morning wants the morning's version back.
     */
    const val SPACING_MS: Long = 6L * 60 * 60 * 1000

    /** How many versions a deck carries before it starts thinning them. */
    const val LIMIT: Int = 12

    /** Below this nothing can be dropped at all, because both ends are held. */
    const val FLOOR: Int = 2

    /**
     * Whether saving now should keep a copy of what is already there.
     *
     * [lastEditedAtEpochMs] is when the stored deck was last written, so a deck
     * being saved for the first time keeps nothing — there is nothing to keep.
     */
    fun keepsACopy(
        lastEditedAtEpochMs: Long?,
        nowEpochMs: Long,
        spacingMs: Long = SPACING_MS,
    ): Boolean = lastEditedAtEpochMs != null && nowEpochMs - lastEditedAtEpochMs >= spacingMs

    /**
     * Which versions to let go of, once a deck has more than it keeps.
     *
     * Deliberately not the oldest. The oldest is the deck as it first stood,
     * which is usually the version worth the most — and a history that drops its
     * far end is a history that quietly becomes "the last few days" no matter how
     * long you have had the deck.
     *
     * Dropped instead is whichever version sits closest in time to the ones on
     * either side of it: the one whose absence opens the smallest hole, and so
     * the one that says the least about how the deck got here. Repeated until the
     * count fits. What is left spans the deck's whole life, close together near
     * the present because that is where the versions are, and both ends intact.
     *
     * Neither end is ever a candidate, and neither is a named version. Pinning
     * more versions than the limit is somebody saying they want them, so the
     * limit gives way rather than the choice.
     *
     * Returns the marks to delete, in no particular order.
     */
    fun surplus(versions: List<VersionMark>, limit: Int = LIMIT): List<VersionMark> {
        val cap = maxOf(FLOOR, limit)
        if (versions.size <= cap) return emptyList()

        val live = versions.sortedBy { it.atEpochMs }.toMutableList()
        val dropped = ArrayList<VersionMark>(live.size - cap)

        while (live.size > cap) {
            val victim = (1 until live.size - 1)
                .filter { !live[it].isPinned }
                // The span that would open up if this one went. Ties go to the
                // earlier index, which is stable and self-correcting: removing
                // one widens its neighbours' spans, so the next choice moves on
                // rather than eroding the same end of the record.
                .minByOrNull { live[it + 1].atEpochMs - live[it - 1].atEpochMs }
                ?: break

            dropped += live.removeAt(victim)
        }

        return dropped
    }
}
