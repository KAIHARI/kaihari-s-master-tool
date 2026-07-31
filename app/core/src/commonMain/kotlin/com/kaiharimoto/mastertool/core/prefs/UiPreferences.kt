package com.kaiharimoto.mastertool.core.prefs

import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import kotlinx.serialization.Serializable

/** Which colour scheme the app renders in. */
@Serializable
enum class ThemeMode {
    /** Follow the operating system. */
    SYSTEM,
    DARK,
    LIGHT,
}

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
    /**
     * Whether the card database is on screen at all.
     *
     * Switching it off is the "now I am looking at the deck" move: the pool is
     * where cards come from, and once they are in, it is just the thing between
     * you and the list. The deck column takes the whole window instead.
     */
    val searchVisible: Boolean = true,
    /**
     * Size the three panes from their row widths so all of them are visible.
     *
     * The alternative — each pane taking a share of the column height and
     * choosing its own column count to suit — is what put cards out of bounds:
     * three panes sized against heights a divider drag handed them cannot agree
     * on a total. On by default; dragging a divider is what turns it off.
     */
    val fitAll: Boolean = true,
    // Ten across for the main deck and fifteen for the extra and side: the row
    // widths a decklist is read in — four rows of ten is forty at a glance, and
    // an extra or side deck is one row of its own maximum.
    val main: SectionPreferences = SectionPreferences(weight = 2f, columns = 10),
    val extra: SectionPreferences = SectionPreferences(weight = 1f, columns = 15),
    val side: SectionPreferences = SectionPreferences(weight = 1f, columns = 15),
    /** Show one tile per distinct card with a count, rather than one per copy. */
    val stacked: Boolean = false,
    val format: Format = Format.TCG,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Sound and haptic feedback. Null means "the platform's default" — on for
     * a tablet in the hands, off at a desk — until the user says otherwise.
     */
    val feedbackEnabled: Boolean? = null,
    /**
     * Passcodes the easter egg throws, when it has been given a set to keep.
     *
     * Empty means "whatever is in the deck right now", which is the useful
     * default and needs no curating.
     */
    val easterEggPool: List<Int> = emptyList(),
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
    )

    companion object {
        const val DEFAULT_SEARCH_WEIGHT = 0.36f
        const val MIN_SEARCH_WEIGHT = 0.2f
        const val MAX_SEARCH_WEIGHT = 0.7f

        val DEFAULT = UiPreferences()
    }
}
