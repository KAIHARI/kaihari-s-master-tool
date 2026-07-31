package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.BreakdownSlot
import com.kaiharimoto.mastertool.core.deck.DeckBreakdown
import com.kaiharimoto.mastertool.core.deck.DeckGrouping
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.layout.DeckFit
import com.kaiharimoto.mastertool.core.layout.DeckFitter
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

// The deck column's fixed furniture. These are the numbers the fitter is told
// about, so they have to be the numbers the panes actually use — anything the
// layout spends that the fitter does not know about is height the cards were
// promised and did not get, which is how they ended up out of bounds.
private val PANE_PADDING = 8.dp
private val HEADER_HEIGHT = 34.dp
private val HEADER_GAP = 6.dp
private val PANE_GAP = 12.dp
private val CARD_SPACING = 6.dp

/**
 * How far apart two groups sit in the breakdown lens.
 *
 * Deliberately small. It is taken out of the card's own cell rather than added
 * to the grid, so the deck cannot grow past the box it was fitted to — the
 * cards on either side of a boundary draw a little narrower and the space
 * between them opens by twice this. Enough to read a run at a glance, not
 * enough to look like the deck has been rearranged.
 */
private val BREAKDOWN_GAP = 6.dp

/** The main deck's group bar: a row of group chips, or the draft editor. */
private val LEGEND_HEIGHT = 36.dp
private val DRAFT_BAR_HEIGHT = 112.dp

/**
 * The three deck sections, stacked so the whole deck is on screen at once.
 *
 * Sizing runs the other way round from how it used to. The row widths are the
 * fixed thing — ten across for the main deck, fifteen for the extra and side,
 * because that is how a decklist is read — and the card size is whatever makes
 * all three fit the column. Previously each pane chose its own column count to
 * suit a height a divider drag had handed it, which is a negotiation with no
 * settlement: the panes could not agree on a total, and the main deck spilled
 * past its box.
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
            .padding(10.dp),
    ) {
        val density = LocalDensity.current
        val preferences = layout.preferences

        // Recomputed on every layout pass, which is one division per section and
        // means the deck re-fits the instant the window, the pool or the row
        // width changes.
        val plan: DeckFit? = if (preferences.fitAll) {
            with(density) {
                DeckFitter.plan(
                    requests = SECTION_ORDER.map { section ->
                        SectionFitRequest(
                            count = state.deck[section].displayCount(preferences.stacked),
                            columns = preferences[section].columns,
                            baselineCount = section.baselineCapacity,
                            collapsed = preferences[section].collapsed,
                            chromeHeight = chromeFor(state, preferences[section].collapsed, section).toPx(),
                        )
                    },
                    availableWidth = maxWidth.toPx(),
                    availableHeight = maxHeight.toPx(),
                    spacing = CARD_SPACING.toPx(),
                    aspectRatio = CARD_ASPECT_RATIO,
                    paneGap = PANE_GAP.toPx(),
                )
            }
        } else {
            null
        }

        Column(
            Modifier
                .fillMaxSize()
                // Only when even the smallest readable card does not fit — a
                // window shorter than the deck. Scrolling is the honest answer
                // there; drawing cards too small to read is not.
                .then(
                    if (plan?.fits == false) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                ),
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
 * The fitter subtracts this before it divides, so a bar that appears here and
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
                    .size(width = 56.dp, height = 2.dp)
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
        val breakdownActive = state.breakdownVisible && section == DeckSection.MAIN

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Fitted: the fitter already solved for the card size, and the grid
            // is drawn at exactly that size and centred in whatever is left.
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
                            spacing = CARD_SPACING.toPx(),
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
                                spacing = CARD_SPACING.toPx(),
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

            val slots = if (breakdownActive) {
                DeckBreakdown.slots(displayed, state.groups, columns)
            } else {
                emptyList()
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = with(density) {
                        if (fit != null) {
                            Modifier
                                .width(fit.gridWidth.toDp())
                                .height(fit.gridHeight.toDp())
                        } else {
                            Modifier.fillMaxSize()
                        }
                    }
                        .onGloballyPositioned {
                            geometry.gridOrigin = it.positionInRoot()
                            geometry.register(drag, section, gridState)
                        },
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                    verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
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
                                // The resolver's index is a grid index, which here
                                // counts stacks; the drop itself is translated back
                                // to a list position when it lands.
                                insertionMarker = hover?.accepted == true && hover.index == i,
                                trailingMarker = hover?.accepted == true &&
                                    hover.index == stacks.size && i == stacks.lastIndex,
                                siblings = ids,
                                position = stack.firstIndex,
                                slot = slots.getOrNull(i),
                            )
                        }
                    } else {
                        // Indexed keys because a deck legitimately holds duplicates.
                        items(ids.size, key = { "${section.name}-$it-${ids[it].value}" }) { position ->
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
                                slot = slots.getOrNull(position),
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
 * the builder, and it is made obvious by the deck lifting under it — every
 * selected card sits up off the table until Save or Cancel.
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
    slot: BreakdownSlot?,
) {
    val card: Card? = state.index.byId(id)
    var menuOpen by remember { mutableStateOf(false) }

    if (card == null) {
        UnknownCardTile(id)
        return
    }

    val selecting = state.groupDraft != null && section == DeckSection.MAIN
    val selected = selecting && state.groupDraft?.isSelected(id) == true
    val groupColor = slot?.groupId
        ?.let { state.groups.byId(it) }
        ?.let { MasterToolPalette.Prism[it.color % MasterToolPalette.Prism.size] }

    // The gap is the whole breakdown: cards stay exactly where the deck put
    // them and the space between two groups opens up. Springing it in means the
    // list separates rather than jumps, so you can see which cards moved apart.
    val gapStart by animateDpAsState(
        if (slot?.gapBefore == true) BREAKDOWN_GAP else 0.dp,
        label = "gapStart",
    )
    val gapEnd by animateDpAsState(
        if (slot?.gapAfter == true) BREAKDOWN_GAP else 0.dp,
        label = "gapEnd",
    )
    val lift by animateFloatAsState(if (selected) 1f else 0f, label = "selectionLift")

    val tile: @Composable () -> Unit = {
        HoverPreview(card) {
            CardTile(
                card = card,
                format = state.format,
                copies = copies,
                highlighted = highlighted || selected,
                // A card being chosen for a group is not being handled, so the
                // tilt stands down and the lift below says what is happening.
                tactile = !selecting,
                onClick = {
                    if (selecting) state.toggleDraftSelection(id) else state.removeOne(card, section)
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

                // Which group this card is in, as a hairline along its foot: the
                // gaps say where the groups are, this says which is which.
                if (groupColor != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(groupColor),
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .padding(start = gapStart, end = gapEnd)
            .graphicsLayer {
                val grow = 1f + 0.07f * lift
                scaleX = grow
                scaleY = grow
                shadowElevation = lift * 16.dp.toPx()
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
            dragEnabled = dragEnabled && !selecting,
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
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            "  ${section.displayName} Deck",
            style = MaterialTheme.typography.titleSmall,
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
                    label = if (state.breakdownVisible) "Breakdown ·" else "Breakdown",
                    selected = state.breakdownVisible,
                    onClick = { state.breakdownVisible = !state.breakdownVisible },
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
 * Material's is 48dp square, which is a third of a card row spent on chrome
 * three times over. The touch target stays honest — 30dp with the pane's own
 * padding around it — and the height the fitter is told about stays real.
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
        modifier = Modifier.size(30.dp),
        content = content,
    )
}

@Composable
private fun CompactButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
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
