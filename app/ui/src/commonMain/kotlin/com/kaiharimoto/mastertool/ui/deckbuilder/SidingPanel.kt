package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.siding.SidingPattern
import com.kaiharimoto.mastertool.core.siding.SidingSwap
import com.kaiharimoto.mastertool.ui.components.CARD_CORNER
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * The siding plans this deck carries, and the two buttons that apply one.
 *
 * Plans are keyed by the opponent's deck rather than by your own, because that
 * is the question being answered at a table: this is what is sitting across from
 * me, so this is what changes. Which is also why each row leads with three
 * cards — you recognise a matchup by its cards faster than by its name, and the
 * name in a tournament report is rarely the name you would have given it.
 *
 * Going first and going second are separate buttons rather than a mode set
 * somewhere else. They are different plans, you know which one you are on before
 * you reach for this, and making it a mode would mean setting it correctly and
 * then trusting that you had.
 *
 * Plans can be applied and recorded. Recording is a diff rather than a form:
 * you side by hand, and this keeps what you did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidingPanel(state: DeckBuilderState) {
    val patterns = state.sidingPatterns.entries.sortedBy { it.key }

    MasterToolSheet(onDismiss = { state.sidingVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Siding", style = MaterialTheme.typography.headlineSmall)

                Box(Modifier.weight(1f))

                // The thing the original tool actually delivered: a piece of
                // paper that goes beside the deck box, not another screen.
                if (patterns.isNotEmpty()) {
                    TextButton(onClick = { state.exportSidingSheet() }) {
                        Text("Print sheet…")
                    }
                }
            }

            Recorder(state)

            if (patterns.isEmpty()) {
                Text(
                    "No plans saved yet. Move cards between the Main and Side decks " +
                        "until the matchup is right, then record what you did.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                "Applying a plan is one edit, and one undo puts it back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(patterns, key = { it.key }) { (name, pattern) ->
                    PatternRow(
                        state = state,
                        name = name,
                        pattern = pattern,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRow(state: DeckBuilderState, name: String, pattern: SidingPattern) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            pattern.thumbnails.take(3).forEach { Thumbnail(state, it) }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pattern.deckName.ifBlank { name },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                summarise(pattern.goingFirst, pattern.goingSecond),
                style = tacticalStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { state.applySiding(pattern, goingFirst = true) },
            enabled = !pattern.goingFirst.isEmpty,
        ) {
            Text("Going 1st")
        }
        OutlinedButton(
            onClick = { state.applySiding(pattern, goingFirst = false) },
            enabled = !pattern.goingSecond.isEmpty,
        ) {
            Text("Going 2nd")
        }
    }
}

/**
 * How big each plan is, before committing to it.
 *
 * An unbalanced swap is legal and almost always a slip, and it is the one thing
 * you cannot see by reading the two lists side by side — so it is said here
 * rather than discovered afterwards from a deck that is now thirty-nine cards.
 */
private fun summarise(first: SidingSwap, second: SidingSwap): String = buildString {
    append("1st ").append(first.cardsIn.size).append(" in / ").append(first.cardsOut.size).append(" out")
    if (!first.isBalanced) append(" ⚠")
    append("   2nd ").append(second.cardsIn.size).append(" in / ").append(second.cardsOut.size).append(" out")
    if (!second.isBalanced) append(" ⚠")
}

@Composable
private fun Thumbnail(state: DeckBuilderState, id: CardId) {
    val card = state.index.byId(id)

    Box(
        Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(
            model = card?.imageUrlSmall ?: card?.imageUrl,
            contentDescription = card?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(34.dp),
        )
    }
}

/**
 * Records what has been swapped so far.
 *
 * The plan is arrived at by doing it — nobody writes a swap down and then
 * performs it — so this reports the difference between the deck on screen and
 * the list you registered, and offers to keep it. Everything it needs is
 * already true before you open this panel.
 */
@Composable
private fun Recorder(state: DeckBuilderState) {
    val swap = state.pendingSwap
    var opponent by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (swap.isEmpty) {
            Text(
                "The deck matches the list you registered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Swapped so far: ${swap.cardsIn.size} in / ${swap.cardsOut.size} out" +
                    if (swap.isBalanced) "" else "  ⚠ uneven",
                style = tacticalStyle(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { state.resetToRegistered() }) { Text("Undo all") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = opponent,
                onValueChange = { opponent = it },
                singleLine = true,
                label = { Text("Against") },
                placeholder = { Text("Snake-Eye") },
                modifier = Modifier.weight(1f),
            )

            // Which half this describes. A toggle rather than two save buttons:
            // you are on one side of a matchup at a time, and saying which is a
            // smaller decision than picking between two verbs.
            FilterChip(
                selected = state.recordingGoingFirst,
                onClick = { state.recordingGoingFirst = true },
                label = { Text("1st") },
            )
            FilterChip(
                selected = !state.recordingGoingFirst,
                onClick = { state.recordingGoingFirst = false },
                label = { Text("2nd") },
            )

            Button(
                onClick = { state.recordSiding(opponent); opponent = "" },
                enabled = opponent.isNotBlank(),
            ) {
                Text("Record")
            }
        }
    }
}
