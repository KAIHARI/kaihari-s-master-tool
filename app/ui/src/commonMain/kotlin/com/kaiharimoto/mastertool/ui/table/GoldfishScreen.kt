package com.kaiharimoto.mastertool.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.kaiharimoto.mastertool.core.hand.OpeningHand
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.PoseMotion
import com.kaiharimoto.mastertool.core.motion.PosePhysics
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardBack
import com.kaiharimoto.mastertool.ui.components.LocalCardBack
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import com.kaiharimoto.mastertool.ui.theme.ChromaticText
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.archivoExpandedFamily
import com.kaiharimoto.mastertool.ui.theme.prismaticBorder
import kotlin.math.abs
import kotlin.random.Random
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.delay

/** One card somewhere between the deck and the hand. */
private class DealtCard(index: Int, origin: Pose3) {
    var motion by mutableStateOf(PoseMotion.at(origin))
    var target by mutableStateOf(origin)
    var revealed by mutableStateOf(false)
    val handIndex = index
}

/**
 * The goldfish table: the deck as a physical stack on a perspective surface.
 *
 * Deal five or six; the cards fly one by one from the top of the stack into a
 * fanned hand, face down, and flip on tap. Mulligan sweeps them back and
 * deals again; Draw plays past the opener. The whole scene is the Phase 6
 * stage pattern — one tilted parent layer for the table, flat overlay springs
 * for anything that leaves it — and the deal itself is `core/hand`'s seeded
 * engine, so an open you want to talk about can be reproduced exactly.
 *
 * The group chip under the hand is the payoff of the breakdown: every open
 * reports itself in the user's own vocabulary — "2 starters, 1 handtrap".
 */
@Composable
fun GoldfishScreen(
    state: DeckBuilderState,
    onBack: () -> Unit,
) {
    val feedback = LocalFeedback.current
    val back = LocalCardBack.current
    var session by remember { mutableStateOf<OpeningHand?>(null) }
    val dealt = remember { mutableStateListOf<DealtCard>() }
    var handSize by remember { mutableStateOf(5) }

    // One loop steps every card in flight, per the sanctioned frame recipe.
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                previous = now
                dealt.forEach { card ->
                    card.motion = PosePhysics.step(card.motion, card.target, SpringSpec.Bouncy, dt)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val cardW = with(density) { (maxWidth * 0.11f).toPx() }
        val cardH = cardW / CARD_ASPECT_RATIO

        // Where the top of the deck sits, and where hand slot [i] of [n] sits —
        // both in root coordinates, since dealt cards live on the flat overlay.
        val deckX = widthPx * 0.82f
        val deckY = heightPx * 0.38f
        fun handPose(i: Int, n: Int): Pose3 {
            val centre = (n - 1) / 2f
            val spread = cardW * 1.12f
            return Pose3(
                position = Vec3(
                    widthPx * 0.5f + (i - centre) * spread - cardW / 2f,
                    heightPx * 0.66f - abs(i - centre) * cardH * 0.03f,
                    0f,
                ),
                rotZ = (i - centre) * 3.5f,
                scale = 1f,
            )
        }

        fun deal(size: Int) {
            val deck = state.deck.main
            if (deck.size < size) return
            handSize = size
            session = OpeningHand.deal(deck, seed = Random.nextLong(), handSize = size)
            dealt.clear()
            feedback.play(SoundEffect.SHUFFLE)
        }

        fun mulligan() {
            val current = session ?: return
            session = current.mulligan(handSize)
            dealt.clear()
            feedback.play(SoundEffect.SHUFFLE)
        }

        fun draw() {
            val current = session ?: return
            if (current.drawn >= current.library.size) return
            session = current.drawOne()
        }

        // When the session's hand grows, fly the new cards out of the stack.
        LaunchedEffect(session?.hand?.size, session) {
            val current = session ?: return@LaunchedEffect
            val n = current.hand.size
            while (dealt.size < n) {
                val i = dealt.size
                val card = DealtCard(
                    index = i,
                    origin = Pose3(
                        position = Vec3(deckX - cardW / 2f, deckY - cardH / 2f, 0f),
                        rotZ = -6f,
                        scale = 0.92f,
                    ),
                )
                dealt += card
                card.target = handPose(i, n)
                feedback.play(SoundEffect.DEAL)
                delay(110)
            }
            // Re-fan everything for the final count.
            dealt.forEachIndexed { i, card -> card.target = handPose(i, n) }
        }

        // ---- the table plane, one perspective layer --------------------------
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = 32f
                    cameraDistance = 24f * this.density
                },
        ) {
            // The surface: a hairline field on black, nothing skeuomorphic.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MasterToolPalette.Surface),
            )

            // The deck: a few edge slices and a back, standing where dealt
            // cards take off from.
            val sliceCount = 7
            repeat(sliceCount) { slice ->
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (deckX - cardW / 2).toInt(),
                                (deckY - cardH / 2 - slice * 2.2f).toInt(),
                            )
                        }
                        .width(with(density) { cardW.toDp() })
                        .height(with(density) { cardH.toDp() })
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (slice == sliceCount - 1) {
                                MasterToolPalette.SurfaceHigh
                            } else {
                                MasterToolPalette.SurfaceRaised
                            },
                        )
                        .then(
                            if (slice == sliceCount - 1) {
                                Modifier.prismaticBorder(
                                    angle = 135f,
                                    cornerRadius = 6.dp,
                                    stroke = 1.5.dp,
                                    alpha = 0.5f,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            Text(
                "${session?.remaining?.size ?: state.deck.main.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MasterToolPalette.TextMuted,
                modifier = Modifier.offset {
                    IntOffset((deckX - cardW / 2).toInt(), (deckY + cardH / 2 + 14).toInt())
                },
            )
        }

        // ---- dealt cards: flat overlay, one spring each ---------------------
        session?.let { current ->
            dealt.forEach { card ->
                val id = current.hand.getOrNull(card.handIndex) ?: return@forEach
                val art = state.index.byId(id)

                Box(
                    Modifier
                        .width(with(density) { cardW.toDp() })
                        .height(with(density) { cardH.toDp() })
                        .graphicsLayer {
                            val pose = card.motion.pose
                            translationX = pose.position.x
                            translationY = pose.position.y
                            rotationZ = pose.rotZ
                            rotationY = pose.rotY
                            scaleX = pose.scale
                            scaleY = pose.scale
                            cameraDistance = 16f * this.density
                            shadowElevation = 10.dp.toPx()
                            shape = RoundedCornerShape(6.dp)
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .background(MasterToolPalette.SurfaceRaised)
                        .clickable {
                            if (!card.revealed) {
                                card.revealed = true
                                // Flip in place: half turn, art appears edge-on.
                                card.target = card.target.copy(rotY = 0f)
                                card.motion = card.motion.copy(
                                    pose = card.motion.pose.copy(rotY = 180f),
                                )
                                feedback.play(SoundEffect.SLIDE)
                            } else {
                                state.heldCard = art
                            }
                        },
                ) {
                    val facingAway =
                        ((card.motion.pose.rotY % 360f) + 360f) % 360f in 90f..270f
                    if (!card.revealed || facingAway || art == null) {
                        // A face-down card wears a card back, here and
                        // everywhere else in the app.
                        CardBack(
                            modifier = Modifier.fillMaxSize(),
                            style = back.style,
                            imageUrl = back.imageUrl,
                            cornerRadius = 6.dp,
                        )
                    } else {
                        AsyncImage(
                            model = art.imageUrlSmall ?: art.imageUrl,
                            contentDescription = art.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // ---- flat chrome ----------------------------------------------------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChromaticText(
                "GOLDFISH",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = archivoExpandedFamily(),
                ),
                color = Color.White,
            )
            session?.let { current ->
                val tally = current.tally(state.groups)
                val parts = state.groups.ordered()
                    .mapNotNull { group ->
                        tally[group.id]?.let { "$it ${group.name.lowercase()}" }
                    }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MasterToolPalette.TextMuted,
                    )
                }
                if (current.mulligans > 0) {
                    Text(
                        "${current.mulligans} mulligan${if (current.mulligans == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MasterToolPalette.TextMuted,
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { deal(5) }) { Text("Deal 5") }
            TextButton(onClick = { deal(6) }) { Text("Deal 6") }
            TextButton(onClick = { mulligan() }, enabled = session != null) { Text("Mulligan") }
            TextButton(onClick = { draw() }, enabled = session != null) { Text("Draw") }
            TextButton(onClick = onBack) { Text("Back to builder") }
        }
    }
}
