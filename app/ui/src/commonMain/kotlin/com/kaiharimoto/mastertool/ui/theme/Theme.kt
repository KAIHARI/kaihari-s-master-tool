package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.prefs.ThemeChoice

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

    /**
     * What a deck pane's cards lie on.
     *
     * Between [Background] and [Surface], and warmer than either: a mat has to
     * read as a thing placed on the desk rather than as another panel of the
     * same application.
     */
    val Mat = Color(0xFF16203A)

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

private val SwissScheme = darkColorScheme(
    primary = SwissColors.accent,
    onPrimary = Color.White,
    secondary = MasterToolPalette.Info,
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = MasterToolPalette.Danger,
)

/** Leather, and gold to read against it. */
private val ClassicScheme = darkColorScheme(
    primary = ClassicColors.accent,
    onPrimary = Color(0xFF1A0F02),
    secondary = Color(0xFFC47832),
    onSecondary = Color(0xFF1A0F02),
    background = Color(0xFF140C04),
    onBackground = Color(0xFFF5E3B8),
    surface = Color(0xFF231607),
    onSurface = Color(0xFFF5E3B8),
    surfaceVariant = Color(0xFF3A2409),
    onSurfaceVariant = Color(0xFFB08C4A),
    outline = Color(0xFF4A2C00),
    error = MasterToolPalette.Danger,
)

/**
 * Cyan on black, with magenta where the original put it.
 *
 * `onPrimary` is near-black rather than white: cyan is bright enough that white
 * text on it is unreadable, which the original never had to find out because it
 * only ever used its accent as a border colour.
 */
private val CyberScheme = darkColorScheme(
    primary = CyberColors.accent,
    onPrimary = Color(0xFF03060A),
    secondary = Color(0xFFFF00FF),
    onSecondary = Color(0xFF03060A),
    background = Color(0xFF050508),
    onBackground = Color(0xFFB9FFF0),
    surface = Color(0xFF0A0A10),
    onSurface = Color(0xFFB9FFF0),
    surfaceVariant = Color(0xFF13131E),
    onSurfaceVariant = Color(0xFF4FBFA6),
    outline = Color(0xFF241A30),
    error = Color(0xFFFF3B6B),
)

/** The original's light block, on the same slate ramp. */
private val DaylightScheme = lightColorScheme(
    primary = DaylightColors.accent,
    onPrimary = Color.White,
    secondary = MasterToolPalette.Info,
    background = Color(0xFFF1F3F7),
    onBackground = Color(0xFF1B2330),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2330),
    surfaceVariant = Color(0xFFE8ECF3),
    onSurfaceVariant = Color(0xFF63708A),
    outline = Color(0xFFD7DCE5),
    error = Color(0xFFDC2626),
)

private fun schemeFor(theme: ThemeChoice) = when (theme) {
    ThemeChoice.SWISS -> SwissScheme
    ThemeChoice.CLASSIC -> ClassicScheme
    ThemeChoice.CYBER -> CyberScheme
    ThemeChoice.DAYLIGHT -> DaylightScheme
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
    theme: ThemeChoice = ThemeChoice.SWISS,
    content: @Composable () -> Unit,
) {
    // Both provided together, always. A Material scheme from one theme under the
    // surface colours of another is a state worth making unreachable.
    CompositionLocalProvider(LocalMasterToolColors provides colorsFor(theme)) {
        MaterialTheme(
            colorScheme = schemeFor(theme),
            // Built in composition rather than as a constant: the faces are
            // bundled resources, and loading one is a composable call.
            typography = masterToolTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
