package com.kaiharimoto.mastertool.ui.fx

import androidx.compose.runtime.Composable

/**
 * How fast the panel in front of the user actually refreshes.
 *
 * Asked for exactly one reason: [com.kaiharimoto.mastertool.core.perf.FrameProbe]
 * derives its budget from it, and a budget derived from a guess is a readout
 * that lies in the one direction nobody checks. Sixteen and two thirds
 * milliseconds is a comfortable frame on a sixty-hertz desktop and a missed one
 * on the hundred-and-twenty-hertz tablet this app is built for, so a probe told
 * the wrong rate either reports jank that is not there or, worse, reports health
 * on a stage that is dropping half its frames.
 *
 * Composable because Android's answer comes through a `Context`, the same reason
 * [rememberHapticEngine] is one.
 *
 * **Zero is a legitimate answer** and means "this platform would not say". The
 * probe treats any non-positive rate as absent and falls back on its own
 * default, so nothing here has to invent a number it does not know.
 */
@Composable
expect fun rememberRefreshHz(): Float
