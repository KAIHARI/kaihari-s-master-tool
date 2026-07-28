package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.ui.components.percent
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * What the decklist is made of, and how often it will do what you built it to do.
 *
 * The opening-hand table is the part that earns its place: consistency is the
 * number a tournament deck is actually tuned against, and it is the number that
 * moves when a 40-card deck becomes a 41-card deck.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckStatsPanel(
    statistics: DeckStatistics,
    section: DeckSection,
    onSectionChange: (DeckSection) -> Unit,
    /** Browses the pool by a facet the breakdown names. */
    onBrowse: (CardFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    MasterToolSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Deck statistics", style = MaterialTheme.typography.headlineSmall)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckSection.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == section,
                        onClick = { onSectionChange(entry) },
                        label = { Text("${entry.displayName} Deck") },
                    )
                }
            }

            if (statistics.sectionSize == 0) {
                Text(
                    "Nothing in the ${section.displayName} Deck yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card-type colours, not section colours: these count what the
                // cards are, and the two happened to look the same while meaning
                // different things.
                Tile("Cards", statistics.sectionSize.toString(), MaterialTheme.colorScheme.primary)
                Tile("Monsters", statistics.monsters.toString(), MasterToolPalette.Monster)
                Tile("Spells", statistics.spells.toString(), MasterToolPalette.Spell)
                Tile("Traps", statistics.traps.toString(), MasterToolPalette.Trap)
            }

            if (statistics.total > 0) {
                DistributionBar(statistics)
            }

            if (statistics.unknownCards > 0) {
                Text(
                    "${statistics.unknownCards} card(s) are not in the card database. " +
                        "They still count towards the deck size and the odds below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MasterToolPalette.Warning,
                )
            }

            if (section == DeckSection.MAIN) {
                OpeningHandOdds(statistics)
            }

            if (statistics.byLevel.isNotEmpty()) {
                Section("Level / Rank") {
                    LevelHistogram(statistics.byLevel)
                    Text(
                        "Link monsters have no level and are not counted here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (statistics.byAttribute.isNotEmpty()) {
                Section("Attribute") {
                    CountChips(statistics.byAttribute.map { it.key.name to it.value }) { name ->
                        Attribute.entries.firstOrNull { it.name == name }
                            ?.let { onBrowse(CardFilter(attributes = setOf(it))) }
                    }
                }
            }

            if (statistics.byRace.isNotEmpty()) {
                Section("Monster type") {
                    CountChips(statistics.byRace.toList()) { onBrowse(CardFilter(races = setOf(it))) }
                }
            }

            if (statistics.byArchetype.isNotEmpty()) {
                Section("Archetype") {
                    CountChips(statistics.byArchetype.toList()) { onBrowse(CardFilter(archetypes = setOf(it))) }
                }
            }
        }
    }
}

/**
 * Odds of seeing a card in your opening hand, by how many copies you run.
 *
 * Shown for the deck as it stands rather than for a nominal 40, because the
 * whole point is to see what a 41st card costs.
 */
@Composable
private fun OpeningHandOdds(statistics: DeckStatistics) {
    Section("Opening hand — ${statistics.sectionSize} cards") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Copies", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
            Text("Going 1st (5)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("Going 2nd (6)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        }

        (1..3).forEach { copies ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("$copies", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
                Text(
                    percent(statistics.openingHandOdds(copies, handSize = 5)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    percent(statistics.openingHandOdds(copies, handSize = 6)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalMasterToolColors.current.accentBright,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DistributionBar(statistics: DeckStatistics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // `weight` rejects zero, so a deck with no traps must skip the segment
        // rather than lay out a zero-width one.
        if (statistics.monsters > 0) {
            Box(Modifier.weight(statistics.monsters.toFloat()).fillMaxHeight().background(MasterToolPalette.Monster))
        }
        if (statistics.spells > 0) {
            Box(Modifier.weight(statistics.spells.toFloat()).fillMaxHeight().background(MasterToolPalette.Spell))
        }
        if (statistics.traps > 0) {
            Box(Modifier.weight(statistics.traps.toFloat()).fillMaxHeight().background(MasterToolPalette.Trap))
        }
    }
}

@Composable
private fun LevelHistogram(byLevel: Map<Int, Int>) {
    val peak = byLevel.values.maxOrNull() ?: return

    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        byLevel.forEach { (level, count) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((56 * count / peak).coerceAtLeast(3).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MasterToolPalette.Monster),
                )
                Text(level.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountChips(entries: List<Pair<String, Int>>, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.take(12).forEach { (label, count) ->
            // These looked interactive and were not: an AssistChip with an empty
            // onClick. Seeing that the deck runs eleven FIRE monsters is the
            // moment you want to look at the others, so that is what it does.
            AssistChip(onClick = { onPick(label) }, label = { Text("$label  $count") })
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun Tile(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
