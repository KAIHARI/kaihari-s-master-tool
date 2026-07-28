package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.deck.DeckEditor
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * Everything about one card, and every way to change how many of it you run.
 *
 * Replaces the desktop tool's hover preview, which has no touch equivalent. A
 * stepper per section rather than add and remove buttons: it says what the deck
 * holds and changes it in one move, where a pair of buttons could only nudge it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDetailBody(
    card: Card,
    format: Format,
    copiesBySection: Map<DeckSection, Int>,
    mainDeckSize: Int,
    openingHandOdds: (copies: Int, handSize: Int) -> Double,
    onSetCount: (DeckSection, Int) -> Unit,
    onMove: (from: DeckSection, to: DeckSection) -> Unit,
    onBrowse: (CardFilter) -> Unit,
) {
    val home = card.requiredSection()
    val copiesHome = copiesBySection[home] ?: 0
    val copiesSide = copiesBySection[DeckSection.SIDE] ?: 0
    val totalCopies = copiesHome + copiesSide
    val limit = DeckEditor.copyLimit(card, format)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AsyncImage(
                model = card.imageUrl ?: card.imageUrlSmall,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(200.dp)
                    .height(292.dp)
                    .clip(RoundedCornerShape(CARD_CORNER))
                    .background(MasterToolPalette.SurfaceRaised),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(card.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    card.type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.category == CardCategory.MONSTER) {
                        card.level?.let { Fact("Level", it.toString()) }
                        card.linkValue?.let { Fact("Link", it.toString()) }
                        Fact("ATK", card.atk?.toString() ?: "—")
                        if (card.linkValue == null) Fact("DEF", card.def?.toString() ?: "—")
                    }
                }

                // Tapping a facet browses the pool by it. These used to be chips
                // with an empty onClick, which read as interactive and were not.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.race?.let { race ->
                        AssistChip(
                            onClick = { onBrowse(CardFilter(races = setOf(race))) },
                            label = { Text(race) },
                        )
                    }
                    AssistChip(
                        onClick = { onBrowse(CardFilter(attributes = setOf(card.attribute))) },
                        label = { Text(card.attribute.name) },
                    )
                    card.archetype?.let { archetype ->
                        AssistChip(
                            onClick = { onBrowse(CardFilter(archetypes = setOf(archetype))) },
                            label = { Text(archetype) },
                        )
                    }
                }

                Text(
                    "${format.name}: ${card.banStatus(format).name.lowercase()
                        .replace('_', ' ')}  •  $totalCopies of $limit in deck",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // The reason a card is worth a third copy, stated rather than
                // left to be felt.
                if (copiesHome > 0 && home == DeckSection.MAIN && mainDeckSize > 0) {
                    Text(
                        "Opening hand: ${percent(openingHandOdds(copiesHome, 5))} going first, " +
                            "${percent(openingHandOdds(copiesHome, 6))} going second " +
                            "(in $mainDeckSize cards)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MasterToolPalette.Accent,
                    )
                }
            }
        }

        Text("Copies", style = MaterialTheme.typography.labelLarge)

        CopiesStepper(
            label = "${home.displayName} Deck",
            count = copiesHome,
            max = limit,
            accent = home.accent(),
            onChange = { onSetCount(home, it) },
        )

        CopiesStepper(
            label = "Side Deck",
            count = copiesSide,
            max = limit,
            accent = MasterToolPalette.SideAccent,
            onChange = { onSetCount(DeckSection.SIDE, it) },
        )

        if (copiesHome > 0 || copiesSide > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (copiesHome > 0) {
                    OutlinedButton(onClick = { onMove(home, DeckSection.SIDE) }) {
                        Text("Move one to Side")
                    }
                }
                if (copiesSide > 0) {
                    OutlinedButton(onClick = { onMove(DeckSection.SIDE, home) }) {
                        Text("Move one to ${home.displayName}")
                    }
                }
            }
        }

        Text(card.description, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun DeckSection.accent() = when (this) {
    DeckSection.MAIN -> MasterToolPalette.MainAccent
    DeckSection.EXTRA -> MasterToolPalette.ExtraAccent
    DeckSection.SIDE -> MasterToolPalette.SideAccent
}

@Composable
private fun Fact(label: String, value: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("$label $value", style = MaterialTheme.typography.labelMedium)
    }
}
