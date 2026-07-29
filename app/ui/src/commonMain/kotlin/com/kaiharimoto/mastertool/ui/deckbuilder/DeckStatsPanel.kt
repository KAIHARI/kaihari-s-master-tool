package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.core.deck.Breaks
import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.deck.GroupOdds
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.ui.components.oneDecimal
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
    /** The gaps in the section being shown, which are the groups measured below. */
    breaks: Breaks,
    /** What each of those groups is about, read off its cards. Index-aligned. */
    groupNames: List<String?>,
    /** The two-card openings somebody wrote down, likeliest first. */
    pairs: List<PairOpening>,
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
                GroupOddsSection(breaks, statistics.sectionSize, groupNames)
                TwoCardOpenings(pairs)
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

/**
 * What each pile you pushed apart does to an opening hand.
 *
 * The best thing about it is that it asks for nothing. The groups are the gaps
 * already in the deck — nobody named a category, tagged a card or filled in a
 * form; an arrangement made to be *looked* at turns out to be an arrangement
 * that can be measured. "These nine are the engine and those six are the
 * handtraps" was always the claim, and this is what it comes to in hands.
 *
 * Only for the Main deck, which is the only section anything is drawn from.
 */
@Composable
private fun GroupOddsSection(breaks: Breaks, sectionSize: Int, names: List<String?>) {
    val groups = GroupOdds.forGroups(breaks, sectionSize)
    if (groups.size < 2) return

    val colors = LocalMasterToolColors.current

    Section("Your groups — $sectionSize cards, going first") {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header("Cards", Modifier.width(34.dp))
            Box(Modifier.weight(1f))
            Box(Modifier.weight(1.4f))
            Header("One", Modifier.width(48.dp), TextAlign.End)
            Header("Two", Modifier.width(48.dp), TextAlign.End)
            Header("Avg", Modifier.width(38.dp), TextAlign.End)
        }

        groups.forEachIndexed { position, group ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.size.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(34.dp),
                )
                // Blank when the group has nothing to say for itself, which is
                // honest -- eight unrelated cards are eight unrelated cards, and
                // a name invented for them would be the program claiming to have
                // understood something.
                Text(
                    names.getOrNull(position).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OddsBar(
                    one = group.one.toFloat(),
                    two = group.two.toFloat(),
                    fill = colors.accent,
                    modifier = Modifier.weight(1.4f),
                )
                Figure(percent(group.one), colors.accentBright, Modifier.width(48.dp))
                Figure(
                    percent(group.two),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.width(48.dp),
                )
                Figure(
                    oneDecimal(group.average),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.width(38.dp),
                )
            }
        }

        Text(
            "The bar is how often the group shows up at all; the tick is how often " +
                "twice. Nothing here was tagged or named — the gaps you pushed into " +
                "the deck said which cards belong together, and what each group is " +
                "called is read back off the cards in it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One group's odds, as a bar and a mark on it.
 *
 * Two quantities on one track, and the first attempt drew the second as a
 * brighter fill nested inside the first — which read backwards, because the eye
 * takes the brightest segment for the value and the dim remainder for an
 * extension of it. A tick is unambiguous: the bar is one number, and the mark
 * says where the other one falls along it.
 */
@Composable
private fun OddsBar(one: Float, two: Float, fill: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val mark = MaterialTheme.colorScheme.onSurface

    Canvas(modifier.height(14.dp)) {
        val bar = 8.dp.toPx()
        val top = (size.height - bar) / 2f
        val radius = CornerRadius(2.dp.toPx())

        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, bar), cornerRadius = radius)
        if (one > 0f) {
            drawRoundRect(
                fill,
                topLeft = Offset(0f, top),
                size = Size(size.width * one.coerceIn(0f, 1f), bar),
                cornerRadius = radius,
            )
        }

        val width = 1.5.dp.toPx()
        // Kept inside the track at both ends, so a group that is almost never
        // seen twice still shows a mark rather than half of one.
        val x = (size.width * two.coerceIn(0f, 1f)).coerceIn(width, size.width - width)
        drawRect(
            mark.copy(alpha = 0.85f),
            topLeft = Offset(x - width / 2f, top - 3.dp.toPx()),
            size = Size(width, bar + 6.dp.toPx()),
        )
    }
}

@Composable
private fun Header(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

/** A number in a column of numbers, so it lines up with the ones above it. */
@Composable
private fun Figure(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
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

/**
 * The openings somebody actually wrote down, and how often each turns up.
 *
 * Every other number on this sheet is about the deck as a pile of cards. This
 * one is about the deck as the thing its owner said it was — and nothing had to
 * be tagged to produce it, because the pair notes were written for their own
 * sake and turn out to be a list of what the deck is trying to do.
 *
 * Absent rather than empty when nobody has written any: a heading over nothing
 * is a feature advertising itself.
 */
@Composable
private fun TwoCardOpenings(pairs: List<PairOpening>) {
    if (pairs.isEmpty()) return

    Section("Two-card openings") {
        pairs.forEach { pair ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${pair.first}  +  ${pair.second}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pair.note.isNotBlank()) {
                        Text(
                            pair.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "${(pair.odds * 100).roundToInt()}%",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "How often an opening five holds both halves, out of the Main deck.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
