package com.kaiharimoto.mastertool.ui.sandbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.board.BoardLayout
import com.kaiharimoto.mastertool.core.board.Placement
import com.kaiharimoto.mastertool.core.board.PlacedCard
import com.kaiharimoto.mastertool.core.board.ZoneId
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import kotlin.math.roundToInt

/**
 * The board, folded.
 *
 * A deck builder can tell you a list opens; it cannot tell you the opening
 * *does* anything, and nobody works that out by reading forty lines of text.
 * They lay the cards out. This is somewhere to lay them out.
 *
 * The fold is the thing worth getting right and it comes straight from the tool
 * this replaces: the far half tips away and the near half tips towards you,
 * hinged on the Extra Monster Zone row between them. It is not decoration. A
 * flat overhead grid is a spreadsheet of a board; tipping it means your own
 * cards are the ones facing you and theirs recede, which is what a table across
 * from somebody actually looks like.
 */
@Composable
fun SandboxScreen(
    state: SandboxState,
    index: CardIndex,
    onBack: () -> Unit,
) {
    val colors = LocalMasterToolColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to the deck")
            }
            Text("Sandbox", style = MaterialTheme.typography.titleMedium)
            Box(Modifier.width(14.dp))
            Text(
                "${state.table.library.size} LEFT   ·   ${state.table.hand.size} IN HAND",
                style = tacticalStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(Modifier.weight(1f))

            TextButton(onClick = { state.draw() }, enabled = state.table.library.isNotEmpty()) {
                Text("Draw")
            }
            TextButton(onClick = { state.redeal() }, enabled = state.canRedeal) {
                Text("Shuffle up")
            }
            // The one stack with no room on the mat, which is also the one a
            // player looks at least often. Still one tap away, and it says how
            // many are in it without being opened.
            TextButton(
                onClick = {
                    state.hold(if (state.held == DragOrigin.Token) null else DragOrigin.Token)
                },
            ) {
                Text(if (state.held == DragOrigin.Token) "Token — pick a zone" else "Token")
            }
            TextButton(onClick = { state.openPile = Pile.BANISHED }) {
                Text("Banished ${state.contentsOf(Pile.BANISHED).size}")
            }
            TextButton(onClick = { state.undo() }, enabled = state.canUndo) { Text("Undo") }
            TextButton(onClick = { state.clearBoard() }) { Text("Sweep") }
        }

        // Everything on the table is registered and hit-tested in this box's own
        // coordinates rather than the window's, so the board does not have to
        // know where on screen it happens to be sitting.
        var origin by remember { mutableStateOf(Offset.Zero) }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .tableSurface(accent = colors.accent, mat = colors.mat, corner = 8.dp)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // Seven zones across is the widest row, and everything else is sized
            // from that: a board that picked its own card size would need the
            // window to be a particular shape.
            val zoneWidth = ((maxWidth - ZONE_GAP * 6) / 7).coerceAtMost(MAX_ZONE_WIDTH)

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZONE_GAP),
            ) {
                Half(state, index, zoneWidth, origin, yours = false)
                ExtraMonsterRow(state, index, zoneWidth, origin)
                Half(state, index, zoneWidth, origin, yours = true)
                Hand(state, index, zoneWidth, origin)
            }

            DragGhost(state, index, zoneWidth)
        }
    }

    state.openPile?.let { PileSheet(state, it, index) }
}

/**
 * One player's two rows, tipped.
 *
 * The rotation is applied to the half as a whole rather than to each row,
 * because Compose has no equivalent of a nested 3D space — a rotated parent
 * flattens its children before they are rotated themselves. Which happens to be
 * exactly right here: a table half is a rigid plane, and the two rows on it
 * should recede together.
 */
@Composable
private fun Half(
    state: SandboxState,
    index: CardIndex,
    zoneWidth: Dp,
    origin: Offset,
    yours: Boolean,
) {
    Column(
        Modifier.graphicsLayer {
            rotationX = if (yours) -FOLD_DEGREES else FOLD_DEGREES
            transformOrigin = TransformOrigin(0.5f, if (yours) 0f else 1f)
            // The one number here that a real screen has to confirm. Compose's
            // units for it are density-relative rather than the CSS pixels the
            // prototype used, and erring long costs a little depth while erring
            // short would bend the far row into something unreadable.
            cameraDistance = CAMERA_DISTANCE
        },
        verticalArrangement = Arrangement.spacedBy(ZONE_GAP),
    ) {
        val rows = if (yours) {
            listOf(BoardLayout.monsterRow, BoardLayout.spellTrapRow)
        } else {
            // Read from across the table: their spells nearest the crease, their
            // monsters behind them, which is what you are looking at.
            listOf(BoardLayout.theirSpellTrapRow, BoardLayout.theirMonsterRow)
        }

        rows.forEachIndexed { position, zones ->
            Row(horizontalArrangement = Arrangement.spacedBy(ZONE_GAP)) {
                // Only your half has stacks. The deck across the table is not
                // simulated, and drawing its graveyard would be drawing a
                // graveyard nothing could ever put a card into.
                Flank(state, index, zoneWidth, origin, yours, position, left = true)
                zones.forEach { zone -> Zone(state, index, zone, zoneWidth, origin) }
                Flank(state, index, zoneWidth, origin, yours, position, left = false)
            }
        }
    }
}

/**
 * The stacks down the sides, where they are on a mat.
 *
 * Extra deck and graveyard flank the row you summon into; the field spell zone
 * and the deck flank the one below. Near enough to a real mat that muscle memory
 * finds them, and every stack opens.
 */
@Composable
private fun Flank(
    state: SandboxState,
    index: CardIndex,
    zoneWidth: Dp,
    origin: Offset,
    yours: Boolean,
    row: Int,
    left: Boolean,
) {
    if (!yours) {
        // Their spell row is the one nearest the crease, so their field spell
        // zone flanks it -- mirrored, because it is on their right.
        if (row == 0 && !left) {
            Zone(state, index, BoardLayout.theirField, zoneWidth, origin)
        } else {
            Gap(zoneWidth)
        }
        return
    }

    // The field spell zone is a zone, not a stack -- a card sits in it and can
    // be turned like any other. It goes where it goes on a mat: the far left of
    // the spell row.
    if (row == 1 && left) {
        Zone(state, index, BoardLayout.field, zoneWidth, origin)
        return
    }

    val pile = when {
        row == 0 && left -> Pile.EXTRA
        row == 0 -> Pile.GRAVEYARD
        else -> Pile.DECK
    }
    PileSlot(state, pile, zoneWidth, origin)
}

/**
 * The two shared zones, on the crease.
 *
 * Gold-edged and set apart, because in a real game they are the ones both
 * players are looking at and the only place on the mat that is nobody's half.
 */
@Composable
private fun ExtraMonsterRow(
    state: SandboxState,
    index: CardIndex,
    zoneWidth: Dp,
    origin: Offset,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ZONE_GAP),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Zone(state, index, BoardLayout.extraMonsterRow[0], zoneWidth, origin, shared = true)
        Box(Modifier.width(zoneWidth * 3 + ZONE_GAP * 2))
        Zone(state, index, BoardLayout.extraMonsterRow[1], zoneWidth, origin, shared = true)
    }
}

/**
 * One zone, and whatever is lying in it.
 *
 * Tapping a card turns it — attack, defence, face-down, round again — which is
 * the fallback for every gesture and the only way to correct one. Tapping an
 * empty zone puts the held card there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Zone(
    state: SandboxState,
    index: CardIndex,
    zone: ZoneId,
    zoneWidth: Dp,
    origin: Offset,
    shared: Boolean = false,
) {
    val colors = LocalMasterToolColors.current
    val haptics = LocalHapticFeedback.current
    val top = state.board[zone].lastOrNull()
    val held = state.held
    // Where this zone sits on the table, which both a drop and a drag need.
    var here by remember { mutableStateOf(Offset.Zero) }
    val edge = when {
        // Where the card in the air would land, which is the only thing worth
        // saying while it is still in the air.
        state.over == zone -> colors.accentBright
        shared -> MasterToolPalette.Warning.copy(alpha = 0.65f)
        // Their half reads quieter than yours. Both are real and both take
        // cards, but the board you are trying to break is the backdrop to the
        // one you are building.
        !zone.mine -> colors.mat.weft.copy(alpha = 0.55f)
        // Everywhere the held card could go, lit at once: a board with five open
        // zones should say which five without being poked at.
        held != null && top == null -> colors.accentBright.copy(alpha = 0.7f)
        // The zone the last card went into, so a board being built shows where
        // it grew rather than only that it is one card larger.
        state.justPlaced == zone -> colors.accentBright
        else -> colors.mat.weft
    }

    Box(
        Modifier
            .width(zoneWidth)
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(3.dp))
            .background(
                when {
                    shared -> MasterToolPalette.Warning.copy(alpha = 0.10f)
                    !zone.mine -> Color.Black.copy(alpha = 0.20f)
                    else -> Color.Black.copy(alpha = 0.14f)
                },
            )
            .onGloballyPositioned {
                val bounds = it.boundsInRoot().translate(-origin)
                here = bounds.topLeft
                state.registerZone(zone, bounds)
            }
            .then(
                if (top == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(zone, top.id) {
                        dragging(
                            state = state,
                            anchor = { here },
                            source = DragOrigin.OnBoard(zone),
                            id = top.id,
                            haptics = haptics,
                        )
                    }
                },
            )
            .border(1.dp, edge, RoundedCornerShape(3.dp))
            .combinedClickable(
                onClick = {
                    when {
                        // Something picked up goes down first: an occupied zone
                        // refuses it anyway, and turning a card you were not
                        // holding would be the wrong guess about what was meant.
                        held != null -> state.placeHeld(zone)
                        top != null -> state.turn(zone)
                        else -> Unit
                    }
                },
                // The only way back off the board that goes backwards. Every
                // other one -- graveyard, banished, another zone -- carries on
                // forwards, so a hand that slipped meant sweeping the table and
                // building it again.
                onLongClick = { if (held == null && top != null) state.toHand(zone) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (top != null) LaidCard(top, index, zoneWidth)
    }
}

/**
 * A card as it lies.
 *
 * Defence is drawn by turning the card ninety degrees rather than by drawing a
 * different-shaped card, so the rotation you see is the rotation the gesture
 * asked for. Face-down is the back of a card and shows nothing, because that is
 * the whole point of it — the sandbox is not going to be more helpful than the
 * game and tell you what your opponent set.
 */
@Composable
private fun LaidCard(placed: PlacedCard, index: CardIndex, zoneWidth: Dp) {
    val turned = placed.placement != Placement.ATTACK
    val angle by animateFloatAsState(
        targetValue = if (turned) -90f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "placement",
    )

    Box(
        Modifier
            .width(zoneWidth - 4.dp)
            .aspectRatio(CARD_ASPECT)
            .graphicsLayer { rotationZ = angle },
    ) {
        if (placed.token) {
            TokenFace(Modifier.fillMaxSize())
        } else if (placed.placement == Placement.SET) {
            CardBack(Modifier.fillMaxSize())
        } else {
            val card = index.byId(placed.id)
            if (card == null) {
                CardBack(Modifier.fillMaxSize())
            } else {
                CardTile(card = card, modifier = Modifier.fillMaxSize(), foil = false)
            }
        }
    }
}

/**
 * A token: a plate, not a card.
 *
 * Deliberately unlike both a card and a card back, because it is neither — it
 * has no art to show and no face to hide, and drawing it as either would be
 * saying something untrue about what is sitting in that zone.
 */
@Composable
private fun TokenFace(modifier: Modifier = Modifier) {
    val colors = LocalMasterToolColors.current

    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.accent.copy(alpha = 0.20f))
            .border(1.dp, colors.accentBright.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("TOKEN", style = tacticalStyle(), color = colors.accentBright)
    }
}

/** The reverse of a card: hatched, warm, and telling you nothing. */
@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MasterToolPalette.Warning.copy(alpha = 0.22f))
            .border(1.dp, MasterToolPalette.Warning.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
    )
}

/**
 * A stack: what it is, how many are in it, and a way in.
 *
 * Drawn as a slab rather than as a card, because it is not one card — and an
 * empty one still has to be tappable and still has to be somewhere, so it keeps
 * its outline whether or not anything is in it.
 */
@Composable
private fun PileSlot(state: SandboxState, pile: Pile, zoneWidth: Dp, origin: Offset) {
    val colors = LocalMasterToolColors.current
    val count = state.contentsOf(pile).size
    val edge = when {
        // Most of what a combo does is send cards here, so a pile that can be
        // dropped into says so the moment something is in the air.
        pile.zone != null && state.over == pile.zone -> colors.accentBright
        count == 0 -> colors.mat.weft.copy(alpha = 0.45f)
        else -> colors.mat.weft
    }

    Box(
        Modifier
            .width(zoneWidth)
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = if (count == 0) 0.06f else 0.22f))
            .onGloballyPositioned { coordinates ->
                pile.zone?.let { state.registerZone(it, coordinates.boundsInRoot().translate(-origin)) }
            }
            .border(1.dp, edge, RoundedCornerShape(3.dp))
            .clickable { state.openPile = pile },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${pile.shortLabel}\n$count",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Gap(zoneWidth: Dp) {
    Box(Modifier.width(zoneWidth))
}

/**
 * The hand, fanned.
 *
 * Overlapped and rotated about a point below the cards, which is what makes a
 * fan a fan rather than a row of tilted rectangles. The held card lifts out of
 * the fan and straightens, so what you are about to put down is the one card
 * that is fully readable.
 */
@Composable
private fun Hand(state: SandboxState, index: CardIndex, zoneWidth: Dp, origin: Offset) {
    val cards = state.table.hand
    if (cards.isEmpty()) return
    val haptics = LocalHapticFeedback.current

    val cardWidth = zoneWidth * 0.94f
    val middle = (cards.size - 1) / 2f

    // Each card takes a slot narrower than it is and draws over its neighbour,
    // which overlaps them without asking a Row for negative spacing.
    val slot = cardWidth * OVERLAP
    Row(
        Modifier.padding(top = 10.dp).height(cardWidth * 1.7f),
        verticalAlignment = Alignment.Bottom,
    ) {
        cards.forEachIndexed { position, id ->
            val fromMiddle = position - middle
            val heldHere = state.heldInHand == position
            val lift by animateFloatAsState(
                targetValue = if (heldHere) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "lift",
            )

            var here by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.width(if (position == cards.lastIndex) cardWidth else slot)) {
            Box(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(CARD_ASPECT)
                    .offset(y = (FAN_DROP * fromMiddle * fromMiddle) * (1f - lift))
                    .graphicsLayer {
                        rotationZ = FAN_DEGREES * fromMiddle * (1f - lift)
                        translationY = -lift * size.height * 0.18f
                        scaleX = 1f + lift * 0.06f
                        scaleY = 1f + lift * 0.06f
                        // Hinged below the card, which is what a hand of cards
                        // held in a fist actually pivots about.
                        transformOrigin = TransformOrigin(0.5f, 1.9f)
                    }
                    .onGloballyPositioned { here = it.positionInRoot() - origin }
                    .pointerInput(id, position) {
                        dragging(
                            state = state,
                            anchor = { here },
                            source = DragOrigin.Hand(position),
                            id = id,
                            haptics = haptics,
                        )
                    }
                    .clickable {
                        state.hold(if (heldHere) null else DragOrigin.Hand(position))
                    },
            ) {
                HandCard(id, index)
            }
            }
        }
    }
}

@Composable
private fun HandCard(id: CardId, index: CardIndex) {
    val card = index.byId(id)
    if (card == null) {
        CardBack(Modifier.fillMaxSize())
    } else {
        CardTile(card = card, modifier = Modifier.fillMaxSize(), foil = false)
    }
}

/**
 * Picking a card up, carrying it, and letting go.
 *
 * The whole point of the board is in the last line: nothing asks which way the
 * card should face, because the way you let go already said. A quick drop stands
 * it up, a flick lays it down, a moment's hold sets it face-down.
 *
 * Positions are converted into the board's own space once, at the start, and
 * carried forward by deltas — the alternative is asking every drag event where
 * on the screen its own composable is, sixty times a second.
 */
private suspend fun PointerInputScope.dragging(
    state: SandboxState,
    anchor: () -> Offset,
    source: DragOrigin,
    id: CardId,
    haptics: HapticFeedback,
) {
    detectDragGestures(
        // The gesture reports positions relative to this card; the board thinks
        // in its own space. Converted once, here, and carried forward by deltas.
        onDragStart = { at -> state.startDrag(source, id, anchor() + at) },
        onDrag = { change, delta ->
            change.consume()
            state.dragBy(delta)
        },
        onDragEnd = {
            // Felt only when the card actually landed. A drop the board refused
            // is a card going back where it came from, and a buzz would be the
            // program agreeing with something it had just refused.
            if (state.endDrag()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDragCancel = { state.cancelDrag() },
    )
}

/**
 * The card in the air.
 *
 * Lifted, tilted and shadowed, and drawn last so it passes over everything. It
 * does not preview the placement the gesture is about to choose: the gesture is
 * not finished until it is finished, and a card that flickered between upright
 * and sideways on the way down would be unreadable.
 */
@Composable
private fun DragGhost(state: SandboxState, index: CardIndex, zoneWidth: Dp) {
    val held = state.drag ?: return
    val card = index.byId(held.id) ?: return

    Box(
        Modifier
            .offset {
                IntOffset(
                    (state.pointer.x - (zoneWidth.toPx() / 2f)).roundToInt(),
                    (state.pointer.y - (zoneWidth.toPx() * 0.7f)).roundToInt(),
                )
            }
            .width(zoneWidth)
            .aspectRatio(CARD_ASPECT)
            .graphicsLayer {
                rotationZ = GHOST_TILT
                scaleX = GHOST_SCALE
                scaleY = GHOST_SCALE
                shadowElevation = 22f
            },
    ) {
        CardTile(card = card, modifier = Modifier.fillMaxSize(), foil = false)
    }
}

/** A card off the table is a card held between finger and thumb. */
private const val GHOST_TILT = -4f
private const val GHOST_SCALE = 1.08f

/** How far each half tips away from the plane of the screen. */
private const val FOLD_DEGREES = 11f

private const val CAMERA_DISTANCE = 18f

private const val CARD_ASPECT = 0.686f

/** How much of a hand card its neighbour leaves showing. */
private const val OVERLAP = 0.72f

/** Degrees between neighbouring cards in the fan. */
private const val FAN_DEGREES = 5.5f

private val ZONE_GAP = 6.dp
private val MAX_ZONE_WIDTH = 96.dp

/** How far the outer cards of the fan hang below the middle one. */
private val FAN_DROP = 2.6.dp
