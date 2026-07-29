package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import com.kaiharimoto.mastertool.ui.components.MasterToolMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.DeckGrouping
import com.kaiharimoto.mastertool.core.deck.DeckKeys
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.deck.TidyBy
import com.kaiharimoto.mastertool.core.layout.DealAnimation
import com.kaiharimoto.mastertool.core.layout.GridFit
import com.kaiharimoto.mastertool.core.layout.GridFitter
import com.kaiharimoto.mastertool.core.layout.MakeRoom
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CARD_CORNER
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.accent
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DragSource
import com.kaiharimoto.mastertool.ui.dnd.DropHover
import com.kaiharimoto.mastertool.ui.theme.DeckMats
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import kotlinx.coroutines.delay

/** How long a whole section takes to be dealt onto the table. */
private const val DEAL_MS = 460

/** How long a revealed card keeps its highlight before settling back. */
private const val FLASH_MS = 1400L

private val SECTION_ORDER =
    listOf(DeckSection.MAIN, DeckSection.EXTRA, DeckSection.SIDE)

/**
 * The three deck sections, stacked and resizable.
 *
 * The panes trade height across a divider rather than each being sized on its
 * own, so dragging one boundary leaves everything on the far side of it where it
 * was. A collapsed pane keeps its header — it is still a drop target and still
 * says how many cards it holds — and drops out of the weighting entirely, which
 * is what makes collapsing the Side deck actually give its space to the Main.
 */
@Composable
fun DeckPanes(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    onDropped: (DragSession, DropHover?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .onGloballyPositioned { layout.deckColumnHeightPx = it.size.height.toFloat() }
            .padding(12.dp),
    ) {
        SECTION_ORDER.forEachIndexed { position, section ->
            val preferences = layout.preferences[section]

            DeckSectionPane(
                state = state,
                layout = layout,
                drag = drag,
                onDropped = onDropped,
                section = section,
                modifier = if (preferences.collapsed) Modifier else Modifier.weight(preferences.weight),
            )

            val next = SECTION_ORDER.getOrNull(position + 1)
            if (next != null) {
                PaneDivider(
                    enabled = !preferences.collapsed && !layout.preferences[next].collapsed,
                    onDrag = { delta -> layout.resizePanes(section, next, delta) },
                )
            }
        }
    }
}

@Composable
private fun PaneDivider(enabled: Boolean, onDrag: (Float) -> Unit) {
    val draggableState = rememberDraggableState(onDelta = onDrag)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (enabled) 16.dp else 8.dp)
            .then(
                if (enabled) {
                    Modifier.draggable(draggableState, Orientation.Vertical)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun DeckSectionPane(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    onDropped: (DragSession, DropHover?) -> Unit,
    section: DeckSection,
    modifier: Modifier = Modifier,
) {
    val ids = state.deck[section]
    val preferences = layout.preferences[section]
    val accent = section.accent()
    val gridState = rememberLazyGridState()
    var flashed by remember { mutableStateOf<CardId?>(null) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    // What the grid actually settled on, so the header can report it and taking
    // manual control starts from what is already on screen.
    var effectiveColumns by remember { mutableStateOf(preferences.columns) }

    // A cursor arrowed past the bottom of the pane has to bring the pane with
    // it, or it is a cursor that can be lost by pressing down.
    LaunchedEffect(state.reveal) {
        val reveal = state.reveal ?: return@LaunchedEffect
        if (reveal.section == section && reveal.index in ids.indices) {
            gridState.animateScrollToItem(reveal.index)
        }
    }

    // One animation for the whole pane. Each card works out its own progress
    // from its index, because forty Animatables to show a quarter-second effect
    // is forty coroutines for something the arithmetic already knows.
    val deal = remember { Animatable(1f) }
    LaunchedEffect(state.dealSerial) {
        if (state.dealSerial == 0L) return@LaunchedEffect
        deal.snapTo(0f)
        deal.animateTo(1f, tween(durationMillis = DEAL_MS, easing = LinearEasing))
    }

    val hover = drag.hover?.takeIf { it.section == section }
    val dropBorder = when {
        hover == null -> null
        hover.accepted -> accent
        else -> MasterToolPalette.Danger
    }

    // Only the pane that owns the requested section reacts; the others ignore it.
    LaunchedEffect(state.revealRequest?.id) {
        val request = state.revealRequest ?: return@LaunchedEffect
        if (request.section != section) return@LaunchedEffect

        // Only move the pane if the card is not already in front of you.
        // Scrolling a card that was visible anyway to the top of the pane is a
        // jump that answers no question.
        val alreadyVisible = gridState.layoutInfo.visibleItemsInfo.any {
            it.index == request.position
        }
        if (!alreadyVisible) gridState.animateScrollToItem(request.position)

        if (request.flash) {
            flashed = request.cardId
            delay(FLASH_MS)
            flashed = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            // A mat rather than a panel, bound in the section's own colour —
            // which is what keeps the three panes apart now that the cards
            // inside them touch and no background shows between.
            .tableSurface(accent, DeckMats.of(state.mat, LocalMasterToolColors.current))
            .then(
                // The whole pane lights up while a card is over it, in the
                // section's own colour when the drop is legal and in red when it
                // is not — a Link monster held over the Main deck says so before
                // it is let go, rather than after.
                if (dropBorder != null) {
                    Modifier.border(2.dp, dropBorder, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            // Registered whole rather than just its grid, so a collapsed or empty
            // pane is still somewhere a card can be dropped.
            .onGloballyPositioned {
                drag.registerPane(section, it.boundsInRoot(), gridState, gridOrigin)
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(state, layout, section, ids.size, preferences, effectiveColumns, accent)

        if (preferences.collapsed) return@Column

        if (ids.isEmpty()) {
            EmptySection(section, accent)
            return@Column
        }

        val stacks = if (layout.preferences.stacked) DeckGrouping.stacks(ids) else emptyList()
        val itemCount = if (layout.preferences.stacked) stacks.size else ids.size

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            // Zero by default. Cards that touch read as one arrangement the way they
            // do on a table; a gutter turns the same forty cards into forty tiles.
            val spacing = layout.preferences.cardGutter.dp

            // Recomputed on every layout pass, which is cheap and means the grid
            // re-fits the moment the pane is resized or a card is added.
            val fit = with(density) {
                if (preferences.autoFit) {
                    GridFitter.fit(
                        count = itemCount,
                        availableWidth = maxWidth.toPx(),
                        availableHeight = maxHeight.toPx(),
                        spacing = spacing.toPx(),
                        aspectRatio = CARD_ASPECT_RATIO,
                        minColumns = SectionPreferences.MIN_COLUMNS,
                        maxColumns = SectionPreferences.MAX_COLUMNS,
                    )
                } else {
                    GridFit(
                        columns = preferences.columns,
                        fits = GridFitter.requiredHeight(
                            count = itemCount,
                            columns = preferences.columns,
                            availableWidth = maxWidth.toPx(),
                            spacing = spacing.toPx(),
                            aspectRatio = CARD_ASPECT_RATIO,
                        ) <= maxHeight.toPx(),
                    )
                }
            }

            // Nothing to scroll means nothing for a drag to be mistaken for, so
            // the card can be picked up the instant the finger moves.
            val competesWithScroll = !fit.fits

            // Written after composition rather than during it — the header sits
            // above this and only needs the number on the next pass. The layout
            // state is told too, so a resize can pin the grid at what is on
            // screen rather than at whatever was last stored.
            SideEffect {
                effectiveColumns = fit.columns
                layout.noteDisplayedColumns(section, fit.columns)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(fit.columns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { gridOrigin = it.positionInRoot() },
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (layout.preferences.stacked) {
                    // The gaps, moved onto the seams that still exist once the
                    // copies are one tile. Without this, turning the density
                    // down looked like it had thrown the arrangement away.
                    val stackGaps = DeckGrouping
                        .breaksOverStacks(state.breaksIn(section), stacks, ids.size)
                        .before

                    // Stacks have no positional identity, so dragging one has
                    // nothing coherent to mean; the stepper does that job instead.
                    items(stacks.size, key = { "${section.name}-stack-${stacks[it].id.value}" }) { i ->
                        val stack = stacks[i]
                        DeckCard(
                            state = state,
                            drag = null,
                            competesWithScroll = competesWithScroll,
                            onDropped = onDropped,
                            section = section,
                            id = stack.id,
                            copies = stack.count,
                            highlighted = flashed == stack.id,
                            insertionMarker = false,
                            siblings = ids,
                            position = stack.firstIndex,
                            columns = fit.columns,
                            deal = { DealAnimation.progressFor(i, stacks.size, deal.value) },
                            modifier = if (i in stackGaps) Modifier.groupStart(accent) else Modifier,
                        )
                    }
                } else {
                    // Keyed by which copy, not by where it sits. A positional key
                    // changes for every tile after an insertion, so the grid would
                    // see the whole tail replaced rather than moved, and nothing
                    // could animate.
                    val copies = DeckKeys.occurrences(ids)
                    // Where the player has pushed this pile apart. Clamped, so a
                    // gap left past the end of a shrinking section simply is not
                    // drawn rather than drawn somewhere wrong.
                    val gaps = state.breaksIn(section).before

                    // Where a card being carried would land, if it would be
                    // taken. The cards either side of it push apart, so the slot
                    // is visible before the card lands rather than only marked.
                    val leanSeam = hover?.takeIf { it.accepted }?.index

                    items(
                        ids.size,
                        key = { "${section.name}-${ids[it].value}-${copies[it]}" },
                    ) { position ->
                        DeckCard(
                            state = state,
                            drag = drag,
                            competesWithScroll = competesWithScroll,
                            onDropped = onDropped,
                            section = section,
                            id = ids[position],
                            copies = state.copiesIn(ids[position], section),
                            highlighted = flashed == ids[position],
                            insertionMarker = hover?.accepted == true && hover.index == position,
                            siblings = ids,
                            position = position,
                            columns = fit.columns,
                            deal = { DealAnimation.progressFor(position, ids.size, deal.value) },
                            // The cards either side of the drop push apart, so
                            // the slot the card is going into is visible before
                            // it lands rather than only marked.
                            lean = leanSeam?.let {
                                MakeRoom.shiftFor(position, it, fit.columns, ids.size)
                            } ?: 0f,
                            // Sorting a section, or dropping a card into the middle
                            // of one, now slides the cards that moved instead of
                            // redrawing the pane somewhere else.
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                                )
                                .then(
                                    if (position in gaps) Modifier.groupStart(accent) else Modifier,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One card in a deck pane.
 *
 * Tap removes a copy, which is the action you take most and which the snackbar
 * can put straight back. Everything less common — and everything that cannot be
 * undone by tapping again — is behind the long-press menu rather than behind a
 * second gesture nobody would find.
 */
/**
 * The gap a player pushed between two piles, drawn where the work happens.
 *
 * A rule, and no space at all. The first version took the space out of the card's
 * own width, on the reasoning that a card pushed aside should read as pushed
 * aside — and a prototype at real card size showed why that is wrong: the card at
 * the gap ends up visibly narrower than its neighbours, and a grid where one card
 * is a different size reads as a fault long before it reads as a gap.
 *
 * So the pane *marks* the arrangement and the showcase *shows* it. Every card
 * here stays the size of every other card, and a group is called out by a line
 * standing between two of them; over in the showcase, where a deck is looked at
 * rather than worked on, the groups genuinely separate onto their own rows. The
 * two views are doing different jobs and it is right that they say it differently.
 *
 * Bar and serifs rather than a plain line, which is the same shape the drop mark
 * uses a few hundred lines up — at zero gutter a bare line against a card edge
 * reads as part of the card.
 */
private fun Modifier.groupStart(accent: Color): Modifier = this.drawWithContent {
    drawContent()

    val x = GROUP_RULE / 2f * density
    val serif = GROUP_SERIF * density
    val stroke = GROUP_RULE * density

    drawLine(
        color = accent,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = stroke,
        cap = StrokeCap.Square,
    )
    listOf(0f, size.height).forEach { y ->
        drawLine(
            color = accent,
            start = Offset(x, y),
            end = Offset(x + serif, y),
            strokeWidth = stroke,
            cap = StrokeCap.Square,
        )
    }
}

/** Drawn over the card, so it costs no width at all. */
private const val GROUP_RULE = 2.5f
private const val GROUP_SERIF = 6f

@Composable
private fun DeckCard(
    state: DeckBuilderState,
    drag: DragController?,
    competesWithScroll: Boolean,
    onDropped: (DragSession, DropHover?) -> Unit,
    section: DeckSection,
    id: CardId,
    copies: Int,
    highlighted: Boolean,
    insertionMarker: Boolean,
    siblings: List<CardId>,
    position: Int,
    columns: Int,
    /**
     * How far into being dealt this card is, 0 to 1.
     *
     * A lambda rather than a value: it is read inside `graphicsLayer`, which
     * runs at draw time, so the deal animates a layer per frame instead of
     * recomposing forty cards sixty times a second.
     */
    deal: () -> Float,
    /**
     * How far this card should lean aside for a drop, as a fraction of its own
     * width. Zero whenever nothing is being carried over this pane.
     */
    lean: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val card: Card? = state.index.byId(id)
    var menuOpen by remember { mutableStateOf(false) }

    val accent = section.accent()
    val selected = state.selection.contains(section, position)
    // Only the pane holding the selection is in selection mode; the other two
    // carry on behaving normally.
    val selecting = state.selection.section == section && !state.selection.isEmpty

    if (card == null) {
        // Still carries the modifier the grid gave it. Without that, a deck in
        // the first seconds after an import -- when every card is unknown --
        // would lose its gaps and its placement animation, and then have them
        // appear as the pool arrived.
        UnknownCardTile(id, modifier)
        return
    }

    val tile: @Composable () -> Unit = {
        HoverPreview(card, note = state.noteOn(id)) {
            CardTile(
                card = card,
                format = state.format,
                copies = copies,
                highlighted = highlighted,
                onClick = {
                    // In selection mode a tap is about the group, not the deck.
                    // Removing a card out from under a selection being built is
                    // the one thing a stray tap must not be able to do.
                    if (selecting) {
                        state.toggleSelected(section, position)
                    } else {
                        state.removeOne(card, section)
                    }
                },
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(accent.copy(alpha = 0.22f))
                            .border(2.dp, accent),
                    )
                }

                // A card the one you are holding is noted with. Quieter than
                // the selection's own border and drawn inside it, so a card
                // that is both reads as picked out *and* connected rather than
                // as two competing states.
                if (id in state.notedWith) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(2.dp)
                            .border(1.5.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(CARD_CORNER)),
                    )
                }

                // A card somebody has written on. A folded corner rather than a
                // badge: it is the mark you would make on the card itself, and
                // at this size a badge would be a second count to misread.
                if (state.noteOn(id) != null) {
                    Canvas(Modifier.matchParentSize()) {
                        // A fraction of the card rather than a fixed size, in
                        // the same ninety-sixths the face and the badges use.
                        // A shape that covers area has to shrink when the card
                        // does; an *edge* -- the printed border, the selection
                        // ring, this fold's own crease -- does not, because a
                        // hairline reads as a hairline at any size and a
                        // proportional one reads as a smudge.
                        val unit = size.width / 96.dp.toPx()
                        val fold = 13.dp.toPx() * unit
                        val inset = 2.dp.toPx() * unit
                        val right = size.width - inset
                        drawPath(
                            Path().apply {
                                moveTo(right - fold, inset)
                                lineTo(right, inset)
                                lineTo(right, inset + fold)
                                close()
                            },
                            accent.copy(alpha = 0.92f),
                        )
                        // The crease, which is what makes it read as folded
                        // paper rather than as a coloured triangle.
                        drawLine(
                            Color.Black.copy(alpha = 0.45f),
                            Offset(right - fold, inset),
                            Offset(right, inset + fold),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }

                // Where the drop would land, in the colour of the section it
                // would land in — the same colour that pane is bound in.
                //
                // It was a plain bar, and a plain bar on the leading edge of a
                // card is indistinguishable from that card's own edge, which is
                // exactly the reading a zero gutter invites: the cards touch, so
                // every seam already looks like a line. The serifs are what make
                // it a mark rather than a border, and the light spilling to the
                // right says which card is being displaced.
                if (insertionMarker) {
                    Canvas(Modifier.matchParentSize()) {
                        val bar = 3.dp.toPx()
                        val serif = 10.dp.toPx()
                        val thickness = 3.dp.toPx()
                        val spill = 18.dp.toPx()

                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to accent.copy(alpha = 0.40f),
                                1f to Color.Transparent,
                                startX = 0f,
                                endX = spill,
                            ),
                            size = Size(spill, size.height),
                        )
                        drawRect(color = accent, size = Size(bar, size.height))
                        drawRect(color = accent, size = Size(serif, thickness))
                        drawRect(
                            color = accent,
                            topLeft = Offset(0f, size.height - thickness),
                            size = Size(serif, thickness),
                        )
                    }
                }
            }
        }
    }

    // Sprung rather than snapped. The seam jumps from one card to the next as a
    // hand moves, and cards that teleported a quarter of their width to follow it
    // would read as the pane flinching. Held as a State and read inside
    // `graphicsLayer`, so the slide invalidates a layer rather than recomposing
    // forty cards for every frame of it.
    val leaning = animateFloatAsState(
        targetValue = lean,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    )

    Box(
        modifier.graphicsLayer {
            translationX = leaning.value * size.width

            val progress = deal()
            if (progress >= 1f) return@graphicsLayer
            alpha = progress
            // Dropped onto its place rather than faded in from nowhere: a card
            // that arrives by becoming opaque has not come from anywhere.
            translationY = -DealAnimation.riseFor(progress) * size.height
        },
    ) {
        if (drag == null) {
            tile()
        } else {
            DragSource(
                controller = drag,
                key = "${section.name}-$position-${id.value}",
                competesWithScroll = competesWithScroll,
                session = { DragSession(card, section, position, IntSize.Zero) },
                onLongPress = { menuOpen = true },
                onDropped = onDropped,
                content = tile,
            )
        }

        MasterToolMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // Selection is entered from here rather than from a modifier key, so
            // it exists at all on a tablet with no keyboard.
            if (!selecting) {
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = { menuOpen = false; state.select(section, position) },
                )
                // Only where there are gaps to make groups out of. Without any,
                // "this group" is the whole section, and an item that quietly
                // means select-all is one nobody presses twice.
                if (!state.breaksIn(section).isEmpty) {
                    DropdownMenuItem(
                        text = { Text("Pick up this group") },
                        onClick = { menuOpen = false; state.selectGroupAt(section, position) },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text("Select through here") },
                    onClick = { menuOpen = false; state.selectThrough(section, position) },
                )
                DropdownMenuItem(
                    // The other honest meaning of "between these two" in a grid,
                    // and the one a reading-order run cannot express.
                    text = { Text("Select block to here") },
                    onClick = {
                        menuOpen = false
                        state.selectBlockThrough(section, position, columns)
                    },
                )
                // Two cards picked out is the shape of a combo, and the only
                // shape a pair note has. Three is a note about the deck, which
                // already has somewhere to live.
                if (state.selection.size == 2) {
                    DropdownMenuItem(
                        text = { Text("What these two do…") },
                        onClick = { menuOpen = false; state.notePickedPair() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Done selecting") },
                    onClick = { menuOpen = false; state.clearSelection() },
                )
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Inspect") },
                onClick = {
                    menuOpen = false
                    state.inspect(siblings.mapNotNull { state.index.byId(it) }, position)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(if (state.noteOn(id) != null) "Read the note…" else "Write a note…")
                },
                onClick = { menuOpen = false; state.noteTarget = id },
            )
            DropdownMenuItem(
                text = { Text("Remove one") },
                onClick = { menuOpen = false; state.removeOne(card, section) },
            )
            if (copies > 1) {
                DropdownMenuItem(
                    text = { Text("Remove all $copies") },
                    onClick = { menuOpen = false; state.removeAllCopies(card, section) },
                )
            }

            HorizontalDivider()

            // Only between two cards. A gap before the first card of a section
            // is not a gap, it is the edge, and offering it would be offering
            // nothing.
            if (position > 0) {
                val already = position in state.breaksIn(section).before
                DropdownMenuItem(
                    text = { Text(if (already) "Close the gap here" else "Start a group here") },
                    onClick = { menuOpen = false; state.toggleBreak(section, position) },
                )
            }

            // Only once there are gaps, for the reason picking a group up is:
            // with none, this pile is the section, and naming it says nothing
            // the heading above it does not already say.
            state.pileAt(section, position)?.let { pile ->
                DropdownMenuItem(
                    text = { Text(if (pile.name.isBlank()) "Name this pile" else "Rename this pile") },
                    onClick = { menuOpen = false; state.pileTarget = pile },
                )
            }

            val elsewhere = if (section == DeckSection.SIDE) card.requiredSection() else DeckSection.SIDE
            DropdownMenuItem(
                text = { Text("Move one to ${elsewhere.displayName}") },
                onClick = { menuOpen = false; state.moveCard(card, section, elsewhere) },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    section: DeckSection,
    count: Int,
    preferences: SectionPreferences,
    columns: Int,
    accent: androidx.compose.ui.graphics.Color,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val overCapacity = count > section.maxSize
    val underMinimum = count < section.minSize

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            "  ${section.displayName} Deck",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            // Read from the section rather than written out, so the bounds cannot
            // drift from the ones the validator enforces.
            if (section.minSize > 0) {
                "  $count / ${section.minSize}–${section.maxSize}"
            } else {
                "  $count / ${section.maxSize}"
            },
            style = tacticalStyle(),
            color = when {
                overCapacity || underMinimum -> MasterToolPalette.Danger
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // What the gaps add up to. Only when there are any, and only as the
        // numbers -- naming the groups would mean asking for names, and the
        // whole point of a gap is that it says enough without one.
        val gaps = state.breaksIn(section)
        if (!gaps.isEmpty) {
            Text(
                "  " + gaps.groups(count).joinToString(" · ") { it.count().toString() },
                style = tacticalStyle(),
                color = accent,
            )
        }

        Box(Modifier.weight(1f))

        // Says the pane is in selection mode, says how many, and gets out of it.
        // Without this the only way to discover you are still selecting is that
        // tapping a card stops removing it.
        val selection = state.selection
        if (selection.section == section && !selection.isEmpty) {
            TextButton(onClick = { state.clearSelection() }) {
                Text(
                    "${selection.size} selected ✕",
                    style = tacticalStyle(),
                    color = accent,
                )
            }
        }

        if (!preferences.collapsed) {
            // Sized to fit by default. The steppers always work and always start
            // from what is on screen; reaching for one is itself the decision to
            // take over, so there is no mode to switch first.
            IconButton(
                onClick = { layout.setColumns(section, columns - 1) },
                enabled = columns > SectionPreferences.MIN_COLUMNS,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer, larger cards per row")
            }
            Text(columns.toString(), style = tacticalStyle())
            IconButton(
                onClick = { layout.setColumns(section, columns + 1) },
                enabled = columns < SectionPreferences.MAX_COLUMNS,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More, smaller cards per row")
            }

            if (preferences.autoFit) {
                Text(
                    "Auto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(onClick = { layout.setAutoFit(section, true) }) {
                    Text("Auto", style = MaterialTheme.typography.labelMedium)
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "${section.displayName} deck options")
                }
                MasterToolMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Tidy first, and with the blurbs, because the difference
                    // between the two halves of this menu is the whole point: a
                    // tidy moves cards next to each other and leaves the rest of
                    // the arrangement alone, a sort throws the arrangement away.
                    // Somebody who arranged this pane by hand needs to be able
                    // to tell which is which before pressing one.
                    MenuHeading("Tidy — keeps your order")
                    TidyBy.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(mode.label)
                                    Text(
                                        mode.blurb,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                menuOpen = false
                                state.tidySection(section, mode)
                            },
                        )
                    }

                    // Only when there are any. A menu entry for taking away
                    // something nobody has put there is a menu entry that
                    // teaches you nothing and takes up a line forever.
                    if (!state.breaksIn(section).isEmpty) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Close every gap")
                                    Text(
                                        "the cards stay where they are",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { menuOpen = false; state.clearBreaks(section) },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MenuHeading("Sort — replaces your order")
                    SortMode.entries.filter { it != SortMode.MANUAL }.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("By ${mode.displayName.lowercase()}") },
                            onClick = {
                                menuOpen = false
                                layout.setSortMode(section, mode)
                                state.sortSection(section, mode)
                            },
                        )
                    }
                }
            }
        }

        IconButton(onClick = { layout.toggleCollapsed(section) }) {
            Icon(
                if (preferences.collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (preferences.collapsed) {
                    "Expand ${section.displayName} deck"
                } else {
                    "Collapse ${section.displayName} deck"
                },
            )
        }
    }
}

/**
 * A section with nothing in it.
 *
 * This was a line of grey text, which is what an empty pane looks like in every
 * other builder and is also the first thing anybody sees on a new deck. It is
 * now the shape of the deck about to be built: one slot for every card the
 * section holds, pressed into the same cloth the cards will sit on, at exactly
 * the size the cards will be.
 *
 * Two decisions worth writing down. The slots are drawn *inset* even though the
 * real grid has no gutter at all — cards can touch because they have art to tell
 * them apart, and empty outlines that touch stop being cards and become graph
 * paper. And the rows fade out downward, because forty hard-edged rectangles is
 * a form to fill in rather than a table to work at.
 *
 * The first slot is ringed in the section's colour. It is where the next card
 * lands, and it turns the whole thing from decoration into an answer.
 */
@Composable
private fun EmptySection(section: DeckSection, accent: Color) {
    // Extra and Side have no minimum, so their capacity is the ceiling. Main's
    // is the forty a legal deck starts at rather than the sixty it may reach:
    // the promise being drawn is what is needed, not what is permitted.
    val capacity = if (section.minSize > 0) section.minSize else section.maxSize

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fit = with(density) {
            GridFitter.fit(
                count = capacity,
                availableWidth = maxWidth.toPx(),
                availableHeight = maxHeight.toPx(),
                spacing = 0f,
                aspectRatio = CARD_ASPECT_RATIO,
                minColumns = SectionPreferences.MIN_COLUMNS,
                maxColumns = SectionPreferences.MAX_COLUMNS,
            )
        }
        val inset = with(density) { 3.dp.toPx() }
        val corner = with(density) { CARD_CORNER.toPx() }
        val ring = with(density) { 1.5.dp.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val cardWidth = GridFitter.cardWidth(size.width, fit.columns, spacing = 0f)
            if (cardWidth <= inset * 2) return@Canvas
            val cardHeight = cardWidth / CARD_ASPECT_RATIO

            repeat(capacity) { index ->
                val left = (index % fit.columns) * cardWidth + inset
                val top = (index / fit.columns) * cardHeight + inset
                val fade = slotFade((top + cardHeight / 2f) / size.height)
                if (fade <= 0.01f) return@repeat

                val slot = Size(cardWidth - inset * 2, cardHeight - inset * 2)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.22f * fade),
                    topLeft = Offset(left, top),
                    size = slot,
                    cornerRadius = CornerRadius(corner),
                )
                // A hairline of light along the bottom edge, which is what makes
                // the dark rectangle read as a depression rather than a hole.
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.035f * fade),
                    topLeft = Offset(left, top + 1f),
                    size = slot,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = 1f),
                )
                if (index == 0) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.5f * fade),
                        topLeft = Offset(left, top),
                        size = slot,
                        cornerRadius = CornerRadius(corner),
                        style = Stroke(width = ring),
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.size(width = 1.dp, height = 6.dp))
            Text(
                "ROOM FOR $capacity",
                style = tacticalStyle(),
                color = accent.copy(alpha = 0.9f),
            )
        }
    }
}

/**
 * How visible a slot is, given where its middle sits down the pane.
 *
 * Full strength through the top third and gone by the bottom, so the grid reads
 * as trailing off rather than as a form with forty boxes on it.
 */
private fun slotFade(position: Float): Float =
    ((1f - (position - 0.30f) / 0.62f)).coerceIn(0f, 1f)

/** A label inside a menu, styled so it cannot be mistaken for something to press. */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = tacticalStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * A passcode the card database does not know.
 *
 * Given the same shape as a real card so it does not collapse its grid row, and
 * showing the passcode because that is the only thing anyone can act on — it is
 * what the validator names in the matching error.
 */
@Composable
private fun UnknownCardTile(id: CardId, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Unknown\n${id.value}",
            style = MaterialTheme.typography.labelSmall,
            color = MasterToolPalette.Danger,
        )
    }
}
