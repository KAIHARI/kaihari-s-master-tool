package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.ui.art.Res
import com.kaiharimoto.mastertool.ui.art.instrument_sans_bold
import com.kaiharimoto.mastertool.ui.art.instrument_sans_italic
import com.kaiharimoto.mastertool.ui.art.instrument_sans_regular
import com.kaiharimoto.mastertool.ui.art.jetbrains_mono_bold
import com.kaiharimoto.mastertool.ui.art.jetbrains_mono_regular
import com.kaiharimoto.mastertool.ui.art.tektur_medium
import org.jetbrains.compose.resources.Font

/**
 * The reading face: Instrument Sans.
 *
 * A grotesque, which is what the original asked for — its stylesheet requested
 * `'Helvetica Neue Haas Grotesk'` and never loaded it, so every install
 * silently fell back to Inter, a face the project's own guidelines rule out.
 * This is that intent actually shipped.
 *
 * Tight is the reason for this one in particular rather than any other
 * grotesque. Card names run long — "Original Sinful Spoils — Snake-Eye" — and a
 * deck pane at thirteen columns is narrow. Width is a feature here.
 */
@Composable
fun readingFamily() = FontFamily(
    Font(Res.font.instrument_sans_regular, FontWeight.Normal),
    Font(Res.font.instrument_sans_bold, FontWeight.Bold),
    Font(Res.font.instrument_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * The counting face: JetBrains Mono, tracked in tight.
 *
 * For anything that is a quantity rather than a word — deck counts, ATK and
 * DEF, levels, opening-hand odds. The original had exactly this idea and called
 * the class `font-mono-tactical`, at `-0.05em`; the tracking is what stops a
 * monospace face reading as a code listing.
 *
 * It earns its place beyond style: a count that changes from 39 to 40 while you
 * watch should not reflow the row it sits in.
 */
@Composable
fun countingFamily() = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** The wordmark, and nothing else. Angular enough that a second use would shout. */
@Composable
fun displayFamily() = FontFamily(Font(Res.font.tektur_medium, FontWeight.Medium))

/** Counts, stats and odds. Not part of [Typography], because Material has no slot for it. */
@Composable
fun tacticalStyle(): TextStyle = TextStyle(
    fontFamily = countingFamily(),
    fontSize = 11.sp,
    letterSpacing = (-0.5).sp,
    fontWeight = FontWeight.Normal,
)

/**
 * The scale, sized for arm's length on a fourteen-inch tablet.
 *
 * Larger than the Material defaults at the bottom end, and tracked tighter at
 * the top: negative tracking on headings is what keeps a long card name on one
 * line, which matters more here than in most applications because the names are
 * not ours to shorten.
 */
@Composable
fun masterToolTypography(): Typography {
    val reading = readingFamily()
    val base = Typography()

    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = reading, letterSpacing = (-1).sp),
        displayMedium = base.displayMedium.copy(fontFamily = reading, letterSpacing = (-0.75).sp),
        displaySmall = base.displaySmall.copy(fontFamily = reading, letterSpacing = (-0.5).sp),

        headlineLarge = base.headlineLarge.copy(fontFamily = reading, letterSpacing = (-0.75).sp),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = reading,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = reading,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),

        titleLarge = base.titleLarge.copy(
            fontFamily = reading,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.35).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = reading,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        titleSmall = base.titleSmall.copy(fontFamily = reading, fontWeight = FontWeight.Bold),

        bodyLarge = base.bodyLarge.copy(fontFamily = reading),
        bodyMedium = base.bodyMedium.copy(fontFamily = reading, fontSize = 15.sp),
        bodySmall = base.bodySmall.copy(fontFamily = reading),

        // Card names live here, at whatever size a tile happens to be. Tight
        // tracking buys most of a character per line.
        labelLarge = base.labelLarge.copy(
            fontFamily = reading,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = base.labelMedium.copy(fontFamily = reading, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(fontFamily = reading, letterSpacing = (-0.1).sp),
    )
}
