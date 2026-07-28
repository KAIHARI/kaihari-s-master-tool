package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.input.ShortcutAction
import com.kaiharimoto.mastertool.core.input.ShortcutContext
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CardInspector
import com.kaiharimoto.mastertool.ui.egg.EasterEgg
import com.kaiharimoto.mastertool.ui.dnd.DragAutoScroll
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragOverlay
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DropHover
import com.kaiharimoto.mastertool.ui.input.ShortcutHelpSheet
import com.kaiharimoto.mastertool.ui.input.ShortcutHost
import com.kaiharimoto.mastertool.ui.update.UpdateState

/**
 * The deck builder, laid out for a landscape tablet.
 *
 * Search sits on the left under the thumb of whichever hand is holding the
 * tablet, and the three deck sections stack down the right where they can all be
 * seen at once — the thing the desktop tool's collapsible panes were working
 * around on a small screen. Every boundary here is draggable and remembered, so
 * the split is whatever the deck being built needs it to be.
 */
@Composable
fun DeckBuilderScreen(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val drag = remember { DragController(canDrop = state::canDrop) }

    // The resolver works in pixels; these are the only two numbers in it that
    // are really about how big a finger is.
    with(density) {
        drag.rowTolerancePx = 8.dp.toPx()
        drag.hysteresisPx = 10.dp.toPx()
    }

    LaunchedEffect(state.toast?.id) {
        val toast = state.toast ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = toast.message,
            actionLabel = if (toast.undo != null) "Undo" else null,
            withDismissAction = toast.undo == null,
        )
        if (result == SnackbarResult.ActionPerformed) toast.undo?.invoke()
        state.consumeToast()
    }

    LaunchedEffect(updateState.message) {
        val text = updateState.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(text, withDismissAction = true)
        updateState.consumeMessage()
    }

    val onDropped: (DragSession, DropHover?) -> Unit = { session, landed ->
        applyDrop(state, session, landed)
    }

    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val overlayOpen = state.inspection != null || state.filtersVisible ||
        state.statsVisible || state.issuesVisible || state.helpVisible ||
        state.eggVisible || state.sidingVisible

    ShortcutHost(
        context = ShortcutContext(
            textInputFocused = state.textInputFocused,
            inspectorOpen = state.inspection != null,
            overlayOpen = overlayOpen,
        ),
        onAction = { action ->
            when (action) {
                ShortcutAction.SAVE -> state.save()
                ShortcutAction.UNDO -> state.undo()
                ShortcutAction.REDO -> state.redo()
                ShortcutAction.FOCUS_SEARCH -> searchFocus.requestFocus()
                ShortcutAction.TOGGLE_FILTERS -> state.filtersVisible = !state.filtersVisible
                ShortcutAction.TOGGLE_STATS -> state.statsVisible = !state.statsVisible
                ShortcutAction.TOGGLE_ISSUES -> state.issuesVisible = !state.issuesVisible
                ShortcutAction.TOGGLE_HELP -> state.helpVisible = !state.helpVisible
                ShortcutAction.DISMISS -> dismissTopLayer(state) { focusManager.clearFocus() }
                ShortcutAction.FOCUS_MAIN -> layout.focusSection(DeckSection.MAIN)
                ShortcutAction.FOCUS_EXTRA -> layout.focusSection(DeckSection.EXTRA)
                ShortcutAction.FOCUS_SIDE -> layout.focusSection(DeckSection.SIDE)
                ShortcutAction.PREVIOUS_CARD -> state.pageInspection(-1)
                ShortcutAction.NEXT_CARD -> state.pageInspection(1)
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            // The dragged card is composed here, outside every pane, so it is not
            // clipped by the grid it was lifted out of.
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    DeckBuilderTopBar(state, layout, updateState, onOpenLibrary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { layout.builderWidthPx = it.size.width.toFloat() },
                    ) {
                        SearchPane(
                            state = state,
                            layout = layout,
                            drag = drag,
                            searchFocus = searchFocus,
                            onDropped = onDropped,
                            modifier = Modifier.weight(layout.preferences.searchWeight).fillMaxHeight(),
                        )

                        SearchDeckDivider(onDrag = layout::resizeSearchPane)

                        DeckPanes(
                            state = state,
                            layout = layout,
                            drag = drag,
                            onDropped = onDropped,
                            modifier = Modifier
                                .weight(1f - layout.preferences.searchWeight)
                                .fillMaxHeight(),
                        )
                    }
                }

                DragOverlay(drag, state.format)
                // Draws nothing; it is here for the same reason the overlay is,
                // which is that a drag outlives the pane it started in.
                DragAutoScroll(drag)
            }
        }
    } // ShortcutHost

    if (state.sidingVisible) SidingPanel(state)

    state.inspection?.let { inspection ->
        CardInspector(
            cards = inspection.cards,
            initialIndex = inspection.index,
            format = state.format,
            copiesBySection = { card ->
                DeckSection.entries.associateWith { state.copiesIn(card.id, it) }
            },
            mainDeckSize = state.deck.main.size,
            openingHandOdds = { copies, handSize ->
                state.mainStatistics.openingHandOdds(copies, handSize)
            },
            onDismiss = { state.inspection = null },
            onPageChanged = state::onInspectionPageChanged,
            onSetCount = { card, section, count -> state.setCount(card, section, count) },
            onMove = { card, from, to -> state.moveCard(card, from, to) },
            onBrowse = { filter ->
                state.onQueryChange("")
                state.onFilterChange(filter)
                state.inspection = null
            },
        )
    }

    if (state.filtersVisible) {
        FilterSheet(
            index = state.index,
            filter = state.filter,
            onChange = state::onFilterChange,
            onClear = state::clearFilters,
            onDismiss = { state.filtersVisible = false },
        )
    }

    if (state.statsVisible) {
        DeckStatsPanel(
            statistics = state.statistics,
            section = state.statsSection,
            onSectionChange = { state.statsSection = it },
            onDismiss = { state.statsVisible = false },
        )
    }

    if (state.issuesVisible) {
        IssuesPanel(
            validation = state.validation,
            onReveal = { issue ->
                val id = issue.cardId ?: return@IssuesPanel
                val section = issue.section ?: state.sectionsHolding(id).firstOrNull()
                if (section != null) {
                    state.reveal(section, id)
                    state.issuesVisible = false
                }
            },
            onDismiss = { state.issuesVisible = false },
        )
    }

    if (state.helpVisible) {
        ShortcutHelpSheet(onDismiss = { state.helpVisible = false })
    }

    if (state.eggVisible) {
        val pinned = layout.preferences.easterEggPool

        // A pinned set if one was kept, otherwise whatever is in the deck — which
        // is the version of this that needs no curating and is never empty when
        // there is anything to throw.
        val pool = remember(pinned, state.deck, state.index) {
            pinned.mapNotNull { state.index.byId(CardId(it)) }
                .ifEmpty {
                    (state.deck.main + state.deck.extra + state.deck.side)
                        .distinct()
                        .mapNotNull(state.index::byId)
                }
        }

        EasterEgg(
            pool = pool,
            pinned = pinned.isNotEmpty(),
            onPin = {
                layout.update { preferences ->
                    preferences.copy(
                        easterEggPool = if (pinned.isNotEmpty()) {
                            emptyList()
                        } else {
                            pool.map { it.id.value }
                        },
                    )
                }
            },
            onDismiss = { state.eggVisible = false },
        )
    }
}

/**
 * What Escape closes, in order.
 *
 * One ordered list rather than a handler per surface. The tool this replaces had
 * Escape implemented in four separate places that did not agree about which one
 * won, which is the sort of thing that is invisible until the one time it closes
 * the wrong thing.
 */
private fun dismissTopLayer(state: DeckBuilderState, clearFocus: () -> Unit) {
    when {
        // Topmost first. The egg covers everything, so it leaves first too.
        state.eggVisible -> state.eggVisible = false
        state.inspection != null -> state.inspection = null
        state.helpVisible -> state.helpVisible = false
        state.filtersVisible -> state.filtersVisible = false
        state.statsVisible -> state.statsVisible = false
        state.sidingVisible -> state.sidingVisible = false
        state.issuesVisible -> state.issuesVisible = false
        state.query.isNotEmpty() -> state.onQueryChange("")
        else -> clearFocus()
    }
}

/**
 * Turns a completed drag into a deck edit.
 *
 * Four outcomes, and every one of them routes through the same editor that tap
 * and the stepper use — so a drop is undoable, and a drop the rules refuse says
 * so in the same words as any other rejected edit.
 */
private fun applyDrop(state: DeckBuilderState, session: DragSession, landed: DropHover?) {
    if (landed == null || !landed.accepted) return

    val target = landed.section
    when {
        // Dropped back on the pool: the copy leaves the deck.
        target == null -> session.section?.let { from ->
            state.removeAt(session.card, from, session.index)
        }

        // A group being carried moves as one, and only within its own section.
        // Checked before the single-card paths so the card actually under the
        // finger does not get moved on its own out of a selection of five.
        state.dragCarriesSelection(target, session.index) &&
            session.section == target -> state.moveSelectionTo(target, landed.index)

        session.section == null -> state.addCardAt(session.card, target, landed.index)

        else -> state.moveCardTo(
            card = session.card,
            from = session.section,
            fromIndex = session.index,
            to = target,
            insertBefore = landed.index,
        )
    }
}

@Composable
private fun SearchDeckDivider(onDrag: (Float) -> Unit) {
    val draggableState = rememberDraggableState(onDelta = onDrag)

    Box(
        Modifier
            .width(10.dp)
            .fillMaxHeight()
            .draggable(draggableState, Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
