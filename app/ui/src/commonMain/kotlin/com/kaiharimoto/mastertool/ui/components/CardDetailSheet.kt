package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * Full card inspector.
 *
 * Replaces the desktop tool's hover preview, which has no touch equivalent. It
 * doubles as the place to move a card between sections, so that action does not
 * need a drag gesture to be reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(
    card: Card,
    format: Format,
    copiesInDeck: Int,
    onDismiss: () -> Unit,
    onAddTo: (DeckSection) -> Unit,
    onRemoveFrom: (DeckSection) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AsyncImage(
                    model = card.imageUrl ?: card.imageUrlSmall,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(200.dp)
                        .height(292.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MasterToolPalette.SlateRaised),
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(card.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        card.type,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (card.category == CardCategory.MONSTER) {
                            card.level?.let { Fact("Level", it.toString()) }
                            card.linkValue?.let { Fact("Link", it.toString()) }
                            Fact("ATK", card.atk?.toString() ?: "—")
                            if (card.linkValue == null) Fact("DEF", card.def?.toString() ?: "—")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        card.race?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                        AssistChip(onClick = {}, label = { Text(card.attribute.name) })
                    }

                    Text(
                        "${format.name}: ${card.banStatus(format).name.lowercase()
                            .replace('_', ' ')}  •  $copiesInDeck in deck",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(card.description, style = MaterialTheme.typography.bodyMedium)

            Text("Add to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val target = card.requiredSection()
                Button(onClick = { onAddTo(target) }) {
                    Text("Add to ${target.displayName}")
                }
                OutlinedButton(onClick = { onAddTo(DeckSection.SIDE) }) { Text("Add to Side") }
                OutlinedButton(onClick = { onRemoveFrom(target) }) { Text("Remove one") }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("$label $value", style = MaterialTheme.typography.labelMedium)
    }
}
