package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What a pile is for, in the player's own words.
 *
 * The gaps already say *these nine go together*, and the statistics already work
 * out what a pile is made of — Snake-Eye, Monsters, Bonfire ×3 — by reading the
 * cards in it. That reading can never be wrong, because nobody wrote it, and it
 * can never say the thing that matters either: **the engine**, **what beats
 * Ryzeal**, **cut these first**.
 *
 * So a name, optional, on top of a reading that is already there. Empty it and
 * the derived one comes back, which is why there is no delete: clearing the box
 * is the delete, and what returns is not nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PileNamePanel(state: DeckBuilderState, pile: Pile) {
    // The field keeps what is being typed and the deck keeps it trimmed, so a
    // space typed mid-sentence is not eaten on its way to the model.
    var text by remember(pile.section, pile.start) { mutableStateOf(pile.name) }

    MasterToolSheet(onDismiss = { state.pileTarget = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Name this pile", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f))
                Text(
                    "${pile.size} CARDS · ${pile.section.displayName.uppercase()}",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Shown on the pile and beside its odds. Leave it empty and the " +
                    "program goes back to reading the pile off the cards in it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    state.namePile(pile.section, pile.start, it)
                },
                singleLine = true,
                label = { Text("The engine") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            )
        }
    }
}
