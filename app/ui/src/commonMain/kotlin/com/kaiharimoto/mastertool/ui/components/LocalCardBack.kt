package com.kaiharimoto.mastertool.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * An image to use for the back instead of the one that ships, if somebody has
 * pointed the app at one.
 *
 * One field, and it was two: the back used to be a choice between two drawn
 * ones. It is kai's artwork now, and a bundled file is not a thing to offer
 * alternatives to. Still a type rather than a bare `String`, because it is what
 * the composition local carries and a local called `LocalCardBack` holding a URL
 * would be a local that has to be renamed the next time the back gains anything.
 */
data class CardBackChoice(
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
