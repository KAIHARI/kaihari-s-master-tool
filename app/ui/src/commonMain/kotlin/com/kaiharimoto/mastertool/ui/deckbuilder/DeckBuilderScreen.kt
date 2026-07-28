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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CardInspector
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragOverlay
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DropHover
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
        }
    }

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
