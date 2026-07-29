package com.kaiharimoto.mastertool.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
// Aliased because `Card` means a Yu-Gi-Oh card everywhere else in this
// codebase, and one file where it means a container is one file where a
// reader has to check.
import androidx.compose.material3.Card as MaterialCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.data.StoredDeck
import com.kaiharimoto.mastertool.core.deck.DeckIdentity
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.deck.DeckLookCodec
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.core.search.TextMatching
import com.kaiharimoto.mastertool.core.util.RelativeTime
import com.kaiharimoto.mastertool.ui.AppDependencies
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CARD_CORNER
import com.kaiharimoto.mastertool.ui.components.CardFaceArt
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.DeckMats
import com.kaiharimoto.mastertool.ui.theme.MatColors
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import com.kaiharimoto.mastertool.ui.theme.wellSurface
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
    index: CardIndex,
    /** The deck in the builder, so a saved one can be held up against it. */
    openDeck: Deck,
    openDeckName: String,
    onOpenDeck: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<StoredDeck>>(emptyList()) }
    var reloadToken by remember { mutableStateOf(0) }
    var comparing by remember { mutableStateOf<StoredDeck?>(null) }

    // The deck whose history is open. Everything inside it belongs to the host.
    var historyOf by remember { mutableStateOf<StoredDeck?>(null) }

    // Stamped when the screen opens. A clock read during composition would make
    // "3 minutes ago" change under a recomposition triggered by something else
    // entirely, which reads as a glitch rather than as time passing.
    val openedAt = remember(reloadToken) { deps.now() }

    var query by remember { mutableStateOf("") }

    LaunchedEffect(reloadToken) {
        decks = deps.deckRepository.all()
    }

    val shown = remember(decks, query) {
        val needle = TextMatching.normalize(query)
        if (needle.isEmpty()) {
            decks
        } else {
            decks.filter { TextMatching.normalize(it.entry.name).contains(needle) }
        }
    }

    val colors = LocalMasterToolColors.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // The same cloth the deck panes are drawn on. Saved decks sitting on the
        // table is the whole idea of this screen, and it was the last one still
        // on a flat Material background.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .tableSurface(colors.accent, colors.mat)
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to the deck builder")
                }
                Text("Deck Library", style = MaterialTheme.typography.headlineMedium)
                Box(Modifier.weight(1f))

                // Punctuation-insensitive, through the same normaliser the card
                // search uses, so "snake eye" finds "Snake-Eye". Deck names are
                // typed by hand and remembered approximately.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Find a deck") },
                    modifier = Modifier.width(260.dp),
                )
                Box(Modifier.width(12.dp))
                Text(
                    if (shown.size == decks.size) {
                        "${decks.size} saved"
                    } else {
                        "${shown.size} of ${decks.size}"
                    },
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (decks.isEmpty()) {
                            "No saved decks yet. Build one and hit Save, or import a .ydk file."
                        } else {
                            "No deck here is called that."
                        },
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
                items(shown, key = { it.entry.id }) { stored ->
                    DeckCard(
                        stored = stored,
                        index = index,
                        // Each deck in the library sits on its own cloth, which
                        // is the whole point of the mat belonging to the deck:
                        // three lists you can tell apart before reading a name.
                        mat = DeckMats.of(DeckLookCodec.read(stored.extended), colors),
                        // Read once per visit rather than per tile, so nine
                        // decks saved in the same minute all say the same thing.
                        now = openedAt,
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
                        onCompare = { comparing = stored },
                        onHistory = { historyOf = stored },
                    )
                }
            }
        }
    }

    comparing?.let { stored ->
        CompareSheet(
            savedName = stored.entry.name,
            saved = stored.entry.deck,
            openName = openDeckName,
            open = openDeck,
            index = index,
            onDismiss = { comparing = null },
        )
    }

    historyOf?.let { stored ->
        DeckHistoryHost(
            deps = deps,
            deckId = stored.entry.id,
            index = index,
            now = openedAt,
            onRestored = {
                historyOf = null
                reloadToken++
                // Straight into the builder with it: going back to a version and
                // then having to find the deck again is two steps for one
                // intention.
                onOpenDeck(stored.entry.id)
            },
            onDismiss = { historyOf = null },
        )
    }
}

@Composable
private fun DeckCard(
    stored: StoredDeck,
    index: CardIndex,
    mat: MatColors,
    now: Long,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCompare: () -> Unit,
    onHistory: () -> Unit,
) {
    val deck = stored.entry.deck
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(stored.entry.id) { mutableStateOf(stored.entry.name) }

    MaterialCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        DeckFaces(DeckIdentity.faces(deck).mapNotNull(index::byId), mat)

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${deck.main.size} MAIN · ${deck.extra.size} EXTRA · ${deck.side.size} SIDE",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f))
                // Worth saying now that decks save themselves: "when did I last
                // touch this" is the question a shelf of them raises, and it
                // used to have no answer anywhere in the program.
                Text(
                    RelativeTime.since(now, stored.entry.updatedAtEpochMs),
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onCompare) { Text("What changed…") }
                TextButton(onClick = onHistory) { Text("Used to be…") }
            }
        }
    }
}

private val FACE_WIDTH = 78.dp
private val FACE_STEP = 80.dp
private val FAN_HEIGHT = 116.dp

/**
 * Three cards, laid out, standing for the whole deck.
 *
 * A saved deck listed by name alone is a filename, and a player with nine of
 * them is reading nine filenames to find the one they mean. Three faces and they
 * find it without reading anything.
 *
 * Fanned rather than butted together, which is the one place in this program
 * where cards deliberately do not touch: a deck pane is an arrangement and this
 * is a portrait of one, and three rectangles in a row reads as a contact sheet.
 * They bleed off the bottom under a scrim so the name can sit close underneath
 * without a hard edge between them.
 */
@Composable
private fun DeckFaces(faces: List<Card>, mat: MatColors) {
    val surface = MaterialTheme.colorScheme.surface

    Box(
        Modifier
            .fillMaxWidth()
            .height(FAN_HEIGHT)
            .wellSurface(mat),
    ) {
        faces.take(3).forEachIndexed { position, card ->
            Box(
                Modifier
                    .offset(
                        x = 14.dp + FACE_STEP * position,
                        // The middle card sits a little proud, which is what
                        // makes three cards read as a hand rather than a row.
                        y = if (position == 1) 8.dp else 15.dp,
                    )
                    .width(FACE_WIDTH)
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .graphicsLayer { rotationZ = (position - 1) * 7f }
                    .clip(RoundedCornerShape(CARD_CORNER)),
            ) {
                // Three grey rectangles standing for a deck defeats the point of
                // showing three cards at all, which is telling nine saved decks
                // apart without reading nine names.
                CardFaceArt(card, Modifier.fillMaxSize())
                AsyncImage(
                    model = card.imageUrlSmall ?: card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(FAN_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to surface,
                    ),
                ),
        )
    }
}

/** Renames only if the name actually changed and is not blank. */
private fun commit(draft: String, stored: StoredDeck, onRename: (String) -> Unit) {
    val name = draft.trim()
    if (name.isNotEmpty() && name != stored.entry.name) onRename(name)
}
