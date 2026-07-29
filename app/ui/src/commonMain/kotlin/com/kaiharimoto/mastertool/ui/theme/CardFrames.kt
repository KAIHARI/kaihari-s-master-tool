package com.kaiharimoto.mastertool.ui.theme

import androidx.compose.ui.graphics.Color
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.visual.FrameKind

/**
 * The four colours a card frame is drawn from.
 *
 * [ink] is chosen per frame rather than per theme, because the frame is the same
 * colour whatever the application is wearing — and one of them is nearly black,
 * which is the case that made this a field rather than a guess. Checked by
 * `CardFrameContrastTest`.
 */
data class FrameColors(
    val light: Color,
    val base: Color,
    val dark: Color,
    val ink: Color,
)

/**
 * What each kind of card is printed in.
 *
 * Taken from the game's own frames rather than from this application's palette:
 * a player reads *orange means it has an effect* faster than they read the card's
 * name, and that reflex is worth more than a house style. Deepened and slightly
 * desaturated from the real thing so forty of them touching sit under the
 * program's own colours instead of fighting them.
 */
object CardFrames {

    fun of(kind: FrameKind): FrameColors = when (kind) {
        FrameKind.NORMAL -> Normal
        FrameKind.EFFECT -> Effect
        FrameKind.RITUAL -> Ritual
        FrameKind.FUSION -> Fusion
        FrameKind.SYNCHRO -> Synchro
        FrameKind.XYZ -> Xyz
        FrameKind.LINK -> Link
        FrameKind.SPELL -> Spell
        FrameKind.TRAP -> Trap
        FrameKind.TOKEN -> Token
        FrameKind.OTHER -> Other
    }

    val Normal = FrameColors(Color(0xFFD2B176), Color(0xFFC9A25C), Color(0xFFA1824A), Color(0xFF42351E))
    val Effect = FrameColors(Color(0xFFC07D5A), Color(0xFFB4643A), Color(0xFF90502E), Color(0xFF000000))
    val Ritual = FrameColors(Color(0xFF75A1D2), Color(0xFF5B8FC9), Color(0xFF4972A1), Color(0xFF131D29))
    val Fusion = FrameColors(Color(0xFF986DB6), Color(0xFF8451A8), Color(0xFF6A4186), Color(0xFFF5F0F8))
    val Synchro = FrameColors(Color(0xFFDED9CD), Color(0xFFD8D2C4), Color(0xFFADA89D), Color(0xFF54524C))

    /** The one that needs light ink, and the first reason [FrameColors.ink] exists. */
    val Xyz = FrameColors(Color(0xFF4C4C54), Color(0xFF2A2A34), Color(0xFF22222A), Color(0xFFA0A0A5))

    val Link = FrameColors(Color(0xFF416A93), Color(0xFF1D4E7E), Color(0xFF173E65), Color(0xFFB9C8D7))
    val Spell = FrameColors(Color(0xFF38A293), Color(0xFF12907F), Color(0xFF0E7366), Color(0xFF020C0B))
    val Trap = FrameColors(Color(0xFFB65D91), Color(0xFFA83E7C), Color(0xFF863263), Color(0xFFF7EDF3))
    val Token = FrameColors(Color(0xFFAAAAAA), Color(0xFF9A9A9A), Color(0xFF7B7B7B), Color(0xFF2B2B2B))

    /** A frame this program has never heard of. Deliberately unlike the others. */
    val Other = FrameColors(Color(0xFF747D8E), Color(0xFF5A6478), Color(0xFF485060), Color(0xFFEAECEE))

    /**
     * The dot in the corner of the art window.
     *
     * The same seven colours the statistics panel breaks a deck down by, so the
     * two agree about what FIRE looks like.
     */
    fun attribute(attribute: Attribute): Color? = when (attribute) {
        Attribute.DARK -> MasterToolPalette.AttributeDark
        Attribute.LIGHT -> MasterToolPalette.AttributeLight
        Attribute.WATER -> MasterToolPalette.AttributeWater
        Attribute.FIRE -> MasterToolPalette.AttributeFire
        Attribute.EARTH -> MasterToolPalette.AttributeEarth
        Attribute.WIND -> MasterToolPalette.AttributeWind
        Attribute.DIVINE -> MasterToolPalette.AttributeDivine
        Attribute.UNKNOWN -> null
    }

    /**
     * How far the art window is pulled towards the card's attribute.
     *
     * Enough that forty cards read as forty cards rather than as nine identical
     * orange ones — a FIRE monster is warm, a DARK one is not — and not so far
     * that the frame stops meaning what it means. At 0.42 a WIND Synchro came out
     * green, which is what *spell* looks like here; the frame has to win.
     */
    const val AttributeTint = 0.26f

    /**
     * The gold of a level star, which is the same on every frame.
     *
     * Ringed in the frame's own [FrameColors.ink] rather than left as a bare
     * disc: gold on the gold Normal frame and on the cream Synchro one is a star
     * nobody can count, and the ink is by construction the one colour that reads
     * on every frame there is.
     */
    val Pip = Color(0xFFF2B33D)
}
