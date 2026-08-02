package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * A light on the stage.
 *
 * [direction] is the way the light *travels*, so a key light above and in front
 * of the table points down and into the screen. Storing it that way rather than
 * as "the direction of the light source" is the one convention that makes both
 * users of it read naturally: shading wants the vector reversed, and the shadow
 * caster wants it exactly as it is.
 */
data class Light(
    val direction: Vec3,
    val intensity: Float = 1f,
    /**
     * What a surface facing away from the key still receives.
     *
     * High, and on purpose. This is a black stage: a card is the brightest
     * object in the frame and dropping it to a quarter brightness because it
     * tilted does not read as shading, it reads as a bug. The range that is
     * left is enough, because the specular term is doing the talking.
     */
    val ambient: Float = 0.72f,
) {
    /** From the surface toward the light — the vector every shading term wants. */
    val toLight: Vec3 = (-direction).normalised()
}

/**
 * What one surface does with the light, ready to paint.
 *
 * Everything is 0..1 and nothing here knows what a colour is. That split is
 * deliberate: the renderer decides that a highlight is white and a fringe comes
 * off the prismatic ramp, and this decides *how much*, so the numbers can be
 * tested without a screen.
 */
data class Shade(
    /** Ambient and lambert together: what to multiply the surface's own colour by. */
    val diffuse: Float,
    /** The highlight's strength at its centre. */
    val specular: Float,
    /** The grazing-angle rim, brightest when the surface is nearly edge-on. */
    val fresnel: Float,
    /**
     * Where the highlight sits on the face, in 0..1 across it.
     *
     * Free to fall outside that range, which is not a bug to clamp away — a
     * highlight that has slid off the edge of the card is the correct answer,
     * and clamping it would pin a bright pool to the border instead.
     */
    val hotspot: Vec2,
    /** Positive when the printed face is toward the eye, negative when the back is. */
    val facing: Float,
)

object Shading {

    /**
     * How far the highlight travels across the face for a given tilt.
     *
     * A tangent would be the honest answer and it runs to infinity at grazing
     * angles; this is the same curve near the middle with the tail cut off by
     * [MIN_FACING], which is what keeps a card turning edge-on from throwing
     * its highlight a thousand pixels sideways.
     */
    private const val SPREAD = 1.15f
    private const val MIN_FACING = 0.32f

    /** Plain diffuse lighting for a surface pointing along [normal]. */
    fun lit(normal: Vec3, light: Light, intensity: Float = 1f): Float {
        val lambert = max(0f, normal.normalised() dot light.toLight)
        return (light.ambient + (1f - light.ambient) * lambert * light.intensity * intensity)
            .coerceIn(0f, 1f)
    }

    /**
     * A whole card, shaded.
     *
     * The visible side is whichever one is pointing at [eye], so a face-down
     * card is lit as the back it is showing rather than as a face nobody can
     * see — which matters, because a set card and a face-up one at the same
     * angle should not have their highlights in the same place.
     */
    fun of(
        pose: Pose3,
        material: CardMaterial,
        light: Light,
        eye: Vec3 = Vec3.Toward,
    ): Shade {
        val normal = Rot3.normal(pose)
        val facing = normal dot eye
        val visible = if (facing < 0f) -normal else normal

        val lambert = max(0f, visible dot light.toLight)
        val diffuse = (light.ambient + (1f - light.ambient) * lambert * light.intensity)
            .coerceIn(0f, 1f)

        // Blinn-Phong: the half-vector between the eye and the light, which is
        // the direction a mirror at this point would have to face.
        val half = (light.toLight + eye.normalised()).normalised()
        val alignment = max(0f, visible dot half)
        val specular = if (lambert <= 0f) {
            0f
        } else {
            (alignment.pow(material.shininess) * material.specular * light.intensity)
                .coerceIn(0f, 1f)
        }

        val fresnel = ((1f - abs(facing)).pow(2.5f) * material.rim).coerceIn(0f, 1f)

        // The hotspot, in the card's own frame. Mirrored along with the card
        // when the back is showing, or the highlight would walk the wrong way
        // across a set card as it turns.
        val side = if (facing < 0f) -1f else 1f
        val right = Rot3.right(pose) * side
        val down = Rot3.down(pose)
        val depth = max(half dot visible, MIN_FACING)

        return Shade(
            diffuse = diffuse,
            specular = specular,
            fresnel = fresnel,
            hotspot = Vec2(
                x = 0.5f + SPREAD * (half dot right) / depth,
                y = 0.5f + SPREAD * (half dot down) / depth,
            ),
            facing = facing,
        )
    }
}
