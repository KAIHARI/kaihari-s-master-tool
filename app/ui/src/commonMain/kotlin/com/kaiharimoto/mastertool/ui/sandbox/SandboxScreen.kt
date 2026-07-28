package com.kaiharimoto.mastertool.ui.sandbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.board.BoardLayout
import com.kaiharimoto.mastertool.core.board.Placement
import com.kaiharimoto.mastertool.core.board.PlacedCard
import com.kaiharimoto.mastertool.core.board.ZoneId
import com.kaiharimoto.mastertool.core.board.ZoneKind
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

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
            TextButton(onClick = { state.undo() }, enabled = state.canUndo) { Text("Undo") }
            TextButton(onClick = { state.clearBoard() }) { Text("Sweep") }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .tableSurface(accent = colors.accent, mat = colors.mat, corner = 8.dp)
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
                Half(state, index, zoneWidth, yours = false)
                ExtraMonsterRow(state, index, zoneWidth)
                Half(state, index, zoneWidth, yours = true)
                Hand(state, index, zoneWidth)
            }
        }
    }
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
            listOf(BoardLayout.spellTrapRow, BoardLayout.monsterRow)
        }

        rows.forEachIndexed { position, zones ->
            // Piles sit at the ends of the row furthest from the middle, which
            // is where a player's hands are and where they are on a real mat.
            val flanked = if (yours) position == 1 else position == 0
            Row(horizontalArrangement = Arrangement.spacedBy(ZONE_GAP)) {
                if (flanked) Pile(state, BoardLayout.deck, "DECK", zoneWidth) else Gap(zoneWidth)
                zones.forEach { zone -> Zone(state, index, zone, zoneWidth) }
                if (flanked) {
                    Pile(state, BoardLayout.graveyard, "GY", zoneWidth)
                } else {
                    Gap(zoneWidth)
                }
            }
        }
    }
}

/**
 * The two shared zones, on the crease.
 *
 * Gold-edged and set apart, because in a real game they are the ones both
 * players are looking at and the only place on the mat that is nobody's half.
 */
@Composable
private fun ExtraMonsterRow(state: SandboxState, index: CardIndex, zoneWidth: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ZONE_GAP),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Zone(state, index, BoardLayout.extraMonsterRow[0], zoneWidth, shared = true)
        Box(Modifier.width(zoneWidth * 3 + ZONE_GAP * 2))
        Zone(state, index, BoardLayout.extraMonsterRow[1], zoneWidth, shared = true)
    }
}

/**
 * One zone, and whatever is lying in it.
 *
 * Tapping a card turns it — attack, defence, face-down, round again — which is
 * the fallback for every gesture and the only way to correct one. Tapping an
 * empty zone puts the held card there.
 */
@Composable
private fun Zone(
    state: SandboxState,
    index: CardIndex,
    zone: ZoneId,
    zoneWidth: Dp,
    shared: Boolean = false,
) {
    val colors = LocalMasterToolColors.current
    val top = state.board[zone].lastOrNull()
    val held = state.heldInHand
    val edge = when {
        shared -> MasterToolPalette.Warning.copy(alpha = 0.65f)
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
                if (shared) {
                    MasterToolPalette.Warning.copy(alpha = 0.10f)
                } else {
                    Color.Black.copy(alpha = 0.14f)
                },
            )
            .border(1.dp, edge, RoundedCornerShape(3.dp))
            .clickable {
                when {
                    top != null -> state.turn(zone)
                    held != null -> state.play(held, zone, Placement.ATTACK)
                    else -> Unit
                }
            },
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
        if (placed.placement == Placement.SET) {
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

/** A pile: how many, and the top of it. */
@Composable
private fun Pile(state: SandboxState, zone: ZoneId, label: String, zoneWidth: Dp) {
    val count = state.board[zone].size

    Box(
        Modifier
            .width(zoneWidth)
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(3.dp))
            .border(
                1.dp,
                LocalMasterToolColors.current.mat.weft.copy(alpha = 0.6f),
                RoundedCornerShape(3.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shown = when (zone.kind) {
            ZoneKind.DECK -> state.table.library.size
            else -> count
        }
        Text(
            "$label\n$shown",
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
private fun Hand(state: SandboxState, index: CardIndex, zoneWidth: Dp) {
    val cards = state.table.hand
    if (cards.isEmpty()) return

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
                    .clickable { state.hold(if (heldHere) null else position) },
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
