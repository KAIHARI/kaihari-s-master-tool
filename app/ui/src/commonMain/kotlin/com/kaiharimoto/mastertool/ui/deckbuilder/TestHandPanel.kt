package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.core.deck.HandSimulator
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.percent
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * Shuffle up and look.
 *
 * Deck building is mostly a question about ratios, and this is the oldest way of
 * answering it. The statistics panel says what the numbers are; this says what
 * they feel like, which is the thing that actually changes a list — nobody moves
 * a card because a percentage told them to, they move it because they kept
 * seeing the same dead hand.
 *
 * So the only judgement it asks for is the one a player is already making:
 * playable, or a brick. It keeps the tally and reports the rate, and that is all
 * it claims to know. Anything more would be pretending to simulate a game.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHandPanel(state: DeckBuilderState, onPlayItOut: (List<CardId>) -> Unit = {}) {
    val hand = state.testHand

    MasterToolSheet(onDismiss = { state.testHandVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Test hand", style = MaterialTheme.typography.headlineSmall)

                Box(Modifier.weight(1f))

                FilterChip(
                    selected = state.testHandGoingFirst,
                    onClick = { state.dealTestHand(goingFirst = true) },
                    label = { Text("Going 1st") },
                )
                Box(Modifier.width(8.dp))
                FilterChip(
                    selected = !state.testHandGoingFirst,
                    onClick = { state.dealTestHand(goingFirst = false) },
                    label = { Text("Going 2nd") },
                )
            }

            if (hand == null || hand.size == 0) {
                Text(
                    "Nothing to shuffle yet — the Main deck is empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hand.cards.forEach { id ->
                    val card = state.index.byId(id)
                    Box(Modifier.width(96.dp)) {
                        if (card == null) {
                            Text(id.value.toString(), style = tacticalStyle())
                        } else {
                            HoverPreview(card) {
                                CardTile(
                                    card = card,
                                    format = state.format,
                                    // Tapping a card here inspects it. Removing a
                                    // copy from the deck by touching a test hand
                                    // would be a trap, not a shortcut.
                                    onClick = { state.inspect(listOf(card), 0) },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { state.judgeTestHand(playable = true) }) { Text("Playable") }
                OutlinedButton(onClick = { state.judgeTestHand(playable = false) }) { Text("Brick") }
                TextButton(onClick = { state.dealTestHand(state.testHandGoingFirst) }) {
                    Text("Reshuffle")
                }
                TextButton(onClick = { state.drawOneMore() }) { Text("Draw one") }
                hand?.let { opening ->
                    // The point at which the question changes. "Would I keep
                    // this" is answered by looking; "what does it actually do"
                    // is only answered by playing it, and that used to mean
                    // shuffling until the same five came up again.
                    TextButton(
                        onClick = {
                            state.testHandVisible = false
                            onPlayItOut(opening.cards)
                        },
                    ) { Text("Play it out") }
                }

                Box(Modifier.weight(1f))

                val tally = state.handTally
                val rate = tally.brickRate
                Text(
                    if (rate == null) {
                        "no hands judged"
                    } else {
                        "${tally.bricks} bricks in ${tally.total}  ·  ${percent(rate)}"
                    },
                    style = tacticalStyle(),
                    color = when {
                        rate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        // A rule of thumb, not a rule. Worth colouring because
                        // the number is the point of the whole panel.
                        rate >= 0.25 -> MasterToolPalette.Danger
                        rate >= 0.15 -> MasterToolPalette.Warning
                        else -> MasterToolPalette.Success
                    },
                )
                TextButton(onClick = { state.resetHandTally() }) { Text("Reset") }
            }
        }
    }
}
