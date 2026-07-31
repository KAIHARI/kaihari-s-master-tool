package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.prefs.ThemeMode
import com.kaiharimoto.mastertool.ui.art.Res
import com.kaiharimoto.mastertool.ui.art.archivo_bold
import com.kaiharimoto.mastertool.ui.art.archivo_expanded_semibold
import com.kaiharimoto.mastertool.ui.art.archivo_medium
import com.kaiharimoto.mastertool.ui.art.archivo_regular
import com.kaiharimoto.mastertool.ui.art.archivo_semibold
import com.kaiharimoto.mastertool.ui.art.inter_medium
import org.jetbrains.compose.resources.Font

/**
 * Swiss Prismatic, second edition.
 *
 * Sharp white on true black. Colour is reserved for two jobs: meaning (card
 * types, attributes, legality) and light (the prismatic ramp, which appears
 * only as chromatic fringing on things being interacted with). Everything else
 * is achromatic, which is what makes the fringes read as light rather than as
 * decoration.
 *
 * Light mode is the exact inversion — sharp black on white — with the *same*
 * chromatic colours; only the compositing flips (additive fringes on black,
 * multiplied fringes on white — see Prismatic.kt).
 *
 * Geometry stays close to square on purpose — cards are rectangles, and a deck
 * builder full of heavily rounded chrome fights the content it is displaying.
 */
object MasterToolPalette {
    // The interactive accent is indigo-white: bright enough to read on black
    // chrome, quiet enough not to compete with card art.
    val Accent = Color(0xFFB8BEFF)
    val AccentBright = Color(0xFFD6D9FF)
    val AccentDeep = Color(0xFF6A72E8)

    // True-black stage with near-neutral lifts. Surfaces are separated by
    // luminance alone; hairlines do the rest.
    val Background = Color(0xFF060608)
    val Surface = Color(0xFF0E0E12)
    val SurfaceRaised = Color(0xFF1A1A21)
    val SurfaceHigh = Color(0xFF26262F)

    val Line = Color(0xFF26262F)
    val LineLight = Color(0xFF3A3A46)

    val Text = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF9C9CAB)
    val Ink = Color(0xFF060608)

    val Success = Color(0xFF2CE08B)
    val Warning = Color(0xFFFFB020)
    val Danger = Color(0xFFFF4D4D)
    val Info = Color(0xFF39B8FF)

    /**
     * The prismatic ramp: the six hues chromatic aberration splits white into.
     *
     * Used for fringe drawing (Prismatic.kt) and as the palette user-defined
     * deck groups pick from — indices into this list, never raw hex, so groups
     * stay coherent when the theme evolves.
     */
    val Prism = listOf(
        Color(0xFFFF4D6A), // red
        Color(0xFFFFB020), // amber
        Color(0xFF2CE08B), // green
        Color(0xFF35E0FF), // cyan
        Color(0xFF8A7CFF), // violet
        Color(0xFFFF5FD2), // magenta
    )

    /** The two fringe primaries for the aberration effect itself. */
    val FringeWarm = Color(0xFFFF3B4D)
    val FringeCool = Color(0xFF2BD9FF)

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

/**
 * True when the current scheme is the dark (white-on-black) one.
 *
 * The prismatic primitives need this — the fringes are additive on black and
 * multiplied on white — and reading it from a local keeps them plain modifier
 * functions instead of composables.
 */
val LocalDarkTheme = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    // White is the accent: primary actions are white on black, the sharpest
    // contrast the screen can produce. Colour is spent elsewhere.
    primary = MasterToolPalette.Text,
    onPrimary = MasterToolPalette.Ink,
    secondary = MasterToolPalette.Accent,
    onSecondary = MasterToolPalette.Ink,
    background = MasterToolPalette.Background,
    onBackground = MasterToolPalette.Text,
    surface = MasterToolPalette.Surface,
    onSurface = MasterToolPalette.Text,
    surfaceVariant = MasterToolPalette.SurfaceRaised,
    onSurfaceVariant = MasterToolPalette.TextMuted,
    outline = MasterToolPalette.Line,
    error = MasterToolPalette.Danger,
)

/** The exact inversion: sharp black on white, same chromatic colours. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = MasterToolPalette.AccentDeep,
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0D),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF0A0A0D),
    surfaceVariant = Color(0xFFECECF0),
    onSurfaceVariant = Color(0xFF5C5C6B),
    outline = Color(0xFFD8D8E0),
    error = MasterToolPalette.Danger,
)

/**
 * Archivo, bundled.
 *
 * A grotesk with punchy caps and solid numerals — ratios, odds and copy counts
 * are half of what this app sets in type. The expanded cut is a separate
 * family used only for display moments (the wordmark, big numerals), where its
 * width is the identity.
 */
@Composable
fun archivoFamily(): FontFamily = FontFamily(
    Font(Res.font.archivo_regular, FontWeight.Normal),
    Font(Res.font.archivo_medium, FontWeight.Medium),
    Font(Res.font.archivo_semibold, FontWeight.SemiBold),
    Font(Res.font.archivo_bold, FontWeight.Bold),
)

@Composable
fun archivoExpandedFamily(): FontFamily = FontFamily(
    Font(Res.font.archivo_expanded_semibold, FontWeight.SemiBold),
)

/**
 * The wordmark's face: a neo-grotesque at Medium, set at its natural tracking.
 *
 * Helvetica Neue is the reference and cannot be shipped — it is Linotype's, and
 * it does not exist on Android or on most Linux desktops, so asking the system
 * for it by name would silently land on whatever that device calls its default
 * sans and the mark would look different on every screen. Inter is the closest
 * face that can travel with the app: same neo-grotesque skeleton, drawn for
 * screens, SIL OFL (see `ui/licenses/Inter-OFL.txt`). Swap the file here if a
 * licensed Helvetica Neue is ever dropped in — nothing else refers to it.
 */
@Composable
fun wordmarkFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_medium, FontWeight.Medium),
)

/**
 * Type scale tuned for arm's length on a 14-inch tablet: larger base sizes than
 * the Material defaults, with tight tracking on headings so long card names stay
 * on one line.
 */
@Composable
private fun appTypography(): Typography {
    val archivo = archivoFamily()
    val base = Typography()

    fun androidx.compose.ui.text.TextStyle.archivo() = copy(fontFamily = archivo)

    return Typography(
        displayLarge = base.displayLarge.archivo(),
        displayMedium = base.displayMedium.archivo(),
        displaySmall = base.displaySmall.archivo(),
        headlineLarge = base.headlineLarge.archivo(),
        headlineMedium = base.headlineMedium.archivo().copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.archivo().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.archivo().copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp,
        ),
        titleMedium = base.titleMedium.archivo().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.archivo().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.archivo(),
        bodyMedium = base.bodyMedium.archivo().copy(fontSize = 15.sp),
        bodySmall = base.bodySmall.archivo(),
        labelLarge = base.labelLarge.archivo().copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        ),
        labelMedium = base.labelMedium.archivo(),
        labelSmall = base.labelSmall.archivo(),
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
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
