package com.kaiharimoto.mastertool.ui.play

import androidx.compose.ui.graphics.Brush
import com.kaiharimoto.mastertool.core.layout.PlaneJacobian
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.ui.gpu.StageShader
import com.kaiharimoto.mastertool.ui.gpu.brush
import com.kaiharimoto.mastertool.ui.gpu.compileStageShader

/**
 * The desk top, one pixel at a time.
 *
 * The playmat is gone and the desk is the surface the game is played on, which
 * makes it the largest object in the frame and the one thing a person looks past
 * for the whole session. A flat brown fill can be a *background*; it cannot be a
 * table somebody's hands are resting on.
 *
 * Three things are what make sawn timber read as timber rather than as brown
 * paper, and all three are here.
 *
 * **The grain is rings, distorted.** A board is a slice through a tree, so its
 * figure is the annual rings cut at an angle — roughly parallel lines that
 * wander, crowd together and occasionally close into a loop. Straight stripes
 * read as veneer or as a pattern; the wander is the whole tell, and it costs two
 * octaves of value noise.
 *
 * **Latewood is darker *and* harder.** The dark line in a ring is denser, so it
 * takes a polish differently: on a lacquered desk the dark grain is *glossier*
 * than the pale wood around it. Colour alone reads as a print of wood. Colour
 * plus a specular that follows the same signal reads as a surface.
 *
 * **There are pores, and they are tiny.** Open-grain timber has a fine pitting
 * that never resolves as individual holes at this distance — it resolves as the
 * surface not being perfectly smooth, which is exactly what a normal
 * perturbation at the sub-thread scale gives.
 *
 * ## What it composites into, and the one thing that keeps it safe
 *
 * `BlendMode.Overlay` over the desk's already-shaded fill, identity at mid grey,
 * for the same reason [FeltWeave] does: the surface underneath is solved by
 * `StageRig` against the same rig as every card, and a shader that *replaced* it
 * would be a second place the lighting could be wrong. So this modulates and
 * never decides. Where there is no runtime shader — Android below 33 — the desk
 * is the flat fill that shipped.
 *
 * Band-limited on the plane's own Jacobian, exactly as the weave is, because a
 * desk seen from the player's chair runs away from the camera much harder than a
 * playmat ever did: it reaches the back wall.
 */
internal object WoodGrain {

    /**
     * How many growth rings fall across a card's width.
     *
     * Under one, and that is the correction that turned this from burlap into
     * timber. The first version drew twelve, which on a desk several cards wide
     * is some hundreds of rings — and with a wander wider than the pitch itself
     * the `fract` wrapped many times per ring and the whole surface came out as
     * scribble. A desk board shows a handful of ring bands across its width, so
     * the pitch is *longer* than a card, not shorter.
     */
    private const val RINGS_PER_CARD = 0.8f

    /** How far the grain may move the wood. Small: it is figure, not paint. */
    private const val DEPTH = 0.22f

    fun compile(): StageShader? = compileStageShader(SOURCE)

    fun brushFor(
        shader: StageShader,
        origin: Pair<Float, Float>,
        cardWidth: Float,
        key: Vec3,
        eye: Vec3,
        density: PlaneJacobian,
    ): Brush = shader.brush {
        float2("uOrigin", origin.first, origin.second)
        float("uPitch", cardWidth / RINGS_PER_CARD)
        float2("uDensity", density.alongX, density.alongY)
        // Toward the lamp, not the way the light travels. See FeltWeave — this
        // is the sign that cost a whole iteration once already.
        float3("uLight", -key.x, -key.y, -key.z)
        // The desk is lacquered, so where the highlight sits depends on where
        // the head is. The felt never needed this; a gloss does.
        float3("uEye", eye.x, eye.y, eye.z)
        float("uDepth", DEPTH)
    }

    private val SOURCE = """
        uniform float2 uOrigin;
        uniform float  uPitch;
        uniform float2 uDensity;
        uniform float3 uLight;
        uniform float3 uEye;
        uniform float  uDepth;

        // Sin-free. The classic `fract(sin(dot(...)) * large)` costs a
        // transcendental per corner, and value noise wants four corners — so a
        // shader over the whole desk was paying sixteen sines a pixel and the
        // frame cost quadrupled. This is the Hugo Elias / Dave Hoskins style
        // mix: a few multiplies and fracts, no library call, and visually
        // indistinguishable at this amplitude.
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
            float2 q = (p - uOrigin) / uPitch;

            float2 pxPerRing = uDensity * uPitch;
            float  cycles = max(1.0 / max(pxPerRing.x, 1e-4), 1.0 / max(pxPerRing.y, 1e-4));
            float  fade = 1.0 - smoothstep(0.30, 0.50, cycles);

            // Boards run the length of the desk, so the rings are bands of
            // constant y and the figure is read across it. Two octaves of
            // wander, both far *smaller* than the ring pitch — that ratio is the
            // difference between a plank and a scribble, and getting it the
            // wrong way round is what the first version did.
            // One wander lookup, not two. The second octave was buying a
            // roughness the fine streaks now supply, at the price of four more
            // hashes on every pixel of the largest object in the frame.
            float t = q.y + (noise(q * float2(0.55, 0.22)) - 0.5) * 0.46;
            float r = fract(t);

            // Latewood: one narrow dark band per ring. Asymmetric power rather
            // than a sine, because a growth ring is abrupt on its outer side and
            // gradual on its inner, and a symmetric wave reads as corrugation.
            float edge = 1.0 - abs(r * 2.0 - 1.0);
            float late = pow(edge, 9.0);
            // Its slope, analytically. The wander varies far more slowly than
            // the ring does, so its own gradient is left out deliberately.
            float dLate = 9.0 * pow(edge, 8.0) * (r < 0.5 ? 2.0 : -2.0);

            // Pores, an order finer than the rings and faded on their own terms
            // — a fade sized for the ring pitch would let these alias freely.
            float poreFade = 1.0 - smoothstep(0.30, 0.50, cycles * 30.0);
            float pore = (noise(q * float2(70.0, 30.0)) - 0.5) * poreFade;

            // Fine grain: the many thin streaks *inside* a ring band, running
            // parallel to it. Wood has both frequencies and the broad bands
            // alone read as quilting — this is the component that says timber.
            // Carried on the same wander, so the two never cross each other.
            //
            // Riding on `t` rather than on a wander of its own: the fine streaks
            // in real timber follow the rings they sit between, and reusing the
            // ring's own coordinate is both more correct and one whole noise
            // lookup cheaper.
            float fineFade = 1.0 - smoothstep(0.30, 0.50, cycles * 9.0);
            float fr = fract(t * 9.0);
            float fine = pow(1.0 - abs(fr * 2.0 - 1.0), 5.0) * fineFade;

            // Sanded timber is *smooth*. Its figure is almost entirely pigment,
            // and leaning on the normal instead is what made the first pass read
            // as quilted leather: broad soft relief where wood has flat colour.
            float3 n = normalize(float3(
                pore * 0.10 * fade,
                (-dLate * 0.012 + pore * 0.14) * fade,
                1.0));
            float3 l = normalize(uLight);
            float3 v = normalize(uEye);

            float delta = (max(dot(n, l), 0.0) - max(l.z, 0.0)) * uDepth;

            // Latewood is darker in the pigment and, because it is denser, takes
            // a tighter polish than the pale wood around it. Colour alone is a
            // print of wood; colour plus a specular that follows the same signal
            // is a surface.
            delta -= (late * 0.115 + fine * 0.045) * fade;
            float3 hv = normalize(l + v);
            float gloss = pow(max(dot(n, hv), 0.0), mix(30.0, 80.0, late));
            delta += gloss * (0.035 + 0.075 * late) * fade;

            float g = clamp(0.5 + delta, 0.0, 1.0);
            return half4(half3(g), 1.0);
        }
    """.trimIndent()
}
