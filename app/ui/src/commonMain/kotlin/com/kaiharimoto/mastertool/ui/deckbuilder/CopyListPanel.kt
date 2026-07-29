package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * The deck as the text people paste to each other.
 *
 * The other half of reading one in, and the half that gets used more: a list
 * goes into a message, a post, a reply to somebody asking what you played. Every
 * other builder can hand you a file, which is the wrong object for a
 * conversation.
 *
 * A field to copy out of rather than a clipboard button, for the reason the
 * paste field is a field: the copy gesture is one every platform already has and
 * every player already knows, and reaching for the clipboard programmatically is
 * a different API on each of the three. Editable, because a read-only field on a
 * tablet is a field you cannot select out of — and nothing here reads it back,
 * so an edit costs nothing but the edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyListPanel(state: DeckBuilderState) {
    // Taken once, when the sheet opens. Recomputing it under an edit would put
    // the deck back into a box somebody is halfway through changing.
    var text by remember { mutableStateOf(state.asText) }

    MasterToolSheet(onDismiss = { state.copyVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Copy the list", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f))
                Text(
                    "${state.deck.totalCards} CARDS",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                // Names come out of the pool, so a pool that has not been read
                // yet writes sixty passcodes. Which is a valid decklist and
                // reads straight back in — and is not what anybody wants to put
                // in a message.
                if (state.poolRead) {
                    "Counted, headed and in the deck's own order. This program reads " +
                        "it straight back, and so does everything else that takes a list."
                } else {
                    "The card database is still opening, so this is coming out as " +
                        "passcodes. Give it a moment and it will come out as names."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
                    .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        state.exportAsText()
                        state.copyVisible = false
                    },
                ) { Text("Save as .txt") }
                TextButton(onClick = { state.copyVisible = false }) { Text("Done") }
            }
        }
    }
}
