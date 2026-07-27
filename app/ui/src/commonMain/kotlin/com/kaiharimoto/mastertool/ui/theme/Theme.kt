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
 * "Tournament table" palette: obsidian slate carrying a single hot gold accent,
 * the colour of a Millennium item. Committing to one dominant hue with one sharp
 * accent reads as deliberate, where an evenly spread palette reads as a default.
 *
 * Geometry is close to square on purpose — cards are rectangles, and a deck
 * builder full of heavily rounded chrome fights the content it is displaying.
 */
object MasterToolPalette {
    val Gold = Color(0xFFE0A82E)
    val GoldBright = Color(0xFFF5C860)
    val Obsidian = Color(0xFF0E1116)
    val Slate = Color(0xFF161B22)
    val SlateRaised = Color(0xFF1F262F)
    val Line = Color(0xFF2C343F)
    val Parchment = Color(0xFFF2ECDF)
    val Ink = Color(0xFF11151A)

    // Section accents. Players read deck panes by colour before they read labels.
    val MainAccent = Color(0xFF4C8DF6)
    val ExtraAccent = Color(0xFF8B5CF6)
    val SideAccent = Color(0xFF34D399)

    val Danger = Color(0xFFE5484D)
    val Warning = Color(0xFFE0A82E)
}

private val DarkColors = darkColorScheme(
    primary = MasterToolPalette.Gold,
    onPrimary = MasterToolPalette.Ink,
    secondary = MasterToolPalette.MainAccent,
    onSecondary = MasterToolPalette.Ink,
    background = MasterToolPalette.Obsidian,
    onBackground = MasterToolPalette.Parchment,
    surface = MasterToolPalette.Slate,
    onSurface = MasterToolPalette.Parchment,
    surfaceVariant = MasterToolPalette.SlateRaised,
    onSurfaceVariant = Color(0xFFB6BFCC),
    outline = MasterToolPalette.Line,
    error = MasterToolPalette.Danger,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A12),
    onPrimary = Color.White,
    secondary = Color(0xFF2C6BD1),
    background = MasterToolPalette.Parchment,
    onBackground = MasterToolPalette.Ink,
    surface = Color(0xFFFBF7EE),
    onSurface = MasterToolPalette.Ink,
    outline = Color(0xFFD3C9B4),
    error = MasterToolPalette.Danger,
)

/**
 * Type scale tuned for arm's length on a 14-inch tablet: larger base sizes than
 * the Material defaults, with tight tracking on headings so long card names stay
 * on one line.
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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
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
