package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What two cards do together.
 *
 * Both cards are shown, side by side and the same size, because that is what the
 * note is about — a pair note beside one card would read as a note on that card
 * that happens to mention another.
 *
 * Saved as it is typed, like everything else written into a deck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairNotePanel(state: DeckBuilderState, a: CardId, b: CardId) {
    val first = state.index.byId(a)
    val second = state.index.byId(b)

    // The field keeps the raw text and the deck keeps it trimmed, so the space
    // bar is not eaten the moment it is pressed.
    var text by remember(a, b) { mutableStateOf(state.noteOn(a, b).orEmpty()) }

    MasterToolSheet(onDismiss = { state.pairTarget = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${first?.name ?: a.value}  +  ${second?.name ?: b.value}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Box(Modifier.weight(1f))
                Text(
                    "SAVED WITH THE DECK",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOfNotNull(first, second).forEach { card ->
                    Box(Modifier.width(88.dp)) {
                        CardTile(card = card, format = state.format)
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; state.writePairNote(a, b, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
                    placeholder = { Text("What these two do together…") },
                )
            }

            Text(
                "A decklist cannot hold this. Clearing the box takes it away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
