package com.kaiharimoto.mastertool.ui.gpu

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * The desktop half of the seam.
 *
 * Skia's own runtime effects, which Skiko exposes and Compose bridges with
 * `asComposeShader()`. No version gate: every desktop build ships the Skia it
 * was compiled against, so if it compiled here it works here.
 *
 * The one thing worth knowing, and it is the reason this was worth proving
 * before anything was built on it: **this rasters without a GPU.** Skia runs
 * SkSL on the CPU backend too, so `:studio` — which draws with
 * `skiko.renderApi=SOFTWARE` in a container with no GL context at all — sees
 * exactly what a tablet sees. A shader the loop's own eye could not look at
 * would have put every per-pixel change back behind a signed release.
 *
 * Unlike Android, uniforms cannot be written into a finished shader: the builder
 * holds them and mints a new immutable `Shader` each time. That is one small
 * allocation per draw of one surface per frame, which is nothing against the
 * hundreds of `Path` objects the shadow pass already makes — but it is the
 * reason [brush] takes the uniforms rather than being a property.
 */
actual class StageShader internal constructor(internal val builder: RuntimeShaderBuilder)

actual fun compileStageShader(sksl: String): StageShader? =
    runCatching { StageShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(sksl))) }
        .getOrNull()

actual fun StageShader.brush(uniforms: ShaderUniforms.() -> Unit): Brush {
    DesktopUniforms(builder).uniforms()
    return ShaderBrush(builder.makeShader().asComposeShader())
}

private class DesktopUniforms(private val builder: RuntimeShaderBuilder) : ShaderUniforms {
    override fun float(name: String, value: Float) = builder.uniform(name, value)
    override fun float2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)
    override fun float3(name: String, x: Float, y: Float, z: Float) =
        builder.uniform(name, x, y, z)
    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
        builder.uniform(name, x, y, z, w)
}
