package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kaiharimoto.mastertool.core.deck.MatChoice
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
    /**
     * The colour of writing on this cloth.
     *
     * Carried by the mat rather than taken from the theme because a deck brings
     * its own mat now: a light one under a dark theme, or the other way round,
     * would otherwise put the caption in a colour chosen for a surface that is
     * no longer underneath it. There is not much writing on a mat — the deck's
     * name and its counts — which is exactly why it would have gone unnoticed.
     */
    val ink: Color,
    val inkQuiet: Color,
    /**
     * The colour of a hollow in this cloth.
     *
     * The empty-section slots — the deck that is not there yet — were a fixed
     * 22% black, which is a shallow recess on five of these and a set of neutral
     * grey holes on the light one, at more than twice the weight of its own
     * weave. Exactly the mistake [ink] exists to have stopped, one surface
     * further down.
     *
     * Carries its own alpha, because how deep a hollow reads depends on how dark
     * the cloth already is.
     */
    val recess: Color,
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
        ink = Color(0xFFF1F5F9),
        inkQuiet = Color(0xFF94A3B8),
        recess = Color.Black.copy(alpha = 0.22f),
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
        ink = Color(0xFFF5E3B8),
        inkQuiet = Color(0xFFB08C4A),
        recess = Color.Black.copy(alpha = 0.26f),
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
        ink = Color(0xFFD6FFFF),
        inkQuiet = Color(0xFF6FB3B3),
        recess = Color.Black.copy(alpha = 0.34f),
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
        ink = Color(0xFF1E293B),
        inkQuiet = Color(0xFF5A647D),
        // Warm-neutral and shallow, for the reason the weft is: a black hollow
        // in a lit surface is a hole cut in it rather than a dip.
        recess = Color(0xFF5A647D).copy(alpha = 0.16f),
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

/**
 * The mats a deck can be laid out on, whatever the application is wearing.
 *
 * The theme belongs to the program and the mat belongs to the deck — see
 * [com.kaiharimoto.mastertool.core.deck.MatChoice] for why that split is worth
 * having. Each one carries its own ink, so a bone mat under a dark theme still
 * has a caption somebody can read.
 *
 * Every one of these is picked to sit *under* card art, which is the only thing
 * a surface has to do. That is also why there is no colour picker: a free choice
 * here is a way to produce a mat that fights the cards on it.
 */
object DeckMats {

    /** What [choice] looks like, falling back to whatever the theme decided. */
    fun of(choice: MatChoice, theme: MasterToolColors): MatColors = when (choice) {
        MatChoice.THEME -> theme.mat
        MatChoice.SLATE -> Slate
        MatChoice.LEATHER -> Leather
        MatChoice.MIDNIGHT -> Midnight
        MatChoice.BAIZE -> Baize
        MatChoice.WINE -> Wine
        MatChoice.BONE -> Bone
    }

    val Slate = MatColors(
        base = Color(0xFF16203A),
        warp = Color.White.copy(alpha = 0.022f),
        weft = Color.Black.copy(alpha = 0.16f),
        sheen = 0.05f,
        vignette = 0.42f,
        ink = Color(0xFFF1F5F9),
        inkQuiet = Color(0xFF94A3B8),
        recess = Color.Black.copy(alpha = 0.22f),
    )

    val Leather = MatColors(
        base = Color(0xFF2A1A08),
        warp = Color(0xFFFFD68C).copy(alpha = 0.030f),
        weft = Color.Black.copy(alpha = 0.26f),
        sheen = 0.055f,
        vignette = 0.52f,
        ink = Color(0xFFF5E3B8),
        inkQuiet = Color(0xFFB08C4A),
        recess = Color.Black.copy(alpha = 0.26f),
    )

    val Midnight = MatColors(
        base = Color(0xFF0A0C14),
        warp = Color(0xFFBECDFF).copy(alpha = 0.020f),
        weft = Color.Black.copy(alpha = 0.34f),
        sheen = 0.035f,
        vignette = 0.58f,
        ink = Color(0xFFE6EAF5),
        inkQuiet = Color(0xFF8892AA),
        recess = Color.Black.copy(alpha = 0.30f),
    )

    /** What a card table is actually covered in. */
    val Baize = MatColors(
        base = Color(0xFF123024),
        warp = Color(0xFFD2FFE1).copy(alpha = 0.026f),
        weft = Color.Black.copy(alpha = 0.28f),
        sheen = 0.05f,
        vignette = 0.48f,
        ink = Color(0xFFE4F5EA),
        inkQuiet = Color(0xFF87AE99),
        recess = Color.Black.copy(alpha = 0.26f),
    )

    val Wine = MatColors(
        base = Color(0xFF2C0E16),
        warp = Color(0xFFFFBEC8).copy(alpha = 0.026f),
        weft = Color.Black.copy(alpha = 0.30f),
        sheen = 0.05f,
        vignette = 0.50f,
        ink = Color(0xFFF7E2E6),
        inkQuiet = Color(0xFFB98D96),
        recess = Color.Black.copy(alpha = 0.28f),
    )

    /**
     * The one light mat, and the reason [MatColors.ink] exists.
     *
     * The warp is well below what a light surface would take on its own, because
     * the weave is drawn *behind* the cards and only shows at the margins — a
     * value chosen by looking at the cloth alone comes out shouting once forty
     * cards are on top of it.
     */
    val Bone = MatColors(
        base = Color(0xFFE8E2D4),
        warp = Color.White.copy(alpha = 0.35f),
        weft = Color(0xFF5A5040).copy(alpha = 0.10f),
        sheen = 0.22f,
        vignette = 0.16f,
        ink = Color(0xFF241E14),
        inkQuiet = Color(0xFF6B6252),
        // The reason [MatColors.recess] exists, the same way the ink was. Warm,
        // and shallow: a black hollow in bone reads as a hole punched through it,
        // and 22% of it is heavier than this cloth's own weave by a factor of two.
        recess = Color(0xFF5A5040).copy(alpha = 0.16f),
    )
}
