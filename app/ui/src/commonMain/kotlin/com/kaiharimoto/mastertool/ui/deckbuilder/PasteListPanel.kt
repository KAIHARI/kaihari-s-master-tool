package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.kaiharimoto.mastertool.core.ydk.DecklistText
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * A decklist that arrived as text.
 *
 * Every list in the wild is a message, a post under a tournament report, a
 * column copied out of a table. This program could open a `.ydk` and nothing
 * else, which meant it could only accept a list from somebody who already had
 * this program.
 *
 * A field to paste into rather than a clipboard button: the paste gesture is one
 * every platform already has and every player already knows, and reaching for
 * the clipboard programmatically is a different API on each of the three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteListPanel(state: DeckBuilderState) {
    var text by remember { mutableStateOf("") }
    val ready = remember(text) { DecklistText.looksLikeAList(text) }

    MasterToolSheet(onDismiss = { state.pasteVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paste a decklist", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f))
                Text(
                    "${text.lineSequence().count { it.isNotBlank() }} LINES",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Counts in front or behind, headings or none at all. Anything it " +
                    "cannot find a card for is listed back at you rather than dropped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("3 Ash Blossom & Joyous Spring") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp)
                    // The shortcut layer has to stop reading keys as commands
                    // while somebody is typing into a field, or a list with a
                    // `v` in it opens the full-deck view halfway through.
                    .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f))
                TextButton(onClick = { state.pasteVisible = false }) { Text("Cancel") }
                Button(
                    enabled = ready,
                    onClick = {
                        state.readPastedList(text)
                        state.pasteVisible = false
                    },
                ) { Text("Read it") }
            }
        }
    }
}
