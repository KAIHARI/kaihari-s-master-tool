package com.kaiharimoto.mastertool.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.kaiharimoto.mastertool.core.export.Png

/**
 * Pixels back out of something that has been drawn.
 *
 * Compose draws into a graphics layer whether it is asked to or not; recording
 * into one this side holds simply keeps a handle on the result, which can then
 * be read as a bitmap. So this is not a screenshot in the operating-system sense
 * — no permission, no other window, nothing outside the composable it is applied
 * to — it is the same draw pass, kept.
 *
 * Applied with [modifier], read with [png]. The picture is exactly what the
 * modifier wrapped, so anything that should not appear in it belongs outside.
 */
class ScreenCapture internal constructor(private val layer: GraphicsLayer) {

    val modifier: Modifier = Modifier.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

    /**
     * The last frame drawn through [modifier], as a PNG.
     *
     * Null rather than an exception when there is nothing to read: called before
     * the first draw, or on a surface a platform declines to read back, and
     * neither is worth crashing a deck builder over.
     */
    suspend fun png(): ByteArray? {
        val bitmap = runCatching { layer.toImageBitmap() }.getOrNull() ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        return runCatching { Png.encode(pixels, bitmap.width, bitmap.height) }.getOrNull()
    }
}

@Composable
fun rememberScreenCapture(): ScreenCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ScreenCapture(layer) }
}
