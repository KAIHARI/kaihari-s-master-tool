package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec2
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

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
     * The lamp's colour temperature: -1 fully cool, 0 white, +1 fully warm.
     *
     * A number rather than a colour, because a lamp does not have a colour
     * until it lands on something — see [Lit], which is where the two are put
     * together. What this says is only which way off white the lamp is.
     */
    val warmth: Float = 0f,
    /**
     * What a surface facing away from the key still receives.
     *
     * High, and on purpose. This is a black stage: a card is the brightest
     * object in the frame and dropping it to a quarter brightness because it
     * tilted does not read as shading, it reads as a bug. The range that is
     * left is enough, because the specular term is doing the talking.
     */
    val ambient: Float = 0.72f,
    /**
     * Where the lamp is, in the mat's own frame — or null for infinitely far.
     *
     * Null is every lamp that shipped before this, and it is the only state in
     * which everything below is a no-op. That matters more than it sounds:
     * `GoldenStageTest` renders the minimal stage against [StageRig.Key], and
     * the whole of this file's new arithmetic has to be invisible to it.
     *
     * A direction says which way light *arrives* and nothing about where from,
     * which is exactly right for the sky and exactly wrong for a bulb standing
     * on a desk. Given a position the direction becomes a function of the point
     * being lit, and three things follow that a direction cannot produce:
     * shadows *diverge* instead of running parallel, the far corner of the table
     * is genuinely dimmer than the near one, and the pool on the wood has
     * somewhere to be.
     *
     * [direction] does not become redundant. It stays the answer for every call
     * with no surface point to offer, and `Scenery` derives a placed lamp's
     * direction *from* its position, so the two cannot disagree about where the
     * light is coming from.
     */
    val position: Vec3? = null,
    /** How big the source is, in mat pixels. Zero is a point, and casts hard. */
    val radius: Float = 0f,
    /**
     * How far off a source with no [position] is, in mat pixels.
     *
     * Only ever read to turn [radius] into an angle, and that is the whole of
     * its job. How soft a shadow is depends on the source's *angular* size —
     * R over L — and a directional lamp has an R with no L. This is that L,
     * stated rather than implied. Zero leaves a directional lamp a point.
     */
    val distance: Float = 0f,
) {
    /** From the surface toward the light — the vector every shading term wants. */
    val toLight: Vec3 = (-direction).normalised()

    /** The way the light travels. The expression `Shadows.cast` used to inline. */
    val travel: Vec3 = direction.normalised()

    /**
     * Toward the lamp from [at] — or the stored vector, when there is nowhere to
     * be toward.
     *
     * Returns the [toLight] **val itself** in that case rather than an equal
     * copy, which is not fastidiousness: the identity is what a test can assert
     * without a tolerance, and it is the cheapest possible proof that a scene
     * with no placed lamp is shaded by exactly the arithmetic it always was.
     */
    fun toLightFrom(at: Vec3?): Vec3 =
        if (position == null || at == null) toLight else (position - at).normalised()

    /** The way the light travels as it passes through [at]. */
    fun travelFrom(at: Vec3?): Vec3 =
        if (position == null || at == null) travel else (at - position).normalised()

    /**
     * How much of this lamp reaches [at], against the surface directly beneath it.
     *
     * Exactly the on-axis irradiance of a uniform disc of radius [radius]:
     * `E ∝ R² / (R² + d²)`. So the source's *size* and its *distance* are one
     * term rather than two guesses — it degrades to inverse-square once you are
     * well away from the bulb, and it saturates instead of diverging as you
     * reach it, which is what stops a card lifted toward the lamp from blowing
     * out.
     *
     * Normalised at the lamp's own height above the surface rather than at some
     * reference distance, and that is a decision with a number behind it: a
     * `(d₀/d)²` form normalised at the middle of the mat comes back at 1.70 at
     * the near corner, and against the night rig's 0.45 of headroom that clips
     * the whole near half of the table flat to white. Normalised at the height,
     * the clamp never engages on the felt at all — only for something lifted
     * toward the bulb, which is the one place a light is allowed to run out of
     * headroom.
     *
     * **Nothing here touches [ambient].** The falloff lands on the *directional*
     * term — on the mat, the wood and the cards — and never on the floor under
     * everything, which is what keeps [StageLighting.NIGHT_FLOOR] a guarantee
     * rather than a hope.
     *
     * The height is measured from z = 0, which is the felt, the desk top and
     * every horizontal surface this stage pools light on. It took a `surfaceZ`
     * parameter for one draft and no caller could ever pass one — [StageRig.lit]
     * has no such argument to forward — so a pool asked about a different plane
     * would have normalised at one height and sampled at another. A parameter
     * nothing can reach is worse than an absent one, because it promises.
     */
    fun attenuation(at: Vec3?): Float {
        val lamp = position ?: return 1f
        if (at == null) return 1f
        val height = lamp.z
        val size = radius * radius
        val away = at - lamp
        val span = size + (away dot away)
        // A sizeless source and a surface point standing exactly on it: the
        // ratio is 0/0, and a NaN is not light — it passes every clamp on the
        // way to the screen and draws nothing. Anything that reaches the bulb
        // saturates, so this does too.
        if (span <= 0f) return 1f
        return ((size + height * height) / span).coerceAtMost(1f)
    }

    /**
     * How much of the *room's own* light reaches [at]: the share of the ambient
     * a surface this far from the lamp still gets.
     *
     * The one place anything is allowed to touch [ambient], and it is allowed
     * because of what ambient physically is. Ambient is bounce — light that has
     * come off the walls and the ceiling and arrived from every direction at
     * once — and in a room with one lamp on a desk, every photon of that bounce
     * started at the lamp. So it cannot be the same everywhere; the far corner
     * of the room is dim for exactly the reason the far corner of the desk is.
     *
     * A **square root** of the attenuation, not the attenuation. Bounce has been
     * scattered before it arrives, so it falls off far more gently than the
     * direct beam — halving it with distance the way the beam halves would give
     * a wall that is black a card's width from the lamp, which is a cave rather
     * than a bedroom.
     *
     * It runs between [ROOM_NEAR] and [ROOM_FAR] rather than between one and
     * zero, and both ends carry an argument. The near end is below one because
     * a room lit by a desk lamp has *less* bounce in it than the same room lit
     * by a window, everywhere, including right beside the lamp — a window is
     * most of a wall and a lamp is a fist. Without that the wall behind the
     * desk came back 68 at night against 67 by day and only the far corner
     * moved. The far end is above zero because a real room is never lightless,
     * and because losing the wall entirely puts back the void that
     * `Scenery.ROOM_ABOVE` was spent on removing.
     *
     * Both are safe next to [StageLighting.NIGHT_FLOOR] rather than a way
     * around it: [StageRig.room] is the only caller and is deliberately not the
     * one that shades cards.
     *
     * One for a lamp with no place, to the bit, which is what keeps every
     * daylit and minimal surface in the app exactly as it was.
     */
    fun roomReach(at: Vec3?): Float {
        if (position == null || at == null) return 1f
        return ROOM_FAR + (ROOM_NEAR - ROOM_FAR) * sqrt(attenuation(at))
    }

    /**
     * How wide the source looks from [at], in radians.
     *
     * The one number a penumbra depends on. A placed lamp measures it against
     * the real distance; a directional one has only [distance] to divide by, and
     * a source with no size is a point from everywhere.
     */
    fun angularRadius(at: Vec3?): Float = when {
        radius <= 0f -> 0f
        // Clamped at one rather than merely guarded against a zero divisor. The
        // small-angle approximation is only an angle while it is small, and a
        // surface nearer the source than the source is wide would otherwise
        // hand a penumbra term a number with no meaning behind it.
        position != null && at != null ->
            (radius / max(EPSILON, (position - at).length)).coerceAtMost(1f)
        distance > 0f -> (radius / distance).coerceAtMost(1f)
        else -> 0f
    }

    companion object {
        /**
         * How much of the rig's ambient a room lit by a placed lamp has: beside
         * the lamp, and at the far wall.
         *
         * Measured on the wall behind the desk, whose stock renders 67 under
         * daylight. [ROOM_NEAR] takes the end of it above the lamp to 34 and
         * [ROOM_FAR] takes the far end to 18, against a stage of 6 — dark
         * enough to be night, light enough that the window frame at 27 still
         * stands out of the wall it is set in.
         */
        const val ROOM_NEAR = 0.38f
        const val ROOM_FAR = 0.14f

        /**
         * A lamp that is an object: the direction follows from where it stands.
         *
         * The one constructor a fixture should use, so that the thing drawn on
         * the desk and the thing lighting the table cannot end up in two places.
         */
        fun at(
            position: Vec3,
            aimedAt: Vec3,
            intensity: Float = 1f,
            warmth: Float = 0f,
            // No default. The primary constructor has one, and two copies of a
            // number are two numbers that can disagree; the one caller is
            // carrying a rig's own ambient across and has it to hand anyway.
            ambient: Float,
            radius: Float = 0f,
        ) = Light(
            direction = (aimedAt - position).normalised(),
            intensity = intensity,
            warmth = warmth,
            ambient = ambient,
            position = position,
            radius = radius,
        )

        /** Small enough to be nothing, large enough to divide by. */
        private const val EPSILON = 1e-4f
    }
}

/**
 * How much light a surface received, and what colour that light was.
 *
 * The temperature rides beside the scalar as one signed number rather than as
 * three channels, and that is worth defending because all three of the obvious
 * shapes would work. A plain RGB triple is the general answer and the wrong one
 * *here*: the most important consumer of a diffuse term on this stage is a
 * card's own art, which the renderer cannot read and can only darken with a
 * single black veil at a single opacity ([Tone.veil]). A triple would be two
 * thirds discarded at exactly the call site that matters most, with no honest
 * way to fold it back — and a coloured wash over card art is banned outright by
 * the handbook anyway. Leaving the temperature in the [Light] and having the
 * renderer ask the rig fails the other way: the rig mixes three lamps, so by the
 * time a face is shaded there is no single lamp left to ask.
 *
 * So [amount] is exactly the number this used to be — every existing consumer of
 * it is still correct — and [warmth] is the new axis, an energy-weighted average
 * of the lamps that actually reached the surface. The channels are derived here
 * rather than at the call site so that no renderer can invent its own mapping,
 * and they are multipliers on **linear** light meant to go through [Tone.shade]:
 * a warm lamp *removes* blue rather than adding red, which is what a filter
 * does, and which is what stops the brightest surfaces on the table from
 * clipping their way back to white.
 */
data class Lit(val amount: Float, val warmth: Float = 0f) {

    private val signed: Float get() = warmth.coerceIn(-1f, 1f)

    val red: Float get() = amount * (1f - max(0f, -signed) * TEMPERATURE)
    val green: Float get() = amount * (1f - abs(signed) * TEMPERATURE * MIDDLE)
    val blue: Float get() = amount * (1f - max(0f, signed) * TEMPERATURE)

    companion object {
        /** Nothing at all: a face turned away from the camera. */
        val None = Lit(0f)

        /**
         * How much of the far end of the spectrum a lamp at full warmth removes.
         *
         * The magnitude is the whole argument, so: at a white surface under full
         * light this puts about nineteen levels of 255 between red and blue,
         * which is a warm white — a bulb against daylight — and nowhere near an
         * orange one. It is chosen in linear light and read out in sRGB, which
         * is why it looks large written down: the encoding curve is steep near
         * white, so a sixteen per cent cut in blue *light* is a seven per cent
         * cut in the blue channel a screen is handed.
         *
         * The stage never sees the full number. Warmth is diluted by the rig's
         * ambient, which is white and is most of the light on the table, so the
         * brightest key-facing pile edge shifts by three or four levels and
         * everything the key cannot reach stays achromatic. The one place it
         * arrives undiluted is the specular pool, and that is correct: a
         * highlight is a reflection of the lamp, not of the room.
         */
        const val TEMPERATURE = 0.16f

        /**
         * Green's share of the shift, either way.
         *
         * A black-body locus moves red and blue in opposite directions and
         * leaves green nearly alone; making green move a third as far is what
         * keeps a warm lamp reading as *warm* rather than as yellow.
         */
        private const val MIDDLE = 0.35f
    }
}

/**
 * What one surface does with the light, ready to paint.
 *
 * Everything is 0..1 and nothing here knows what a `Color` is. That split is
 * deliberate: the renderer decides that a fringe comes off the prismatic ramp,
 * and this decides *how much*, so the numbers can be tested without a screen.
 * [lamp] does not break it — a temperature is still three multipliers, and the
 * renderer is still the only thing that owns a colour.
 */
data class Shade(
    /** Ambient and lambert together: what to multiply the surface's own colour by. */
    val diffuse: Float,
    /** The highlight's strength at its centre. */
    val specular: Float,
    /**
     * The colour of the light itself, at full strength — what to paint the
     * highlight with, [specular] being how much of it.
     *
     * Undiluted by the ambient, unlike everything else the temperature touches,
     * and that is the physics rather than a licence: a specular highlight is a
     * mirror image of the lamp, so it is the lamp's own colour and no part of
     * the room's. It is also the only place on a card face where a temperature
     * can legitimately show at all — the diffuse term reaches the art as a black
     * veil, and a coloured wash over a card is the anti-pattern, not the effect.
     */
    val lamp: Lit,
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

        // The card already knows where it is, so a placed lamp needs no new
        // parameter here — which is the whole reason this signature survives a
        // release that gave light a position. `pose.position` is in the mat's
        // frame, the same frame the rig is in, and it is the point every term
        // below should have been asking about all along.
        val at = pose.position
        val toLight = light.toLightFrom(at)
        val reach = light.attenuation(at)

        val lambert = max(0f, visible dot toLight)
        val diffuse = (light.ambient + (1f - light.ambient) * lambert * light.intensity * reach)
            .coerceIn(0f, 1f)

        // Blinn-Phong: the half-vector between the eye and the light, which is
        // the direction a mirror at this point would have to face.
        val half = (toLight + eye.normalised()).normalised()
        val alignment = max(0f, visible dot half)
        val specular = if (lambert <= 0f) {
            0f
        } else {
            (alignment.pow(material.shininess) * material.specular * light.intensity * reach)
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
            lamp = Lit(1f, light.warmth),
            fresnel = fresnel,
            hotspot = Vec2(
                x = 0.5f + SPREAD * (half dot right) / depth,
                y = 0.5f + SPREAD * (half dot down) / depth,
            ),
            facing = facing,
        )
    }
}
