package com.kaiharimoto.mastertool.core.prefs

import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.scene.DeskLight
import com.kaiharimoto.mastertool.core.scene.Scene
import com.kaiharimoto.mastertool.core.tune.StageTuning
import kotlinx.serialization.Serializable

/** Which colour scheme the app renders in. */
@Serializable
enum class ThemeMode {
    /** Follow the operating system. */
    SYSTEM,
    DARK,
    LIGHT,
}

/**
 * Which back a face-down card wears.
 *
 * Two, because two is what people mean by "the card back": the plain one with a
 * dark oval, and the older spiral. Both are drawn rather than shipped as
 * artwork — see `ui/components/CardBack.kt` for why — and either can be
 * overridden per install by pointing [UiPreferences.cardBackUrl] at an image.
 */
@Serializable
enum class CardBackStyle {
    OVAL,
    SPIRAL,
    ;

    val displayName: String
        get() = when (this) {
            OVAL -> "Oval"
            SPIRAL -> "Spiral"
        }
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
    /**
     * Cards per row in the search grid; 0 means size to fit.
     *
     * Four by default: the pool is scanned, not read, and four across is the
     * width at which a card's art is recognisable at arm's length without the
     * column becoming a list.
     */
    val searchColumns: Int = 4,
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
    /** Which of the drawn backs a face-down card wears. */
    val cardBack: CardBackStyle = CardBackStyle.OVAL,
    /**
     * An image to use for the card back instead of the drawn one.
     *
     * Blank by default and blank in the repository, deliberately. The official
     * back is Konami's artwork and this project is public, so it is not shipped
     * here; anyone who wants the real thing points this at it on their own
     * device and it is cached like any other card image.
     */
    val cardBackUrl: String = "",
    /**
     * The foil edge every card wears, in the builder and on the play stage.
     *
     * **On by default**, which is a deliberate exception to the handbook. The
     * prismatic ramp is spent sparingly everywhere else — `docs/DESIGN.md` says
     * fringing everything reads as decoration and fringing the thing under your
     * finger reads as light, and that rule stands for the *fringe*. This is the
     * other effect: the original tool put it on every card in the deck grid, it
     * is what a sleeved card in a binder actually does, and kai asked for it
     * back by name. Default off would have meant restoring something nobody
     * could see without first finding a switch.
     *
     * A preference rather than a constant because it is a matter of taste that a
     * person can hold an opinion about, and because the honest answer to "this
     * is too much" is a way to turn it off rather than an argument.
     */
    val prismaticCards: Boolean = true,
    val format: Format = Format.TCG,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Which room the play stage is in.
     *
     * [Scene.MINIMAL] by default, and that is a decision rather than caution.
     * The minimal stage is what `docs/DESIGN.md` describes and it is what the
     * app *is*; a desk is a thing you go and choose, the way you choose a card
     * back. A build that arrived one morning with a bedroom in it would have
     * changed the tool on somebody who had not asked.
     */
    val scene: Scene = Scene.MINIMAL,
    /**
     * Which lamp is on in the desk scene.
     *
     * [DeskLight.AUTO] reads the hour off the device, so a table opened at
     * midnight is lit by a lamp without anybody having said so. The two manual
     * values exist because a preference that can only be inferred is a
     * preference the user cannot disagree with.
     */
    val deskLight: DeskLight = DeskLight.AUTO,
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
    /**
     * The numbers the play stage is tuned by.
     *
     * A field with a default, the same as everything else here, and that is the
     * whole of the plumbing — `StageTuning.DEFAULT` is what shipped, so a
     * document written before this existed reads back as the untouched stage.
     *
     * It is on the preferences rather than in a file of its own because it is a
     * preference: it is a person's opinion about how their table should look,
     * and it should survive a relaunch the way the room and the card back do.
     * Exporting it is a separate act — see `TuningCodec`.
     */
    val stageTuning: StageTuning = StageTuning.DEFAULT,
    /**
     * Which generation of the layout this document was written by.
     *
     * The one thing a defaulted field cannot do on its own: a preference that
     * was *stored* keeps its old value forever, so changing a default only ever
     * reaches new installs. Bumping this is how a change to what the layout
     * fundamentally is — row widths, whether the deck is fitted — reaches a
     * device that already has a document on it. Everything else here stays a
     * plain field with a default.
     */
    val layoutVersion: Int = 0,
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
        // The only field here whose values arrive from a finger dragging a
        // slider, so the only one that can produce a NaN. One reaching
        // `StagePlane`'s trigonometry is a stage that is broken after a restart,
        // with nothing on screen and nothing in a log.
        stageTuning = stageTuning.sanitised(),
    )

    /**
     * The same document as written by today's build.
     *
     * Only the layout is rebuilt — the format, the theme, the feedback setting,
     * the pinned easter-egg pool and the search split are the person's, not the
     * generation's, and survive untouched.
     */
    fun migrated(): UiPreferences = when {
        layoutVersion >= LAYOUT_VERSION -> this
        else -> copy(
            layoutVersion = LAYOUT_VERSION,
            fitAll = DEFAULT.fitAll,
            searchColumns = DEFAULT.searchColumns,
            main = main.copy(columns = DEFAULT.main.columns, autoFit = true),
            extra = extra.copy(columns = DEFAULT.extra.columns, autoFit = true),
            side = side.copy(columns = DEFAULT.side.columns, autoFit = true),
        )
    }

    companion object {
        const val DEFAULT_SEARCH_WEIGHT = 0.36f
        const val MIN_SEARCH_WEIGHT = 0.2f
        const val MAX_SEARCH_WEIGHT = 0.7f

        /**
         * 1: ten across for the main deck and fifteen for the extra and side,
         * with all three panes sized to be visible at once.
         * 2: four across in the card pool.
         */
        const val LAYOUT_VERSION = 2

        val DEFAULT = UiPreferences(layoutVersion = LAYOUT_VERSION)
    }
}
