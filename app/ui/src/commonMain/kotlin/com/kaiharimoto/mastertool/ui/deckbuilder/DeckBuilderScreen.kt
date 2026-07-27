package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardDetailSheet
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.accent
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.update.UpdateState
import kotlinx.coroutines.delay

/** How long a revealed card keeps its highlight before settling back. */
private const val FLASH_MS = 1400L

/**
 * The deck builder, laid out for a landscape tablet.
 *
 * Search sits on the left under the thumb of whichever hand is holding the
 * tablet, and the three deck sections stack down the right where they can all be
 * seen at once — the thing the desktop tool's collapsible panes were working
 * around on a small screen.
 */
@Composable
fun DeckBuilderScreen(
    state: DeckBuilderState,
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
            DeckBuilderTopBar(state, updateState, onOpenLibrary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(Modifier.fillMaxSize()) {
                SearchPane(state, Modifier.weight(0.36f).fillMaxHeight())

                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline),
                )

                DeckPanes(state, Modifier.weight(0.64f).fillMaxHeight())
            }
        }
    }

    state.inspectedCard?.let { card ->
        CardDetailSheet(
            card = card,
            format = state.format,
            copiesBySection = DeckSection.entries.associateWith { state.copiesIn(card.id, it) },
            mainDeckSize = state.deck.main.size,
            openingHandOdds = { copies, handSize ->
                state.mainStatistics.openingHandOdds(copies, handSize)
            },
            onDismiss = { state.inspectedCard = null },
            onSetCount = { section, count -> state.setCount(card, section, count) },
            onMove = { from, to -> state.moveCard(card, from, to) },
            onBrowse = { filter ->
                state.onQueryChange("")
                state.onFilterChange(filter)
                state.inspectedCard = null
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
private fun DeckBuilderTopBar(
    state: DeckBuilderState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
) {
    val validation = state.validation
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "kai's master tool",
            style = MaterialTheme.typography.titleMedium,
            color = MasterToolPalette.Gold,
        )

        OutlinedTextField(
            value = state.deckName,
            onValueChange = state::rename,
            singleLine = true,
            label = { Text("Deck") },
            modifier = Modifier.width(240.dp),
        )

        Text(
            "${state.deck.main.size} main · ${state.deck.extra.size} extra · " +
                "${state.deck.side.size} side",
            style = MaterialTheme.typography.labelMedium,
        )

        // The legality readout is the way in to the issue list: knowing there are
        // three problems is only useful if you can find out what they are.
        AssistChip(
            onClick = { state.issuesVisible = true },
            label = {
                Text(
                    if (validation.isLegal) {
                        if (validation.warnings.isEmpty()) "Legal" else "${validation.warnings.size} note(s)"
                    } else {
                        "${validation.errors.size} issue(s)"
                    },
                    color = when {
                        !validation.isLegal -> MasterToolPalette.Danger
                        validation.warnings.isNotEmpty() -> MasterToolPalette.Warning
                        else -> MasterToolPalette.SideAccent
                    },
                )
            },
        )

        Box(Modifier.weight(1f))

        // Switching format re-badges the whole pool and re-runs validation,
        // because the banlist is read through it everywhere.
        Format.entries.forEach { entry ->
            FilterChip(
                selected = state.format == entry,
                onClick = { state.onFormatChange(entry) },
                label = { Text(entry.name) },
            )
        }

        if (state.isSyncing) {
            CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            state.syncMessage?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }

        IconButton(onClick = state::undo, enabled = state.canUndo) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = state::redo, enabled = state.canRedo) {
            Icon(Icons.Filled.Redo, contentDescription = "Redo")
        }
        IconButton(onClick = { state.statsVisible = true }) {
            Icon(Icons.Filled.BarChart, contentDescription = "Deck statistics")
        }
        IconButton(onClick = { state.save() }) {
            Icon(Icons.Filled.Save, contentDescription = "Save deck")
        }
        TextButton(onClick = onOpenLibrary) { Text("Library") }

        // Everything that is not part of building the list lives behind one
        // control, so the bar still fits when the window is not 1600dp wide.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("New deck") },
                    onClick = { menuOpen = false; state.newDeck() },
                )
                DropdownMenuItem(
                    text = { Text("Import…") },
                    onClick = { menuOpen = false; state.importFromFile() },
                )
                DropdownMenuItem(
                    text = { Text("Export…") },
                    onClick = { menuOpen = false; state.exportToFile() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { menuOpen = false; state.shareDeck() },
                )
                DropdownMenuItem(
                    text = { Text("Refresh card database") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = { menuOpen = false; state.refreshCardPool(force = true) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (updateState.isChecking) "Checking…" else "v${updateState.currentVersionName}"
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    onClick = { menuOpen = false; updateState.check(userInitiated = true) },
                )
            }
        }
    }
}

@Composable
private fun SearchPane(state: DeckBuilderState, modifier: Modifier = Modifier) {
    val gridState = rememberLazyGridState()

    // A new query produces a new list; staying scrolled halfway down it shows
    // results that were never looked at.
    LaunchedEffect(state.query, state.filter) {
        gridState.scrollToItem(0)
    }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = state::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
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

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.results, key = { it.id.value }) { card ->
                val remaining = state.remaining(card)
                CardTile(
                    card = card,
                    format = state.format,
                    copies = state.copiesInDeck(card.id),
                    // Cards that cannot be added are dimmed rather than hidden,
                    // so the pool stays stable while you scan it.
                    dimmed = remaining == 0,
                    onClick = { state.addCard(card) },
                    onLongClick = { state.inspectedCard = card },
                )
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
                        modifier = Modifier.height(16.dp).width(16.dp),
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

@Composable
private fun DeckPanes(state: DeckBuilderState, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeckSectionPane(state, DeckSection.MAIN, Modifier.weight(2f))
        DeckSectionPane(state, DeckSection.EXTRA, Modifier.weight(1f))
        DeckSectionPane(state, DeckSection.SIDE, Modifier.weight(1f))
    }
}

@Composable
private fun DeckSectionPane(
    state: DeckBuilderState,
    section: DeckSection,
    modifier: Modifier = Modifier,
) {
    val ids = state.deck[section]
    val accent = section.accent()
    val overCapacity = ids.size > section.maxSize
    val underMinimum = ids.size < section.minSize
    val gridState = rememberLazyGridState()
    var flashed by remember { mutableStateOf<CardId?>(null) }

    // Only the pane that owns the requested section reacts; the others ignore it.
    LaunchedEffect(state.revealRequest?.id) {
        val request = state.revealRequest ?: return@LaunchedEffect
        if (request.section != section) return@LaunchedEffect

        gridState.animateScrollToItem(request.position)
        flashed = request.cardId
        delay(FLASH_MS)
        flashed = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Text(
                "  ${section.displayName} Deck",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                // Read from the section rather than written out, so the bounds
                // cannot drift from the ones the validator enforces.
                if (section.minSize > 0) {
                    "  ${ids.size} / ${section.minSize}–${section.maxSize}"
                } else {
                    "  ${ids.size} / ${section.maxSize}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    overCapacity || underMinimum -> MasterToolPalette.Danger
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (ids.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap a card on the left to add it here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 78.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Indexed keys because a deck legitimately holds duplicate passcodes.
            items(ids.size, key = { "${section.name}-$it-${ids[it].value}" }) { position ->
                val id = ids[position]
                val card: Card? = state.index.byId(id)
                if (card == null) {
                    UnknownCardTile(id)
                } else {
                    CardTile(
                        card = card,
                        format = state.format,
                        copies = state.copiesIn(id, section),
                        highlighted = flashed == id,
                        onClick = { state.removeOne(card, section) },
                        onLongClick = { state.inspectedCard = card },
                    )
                }
            }
        }
    }
}

/**
 * A passcode the card database does not know.
 *
 * Given the same shape as a real card so it does not collapse its grid row, and
 * showing the passcode because that is the only thing anyone can act on — it is
 * what the validator names in the matching error.
 */
@Composable
private fun UnknownCardTile(id: CardId) {
    Box(
        Modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(4.dp))
            .background(MasterToolPalette.SlateRaised)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Unknown\n${id.value}",
            style = MaterialTheme.typography.labelSmall,
            color = MasterToolPalette.Danger,
        )
    }
}
