package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The menu that opens off a card, and off the toolbar.
 *
 * The most-pressed control in the program was the last piece of stock Material
 * in it: a softly rounded panel on a wide drop shadow, which is what every other
 * application on the tablet also opens.
 *
 * It gets the card's own edge instead — the same three-point corner and the same
 * near-black hairline that every card in the deck is drawn with — and a short,
 * tight shadow. Not *no* shadow: a first version had none and read as a hole cut
 * into the deck rather than as a menu, which is the right answer for the wrong
 * reason. A card lies on the table; a menu is held above it. The shadow says
 * which, and the only thing wrong with Material's was how far above.
 *
 * The edge is black rather than the theme's accent for a reason that only shows
 * up in a prototype: an accent hairline already means *this is selected* in this
 * program, and a menu wearing one reads as a selection somebody made.
 *
 * Colour is left to Material on purpose. A menu item's container and its text
 * are paired by the colour scheme, and a wrapper that changed one of them would
 * be deciding the contrast of every theme — including the light one — from here.
 * The form carries the identity; the palette stays where it is checked.
 */
@Composable
fun MasterToolMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(CARD_CORNER),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.55f)),
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}
