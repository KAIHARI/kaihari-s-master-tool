package com.kaiharimoto.mastertool.core.visual

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything a card needs to be drawn as foil, for one position of the pointer.
 *
 * Angles are degrees. Positions are fractions of the card, so a tile in a deck
 * pane and a full-size preview of the same card shade identically.
 */
data class FoilShading(
    /** Direction of the holographic band across the card. */
    val gradientAngleDegrees: Float,
    /** Tilt towards the viewer, matching CSS `rotateX` / Compose `rotationX`. */
    val rotationXDegrees: Float,
    val rotationYDegrees: Float,
    /** Where the specular highlight sits, 0..1 from the top-left corner. */
    val specularX: Float,
    val specularY: Float,
    /** Colour of that highlight. Channels are 0..1. */
    val specularRed: Float,
    val specularGreen: Float,
    val specularBlue: Float,
) {
    companion object {
        /** A card lying flat and unlit — what to draw when nothing is pointing at it. */
        val FLAT = FoilShading(
            gradientAngleDegrees = BASE_ANGLE,
            rotationXDegrees = 0f,
            rotationYDegrees = 0f,
            specularX = 0.5f,
            specularY = 0.5f,
            specularRed = 1f,
            specularGreen = 182f / 255f,
            specularBlue = 193f / 255f,
        )
    }
}

/** Where the holographic band sits with the pointer dead centre. */
private const val BASE_ANGLE = 135f

/**
 * How a foil card catches the light.
 *
 * Ported from the tool this replaces, whose card hover is the one thing in it
 * that no other deck builder has. Three behaviours together are what make it
 * read as foil rather than as a gradient that moves:
 *
 *  - the band's angle swings with the pointer, about forty degrees either way;
 *  - the highlight sits *opposite* the pointer, because that is where a light
 *    source reflects off a surface you are leaning over;
 *  - its colour runs pink to cyan as a function of that angle, so the card
 *    changes hue as it turns rather than merely brightening.
 *
 * The original drove tilt from raw pixel offsets — `(pointerY - centreY) / 4` —
 * which ties the tilt to the card's size. That is fine when every card on screen
 * is 68px wide, and wrong here, where the same card is drawn as a deck tile and
 * again as a 280dp preview: the preview would have tilted three times as far.
 * The maxima below are that divisor evaluated at the original's card size, now
 * applied to a normalised offset so every card tilts the same amount.
 */
object FoilMath {

    /** `68px / 2 / 4`, the original's horizontal tilt at the edge of its card. */
    private const val MAX_ROTATION_Y = 8.5f

    /** The same divisor over the card's height, `99px / 2 / 4`. */
    private const val MAX_ROTATION_X = 12.4f

    private const val ANGLE_SWING_X = 25f
    private const val ANGLE_SWING_Y = 15f

    // The pink and cyan the band runs between, and the window of gradient angle
    // over which it crosses from one to the other.
    private const val ANGLE_AT_PINK = 95f
    private const val ANGLE_SPAN = 80f
    private val PINK = Triple(255f, 182f, 193f)
    private val CYAN = Triple(0f, 255f, 255f)

    /**
     * Shading for a pointer at ([pointerX], [pointerY]) over a card of [width] by
     * [height], all in the same units.
     *
     * A pointer outside the card is clamped to its edge rather than extrapolated:
     * during a drag the pointer leaves the card it picked up, and letting the
     * angle run on would spin the band far past anything a real card does.
     */
    fun shade(pointerX: Float, pointerY: Float, width: Float, height: Float): FoilShading {
        if (width <= 0f || height <= 0f) return FoilShading.FLAT

        // 0..1 across the card, then -1..1 from its centre.
        val u = (pointerX / width).coerceIn(0f, 1f)
        val v = (pointerY / height).coerceIn(0f, 1f)
        val offsetX = u * 2f - 1f
        val offsetY = v * 2f - 1f

        val angle = BASE_ANGLE + offsetX * ANGLE_SWING_X - offsetY * ANGLE_SWING_Y
        val (red, green, blue) = tintFor(angle)

        return FoilShading(
            gradientAngleDegrees = angle,
            // Pointer below the centre tips the top away, as leaning over a card does.
            rotationXDegrees = offsetY * MAX_ROTATION_X,
            rotationYDegrees = -offsetX * MAX_ROTATION_Y,
            // Mirrored: the reflection is on the far side from where you are looking.
            specularX = 1f - u,
            specularY = 1f - v,
            specularRed = red,
            specularGreen = green,
            specularBlue = blue,
        )
    }

    /**
     * Shading for a card nobody is pointing at, at [progress] through one cycle.
     *
     * Touch has no hover, so on a tablet the foil would only ever be seen while a
     * card was under a finger — which is exactly when it is hidden by one. This
     * walks a virtual pointer in a slow ellipse instead, reusing [shade] so the
     * idle sweep and a real pointer cannot drift apart.
     */
    fun sweep(progress: Float, width: Float, height: Float): FoilShading {
        val theta = progress.mod(1f) * 2f * PI.toFloat()
        // Kept off the edges: cornering the pointer would snap the band between
        // its extremes once per cycle instead of gliding.
        val u = 0.5f + 0.34f * cos(theta)
        val v = 0.5f + 0.34f * sin(theta)
        return shade(u * width, v * height, width, height)
    }

    /**
     * The highlight colour at a given band angle: pink where the band is shallow,
     * cyan where it is steep, clamped at both ends.
     */
    private fun tintFor(angleDegrees: Float): Triple<Float, Float, Float> {
        val mix = ((angleDegrees - ANGLE_AT_PINK) / ANGLE_SPAN).coerceIn(0f, 1f)
        return Triple(
            lerp(PINK.first, CYAN.first, mix) / 255f,
            lerp(PINK.second, CYAN.second, mix) / 255f,
            lerp(PINK.third, CYAN.third, mix) / 255f,
        )
    }

    private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction
}
