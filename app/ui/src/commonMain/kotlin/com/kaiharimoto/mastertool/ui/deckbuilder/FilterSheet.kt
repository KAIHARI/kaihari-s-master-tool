package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.core.search.CardIndex

/**
 * An open-ended stat range needs a top end to be expressed as an `IntRange`.
 *
 * Kept well above any printed ATK so "2500 and up" means what it says, and
 * recognised on the way back out so the field shows blank rather than 99999.
 */
private const val STAT_CEILING = 99_999

/** How many archetype chips to draw at once — there are several thousand. */
private const val ARCHETYPE_LIMIT = 40

/**
 * Quick filters.
 *
 * A bottom sheet rather than the desktop tool's always-visible side panel:
 * horizontal space on a tablet is better spent on cards, and filters are set in
 * bursts rather than continuously.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    index: CardIndex,
    filter: CardFilter,
    onChange: (CardFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClear) { Text("Clear all") }
            }

            FilterGroup("Card type") {
                CardCategory.entries.filter { it != CardCategory.OTHER }.forEach { category ->
                    Chip(
                        label = category.name.lowercase().replaceFirstChar(Char::uppercase),
                        selected = category in filter.categories,
                        onToggle = {
                            onChange(filter.copy(categories = filter.categories.toggle(category)))
                        },
                    )
                }
            }

            // Three states, not two: the old chip could say "Extra Deck only" or
            // nothing, and had no way to ask for main-deck cards.
            FilterGroup("Deck") {
                Chip("Any", filter.extraDeckOnly == null) {
                    onChange(filter.copy(extraDeckOnly = null))
                }
                Chip("Extra Deck only", filter.extraDeckOnly == true) {
                    onChange(filter.copy(extraDeckOnly = true))
                }
                Chip("Main Deck only", filter.extraDeckOnly == false) {
                    onChange(filter.copy(extraDeckOnly = false))
                }
            }

            FilterGroup("Attribute") {
                Attribute.entries.filter { it != Attribute.UNKNOWN }.forEach { attribute ->
                    Chip(
                        label = attribute.name,
                        selected = attribute in filter.attributes,
                        onToggle = {
                            onChange(filter.copy(attributes = filter.attributes.toggle(attribute)))
                        },
                    )
                }
            }

            FilterGroup("Level / Rank") {
                (1..12).forEach { level ->
                    Chip(
                        label = level.toString(),
                        selected = level in filter.levels,
                        onToggle = { onChange(filter.copy(levels = filter.levels.toggle(level))) },
                    )
                }
            }

            FilterGroup("Banlist") {
                BanStatus.entries.forEach { status ->
                    Chip(
                        label = status.name.lowercase().replace('_', '-')
                            .replaceFirstChar(Char::uppercase),
                        selected = status in filter.banStatuses,
                        onToggle = {
                            onChange(filter.copy(banStatuses = filter.banStatuses.toggle(status)))
                        },
                    )
                }
            }

            StatRange(
                label = "ATK",
                range = filter.atkRange,
                onChange = { onChange(filter.copy(atkRange = it)) },
            )

            StatRange(
                label = "DEF",
                range = filter.defRange,
                onChange = { onChange(filter.copy(defRange = it)) },
            )

            if (index.races.isNotEmpty()) {
                FilterGroup("Monster type") {
                    index.races.forEach { race ->
                        Chip(
                            label = race,
                            selected = race in filter.races,
                            onToggle = { onChange(filter.copy(races = filter.races.toggle(race))) },
                        )
                    }
                }
            }

            if (index.archetypes.isNotEmpty()) {
                ArchetypeGroup(
                    archetypes = index.archetypes,
                    selected = filter.archetypes,
                    onToggle = { archetype ->
                        onChange(filter.copy(archetypes = filter.archetypes.toggle(archetype)))
                    },
                )
            }
        }
    }
}

/**
 * Archetypes, searched rather than listed.
 *
 * There are a few thousand of them. Drawing them all as chips the way the other
 * facets do would build several thousand composables inside a scrolling column
 * every time the sheet opened, so the list is filtered by a query and capped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchetypeGroup(
    archetypes: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val shown = remember(query, archetypes, selected) {
        val needle = query.trim().lowercase()
        val matches = if (needle.isEmpty()) {
            archetypes
        } else {
            archetypes.filter { it.lowercase().contains(needle) }
        }
        // Selected archetypes stay visible even when the query no longer matches
        // them, so a filter can always be switched back off from here.
        (selected.sorted() + matches.filterNot { it in selected }).take(ARCHETYPE_LIMIT)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Archetype", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search archetypes") },
                modifier = Modifier.width(260.dp),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { archetype ->
                Chip(archetype, archetype in selected) { onToggle(archetype) }
            }
        }
    }
}

/**
 * A numeric range for ATK or DEF.
 *
 * Typed rather than dragged: a slider cannot hit 2500 reliably, and 2500 is the
 * kind of number a deck is actually built around.
 */
@Composable
private fun StatRange(label: String, range: IntRange?, onChange: (IntRange?) -> Unit) {
    // Re-derived when the range changes so "Clear all" empties the fields. The
    // round trip is exact — an absent bound reads back as blank, not as 0 or
    // 99999 — so typing does not fight the reset.
    var minText by remember(range) {
        mutableStateOf(range?.first?.takeIf { it != 0 }?.toString().orEmpty())
    }
    var maxText by remember(range) {
        mutableStateOf(range?.last?.takeIf { it != STAT_CEILING }?.toString().orEmpty())
    }

    fun push() {
        val low = minText.toIntOrNull()
        val high = maxText.toIntOrNull()
        onChange(if (low == null && high == null) null else (low ?: 0)..(high ?: STAT_CEILING))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it.filter(Char::isDigit); push() },
                singleLine = true,
                label = { Text("Min") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )

            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it.filter(Char::isDigit); push() },
                singleLine = true,
                label = { Text("Max") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )

            if (range != null) {
                TextButton(onClick = { onChange(null) }) { Text("Any") }
            }
        }

        if (range != null) {
            Text(
                "Cards with no $label are excluded while this is set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(selected = selected, onClick = onToggle, label = { Text(label) })
}

/** Set toggle helper: filters are read as "one more facet on" or "that one off". */
private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

/** One removable chip in the active-filter bar. */
data class FilterPill(val label: String, val without: CardFilter)

/**
 * The active filter, broken into individually removable pieces.
 *
 * Reading back what is currently narrowing the pool — and switching one facet
 * off without opening the sheet — is the thing the desktop tool's filter bar got
 * right and the only part of it worth copying wholesale.
 */
fun CardFilter.pills(): List<FilterPill> = buildList {
    categories.forEach {
        add(FilterPill(it.name.lowercase().replaceFirstChar(Char::uppercase), copy(categories = categories - it)))
    }
    when (extraDeckOnly) {
        true -> add(FilterPill("Extra Deck only", copy(extraDeckOnly = null)))
        false -> add(FilterPill("Main Deck only", copy(extraDeckOnly = null)))
        null -> Unit
    }
    attributes.forEach { add(FilterPill(it.name, copy(attributes = attributes - it))) }
    levels.sorted().forEach { add(FilterPill("Level $it", copy(levels = levels - it))) }
    banStatuses.forEach {
        add(FilterPill(it.name.lowercase().replace('_', '-').replaceFirstChar(Char::uppercase), copy(banStatuses = banStatuses - it)))
    }
    races.forEach { add(FilterPill(it, copy(races = races - it))) }
    archetypes.forEach { add(FilterPill(it, copy(archetypes = archetypes - it))) }
    atkRange?.let { add(FilterPill("ATK ${it.describe()}", copy(atkRange = null))) }
    defRange?.let { add(FilterPill("DEF ${it.describe()}", copy(defRange = null))) }
}

private fun IntRange.describe(): String = when {
    first == 0 -> "≤ $last"
    last == STAT_CEILING -> "≥ $first"
    else -> "$first–$last"
}
