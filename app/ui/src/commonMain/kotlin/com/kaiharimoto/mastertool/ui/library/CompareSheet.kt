package com.kaiharimoto.mastertool.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.DeckChange
import com.kaiharimoto.mastertool.core.deck.DeckDiff
import com.kaiharimoto.mastertool.core.deck.SectionDiff
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What changed between a saved list and the one in front of you.
 *
 * The question every list gets asked between events, and until now the only way
 * to answer it was to open both and read them side by side. Counts rather than
 * copies — three Bonfire becoming two is one line saying so, not a card leaving
 * and a card arriving.
 *
 * It reads in the direction people ask it: *what did I change*, so the saved
 * deck is the before and the open one is the after, and the sheet is opened from
 * the saved deck's tile.
 */
@Composable
fun CompareSheet(
    savedName: String,
    saved: Deck,
    openName: String,
    open: Deck,
    index: CardIndex,
    onDismiss: () -> Unit,
) {
    val diff = DeckDiff.between(saved, open)

    MasterToolSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("What changed", style = MaterialTheme.typography.headlineSmall)

            Text(
                "$savedName  →  ${openName.ifBlank { "the deck you have open" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                diff.reorderedOnly -> Verdict(
                    "The same cards, arranged differently.",
                    "Which is not nothing — the order is the part of a deck that " +
                        "is yours. Nothing was cut and nothing came in.",
                )

                diff.isEmpty -> Verdict(
                    "Nothing has changed.",
                    "Card for card and in the same order.",
                )

                else -> {
                    Text(
                        "${diff.cardsRemoved} out  ·  ${diff.cardsAdded} in" +
                            if (diff.sameOrder) "" else "  ·  and rearranged",
                        style = tacticalStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    DeckSection.entries.forEach { section ->
                        val part = diff[section]
                        if (!part.isEmpty) Block(section, part, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun Verdict(headline: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(headline, style = MaterialTheme.typography.titleMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Block(section: DeckSection, part: SectionDiff, index: CardIndex) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${section.displayName.uppercase()} DECK",
                style = tacticalStyle(),
            )
            Box(Modifier.weight(1f))
            // The count that a swap is judged by. An uneven one is legal and
            // usually a slip, and it is the thing you cannot see by reading the
            // two columns.
            Text(
                "${part.cardsRemoved} out / ${part.cardsAdded} in",
                style = tacticalStyle(),
                color = if (part.isBalanced) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MasterToolPalette.Warning
                },
            )
        }

        part.removed.forEach { Line(it, index, MasterToolPalette.Danger) }
        part.added.forEach { Line(it, index, MasterToolPalette.Success) }
    }
}

@Composable
private fun Line(change: DeckChange, index: CardIndex, accent: Color) {
    val name = index.byId(change.id)?.name ?: change.id.value.toString()

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The count on both sides rather than a signed number: "3 → 2" is what
        // happened, and "-1" is arithmetic about what happened.
        Text(
            "${change.before} → ${change.after}",
            style = tacticalStyle(),
            color = accent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
