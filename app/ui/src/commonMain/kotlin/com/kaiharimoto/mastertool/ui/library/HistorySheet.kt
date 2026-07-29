package com.kaiharimoto.mastertool.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.data.DeckVersion
import com.kaiharimoto.mastertool.core.deck.DeckDiff
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.util.RelativeTime
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What a deck used to be.
 *
 * Drawn as a line down the page with the deck as it stands at the top of it,
 * because that is what a history *is* — a list would say the same words and read
 * as settings. A version somebody named is a filled node with the name beside it,
 * which is the thing you scan for when you are looking for the list you
 * registered somewhere.
 *
 * No delete. Versions thin themselves out, and a named one stops being kept the
 * moment it stops being named — so a button for it would be a third way to say
 * something already sayable, standing next to the one button here that changes a
 * deck.
 */
@Composable
fun HistorySheet(
    deckName: String,
    versions: List<DeckVersion>,
    /** The deck itself, which every version is read against. */
    current: Deck,
    editedAtEpochMs: Long,
    now: Long,
    onKeepNow: (String?) -> Unit,
    onName: (DeckVersion, String?) -> Unit,
    onRestore: (DeckVersion) -> Unit,
    onCompare: (DeckVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf<DeckVersion?>(null) }
    var keeping by remember { mutableStateOf(false) }

    MasterToolSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("What this deck used to be", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (versions.isEmpty()) deckName else "$deckName  ·  ${versions.size} kept",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Box(Modifier.height(18.dp))

            Node(first = true, last = versions.isEmpty(), filled = true) {
                Text("The deck as it stands", style = MaterialTheme.typography.titleMedium)
                Text(
                    "EDITED ${RelativeTime.since(now, editedAtEpochMs).uppercase()}",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(
                    onClick = { keeping = true },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Keep this one…") }
            }

            versions.forEachIndexed { position, version ->
                Node(
                    first = false,
                    last = position == versions.lastIndex,
                    filled = false,
                    accent = version.name != null,
                    onClick = { onCompare(version) },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                RelativeTime.since(now, version.keptAtEpochMs).uppercase(),
                                style = tacticalStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            version.name?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Against(version, current)
                        }

                        // One tap takes a name off, because that is the whole of
                        // un-keeping something. Putting one on asks what to call
                        // it, because a version called nothing is what the
                        // automatic rule already produces.
                        IconButton(
                            onClick = {
                                if (version.name != null) onName(version, null) else naming = version
                            },
                        ) {
                            Icon(
                                if (version.name != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (version.name != null) {
                                    "Stop keeping this version"
                                } else {
                                    "Keep this version"
                                },
                                tint = if (version.name != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    TextButton(
                        onClick = { onRestore(version) },
                        modifier = Modifier.padding(top = 2.dp),
                    ) { Text("Go back to it") }
                }
            }

            Text(
                if (versions.isEmpty()) {
                    "Nothing yet. A copy is kept by itself the next time you come " +
                        "back to this deck after a break. Keep one now if this is the " +
                        "list you are registering."
                } else {
                    "Tap one to see what changed. A version you name is kept for good; " +
                        "the rest make room for newer ones, oldest and newest last."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RAIL, top = 14.dp),
            )
        }
    }

    if (keeping) {
        NameDialog(
            title = "Keep this one",
            detail = "It stops being thinned out, and the name is how you will find it.",
            onDismiss = { keeping = false },
            onConfirm = { keeping = false; onKeepNow(it) },
        )
    }

    naming?.let { version ->
        NameDialog(
            title = "Keep this version",
            detail = "Named versions are never let go of.",
            onDismiss = { naming = null },
            onConfirm = { naming = null; onName(version, it) },
        )
    }
}

/** How this version reads against the deck as it is now. */
@Composable
private fun Against(version: DeckVersion, current: Deck) {
    val diff = remember(version.id, current) { DeckDiff.between(version.deck, current) }

    when {
        diff.reorderedOnly -> Text(
            "The same cards, arranged differently.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        diff.isEmpty -> Text(
            "This is the deck as it stands.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        else -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            // Reads from the version towards the deck you have: what came out of
            // it to get here, and what went in. Same direction as the compare
            // sheet a tap away, so the numbers do not swap under you.
            Text("${diff.cardsRemoved} OUT", style = tacticalStyle(), color = MasterToolPalette.Danger)
            Text("${diff.cardsAdded} IN", style = tacticalStyle(), color = MasterToolPalette.Success)
            if (!diff.sameOrder) {
                Text(
                    "AND REARRANGED",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val RAIL = 34.dp
private val NODE = 5.5.dp

/**
 * One stop on the line.
 *
 * The rail is drawn behind the whole row rather than as a spacer column so it
 * runs continuously between nodes however tall the rows turn out to be, and it
 * stops at the first and last node instead of running off into the padding.
 */
@Composable
private fun Node(
    first: Boolean,
    last: Boolean,
    filled: Boolean,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val line = MaterialTheme.colorScheme.outline
    val dot = when {
        accent -> MaterialTheme.colorScheme.primary
        filled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RAIL.toPx() / 2
                val y = 14.dp.toPx()
                drawLine(
                    color = line,
                    start = Offset(x, if (first) y else 0f),
                    end = Offset(x, if (last) y else size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = dot, radius = NODE.toPx(), center = Offset(x, y))
                if (accent) {
                    drawCircle(
                        color = dot.copy(alpha = 0.18f),
                        radius = NODE.toPx() * 2.4f,
                        center = Offset(x, y),
                    )
                }
            },
    ) {
        Column(
            Modifier
                .padding(start = RAIL)
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(top = 6.dp, bottom = 14.dp, end = 4.dp),
        ) { content() }
    }
}

@Composable
private fun NameDialog(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(detail, style = MaterialTheme.typography.bodyMedium)
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
