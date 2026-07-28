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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import com.kaiharimoto.mastertool.ui.theme.wellSurface
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DragSource
import com.kaiharimoto.mastertool.ui.dnd.DropHover

/** Where the stepper starts when it takes over from sizing-to-fit. */
private const val DEFAULT_POOL_COLUMNS = 6

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

    // A new query produces a new list; staying scrolled halfway down it shows
    // results that were never looked at.
    LaunchedEffect(state.query, state.filter) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier
            // Registered as a drop target so a card can be dragged out of the deck
            // and dropped back where cards come from.
            .onGloballyPositioned { drag.registerSearchPane(it.boundsInRoot()) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = state::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus)
                .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            singleLine = true,
            placeholder = { Text("Search cards") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { state.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Enter adds the best match and leaves the query alone, so three
            // presses is three copies without a hand leaving the keyboard.
            keyboardActions = KeyboardActions(onSearch = { state.addTopMatch() }),
        )

        Row(
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
            Box(Modifier.weight(1f))

            // The pool's own density. Sized to fit until you say otherwise,
            // like the deck panes; "Fit" puts it back.
            val fixed = layout.preferences.searchColumns
            IconButton(
                onClick = { layout.setSearchColumns(if (fixed > 0) fixed - 1 else DEFAULT_POOL_COLUMNS - 1) },
                enabled = fixed != SectionPreferences.MIN_COLUMNS,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer, larger cards per row")
            }
            if (fixed > 0) {
                TextButton(onClick = { layout.setSearchColumns(0) }) {
                    Text(fixed.toString(), style = tacticalStyle())
                }
            } else {
                Text("Fit", style = tacticalStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = { layout.setSearchColumns(if (fixed > 0) fixed + 1 else DEFAULT_POOL_COLUMNS + 1) },
                enabled = fixed != SectionPreferences.MAX_COLUMNS,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More, smaller cards per row")
            }

            Text(
                // Says how many cards matched, not how big the pool is: the old
                // readout compared a 150-card page against all 13,000 cards.
                if (state.matchCount > state.results.size) {
                    "${state.results.size} of ${state.matchCount} matches"
                } else {
                    "${state.matchCount} matches"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.filter.isActive) {
            ActiveFilterBar(state)
        }

        if (state.index.size == 0 && !state.isSyncing) {
            EmptyPoolNotice(onRetry = { state.refreshCardPool(force = true) })
            return@Column
        }

        if (state.results.isEmpty() && !state.isSyncing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing matches that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val fixedColumns = layout.preferences.searchColumns
        LazyVerticalGrid(
            columns = if (fixedColumns > 0) {
                GridCells.Fixed(fixedColumns)
            } else {
                GridCells.Adaptive(minSize = 108.dp)
            },
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .wellSurface(LocalMasterToolColors.current.mat)
                .padding(6.dp),
            // Open, but not as open as Material's default. The deck panes close
            // their gutter entirely because what is in them is an arrangement;
            // this is a box of cards, and cards in a box have gaps.
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
}

/** What is currently narrowing the pool, and a way to switch each piece off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterBar(state: DeckBuilderState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
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
private fun EmptyPoolNotice(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
