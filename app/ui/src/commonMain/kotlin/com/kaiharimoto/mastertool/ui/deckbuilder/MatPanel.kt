package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.MatChoice
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.theme.DeckMats
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolColors
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * What this deck is laid out on.
 *
 * Swatches rather than a list of names, and drawn by the same code that draws
 * the real thing — so what is being chosen is the surface itself and not a
 * label for it. Each one has a card on it, because a mat's whole job is sitting
 * under card art and a swatch of bare cloth would be choosing it blind.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatPanel(state: DeckBuilderState) {
    val colors = LocalMasterToolColors.current

    MasterToolSheet(onDismiss = { state.matVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row {
                Text("The mat", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.weight(1f))
                Text(
                    "SAVED WITH THE DECK",
                    style = tacticalStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MatChoice.entries.forEach { choice ->
                    Swatch(
                        choice = choice,
                        chosen = choice == state.mat,
                        accent = colors.accent,
                        theme = colors,
                        onClick = { state.chooseMat(choice) },
                    )
                }
            }

            Text(
                "The theme is the program's; the mat is this deck's. Three lists " +
                    "on three cloths are three decks you can tell apart from across " +
                    "the room, and it travels in the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Swatch(
    choice: MatChoice,
    chosen: Boolean,
    accent: Color,
    theme: MasterToolColors,
    onClick: () -> Unit,
) {
    val mat = DeckMats.of(choice, theme)

    Column(
        Modifier.width(112.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .tableSurface(accent, mat)
                // The ring is outside the mat's own binding rather than instead
                // of it, so a chosen swatch still shows the edge it will draw.
                .then(
                    if (chosen) {
                        Modifier.border(2.dp, accent, RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            // One card, at the proportions a real one has.
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .clip(RoundedCornerShape(2.dp))
                    .background(mat.ink.copy(alpha = 0.18f))
                    .border(1.dp, mat.ink.copy(alpha = 0.30f), RoundedCornerShape(2.dp)),
            )
        }

        Text(
            if (choice == MatChoice.THEME) "AS THE THEME" else choice.name,
            style = tacticalStyle(),
            color = if (chosen) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
