package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * A line written against one card.
 *
 * Smaller than the deck's notes and used far more often: *only starter that
 * plays through Ash*, *third copy is for the mirror*, *never open this going
 * second*. The card is shown beside the box because a note about a card you
 * cannot see is a note about a passcode.
 *
 * No save button, exactly like the deck's notes: the text goes straight into the
 * deck and the deck saves itself. Anything that wrote on the way out instead
 * would lose the sentence to whichever way somebody happened to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardNotePanel(state: DeckBuilderState, id: CardId) {
    val card = state.index.byId(id)
    // The field keeps the raw text and the deck keeps it trimmed. Binding the
    // field straight to the model would eat the space the moment it was typed,
    // because a note is stored without its edges.
    var text by remember(id) { mutableStateOf(state.noteOn(id).orEmpty()) }

    MasterToolSheet(onDismiss = { state.noteTarget = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card?.name ?: id.value.toString(),
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
                if (card != null) {
                    Box(Modifier.width(96.dp)) {
                        CardTile(card = card, format = state.format)
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; state.writeNote(id, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        // Reported like every other field, so the letters typed
                        // here do not also fire the single-key shortcuts.
                        .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
                    placeholder = { Text("Why this card is in the deck…") },
                )
            }

            Text(
                "Every copy carries it, and clearing the box takes it away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // What this card does with other cards, which is written elsewhere
            // and is exactly what somebody opening this sheet wants to see.
            val pairs = state.pairNotes.about(id)
            if (pairs.isNotEmpty()) {
                Text(
                    "WITH",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                pairs.forEach { (pair, note) ->
                    val other = state.pairNotes.other(pair, than = id)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.noteTarget = null
                                state.pairTarget = id to other
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            state.index.byId(other)?.name ?: other.value.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
