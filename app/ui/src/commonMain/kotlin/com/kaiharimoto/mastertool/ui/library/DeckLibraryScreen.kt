package com.kaiharimoto.mastertool.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.data.StoredDeck
import com.kaiharimoto.mastertool.ui.AppDependencies
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlinx.coroutines.launch

/**
 * Saved decks.
 *
 * Deliberately a grid of large targets rather than a dense list: this screen is
 * used with a thumb, often while holding the tablet one-handed at a venue.
 */
@Composable
fun DeckLibraryScreen(
    deps: AppDependencies,
    onOpenDeck: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<StoredDeck>>(emptyList()) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(reloadToken) {
        decks = deps.deckRepository.all()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to the deck builder")
                }
                Text("Deck Library", style = MaterialTheme.typography.headlineMedium)
                Box(Modifier.weight(1f))
                Text(
                    "${decks.size} saved",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (decks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No saved decks yet. Build one and hit Save, or import a .ydk file.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(decks, key = { it.entry.id }) { stored ->
                    DeckCard(
                        stored = stored,
                        onOpen = { onOpenDeck(stored.entry.id) },
                        onRename = { name ->
                            scope.launch {
                                deps.deckRepository.rename(stored.entry.id, name)
                                reloadToken++
                            }
                        },
                        onDelete = {
                            scope.launch {
                                deps.deckRepository.delete(stored.entry.id)
                                reloadToken++
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckCard(
    stored: StoredDeck,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val deck = stored.entry.deck
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(stored.entry.id) { mutableStateOf(stored.entry.name) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (renaming) {
                    // Committed on the tick or on Done, and abandoned on the
                    // cross. A deck name is worth confirming: the file it writes
                    // is named after it.
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text("Deck name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { renaming = false; commit(draft, stored, onRename) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { renaming = false; commit(draft, stored, onRename) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save name")
                    }
                    IconButton(onClick = { renaming = false; draft = stored.entry.name }) {
                        Icon(Icons.Filled.Close, contentDescription = "Keep the old name")
                    }
                } else {
                    Text(
                        stored.entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename ${stored.entry.name}")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete ${stored.entry.name}",
                        tint = MasterToolPalette.Danger,
                    )
                }
            }

            Text(
                "${deck.main.size} main · ${deck.extra.size} extra · ${deck.side.size} side",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stored.entry.notes.isNotBlank()) {
                // Stored end to end since the repository was written and never
                // shown anywhere. Whatever is in there was worth writing down.
                Text(
                    stored.entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (stored.extended != null) {
                // Signals that this deck carries desktop-authored YDKX data.
                Text(
                    "Includes siding data",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}

/** Renames only if the name actually changed and is not blank. */
private fun commit(draft: String, stored: StoredDeck, onRename: (String) -> Unit) {
    val name = draft.trim()
    if (name.isNotEmpty() && name != stored.entry.name) onRename(name)
}
