package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
        SearchField(state, searchFocus)

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

/**
 * The search box.
 *
 * Hand-built rather than an `OutlinedTextField`, for two reasons. The visible
 * one is that a Material field with a floating label is the single most
 * generic-looking thing that was left on the screen, and this is the widget the
 * app is used through. The load-bearing one is the completion: the grey text
 * after the cursor has to line up with the typed text character for character,
 * and the only way to guarantee that is to draw both with the same style at the
 * same origin, which means owning the padding rather than inheriting it.
 *
 * So the ghost is one `Text` holding the whole completed name, with the typed
 * prefix drawn in [Color.Transparent] and the real field laid over it. The
 * transparent run occupies exactly the advance width of the text it is standing
 * in for, because it *is* that text.
 */
@Composable
private fun SearchField(state: DeckBuilderState, searchFocus: FocusRequester) {
    val colors = LocalMasterToolColors.current
    val scheme = MaterialTheme.colorScheme
    val completion = state.completion

    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(state.query)) }

    // The query also changes from outside the field: Escape clears it, Tab
    // rewrites it to the completed name. Both have to leave the cursor at the
    // end, which is the one thing the String-valued text field cannot say.
    LaunchedEffect(state.query) {
        if (state.query != field.text) {
            field = TextFieldValue(state.query, TextRange(state.query.length))
        }
    }

    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(colors.mat.base.copy(alpha = 0.55f))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) colors.accentBright else scheme.outlineVariant,
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = if (focused) colors.accentBright else scheme.onSurfaceVariant,
        )

        Box(Modifier.weight(1f)) {
            when {
                completion != null -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.Transparent)) { append(completion.typed) }
                        withStyle(SpanStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                            append(completion.suffix)
                        }
                    },
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )

                state.query.isEmpty() -> Text(
                    "Search cards",
                    style = textStyle.copy(color = scheme.onSurfaceVariant.copy(alpha = 0.6f)),
                    maxLines = 1,
                )
            }

            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != state.query) state.onQueryChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus)
                    .onFocusChanged {
                        focused = it.isFocused
                        state.onTextFieldFocusChanged(it.isFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.Tab) {
                            return@onPreviewKeyEvent false
                        }
                        // Consumed whether or not there was anything to accept.
                        // Tab out of a search box and into whichever control the
                        // focus order happens to reach next is never the intent.
                        state.acceptCompletion()
                        true
                    },
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.accentBright),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Enter adds the best match and leaves the query alone, so three
                // presses is three copies without a hand leaving the keyboard.
                keyboardActions = KeyboardActions(onSearch = { state.addTopMatch() }),
            )
        }

        if (completion != null) {
            Text(
                "TAB",
                style = tacticalStyle(),
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        if (state.query.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Clear search",
                modifier = Modifier
                    .size(22.dp)
                    .clickable { state.onQueryChange("") }
                    .padding(3.dp),
                tint = scheme.onSurfaceVariant,
            )
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
