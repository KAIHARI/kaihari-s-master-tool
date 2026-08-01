package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.deck.DeckGroup
import com.kaiharimoto.mastertool.core.deck.DeckGrouping
import com.kaiharimoto.mastertool.core.deck.GroupMarks
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.layout.BreakdownLayout
import com.kaiharimoto.mastertool.core.layout.CellEdges
import com.kaiharimoto.mastertool.core.layout.DeckFit
import com.kaiharimoto.mastertool.core.layout.DeckFitter
import com.kaiharimoto.mastertool.core.layout.GridPoint
import com.kaiharimoto.mastertool.core.layout.GridRegion
import com.kaiharimoto.mastertool.core.layout.GridFit
import com.kaiharimoto.mastertool.core.layout.GridFitter
import com.kaiharimoto.mastertool.core.layout.SectionFit
import com.kaiharimoto.mastertool.core.layout.SectionFitRequest
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.prefs.SectionPreferences
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.accent
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DragSource
import com.kaiharimoto.mastertool.ui.dnd.DropHover
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlinx.coroutines.delay

/** How long a revealed card keeps its highlight before settling back. */
private const val FLASH_MS = 1400L

private val SECTION_ORDER =
    listOf(DeckSection.MAIN, DeckSection.EXTRA, DeckSection.SIDE)

// The deck column's fixed furniture, kept as lean as it can be read at. Every
// dp here is a dp the cards do not get: these are the numbers the fitter is
// told about, so they have to be the numbers the panes actually use — anything
// the layout spends that the fitter does not know about is height the cards
// were promised and did not get.
private val COLUMN_PADDING = 6.dp
private val PANE_PADDING = 6.dp
private val HEADER_HEIGHT = 28.dp
private val HEADER_GAP = 4.dp
private val PANE_GAP = 8.dp
private val CARD_SPACING = 2.dp

/**
 * How far a card pulls back from a neighbour in another group.
 *
 * The deck is a mosaic — two dp between cards, near enough to touching that a
 * row reads as one surface. The lens does not open every gutter; it opens only
 * the ones where two groups meet, and this is how far each of the two cards
 * gives way. The deck therefore cracks along exactly the lines the groups draw
 * and nowhere else, which is the whole idea: a group is a block of the deck you
 * can see the shape of, not a tint over some cards.
 */
private val CRACK = 4.dp

/** How far inside the cell a block's colour stops. */
private val CELL_INSET = 1.5.dp

/** The group a draft's selection is drawn as, before it has been saved. */
private const val DRAFT_GROUP_ID = "__draft"

/** The main deck's group bar: a row of group chips, or the draft editor. */
private val LEGEND_HEIGHT = 32.dp
private val DRAFT_BAR_HEIGHT = 88.dp

/**
 * The three deck sections, stacked so the whole deck is on screen at once.
 *
 * Sizing runs the other way round from how it used to. The row widths are the
 * fixed thing — ten across for the main deck, fifteen for the extra and side,
 * because that is how a decklist is read — and the fitter solves for the one
 * width all three are drawn at. Everything left over is negative space around
 * the stack, which is why this centres it: the deck sits in the middle of the
 * screen filling what it can, rather than growing margins inside the main pane.
 *
 * Dragging a divider still works and is still remembered — it just means "I am
 * sizing these by hand now", which switches the fitter off until the deck is
 * fitted again from the header.
 */
@Composable
fun DeckPanes(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    onDropped: (DragSession, DropHover?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .onGloballyPositioned { layout.deckColumnHeightPx = it.size.height.toFloat() }
            .padding(COLUMN_PADDING),
    ) {
        val density = LocalDensity.current
        val preferences = layout.preferences

        // Recomputed on every layout pass, which is one division and means the
        // deck re-fits the instant the window, the pool or the row width changes.
        val plan: DeckFit? = if (preferences.fitAll) {
            with(density) {
                DeckFitter.plan(
                    requests = SECTION_ORDER.map { section ->
                        SectionFitRequest(
                            count = state.deck[section].displayCount(preferences.stacked),
                            columns = preferences[section].columns,
                            baselineCount = section.baselineCapacity,
                            spacing = CARD_SPACING.toPx(),
                            collapsed = preferences[section].collapsed,
                            chromeHeight = chromeFor(state, preferences[section].collapsed, section).toPx(),
                        )
                    },
                    availableWidth = maxWidth.toPx(),
                    availableHeight = maxHeight.toPx(),
                    aspectRatio = CARD_ASPECT_RATIO,
                    paneGap = PANE_GAP.toPx(),
                )
            }
        } else {
            null
        }

        val stackWidth = plan?.let { with(density) { it.contentWidth.toDp() } }
        Column(
            when {
                // Sized by hand: the panes divide the column by weight, as they did.
                stackWidth == null -> Modifier.fillMaxSize()

                // Only when even the smallest readable card does not fit — a
                // window shorter than the deck. Scrolling is the honest answer
                // there; drawing cards too small to read is not.
                plan?.fits == false -> Modifier
                    .width(stackWidth)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())

                // The stack is exactly as tall as it needs to be, so what is
                // left over sits around it: the deck in the middle of the
                // screen with a little air, rather than pinned to a corner.
                else -> Modifier.width(stackWidth).align(Alignment.Center)
            },
        ) {
            SECTION_ORDER.forEachIndexed { position, section ->
                val sectionPreferences = preferences[section]
                val fit = plan?.sections?.get(position)

                DeckSectionPane(
                    state = state,
                    layout = layout,
                    drag = drag,
                    onDropped = onDropped,
                    section = section,
                    fit = fit,
                    spacing = CARD_SPACING,
                    scrolls = plan?.fits == false,
                    modifier = when {
                        sectionPreferences.collapsed -> Modifier
                        fit != null -> Modifier.height(with(density) { fit.paneHeight.toDp() })
                        else -> Modifier.weight(sectionPreferences.weight)
                    },
                )

                if (position < SECTION_ORDER.lastIndex) {
                    val next = SECTION_ORDER[position + 1]
                    PaneDivider(
                        // A divider under a fitted column has nothing to trade:
                        // taking hold of it is the decision to size by hand, and
                        // the drag that follows lands on real weights.
                        enabled = !sectionPreferences.collapsed && !preferences[next].collapsed,
                        onDrag = { delta -> layout.resizePanes(section, next, delta) },
                    )
                }
            }
        }
    }
}

/** The capacity a section is sized for before it holds that many cards. */
private val DeckSection.baselineCapacity: Int
    get() = if (this == DeckSection.MAIN) minSize else maxSize

/** How many tiles the section draws, which the stacked view compresses. */
private fun List<CardId>.displayCount(stacked: Boolean): Int =
    if (stacked) distinct().size else size

/**
 * Everything in a pane that is not grid, to the pixel.
 *
 * The fitter subtracts this before it solves, so a bar that appears here and
 * not in this sum is a bar the cards pay for.
 */
private fun chromeFor(state: DeckBuilderState, collapsed: Boolean, section: DeckSection): Dp {
    if (collapsed) return PANE_PADDING * 2 + HEADER_HEIGHT

    var chrome = PANE_PADDING * 2 + HEADER_HEIGHT + HEADER_GAP
    val bar = barHeightFor(state, section)
    if (bar > 0.dp) chrome += bar + HEADER_GAP
    return chrome
}

/** How tall the main deck's group bar is right now, and zero everywhere else. */
private fun barHeightFor(state: DeckBuilderState, section: DeckSection): Dp = when {
    section != DeckSection.MAIN -> 0.dp
    state.groupDraft != null -> DRAFT_BAR_HEIGHT
    state.breakdownVisible -> LEGEND_HEIGHT
    else -> 0.dp
}

@Composable
private fun PaneDivider(enabled: Boolean, onDrag: (Float) -> Unit) {
    val draggableState = rememberDraggableState(onDelta = onDrag)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PANE_GAP)
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
                    .size(width = 48.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
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
    fit: SectionFit?,
    spacing: Dp,
    scrolls: Boolean,
    modifier: Modifier = Modifier,
) {
    val ids = state.deck[section]
    val preferences = layout.preferences[section]
    val accent = section.accent()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    var flashed by remember { mutableStateOf<CardId?>(null) }
    // Written by two `onGloballyPositioned` callbacks — the pane's and its
    // grid's — and registered from both, because they fire parent-first: the
    // pane's callback alone would register whatever origin the grid had on the
    // *previous* layout pass (and (0,0) on the first, which sent every drop to
    // the wrong slot).
    val geometry = remember { PaneGeometry() }
    // What the grid actually settled on, so the header can report it.
    var effectiveColumns by remember { mutableStateOf(preferences.columns) }

    val hover = drag.hover?.takeIf { it.section == section }
    val dropBorder = when {
        hover == null -> null
        hover.accepted -> accent
        else -> MasterToolPalette.Danger
    }

    // Only the pane that owns the requested section reacts; the others ignore
    // it. A collector rather than an effect keyed on the request: the request
    // is consumed in here, and consuming the key of the effect that is
    // handling it would cancel the scroll it was consumed to run.
    LaunchedEffect(Unit) {
        snapshotFlow { state.revealRequest }.collect { request ->
            if (request == null || request.section != section) return@collect
            // Consumed on pickup: this state holder outlives the screen, and an
            // unconsumed request replays its scroll-and-flash every time the
            // builder comes back from the library.
            state.revealRequest = null

            // The stored position indexes the raw list, which is also the grid's
            // order — the breakdown lens no longer reorders anything. Only the
            // stacked view draws something else.
            val item = if (layout.preferences.stacked) {
                DeckGrouping.stacks(state.deck[section]).indexOfFirst { it.id == request.cardId }
            } else {
                request.position
            }
            if (item < 0) return@collect

            gridState.animateScrollToItem(item)
            flashed = request.cardId
            delay(FLASH_MS)
            flashed = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                geometry.paneBounds = it.boundsInRoot()
                geometry.register(drag, section, gridState)
            }
            .padding(PANE_PADDING),
        verticalArrangement = Arrangement.spacedBy(HEADER_GAP),
    ) {
        SectionHeader(state, layout, section, ids.size, preferences, effectiveColumns, accent)

        if (preferences.collapsed) return@Column

        // The main deck's group bar: the legend of what has been drawn up, or
        // the group being drawn up right now.
        val barHeight = barHeightFor(state, section)
        if (barHeight > 0.dp) {
            BreakdownBar(state, Modifier.fillMaxWidth().height(barHeight))
        }

        val stacks = if (layout.preferences.stacked) DeckGrouping.stacks(ids) else emptyList()
        val displayed = if (layout.preferences.stacked) stacks.map { it.id } else ids
        val breakdownActive = state.breakdownVisible &&
            section == DeckSection.MAIN &&
            !layout.preferences.stacked

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Fitted: the fitter already solved for the card size, and the grid
            // is drawn at exactly that size — it fills the pane, which is what
            // leaves the negative space outside the stack rather than inside it.
            // Unfitted: the pane picks a column count for the height it was
            // dragged to, which is the old behaviour, kept for hand-sizing.
            val manualFit: GridFit? = if (fit == null) {
                with(density) {
                    if (preferences.autoFit) {
                        GridFitter.stableFit(
                            count = displayed.size,
                            baselineCount = if (layout.preferences.stacked) 0 else section.baselineCapacity,
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
                                count = displayed.size,
                                columns = preferences.columns,
                                availableWidth = maxWidth.toPx(),
                                spacing = spacing.toPx(),
                                aspectRatio = CARD_ASPECT_RATIO,
                            ) <= maxHeight.toPx(),
                        )
                    }
                }
            } else {
                null
            }

            val columns = fit?.columns ?: manualFit!!.columns
            // Nothing to scroll means nothing for a drag to be mistaken for, so
            // the card can be picked up the instant the finger moves.
            val competesWithScroll = if (fit != null) scrolls else !manualFit!!.fits

            // Written after composition rather than during it — the header sits
            // above this and only needs the number on the next pass.
            SideEffect { effectiveColumns = columns }

            // Nothing is rearranged. The lens is geometry over the deck as it
            // is stored, and a card being picked for a group joins that group's
            // block immediately — drawn as a group that does not exist yet, so
            // the shape you are making is visible while you make it.
            val draft = state.groupDraft.takeIf { section == DeckSection.MAIN }
            val lensGroups = remember(state.groups, draft?.selection, draft?.color) {
                when (draft) {
                    null -> state.groups
                    else -> {
                        val provisional = DeckGroup(
                            id = DRAFT_GROUP_ID,
                            name = draft.name,
                            color = draft.color,
                            order = -1,
                        )
                        draft.selection.fold(state.groups.upsert(provisional)) { groups, id ->
                            groups.assign(id, DRAFT_GROUP_ID)
                        }
                    }
                }
            }

            val plan = if (breakdownActive) {
                remember(displayed, lensGroups, columns) {
                    BreakdownLayout.plan(displayed, lensGroups, columns)
                }
            } else {
                null
            }

            // One spring for the whole mode: the deck breaking apart, and the
            // colour arriving with it.
            val crack by animateFloatAsState(
                if (plan != null) 1f else 0f,
                spring(dampingRatio = 0.68f, stiffness = 240f),
                label = "breakdownCrack",
            )

            val marks = remember(lensGroups) { GroupMarks.marks(lensGroups.ordered()) }

            // Where each block's mark sits: the first cell of every run of
            // cards that touch, so a group split across the deck names each of
            // its pieces rather than leaving the others anonymous.
            val markCells: Map<Int, String> = remember(plan) {
                val laid = plan ?: return@remember emptyMap()
                buildMap {
                    laid.pieces.forEach { piece ->
                        val groupId = piece.groupId ?: return@forEach
                        BreakdownLayout.blocks(piece.cells, laid.columns).forEach { block ->
                            put(block.first(), groupId)
                        }
                    }
                }
            }

            Box(
                modifier = if (fit != null) {
                    Modifier.fillMaxWidth().height(with(density) { fit.gridHeight.toDp() })
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                // Behind the cards: one solid shape per block. What shows is
                // the colour standing in the space the crack opened, and the
                // two dp between cards of the same group — so a block reads as
                // one object with a bold edge, not as cards with a tint.
                if (plan != null && crack > 0.01f) {
                    Canvas(Modifier.matchParentSize()) {
                        val metrics = CellMetrics(
                            cardWidth = cardWidth.toPx(),
                            cardHeight = cardHeight.toPx(),
                            spacing = spacing.toPx(),
                            columns = columns,
                            inset = CELL_INSET.toPx(),
                        )

                        plan.pieces.forEach { piece ->
                            // The part of the deck with no role yet is drawn in
                            // no colour at all: it is what is left, and it
                            // should look like what is left.
                            if (piece.isRemainder) return@forEach
                            val color = MasterToolPalette.Prism[
                                piece.colorIndex % MasterToolPalette.Prism.size
                            ]
                            drawRegion(
                                cells = piece.cells,
                                metrics = metrics,
                                color = color,
                                alpha = crack,
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            geometry.gridOrigin = it.positionInRoot()
                            geometry.register(drag, section, gridState)
                        },
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    if (layout.preferences.stacked) {
                        // Stacks have no positional identity, so dragging one has
                        // nothing coherent to mean; the stepper does that job
                        // instead. The long-press menu still applies, which is why
                        // the tile keeps its gesture handling with only the drag
                        // switched off.
                        items(stacks.size, key = { "${section.name}-stack-${stacks[it].id.value}" }) { i ->
                            val stack = stacks[i]
                            DeckCard(
                                state = state,
                                drag = drag,
                                dragEnabled = false,
                                competesWithScroll = competesWithScroll,
                                onDropped = onDropped,
                                section = section,
                                id = stack.id,
                                copies = stack.count,
                                highlighted = flashed == stack.id,
                                insertionMarker = hover?.accepted == true && hover.index == i,
                                trailingMarker = hover?.accepted == true &&
                                    hover.index == stacks.size && i == stacks.lastIndex,
                                siblings = ids,
                                position = stack.firstIndex,
                                mark = null,
                            )
                        }
                    } else {
                        // Deck order, exactly as stored — the grid is the same
                        // one the lens is off. Indexed keys because a deck
                        // legitimately holds duplicates.
                        items(ids.size, key = { "${section.name}-$it-${ids[it].value}" }) { position ->
                            val group = plan?.groupAt(position)
                            DeckCard(
                                state = state,
                                drag = drag,
                                dragEnabled = true,
                                competesWithScroll = competesWithScroll,
                                onDropped = onDropped,
                                section = section,
                                id = ids[position],
                                copies = state.copiesIn(ids[position], section),
                                highlighted = flashed == ids[position],
                                insertionMarker = hover?.accepted == true && hover.index == position,
                                // "Append at the end" resolves to one past the last
                                // index, which no card's leading edge can show.
                                trailingMarker = hover?.accepted == true &&
                                    hover.index == ids.size && position == ids.lastIndex,
                                siblings = ids,
                                position = position,
                                mark = markCells[position]?.let { marks[it] },
                                // Inside a block the block's own edge is the
                                // card's edge; two lines a millimetre apart read
                                // as a printing error.
                                hairline = group == null,
                                edges = plan?.edgesAt(position) ?: CellEdges.NONE,
                                crack = crack,
                            )
                        }
                    }
                }

                if (ids.isEmpty()) {
                    Text(
                        "Tap a card on the left to add it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

/** Where the cards are, in pixels, so a region can be traced over them. */
private data class CellMetrics(
    val cardWidth: Float,
    val cardHeight: Float,
    val spacing: Float,
    val columns: Int,
    val inset: Float,
) {
    val pitchX: Float get() = cardWidth + spacing
    val pitchY: Float get() = cardHeight + spacing

    /** The x of a lattice line: the boundary [x] cells from the left edge. */
    fun latticeX(x: Int): Float = x * pitchX - spacing / 2f

    fun latticeY(y: Int): Float = y * pitchY - spacing / 2f
}

/**
 * Draws a set of cells as one solid shape per cluster of cards that touch.
 *
 * The outline is traced in [GridRegion] — pure integer geometry, so cards that
 * touch produce one boundary with the edge between them gone, rather than
 * rectangles with seams down the middle. Each edge is then pulled inward by
 * [CellMetrics.inset], which is what leaves bare background between one block
 * and the next.
 *
 * The colour is solid. A wash under a card is a colour you cannot name and a
 * card you cannot read; a solid edge standing in the space the crack opened is
 * both unmistakable and quiet, because it only occupies space the cards gave up.
 *
 * Corners stay square: everything else in this app is square, and a rounded
 * rectilinear polygon needs its radius clamped per corner against the shortest
 * adjacent edge — which for a one-card step is most of that edge, so the shape
 * stops being the shape.
 */
private fun DrawScope.drawRegion(
    cells: List<Int>,
    metrics: CellMetrics,
    color: Color,
    alpha: Float,
) {
    if (cells.isEmpty() || alpha <= 0f) return

    val rings = GridRegion.outline(cells, metrics.columns)
    if (rings.isEmpty()) return

    val path = Path()
    rings.forEach { ring ->
        val corners = ring.corners
        if (corners.size < 4) return@forEach

        corners.indices.forEach { i ->
            val previous = corners[(i - 1 + corners.size) % corners.size]
            val point = corners[i]
            val next = corners[(i + 1) % corners.size]

            // Each edge moves inward along its own interior normal; a corner is
            // where two moved edges meet, and because every edge is axis
            // aligned that intersection is just one x and one y.
            val inNormal = normalOf(previous, point)
            val outNormal = normalOf(point, next)
            val x = metrics.latticeX(point.x) + metrics.inset * (inNormal.first + outNormal.first)
            val y = metrics.latticeY(point.y) + metrics.inset * (inNormal.second + outNormal.second)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    drawPath(path, color.copy(alpha = alpha))
}

/**
 * The interior normal of the edge from [from] to [to].
 *
 * Rings are traced clockwise with the interior on the right, so rotating the
 * direction a quarter turn clockwise — in screen coordinates, where y grows
 * downward — points into the shape.
 */
private fun normalOf(from: GridPoint, to: GridPoint): Pair<Float, Float> {
    val dx = (to.x - from.x).coerceIn(-1, 1).toFloat()
    val dy = (to.y - from.y).coerceIn(-1, 1).toFloat()
    return -dy to dx
}

/**
 * One card in a deck pane.
 *
 * Tap removes a copy, which is the action you take most and which the snackbar
 * can put straight back. Everything less common — and everything that cannot be
 * undone by tapping again — is behind the long-press menu rather than behind a
 * second gesture nobody would find.
 *
 * While a group is being drawn up the tap means something else entirely: it
 * puts the card in the group, or takes it out. That is the one modal gesture in
 * the builder, and it is made obvious by the card standing up off the table in
 * the group's colour — lifted and outlined, never resized, so the deck keeps
 * its shape while you pick through it.
 */
@Composable
private fun DeckCard(
    state: DeckBuilderState,
    drag: DragController,
    dragEnabled: Boolean,
    competesWithScroll: Boolean,
    onDropped: (DragSession, DropHover?) -> Unit,
    section: DeckSection,
    id: CardId,
    copies: Int,
    highlighted: Boolean,
    insertionMarker: Boolean,
    trailingMarker: Boolean,
    siblings: List<CardId>,
    position: Int,
    mark: String?,
    hairline: Boolean = true,
    edges: CellEdges = CellEdges.NONE,
    crack: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val card: Card? = state.index.byId(id)
    var menuOpen by remember { mutableStateOf(false) }

    if (card == null) {
        UnknownCardTile(id)
        return
    }

    val draft = state.groupDraft.takeIf { section == DeckSection.MAIN }
    val selected = draft?.isSelected(id) == true
    val selectionColor = draft
        ?.let { MasterToolPalette.Prism[it.color % MasterToolPalette.Prism.size] }
        ?.takeIf { selected }

    val lift by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "selectionLift",
    )

    // A chosen card carries the mark of the group it is being put in, so the
    // bar's count is not the only thing that says what you have picked.
    val selectionMark = if (selected) GroupMarks.mark(draft?.name.orEmpty()) else null
    val chipColor = selectionColor
        ?: mark?.let { _ ->
            state.groups.groupOf(id)
                ?.let { state.groups.byId(it) }
                ?.let { MasterToolPalette.Prism[it.color % MasterToolPalette.Prism.size] }
        }

    val tile: @Composable () -> Unit = {
        HoverPreview(card) {
            CardTile(
                card = card,
                format = state.format,
                copies = copies,
                highlighted = highlighted,
                // The chosen card wears its group's colour as a solid ring. The
                // turning prismatic one is for a card being *pointed at* — a
                // reveal — and reads as noise on a dozen cards at once.
                outline = selectionColor,
                // Inside a coloured piece the piece's edge is the card's edge.
                hairline = hairline,
                // The card being chosen is not being handled, so its tilt stands
                // down and the lift says what is happening instead. Every other
                // card keeps its feel, which is the opposite of the old blanket
                // rule that killed the tilt on all forty during the one mode
                // where the most cards get touched.
                tactile = !selected,
                onClick = {
                    if (draft != null) {
                        state.toggleDraftSelection(id)
                    } else {
                        state.removeOne(card, section)
                    }
                },
            ) {
                // A bar down the leading edge of the card the drop would land
                // before — or down the trailing edge of the last card, for a
                // drop that appends.
                if (insertionMarker || trailingMarker) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .align(if (insertionMarker) Alignment.CenterStart else Alignment.CenterEnd)
                            .background(MasterToolPalette.AccentBright),
                    )
                }

                // Which group this piece is, in two letters. Six hues cannot
                // name eight groups, and telling violet from magenta across a
                // tablet is guessing rather than reading — so the colour
                // reinforces the mark rather than carrying the name alone.
                val chip = mark ?: selectionMark
                if (chip != null && chipColor != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .size(width = 17.dp, height = 13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(chipColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            chip,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 0.4.sp,
                            ),
                            color = MasterToolPalette.Ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    // The card gives way only on the sides that face another group, so a block
    // of cards stays flush and the deck opens along the group lines alone.
    val opening = CRACK * crack
    Box(
        modifier
            .padding(
                start = if (edges.start) opening else 0.dp,
                top = if (edges.top) opening else 0.dp,
                end = if (edges.end) opening else 0.dp,
                bottom = if (edges.bottom) opening else 0.dp,
            )
            .graphicsLayer {
            // Off the table, not bigger than it was: a selected card keeps its
            // size so the grid it came out of stays legible behind it, and the
            // shape the selection is drawing does not breathe.
            translationY = -8.dp.toPx() * lift
            shadowElevation = 18.dp.toPx() * lift
            shape = RoundedCornerShape(4.dp)
        },
    ) {
        DragSource(
            controller = drag,
            key = "${section.name}-$position-${id.value}",
            competesWithScroll = competesWithScroll,
            session = { DragSession(card, section, position, IntSize.Zero) },
            onLongPress = { menuOpen = true },
            onDropped = onDropped,
            // Picking cards for a group and dragging them somewhere are two
            // readings of the same movement; while a draft is open, tap wins.
            dragEnabled = dragEnabled && draft == null,
            content = tile,
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Inspect") },
                onClick = {
                    menuOpen = false
                    state.inspect(siblings.mapNotNull { state.index.byId(it) }, position)
                },
            )
            DropdownMenuItem(
                text = { Text("Hold card") },
                onClick = { menuOpen = false; state.heldCard = card },
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

            val elsewhere = if (section == DeckSection.SIDE) card.requiredSection() else DeckSection.SIDE
            DropdownMenuItem(
                text = { Text("Move one to ${elsewhere.displayName}") },
                onClick = { menuOpen = false; state.moveCard(card, section, elsewhere) },
            )

            // The pointer/keyboard idiom for assignment; tapping cards into an
            // open draft is the touch one.
            if (section == DeckSection.MAIN) {
                HorizontalDivider()

                state.groups.ordered().forEach { group ->
                    val assigned = state.groups.assignments[id] == group.id
                    DropdownMenuItem(
                        text = { Text(if (assigned) "✓ ${group.name}" else group.name) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(width = 4.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        MasterToolPalette.Prism[
                                            group.color % MasterToolPalette.Prism.size
                                        ]
                                    ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            state.assignCardToGroup(id, if (assigned) null else group.id)
                        },
                    )
                }

                DropdownMenuItem(
                    text = { Text("New group from here…") },
                    onClick = { menuOpen = false; state.startGroupDraft(seed = id) },
                )
            }
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
    accent: Color,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val overCapacity = count > section.maxSize
    val underMinimum = count < section.minSize

    Row(
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            "  ${section.displayName}",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            // Read from the section rather than written out, so the bounds cannot
            // drift from the ones the validator enforces.
            if (section.minSize > 0) {
                "  $count / ${section.minSize}–${section.maxSize}"
            } else {
                "  $count / ${section.maxSize}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                overCapacity || underMinimum -> MasterToolPalette.Danger
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Box(Modifier.weight(1f))

        if (!preferences.collapsed) {
            // The breakdown belongs to the main deck and nowhere else: it is the
            // main forty that gets argued about in roles.
            if (section == DeckSection.MAIN) {
                CompactButton(
                    label = "Breakdown",
                    selected = state.breakdownVisible,
                    onClick = { state.toggleBreakdown() },
                )
            }

            // Ten across, fifteen across — the row width is the setting, and the
            // cards are sized to whatever makes it fit.
            CompactIconButton(
                onClick = { layout.setColumns(section, columns - 1) },
                enabled = columns > SectionPreferences.MIN_COLUMNS,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer, larger cards per row")
            }
            Text(columns.toString(), style = MaterialTheme.typography.labelMedium)
            CompactIconButton(
                onClick = { layout.setColumns(section, columns + 1) },
                enabled = columns < SectionPreferences.MAX_COLUMNS,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "More, smaller cards per row")
            }

            Box {
                CompactIconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "${section.displayName} deck options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortMode.entries.filter { it != SortMode.MANUAL }.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("Sort by ${mode.displayName.lowercase()}") },
                            onClick = {
                                menuOpen = false
                                layout.setSortMode(section, mode)
                                state.sortSection(section, mode)
                            },
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                if (layout.preferences.fitAll) {
                                    "✓ Fit the whole deck on screen"
                                } else {
                                    "Fit the whole deck on screen"
                                }
                            )
                        },
                        onClick = {
                            menuOpen = false
                            layout.setFitAll(!layout.preferences.fitAll)
                        },
                    )

                    if (section == DeckSection.MAIN) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("New group…") },
                            onClick = { menuOpen = false; state.startGroupDraft() },
                        )
                        DropdownMenuItem(
                            text = { Text("Manage groups…") },
                            onClick = { menuOpen = false; state.groupManagerVisible = true },
                        )
                    }
                }
            }
        }

        CompactIconButton(onClick = { layout.toggleCollapsed(section) }) {
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
 * A header-sized icon button.
 *
 * Material's is 48dp square, which is most of a card row spent on chrome three
 * times over. The touch target stays honest — 26dp with the pane's own padding
 * around it — and the height the fitter is told about stays real.
 */
@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(26.dp),
        content = content,
    )
}

@Composable
private fun CompactButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(24.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * A drop target's geometry, assembled across two layout callbacks.
 *
 * The pane's bounds and its grid's origin arrive in separate
 * `onGloballyPositioned` callbacks, and those fire parent-first — so either one
 * alone would register the other's value from the previous layout pass.
 * Both writers register, and whichever ran last in a pass wins with a fully
 * current pair.
 */
private class PaneGeometry {
    var paneBounds: Rect = Rect.Zero
    var gridOrigin: Offset = Offset.Zero

    fun register(drag: DragController, section: DeckSection, gridState: LazyGridState) {
        if (paneBounds == Rect.Zero) return
        drag.registerPane(section, paneBounds, gridState, gridOrigin)
    }
}

/**
 * A passcode the card database does not know.
 *
 * Given the same shape as a real card so it does not collapse its grid row, and
 * showing the passcode because that is the only thing anyone can act on — it is
 * what the validator names in the matching error.
 */
@Composable
private fun UnknownCardTile(id: CardId) {
    Box(
        Modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(4.dp))
            .background(MasterToolPalette.SurfaceRaised)
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
