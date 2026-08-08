package com.kaiharimoto.mastertool.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.io.File

/**
 * Does a runtime shader work here at all?
 *
 * The play stage has always drawn with paths and gradients, and the standing
 * rule was that a shader is not common code so it does not get used. Reaching
 * photographic fidelity changes that calculus — a per-pixel material response is
 * the only way a surface stops being a fill — so the first question is whether
 * the seam is even available on both targets, and the second is whether it
 * survives the one place this repository can actually look at a picture.
 *
 * That second question is the sharp one. `:studio` runs on a **raster** surface
 * with `skiko.renderApi=SOFTWARE`, no GL context and no GPU, because it runs in
 * a container. If SkSL only ran on Ganesh then every shader written from here on
 * would be invisible to the loop's own eye, and the loop would be back to
 * shipping a release to find out what a change did — which is the thing the
 * studio exists to end.
 *
 * So this draws the felt. Not a test pattern: the actual thing wanted first, a
 * woven surface with a normal that varies per pixel and a specular that answers
 * it, so that a positive result is also the beginning of the work rather than
 * only a green light.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull()?.removePrefix("--out=") ?: "build/shots")
    out.mkdirs()

    val effect = runCatching { RuntimeEffect.makeForShader(FELT) }
        .onFailure {
            println("[spike] SkSL did not compile: ${it.message}")
            kotlin.system.exitProcess(1)
        }
        .getOrThrow()
    println("[spike] SkSL compiled")

    runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(
            width = WIDTH,
            height = HEIGHT,
            density = Density(1f),
            coroutineContext = coroutineContext,
        ) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val builder = RuntimeShaderBuilder(effect).apply {
                        uniform("uSize", size.width, size.height)
                        // A key from the upper left, which is where the day
                        // window is, and a warm one.
                        uniform("uLight", -0.45f, -0.62f, 0.64f)
                        uniform("uWeave", 190f)
                        uniform("uBase", 0.055f, 0.055f, 0.075f)
                    }
                    drawRect(
                        // `asComposeShader()` is the desktop half of the seam.
                        // Android has no such call — there `Shader` is a
                        // typealias for `android.graphics.Shader` and a
                        // `RuntimeShader` already is one.
                        brush = ShaderBrush(builder.makeShader().asComposeShader()),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                    // A white rule down the middle, so a shader that silently
                    // draws nothing is told apart from one that draws black.
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(size.width / 2f - 1f, 0f),
                        size = Size(2f, size.height),
                    )
                }
            }
        }

        try {
            val image = scene.render(0L)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("no encode")
            File(out, "shader-spike.png").writeBytes(png.bytes)
            println("[spike] wrote ${File(out, "shader-spike.png").absolutePath} (${png.size / 1024} KiB)")
        } finally {
            scene.close()
        }
    }
    kotlin.system.exitProcess(0)
}

private const val WIDTH = 900
private const val HEIGHT = 500

/**
 * Woven cloth, per pixel.
 *
 * The weave is two out-of-phase square-ish waves, one per axis, which is what a
 * plain weave *is* — warp over weft, alternating. Their gradient is the surface
 * normal, and the normal is what the light is asked about, so the threads catch
 * the key on one side and shade on the other instead of being a pattern printed
 * on a flat colour. That difference is the entire reason to want a shader here.
 */
private val FELT = """
    uniform float2 uSize;
    uniform float3 uLight;
    uniform float  uWeave;
    uniform float3 uBase;

    // Height of the cloth at p: two interleaved thread families.
    float threads(float2 p) {
        float warp = sin(p.x * 6.2831853);
        float weft = sin(p.y * 6.2831853);
        // Over-under: whichever thread is on top at this point owns the height.
        float over = step(0.0, warp * weft);
        return mix(0.5 + 0.5 * weft, 0.5 + 0.5 * warp, over);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize.y * uWeave;

        // Central differences give the normal without any derivative builtins,
        // which keeps this identical on both platforms' SkSL.
        float e = 0.35;
        float h  = threads(uv);
        float hx = threads(uv + float2(e, 0.0)) - threads(uv - float2(e, 0.0));
        float hy = threads(uv + float2(0.0, e)) - threads(uv - float2(0.0, e));

        float3 n = normalize(float3(-hx * 1.6, -hy * 1.6, 1.0));
        float3 l = normalize(uLight);

        float diffuse = max(dot(n, l), 0.0);
        float3 v = float3(0.0, 0.0, 1.0);
        float3 hv = normalize(l + v);
        float spec = pow(max(dot(n, hv), 0.0), 42.0) * 0.16;

        // Cloth is retroreflective at grazing angles — the sheen that makes
        // velvet read as velvet rather than as matte paint.
        float sheen = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.10;

        float3 lit = uBase * (0.62 + 0.55 * diffuse) + spec + sheen;
        return half4(half3(lit), 1.0);
    }
""".trimIndent()
