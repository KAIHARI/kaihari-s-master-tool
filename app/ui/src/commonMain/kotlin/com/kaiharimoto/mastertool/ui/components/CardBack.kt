package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.prefs.CardBackStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The back of a card.
 *
 * Drawn rather than shipped as artwork, for two reasons that both stand on their
 * own. The official Yu-Gi-Oh! back is Konami's, and this repository is public —
 * putting their file in it is redistributing it, which is a different thing from
 * an app fetching card fronts from a card database at runtime. And a back is on
 * screen constantly, often dozens at a time, so it has to be instant and correct
 * with no network at all; a face-down card that is a grey rectangle until a
 * download lands is not a face-down card.
 *
 * So: two backs, both vector, both correct at any size, and a field in settings
 * for anyone who wants to point the app at a different image on their own device
 * (`cardBackUrl`). That last part is how the real artwork gets used without this
 * repository carrying it.
 *
 * The two are the two people actually mean by "the card back": the plain one
 * with a dark oval, and the older spiral.
 */
@Composable
fun CardBack(
    modifier: Modifier = Modifier,
    style: CardBackStyle = CardBackStyle.OVAL,
    imageUrl: String = "",
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier.clip(shape).background(FIELD_DEEP)) {
        // Drawn first and always, so it is what shows while a supplied image
        // loads and what remains if that image never arrives.
        Canvas(Modifier.fillMaxSize()) {
            drawField()
            when (style) {
                CardBackStyle.OVAL -> drawOval()
                CardBackStyle.SPIRAL -> drawSpiral()
            }
            drawEdge()
        }

        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        }
    }
}

/**
 * The card stock: a warm brown, lit slightly from the top left.
 *
 * Warm rather than the app's true black on purpose. Everything else here is ink
 * on a screen; a card back is the one element pretending to be a physical object
 * held under a light, and it is the only warm surface in the whole app.
 */
private fun DrawScope.drawField() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(FIELD_LIGHT, FIELD, FIELD_DEEP),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

/** A thin darker rule just inside the edge, the way printing sits on card stock. */
private fun DrawScope.drawEdge() {
    val inset = min(size.width, size.height) * 0.045f
    drawRect(
        color = EDGE,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        style = Stroke(width = min(size.width, size.height) * 0.018f),
    )
}

/**
 * The plain back: one dark oval, centred.
 *
 * Proportioned off the card's short side so it is the same shape whatever size
 * the card is drawn at — a back that got rounder on a bigger card would read as
 * a different card.
 */
private fun DrawScope.drawOval() {
    val width = size.width * 0.62f
    val height = size.height * 0.42f
    val topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f)

    // A soft halo first, so the oval sits *in* the card rather than on top of it.
    drawOval(
        color = HALO,
        topLeft = Offset(topLeft.x - width * 0.05f, topLeft.y - height * 0.05f),
        size = Size(width * 1.10f, height * 1.10f),
    )
    drawOval(color = INK, topLeft = topLeft, size = Size(width, height))
    // The rim: a card back is embossed, and one light edge is the whole read.
    drawOval(
        color = RIM,
        topLeft = topLeft,
        size = Size(width, height),
        style = Stroke(width = min(size.width, size.height) * 0.012f),
    )
}

/**
 * The older back: a spiral turning out from the middle.
 *
 * An Archimedean spiral — radius growing linearly with angle — sampled finely
 * enough that the curve reads as drawn rather than as line segments, and stroked
 * with a width that tapers outward so it has the weight of something printed.
 */
private fun DrawScope.drawSpiral() {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = min(size.width, size.height) * 0.44f
    val turns = 3.6f
    val sweep = turns * 2f * PI.toFloat()
    val steps = 720

    // Drawn as a series of short segments rather than one path, because the
    // stroke width changes along its length and a single path cannot taper.
    rotate(degrees = -18f, pivot = centre) {
        var previous = centre
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val angle = t * sweep
            val radius = maxRadius * t
            val point = Offset(
                centre.x + radius * cos(angle),
                centre.y + radius * sin(angle),
            )
            drawLine(
                color = INK,
                start = previous,
                end = point,
                strokeWidth = maxRadius * (0.19f - 0.10f * t),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            previous = point
        }

        // The eye of the spiral, so the middle is solid rather than a knot of
        // overlapping strokes.
        drawCircle(color = INK, radius = maxRadius * 0.10f, center = centre)
    }
}

/** A path helper kept for callers that want the spiral as a shape. */
internal fun spiralPath(centre: Offset, maxRadius: Float, turns: Float): Path {
    val path = Path()
    val sweep = turns * 2f * PI.toFloat()
    val steps = 480

    path.moveTo(centre.x, centre.y)
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val angle = t * sweep
        val radius = maxRadius * t
        path.lineTo(centre.x + radius * cos(angle), centre.y + radius * sin(angle))
    }
    return path
}

// Card stock, not app surface: the one warm thing on screen.
private val FIELD_LIGHT = Color(0xFF9C6B3C)
private val FIELD = Color(0xFF8A5A2E)
private val FIELD_DEEP = Color(0xFF6E4523)
private val EDGE = Color(0x55341F0E)
private val HALO = Color(0x33241505)
private val INK = Color(0xFF17110A)
private val RIM = Color(0x33C79A63)
