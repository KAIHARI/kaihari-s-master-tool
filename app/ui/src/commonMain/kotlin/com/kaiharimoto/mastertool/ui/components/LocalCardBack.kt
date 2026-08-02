package com.kaiharimoto.mastertool.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.kaiharimoto.mastertool.core.prefs.CardBackStyle

/** Which back to draw, and an image to use instead if one has been supplied. */
data class CardBackChoice(
    val style: CardBackStyle = CardBackStyle.OVAL,
    val imageUrl: String = "",
)

/**
 * The card back, available anywhere without being threaded through every screen.
 *
 * A local rather than a parameter because a face-down card can appear in the
 * builder, on the table, in the play space and in a pile sheet, and none of those
 * call sites has any other reason to know about preferences. It is static: the
 * back changes when someone changes a setting, which is not a thing worth
 * re-reading on every recomposition.
 */
val LocalCardBack = staticCompositionLocalOf { CardBackChoice() }
