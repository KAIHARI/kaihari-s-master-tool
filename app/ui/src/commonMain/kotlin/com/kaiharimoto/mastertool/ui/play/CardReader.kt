package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * One card, big enough to read.
 *
 * The play stage could already show you a card — press and hold and it comes off
 * the table and turns to face you — and that answers "which card is this" and
 * not "what does it do". At the size a card is drawn on a board seen at an
 * angle, its effect text is a texture. kai plays combo decks off a tablet; the
 * text is the thing being read.
 *
 * Deliberately **not** `CardDetailBody`. That one is the builder's, and most of
 * it is about how many copies you run: steppers, section moves, opening-hand
 * odds. None of that means anything with a card in play, and a sheet full of
 * controls you must not touch is worse than one with none. What is shared is the
 * shape of the answer — art, name, type line, the numbers, the text — and that
 * is thirty lines rather than an abstraction over two screens that want
 * different things.
 *
 * It stays up until dismissed, which is the whole point: a held finger is a
 * glance, and reading four sentences of ruling text with your thumb on the glass
 * is not reading, it is holding still.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardReader(card: Card, onDismiss: () -> Unit) {
    // The scrim takes the whole screen and dismisses, which is the primary way
    // out — a modal over a table you are in the middle of playing on should
    // close by touching the table. `indication = null` because a full-screen
    // ripple on a scrim is a flash of light across the board.
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MasterToolPalette.Ink.copy(alpha = 0.82f))
            .clickable(interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MasterToolPalette.Surface)
                // Its own click, consuming nothing to the scrim behind it: the
                // panel is the one place on the screen where touching does not
                // close it, or you cannot scroll a long effect without losing it.
                .clickable(interaction, indication = null) {}
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AsyncImage(
                    model = card.imageUrl ?: card.imageUrlSmall,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(172.dp)
                        .height(251.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MasterToolPalette.SurfaceRaised),
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MasterToolPalette.Text,
                    )
                    Text(
                        card.type,
                        style = MaterialTheme.typography.labelLarge,
                        color = MasterToolPalette.TextMuted,
                    )

                    if (card.category == CardCategory.MONSTER) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            card.level?.let { Stat("Level", it.toString()) }
                            card.linkValue?.let { Stat("Link", it.toString()) }
                            Stat("ATK", card.atk?.toString() ?: "—")
                            // A Link monster has no defence, and printing an em
                            // dash for one says "unknown" where the truth is
                            // "there is no such number".
                            if (card.linkValue == null) Stat("DEF", card.def?.toString() ?: "—")
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            card.race?.let { Stat("Type", it) }
                            Stat("Attribute", card.attribute.name)
                        }
                    }
                }
            }

            // The reason this screen exists. Sixteen point over a fourteen-point
            // sheet, and a line height that lets the eye find the start of the
            // next line — effect text is written as one paragraph of clauses and
            // is read by hunting for the clause that matters.
            Text(
                card.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                ),
                color = MasterToolPalette.Text,
            )

            Text(
                "Tap anywhere to close",
                style = MaterialTheme.typography.labelSmall,
                color = MasterToolPalette.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One number with its name over it, in the type scale the handbook sets. */
@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MasterToolPalette.TextMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MasterToolPalette.Text,
        )
    }
}
