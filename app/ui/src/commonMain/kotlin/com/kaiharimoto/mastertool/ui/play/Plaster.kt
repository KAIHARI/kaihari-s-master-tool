package com.kaiharimoto.mastertool.ui.play

import androidx.compose.ui.graphics.Brush
import com.kaiharimoto.mastertool.core.render.Homography
import com.kaiharimoto.mastertool.ui.gpu.StageShader
import com.kaiharimoto.mastertool.ui.gpu.brush
import com.kaiharimoto.mastertool.ui.gpu.compileStageShader
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The wall, one pixel at a time.
 *
 * The room is half the picture from the seat the stage opens at, and most of
 * that half is wall. It has been one flat fill with a gradient across it since
 * it stopped being a hairline, and after the tonemap it is a *bright* flat fill
 * — code 115 rather than 62 — which is a much more obvious flat fill. A painted
 * wall is the surface a photograph of a room is mostly made of, and the two
 * things that stop it reading as paper are both very quiet.
 *
 * **A skimmed wall is not flat, it is gently wavy.** A plasterer's float leaves
 * broad, soft undulations a hand's width across and a fraction of a millimetre
 * deep. You never see them as shape; you see them as the light moving very
 * slightly as it crosses the wall, which is why the term here is a *bump* and
 * not a stain. Lit from the window's own direction, so it changes with the room.
 *
 * **The pigment is not uniform either.** Emulsion over filler dries a little
 * unevenly, and the variation is much lower in frequency than the relief. Three
 * per cent, which is invisible as a patch and is the difference between a
 * surface and a fill.
 *
 * ## How it knows where it is
 *
 * This is the first shader on this stage that colours a surface which is **not**
 * the mat plane, and that is the whole engineering of it. [WoodGrain] and
 * [FeltWeave] get their coordinates for free: they draw on z = 0, where
 * `StagePlane.flatten` is the identity, so a draw-scope pixel *is* a mat point.
 * A wall is a plane standing up in the room, so the path being filled has been
 * flattened and a shader over it is handed mat pixels with no idea which part of
 * the wall it is standing on.
 *
 * [Homography.rectToQuad] over the face's own two edges, inverted, is that map —
 * which is exactly the use `Homography.inverse`'s KDoc was written for. The
 * coordinates it recovers are **world units on the wall's own plane**, offset by
 * where the face starts, so the four pieces that tile one wall around a window
 * share one continuous surface instead of restarting the pattern at every jamb.
 *
 * And the level of detail is analytic, because there are no derivatives in AGSL
 * or SkSL on either platform. `Homography.scaleAt` is `sqrt(|det|)/|w|^1.5`;
 * only the `w` varies, so the determinant arrives as a uniform and the shader
 * computes the rest from the `w` it already has for the divide. A wall seen from
 * a chair packs several times more surface into a pixel at its far end than at
 * its near one, and a texture band-limited on one end crawls on the other.
 *
 * ## What it composites into
 *
 * `BlendMode.Overlay`, identity at mid grey, exactly as [WoodGrain] and
 * [FeltWeave] do: the fill underneath is `StageRig`'s answer for this face and a
 * shader that *replaced* it would be a second place the room's lighting could be
 * wrong. Where there is no runtime shader the wall is the flat fill that
 * shipped.
 */
internal object Plaster {

    /** Compiled once per screen; null wherever the platform has no shader. */
    fun compile(): StageShader? = compileStageShader(SOURCE)

    /**
     * How wide one undulation of the float is, in card widths.
     *
     * A card is 59mm, so this is about eighteen centimetres — a hand's span,
     * which is the arc a plasterer's float actually sweeps. It is the one
     * number here that is a fact about the trade rather than about the picture.
     */
    private const val SWEEP = 3.1f

    /**
     * How much the wall's own tone varies, as a fraction either side of the mean.
     *
     * Six per cent, and read at a *wall's* scale rather than a float's — see
     * [DRIFT]. Three per cent at the float's own pitch is what emulsion over
     * filler does and it was measured invisible: 3.4 codes of variation, spread
     * across a hand's span, on a surface a fifth of the screen wide. A wall in a
     * photograph varies by much more than its paint does, because it also
     * carries the light not being uniform, the dust, and everything that has
     * ever been near it.
     */
    private const val MOTTLE = 0.060f

    /**
     * How many float sweeps across one slow drift of tone.
     *
     * The pigment is read at this multiple of [SWEEP] and the relief at the
     * sweep itself, which is two frequencies from one field for one extra tap.
     * A single frequency was the whole reason the first version could not be
     * seen: variation at exactly one scale is a texture, and what reads as a
     * surface is variation at every scale at once. Four sweeps is about
     * seventy centimetres — a quarter of this wall.
     */
    private const val DRIFT = 4f

    /**
     * How steep the relief is, as a slope.
     *
     * Eighty-five thousandths is under five degrees at the steepest point of an
     * undulation, which on a wall lit from one side moves the lambert term by a
     * few per cent — visible as light and never as shape. Anything an order
     * above this is stucco.
     *
     * **It reads at night and barely by day, and that is the room rather than
     * this number.** `DeskDay.key` comes *through* the window, so it travels
     * along the wall's own outward normal — the wall containing a window is the
     * one surface that window cannot light, and by day it is nearly all ambient.
     * Relief under pure ambient has nothing to catch. Measured, the wall moves
     * by up to 19 codes at night and 7 by day. Cranking this to make the day
     * case show would make the night case stucco; the day case is `AAA.md`
     * #61c's job — splitting the day key into a sky that stands *in front of*
     * the wall and a sun that comes through the opening.
     */
    private const val RELIEF = 0.085f

    /**
     * A brush that plasters one face.
     *
     * [toFace] maps a draw-scope pixel to a point on the face's own plane, in
     * world units, and [origin] is where that face's own corner sits along the
     * plane's two axes — so two coplanar faces agree. [lightInPlane] is the
     * direction *toward* the key, resolved onto the same two axes, which is the
     * sign that has cost this project a whole iteration once already.
     */
    fun brushFor(
        shader: StageShader,
        toFace: Homography,
        origin: Pair<Float, Float>,
        cardWidth: Float,
        lightInPlane: Pair<Float, Float>,
        lightAlongNormal: Float,
    ): Brush = shader.brush {
        matrix3("uToFace", toFace.columnMajor())
        float2("uOrigin", origin.first, origin.second)
        float("uPitch", cardWidth * SWEEP)
        // `sqrt(|det|)` of the same map: `scaleAt`'s only constant. See the
        // class note — the `w` is per pixel and the determinant is not.
        float("uDetRoot", sqrt(abs(toFace.determinant())))
        float2("uLight", lightInPlane.first, lightInPlane.second)
        float("uLightN", lightAlongNormal)
        float("uMottle", MOTTLE)
        float("uDrift", DRIFT)
        float("uRelief", RELIEF)
    }

    /** The nine numbers a shader wants, down the columns. */
    private fun Homography.columnMajor() = floatArrayOf(
        m00, m10, m20,
        m01, m11, m21,
        m02, m12, m22,
    )

    private val SOURCE = """
        uniform float3x3 uToFace;
        uniform float2   uOrigin;
        uniform float    uPitch;
        uniform float    uDetRoot;
        uniform float2   uLight;
        uniform float    uLightN;
        uniform float    uMottle;
        uniform float    uDrift;
        uniform float    uRelief;

        // The same sin-free mix `WoodGrain` uses, and for the same reason: the
        // classic `fract(sin(dot(...)))` costs a transcendental per corner and
        // value noise wants four of them.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
            q += dot(q, float3(q.y, q.z, q.x) + 33.33);
            return fract((q.x + q.y) * q.z);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }

        half4 main(float2 p) {
            // Back onto the wall. The divide is the homography's own; `w` is
            // also what the level of detail is a function of, so it is kept.
            float3 q = uToFace * float3(p, 1.0);
            float  w = abs(q.z) < 1e-6 ? 1e-6 : q.z;
            float2 at = (float2(q.x, q.y) / w + uOrigin) / uPitch;

            // Undulations per pixel, analytically. `sqrt(|det|)/|w|^1.5` is the
            // map's own scale; over the pitch it is how much of the pattern one
            // pixel covers, and past a half the pattern is finer than the
            // sampling and has to go quietly rather than sparkle.
            float perPixel = uDetRoot / (pow(abs(w), 1.5) * uPitch);
            float fade = 1.0 - smoothstep(0.25, 0.50, perPixel);

            // One noise field at two scales. The gradient at the float's own
            // pitch is the relief; the value a few times slower than that is the
            // tone. Reading both off one frequency is what made the first
            // version invisible — variation at exactly one scale is a texture,
            // and what reads as a surface is variation at more than one.
            float e = 0.35;
            float c = noise(at);
            float du = noise(at + float2(e, 0.0)) - c;
            float dv = noise(at + float2(0.0, e)) - c;
            float drift = noise(at / uDrift);

            // Lambert against a perturbed normal, as a *difference* from the
            // flat answer, so a face already shaded by the rig is modulated and
            // never replaced. The slope is in the plane's own two axes and the
            // normal is the third, which is why the light arrives split the same
            // way.
            float3 n = normalize(float3(-du * uRelief / e, -dv * uRelief / e, 1.0));
            float lit = n.x * uLight.x + n.y * uLight.y + n.z * uLightN;
            float delta = (max(lit, 0.0) - max(uLightN, 0.0)) * fade;

            // The drift is not faded: it is slower than any pixel and cannot
            // alias, and fading it would take the wall's tone away exactly where
            // the wall is furthest off and most obviously a fill.
            float pigment = (drift - 0.5) * 2.0 * uMottle;

            // Overlay wants mid grey for "leave it alone".
            float v = clamp(0.5 + delta + pigment, 0.0, 1.0);
            return half4(half3(v), 1.0);
        }
    """.trimIndent()
}
