package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
    onClick: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(4.dp)
    val banStatus = card.banStatus(format)

    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(shape)
            .background(MasterToolPalette.SlateRaised)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) {
                    MasterToolPalette.GoldBright
                } else {
                    MaterialTheme.colorScheme.outline
                },
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
            .background(MasterToolPalette.Gold),
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
