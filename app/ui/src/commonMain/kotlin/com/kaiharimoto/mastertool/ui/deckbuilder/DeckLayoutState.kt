package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.data.PreferencesRepository
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
import com.kaiharimoto.mastertool.core.prefs.ThemeChoice
import com.kaiharimoto.mastertool.core.prefs.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How the deck builder is laid out, and where that gets remembered.
 *
 * Split out from [DeckBuilderState] because it answers a different question: one
 * holds the deck you are building, this holds the shape of the room you are
 * building it in. They change at completely different rates — a resize drag
 * writes this sixty times a second and never touches the deck.
 */
class DeckLayoutState(
    private val repository: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    var preferences by mutableStateOf(UiPreferences.DEFAULT)
        private set

    /** Height of the deck column in pixels, needed to turn a drag into a weight. */
    var deckColumnHeightPx by mutableStateOf(0f)

    /** Width of the whole builder in pixels, for the search / deck split. */
    var builderWidthPx by mutableStateOf(0f)

    /**
     * What each pane's grid actually settled on, which is not always what is
     * stored: an auto-fitting section computes its width from the space it was
     * given, and only the composable that laid it out knows the answer.
     *
     * Deliberately not snapshot state and never persisted. Nothing composes off
     * it — it is only read at the moment a resize pins a section.
     */
    private val displayedColumns = mutableMapOf<DeckSection, Int>()

    private var saveJob: Job? = null

    fun start(onLoaded: (UiPreferences) -> Unit = {}) {
        scope.launch {
            preferences = repository.load()
            onLoaded(preferences)
        }
    }

    fun update(transform: (UiPreferences) -> UiPreferences) {
        preferences = transform(preferences).sanitised()
        scheduleSave()
    }

    fun updateSection(section: DeckSection, transform: (SectionPreferences) -> SectionPreferences) {
        update { it.with(section, transform(it[section])) }
    }

    fun toggleCollapsed(section: DeckSection) {
        updateSection(section) { it.copy(collapsed = !it.collapsed) }
    }

    /**
     * Gives one section the whole column by collapsing the other two, and puts
     * them back when it is already the only one open.
     *
     * What "focus the Main deck" can usefully mean once panes collapse: all three
     * are on screen at once, so there is nothing to scroll to — the useful move is
     * to give the one being worked on the space.
     */
    fun focusSection(section: DeckSection) {
        val alreadyFocused = DeckSection.entries.all { entry ->
            preferences[entry].collapsed == (entry != section)
        }

        update { prefs ->
            DeckSection.entries.fold(prefs) { acc, entry ->
                acc.with(
                    entry,
                    acc[entry].copy(collapsed = if (alreadyFocused) false else entry != section),
                )
            }
        }
    }

    /** Reaching for the density control is itself the decision to size by hand. */
    fun setColumns(section: DeckSection, columns: Int) {
        updateSection(section) { it.copy(columns = columns, autoFit = false) }
    }

    /** Told by each pane what its grid settled on, once per layout pass. */
    fun noteDisplayedColumns(section: DeckSection, columns: Int) {
        displayedColumns[section] = columns
    }

    /**
     * How wide the pane's grid actually is, for anything that has to think in
     * rows — an arrow key stepping down, a block selection.
     *
     * Falls back to the stored preference, which is what would have been used
     * had the pane not measured yet. Not snapshot state on purpose: this is read
     * at the moment a key is pressed, never during composition.
     */
    fun columnsOn(section: DeckSection): Int =
        displayedColumns[section] ?: preferences[section].columns

    fun setAutoFit(section: DeckSection, autoFit: Boolean) {
        updateSection(section) { it.copy(autoFit = autoFit) }
    }

    /**
     * Cards per row in the pool. Zero means size to fit, which is the default.
     *
     * The preference has been stored and read since the pane was written and
     * nothing ever set it, so the only reachable value was the default.
     */
    fun setSearchColumns(columns: Int) {
        update { it.copy(searchColumns = columns.coerceAtLeast(0)) }
    }

    /**
     * Remembers which deck was open, so the next run opens it again.
     *
     * Called whenever the open deck changes rather than on the way out, because
     * there is no reliable moment of closing on either platform — a tablet's
     * process is reclaimed without ceremony, and that is the case this exists
     * for in the first place.
     *
     * A null id is ignored rather than stored, for two reasons that happen to
     * agree. It removes a race: the restore reads this document, opens a deck,
     * and the id only arrives once that load finishes, so a null in the meantime
     * would erase the very thing being restored. And it is the better behaviour
     * anyway — a new deck has no id, cannot be reopened, and forgetting the last
     * real one to remember nothing is a worse thing to find on next launch.
     */
    fun rememberOpenDeck(id: String?) {
        if (id == null || preferences.lastDeckId == id) return
        update { it.copy(lastDeckId = id) }
    }

    /** Persisted like every other layout setting, and applied on the next frame. */
    fun setTheme(theme: ThemeChoice) {
        update { it.copy(theme = theme) }
    }

    fun setSortMode(section: DeckSection, mode: SortMode) {
        updateSection(section) { it.copy(sortMode = mode) }
    }

    /**
     * Drags the divider between two panes.
     *
     * The two panes trade weight rather than each being set independently, so the
     * total stays put and the pane on the far side of the drag does not move —
     * which is what makes a three-pane resize feel like moving one boundary
     * instead of rescaling the whole column.
     *
     * Both panes are pinned at the width they are showing before anything moves,
     * so the drag makes their cards larger or smaller rather than re-flowing the
     * grid around cards that stay one size. Dragging a divider is itself the
     * decision to size by hand, the same way reaching for the density stepper is.
     */
    fun resizePanes(above: DeckSection, below: DeckSection, deltaPx: Float) {
        val height = deckColumnHeightPx
        if (height <= 0f) return

        pin(above)
        pin(below)

        val visibleWeight = DeckSection.entries
            .filterNot { preferences[it].collapsed }
            .sumOf { preferences[it].weight.toDouble() }
            .toFloat()
        if (visibleWeight <= 0f) return

        val weightPerPixel = visibleWeight / height
        val delta = deltaPx * weightPerPixel

        val top = preferences[above]
        val bottom = preferences[below]

        val newTop = (top.weight + delta)
            .coerceIn(SectionPreferences.MIN_WEIGHT, SectionPreferences.MAX_WEIGHT)
        // Apply only what the top pane actually accepted, so hitting a limit
        // stops the drag rather than quietly shrinking the other pane past it.
        val applied = newTop - top.weight
        val newBottom = bottom.weight - applied
        if (newBottom < SectionPreferences.MIN_WEIGHT) return

        update {
            it.with(above, top.copy(weight = newTop))
                .with(below, bottom.copy(weight = newBottom))
        }
    }

    /**
     * Pins one pane at the width it is showing, if it was still sizing itself.
     *
     * A no-op once the section is manual, so calling it on every frame of a drag
     * cannot overwrite the pinned count with a mid-drag one.
     */
    private fun pin(section: DeckSection) {
        val displayed = displayedColumns[section] ?: return
        updateSection(section) { it.frozenAt(displayed) }
    }

    fun resizeSearchPane(deltaPx: Float) {
        val width = builderWidthPx
        if (width <= 0f) return
        update { it.copy(searchWeight = it.searchWeight + deltaPx / width) }
    }

    /**
     * Written back well after the drag stops.
     *
     * A resize produces a new value every frame, and none of the intermediate
     * ones are worth a disk write.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            repository.save(preferences)
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
