package com.kaiharimoto.mastertool.ui.play

import androidx.compose.ui.graphics.Brush
import com.kaiharimoto.mastertool.ui.gpu.StageShader
import com.kaiharimoto.mastertool.ui.gpu.brush
import com.kaiharimoto.mastertool.ui.gpu.compileStageShader
import com.kaiharimoto.mastertool.core.layout.PlaneJacobian
import com.kaiharimoto.mastertool.core.motion.Vec3

/**
 * The playmat's cloth, one pixel at a time.
 *
 * `AAA.md` #65 asks for a weave and says why: it is what stops a large flat
 * surface from reading as a fill colour. What it does not say — because it was
 * written when the stage could only draw paths — is that a weave *drawn* is
 * still a fill colour with a pattern on it. What makes cloth read as cloth is
 * that each thread has its own normal, so the ones lying across the light are
 * bright on the side facing it and dark on the far side, and the whole surface
 * changes as the light or the head moves. That is per-pixel or it is nothing,
 * and it is the first thing the shader seam was opened for.
 *
 * ## How it composites, and why that is the safe shape
 *
 * The mat is already drawn — as a flat colour, or as the lamp's pool under a
 * placed light — and this goes over the top in **overlay**, which is the
 * identity at mid grey. So the shader emits 0.5 where the cloth is flat and
 * departs from it only where a thread's normal actually catches or loses the
 * key. Three things follow, and all three are the reason it is done this way
 * rather than by moving the whole felt into SkSL.
 *
 * The pool keeps its own arithmetic — `StageRig.pool` is solved against the same
 * rig that lights every card, and duplicating that falloff in shader source
 * would be the second place it could be wrong.
 *
 * A shader that fails to compile, or an Android below 33, loses exactly the
 * weave and nothing else. The mat underneath is the mat that shipped.
 *
 * And it is *bounded*: overlay at mid grey cannot move a pixel, so the worst a
 * bug in here can do is push the felt around a little, never blank it.
 *
 * ## Why the threads are too big
 *
 * A playmat's weave is about a third of a millimetre, a card is 59mm wide, and a
 * card is around 104px on the reference stage — so an honest thread is half a
 * pixel. Half a pixel of high-contrast detail is not cloth, it is aliasing, and
 * it would crawl the moment the table turned. So [THREADS_PER_CARD] is a
 * legibility choice in the same tradition as `CardSolid.pileDepth` exaggerating
 * a deck's height: the number that reads is the honest one, and the reason it
 * departs from the measurement is written down rather than tuned to.
 */
internal object FeltWeave {

    /**
     * Threads across a card's width.
     *
     * Around three pixels a thread at the reference size, which is the coarsest
     * the eye still reads as woven rather than as corduroy, and fine enough that
     * the pattern never becomes a subject.
     */
    private const val THREADS_PER_CARD = 34f

    /**
     * How far the cloth is allowed to move the felt, at most.
     *
     * Small on purpose. This is the surface sixty cards are read against and the
     * handbook's first line is that the mat stays the brightest thing in the
     * frame; a weave that competes with a card is a weave that has to go.
     */
    private const val DEPTH = 0.55f

    fun compile(): StageShader? = compileStageShader(SOURCE)

    /**
     * @param origin the mat's top-left, so the cloth is pinned to the mat rather
     *   than to the canvas and does not swim when the board is re-solved.
     * @param cardWidth the one length everything on this stage is measured in.
     * @param key the rig's key direction, in the mat's own frame, so the threads
     *   are lit by the same lamp as the cards resting on them.
     */
    fun brushFor(
        shader: StageShader,
        origin: Pair<Float, Float>,
        cardWidth: Float,
        key: Vec3,
        density: PlaneJacobian,
    ): Brush =
        shader.brush {
            float2("uOrigin", origin.first, origin.second)
            float("uPitch", cardWidth / THREADS_PER_CARD)
            // How far a mat unit travels on the glass, per axis, so the cloth
            // can refuse to draw threads finer than the screen can hold. Both
            // axes, because a turned table compresses whichever one is running
            // away from you and a single scale would be right at one yaw only.
            //
            // Taken at the mat's *centre*: one uniform for the whole draw, so
            // the far edge fades a little late and the near edge a little
            // early. Exact per-pixel would mean the projection's parameters
            // inside the shader — a second place the projection could be wrong,
            // which the felt's own KDoc already refuses for the pool.
            float2("uDensity", density.alongX, density.alongY)
            // **Negated**, and this is the whole of a bug that shipped a
            // perfectly flat mat. `Light.direction` is the way the light
            // *travels* — the key is (0.30, 0.45, -0.84), heading down onto the
            // table — and every dot product wants the vector pointing back *at*
            // the lamp. Handed the travel direction, `max(dot(n, l), 0)` is zero
            // on every thread of a surface facing up, so the shader compiled,
            // ran, and returned mid grey everywhere: overlay's identity, which
            // is a weave that costs a full shader pass and cannot be seen.
            float3("uLight", -key.x, -key.y, -key.z)
            float("uDepth", DEPTH)
        }

    /**
     * Plain weave: warp over weft, alternating, which is what "plain weave"
     * means and is enough structure for the eye to name the material.
     *
     * The normal comes from central differences rather than from a derivative
     * builtin, because runtime shaders do not have `dFdx` on either platform and
     * a hand-rolled difference is identical SkSL on both.
     */
    private val SOURCE = """
        uniform float2 uOrigin;
        uniform float  uPitch;
        uniform float2 uDensity;   // screen px per mat unit, per axis
        uniform float3 uLight;
        uniform float  uDepth;

        const float TAU = 6.2831853;

        // A basket weave, and both its slopes, in closed form.
        //
        // One thread crossing per cell: on even cells the weft lies over the
        // warp and owns the surface, on odd cells the warp does. Within a cell
        // the thread is a raised cosine, which is zero *and* flat at every cell
        // boundary — so the whole surface is smooth across the joins without
        // anything having to blend them.
        //
        // Returned with its derivatives rather than sampled four times at an
        // offset, which is what this replaced: six transcendentals for a slope
        // that is two, and an epsilon nobody could defend.
        float3 weave(float2 q) {
            float2 c   = floor(q);
            float2 f   = fract(q) - 0.5;
            float  par = mod(c.x + c.y, 2.0);
            float2 s   = cos(TAU * f) * 0.5 + 0.5;
            float2 d   = -TAU * 0.5 * sin(TAU * f);
            return float3(mix(s.y, s.x, par),
                          mix(0.0, d.x, par),
                          mix(d.y, 0.0, par));
        }

        half4 main(float2 p) {
            float2 uv = (p - uOrigin) / uPitch;
            float3 hg = weave(uv);

            // Nyquist. A thread narrower than two pixels cannot be drawn, only
            // aliased — and an aliased thread *crawls* as the camera turns,
            // which breaks "nothing idles" by accident rather than by choice.
            // So the relief is faded out as the cloth approaches half a cycle
            // per pixel, per axis, and the far half of a low seat simply stops
            // having threads instead of shimmering.
            float2 pxPerThread = uDensity * uPitch;
            float  cycles = max(1.0 / max(pxPerThread.x, 1e-4),
                                1.0 / max(pxPerThread.y, 1e-4));
            float  fade = 1.0 - smoothstep(0.30, 0.50, cycles);

            float3 n = normalize(float3(-hg.y * fade, -hg.z * fade, 1.0));
            float3 l = normalize(uLight);

            float delta = (max(dot(n, l), 0.0) - max(l.z, 0.0)) * uDepth;

            // The glint the fade removes does not vanish, it broadens — which is
            // what happens to a specular lobe when the geometry under it becomes
            // too small to resolve. Losing it instead makes the far felt read as
            // a different material from the near felt.
            float3 hv = normalize(l + float3(0.0, 0.0, 1.0));
            float  soft = 1.0 - fade * fade;
            delta += pow(max(dot(n, hv), 0.0), mix(60.0, 8.0, soft)) * 0.22 * (1.0 - 0.5 * soft);

            float g = clamp(0.5 + delta, 0.0, 1.0);
            return half4(half3(g), 1.0);
        }
    """.trimIndent()
}
