package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of controls that never hides one.
 *
 * ## The bug this is made of
 *
 * Three bars in this app were written the same way — a `Row` with a
 * `Box(Modifier.weight(1f))` in the middle to push the right-hand group to the
 * far edge — and all three were designed against a 1480dp tablet. A `Row` does
 * not wrap and does not scroll: when its children do not fit, it lays them out
 * anyway and the window clips whatever hangs off the end. Silently.
 *
 * On a 780dp phone that cost the deck builder its Save, Undo, Table, Play and
 * overflow menu, and the play stage its Draw, Shuffle and New hand. Both screens
 * rendered perfectly the whole time, which is why it took a user's screenshot to
 * find and not a test.
 *
 * ## Why it is two arrangements rather than one clever one
 *
 * A `Row` cannot both hug the right edge and scroll. Hugging needs a weighted
 * child, a weighted child needs a finite width, and a scroll container hands its
 * content infinity — so the two are mutually exclusive and something has to
 * choose. The width chooses: at or above [fitsAt] this is exactly the row that
 * was designed, weighted spacer and all, and below it the spacer becomes a fixed
 * gap and the row scrolls.
 *
 * That keeps the tablet byte-identical, which matters more than it sounds: the
 * fix for a phone should not be visible on the device the thing was tuned on.
 *
 * ## [fitsAt] is measured
 *
 * Shoot the bar at descending widths — `tools/devices.sh` — find where the last
 * control clips, and round up. Guessing is how the threshold ends up just below
 * the width that actually breaks. Wrong on the roomy side costs a scroll nobody
 * uses; wrong on the narrow side costs somebody a button.
 */
@Composable
fun OverflowBar(
    fitsAt: Dp,
    modifier: Modifier = Modifier,
    contentPadding: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable OverflowBarScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val roomy = maxWidth >= fitsAt
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (roomy) Modifier else Modifier.horizontalScroll(scroll))
                .then(contentPadding),
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
        ) {
            OverflowBarScope(this, roomy).content()
        }
    }
}

/**
 * A [RowScope] that also knows whether the bar had room.
 *
 * Everything a `Row` offers, plus [Gap] — which is the one place the two
 * arrangements differ, and the one thing a caller must not write by hand.
 */
class OverflowBarScope internal constructor(
    row: RowScope,
    /** True when every control fits and the bar is laid out as designed. */
    val roomy: Boolean,
) : RowScope by row {

    /**
     * Pushes what follows to the far edge — where there is an edge to push to.
     *
     * Under a scroll container there is not, so this becomes an ordinary gap.
     * Calling `Modifier.weight` directly instead is the mistake this type exists
     * to prevent: it throws the moment the bar is narrow enough to scroll, which
     * is the only case anybody was trying to fix.
     */
    @Composable
    fun Gap(narrow: Dp = 16.dp) {
        if (roomy) Box(Modifier.weight(1f)) else Spacer(Modifier.width(narrow))
    }
}
