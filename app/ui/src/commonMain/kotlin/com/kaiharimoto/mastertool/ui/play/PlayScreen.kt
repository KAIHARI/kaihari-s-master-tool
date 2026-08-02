package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.board.BoardCard
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlacedCard
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.toPixels
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.mat.MatGestureMachine
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardBack
import com.kaiharimoto.mastertool.ui.components.LocalCardBack
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI

/** How far a carried card rises off the mat, as a share of its own height. */
private const val LIFT_Z = 0.42f

/** A card raised to be *read* comes closer than one being slid. */
private const val HAND_LIFT = LIFT_Z * 1.6f

/** How far a hand card leans back, so hand and table read as two objects. */
private const val HAND_LEAN = -7f

/**
 * A stack's offset per card, exaggerated about four times.
 *
 * A real three-card pile is a millimetre. At tablet scale that is well under a
 * pixel and simply invisible, and the thing that needs communicating is only
 * "there is more than one here" — so the offset is notation rather than
 * measurement, and it is capped so a twenty-card pile is not a tower.
 */
private const val STACK_SLIVER = 0.020f
private const val STACK_MAX_DRAWN = 4

private val TOP_BAR = 44.dp

/**
 * The play stage: a deck, a table, and nothing telling you what you may do.
 *
 * This is the fishbowl grown up. The goldfish screen dealt cards onto a
 * perspective surface and let you look at them; here you pick them up, put them
 * anywhere, turn them over, stack them, and sweep them into the graveyard —
 * with the rules of the game living entirely in your head, which is what a
 * table is.
 *
 * Three layers, and one invariant that removes the need to sort anything:
 * **a card resting on the mat has z = 0, and anything with z above zero lives
 * on the flat layer above.** Parent order then answers every occlusion question
 * — plane, then air — so a resting card's transform collapses to a position and
 * a scale, and there is no per-card depth sort anywhere.
 *
 * Motion is one `withFrameNanos` loop over plain lists, with each card's pose
 * read inside its own `graphicsLayer`. Poses live in [StageCard] objects held by
 * the screen rather than remembered in composables, because a card picked up
 * changes parent — mat to air — which destroys and recreates its composable,
 * and a pose that reset at the moment of pickup would be a pose that reset on
 * the one frame it must not.
 */
@Composable
fun PlayScreen(state: DeckBuilderState, onBack: () -> Unit) {
    val deck = state.deck
    val play = remember(deck.main, deck.extra) { PlayState(deck.main, deck.extra) }
    val cards = remember(deck.main, deck.extra) { mutableMapOf<Int, StageCard>() }
    val machine = remember(deck.main, deck.extra) { MatGestureMachine() }
    val feedback = LocalFeedback.current
    val back = LocalCardBack.current
    var menuFor by remember { mutableStateOf<DragOrigin?>(null) }

    Box(Modifier.fillMaxSize().background(MasterToolPalette.Ink)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = TOP_BAR)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            val stage = remember(widthPx, heightPx) { StagePlane.forStage(widthPx, heightPx) }
            val layout = remember(widthPx, heightPx, stage) {
                BoardLayouter.solve(
                    width = widthPx,
                    height = heightPx,
                    aspectRatio = CARD_ASPECT_RATIO,
                    // The stage reporting on itself, rather than a constant that
                    // would drift the first time the tilt changed.
                    perspectiveGrowth = stage.perspectiveGrowth,
                )
            }

            if (!layout.fits) {
                Text(
                    "Not enough room to lay a table out here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@BoxWithConstraints
            }

            val seats = remember(play.field, layout, play.carry) {
                seatsFor(play.field, layout, play.carry)
            }

            // Aim every card at where it now belongs. Done outside the frame
            // loop because it only changes when the board does.
            seats.forEach { seat ->
                val card = cards.getOrPut(seat.id) { StageCard(seat.id).also { it.placeAt(seat.pose) } }
                card.pinned = seat.carried
                card.aimAt(seat.pose)
            }

            // ---- the one loop -------------------------------------------------
            LaunchedEffect(Unit) {
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                        previous = now
                        // A finger held perfectly still produces no pointer
                        // events, so the long press has to be driven from here.
                        machine.onTick(now / 1_000_000L)
                        cards.values.forEach { it.step(SpringSpec.Bouncy, dt.coerceAtMost(0.05f)) }
                    }
                }
            }

            // ---- layer one: the tilted plane ------------------------------------
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = stage.tiltDegrees
                        cameraDistance = stage.cameraDistance / this.density
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawMat(layout)
                    drawIndicator(play.carry?.intent, layout)
                    seats.filter { !it.carried }.forEach { drawContact(it, layout) }
                }

                seats.filter { !it.carried }.forEach { seat ->
                    StagedCard(seat, cards, layout, state, back, density)
                }
            }

            // ---- layer two: the air ---------------------------------------------
            // Anything with z above zero, drawn flat and projected by hand so it
            // leaves the plane without a seam.
            seats.filter { it.carried }.forEach { seat ->
                StagedCard(seat, cards, layout, state, back, density, stage = stage)
            }

            MatInput(
                play = play,
                machine = machine,
                layout = layout,
                stage = stage,
                feedback = feedback,
                onMenu = { menuFor = it },
            )

            menuFor?.let { origin ->
                CardActions(play, origin, onDismiss = { menuFor = null })
            }
        }

        PlayTopBar(play, onBack)
    }
}

/** One card, and everything the stage needs to know about drawing it. */
private data class Seat(
    val id: Int,
    val card: BoardCard,
    val pose: Pose3,
    val faceUp: Boolean,
    val carried: Boolean,
    val depth: Int,
    val materials: Int,
    val counters: Int,
    val width: Float,
    val height: Float,
)

/**
 * Every card that is visible, and where it belongs.
 *
 * Ordered back to front by depth on the mat, with recency breaking ties —
 * `PlayField.mat` is ordered by recency alone, which is right for two cards in
 * the same place and wrong for two at different depths, because on a tilted
 * plane a card played early near the front must still occlude one played later
 * further back.
 */
private fun seatsFor(field: PlayField, layout: BoardLayout, carry: Carry?): List<Seat> {
    val seats = mutableListOf<Seat>()
    val cardWidth = layout.cardWidth
    val cardHeight = layout.cardHeight

    fun poseAt(at: MatPoint, z: Float, turned: Boolean, faceUp: Boolean, lean: Float = 0f): Pose3 {
        val (x, y) = layout.toPixels(at)
        return Pose3(
            position = Vec3(x, y, z),
            rotX = lean,
            // One truth about which face shows: half a turn means the back.
            rotY = if (faceUp) 0f else 180f,
            rotZ = if (turned) -90f else 0f,
            scale = 1f,
        )
    }

    field.mat.forEach { placed ->
        val carrying = carry?.id == placed.id
        seats += Seat(
            id = placed.id,
            card = placed.card,
            pose = poseAt(
                at = if (carrying) carry.at else placed.at,
                z = if (carrying) cardHeight * LIFT_Z else 0f,
                turned = placed.turned || (carrying && carry.quarterTurns % 2 != 0),
                faceUp = placed.faceUp,
            ),
            faceUp = placed.faceUp,
            carried = carrying,
            depth = placed.depth,
            materials = placed.card.materials.size,
            counters = placed.card.counters,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // Depth first, recency second. `mat` is ordered by recency alone, which is
    // right for two cards in the same place and wrong for two at different
    // depths: on a tilted plane a card played early near the front must still
    // occlude one played later further back.
    seats.sortWith(compareBy({ quantised(it.pose.position.y, layout.field.height) }, { it.id }))

    // The hand, fanned along the band the solver set aside.
    field.hand.forEachIndexed { index, card ->
        val carrying = carry?.from is DragOrigin.Hand &&
            (carry.from as DragOrigin.Hand).index == index
        val at = handPointFor(layout, index, field.hand.size)
        seats += Seat(
            id = card.instanceId,
            card = card,
            pose = poseAt(
                at = if (carrying) carry.at else at,
                z = if (carrying) cardHeight * HAND_LIFT else 0f,
                turned = false,
                faceUp = true,
                lean = if (carrying) 0f else HAND_LEAN,
            ),
            faceUp = true,
            carried = carrying,
            depth = 1,
            materials = 0,
            counters = 0,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // The tops of the piles, so a stack is a card and a number the way a stack is.
    listOf(
        BoardSlot.Deck to field.deck,
        BoardSlot.ExtraDeck to field.extraDeck,
        BoardSlot.Graveyard to field.graveyard,
        BoardSlot.Banished to field.banished,
    ).forEach { (slot, pile) ->
        val top = pile.firstOrNull() ?: return@forEach
        val rect = layout[slot] ?: return@forEach
        val faceUp = slot == BoardSlot.Graveyard || slot == BoardSlot.Banished
        seats += Seat(
            id = top.instanceId,
            card = top,
            pose = Pose3(
                position = Vec3(rect.centerX, rect.centerY, 0f),
                rotY = if (faceUp) 0f else 180f,
            ),
            faceUp = faceUp,
            carried = false,
            depth = min(pile.size, STACK_MAX_DRAWN + 1),
            materials = 0,
            counters = 0,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // A card being carried out of a pile has no seat of its own yet — it is
    // still in the pile as far as the field is concerned — so it gets one here,
    // or dragging out of the graveyard would carry something invisible.
    val from = carry?.from
    if (from is DragOrigin.Pile) {
        val pile = when (from.pile) {
            BoardSlot.Deck -> field.deck
            BoardSlot.ExtraDeck -> field.extraDeck
            BoardSlot.Graveyard -> field.graveyard
            BoardSlot.Banished -> field.banished
            is BoardSlot.Zone -> emptyList()
        }
        pile.getOrNull(from.index)?.let { card ->
            seats += Seat(
                id = card.instanceId,
                card = card,
                pose = poseAt(carry.at, cardHeight * LIFT_Z, carry.quarterTurns % 2 != 0, true),
                faceUp = true,
                carried = true,
                depth = 1,
                materials = 0,
                counters = 0,
                width = cardWidth,
                height = cardHeight,
            )
        }
    }

    return seats
}

/** Quantised so a small wobble in y cannot reshuffle the whole paint order. */
private fun quantised(y: Float, height: Float): Int =
    if (height <= 0f) 0 else (y / (height * 0.02f)).toInt()

/**
 * Where hand card [index] of [count] sits.
 *
 * A row that fans rather than an arc that curls: an arc is lovely at six cards
 * and unreadable at fourteen, and a combo line routinely holds fourteen. The
 * cards overlap only as far as they must, so the common case still bows.
 */
internal fun handPointFor(layout: BoardLayout, index: Int, count: Int): MatPoint {
    val band = layout.hand
    val step = if (count <= 1) {
        0f
    } else {
        min(layout.cardWidth * 0.62f, (band.width - layout.cardWidth) / (count - 1))
    }
    val spread = layout.cardWidth + step * (count - 1)
    val x = band.left + (band.width - spread) / 2f + layout.cardWidth / 2f + index * step

    return MatPoint(
        x = if (layout.field.width > 0f) (x - layout.field.left) / layout.field.width else 0.5f,
        y = if (layout.field.height > 0f) {
            (band.centerY - layout.field.top) / layout.field.height
        } else {
            1f
        },
    )
}

/** The felt, its zones, and the pool of light that makes a shadow possible at all. */
private fun DrawScope.drawMat(layout: BoardLayout) {
    val mat = layout.field

    // A true-black table cannot receive a dark shadow — there is nothing to
    // take away. So the mat carries a little light, and a card resting on it
    // removes some.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.045f), Color.Transparent),
            center = Offset(mat.centerX, mat.centerY),
            radius = maxOf(mat.width, mat.height) * 0.75f,
        ),
        topLeft = Offset(mat.left - mat.width * 0.1f, mat.top - mat.height * 0.1f),
        size = Size(mat.width * 1.2f, mat.height * 1.2f),
    )

    layout.slots.forEach { (slot, rect) ->
        val pile = slot !is BoardSlot.Zone
        drawRoundRect(
            color = Color.White.copy(alpha = if (pile) 0.10f else 0.06f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rect.width * 0.05f),
            style = Stroke(width = rect.width * 0.008f),
        )
    }
}

/** The contact shadow: a card resting on the mat takes light out of it. */
private fun DrawScope.drawContact(seat: Seat, layout: BoardLayout) {
    val w = seat.width * 0.50f
    val h = seat.height * 0.28f
    drawOval(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(
            seat.pose.position.x - w / 2f,
            seat.pose.position.y + seat.height * 0.30f - h / 2f,
        ),
        size = Size(w, h),
    )
}

/**
 * Where the card in the air is going to land, drawn on the mat under it.
 *
 * The indicator and the outcome are the same value — `DropTargets` decided it
 * once and both the highlight and the release read that decision — so the table
 * cannot promise one thing and do another.
 */
private fun DrawScope.drawIndicator(intent: DropIntent?, layout: BoardLayout) {
    if (intent == null) return

    val accent = MasterToolPalette.AccentBright
    fun ring(rect: Slot, alpha: Float) {
        drawRoundRect(
            color = accent.copy(alpha = alpha),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rect.width * 0.05f),
            style = Stroke(width = rect.width * 0.035f),
        )
    }

    when (intent) {
        is DropIntent.Zone -> layout[intent.slot]?.let { ring(it, 0.85f) }
        DropIntent.Graveyard -> layout[BoardSlot.Graveyard]?.let { ring(it, 0.85f) }
        DropIntent.Banish -> layout[BoardSlot.Banished]?.let { ring(it, 0.85f) }
        DropIntent.Deck -> layout[BoardSlot.Deck]?.let { ring(it, 0.85f) }
        DropIntent.ExtraDeck -> layout[BoardSlot.ExtraDeck]?.let { ring(it, 0.85f) }
        DropIntent.Hand -> ring(layout.hand, 0.6f)
        else -> Unit
    }
}

/**
 * One card on the stage.
 *
 * Both faces are always composed, each setting its own alpha inside its own
 * `graphicsLayer`. Picking a face by reading the angle in the composable body
 * would recompose the card on every frame of a flip, which is the thing the
 * whole one-loop pattern exists to avoid — and you cannot branch inside a
 * `graphicsLayer` block, because it is a draw lambda, not composition.
 */
@Composable
private fun StagedCard(
    seat: Seat,
    cards: MutableMap<Int, StageCard>,
    layout: BoardLayout,
    state: DeckBuilderState,
    back: com.kaiharimoto.mastertool.ui.components.CardBackChoice,
    density: androidx.compose.ui.unit.Density,
    stage: StagePlane? = null,
) {
    val motion = cards[seat.id] ?: return
    val art = state.index.byId(seat.card.cardId)

    with(density) {
        Box(
            Modifier
                .size(seat.width.toDp(), seat.height.toDp())
                .graphicsLayer {
                    val pose = motion.pose
                    // On the plane a card is flat and its scale is one; in the
                    // air the projection is applied here by hand, so the two
                    // agree exactly at the moment it leaves.
                    val projected = stage?.project(pose.position.x, pose.position.y, pose.position.z)
                    val x = projected?.x ?: pose.position.x
                    val y = projected?.y ?: pose.position.y
                    val lift = projected?.scale ?: 1f

                    translationX = x - seat.width / 2f
                    translationY = y - seat.height / 2f
                    rotationX = pose.rotX
                    rotationY = pose.rotY
                    rotationZ = pose.rotZ
                    scaleX = lift * pose.scale
                    scaleY = lift * pose.scale
                    cameraDistance = (seat.width * 6f) / this.density
                },
        ) {
            // Stacked cards, as slivers behind the top one.
            repeat(min(seat.depth - 1, STACK_MAX_DRAWN)) { i ->
                val offset = seat.width * STACK_SLIVER * (i + 1)
                Box(
                    Modifier
                        .size(seat.width.toDp(), seat.height.toDp())
                        .graphicsLayer {
                            translationX = -offset
                            translationY = -offset
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(MasterToolPalette.SurfaceHigh),
                )
            }

            CardFace(art, faceUp = true, back = back, motion = motion)
            CardFace(art, faceUp = false, back = back, motion = motion)

            if (seat.counters > 0 || seat.materials > 0) {
                Badge(
                    text = if (seat.counters > 0) "●${seat.counters}" else "◈${seat.materials}",
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

/**
 * One side of a card.
 *
 * Its own layer, whose alpha is a function of the turn — so a flip swaps face
 * for back at exactly ninety degrees, where the card is edge-on and neither is
 * visible anyway, without composition being involved at all.
 */
@Composable
private fun CardFace(
    art: Card?,
    faceUp: Boolean,
    back: com.kaiharimoto.mastertool.ui.components.CardBackChoice,
    motion: StageCard,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val turn = motion.pose.rotY * (PI.toFloat() / 180f)
                val facing = cos(turn)
                alpha = if (faceUp) {
                    if (facing > 0f) 1f else 0f
                } else {
                    if (facing > 0f) 0f else 1f
                }
                // The back is drawn mirrored, or it would read as reversed once
                // the card has turned to show it.
                if (!faceUp) rotationY = 180f
            },
    ) {
        if (faceUp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MasterToolPalette.SurfaceRaised),
            ) {
                if (art != null) {
                    Text(
                        art.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(2.dp),
                    )
                    AsyncImage(
                        model = art.imageUrlSmall ?: art.imageUrl,
                        contentDescription = art.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        } else {
            CardBack(Modifier.fillMaxSize(), back.style, back.imageUrl)
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MasterToolPalette.Ink.copy(alpha = 0.82f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MasterToolPalette.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayTopBar(play: PlayState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(TOP_BAR).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarButton("← Deck", onClick = onBack)
        Divider()
        Text(
            play.field.lifePoints.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (play.field.lifePoints <= 0) MasterToolPalette.Danger else Color.Unspecified,
        )
        listOf(-1000, -500, -100, 100).forEach { delta ->
            BarButton(if (delta > 0) "+$delta" else "$delta") {
                play.move { it.adjustLifePoints(delta) }
            }
        }
        Divider()
        BarButton(play.field.phase.label) { play.move { it.nextPhase() } }
        BarButton("Turn ${play.field.turn} ⟳") { play.move { it.endTurn() } }

        Box(Modifier.weight(1f))

        play.announcement?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MasterToolPalette.AccentBright,
            )
        }
        BarButton("Draw") { play.move { it.draw() } }
        BarButton("Shuffle") { play.move { it.shuffleDeck(it.turn * 31L + it.deck.size) } }
        BarButton("Undo", enabled = play.canUndo) { play.undo() }
        BarButton("Redo", enabled = play.canRedo) { play.redo() }
        BarButton("New hand") { play.restart() }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun BarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(2.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            maxLines = 1,
        )
    }
}

/** Whether a point in mat pixels is inside a rectangle, with a little grace. */
internal fun Slot.holds(x: Float, y: Float, grace: Float = 0f): Boolean =
    x >= left - grace && x <= right + grace && y >= top - grace && y <= bottom + grace


/**
 * Everything a card can be told to do that is not a movement.
 *
 * Behind the two-finger hold, which keeps every one-finger gesture purely about
 * where a card is. The pointer idiom is a right-click on the same card, and the
 * keyboard one is the shortcut table — three producers, one menu.
 */
@Composable
private fun CardActions(play: PlayState, origin: DragOrigin, onDismiss: () -> Unit) {
    val id = (origin as? DragOrigin.Mat)?.id

    androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        // A lambda rather than a local `fun`, because a local function cannot be
        // @Composable and DropdownMenuItem is one. Every entry below dismisses
        // first and acts second, so the menu never outlives the card it is about.
        val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label) },
                onClick = { onDismiss(); action() },
            )
        }

        if (id != null) {
            val placed = play.field.placed(id)
            item("Turn over") { play.move { it.flip(id) } }
            item("Rotate") { play.move { it.rotate(id) } }
            item("Bring to front") { play.move { it.bringToFront(id) } }
            androidx.compose.material3.HorizontalDivider()
            item("To graveyard") { play.move { it.toGraveyard(id) } }
            item("Banish") { play.move { it.toBanish(id) } }
            item("Banish face-down") { play.move { it.toBanish(id, faceDown = true) } }
            item("To hand") { play.move { it.toHand(id) } }
            item("To deck top") { play.move { it.toDeckTop(id) } }
            item("To deck bottom") { play.move { it.toDeckBottom(id) } }
            androidx.compose.material3.HorizontalDivider()
            if ((placed?.depth ?: 1) > 1) {
                item("Take the top card off") {
                    play.move { field -> field.unstack(id, placed!!.at.let { it.copy(x = it.x + 0.06f) }) }
                }
            }
            if ((placed?.card?.materials?.size ?: 0) > 0) {
                item("Detach a material") { play.move { it.detachMaterial(id) } }
            }
            item("Add a counter") { play.move { it.addCounter(id, 1) } }
            if ((placed?.card?.counters ?: 0) > 0) {
                item("Remove a counter") { play.move { it.addCounter(id, -1) } }
            }
        } else if (origin is DragOrigin.Pile) {
            item("Shuffle the deck") { play.move { it.shuffleDeck(it.turn * 31L + it.deck.size) } }
            item("Draw") { play.move { it.draw() } }
        }
    }
}
