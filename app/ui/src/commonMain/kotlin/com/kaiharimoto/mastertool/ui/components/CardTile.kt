package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/** Yu-Gi-Oh! cards are 59 x 86 mm. Everything that shows one uses this ratio. */
const val CARD_ASPECT_RATIO = 59f / 86f

/**
 * Barely rounded, because a real card barely is.
 *
 * At the size a Main deck pane draws them a softer corner reads as a rounded UI
 * tile rather than as a card, and the effect compounds across forty of them.
 */
val CARD_CORNER = 3.dp

/** The dark line printed around every card. */
val CARD_EDGE = Color.Black.copy(alpha = 0.85f)

/**
 * A single card.
 *
 * Tap only. Long press belongs to `DragSource`, which wraps these: two detectors
 * competing for the same press is a gesture that works differently depending on
 * how fast you were.
 */
@Composable
fun CardTile(
    card: Card,
    modifier: Modifier = Modifier,
    format: Format = Format.TCG,
    copies: Int = 0,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    /**
     * Whether this card catches the light.
     *
     * Off for the copy that follows the cursor during a drag, which is already
     * lifted, rotated and shadowed — a second effect on top reads as a bug.
     */
    foil: Boolean = true,
    onClick: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(CARD_CORNER)
    val banStatus = card.banStatus(format)

    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(shape)
            .foil(foil)
            .background(MasterToolPalette.SurfaceRaised)
            // The card's own printed edge, not a UI outline. Deck panes lay cards
            // out touching, so this hairline is the only thing separating one from
            // the next — in the theme's outline colour they merged into a slab.
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) MasterToolPalette.AccentBright else CARD_EDGE,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        // Drawn beneath the artwork rather than only when both URLs are absent:
        // a card whose image fails to load — the offline case this app is built
        // for — otherwise renders as a blank rectangle with nothing to read.
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center).padding(4.dp),
        )

        AsyncImage(
            model = card.imageUrlSmall ?: card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }

        // A lit top edge. One card is a rectangle; a grid of them lit from above is
        // a stack of physical objects, and the difference costs a single line.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = CARD_CORNER)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        )

        if (banStatus != BanStatus.UNLIMITED) {
            BanBadge(banStatus, Modifier.align(Alignment.TopStart).padding(3.dp))
        }

        if (copies > 0) {
            CopyBadge(copies, Modifier.align(Alignment.TopEnd).padding(3.dp))
        }

        overlay()
    }
}

@Composable
private fun BanBadge(status: BanStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        BanStatus.FORBIDDEN -> "0" to MasterToolPalette.Danger
        BanStatus.LIMITED -> "1" to MasterToolPalette.Warning
        BanStatus.SEMI_LIMITED -> "2" to MasterToolPalette.MainAccent
        BanStatus.UNLIMITED -> return
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MasterToolPalette.Ink,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CopyBadge(copies: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MasterToolPalette.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = copies.toString(),
            fontSize = 13.sp,
            color = MasterToolPalette.Ink,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
