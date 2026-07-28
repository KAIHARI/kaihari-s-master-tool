package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.DeckSection

/**
 * Which cards are picked up.
 *
 * Positions rather than passcodes, because a deck holds duplicates and the two
 * copies of a card sitting at either end of the Main deck are different things to
 * have selected.
 *
 * Confined to one section on purpose. Selecting across sections would mean a
 * drag with two destinations and a delete with two legality rules, and there is
 * no gesture anyone would use it for — the useful multi-card actions are all
 * "these, together, somewhere else".
 *
 * The indices refer to the deck as it is right now. Any edit that is not this
 * selection's own move invalidates them, which is why [afterEdit] exists and why
 * calling it is the caller's job rather than something inferred here.
 */
data class Selection(
    val section: DeckSection? = null,
    val indices: Set<Int> = emptySet(),
    /** Where a range extension measures from. */
    val anchor: Int = NONE,
) {
    val size: Int get() = indices.size
    val isEmpty: Boolean get() = indices.isEmpty()

    fun contains(section: DeckSection, index: Int): Boolean =
        this.section == section && index in indices

    /** In deck order, which is the order a multi-card move has to preserve. */
    fun ordered(): List<Int> = indices.sorted()

    companion object {
        const val NONE = -1
        val EMPTY = Selection()
    }
}

/**
 * The rules for building a selection up.
 *
 * Separate from the data so the behaviour is testable without a grid, which
 * matters more than usual here: every one of these is reached through a gesture,
 * and gestures are the part of the app that cannot be tested at all.
 */
object Selections {

    /** One card, replacing whatever was selected. The plain tap. */
    fun only(section: DeckSection, index: Int): Selection =
        Selection(section = section, indices = setOf(index), anchor = index)

    /**
     * Adds or removes one card, keeping the rest.
     *
     * Touching a different section starts over rather than merging: a selection
     * spanning two sections is a state with no useful action behind it.
     */
    fun toggle(current: Selection, section: DeckSection, index: Int): Selection {
        if (current.section != section) return only(section, index)

        val next = if (index in current.indices) current.indices - index else current.indices + index
        return if (next.isEmpty()) {
            Selection.EMPTY
        } else {
            current.copy(
                indices = next,
                // The anchor follows the last card touched, so a range extended
                // afterwards measures from where you actually are.
                anchor = index,
            )
        }
    }

    /**
     * Everything between the anchor and [index], in reading order.
     *
     * Replaces the range rather than adding to it, so dragging a shift-selection
     * back and forth grows and shrinks instead of only ever growing.
     */
    fun extendTo(current: Selection, section: DeckSection, index: Int): Selection {
        if (current.section != section || current.anchor == Selection.NONE) {
            return only(section, index)
        }

        val from = minOf(current.anchor, index)
        val to = maxOf(current.anchor, index)
        return current.copy(indices = (from..to).toSet())
    }

    /**
     * Everything in the rectangle between the anchor and [index], given the grid's
     * width.
     *
     * The other useful meaning of "between two cards" in a grid, and the one a
     * reading-order range cannot express: four columns of three rows, rather than
     * everything from here to there. The tool this replaces had it as a separate
     * vertical selection mode.
     */
    fun extendBlockTo(
        current: Selection,
        section: DeckSection,
        index: Int,
        columns: Int,
        count: Int,
    ): Selection {
        if (columns <= 0) return extendTo(current, section, index)
        if (current.section != section || current.anchor == Selection.NONE) {
            return only(section, index)
        }

        val anchorRow = current.anchor / columns
        val anchorColumn = current.anchor % columns
        val row = index / columns
        val column = index % columns

        val rows = minOf(anchorRow, row)..maxOf(anchorRow, row)
        val cols = minOf(anchorColumn, column)..maxOf(anchorColumn, column)

        val block = rows.flatMap { r -> cols.map { c -> r * columns + c } }
            .filter { it in 0 until count }
            .toSet()

        return current.copy(indices = block)
    }

    /**
     * The selection after the section changed underneath it.
     *
     * Deliberately total loss rather than a best effort at tracking. A card
     * removed from the middle shifts every index after it, and a selection that
     * silently came to mean different cards than the ones highlighted would be
     * worse than one that went away — the next action on it moves real cards.
     */
    /**
     * The moving end of a range — where the next shift-arrow measures *to*.
     *
     * There is no separate focus field, and adding one would mean every place
     * that builds a selection had to keep it right. It can be derived instead:
     * [extendTo] always produces a contiguous range with the anchor at one end,
     * so the focus is the other end — the one further from the anchor. A single
     * card is its own focus, which falls out of the same comparison rather than
     * needing a case.
     */
    fun focusOf(current: Selection): Int {
        if (current.isEmpty) return current.anchor
        val lowest = current.indices.min()
        val highest = current.indices.max()
        val anchor = if (current.anchor == Selection.NONE) lowest else current.anchor
        return if (anchor - lowest >= highest - anchor) lowest else highest
    }

    fun afterEdit(current: Selection, section: DeckSection): Selection =
        if (current.section == section) Selection.EMPTY else current
}
