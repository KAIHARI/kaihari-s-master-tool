package com.kaiharimoto.mastertool.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.board.BoardCard
import com.kaiharimoto.mastertool.core.board.BoardState
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.FieldZone
import com.kaiharimoto.mastertool.core.deck.DeckLenses
import com.kaiharimoto.mastertool.core.deck.Lens
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.model.Card
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardBack
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.LocalCardBack
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.ui.deckbuilder.color
import com.kaiharimoto.mastertool.ui.fx.Feedback
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/** The strip of controls above the table, and the height the solver is told about. */
private val TOP_BAR = 44.dp

/**
 * How far the table is tipped away from you.
 *
 * Nine degrees, which is less than it sounds — enough that the far row reads as
 * further away and the near row as the one under your hands, and not so much
 * that the graveyard turns into a sliver. A duel mat on a desk is seen at a much
 * steeper angle than this; the screen is not a desk, and past about fifteen the
 * top row stops being comfortably tappable.
 *
 * Compose maps pointer input back through the layer's inverse matrix, camera
 * and all, so a tap on the far row lands where it looks like it landed. That is
 * the whole reason this is a parent transform and not something drawn.
 */
private const val TILT_DEGREES = 9f

/**
 * How far the camera sits from the table, in multiples of the table's height.
 *
 * Far enough that the perspective is a hint rather than a fisheye. Compose
 * scales `cameraDistance` by density internally, so this is computed against
 * the table's height in dp.
 */
private const val CAMERA_DISTANCE_IN_HEIGHTS = 3.2f

/**
 * How much bigger the near edge gets, which the solver is handed so the table
 * still fits once it has been projected.
 *
 * Derived rather than eyeballed. Tipping about the centre pushes the near edge
 * a distance `(h/2)·sin θ` toward the camera, and the perspective divide scales
 * it by `d / (d − (h/2)·sin θ)`. At nine degrees with the camera 3.2 heights
 * out that is a shade under 1.03; the value here rounds up so the arithmetic
 * being slightly off can only ever leave the table smaller than its box, never
 * hanging over the edge.
 */
private const val PERSPECTIVE_GROWTH = 1.05f

/**
 * The duel table: one side of a board, as a thing you move cards around on.
 *
 * A sandbox, not a rules engine — the same stance `BoardState` takes, and the
 * reason this can exist at all. EDOPro and Master Duel enforce the game; what
 * neither of them lets you do is *set a board up*, look at it, take one move
 * back and try the other line. That is what a real table is for and what this
 * is: the model refuses only what is physically impossible, and the player
 * knows the rest.
 *
 * Where cards go is solved in `core/layout/BoardLayout.kt` rather than argued
 * with here, for the reason the deck panes learned twice. Every zone, pile and
 * the hand's band come out of one pass over the space the table actually has —
 * the bar is a sibling in a column, so the solver is handed what is left rather
 * than trusted to remember to subtract it.
 *
 * Tipped nine degrees on one parent `graphicsLayer`, which is the handbook's
 * fake-3D exactly: no engine, one transform, everything on the table sharing a
 * single projection so it reads as one surface. Nothing inside it knows it is
 * tilted — the geometry is solved flat and the layer does the rest. Compose
 * maps pointer input back through that layer's inverse matrix, camera and all,
 * so a tap on the far row lands where it looks like it landed; the only thing
 * the tilt costs is a slightly smaller table, which the solver is told about
 * rather than left to discover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuelTableScreen(state: DeckBuilderState, onBack: () -> Unit) {
    val deck = state.deck
    val table = remember(deck.main, deck.extra) {
        DuelTableState(deck.main, deck.extra)
    }
    var placement by remember { mutableStateOf(CardPosition.FACE_UP_ATK) }
    val feedback = LocalFeedback.current

    Column(Modifier.fillMaxSize().background(MasterToolPalette.Ink)) {
        TableTopBar(table, placement, { placement = it }, feedback, onBack)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val layout = with(density) {
                BoardLayouter.solve(
                    width = maxWidth.toPx(),
                    height = maxHeight.toPx(),
                    aspectRatio = CARD_ASPECT_RATIO,
                    perspectiveGrowth = PERSPECTIVE_GROWTH,
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

            // One tilted plane for the whole table, which is the handbook's
            // fake-3D exactly: no engine, one graphicsLayer, and everything on
            // the table sharing a single projection so it stays one surface.
            // Nothing inside here knows it is tilted — the geometry is solved
            // flat and the layer does the rest, which is also why a tap still
            // lands where it looks like it lands.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = TILT_DEGREES
                        cameraDistance = (layout.bounds.height / this.density) *
                            CAMERA_DISTANCE_IN_HEIGHTS
                    },
            ) {
                // Every zone's outline, whether or not it holds anything. An
                // empty board should still look like a board.
                layout.slots.forEach { (slot, rect) ->
                    ZoneOutline(
                        slot = slot,
                        rect = rect,
                        accepting = table.accepts(slot),
                        density = density,
                        onClick = { onSlotTapped(table, slot, placement, feedback) },
                    )
                }

                // Composed by card, not by zone. A card that moves is then the
                // same composable in a new place rather than one disappearing
                // and another appearing, which is the whole reason it can
                // travel there instead of arriving.
                val seats = remember(table.board, layout) { seatCards(table.board, layout) }
                seats.forEach { seat ->
                    key(seat.card.instanceId) {
                        SeatedCard(state, table, seat, density, placement)
                    }
                }

                HandReadout(state, table, layout, density)
            }
        }
    }

    table.openPile?.let { pile ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { table.openPile = null },
            sheetState = sheetState,
        ) {
            PileContents(state, table, pile)
        }
    }
}

/**
 * What a tap on a slot means.
 *
 * With a card in hand it means "put it there". With nothing held it means the
 * most common thing that slot does — the deck is drawn from, a pile is opened
 * to read — because a table you have to think about twice to draw a card from
 * is a table nobody goldfishes on.
 */
private fun onSlotTapped(
    table: DuelTableState,
    slot: BoardSlot,
    placement: CardPosition,
    feedback: Feedback,
) {
    if (table.held != null) {
        if (table.place(slot, placement)) feedback.play(SoundEffect.SNAP)
        return
    }

    when (slot) {
        BoardSlot.Deck -> if (table.move { it.draw() }) feedback.play(SoundEffect.DEAL)
        BoardSlot.Graveyard, BoardSlot.Banished, BoardSlot.ExtraDeck -> table.openPile = slot
        is BoardSlot.Zone -> Unit
    }
}

@Composable
private fun TableTopBar(
    table: DuelTableState,
    placement: CardPosition,
    onPlacement: (CardPosition) -> Unit,
    feedback: Feedback,
    onBack: () -> Unit,
) {
    val board = table.board

    Row(
        Modifier.fillMaxWidth().height(TOP_BAR).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("← Deck", style = MaterialTheme.typography.labelMedium)
        }

        Divider()

        // Life points, moved in the steps a duel actually moves them in.
        Text(
            board.lifePoints.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (board.lifePoints <= 0) MasterToolPalette.Danger else Color.Unspecified,
        )
        listOf(-1000, -500, -100, 100).forEach { delta ->
            BarButton(if (delta > 0) "+${delta}" else "$delta") {
                table.move { it.adjustLifePoints(delta) }
            }
        }

        Divider()

        BarButton(board.phase.label) { table.move { it.nextPhase() } }
        BarButton("Turn ${board.turn} ⟳") { table.move { it.endTurn() } }

        Divider()

        // What a placement means, said once and visibly, rather than hidden in
        // a gesture nobody would find.
        listOf(
            CardPosition.FACE_UP_ATK to "ATK",
            CardPosition.FACE_UP_DEF to "DEF",
            CardPosition.FACE_DOWN_DEF to "Set",
        ).forEach { (position, label) ->
            BarButton(label, selected = placement == position) { onPlacement(position) }
        }

        Box(Modifier.weight(1f))

        BarButton("Draw") {
            if (table.move { it.draw() }) feedback.play(SoundEffect.DEAL)
        }
        BarButton("Shuffle") {
            if (table.move { it.shuffleDeck(it.turn * 31L + it.deck.size) }) {
                feedback.play(SoundEffect.SHUFFLE)
            }
        }
        BarButton("Undo", enabled = table.canUndo) { table.undo() }
        BarButton("Redo", enabled = table.canRedo) { table.redo() }
        BarButton("New hand") { table.restart(); feedback.play(SoundEffect.SHUFFLE) }
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
private fun BarButton(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) MasterToolPalette.SurfaceHigh else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** Where a card can go, drawn as the mat draws it: an outline and a name. */
@Composable
private fun ZoneOutline(
    slot: BoardSlot,
    rect: Slot,
    accepting: Boolean,
    density: Density,
    onClick: () -> Unit,
) {
    val glow by animateFloatAsState(
        if (accepting) 1f else 0f,
        spring(stiffness = 420f),
        label = "zoneAccepting",
    )

    with(density) {
        Box(
            Modifier
                .offset(rect.left.toDp(), rect.top.toDp())
                .size(rect.width.toDp(), rect.height.toDp())
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = if (accepting) 2.dp else 1.dp,
                    color = MaterialTheme.colorScheme.outline
                        .copy(alpha = 0.35f + 0.65f * glow),
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val label = slot.label()
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun BoardSlot.label(): String? = when (this) {
    BoardSlot.Deck -> "Deck"
    BoardSlot.ExtraDeck -> "Extra"
    BoardSlot.Graveyard -> "GY"
    BoardSlot.Banished -> "Banished"
    is BoardSlot.Zone -> when (zone) {
        FieldZone.FieldSpell -> "Field"
        is FieldZone.ExtraMonster -> "Extra\nMonster"
        else -> null
    }
}

/**
 * One card and where it currently belongs.
 *
 * The table is drawn from a list of these rather than by walking the zones,
 * because a card that moves has to stay the *same* composable to travel. Walk
 * the zones and a card leaving the hand for a monster zone is one composable
 * being removed and a different one appearing somewhere else — nothing in that
 * has any reason to animate.
 */
private data class Seat(
    val card: BoardCard,
    val rect: Slot,
    /** What picking it up means, or null for somewhere you cannot pick from. */
    val pick: Held?,
    val faceUp: Boolean,
    val badge: String?,
    /** Set when it is on the field, which is where the full menu applies. */
    val zone: FieldZone?,
)

/**
 * Every card currently visible, and where it sits.
 *
 * Piles show their top card only — a stack is one card and a number, the way a
 * stack looks — so a card sent to the graveyard travels from its zone to the
 * pile and then simply stays there as the new top. That the animation falls out
 * of the model rather than being scripted is the point of doing it this way.
 *
 * Order is draw order: piles, then the field, then the hand, then whatever is
 * held, so the card in your hand is never underneath the table.
 */
private fun seatCards(board: BoardState, layout: BoardLayout): List<Seat> = buildList {
    fun pileTop(slot: BoardSlot, cards: List<BoardCard>, faceUp: Boolean, pick: Held?) {
        val top = cards.firstOrNull() ?: return
        val rect = layout[slot] ?: return
        add(Seat(top, rect, pick, faceUp, cards.size.toString(), null))
    }

    pileTop(BoardSlot.Deck, board.deck, faceUp = false, pick = null)
    pileTop(BoardSlot.ExtraDeck, board.extraDeck, faceUp = false, pick = null)
    pileTop(BoardSlot.Graveyard, board.graveyard, faceUp = true, pick = null)
    pileTop(BoardSlot.Banished, board.banished, faceUp = true, pick = null)

    layout.slots.keys.filterIsInstance<BoardSlot.Zone>().forEach { slot ->
        val card = board.at(slot.zone) ?: return@forEach
        val rect = layout[slot] ?: return@forEach
        add(
            Seat(
                card = card,
                rect = rect,
                pick = Held.OnField(slot.zone),
                faceUp = card.position.faceUp,
                badge = card.counters.takeIf { it > 0 }?.let { "\u25cf$it" }
                    ?: card.materials.size.takeIf { it > 0 }?.let { "\u25c8$it" },
                zone = slot.zone,
            ),
        )
    }

    board.hand.forEachIndexed { index, card ->
        add(
            Seat(
                card = card,
                rect = handSlot(layout, index, board.hand.size),
                pick = Held.InHand(index),
                faceUp = true,
                badge = null,
                zone = null,
            ),
        )
    }
}

/**
 * Where hand card [index] of [count] sits along the band the solver set aside.
 *
 * A row rather than an arc: an arc is lovely with six cards and unreadable with
 * fourteen, and a combo line routinely holds fourteen. The cards overlap only
 * as far as they must to fit, so the common case still reads as a fan.
 */
private fun handSlot(layout: BoardLayout, index: Int, count: Int): Slot {
    val band = layout.hand
    val step = if (count <= 1) {
        0f
    } else {
        minOf(
            layout.cardWidth + layout.gap,
            (band.width - layout.cardWidth) / (count - 1),
        )
    }
    val spread = layout.cardWidth + step * (count - 1)

    return Slot(
        left = band.left + (band.width - spread) / 2f + index * step,
        top = band.top,
        width = layout.cardWidth,
        height = layout.cardHeight,
    )
}

/**
 * A card in its seat.
 *
 * Tap picks it up, and picks it up again to put it down. Everything that is not
 * a move — flipping, banishing, counters, materials — is behind the long press,
 * the same division the deck builder's tiles use.
 */
@Composable
private fun SeatedCard(
    state: DeckBuilderState,
    table: DuelTableState,
    seat: Seat,
    density: Density,
    placement: CardPosition,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val zone = seat.zone
    val feedback = LocalFeedback.current

    TablePiece(
        card = seat.card,
        resolved = state.index.byId(seat.card.cardId).takeIf { seat.faceUp },
        rect = seat.rect,
        density = density,
        held = seat.pick != null && table.held == seat.pick,
        badge = seat.badge,
        onTap = {
            when {
                // Something is held and this zone will take it: that is a move,
                // not a reach for the card already sitting there.
                zone != null && table.accepts(BoardSlot.Zone(zone)) -> {
                    if (table.place(BoardSlot.Zone(zone), placement)) {
                        feedback.play(SoundEffect.SNAP)
                    }
                }

                seat.pick != null -> {
                    table.pick(seat.pick)
                    if (table.held != null) feedback.play(SoundEffect.LIFT)
                }

                // The top of a pile: the deck is drawn from, the rest are read.
                else -> pileOf(seat, table.board)?.let { pile ->
                    onSlotTapped(table, pile, placement, feedback)
                }
            }
        },
        onLongPress = {
            if (zone != null) {
                menuOpen = true
            } else {
                pileOf(seat, table.board)?.let { table.openPile = it }
            }
        },
    ) {
        if (zone == null) return@TablePiece

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val holding = table.held
            if (holding is Held.OnField && holding.zone != zone) {
                DropdownMenuItem(
                    text = { Text("Attach held card as material") },
                    onClick = {
                        menuOpen = false
                        if (table.move { it.attachAsMaterial(holding.zone, zone) }) {
                            feedback.play(SoundEffect.SLIDE)
                        }
                        table.drop()
                    },
                )
                HorizontalDivider()
            }

            if (seat.card.position.faceUp) {
                DropdownMenuItem(
                    text = { Text("Change position") },
                    onClick = { menuOpen = false; table.move { it.changePosition(zone) } },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Flip face-up") },
                    onClick = { menuOpen = false; table.move { it.flip(zone) } },
                )
            }

            DropdownMenuItem(
                text = { Text("Send to graveyard") },
                onClick = {
                    menuOpen = false
                    if (table.move { it.sendToGraveyard(zone) }) {
                        feedback.play(SoundEffect.SLIDE)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Banish") },
                onClick = {
                    menuOpen = false
                    if (table.move { it.banishFrom(zone) }) feedback.play(SoundEffect.SLIDE)
                },
            )
            DropdownMenuItem(
                text = { Text("Banish face-down") },
                onClick = {
                    menuOpen = false
                    table.move { it.banishFrom(zone, faceDown = true) }
                },
            )
            DropdownMenuItem(
                text = { Text("Return to hand") },
                onClick = { menuOpen = false; table.move { it.returnToHand(zone) } },
            )

            HorizontalDivider()

            if (seat.card.materials.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Detach a material (${seat.card.materials.size})") },
                    onClick = { menuOpen = false; table.move { it.detachMaterial(zone) } },
                )
            }
            DropdownMenuItem(
                text = { Text("Add a counter") },
                onClick = { menuOpen = false; table.move { it.addCounter(zone, 1) } },
            )
            if (seat.card.counters > 0) {
                DropdownMenuItem(
                    text = { Text("Remove a counter") },
                    onClick = { menuOpen = false; table.move { it.addCounter(zone, -1) } },
                )
            }
        }
    }
}

/** Which pile a seated card is the top of, if it is one. */
private fun pileOf(seat: Seat, board: BoardState): BoardSlot? = when (seat.card.instanceId) {
    board.deck.firstOrNull()?.instanceId -> BoardSlot.Deck
    board.extraDeck.firstOrNull()?.instanceId -> BoardSlot.ExtraDeck
    board.graveyard.firstOrNull()?.instanceId -> BoardSlot.Graveyard
    board.banished.firstOrNull()?.instanceId -> BoardSlot.Banished
    else -> null
}

/**
 * What is in the hand, in the words the deck was labelled with.
 *
 * This is the payoff of the lens work sitting one screen away. You spent the
 * time saying which twelve cards are your starters; the table is where that
 * answer is worth something, because "is this opener any good" is a question
 * about roles and not about card names. Whichever lens the deck was left on is
 * the one that speaks here — roles if you drew them, archetypes or copy counts
 * if you did not, so an unlabelled deck still gets a reading.
 */
@Composable
private fun HandReadout(
    state: DeckBuilderState,
    table: DuelTableState,
    layout: BoardLayout,
    density: Density,
) {
    val hand = table.board.hand
    val keying = remember(hand, state.lens, state.groups, state.index, state.format) {
        DeckLenses.key(
            lens = state.lens,
            section = hand.map { it.cardId },
            cards = state.index::byId,
            groups = state.groups,
            format = state.format,
        )
    }

    if (hand.isEmpty()) return

    val parts = keying.keys
        .map { it to keying.countOf(it.id) }
        .filter { (_, count) -> count > 0 }

    with(density) {
        Row(
            Modifier
                .offset(layout.readout.left.toDp(), layout.readout.top.toDp())
                .size(layout.readout.width.toDp(), layout.readout.height.toDp()),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            parts.forEach { (key, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 12.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(key.paint.color()),
                    )
                    Text(
                        "$count ${key.label}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // What is left over is part of the reading, not an omission from
            // it — an opener with two cards that do nothing is a fact about
            // that opener. The plain Deck lens labels nothing on purpose, so
            // there it says the only thing it honestly can.
            val remainder = when {
                keying.lens == Lens.DECK -> "${hand.size} in hand"
                parts.isEmpty() -> "${hand.size} in hand, none of them labelled"
                keying.unclaimed > 0 -> "${keying.unclaimed} loose"
                else -> null
            }

            if (remainder != null) {
                Text(
                    remainder,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One card on the table, wherever it is.
 *
 * Held cards lift and cast a shadow rather than growing, the same rule the deck
 * builder's selection follows: the board keeps its shape while you pick through
 * it. A defending monster is the card turned on its side, which is what a
 * defending monster is.
 */
@Composable
private fun TablePiece(
    card: BoardCard,
    resolved: Card?,
    rect: Slot,
    density: Density,
    held: Boolean,
    badge: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val lift by animateFloatAsState(
        if (held) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "tableLift",
    )
    // Where it is, as opposed to where it belongs. Seeded at its first seat so
    // a card that appears does not fly in from the corner, and animated from
    // then on so a card that moves goes there.
    val position = remember { Animatable(Offset(rect.left, rect.top), Offset.VectorConverter) }
    LaunchedEffect(rect.left, rect.top) {
        position.animateTo(
            Offset(rect.left, rect.top),
            spring(
                dampingRatio = 0.78f,
                stiffness = 260f,
                visibilityThreshold = Offset.VisibilityThreshold,
            ),
        )
    }

    val turn by animateFloatAsState(
        if (card.position == CardPosition.FACE_UP_DEF ||
            card.position == CardPosition.FACE_DOWN_DEF
        ) {
            -90f
        } else {
            0f
        },
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tableTurn",
    )

    with(density) {
        Box(
            Modifier
                .size(rect.width.toDp(), rect.height.toDp())
                // Read inside the layer rather than in the composable body: a
                // travelling card must not recompose the table sixty times a
                // second to get there.
                .graphicsLayer {
                    translationX = position.value.x
                    translationY = position.value.y - 6.dp.toPx() * lift
                    rotationZ = turn
                    shadowElevation = 16.dp.toPx() * lift
                    shape = RoundedCornerShape(4.dp)
                }
                .pointerInput(card.instanceId, held) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() },
                    )
                },
        ) {
            // Drawn here rather than with the builder's CardTile: that tile
            // owns a click and a hover tilt of its own, and two gesture
            // detectors competing for one press is a board that behaves
            // differently depending on how fast you were.
            if (card.position.faceUp && resolved != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MasterToolPalette.SurfaceRaised)
                        .then(
                            if (held) {
                                Modifier.border(
                                    2.5.dp,
                                    MasterToolPalette.AccentBright,
                                    RoundedCornerShape(4.dp),
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp),
                                )
                            },
                        ),
                ) {
                    Text(
                        resolved.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(2.dp),
                    )
                    AsyncImage(
                        model = resolved.imageUrlSmall ?: resolved.imageUrl,
                        contentDescription = resolved.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    )
                }
            } else {
                FaceDown(held)
            }

            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MasterToolPalette.Ink.copy(alpha = 0.82f))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MasterToolPalette.Text,
                        maxLines = 1,
                    )
                }
            }

            content()
        }
    }
}

/** A card whose face is not showing: the card back, plus whatever it is doing. */
@Composable
private fun FaceDown(held: Boolean) {
    val back = LocalCardBack.current

    Box(Modifier.fillMaxSize()) {
        CardBack(
            modifier = Modifier.fillMaxSize(),
            style = back.style,
            imageUrl = back.imageUrl,
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = if (held) 2.5.dp else 1.dp,
                    color = if (held) {
                        MasterToolPalette.AccentBright
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(4.dp),
                ),
        )
    }
}

/** A pile, opened to be read and played from. */
@Composable
private fun PileContents(state: DeckBuilderState, table: DuelTableState, pile: BoardSlot) {
    val board = table.board
    val cards = when (pile) {
        BoardSlot.Deck -> board.deck
        BoardSlot.ExtraDeck -> board.extraDeck
        BoardSlot.Graveyard -> board.graveyard
        BoardSlot.Banished -> board.banished
        is BoardSlot.Zone -> emptyList()
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${pile.label() ?: "Pile"} · ${cards.size}",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (cards.isEmpty()) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            // Playing *from* a pile only means something for the two piles you
            // are allowed to reach into without a card telling you to.
            if (pile == BoardSlot.ExtraDeck || pile == BoardSlot.Graveyard) {
                "Tap one to pick it up, then tap where it goes."
            } else {
                "Reading only."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(150.dp),
        ) {
            items(cards.size) { index ->
                val card = cards[index]
                val resolved = state.index.byId(card.cardId)
                Box(Modifier.width(96.dp).height(140.dp)) {
                    if (resolved != null) {
                        CardTile(
                            card = resolved,
                            copies = 0,
                            modifier = Modifier.fillMaxSize(),
                            onClick = {
                                if (pile == BoardSlot.ExtraDeck || pile == BoardSlot.Graveyard) {
                                    table.pick(Held.InPile(pile, index))
                                    table.openPile = null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
