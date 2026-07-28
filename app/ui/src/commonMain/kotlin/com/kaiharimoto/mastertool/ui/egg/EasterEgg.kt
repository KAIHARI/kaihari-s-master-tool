package com.kaiharimoto.mastertool.ui.egg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.egg.Bounds
import com.kaiharimoto.mastertool.core.egg.Sprite
import com.kaiharimoto.mastertool.core.egg.SpritePhysics
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.ui.art.Res
import com.kaiharimoto.mastertool.ui.art.chibi
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.resources.painterResource

private const val GRAVITY = 1400f
private const val CARD_LIFETIME = 6f
private const val SPARK_LIFETIME = 1.4f
private const val MAX_CARDS = 10
private const val MAX_SPARKS = 90

/** A card in flight, with its motion held apart from the list that holds it. */
private class FlyingCard(val card: Card, var sprite: Sprite) {
    val motion = mutableStateOf(sprite)
}

/**
 * The chibi, loose.
 *
 * The desktop tool did this with a DOM element hopping around and a pile of CSS
 * keyframes. Rebuilt here as an actual simulation: the chibi is a sprite under
 * gravity that bounces off the edges of the screen, and tapping anywhere makes it
 * fling a card from your deck in that direction. It is a toy, and it is supposed
 * to be.
 *
 * Three things keep it from costing a frame budget it has not earned. Sparks are
 * drawn in a single [Canvas] rather than composed, so ninety of them are one draw
 * call and no composition at all. Cards — never more than ten — are real
 * composables so they get the same image loading as everywhere else, but their
 * positions are read inside `graphicsLayer`, which re-runs at draw time and
 * invalidates only its own layer. And the whole thing runs off one
 * [withFrameNanos] loop rather than one animation per object.
 */
@Composable
fun EasterEgg(
    pool: List<Card>,
    pinned: Boolean,
    onPin: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        val density = LocalDensity.current
        val bounds = with(density) { Bounds(maxWidth.toPx(), maxHeight.toPx()) }
        val chibiRadius = with(density) { 44.dp.toPx() }
        val cardHalfWidth = with(density) { 32.dp.toPx() }

        val chibi = remember { mutableStateOf(Sprite(x = 0f, y = 0f, vx = 0f, vy = 0f)) }
        val cards = remember { mutableStateListOf<FlyingCard>() }
        // Not snapshot state: ninety of these change every frame and nothing
        // should recompose because of it. The Canvas redraws off `tick` instead.
        val sparks = remember { mutableListOf<Sprite>() }
        var tick by remember { mutableStateOf(0L) }
        var thrown by remember { mutableStateOf(0) }
        val random = remember { Random(1) }

        LaunchedEffect(bounds.width, bounds.height) {
            if (bounds.width <= 0f || bounds.height <= 0f) return@LaunchedEffect

            chibi.value = Sprite(
                x = bounds.width / 2f,
                y = bounds.height / 3f,
                vx = 260f,
                vy = -120f,
                radius = chibiRadius,
            )

            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                    previous = now

                    chibi.value = SpritePhysics.step(
                        sprite = chibi.value,
                        dt = dt,
                        bounds = bounds,
                        gravity = GRAVITY * 0.35f,
                    )

                    // A trail, so the chibi is doing something rather than merely
                    // moving.
                    if (sparks.size < MAX_SPARKS) {
                        sparks += Sprite(
                            x = chibi.value.x + random.nextFloat() * 40f - 20f,
                            y = chibi.value.y + random.nextFloat() * 40f - 20f,
                            vx = random.nextFloat() * 160f - 80f,
                            vy = random.nextFloat() * 160f - 80f,
                            radius = 2f + random.nextFloat() * 4f,
                        )
                    }

                    // Indexed rather than `replaceAll`, which is a JVM library
                    // method and not part of the common standard library.
                    for (i in sparks.indices) {
                        sparks[i] = SpritePhysics.step(
                            sprite = sparks[i],
                            dt = dt,
                            bounds = bounds,
                            gravity = GRAVITY * 0.4f,
                            bounce = false,
                        )
                    }
                    sparks.removeAll { SpritePhysics.isSpent(it, bounds, SPARK_LIFETIME) }

                    cards.forEach { flying ->
                        flying.sprite = SpritePhysics.step(
                            sprite = flying.sprite,
                            dt = dt,
                            bounds = bounds,
                            gravity = GRAVITY,
                            bounce = false,
                        )
                        flying.motion.value = flying.sprite
                    }
                    cards.removeAll { SpritePhysics.isSpent(it.sprite, bounds, CARD_LIFETIME) }

                    tick = now
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pool) {
                    detectTapGestures { at ->
                        val card = pool.randomOrNull(random) ?: return@detectTapGestures
                        if (cards.size >= MAX_CARDS) cards.removeAt(0)

                        val from = chibi.value
                        val angle = atan2(at.y - from.y, at.x - from.x)
                        val speed = 900f + random.nextFloat() * 500f

                        cards += FlyingCard(
                            card,
                            Sprite(
                                x = from.x,
                                y = from.y,
                                vx = cos(angle) * speed,
                                vy = sin(angle) * speed - 300f,
                                spin = random.nextFloat() * 1200f - 600f,
                                radius = cardHalfWidth,
                            ),
                        )
                        thrown++
                    }
                },
        )

        val sparkColor = LocalMasterToolColors.current.accentBright

        Canvas(Modifier.fillMaxSize()) {
            // Reading the frame counter here is what schedules the next draw. The
            // sparks themselves are deliberately not snapshot state, so this is
            // the only thing tying them to the frame clock.
            if (tick != Long.MIN_VALUE) {
                sparks.forEach { spark ->
                drawCircle(
                    color = sparkColor.copy(
                        alpha = (1f - spark.age / SPARK_LIFETIME).coerceIn(0f, 1f),
                    ),
                        radius = spark.radius,
                        center = Offset(spark.x, spark.y),
                    )
                }
            }
        }

        cards.forEach { flying ->
            AsyncImage(
                model = flying.card.imageUrlSmall ?: flying.card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 64.dp, height = 94.dp)
                    .graphicsLayer {
                        val motion = flying.motion.value
                        translationX = motion.x - size.width / 2f
                        translationY = motion.y - size.height / 2f
                        rotationZ = motion.rotation
                    },
            )
        }

        Image(
            painter = painterResource(Res.drawable.chibi),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val motion = chibi.value
                    translationX = motion.x - size.width / 2f
                    translationY = motion.y - size.height / 2f
                    // Leans into wherever it is heading.
                    rotationZ = (motion.vx / 40f).coerceIn(-18f, 18f)
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (pool.isEmpty()) {
                    "Nothing to throw — put some cards in the deck"
                } else {
                    "$thrown thrown  ·  ${pool.size} in the pool"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = onPin) {
                Text(if (pinned) "Unpin pool" else "Pin this deck")
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

