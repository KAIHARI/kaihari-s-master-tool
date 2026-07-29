package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.kaiharimoto.mastertool.core.deck.MatChoice
import com.kaiharimoto.mastertool.core.prefs.ThemeChoice
import com.kaiharimoto.mastertool.core.visual.Contrast
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every cloth, under every theme, with writing somebody can read on it.
 *
 * This exists because the same mistake was made twice and caught both times by
 * looking at a picture. A deck brings its own mat now, so it can be lighter than
 * the application is wearing — and the showcase's caption and its save tab were
 * both coloured for the theme rather than for the cloth underneath them. On the
 * bone mat that is writing the colour of the surface it sits on.
 *
 * Looking found it. Nothing would have found it again, and there are seven mats
 * and four themes, which is twenty-eight combinations nobody is going to open
 * one at a time.
 */
class MatContrastTest {

    private val cloths: List<Pair<String, MatColors>> =
        ThemeChoice.entries.flatMap { theme ->
            val colors = colorsFor(theme)
            MatChoice.entries.map { choice ->
                "${choice.name} under ${theme.name}" to DeckMats.of(choice, colors)
            }
        }

    @Test
    fun everyMatCanBeWrittenOn() {
        cloths.forEach { (what, mat) ->
            val ratio = Contrast.ratio(mat.ink.toArgb(), mat.base.toArgb())
            assertTrue(
                ratio >= Contrast.READABLE,
                "$what: ink is ${ratio.rounded()}:1 against the cloth",
            )
        }
    }

    @Test
    fun theQuieterWritingIsStillWriting() {
        // Counts and captions, which are allowed to be quieter than body text
        // and are not allowed to disappear.
        cloths.forEach { (what, mat) ->
            val ratio = Contrast.ratio(mat.inkQuiet.toArgb(), mat.base.toArgb())
            assertTrue(
                ratio >= Contrast.READABLE_LARGE,
                "$what: the quiet ink is ${ratio.rounded()}:1 against the cloth",
            )
        }
    }

    @Test
    fun theTwoInksAreDifferentEnoughToBeWorthHaving() {
        // If they were the same there would be no quiet, and the caption line
        // would read as two halves of one sentence rather than as a name and a
        // set of counts.
        cloths.forEach { (what, mat) ->
            assertTrue(
                Contrast.ratio(mat.ink.toArgb(), mat.inkQuiet.toArgb()) > 1.2,
                "$what: the two inks are the same colour",
            )
        }
    }

    @Test
    fun aMatKnowsWhichWayItRuns() {
        // The save tab picks its backing from whether the ink is light, so the
        // ink and the cloth must never both be light or both be dark. This is
        // the assumption that rule rests on, stated where it can fail loudly.
        cloths.forEach { (what, mat) ->
            val inkIsLight = Contrast.relativeLuminance(mat.ink.toArgb()) > 0.5
            val clothIsLight = Contrast.relativeLuminance(mat.base.toArgb()) > 0.5
            assertTrue(inkIsLight != clothIsLight, "$what: the ink and the cloth agree")
        }
    }

    private fun Double.rounded(): String {
        val tenths = (this * 10).toInt()
        return "${tenths / 10}.${tenths % 10}"
    }
}
