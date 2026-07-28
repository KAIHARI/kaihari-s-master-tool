package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * A `−  n  +` control for the number of copies of a card in one section.
 *
 * Setting a count outright rather than adding and removing one at a time is what
 * `DeckEditor.setCount` is for: it clamps to whatever the banlist and the
 * section's capacity permit instead of rejecting, so the control never
 * dead-ends, and it re-inserts at the card's original position so the grid does
 * not reshuffle under your finger.
 */
@Composable
fun CopiesStepper(
    label: String,
    count: Int,
    max: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp),
        )

        IconButton(onClick = { onChange(count - 1) }, enabled = count > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "One fewer copy in $label")
        }

        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (count > 0) MasterToolPalette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        IconButton(onClick = { onChange(count + 1) }, enabled = count < max) {
            Icon(Icons.Filled.Add, contentDescription = "One more copy in $label")
        }
    }
}
