package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors

/**
 * The most-seen thing in the program, and the last one still wearing Material.
 *
 * Nearly every edit raises one — a card removed, a deck saved, a tidy applied,
 * a siding plan swapped in — so it is seen more often than any panel and was the
 * one surface still shaped and coloured like somebody else's app.
 *
 * Same cloth as the panels, a 4dp corner rather than Material's rounded slab,
 * and a hairline of the section-neutral accent so it reads as lifted off the
 * table rather than printed on it. The action keeps the bright accent, because
 * "Undo" is the only word in this program that has to be found in a hurry.
 */
@Composable
fun MasterToolSnackbar(data: SnackbarData) {
    val colors = LocalMasterToolColors.current
    val shape = RoundedCornerShape(4.dp)

    Snackbar(
        snackbarData = data,
        modifier = Modifier.border(1.dp, colors.accent.copy(alpha = 0.45f), shape),
        shape = shape,
        containerColor = colors.mat.base,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = colors.accentBright,
    )
}
