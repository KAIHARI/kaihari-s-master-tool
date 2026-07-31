package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.GroupMarks
import com.kaiharimoto.mastertool.core.deck.GroupPresets
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * The strip under the main deck's header: what the deck is broken into, or the
 * group currently being drawn up.
 *
 * It sits inside the pane rather than over it because picking cards for a group
 * means looking at the deck while you do it — a modal sheet would cover the one
 * thing the gesture is about. Its height is fixed and declared to the fitter,
 * so opening it takes room from the cards rather than pushing them out of view.
 */
@Composable
fun BreakdownBar(state: DeckBuilderState, modifier: Modifier = Modifier) {
    val draft = state.groupDraft
    if (draft == null) {
        GroupLegend(state, modifier)
    } else {
        GroupDraftEditor(state, modifier)
    }
}

/**
 * The groups as they stand, each with what it holds.
 *
 * Tapping one reopens it with its cards already selected, which makes "add two
 * more handtraps to the handtrap group" the same gesture as making it was.
 */
@Composable
private fun GroupLegend(state: DeckBuilderState, modifier: Modifier = Modifier) {
    val main = state.deck.main
    val groups = state.groups.ordered()
    val ungrouped = main.count { state.groups.groupOf(it) == null }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(
            onClick = { state.startGroupDraft() },
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.height(30.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  New group", style = MaterialTheme.typography.labelMedium)
        }

        val marks = GroupMarks.marks(groups)
        groups.forEach { group ->
            GroupChip(
                group = group,
                mark = marks[group.id].orEmpty(),
                count = state.groups.countIn(main, group.id),
                onClick = { state.editGroup(group) },
            )
        }

        if (groups.isEmpty()) {
            Text(
                "Name a role, then tap the cards that play it.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "$ungrouped ungrouped",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupChip(group: DeckGroup, mark: String, count: Int, onClick: () -> Unit) {
    val color = MasterToolPalette.Prism[group.color % MasterToolPalette.Prism.size]

    AssistChip(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        // The same mark the piece wears, so finding a group on the deck is
        // reading two letters rather than matching two hues.
        leadingIcon = { MarkChip(mark, color) },
        label = {
            Text(
                "${group.name}  ·  $count",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
    )
}

/**
 * A group being drawn up: give it a name, then tap the cards.
 *
 * The presets are the eight words decklists are actually argued about in, and
 * picking one fills in the name and a colour in a single tap — the fast path,
 * with the text field there for anything the eight do not cover.
 */
@Composable
private fun GroupDraftEditor(state: DeckBuilderState, modifier: Modifier = Modifier) {
    val draft = state.groupDraft ?: return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = state::setDraftName,
                singleLine = true,
                placeholder = { Text("Group name", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .width(180.dp)
                    .height(46.dp)
                    .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            )

            MarkChip(
                mark = GroupMarks.mark(draft.name),
                color = MasterToolPalette.Prism[draft.color % MasterToolPalette.Prism.size],
            )

            MasterToolPalette.Prism.forEachIndexed { index, color ->
                Swatch(
                    color = color,
                    selected = index == draft.color,
                    onClick = { state.setDraftColor(index) },
                )
            }

            Box(Modifier.width(8.dp))

            Text(
                // The count is the feedback for the tapping: nothing else on
                // screen says how many cards are in the group being made.
                "${draft.selection.size} selected · tap cards in the Main deck",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (!draft.isNew) {
                TextButton(
                    onClick = { state.deleteDraftGroup() },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MasterToolPalette.Danger,
                    )
                }
            }
            TextButton(
                onClick = { state.cancelGroupDraft() },
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { state.saveGroupDraft() },
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupPresets.all.forEach { preset ->
                val color = MasterToolPalette.Prism[preset.color % MasterToolPalette.Prism.size]
                SuggestionChip(
                    onClick = { state.applyPreset(preset) },
                    modifier = Modifier.height(30.dp),
                    label = {
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (draft.name.equals(preset.name, ignoreCase = true)) {
                                color
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                )
            }
        }
    }
}

/** A group's two-letter mark, drawn exactly as it appears on the deck. */
@Composable
private fun MarkChip(mark: String, color: Color) {
    Box(
        Modifier
            .size(width = 20.dp, height = 15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mark,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
            ),
            color = MasterToolPalette.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 22.dp else 16.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}
