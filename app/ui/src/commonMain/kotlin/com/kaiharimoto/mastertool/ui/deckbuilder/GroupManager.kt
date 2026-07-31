package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.ui.input.SheetShortcuts
import com.kaiharimoto.mastertool.ui.input.ShortcutRelay
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * Housekeeping for groups that already exist: rename, recolour, reorder, delete.
 *
 * Making a group happens at the deck, not here — name it and tap the cards it
 * holds, with the deck in front of you. This sheet is for the things that are
 * about the list of groups rather than about any card, which is why "New group"
 * closes it and hands you back to the deck.
 *
 * Colours are the six prismatic hues and nothing else — a fixed swatch row,
 * not a picker. A bounded palette is what keeps the breakdown looking like the
 * app rather than like a whiteboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagerSheet(
    state: DeckBuilderState,
    onDismiss: () -> Unit,
    shortcuts: ShortcutRelay? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetShortcuts(shortcuts) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Groups", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = {
                        onDismiss()
                        state.startGroupDraft()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  New group")
                    }
                }

                if (state.groups.groups.isEmpty()) {
                    Text(
                        "Split the deck by what each card is for — engine, handtraps, " +
                            "starters, extenders. Assignment is yours alone: name a group, " +
                            "then tap the cards in the Main deck that belong to it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val ordered = state.groups.ordered()
                ordered.forEachIndexed { index, group ->
                    GroupRow(
                        group = group,
                        count = state.deck.main.count { state.groups.assignments[it] == group.id },
                        canMoveUp = index > 0,
                        canMoveDown = index < ordered.lastIndex,
                        onRename = { name ->
                            state.updateGroups { it.upsert(group.copy(name = name)) }
                        },
                        onRecolor = { color ->
                            state.updateGroups { it.upsert(group.copy(color = color)) }
                        },
                        onMove = { delta ->
                            state.updateGroups { it.reorder(group.id, index + delta) }
                        },
                        onDelete = { state.updateGroups { it.remove(group.id) } },
                        onTextFocus = state::onTextFieldFocusChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: DeckGroup,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: (String) -> Unit,
    onRecolor: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onTextFocus: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = group.name,
            onValueChange = onRename,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onTextFocus(it.isFocused) },
        )

        Text(
            "$count in main",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MasterToolPalette.Prism.forEachIndexed { index, color ->
            Swatch(
                color = color,
                selected = index == group.color,
                onClick = { onRecolor(index) },
            )
        }

        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${group.name} up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${group.name} down")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${group.name}",
                tint = MasterToolPalette.Danger,
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 26.dp else 20.dp)
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
