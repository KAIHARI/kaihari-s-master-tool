package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.data.PreferencesRepository
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
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

    fun setColumns(section: DeckSection, columns: Int) {
        updateSection(section) { it.copy(columns = columns) }
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
     */
    fun resizePanes(above: DeckSection, below: DeckSection, deltaPx: Float) {
        val height = deckColumnHeightPx
        if (height <= 0f) return

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
