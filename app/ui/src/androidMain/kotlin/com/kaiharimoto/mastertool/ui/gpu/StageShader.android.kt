package com.kaiharimoto.mastertool.ui.gpu

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Android's half of the seam.
 *
 * `RuntimeShader` arrived in API 33 and this app's `minSdk` is 26, so the
 * version gate is not a formality — it is the majority of the fallback's reason
 * to exist. The Tab S11 this is tuned on is far past it; a five-year-old phone
 * is not, and `AAA.md` #98 is explicit that the app should not discover a
 * device's limits by dropping frames on somebody.
 *
 * `RuntimeShader` *is* an `android.graphics.Shader`, and `Shader` is what
 * `androidx.compose.ui.graphics.Shader` is a typealias for here — so the brush
 * is one constructor call with nothing to bridge. Desktop is the awkward side.
 */
actual class StageShader internal constructor(internal val shader: RuntimeShader)

actual fun compileStageShader(sksl: String): StageShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    // Compilation failure is a runtime throw here rather than a returned error,
    // and it is caught rather than allowed out: a shader that will not build is
    // a scene drawn the old way, never a crash on somebody's tablet.
    return runCatching { StageShader(RuntimeShader(sksl)) }.getOrNull()
}

actual fun StageShader.brush(uniforms: ShaderUniforms.() -> Unit): Brush {
    // Uniforms are set on the shader itself, which is mutable and re-usable —
    // so a compiled shader is allocated once for the life of the screen and
    // every frame only writes floats into it.
    AndroidUniforms(shader).uniforms()
    return ShaderBrush(shader)
}

private class AndroidUniforms(private val shader: RuntimeShader) : ShaderUniforms {
    override fun float(name: String, value: Float) = shader.setFloatUniform(name, value)
    override fun float2(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)
    override fun float3(name: String, x: Float, y: Float, z: Float) =
        shader.setFloatUniform(name, x, y, z)
    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
        shader.setFloatUniform(name, x, y, z, w)
}
