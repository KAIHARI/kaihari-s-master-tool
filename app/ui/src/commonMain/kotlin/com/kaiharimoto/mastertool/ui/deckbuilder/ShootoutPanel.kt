package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.OpeningHand
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * Two decks, two opening hands, at the same time.
 *
 * The test-hand panel answers "does my list open"; this answers the question
 * that actually decides a match, which is "does my list open *against theirs*".
 * Sitting them one above the other is most of the answer — you can see whether
 * your interruption is the one their board cares about before a single card is
 * played.
 *
 * The opponent's deck is loaded from a file and kept entirely separate from the
 * one being built. Nothing here can edit either list.
 *
 * This is the first useful piece of a shootout rather than the whole thing: the
 * run structure the original had — a set number of trials, siding for both sides
 * between games, a report at the end — is recorded in the loop journal and not
 * built. Half a run structure would be worse than none, and two hands side by
 * side is worth having on its own.
 */
@Composable
fun ShootoutPanel(state: DeckBuilderState) {
    MasterToolSheet(onDismiss = { state.shootoutVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shootout", style = MaterialTheme.typography.headlineSmall)

                Box(Modifier.weight(1f))

                FilterChip(
                    selected = state.youGoFirst,
                    onClick = { state.dealShootout(goingFirst = true) },
                    label = { Text("You go 1st") },
                )
                Box(Modifier.width(8.dp))
                FilterChip(
                    selected = !state.youGoFirst,
                    onClick = { state.dealShootout(goingFirst = false) },
                    label = { Text("You go 2nd") },
                )
            }

            if (state.opponentDeck.main.isEmpty()) {
                Text(
                    "Load the deck you are testing against. A .ydk or .ydkx file — " +
                        "nothing here can change either list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { state.loadOpponent() }) { Text("Load opponent…") }
                return@Column
            }

            Side(
                state = state,
                title = state.deckName.ifBlank { "Your deck" },
                hand = state.yourOpening,
            )
            Side(
                state = state,
                title = state.opponentName.ifBlank { "Opponent" },
                hand = state.theirOpening,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { state.dealShootout(state.youGoFirst) }) { Text("Deal again") }
                TextButton(onClick = { state.loadOpponent() }) { Text("Change opponent…") }

                Box(Modifier.weight(1f))

                Text(
                    "${state.opponentDeck.main.size} MAIN · ${state.opponentDeck.side.size} SIDE",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One player's name and opening, which is the same shape on both sides. */
@Composable
private fun Side(state: DeckBuilderState, title: String, hand: OpeningHand?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Box(Modifier.width(10.dp))
            Text(
                if (hand == null) "" else if (hand.goingFirst) "GOING 1ST" else "GOING 2ND",
                style = tacticalStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hand == null || hand.size == 0) {
            Text(
                "Nothing to shuffle — that Main deck is empty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hand.cards.forEach { id ->
                val card = state.index.byId(id)
                Box(Modifier.width(88.dp)) {
                    if (card == null) {
                        Text(id.value.toString(), style = tacticalStyle())
                    } else {
                        HoverPreview(card) {
                            // Inspects. Nothing in this panel edits a deck —
                            // least of all the opponent's.
                            CardTile(
                                card = card,
                                format = state.format,
                                onClick = { state.inspect(listOf(card), 0) },
                            )
                        }
                    }
                }
            }
        }
    }
}
