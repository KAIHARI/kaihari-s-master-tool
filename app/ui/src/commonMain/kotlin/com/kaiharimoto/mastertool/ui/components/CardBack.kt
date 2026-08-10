package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.ui.art.Res
import com.kaiharimoto.mastertool.ui.art.card_back
import org.jetbrains.compose.resources.imageResource

/**
 * The back of a card: kai's artwork, bundled.
 *
 * ## Why this one is a file when the other two were not
 *
 * There used to be two *drawn* backs — a brown field with an oval, and an older
 * spiral — and the argument for drawing them stands, unchanged, for the artwork
 * it was about: the official Yu-Gi-Oh! back is Konami's, this repository is
 * public, and shipping their file here is redistributing it, which is a
 * different thing from an app fetching card fronts from a card database at
 * runtime. None of that reaches kai's own drawing, so this one is simply
 * committed.
 *
 * What does still apply is the other half of that argument, which was never
 * about copyright: a back is on screen constantly, often sixty at a time, so it
 * has to be instant and correct with no network at all. A bundled resource is,
 * and [imageUrl] — `UiPreferences.cardBackUrl`, an image on the user's own
 * device, for anyone who wants the real thing — is still how that gets in
 * without this repository carrying it.
 *
 * ## Two things about how it is drawn
 *
 * **[ContentScale.FillBounds], not `Crop`.** The art is 750×1050 and a card is
 * 59×86, so it is four per cent wide for the box it goes in. Squeezed, nobody
 * can tell. Cropped, the scale-to-fill takes about twenty pixels off the top and
 * bottom of a thirty-three pixel white margin — and that margin is part of the
 * card rather than the page it was drawn on, which is a thing to be asked about
 * once rather than guessed at.
 *
 * **Something correct is under it.** [imageResource] resolves asynchronously
 * even for a file inside the app, and a face-down card that is a blank rectangle
 * for a frame is a face-down card that flickers when a hand is dealt. So the
 * artwork's own three flat bands are painted first — no speckle, no ellipse,
 * just the colours in the right places, which is a placeholder rather than a
 * second copy of the drawing.
 */
@Composable
fun CardBack(
    modifier: Modifier = Modifier,
    imageUrl: String = "",
    cornerRadius: Dp = 4.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier.clip(shape)) {
        Canvas(Modifier.fillMaxSize()) { drawBands() }

        // `FilterQuality.Medium`, which asks for mipmaps. The art is 750 wide
        // and a card on the stage is about a hundred, so this is a seven-to-one
        // reduction, and the default `Low` samples four texels and no more —
        // half the speckle is never looked at, and *which* half changes as the
        // card moves. A field of high-frequency noise is the worst case there
        // is for that, and the failure mode is the back crawling under a drag.
        //
        // **Asks for**, deliberately: the studio rasterises in software and its
        // shots come back the same either way, so this is a hint the device's
        // GPU can honour and the loop's own eye cannot check. It is here because
        // it is the correct hint and costs nothing, not because a picture proved
        // it — `FrameProbe` and a thumb on the tablet are the honest test.
        val art = imageResource(Res.drawable.card_back)
        Image(
            painter = remember(art) { BitmapPainter(art, filterQuality = FilterQuality.Medium) },
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The artwork with everything interesting taken out: the hairline, the margin,
 * the frame and the field, as three rectangles.
 *
 * The numbers are measured off the file rather than chosen — 5px of ink, 28px of
 * paper and 5px more ink, on every side of a 750×1050 image — so the placeholder
 * cannot end up a different shape from the thing it stands in for. Each is a
 * fraction of its own axis for the same reason the image is drawn
 * [ContentScale.FillBounds]: the two scale independently, so an inset taken from
 * the width would land in the wrong place down the long side.
 */
private fun DrawScope.drawBands() {
    fun band(inset: Float, colour: Color) {
        val x = size.width * (inset / SOURCE_WIDTH)
        val y = size.height * (inset / SOURCE_HEIGHT)
        drawRect(
            color = colour,
            topLeft = Offset(x, y),
            size = Size(size.width - x * 2f, size.height - y * 2f),
        )
    }

    drawRect(color = INK)
    band(HAIRLINE, PAPER)
    band(HAIRLINE + MARGIN + FRAME, FIELD)
}

private const val SOURCE_WIDTH = 750f
private const val SOURCE_HEIGHT = 1050f

private const val HAIRLINE = 5f
private const val MARGIN = 28f
private const val FRAME = 5f

/** The hairline round the outside, and the frame round the field. */
private val INK = Color(0xFF000000)

/** The margin between them, which is part of the card rather than the page. */
private val PAPER = Color(0xFFFFFFFF)

/** And the field the speckle is printed on. */
private val FIELD = Color(0xFFDE3163)
