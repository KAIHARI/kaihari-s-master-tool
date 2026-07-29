package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.visual.CardFace
import com.kaiharimoto.mastertool.core.visual.FrameKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.kaiharimoto.mastertool.core.visual.Contrast

/**
 * Every frame with a name somebody can read on it.
 *
 * The same guard as `MatContrastTest`, for the same reason and found the same
 * way. The first drawing of these used one ink colour for all of them, which is
 * fine on nine frames and unreadable on the tenth: an Xyz card is near-black, and
 * dark ink on it is a card with no name. Looking at a picture found it; nothing
 * else would have, and there are eleven frames and three pieces of writing on
 * each.
 */
class CardFrameContrastTest {

    private val frames = FrameKind.entries.map { it to CardFrames.of(it) }

    @Test
    fun everyFrameCanBeWrittenOn() {
        frames.forEach { (kind, frame) ->
            // Against the base, which is what most of the name sits over. The
            // gradient runs light-to-dark, so the two ends are checked below.
            val ratio = Contrast.ratio(frame.ink.toArgb(), frame.base.toArgb())
            assertTrue(
                ratio >= Contrast.READABLE,
                "$kind: ink is ${(ratio * 10).toInt() / 10.0}:1 on the frame",
            )
        }
    }

    @Test
    fun theNameIsReadableAtBothEndsOfTheFrameGradient() {
        // The name band crosses the light end and the footer the dark end, so a
        // ratio that only holds in the middle is a card whose name fades out.
        frames.forEach { (kind, frame) ->
            listOf("light" to frame.light, "dark" to frame.dark).forEach { (where, colour) ->
                val ratio = Contrast.ratio(frame.ink.toArgb(), colour.toArgb())
                assertTrue(
                    ratio >= Contrast.READABLE_LARGE,
                    "$kind: ink is ${(ratio * 10).toInt() / 10.0}:1 at the $where end",
                )
            }
        }
    }

    @Test
    fun noTwoFramesAreTheSameColour() {
        // The whole point of drawing the frame is telling a spell from a trap
        // across the table without reading either.
        val bases = frames.map { it.second.base }

        assertEquals(bases.size, bases.distinct().size)
    }

    @Test
    fun aLevelStarShowsUpOnEveryFrame() {
        // Not the gold against the frame -- on the gold Normal frame and the
        // cream Synchro one that is 1.3:1, which is why the star is ringed in
        // the frame's ink instead. So this asks what the ring asks.
        frames.forEach { (kind, frame) ->
            val ratio = Contrast.ratio(frame.ink.toArgb(), frame.base.toArgb())
            assertTrue(ratio >= Contrast.READABLE, "$kind: the stars disappear into the frame")
        }
    }

    @Test
    fun everyAttributeHasAColourExceptTheOneThatIsNotAnAttribute() {
        Attribute.entries.filter { it != Attribute.UNKNOWN }.forEach {
            assertNotNull(CardFrames.attribute(it), "$it")
        }

        // A card whose attribute the database did not say draws no dot at all,
        // rather than a grey one that reads as a tenth attribute.
        assertNull(CardFrames.attribute(Attribute.UNKNOWN))
    }

    @Test
    fun theFrameAlwaysWinsOverTheAttribute() {
        // The art window is pulled towards the card's attribute so that forty
        // effect monsters are not a wall of one colour. Past halfway the pull
        // stops being a tint and starts being the colour, and the frame stops
        // meaning what it means -- which is how a WIND Synchro came out looking
        // like a spell the first time this was tried.
        assertTrue(
            CardFrames.AttributeTint < 0.5f,
            "at ${CardFrames.AttributeTint} the attribute would outweigh the frame",
        )
        assertTrue(CardFrames.AttributeTint > 0f, "a tint of nothing is not a tint")
    }

    @Test
    fun everyFrameKindHasAFrame() {
        // `of` is a `when` over the enum, so this is really about the mapping
        // from the database's strings staying pointed at something drawable.
        listOf("normal", "effect", "spell", "trap", "xyz", "link", "skill", "").forEach {
            CardFrames.of(CardFace.kindOf(it))
        }
    }
}
