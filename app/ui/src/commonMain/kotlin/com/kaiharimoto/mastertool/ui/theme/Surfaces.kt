package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.ThemeChoice

/**
 * The cloth a deck pane is drawn on.
 *
 * Two thread colours rather than one texture, because that is what makes a weave
 * read as woven: a light warp catching the light and a dark weft in its shadow.
 * Alpha is baked into the colours; [sheen] and [vignette] are separate because
 * they are lighting rather than material, and a bright theme needs a great deal
 * of the first and almost none of the second.
 */
@Immutable
data class MatColors(
    val base: Color,
    val warp: Color,
    val weft: Color,
    val sheen: Float,
    val vignette: Float,
)

/**
 * The binding around each pane, and the bar in its header.
 *
 * Load-bearing since the gutter closed: with cards touching there is no
 * background showing between them, so the binding is what says which pane you
 * are looking at without reading the label.
 */
@Immutable
data class SectionColors(val main: Color, val extra: Color, val side: Color) {
    operator fun get(section: DeckSection): Color = when (section) {
        DeckSection.MAIN -> main
        DeckSection.EXTRA -> extra
        DeckSection.SIDE -> side
    }
}

/**
 * Everything a theme decides that Material's colour scheme has no slot for.
 *
 * Semantic colours — danger, warning, success — deliberately stay in
 * [MasterToolPalette] as constants. A deck over sixty cards is wrong in every
 * theme, and a red that shifted with the surface would be saying that how wrong
 * it is depends on the décor.
 */
@Immutable
data class MasterToolColors(
    val mat: MatColors,
    val sections: SectionColors,
    val accent: Color,
    val accentBright: Color,
)

/**
 * Static rather than dynamic: the theme changes when someone picks a new one,
 * which is rare and should recompose everything, and never otherwise.
 */
val LocalMasterToolColors = staticCompositionLocalOf { SwissColors }

/** Slate and indigo. What the app has always looked like, and the default. */
val SwissColors = MasterToolColors(
    mat = MatColors(
        base = Color(0xFF16203A),
        warp = Color.White.copy(alpha = 0.022f),
        weft = Color.Black.copy(alpha = 0.16f),
        sheen = 0.05f,
        vignette = 0.42f,
    ),
    sections = SectionColors(
        main = Color(0xFF3B82F6),
        extra = Color(0xFFA855F7),
        side = Color(0xFF10B981),
    ),
    accent = Color(0xFF6366F1),
    accentBright = Color(0xFF818CF8),
)

/**
 * Brown leather and gold.
 *
 * The original had this idea and left it as six hex values, which is why it
 * never became the table it was reaching for. The warp is warm rather than white
 * and the weft is heavier, so the grain reads as hide instead of as cloth.
 */
val ClassicColors = MasterToolColors(
    mat = MatColors(
        base = Color(0xFF2A1A08),
        warp = Color(0xFFFFD68C).copy(alpha = 0.030f),
        weft = Color.Black.copy(alpha = 0.26f),
        sheen = 0.055f,
        vignette = 0.52f,
    ),
    sections = SectionColors(
        main = Color(0xFFE8A33D),
        extra = Color(0xFFC47832),
        side = Color(0xFF96A05A),
    ),
    accent = Color(0xFFE8A33D),
    accentBright = Color(0xFFF5C877),
)

/** Cyan and magenta over near-black. The one theme in the original with nerve. */
val CyberColors = MasterToolColors(
    mat = MatColors(
        base = Color(0xFF08080E),
        warp = Color(0xFF00FFFF).copy(alpha = 0.030f),
        weft = Color.Black.copy(alpha = 0.42f),
        sheen = 0.03f,
        vignette = 0.60f,
    ),
    sections = SectionColors(
        main = Color(0xFF00FFFF),
        extra = Color(0xFFFF00FF),
        side = Color(0xFF3CFF8C),
    ),
    accent = Color(0xFF00FFFF),
    accentBright = Color(0xFF8CFFFF),
)

/**
 * A lit desk.
 *
 * Inverted lighting, not an inverted palette: the sheen goes up an order of
 * magnitude and the vignette nearly off, because a bright surface is lit from
 * the room rather than shadowed at its edges. A dark theme's numbers negated
 * would have produced grey paper with a dirty rim.
 */
val DaylightColors = MasterToolColors(
    mat = MatColors(
        base = Color(0xFFE3E7EF),
        warp = Color.White.copy(alpha = 0.55f),
        weft = Color(0xFF5A647D).copy(alpha = 0.10f),
        sheen = 0.5f,
        vignette = 0.13f,
    ),
    sections = SectionColors(
        main = Color(0xFF2563EB),
        extra = Color(0xFF9333EA),
        side = Color(0xFF059669),
    ),
    accent = Color(0xFF4338CA),
    accentBright = Color(0xFF6366F1),
)

fun colorsFor(theme: ThemeChoice): MasterToolColors = when (theme) {
    ThemeChoice.SWISS -> SwissColors
    ThemeChoice.CLASSIC -> ClassicColors
    ThemeChoice.CYBER -> CyberColors
    ThemeChoice.DAYLIGHT -> DaylightColors
}
