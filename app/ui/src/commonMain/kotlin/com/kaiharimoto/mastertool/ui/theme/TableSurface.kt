package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mat a deck pane's cards are laid out on.
 *
 * A flat fill says "panel". What a deck sits on is a surface, and the three
 * things that make it read as one are cheap: a woven grain, a vignette so the
 * middle sits forward of the edges, and a sheen along the top so it looks lit
 * from somewhere. None of it is visible under a full Main deck, which is the
 * point — it shows at the margins, in a half-built section, and behind the gap a
 * card leaves while it is being dragged.
 *
 * The binding around the edge is the section's own colour. It is what tells the
 * three panes apart at a glance now that they are no longer separated by empty
 * background, and it is doing a job the header alone was doing before.
 *
 * [mat] rather than a constant, because a leather table and a lit desk are the
 * same three effects at very different settings -- and a bright surface needs
 * its lighting inverted, not its palette.
 */
fun Modifier.tableSurface(
    accent: Color,
    mat: MatColors,
    corner: Dp = 6.dp,
    weaveSpacing: Dp = 5.dp,
): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val spacing = weaveSpacing.toPx().coerceAtLeast(1f)

    // Built once per size rather than per frame. Two paths of a few hundred
    // segments each draw as two calls; the same lines drawn individually would
    // be a few hundred, every frame, behind every pane.
    val downhill = diagonalPath(size, spacing, descending = true)
    val uphill = diagonalPath(size, spacing, descending = false)

    val vignette = Brush.radialGradient(
        0.38f to Color.Transparent,
        1f to Color.Black.copy(alpha = mat.vignette),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = maxOf(size.maxDimension * 0.72f, 1f),
    )

    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = mat.sheen),
        1f to Color.Transparent,
        startY = 0f,
        endY = maxOf(size.height * 0.58f, 1f),
    )

    onDrawBehind {
        drawRoundRect(mat.base, cornerRadius = radius)

        // Warp light, weft dark: the same trick a photograph of cloth relies on.
        // Which of the two is lighter is the theme's business -- on a bright mat
        // the warp is near-white and the weft is a faint blue-grey.
        drawPath(downhill, mat.warp, style = Stroke(1f))
        drawPath(uphill, mat.weft, style = Stroke(1f))

        drawRoundRect(brush = sheen, cornerRadius = radius)
        drawRoundRect(brush = vignette, cornerRadius = radius)

        // Drawn inside the shape rather than as a border modifier so it stays put
        // when a drop highlight puts a second, brighter ring outside it.
        drawRoundRect(
            color = accent.copy(alpha = 0.32f),
            topLeft = Offset(0.5f, 0.5f),
            size = Size(size.width - 1f, size.height - 1f),
            cornerRadius = radius,
            style = Stroke(1f),
        )
    }
}

/**
 * Parallel 45-degree lines covering [size].
 *
 * Offsets run from `-height` so the lines entering through the left edge are
 * included; stopping at zero would leave the bottom-left corner bare.
 */
private fun diagonalPath(size: Size, spacing: Float, descending: Boolean): Path {
    val path = Path()
    if (size.width <= 0f || size.height <= 0f) return path

    var x = -size.height
    while (x <= size.width) {
        if (descending) {
            path.moveTo(x, 0f)
            path.lineTo(x + size.height, size.height)
        } else {
            path.moveTo(x, size.height)
            path.lineTo(x + size.height, 0f)
        }
        x += spacing
    }
    return path
}
