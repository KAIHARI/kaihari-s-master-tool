package com.kaiharimoto.mastertool.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.input.LocalKey
import com.kaiharimoto.mastertool.core.input.Shortcut
import com.kaiharimoto.mastertool.core.input.ShortcutScope
import com.kaiharimoto.mastertool.core.input.ShortcutTable

/**
 * The keyboard shortcuts, read straight off the table that implements them.
 *
 * Nothing is written out by hand here, so this cannot describe a binding that
 * does not exist or miss one that does — which is the failure mode of every
 * hand-maintained shortcut list, including the one this replaces.
 *
 * The last group is the exception that proves it: two keys the search box
 * handles itself, which the table cannot resolve because a global binding for
 * Tab or Enter would fire everywhere. They still live in `:core` beside the
 * table rather than as a comment next to the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutHelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Keyboard", style = MaterialTheme.typography.headlineSmall)

            Group("Anywhere", ShortcutTable.all.filter { it.scope == ShortcutScope.ANYWHERE })
            Group("Building a deck", ShortcutTable.all.filter { it.scope == ShortcutScope.BUILDER })
            Group("Inspecting a card", ShortcutTable.all.filter { it.scope == ShortcutScope.INSPECTOR })

            // Not in the table above, because the table resolves keys globally
            // and these two belong to one field. Listed all the same: this sheet
            // claims to be the whole keyboard story.
            LocalGroup("In the search box", ShortcutTable.inTheSearchBox)

            Text(
                "While the cursor is in a text field, only Esc, Ctrl+S and Ctrl+F stay live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalGroup(title: String, keys: List<LocalKey>) {
    if (keys.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        keys.forEach { key -> KeyRow(key.label, key.description) }
    }
}

@Composable
private fun Group(title: String, shortcuts: List<Shortcut>) {
    // A binding with nothing to say about itself is one of a set — the three
    // siblings of each arrow row — and printing four rows that each say a
    // quarter of the same idea is worse than printing one that says all of it.
    val listed = shortcuts.filter { it.inHelp }
    if (listed.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        listed.forEach { shortcut ->
            KeyRow(shortcut.helpLabel ?: shortcut.chord.label, shortcut.description)
        }
    }
}

/** One line of the sheet: what to press, and what it does. */
@Composable
private fun KeyRow(label: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
