package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.core.search.CardIndex

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
                Chip(
                    label = "Extra Deck",
                    selected = filter.extraDeckOnly == true,
                    onToggle = {
                        onChange(
                            filter.copy(extraDeckOnly = if (filter.extraDeckOnly == true) null else true)
                        )
                    },
                )
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
