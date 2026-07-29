package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.SaveStatus
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.prefs.ThemeChoice
import com.kaiharimoto.mastertool.core.prefs.UiPreferences
import com.kaiharimoto.mastertool.ui.egg.ChibiLogo
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.displayFamily
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import com.kaiharimoto.mastertool.ui.update.UpdateState

@Composable
fun DeckBuilderTopBar(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
    onOpenSandbox: () -> Unit,
) {
    val validation = state.validation
    var menuOpen by remember { mutableStateOf(false) }
    var keeping by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChibiLogo(onWake = { state.eggVisible = true })

        // The one place the display face is used. It is angular enough that a
        // second use would start shouting.
        Text(
            "kai's master tool",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = displayFamily()),
            color = MaterialTheme.colorScheme.primary,
        )

        DeckName(state)

        Text(
            "${state.deck.main.size} main · ${state.deck.extra.size} extra · " +
                "${state.deck.side.size} side",
            // Three numbers that change while you watch. In a proportional face
            // the whole row shifts when one of them reaches double digits.
            style = tacticalStyle(),
        )

        // The legality readout is the way in to the issue list: knowing there are
        // three problems is only useful if you can find out what they are.
        //
        // Still drawn while there is no verdict, greyed and inert rather than
        // absent: a control that appears a second after the bar does is a bar
        // that rearranges itself under a thumb already on the way to it.
        AssistChip(
            onClick = { state.issuesVisible = true },
            enabled = validation != null,
            label = {
                Text(
                    when {
                        validation == null -> "Checking…"
                        validation.isLegal && validation.warnings.isEmpty() -> "Legal"
                        validation.isLegal -> "${validation.warnings.size} note(s)"
                        else -> "${validation.errors.size} issue(s)"
                    },
                    color = when {
                        validation == null -> MaterialTheme.colorScheme.onSurfaceVariant
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
        SaveControl(state)
        TextButton(onClick = onOpenLibrary) { Text("Library") }

        // Everything that is not part of building the list lives behind one
        // control, so the bar still fits when the window is not 1600dp wide.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Sandbox…")
                            Text(
                                "lay the opening out on a board",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { menuOpen = false; onOpenSandbox() },
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("New deck") },
                    onClick = { menuOpen = false; state.newDeck() },
                )
                DropdownMenuItem(
                    text = { Text("Used to be…") },
                    onClick = { menuOpen = false; state.showHistory() },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Keep this one…")
                            Text(
                                "so the list you registered survives being edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { menuOpen = false; keeping = true },
                )
                DropdownMenuItem(
                    text = { Text("Import…") },
                    onClick = { menuOpen = false; state.importFromFile() },
                )
                DropdownMenuItem(
                    text = { Text("Paste a list…") },
                    onClick = { menuOpen = false; state.pasteVisible = true },
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
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Print the decklist…")
                            Text(
                                "counted and grouped by type, for a judge",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { menuOpen = false; state.exportDecklistSheet() },
                )

                HorizontalDivider()

                // Reachable without a keyboard, which is the only way it exists
                // at all on the tablet this app is landscape-locked for.
                DropdownMenuItem(
                    text = {
                        Text(if (state.deckNotes.isBlank()) "Notes…" else "Notes… (written)")
                    },
                    onClick = { menuOpen = false; state.notesVisible = true },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Shootout…")
                            Text(
                                if (state.opponentDeck.main.isEmpty()) {
                                    "both openings, against a deck you load"
                                } else {
                                    "against ${state.opponentName}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        menuOpen = false
                        if (state.opponentDeck.main.isNotEmpty()) state.dealShootout(state.youGoFirst)
                        state.shootoutVisible = true
                    },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("The mat…")
                            Text(
                                "what this deck is laid out on",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { menuOpen = false; state.matVisible = true },
                )
                DropdownMenuItem(
                    text = { Text("See the whole deck") },
                    onClick = { menuOpen = false; state.showcaseVisible = true },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                if (layout.preferences.stacked) {
                                    "Show every copy"
                                } else {
                                    "Stack duplicate cards"
                                }
                            )
                            // A stack has no position, so there is nothing
                            // coherent for dragging one to mean and the panes
                            // stop accepting drags while stacked. That was true
                            // and invisible: cards simply stopped moving, which
                            // reads as the program having broken.
                            Text(
                                if (layout.preferences.stacked) {
                                    "arranging comes back"
                                } else {
                                    "counts instead of cards; arranging turns off"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        menuOpen = false
                        layout.update { it.copy(stacked = !it.stacked) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Test hand…") },
                    onClick = {
                        menuOpen = false
                        state.dealTestHand(state.testHandGoingFirst)
                        state.testHandVisible = true
                    },
                )

                // Always available. A deck with no plans is exactly the deck
                // you are about to record the first one for.
                DropdownMenuItem(
                    text = {
                        Text(
                            when {
                                state.sidingPatterns.isNotEmpty() ->
                                    "Siding… (${state.sidingPatterns.size})"
                                !state.pendingSwap.isEmpty -> "Siding… (unsaved swap)"
                                else -> "Siding…"
                            },
                        )
                    },
                    onClick = { menuOpen = false; state.sidingVisible = true },
                )
                DropdownMenuItem(
                    text = { Text("Reset layout") },
                    onClick = {
                        menuOpen = false
                        layout.update {
                            // Keep what is about the deck, reset what is about the room.
                            // The surface is a choice about the room and survives
                            // anyway, because resetting it is not what this asks.
                            UiPreferences.DEFAULT.copy(
                                format = it.format,
                                stacked = it.stacked,
                                theme = it.theme,
                            )
                        }
                    },
                )

                HorizontalDivider()

                // Listed rather than cycled. Four is too many to arrive at by
                // tapping, and the description is the point -- "Leather and gold"
                // says what you are about to get where "Classic" does not.
                ThemeChoice.entries.forEach { choice ->
                    val current = layout.preferences.theme == choice
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (current) "${choice.displayName} ✓" else choice.displayName,
                                color = if (current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        trailingIcon = {
                            Text(
                                choice.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            layout.setTheme(choice)
                        },
                    )
                }

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

    if (keeping) KeepThisOneDialog(
        onDismiss = { keeping = false },
        onConfirm = { keeping = false; state.keepThisOne(it) },
    )
}

/**
 * What to call the version being kept.
 *
 * A name is optional and worth asking for anyway: an unnamed version is exactly
 * what the automatic rule already produces, so somebody reaching for this menu
 * item has a reason in mind, and the reason is usually the name.
 */
@Composable
private fun KeepThisOneDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep this one") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The deck as it stands is kept, and editing from here cannot " +
                        "take it away. Named versions are never thinned out.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("What to call it") },
                    placeholder = { Text("Regionals, Manchester") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifEmpty { null }) }) { Text("Keep it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The deck's name, as a title rather than as a form field.
 *
 * A labelled text box says "fill this in". The name of the thing you are
 * looking at is a heading, and this is the top of the only screen it appears
 * on — so it reads as one until you touch it, which is the only moment it needs
 * to be editable.
 *
 * The field is still a real text field while it is open, so it keeps reporting
 * focus. That is what suppresses single-key shortcuts: without it, typing
 * "side" into a deck name would open the statistics panel and the deck check on
 * the way past.
 */
@Composable
private fun DeckName(state: DeckBuilderState) {
    var editing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (!editing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { editing = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                state.deckName.ifBlank { "Untitled Deck" },
                style = MaterialTheme.typography.titleLarge,
                color = if (state.deckName.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            // Faint, because the whole row is the target and this only has to
            // say that it is one.
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Rename deck",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
            )
        }
        return
    }

    OutlinedTextField(
        value = state.deckName,
        onValueChange = state::rename,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { editing = false }),
        modifier = Modifier
            .width(260.dp)
            .focusRequester(focus)
            .onFocusChanged { focused ->
                state.onTextFieldFocusChanged(focused.isFocused)
                // Closing on focus loss rather than needing a second gesture:
                // clicking away from a name you have finished typing is the
                // gesture, and asking for confirmation as well would be asking
                // twice.
                if (!focused.isFocused) editing = false
            },
    )

    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * Save, and a standing answer to "would I lose this".
 *
 * The program will not write a deck that has never been saved — that would fill
 * a library with Untitled Decks nobody asked for — so the first save is manual,
 * and the price of that decision is that the state has to be visible rather
 * than assumed. A deck that has been saved once keeps itself saved from then on,
 * and says so quietly.
 */
@Composable
private fun SaveControl(state: DeckBuilderState) {
    val status = state.saveStatus

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.save() }) {
            Icon(
                Icons.Filled.Save,
                contentDescription = when (status) {
                    SaveStatus.NEVER_SAVED -> "Save deck — not saved yet"
                    SaveStatus.UNSAVED_CHANGES -> "Save deck — unsaved changes"
                    else -> "Save deck"
                },
                tint = when (status) {
                    // The only alarming state, and the only one worth colour.
                    SaveStatus.NEVER_SAVED -> MasterToolPalette.Warning
                    SaveStatus.UNTOUCHED -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> LocalContentColor.current
                },
            )
        }

        // Nothing at all for an untouched deck: a program that says "unsaved"
        // about an empty screen is a program crying wolf before anything has
        // happened.
        val label = when (status) {
            SaveStatus.UNTOUCHED -> null
            SaveStatus.NEVER_SAVED -> "NOT SAVED"
            SaveStatus.UNSAVED_CHANGES -> "SAVING…"
            SaveStatus.SAVED -> "SAVED"
        }
        if (label != null) {
            Text(
                label,
                style = tacticalStyle(),
                color = when (status) {
                    SaveStatus.NEVER_SAVED -> MasterToolPalette.Warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
