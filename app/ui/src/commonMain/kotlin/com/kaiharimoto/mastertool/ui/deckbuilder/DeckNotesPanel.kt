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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What the deck is for, in the player's own words.
 *
 * The one thing in this program that is neither a card nor a number: why the
 * third copy was cut, what to do against the matchup that keeps beating you,
 * which line the Extra deck is actually built around. A decklist cannot hold any
 * of that, and it is most of what somebody knows about their own deck.
 *
 * No save button. The text goes straight into the deck and the deck saves
 * itself, which is the only behaviour that makes sense for something written in
 * a hurry between rounds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckNotesPanel(state: DeckBuilderState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { state.notesVisible = false },
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notes", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f))
                Text(
                    "SAVED WITH THE DECK",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.deckNotes,
                onValueChange = state::updateNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    // Reported like every other field, so the letters typed here
                    // do not also fire the single-key shortcuts.
                    .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
                placeholder = {
                    Text("Why the third copy came out, what to do against the bad matchup…")
                },
            )
        }
    }
}
