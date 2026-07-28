package com.kaiharimoto.mastertool.core.prefs

import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import kotlinx.serialization.Serializable

/** How one deck pane is laid out. */
@Serializable
data class SectionPreferences(
    /** Share of the deck column's height, relative to the other panes. */
    val weight: Float,
    /** Cards per row, used only when [autoFit] is off. */
    val columns: Int,
    /**
     * Size the cards so the whole section is visible, rather than by hand.
     *
     * On by default. A deck is a small, known quantity and the useful default is
     * to see all of it; picking a column count manually means re-picking it every
     * time the deck grows past a row.
     */
    val autoFit: Boolean = true,
    val collapsed: Boolean = false,
    val sortMode: SortMode = SortMode.MANUAL,
) {
    fun sanitised(fallbackWeight: Float): SectionPreferences = copy(
        weight = if (weight.isFinite()) weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) else fallbackWeight,
        columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
    )

    /**
     * Pins the grid at the width it is currently showing.
     *
     * Called when a pane is resized. With [autoFit] left on, dragging a divider
     * re-picks the column count, so the cards stay one size and the grid reflows
     * around them — which is the one thing that never happens to cards on a table.
     * Pinning first means a resize does what pulling a stack towards you does:
     * the same cards, bigger.
     *
     * Already-manual sections are left alone, so this is safe to call on every
     * frame of a drag rather than only on the first.
     */
    fun frozenAt(displayedColumns: Int): SectionPreferences =
        if (!autoFit) this else copy(columns = displayedColumns, autoFit = false)

    companion object {
        const val MIN_WEIGHT = 0.25f
        const val MAX_WEIGHT = 8f
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 20
    }
}

/**
 * Layout settings, stored as one JSON document.
 *
 * One document rather than a column per setting so that adding a preference is a
 * field and never a schema migration — the file is read with unknown keys
 * ignored, so an older build opening a newer document keeps working too.
 *
 * Everything here is read back off disk, which is why [sanitised] exists and is
 * applied on both load and save. A weight of zero or NaN would reach
 * `Modifier.weight`, which rejects both, and a settings file is exactly the kind
 * of thing that survives a half-written update.
 */
@Serializable
data class UiPreferences(
    /** Share of the window given to search, against the deck panes. */
    val searchWeight: Float = DEFAULT_SEARCH_WEIGHT,
    /** Cards per row in the search grid; 0 means size to fit. */
    val searchColumns: Int = 0,
    val main: SectionPreferences = SectionPreferences(weight = 2f, columns = 10),
    val extra: SectionPreferences = SectionPreferences(weight = 1f, columns = 10),
    val side: SectionPreferences = SectionPreferences(weight = 1f, columns = 10),
    /** Show one tile per distinct card with a count, rather than one per copy. */
    val stacked: Boolean = false,
    /**
     * Space between cards in a deck pane, in dp.
     *
     * Zero by default, and that is the whole point: cards that touch read as one
     * arrangement the way they do laid out on a table, where a gutter makes them
     * read as separate tiles in a piece of software. What keeps them legible at
     * zero is the card's own printed edge, drawn by `CardTile` — not space.
     */
    val cardGutter: Int = 0,
    /**
     * Which surface the deck is laid out on.
     *
     * Chosen rather than followed from the system: this app is used in one room
     * under one set of lights at a time, and a tool that changed its own
     * appearance at sunset because the tablet said so would be answering a
     * question nobody asked it.
     */
    val theme: ThemeChoice = ThemeChoice.SWISS,
    val format: Format = Format.TCG,
    /**
     * Passcodes the easter egg throws, when it has been given a set to keep.
     *
     * Empty means "whatever is in the deck right now", which is the useful
     * default and needs no curating.
     */
    val easterEggPool: List<Int> = emptyList(),
    /**
     * The deck that was open when the program was last closed.
     *
     * Not a layout setting, and it lives here anyway: this document is what the
     * app remembers about itself between runs, and adding a second one for a
     * single nullable string would be a table for a sentence.
     *
     * Null means there was none, which is also what a deleted deck comes back
     * as — reopening a deck that is no longer there simply does nothing.
     */
    val lastDeckId: String? = null,
) {
    operator fun get(section: DeckSection): SectionPreferences = when (section) {
        DeckSection.MAIN -> main
        DeckSection.EXTRA -> extra
        DeckSection.SIDE -> side
    }

    fun with(section: DeckSection, preferences: SectionPreferences): UiPreferences =
        when (section) {
            DeckSection.MAIN -> copy(main = preferences)
            DeckSection.EXTRA -> copy(extra = preferences)
            DeckSection.SIDE -> copy(side = preferences)
        }

    fun sanitised(): UiPreferences = copy(
        searchWeight = if (searchWeight.isFinite()) {
            searchWeight.coerceIn(MIN_SEARCH_WEIGHT, MAX_SEARCH_WEIGHT)
        } else {
            DEFAULT_SEARCH_WEIGHT
        },
        searchColumns = if (searchColumns <= 0) {
            0
        } else {
            searchColumns.coerceIn(SectionPreferences.MIN_COLUMNS, SectionPreferences.MAX_COLUMNS)
        },
        main = main.sanitised(fallbackWeight = 2f),
        extra = extra.sanitised(fallbackWeight = 1f),
        side = side.sanitised(fallbackWeight = 1f),
        cardGutter = cardGutter.coerceIn(MIN_CARD_GUTTER, MAX_CARD_GUTTER),
    )

    companion object {
        const val DEFAULT_SEARCH_WEIGHT = 0.36f
        const val MIN_SEARCH_WEIGHT = 0.2f
        const val MAX_SEARCH_WEIGHT = 0.7f

        const val MIN_CARD_GUTTER = 0
        /** Past this the pane stops reading as an arrangement and starts reading as a list. */
        const val MAX_CARD_GUTTER = 12

        val DEFAULT = UiPreferences()
    }
}
