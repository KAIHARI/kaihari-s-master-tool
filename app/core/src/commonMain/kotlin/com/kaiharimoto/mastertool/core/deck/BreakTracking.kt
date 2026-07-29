package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.CardId

/**
 * Works out what happened to a section, so the gaps in it can follow.
 *
 * The alternative was for every edit in the program to report its own index —
 * add, remove, drop, drag-out, siding, undo, import — and for each of them to
 * remember to. That is nine places to keep in step with one another, and the
 * one that forgot would leave a gap silently pointing at the wrong card. This
 * reads the answer off the before and after instead, which cannot fall out of
 * step because there is nothing to keep in step with.
 *
 * It only has to be right about the edits a person actually makes, and there are
 * three: something arrived, something left, or something moved.
 */
object BreakTracking {

    /**
     * Where the gaps end up when [before] becomes [after].
     *
     * A change it cannot read as one of those three — a sort, a tidy, a whole
     * list replaced by import — leaves the gaps where they were rather than
     * throwing them away. That is the recoverable failure of the two: a gap in
     * an odd place is one tap from being moved, and a gap that has been deleted
     * is gone.
     */
    fun follow(breaks: Breaks, before: List<CardId>, after: List<CardId>): Breaks {
        if (breaks.isEmpty) return Breaks.NONE
        if (before == after) return breaks.clampedTo(after.size)

        val head = firstDifference(before, after)
        val moved = when {
            after.size > before.size -> breaks.afterInsert(head, after.size - before.size)

            after.size < before.size -> {
                // Removed one at a time from the same place, which is what
                // removing a run of cards at `head` actually is.
                var walked = breaks
                repeat(before.size - after.size) { walked = walked.afterRemove(head) }
                walked
            }

            else -> singleShift(before, after)
                ?.let { (from, to) -> breaks.afterShift(from, to) }
                ?: breaks
        }
        return moved.clampedTo(after.size)
    }

    /** The first position at which the two lists disagree, or the end of the shorter. */
    private fun firstDifference(before: List<CardId>, after: List<CardId>): Int {
        val shared = minOf(before.size, after.size)
        for (i in 0 until shared) if (before[i] != after[i]) return i
        return shared
    }

    /**
     * Reads a same-length change as one card having been picked up and put down.
     *
     * Every hand-arrangement in the program is this: a card dragged from one
     * place in a pane to another. Anything else the same length — a sort, a
     * shuffle — is not, and returns null rather than an invented answer.
     */
    private fun singleShift(before: List<CardId>, after: List<CardId>): Pair<Int, Int>? {
        val first = before.indices.firstOrNull { before[it] != after[it] } ?: return null
        val last = before.indices.lastOrNull { before[it] != after[it] } ?: return null

        // Forward: the card at `first` travelled to `last`, everything between
        // shuffled back by one.
        if (after[last] == before[first] &&
            after.subList(first, last) == before.subList(first + 1, last + 1)
        ) {
            return first to last
        }

        // Backward: the card at `last` travelled to `first`.
        if (after[first] == before[last] &&
            after.subList(first + 1, last + 1) == before.subList(first, last)
        ) {
            return last to first
        }

        return null
    }
}
