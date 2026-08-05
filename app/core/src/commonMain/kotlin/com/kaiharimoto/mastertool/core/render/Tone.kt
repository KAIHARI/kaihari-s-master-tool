package com.kaiharimoto.mastertool.core.render

import kotlin.math.pow

/**
 * A colour channel, and what a light does to it.
 *
 * Everywhere else in `core/render` a number is an amount of light: [Shading]
 * hands back a diffuse term, [StageRig] hands back how bright one face of a
 * solid is, and both of them are honest physical fractions. A colour channel is
 * not. What a `Color` stores — and what a canvas blends — is the sRGB
 * *encoding* of a channel, a curve fitted to how the eye spends its precision,
 * and half of that number is nowhere near half the light.
 *
 * So multiplying a channel by a light term is the one arithmetic on this stage
 * that looks obviously right and is not. It over-darkens everything it touches,
 * worst in the midtones — a mid grey under half the light lands on 0.25 where
 * the light actually puts it at 0.36 — and the symptom is a table whose shaded
 * surfaces all slide toward the same muddy grey instead of staying the colours
 * they were. [shade] is that multiply, done in the space where multiplying
 * means something.
 */
object Tone {

    /**
     * Where the straight segment hands over to the power curve, linear side.
     *
     * The real transfer function is piecewise, and using it rather than the
     * usual gamma-2.2 shorthand is worth the branch here specifically because
     * of where this app lives. The stage is true black: its shadows, its pile
     * edges, its felt fall-off and every veil drawn over a card are decided in
     * the bottom hundredth of the range, and that is exactly where the two
     * curves part company. A power curve leaves the origin with an infinite
     * slope, so it has no useful precision down there and no stable inverse;
     * the real one leaves it along a straight line of slope 12.92, which keeps
     * near-black values distinguishable and round-trippable. At a linear 0.002
     * the shorthand is out by more than a factor of two — on a black stage
     * that is the difference between an edge you can see and one you cannot.
     */
    const val KNEE_LINEAR = 0.0031308f

    /** The same point from the other side: [KNEE_LINEAR] times the slope. */
    const val KNEE_ENCODED = 0.04045f

    /**
     * The channel [veil] solves its opacity at.
     *
     * A mid grey, and this is an approximation with a known error rather than a
     * derivation. One opacity laid over a picture cannot be encoding-correct
     * for every channel beneath it, because the veil a channel actually wants,
     * `1 - shade(c, light) / c`, is a function of `c`. The +0.055 offset in the
     * transfer function is what makes it one: it is precisely what stops the
     * curve being a pure power, so the channel never divides back out — and it
     * weighs most where the channel is smallest, which is the whole bottom half
     * of the range and not merely the straight segment below [KNEE_ENCODED].
     *
     * Measured at the rig's own diffuse of 0.72, the veil each channel wants is
     * 0.268 at c=0.05, 0.198 at 0.10, 0.163 at 0.20 and 0.142 at 0.50 — a 1.89x
     * spread, every value of it far above the knee. Solving at 0.5 makes mid
     * grey exact and leaves the rest out in a fixed direction: dark channels
     * are veiled **too lightly** and come back brighter than the light puts
     * them, light ones very slightly too heavily. At diffuse 0.72 that is 1.19x
     * too bright at its worst (around c=0.04), 1.17x at c=0.05, 1.07x at 0.10,
     * 1.03x at 0.20, and the other way by 0.99x at c=0.90.
     *
     * It is still the right call for a picture: nowhere on the ramp is it out
     * by two levels of 255 at the light this stage actually uses, and the
     * alternative is not a better constant but a per-channel shader no renderer
     * can run over art it cannot read. What it is not is free at every light —
     * [veil] carries the warning about that, and it is the reason the bound is
     * pinned in a test rather than asserted here. The answer this replaces,
     * `1 - light`, is out by far more than any of it at every channel.
     */
    const val MID_TONE = 0.5f

    private const val SLOPE = 12.92f
    private const val SCALE = 1.055f
    private const val OFFSET = 0.055f
    private const val GAMMA = 2.4f

    /** An encoded channel as the amount of light it stands for. */
    fun toLinear(channel: Float): Float {
        val encoded = unit(channel)
        return if (encoded <= KNEE_ENCODED) {
            encoded / SLOPE
        } else {
            ((encoded + OFFSET) / SCALE).pow(GAMMA)
        }
    }

    /** An amount of light as the channel that stores it. */
    fun toSrgb(linear: Float): Float {
        val light = unit(linear)
        return if (light <= KNEE_LINEAR) {
            light * SLOPE
        } else {
            (SCALE * light.pow(1f / GAMMA) - OFFSET).coerceIn(0f, 1f)
        }
    }

    /**
     * This channel, under this much light. The one call the renderer makes.
     *
     * Both ends are exact rather than merely close, because both ends are
     * places the eye can check: a fully lit surface has to come back as its own
     * colour bit for bit, or nothing on the stage ever quite looks like the art
     * it was given, and an unlit one has to reach black on a stage made of it.
     */
    fun shade(channel: Float, light: Float): Float {
        val amount = unit(light)
        val base = unit(channel)
        if (amount >= 1f) return base
        if (amount <= 0f || base <= 0f) return 0f
        return toSrgb(toLinear(base) * amount)
    }

    /**
     * The opacity a black veil needs to shade a surface this renderer cannot
     * read — a card's own art — down to [light] of its brightness.
     *
     * The same statement as [shade], delivered the only way it can be when the
     * thing being darkened is a picture. It matters that this exists at all:
     * compositing black at `1 - light` is the identical mistake as multiplying
     * raw, because alpha blends in the encoding too, and a face left uncorrected
     * beside an edge that was corrected is worse than neither — then the white
     * band on a pile and the card resting on it disagree about where the lamp
     * is, which is the one thing the single rig exists to prevent.
     *
     * One opacity cannot be right for every channel underneath it; [MID_TONE]
     * carries the size of what is left over. The sign is the part worth
     * remembering at the tablet, because it sends you at a different constant
     * if you have it backwards: dark art comes back **brighter** than the light
     * puts it, lifted rather than crushed.
     *
     * **Read this before turning [StageRig]'s key ambient down.** That is the
     * obvious next move now the encoding is right — the rig floors every face
     * at 0.72 and the stage would take more contrast — but the leftover error
     * is not a constant. It grows sharply as the light falls, because the veil
     * thickens and the fixed +0.055 offset becomes a larger share of a smaller
     * number. Worst dark channel: 1.19x too bright at a diffuse of 0.72, 1.44x
     * at 0.5, 1.87x at 0.3 — where a channel of 0.10 lands on 0.056 against the
     * 0.039 the light asks for, four and a half levels of 255, on the dark card
     * art this file exists to keep readable against a true-black stage. Below
     * about 0.5 the honest fix is not a different reference channel but drawing
     * the face's shading some way other than one flat overlay. The bound is
     * pinned in `ToneTest`, so lowering the ambient will be argued with there
     * first.
     */
    fun veil(light: Float, over: Float = MID_TONE): Float {
        val base = unit(over)
        if (base <= 0f) return 0f
        return ((base - shade(base, light)) / base).coerceIn(0f, 1f)
    }

    /**
     * Every way in closes the range itself.
     *
     * The light terms upstream are dot products, divisions and sums that are
     * each clamped on their own, and `pow` of a negative base is NaN — which
     * does not draw a wrong colour, it draws nothing, silently. A number that
     * is not a number is not light, so it is treated as none.
     */
    private fun unit(value: Float): Float = when {
        value.isNaN() -> 0f
        value < 0f -> 0f
        value > 1f -> 1f
        else -> value
    }
}
