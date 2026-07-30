package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.prefs.ThemeMode
import com.kaiharimoto.mastertool.core.prefs.UiPreferences
import com.kaiharimoto.mastertool.ui.egg.ChibiLogo
import com.kaiharimoto.mastertool.ui.theme.ChromaticText
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.archivoExpandedFamily
import com.kaiharimoto.mastertool.ui.update.UpdateState

@Composable
fun DeckBuilderTopBar(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
) {
    val validation = state.validation
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChibiLogo(onWake = { state.eggVisible = true })

        // The wordmark carries the identity: expanded cut, chromatic fringe —
        // white light through a lens, not a coloured logo.
        ChromaticText(
            "KAI'S MASTER TOOL",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = archivoExpandedFamily(),
                letterSpacing = 1.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = state.deckName,
            onValueChange = state::rename,
            singleLine = true,
            label = { Text("Deck") },
            modifier = Modifier
                .width(240.dp)
                .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
        )

        Text(
            "${state.deck.main.size} main · ${state.deck.extra.size} extra · " +
                "${state.deck.side.size} side",
            style = MaterialTheme.typography.labelMedium,
        )

        // The legality readout is the way in to the issue list: knowing there are
        // three problems is only useful if you can find out what they are.
        AssistChip(
            onClick = { state.issuesVisible = true },
            label = {
                Text(
                    if (validation.isLegal) {
                        if (validation.warnings.isEmpty()) {
                            "Legal"
                        } else {
                            "${validation.warnings.size} note(s)"
                        }
                    } else {
                        "${validation.errors.size} issue(s)"
                    },
                    color = when {
                        !validation.isLegal -> MasterToolPalette.Danger
                        validation.warnings.isNotEmpty() -> MasterToolPalette.Warning
                        else -> MasterToolPalette.SideAccent
                    },
                )
            },
        )

        Box(Modifier.weight(1f))

        // Switching format re-badges the whole pool and re-validates the deck,
        // because the banlist is read through it everywhere.
        Format.entries.forEach { entry ->
            FilterChip(
                selected = state.format == entry,
                onClick = {
                    state.onFormatChange(entry)
                    layout.update { it.copy(format = entry) }
                },
                label = { Text(entry.name) },
            )
        }

        if (state.isSyncing) {
            CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            state.syncMessage?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }

        IconButton(onClick = state::undo, enabled = state.canUndo) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = state::redo, enabled = state.canRedo) {
            Icon(Icons.Filled.Redo, contentDescription = "Redo")
        }
        IconButton(onClick = { state.statsVisible = true }) {
            Icon(Icons.Filled.BarChart, contentDescription = "Deck statistics")
        }
        IconButton(onClick = { state.consistencyVisible = true }) {
            Icon(Icons.Filled.Percent, contentDescription = "Opening-hand odds")
        }
        IconButton(onClick = { state.save() }) {
            Icon(Icons.Filled.Save, contentDescription = "Save deck")
        }
        TextButton(onClick = onOpenLibrary) { Text("Library") }

        // Everything that is not part of building the list lives behind one
        // control, so the bar still fits when the window is not 1600dp wide.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("New deck") },
                    onClick = { menuOpen = false; state.newDeck() },
                )
                DropdownMenuItem(
                    text = { Text("Import…") },
                    onClick = { menuOpen = false; state.importFromFile() },
                )
                DropdownMenuItem(
                    text = { Text("Export…") },
                    onClick = { menuOpen = false; state.exportToFile() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { menuOpen = false; state.shareDeck() },
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = {
                        Text(
                            if (layout.preferences.stacked) {
                                "Show every copy"
                            } else {
                                "Stack duplicate cards"
                            }
                        )
                    },
                    onClick = {
                        menuOpen = false
                        layout.update { it.copy(stacked = !it.stacked) }
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            when (layout.preferences.themeMode) {
                                ThemeMode.SYSTEM -> "Theme: match system"
                                ThemeMode.DARK -> "Theme: dark"
                                ThemeMode.LIGHT -> "Theme: light"
                            }
                        )
                    },
                    onClick = {
                        // Cycles rather than opening a submenu: three values, and
                        // the whole screen previews the choice instantly.
                        layout.update {
                            it.copy(
                                themeMode = when (it.themeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                }
                            )
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reset layout") },
                    onClick = {
                        menuOpen = false
                        layout.update {
                            // Keep what is about the deck or the person — the
                            // format, the stacked view, the pinned easter-egg
                            // pool, the theme — and reset what is about the room.
                            UiPreferences.DEFAULT.copy(
                                format = it.format,
                                stacked = it.stacked,
                                easterEggPool = it.easterEggPool,
                                themeMode = it.themeMode,
                            )
                        }
                    },
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Refresh card database") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = { menuOpen = false; state.refreshCardPool(force = true) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (updateState.isChecking) {
                                "Checking…"
                            } else {
                                "v${updateState.currentVersionName}"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    onClick = { menuOpen = false; updateState.check(userInitiated = true) },
                )
            }
        }
    }
}
