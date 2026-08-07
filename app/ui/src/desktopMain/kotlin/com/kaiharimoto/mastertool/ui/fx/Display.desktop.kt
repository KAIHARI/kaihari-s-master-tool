package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment

@Composable
actual fun rememberRefreshHz(): Float = remember {
    // `REFRESH_RATE_UNKNOWN` is zero, which is already the "would not say"
    // answer this seam is specified in terms of — so it needs no special case,
    // only the catch around a headless environment that has no screen at all.
    runCatching {
        val rate = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.displayMode.refreshRate
        if (rate == DisplayMode.REFRESH_RATE_UNKNOWN) 0f else rate.toFloat()
    }.getOrDefault(0f)
}
