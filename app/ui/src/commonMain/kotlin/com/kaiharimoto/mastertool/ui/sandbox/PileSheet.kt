package com.kaiharimoto.mastertool.ui.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * Looking through a stack, and taking something out of it.
 *
 * Half of what a modern turn is made of never passes through the opening hand:
 * the board is built out of the Extra deck, the combo runs through the
 * graveyard, and the first card played is usually one a searcher went and found.
 * A board that could only count those stacks could not show a turn.
 *
 * One sheet for all four, because they are the same question — *which one* — and
 * four differently-shaped answers would be three more things to learn. What
 * differs is only what you can do with the card once you have found it, and that
 * is two buttons at most.
 *
 * Tapping a card picks it up rather than opening a menu. The board already knows
 * what a picked-up card is; a monster fished out of the graveyard lands in a zone
 * by exactly the same tap or drag as one out of the hand, and the gesture still
 * says which way it faces.
 */
@Composable
fun PileSheet(state: SandboxState, pile: Pile, index: CardIndex) {
    val contents = state.contentsOf(pile)

    MasterToolSheet(onDismiss = { state.openPile = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pile.label, style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.width(12.dp))
                Text(
                    when (pile) {
                        // Saying "top first" is cheaper than making somebody
                        // work it out, and getting it backwards is the kind of
                        // mistake that is only noticed several turns later.
                        Pile.DECK -> "${contents.size} CARDS · TOP FIRST"
                        else -> "${contents.size} CARDS"
                    },
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(Modifier.weight(1f))

                TextButton(onClick = { state.openPile = null }) { Text("Close") }
            }

            if (contents.isEmpty()) {
                Text(
                    when (pile) {
                        Pile.DECK -> "Nothing left to draw."
                        Pile.EXTRA -> "No Extra deck in this list."
                        Pile.GRAVEYARD -> "Nothing here yet."
                        Pile.BANISHED -> "Nothing banished."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp),
            ) {
                itemsIndexed(contents, key = { position, id -> "$position-${id.value}" }) { position, id ->
                    val card = index.byId(id)
                    Box(Modifier.aspectRatio(CARD_ASPECT)) {
                        if (card == null) {
                            Text(id.value.toString(), style = tacticalStyle())
                        } else {
                            HoverPreview(card) {
                                CardTile(
                                    card = card,
                                    modifier = Modifier.fillMaxWidth(),
                                    foil = false,
                                    onClick = {
                                        // The deck has nowhere to be summoned
                                        // from, so finding a card there means
                                        // taking it; everywhere else, picking it
                                        // up is the more useful of the two and
                                        // the other is one button away.
                                        if (pile == Pile.DECK) {
                                            state.takeToHand(pile, position)
                                        } else {
                                            state.holdFromPile(pile, position)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (pile != Pile.DECK && pile != Pile.EXTRA) {
                Text(
                    "Tap a card to pick it up, then tap a zone to put it there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val CARD_ASPECT = 0.686f
