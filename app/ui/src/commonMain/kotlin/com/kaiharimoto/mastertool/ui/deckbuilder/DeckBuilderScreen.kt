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
import com.kaiharimoto.mastertool.core.layout.GridStep
import com.kaiharimoto.mastertool.core.input.ShortcutContext
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CardInspector
import com.kaiharimoto.mastertool.ui.components.MasterToolSnackbar
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
    onOpenSandbox: (List<CardId>) -> Unit,
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

    val overlayOpen = state.anyOverlayOpen

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
                ShortcutAction.TOGGLE_TEST_HAND -> {
                    // Dealing on the way in, so the panel is never empty when it
                    // opens -- the whole point is to be looking at a hand.
                    if (!state.testHandVisible) state.dealTestHand(state.testHandGoingFirst)
                    state.testHandVisible = !state.testHandVisible
                }
                ShortcutAction.TOGGLE_SIDING -> state.sidingVisible = !state.sidingVisible
                ShortcutAction.TOGGLE_HELP -> state.helpVisible = !state.helpVisible
                ShortcutAction.TOGGLE_SHOWCASE -> state.showcaseVisible = !state.showcaseVisible
                ShortcutAction.TOGGLE_GAP -> state.toggleGapAtCursor()
                ShortcutAction.DISMISS -> dismissTopLayer(state) { focusManager.clearFocus() }
                ShortcutAction.FOCUS_MAIN -> layout.focusSection(DeckSection.MAIN)
                ShortcutAction.FOCUS_EXTRA -> layout.focusSection(DeckSection.EXTRA)
                ShortcutAction.FOCUS_SIDE -> layout.focusSection(DeckSection.SIDE)
                ShortcutAction.PREVIOUS_CARD -> state.pageInspection(-1)
                ShortcutAction.NEXT_CARD -> state.pageInspection(1)

                // The grid's width is a layout fact, so it is fetched here at
                // the moment the key is pressed rather than held in the state
                // holder, which would then have to be told about every resize.
                ShortcutAction.CURSOR_LEFT -> state.arrow(layout, GridStep.LEFT)
                ShortcutAction.CURSOR_RIGHT -> state.arrow(layout, GridStep.RIGHT)
                ShortcutAction.CURSOR_UP -> state.arrow(layout, GridStep.UP)
                ShortcutAction.CURSOR_DOWN -> state.arrow(layout, GridStep.DOWN)

                ShortcutAction.EXTEND_LEFT -> state.arrow(layout, GridStep.LEFT, extend = true)
                ShortcutAction.EXTEND_RIGHT -> state.arrow(layout, GridStep.RIGHT, extend = true)
                ShortcutAction.EXTEND_UP -> state.arrow(layout, GridStep.UP, extend = true)
                ShortcutAction.EXTEND_DOWN -> state.arrow(layout, GridStep.DOWN, extend = true)

                ShortcutAction.CARRY_LEFT -> state.carry(layout, GridStep.LEFT)
                ShortcutAction.CARRY_RIGHT -> state.carry(layout, GridStep.RIGHT)
                ShortcutAction.CARRY_UP -> state.carry(layout, GridStep.UP)
                ShortcutAction.CARRY_DOWN -> state.carry(layout, GridStep.DOWN)
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) { MasterToolSnackbar(it) } },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            // The dragged card is composed here, outside every pane, so it is not
            // clipped by the grid it was lifted out of.
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    DeckBuilderTopBar(
                        state = state,
                        layout = layout,
                        updateState = updateState,
                        onOpenLibrary = onOpenLibrary,
                        onOpenSandbox = { onOpenSandbox(emptyList()) },
                    )
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

    if (state.testHandVisible) TestHandPanel(state, onOpenSandbox)

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
            noteOn = { state.noteOn(it.id) },
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
            breaks = state.breaksIn(state.statsSection),
            onSectionChange = { state.statsSection = it },
            onBrowse = { filter ->
                state.onQueryChange("")
                state.onFilterChange(filter)
                state.statsVisible = false
            },
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

    if (state.notesVisible) {
        DeckNotesPanel(state)
    }

    state.noteTarget?.let { CardNotePanel(state, it) }

    if (state.matVisible) MatPanel(state)

    if (state.shootoutVisible) {
        ShootoutPanel(state, onOpenSandbox)
    }

    // Not a sheet. A sheet would leave the builder showing around the edges,
    // and the whole point is that there is nothing else on the screen.
    if (state.showcaseVisible) {
        DeckShowcase(state, onDismiss = { state.showcaseVisible = false })
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
 * An arrow key, given the width of the pane it lands in.
 *
 * The state holder deliberately does not know how wide any grid is — that is a
 * layout fact, remeasured on every resize, and a copy of it kept elsewhere would
 * be a copy that goes stale between the resize and the next key press. So it is
 * fetched here, at the moment of the press, from the pane that just measured it.
 */
private fun DeckBuilderState.arrow(
    layout: DeckLayoutState,
    step: GridStep,
    extend: Boolean = false,
) {
    val section = selection.section ?: DeckSection.MAIN
    moveCursor(step, layout.columnsOn(section), extend)
}

private fun DeckBuilderState.carry(layout: DeckLayoutState, step: GridStep) {
    val section = selection.section ?: return
    carrySelection(step, layout.columnsOn(section))
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
        // The layers come first, in the order they stack, and that order is
        // declared once beside the flags themselves rather than here.
        state.dismissTopOverlay() -> Unit
        // Before the search box, and after every overlay: a selection is the
        // most recent thing you started, and putting cards down is what Escape
        // means while you are holding some.
        !state.selection.isEmpty -> state.clearSelection()
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
