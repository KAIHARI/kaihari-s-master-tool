package com.kaiharimoto.mastertool.ui.gpu

import androidx.compose.ui.graphics.Brush

/**
 * A runtime shader, where the platform has one.
 *
 * ## Why this exists, and why it did not before
 *
 * The standing rule was that the stage draws with paths and gradients and
 * nothing else, because a shader is not common code and the two platforms spell
 * it differently. `AAA.md` #99 is that rule with a question mark on it: Android
 * 13 has `RuntimeShader`, desktop Skia has `RuntimeEffect`, and a thin seam with
 * a plain-draw fallback is reachable — "the one place the no-engine rule
 * deserves re-reading rather than restating".
 *
 * It is re-read now because the target moved. A surface stops reading as a fill
 * colour when its *normal varies per pixel* and the light is asked about it
 * there, and there is no arrangement of paths and gradients that does that. Felt
 * with a weave, lacquer with a clear coat, foil as a diffraction grating, a
 * shadow that darkens what is under it rather than painting over it — all of
 * them are per-pixel or they are pretend.
 *
 * The no-**engine** rule is untouched and still right. Nothing here draws
 * geometry, nothing here holds a scene, and every vertex on this stage is still
 * solved by tested arithmetic in `:core`. This is a way of colouring a rectangle.
 *
 * ## The contract
 *
 * [compile] returns null rather than throwing, on every platform, for every
 * reason: too old an Android, a driver that refuses, a typo in the SkSL. **Every
 * caller must have a drawing that works without it**, and that fallback is not a
 * degraded mode to be tolerated — it is what a third of Android devices in the
 * wild will actually see, and it is what shipped before this file existed.
 *
 * A shader is compiled once and re-used. [brush] sets uniforms and hands back
 * something a `DrawScope` can fill with, which is the only shape of use this
 * stage has: there is no vertex program here, no render target, no pass.
 */
expect class StageShader

/**
 * Compiles [sksl], or answers null where this platform cannot.
 *
 * Call it once and remember it. Compiling costs milliseconds and the result is
 * immutable; doing it inside a draw is how a shader becomes the most expensive
 * thing on the stage.
 */
expect fun compileStageShader(sksl: String): StageShader?

/** Sets [uniforms] and returns a brush that fills with the result. */
expect fun StageShader.brush(uniforms: ShaderUniforms.() -> Unit): Brush

/**
 * The uniform types this stage actually sets.
 *
 * Deliberately not the full set either platform offers. A seam that exposes
 * everything is a seam that has to be kept in step with two APIs forever; this
 * one covers scalars, points, colours and the odd matrix, which is every uniform
 * the shading in `:core` produces.
 */
interface ShaderUniforms {
    fun float(name: String, value: Float)
    fun float2(name: String, x: Float, y: Float)
    fun float3(name: String, x: Float, y: Float, z: Float)
    fun float4(name: String, x: Float, y: Float, z: Float, w: Float)
}
