package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.prefs.CardBackStyle
import com.kaiharimoto.mastertool.core.prefs.ThemeMode
import com.kaiharimoto.mastertool.core.prefs.UiPreferences
import com.kaiharimoto.mastertool.ui.egg.ChibiLogo
import com.kaiharimoto.mastertool.ui.fx.defaultFeedbackEnabled
import com.kaiharimoto.mastertool.ui.theme.ChromaticText
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.wordmarkFamily
import com.kaiharimoto.mastertool.ui.update.UpdateState

@Composable
fun DeckBuilderTopBar(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
    onOpenTable: () -> Unit = {},
    onOpenPlay: () -> Unit = {},
) {
    val validation = state.validation
    var menuOpen by remember { mutableStateOf(false) }

    // ---- narrow windows ------------------------------------------------------
    //
    // This bar holds about 1400dp of content: the wordmark, the deck name, the
    // counts, the legality chip, two format chips, six icon buttons, Table,
    // Play, Library and the overflow menu. The tablet it was designed against
    // is 1480dp wide, so it fits there by about eighty pixels — and nowhere
    // else. On a 780dp phone everything from the format chips rightward is
    // simply gone: no Save, no Undo, no Table, no Play, no menu. The builder
    // renders perfectly and cannot be used, which is what kai reported.
    //
    // Same shape of fix as the play stage's bar. A `Row` cannot both hug the
    // right edge and scroll — a weighted child needs a finite width and a
    // scroll container hands it infinity — so the width picks an arrangement.
    // Wide enough and it is the bar that shipped, to the pixel. Narrower and
    // the spacer becomes a fixed gap and the row scrolls, so every control is
    // reachable at any size.
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
    ) {
    val roomy = maxWidth >= HEADER_FITS_AT
    val barScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (roomy) Modifier else Modifier.horizontalScroll(barScroll))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChibiLogo(onWake = { state.eggVisible = true })

        // The wordmark carries the identity: a neo-grotesque at Medium, set
        // lower case at its own tracking, wearing the chromatic fringe — white
        // light through a lens, not a coloured logo.
        ChromaticText(
            "kai's master tool",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = wordmarkFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = state.deckName,
            onValueChange = state::rename,
            singleLine = true,
            placeholder = { Text("Deck name", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .width(210.dp)
                .height(46.dp)
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

        // Pushes the right-hand group to the far edge when there is an edge to
        // push it to; under a scroll container there is not.
        if (roomy) Box(Modifier.weight(1f)) else Spacer(Modifier.width(16.dp))

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
        // The one control that changes what the window is for: with the pool
        // hidden the deck takes the whole width and re-fits into it.
        IconButton(onClick = layout::toggleSearchPane) {
            Icon(
                if (layout.preferences.searchVisible) Icons.Filled.SearchOff else Icons.Filled.Search,
                contentDescription = if (layout.preferences.searchVisible) {
                    "Hide the card database"
                } else {
                    "Show the card database"
                },
            )
        }
        IconButton(onClick = { state.statsVisible = true }) {
            Icon(Icons.Filled.BarChart, contentDescription = "Deck statistics")
        }
        IconButton(onClick = { state.openGoal() }) {
            Icon(Icons.Filled.Percent, contentDescription = "Opening-hand odds")
        }
        IconButton(onClick = { state.save() }) {
            Icon(Icons.Filled.Save, contentDescription = "Save deck")
        }
        TextButton(onClick = onOpenTable, enabled = state.deck.main.size >= 1) {
            Text("Table")
        }
        TextButton(onClick = onOpenPlay, enabled = state.deck.main.size >= 5) {
            Text("Play")
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
                            if (layout.preferences.fitAll) {
                                "✓ Fit the whole deck on screen"
                            } else {
                                "Fit the whole deck on screen"
                            }
                        )
                    },
                    onClick = {
                        menuOpen = false
                        layout.setFitAll(!layout.preferences.fitAll)
                    },
                )
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
                        val effective = layout.preferences.feedbackEnabled
                            ?: defaultFeedbackEnabled()
                        Text(if (effective) "Sound & haptics: on" else "Sound & haptics: off")
                    },
                    onClick = {
                        layout.update {
                            val effective = it.feedbackEnabled ?: defaultFeedbackEnabled()
                            it.copy(feedbackEnabled = !effective)
                        }
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
                    text = { Text("Card back: ${layout.preferences.cardBack.displayName}") },
                    onClick = {
                        // Cycles for the same reason the theme does: two values,
                        // and every face-down card on screen previews it at once.
                        layout.update {
                            it.copy(
                                cardBack = when (it.cardBack) {
                                    CardBackStyle.OVAL -> CardBackStyle.SPIRAL
                                    CardBackStyle.SPIRAL -> CardBackStyle.OVAL
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
                            // pool, the theme, which room the table is in — and
                            // reset what is about the layout. "The room" in the
                            // comment this replaces meant the panes; the play
                            // stage now has a literal one, and it is a taste
                            // rather than a layout, so it survives like the
                            // theme beside it.
                            UiPreferences.DEFAULT.copy(
                                format = it.format,
                                stacked = it.stacked,
                                easterEggPool = it.easterEggPool,
                                themeMode = it.themeMode,
                                feedbackEnabled = it.feedbackEnabled,
                                cardBack = it.cardBack,
                                cardBackUrl = it.cardBackUrl,
                                scene = it.scene,
                                deskLight = it.deskLight,
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
}

/**
 * The width the deck builder's header needs to show every control at once.
 *
 * About 1400dp of content, and the tablet it was designed against is 1480dp —
 * so it has always fitted by roughly eighty pixels and by nothing else. Even an
 * ordinary 1280dp tablet loses the end of it.
 *
 * Measured from the content rather than chosen, and rounded up: being wrong on
 * the roomy side costs a scroll nobody uses, being wrong on the narrow side
 * costs somebody the Save button.
 */
private val HEADER_FITS_AT = 1400.dp

