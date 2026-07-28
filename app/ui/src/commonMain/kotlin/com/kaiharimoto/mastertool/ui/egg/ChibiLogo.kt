package com.kaiharimoto.mastertool.ui.egg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.art.Res
import com.kaiharimoto.mastertool.ui.art.chibi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** How long a run of taps stays a run before it is forgotten. */
private const val TAP_WINDOW_MS = 600L

private const val TAPS_TO_WAKE = 3

/**
 * The logo, and the way in to the easter egg.
 *
 * Three taps, as it always was. Counted with a window that forgets rather than a
 * clock, so a stray tap on the way past cannot arm it half an hour later.
 */
@Composable
fun ChibiLogo(
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    var taps by remember { mutableStateOf(0) }

    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        if (taps >= TAPS_TO_WAKE) {
            taps = 0
            onWake()
            return@LaunchedEffect
        }
        delay(TAP_WINDOW_MS)
        taps = 0
    }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { taps++ },
    ) {
        Image(
            painter = painterResource(Res.drawable.chibi),
            contentDescription = "kai's master tool",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
