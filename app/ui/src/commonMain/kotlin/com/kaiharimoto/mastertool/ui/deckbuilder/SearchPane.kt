package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DragSource
import com.kaiharimoto.mastertool.ui.dnd.DropHover

/**
 * The card pool, laid out for a window with width to spare.
 *
 * Field at the top, results under it, which is what a pane down the left-hand
 * side of a tablet wants. The portrait builder assembles the *same four pieces*
 * in the other order — see `PoolDock` — because on a phone the field belongs at
 * the bottom where the thumbs are, and the pieces being shared is what stops
 * the two arrangements from drifting into two different card pools.
 */
@Composable
fun SearchPane(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    searchFocus: FocusRequester,
    onDropped: (DragSession, DropHover?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    Column(
        modifier
            // Registered as a drop target so a card can be dragged out of the deck
            // and dropped back where cards come from.
            .onGloballyPositioned { drag.registerSearchPane(it.boundsInRoot()) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchField(
            state = state,
            searchFocus = searchFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        SearchMetaRow(state = state, layout = layout)

        if (state.filter.isActive) {
            ActiveFilterBar(state)
        }

        PoolGrid(
            state = state,
            layout = layout,
            drag = drag,
            gridState = gridState,
            onDropped = onDropped,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The one text field the whole pool is driven from.
 *
 * [onSubmit] is what the IME's Search key does. On a tablet that is nothing —
 * the results are already there, the field never covered them. On a phone it is
 * the whole gesture: the keyboard goes away and the dock settles back to where
 * the deck is visible again, which is the moment a search stops being a search
 * and becomes a list of cards to put in a deck.
 */
@Composable
fun SearchField(
    state: DeckBuilderState,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = state.query,
        onValueChange = state::onQueryChange,
        modifier = modifier
            .focusRequester(searchFocus)
            .onFocusChanged {
                state.onTextFieldFocusChanged(it.isFocused)
                onFocusChanged(it.isFocused)
            },
        singleLine = true,
        placeholder = {
            Text(
                if (state.searchEffects) "Search names and effects" else "Search cards",
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { state.onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSubmit()
            },
        ),
    )
}

/**
 * What is narrowing the pool, and what the numbers underneath mean.
 *
 * The effect-text chip is here rather than in the filter sheet because it is
 * not a filter: it changes what a *query* means rather than which cards a query
 * is allowed to return, and it is worth one tap rather than two.
 */
@Composable
fun SearchMetaRow(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { state.filtersVisible = true }) {
            Icon(Icons.Filled.FilterList, contentDescription = null)
            Text(
                if (state.filter.isActive) {
                    "  Filters (${state.filter.activeFacetCount})"
                } else {
                    "  Filters"
                },
            )
        }

        FilterChip(
            selected = state.searchEffects,
            onClick = {
                val next = !state.searchEffects
                state.onSearchEffectsChange(next)
                layout.update { it.copy(searchEffects = next) }
            },
            label = { Text("Effects") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Subject,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        Box(Modifier.weight(1f))

        Text(
            matchSummary(state),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The readout under the field.
 *
 * Two numbers when the text search found anything, because they are two
 * different answers: one card is *called* what you typed and thirty-seven
 * *mention* it, and a single total of thirty-eight reads as a search that has
 * gone wrong.
 */
private fun matchSummary(state: DeckBuilderState): String {
    val shown = state.results.size
    val total = state.matchCount
    val head = if (total > shown) "$shown of $total matches" else "$total matches"
    return if (state.effectMatchCount > 0) "$head · ${state.effectMatchCount} by effect" else head
}

/** The pool itself: a grid of tiles you can tap to add or drag onto the deck. */
@Composable
fun PoolGrid(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    gridState: LazyGridState,
    onDropped: (DragSession, DropHover?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = layout.preferences.searchColumns,
) {
    // A new query produces a new list; staying scrolled halfway down it shows
    // results that were never looked at.
    LaunchedEffect(state.query, state.filter, state.searchEffects) {
        gridState.scrollToItem(0)
    }

    if (state.index.size == 0 && !state.isSyncing) {
        EmptyPoolNotice(
            onRetry = { state.refreshCardPool(force = true) },
            modifier = modifier,
        )
        return
    }

    if (state.results.isEmpty() && !state.isSyncing) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                if (state.query.isEmpty()) "Nothing matches those filters." else "Nothing matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = if (columns > 0) {
            GridCells.Fixed(columns)
        } else {
            GridCells.Adaptive(minSize = 108.dp)
        },
        state = gridState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.results.size, key = { state.results[it].id.value }) { position ->
            val card = state.results[position]
            DragSource(
                controller = drag,
                key = card.id.value,
                // The pool is thousands of cards deep and always scrolls, so
                // a finger that moves straight away is scrolling it.
                competesWithScroll = true,
                session = { DragSession(card, section = null, index = position, size = IntSize.Zero) },
                onLongPress = { state.inspect(state.results, position) },
                onDropped = onDropped,
            ) {
                HoverPreview(card) {
                    CardTile(
                        card = card,
                        format = state.format,
                        copies = state.copiesInDeck(card.id),
                        // Cards that cannot be added are dimmed rather than
                        // hidden, so the pool stays stable while you scan it.
                        dimmed = state.remaining(card) == 0,
                        onClick = { state.addCard(card) },
                    )
                }
            }
        }
    }
}

/** What is currently narrowing the pool, and a way to switch each piece off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterBar(state: DeckBuilderState, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.filter.pills().forEach { pill ->
            AssistChip(
                onClick = { state.onFilterChange(pill.without) },
                label = { Text(pill.label) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Remove ${pill.label} filter",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        TextButton(onClick = state::clearFilters) { Text("Clear all") }
    }
}

@Composable
private fun EmptyPoolNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No cards downloaded yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Connect to the internet once to fetch the card database. " +
                "After that the app works offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        TextButton(onClick = onRetry) { Text("Download now") }
    }
}
