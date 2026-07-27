package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CardDetailSheet
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.update.UpdateState

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
            copiesInDeck = state.copiesInDeck(card.id),
            onDismiss = { state.inspectedCard = null },
            onAddTo = { section -> state.addCard(card, section) },
            onRemoveFrom = { section -> state.removeOne(card, section) },
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
}

@Composable
private fun DeckBuilderTopBar(
    state: DeckBuilderState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
) {
    val validation = state.validation

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
            modifier = Modifier.width(280.dp),
        )

        // Live legality readout: the number that matters at deck check.
        val mainCount = state.deck.main.size
        val legality = if (validation.isLegal) "Legal" else "${validation.errors.size} issue(s)"
        val legalityColor =
            if (validation.isLegal) MasterToolPalette.SideAccent else MasterToolPalette.Danger

        Column {
            Text("$mainCount main · ${state.deck.extra.size} extra · ${state.deck.side.size} side",
                style = MaterialTheme.typography.labelMedium)
            Text(legality, style = MaterialTheme.typography.labelMedium, color = legalityColor)
        }

        Box(Modifier.weight(1f))

        if (state.isSyncing) {
            CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            state.syncMessage?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }

        IconButton(onClick = state::undo, enabled = state.canUndo) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = { state.refreshCardPool(force = true) }) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh card database")
        }
        TextButton(onClick = state::newDeck) { Text("New") }
        TextButton(onClick = state::importFromFile) { Text("Import") }
        TextButton(onClick = state::exportToFile) { Text("Export") }
        IconButton(onClick = state::shareDeck) {
            Icon(Icons.Filled.Share, contentDescription = "Share deck")
        }
        IconButton(onClick = { state.save() }) {
            Icon(Icons.Filled.Save, contentDescription = "Save deck")
        }
        TextButton(onClick = onOpenLibrary) { Text("Library") }

        // Version doubles as the update control: tapping it checks GitHub.
        TextButton(
            onClick = { updateState.check(userInitiated = true) },
            enabled = !updateState.isChecking,
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Text(
                if (updateState.isChecking) "  Checking…" else "  v${updateState.currentVersionName}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPane(state: DeckBuilderState, modifier: Modifier = Modifier) {
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
            if (state.filter.isActive) {
                TextButton(onClick = state::clearFilters) { Text("Clear") }
            }
            Box(Modifier.weight(1f))
            Text(
                "${state.results.size} of ${state.index.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.index.size == 0 && !state.isSyncing) {
            EmptyPoolNotice(onRetry = { state.refreshCardPool(force = true) })
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
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
        DeckSectionPane(state, DeckSection.MAIN, MasterToolPalette.MainAccent, Modifier.weight(2f))
        DeckSectionPane(state, DeckSection.EXTRA, MasterToolPalette.ExtraAccent, Modifier.weight(1f))
        DeckSectionPane(state, DeckSection.SIDE, MasterToolPalette.SideAccent, Modifier.weight(1f))
    }
}

@Composable
private fun DeckSectionPane(
    state: DeckBuilderState,
    section: DeckSection,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val ids = state.deck[section]
    val overCapacity = ids.size > section.maxSize
    val underMinimum = ids.size < section.minSize

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
                "  ${ids.size}${if (section == DeckSection.MAIN) " / 40–60" else " / 15"}",
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
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Indexed keys because a deck legitimately holds duplicate passcodes.
            items(ids.size, key = { "${section.name}-$it-${ids[it].value}" }) { position ->
                val card: Card? = state.index.byId(ids[position])
                if (card == null) {
                    UnknownCardTile()
                } else {
                    CardTile(
                        card = card,
                        format = state.format,
                        onClick = { state.removeOne(card, section) },
                        onLongClick = { state.inspectedCard = card },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnknownCardTile() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MasterToolPalette.SlateRaised)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Unknown card")
    }
}
