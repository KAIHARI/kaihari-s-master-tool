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
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CardInspector
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                    modifier = Modifier.weight(layout.preferences.searchWeight).fillMaxHeight(),
                )

                SearchDeckDivider(onDrag = layout::resizeSearchPane)

                DeckPanes(
                    state = state,
                    layout = layout,
                    modifier = Modifier
                        .weight(1f - layout.preferences.searchWeight)
                        .fillMaxHeight(),
                )
            }
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
