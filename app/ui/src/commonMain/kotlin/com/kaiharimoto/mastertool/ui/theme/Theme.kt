package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Swiss Prismatic, carried over from the tool this replaces.
 *
 * The values are the ones in `offline/swiss-prismatic-theme.css`, so the rebuilt
 * app looks like the thing people already use rather than like a different
 * program that happens to open the same files: deep slate, one indigo accent, and
 * the card-type and attribute colours the original assigned.
 *
 * Geometry stays close to square on purpose — cards are rectangles, and a deck
 * builder full of heavily rounded chrome fights the content it is displaying.
 */
object MasterToolPalette {
    // --color-primary and friends.
    val Accent = Color(0xFF6366F1)
    val AccentBright = Color(0xFF818CF8)
    val AccentDeep = Color(0xFF4338CA)

    // --color-bg / --color-surface-*.
    val Background = Color(0xFF0F172A)
    val Surface = Color(0xFF1E293B)
    val SurfaceRaised = Color(0xFF334155)
    val SurfaceHigh = Color(0xFF475569)

    val Line = Color(0xFF334155)
    val LineLight = Color(0xFF475569)

    val Text = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF94A3B8)
    val Ink = Color(0xFF0F172A)

    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    /**
     * Card types, as the original coloured them.
     *
     * Used for the type split in the statistics panel, where the segments are
     * about what the cards *are* — colouring that by deck section, as it was,
     * quietly said the wrong thing.
     */
    val Monster = Color(0xFF3B82F6)
    val Spell = Color(0xFF10B981)
    val Trap = Color(0xFFA855F7)

    // Section accents, drawn from the same three hues so the panes stay
    // distinguishable without introducing colours the original never had.
    val MainAccent = Monster
    val ExtraAccent = Trap
    val SideAccent = Spell

    /** Monster attributes, for the statistics breakdown. */
    val AttributeDark = Color(0xFF7C3AED)
    val AttributeLight = Color(0xFFFBBF24)
    val AttributeWater = Color(0xFF0EA5E9)
    val AttributeFire = Color(0xFFEF4444)
    val AttributeEarth = Color(0xFF92400E)
    val AttributeWind = Color(0xFF22C55E)
    val AttributeDivine = Color(0xFFFCD34D)
}

private val DarkColors = darkColorScheme(
    primary = MasterToolPalette.Accent,
    onPrimary = Color.White,
    secondary = MasterToolPalette.Info,
    onSecondary = Color.White,
    background = MasterToolPalette.Background,
    onBackground = MasterToolPalette.Text,
    surface = MasterToolPalette.Surface,
    onSurface = MasterToolPalette.Text,
    surfaceVariant = MasterToolPalette.SurfaceRaised,
    onSurfaceVariant = MasterToolPalette.TextMuted,
    outline = MasterToolPalette.Line,
    error = MasterToolPalette.Danger,
)

/** The original's `[data-theme="light"]` block, same slate ramp inverted. */
private val LightColors = lightColorScheme(
    primary = MasterToolPalette.AccentDeep,
    onPrimary = Color.White,
    secondary = MasterToolPalette.Info,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    error = MasterToolPalette.Danger,
)

/**
 * Type scale tuned for arm's length on a 14-inch tablet: larger base sizes than
 * the Material defaults, with tight tracking on headings so long card names stay
 * on one line.
 *
 * The original set Inter, which is not bundled here — adding a font resource
 * would mean taking back the Compose resources dependency this project
 * deliberately dropped, for a face most platforms already approximate. The
 * colour system is what reads as the original; the letterforms are not.
 */
private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp),
    )
}

/** `--radius-sm` through `--radius-xl`. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun MasterToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
