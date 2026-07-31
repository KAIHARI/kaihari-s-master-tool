package com.kaiharimoto.mastertool.ui.deckbuilder

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.deck.BreakdownEntry
import com.kaiharimoto.mastertool.core.deck.DeckBreakdown
import com.kaiharimoto.mastertool.core.deck.DeckGrouping
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.layout.GridFit
import com.kaiharimoto.mastertool.core.layout.GridFitter
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
import com.kaiharimoto.mastertool.ui.theme.LocalDarkTheme
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.chromaticEdge
import kotlinx.coroutines.delay

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
    // Written by two `onGloballyPositioned` callbacks — the pane's and its
    // grid's — and registered from both, because they fire parent-first: the
    // pane's callback alone would register whatever origin the grid had on the
    // *previous* layout pass (and (0,0) on the first, which sent every drop to
    // the wrong slot).
    val geometry = remember { PaneGeometry() }
    // What the grid actually settled on, so the header can report it and taking
    // manual control starts from what is already on screen.
    var effectiveColumns by remember { mutableStateOf(preferences.columns) }

    val hover = drag.hover?.takeIf { it.section == section }
    val dropBorder = when {
        hover == null -> null
        hover.accepted -> accent
        else -> MasterToolPalette.Danger
    }

    val breakdownActive = state.breakdownVisible &&
        section == DeckSection.MAIN && !layout.preferences.stacked

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

            // The stored position indexes the raw list; the grid may be
            // showing one tile per distinct card, or the breakdown's
            // header-and-block order.
            val item = when {
                layout.preferences.stacked ->
                    DeckGrouping.stacks(state.deck[section]).indexOfFirst { it.id == request.cardId }

                breakdownActive ->
                    DeckBreakdown.flatten(state.deck[section], state.groups).indexOfFirst {
                        it is BreakdownEntry.CardEntry && it.rawIndex == request.position
                    }

                else -> request.position
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
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(state, layout, section, ids.size, preferences, effectiveColumns, accent)

        if (preferences.collapsed) return@Column

        if (ids.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap a card on the left to add it here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val stacks = if (layout.preferences.stacked) DeckGrouping.stacks(ids) else emptyList()
        val itemCount = if (layout.preferences.stacked) stacks.size else ids.size

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val spacing = 6.dp

            // The stacked view compresses to its contents; everything else is
            // sized for the section's natural capacity, so the first card
            // added lands in a 40-card-shaped grid and nothing reflows while
            // ratios are being weighed. Only growing past the baseline zooms
            // the grid out.
            val baseline = when {
                layout.preferences.stacked -> 0
                section == DeckSection.MAIN -> section.minSize
                else -> section.maxSize
            }

            // Recomputed on every layout pass, which is cheap and means the grid
            // re-fits the moment the pane is resized or a card is added.
            val fit = with(density) {
                if (preferences.autoFit) {
                    GridFitter.stableFit(
                        count = itemCount,
                        baselineCount = baseline,
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
            // above this and only needs the number on the next pass.
            SideEffect { effectiveColumns = fit.columns }

            LazyVerticalGrid(
                columns = GridCells.Fixed(fit.columns),
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
                if (breakdownActive) {
                    // The breakdown lens: one full-width label per group, then
                    // its cards. Position within a block is display order —
                    // the deck's stored order is untouched, which is why a
                    // drop here means "assign", never "insert".
                    val entries = DeckBreakdown.flatten(ids, state.groups)
                    val dropTarget = hover?.takeIf { it.accepted }
                        ?.let { DeckBreakdown.dropGroup(entries, it.index) }
                    val dropLive = hover?.accepted == true

                    entries.forEachIndexed { i, entry ->
                        when (entry) {
                            is BreakdownEntry.Header -> item(
                                key = "${section.name}-hdr-${entry.groupId ?: "ungrouped"}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                BreakdownHeader(
                                    entry = entry,
                                    count = entries.count {
                                        it is BreakdownEntry.CardEntry && it.groupId == entry.groupId
                                    },
                                    receiving = dropLive && dropTarget == entry.groupId,
                                )
                            }

                            is BreakdownEntry.CardEntry -> item(
                                key = "${section.name}-bd-$i-${entry.id.value}",
                            ) {
                                DeckCard(
                                    state = state,
                                    drag = drag,
                                    dragEnabled = true,
                                    competesWithScroll = competesWithScroll,
                                    onDropped = onDropped,
                                    section = section,
                                    id = entry.id,
                                    copies = state.copiesIn(entry.id, section),
                                    highlighted = flashed == entry.id,
                                    insertionMarker = false,
                                    trailingMarker = false,
                                    siblings = ids,
                                    position = entry.rawIndex,
                                )
                            }
                        }
                    }
                } else if (layout.preferences.stacked) {
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
) {
    val card: Card? = state.index.byId(id)
    var menuOpen by remember { mutableStateOf(false) }

    if (card == null) {
        UnknownCardTile(id)
        return
    }

    val tile: @Composable () -> Unit = {
        HoverPreview(card) {
            CardTile(
                card = card,
                format = state.format,
                copies = copies,
                highlighted = highlighted,
                onClick = { state.removeOne(card, section) },
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
            }
        }
    }

    Box {
        DragSource(
            controller = drag,
            key = "${section.name}-$position-${id.value}",
            competesWithScroll = competesWithScroll,
            session = { DragSession(card, section, position, IntSize.Zero) },
            onLongPress = { menuOpen = true },
            onDropped = onDropped,
            dragEnabled = dragEnabled,
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

            // The pointer/keyboard idiom for assignment; dragging onto a block
            // in the breakdown is the touch one.
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
                    text = { Text("Manage groups…") },
                    onClick = { menuOpen = false; state.groupManagerVisible = true },
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
            style = MaterialTheme.typography.labelMedium,
            color = when {
                overCapacity || underMinimum -> MasterToolPalette.Danger
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Box(Modifier.weight(1f))

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
            Text(columns.toString(), style = MaterialTheme.typography.labelMedium)
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

                    if (section == DeckSection.MAIN) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.breakdownVisible) {
                                        "✓ Breakdown view"
                                    } else {
                                        "Breakdown view"
                                    }
                                )
                            },
                            onClick = {
                                menuOpen = false
                                state.breakdownVisible = !state.breakdownVisible
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Manage groups…") },
                            onClick = { menuOpen = false; state.groupManagerVisible = true },
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
 * One group's label row in the breakdown: colour chip, name, count.
 *
 * While an accepted drag hovers over its block the row wears the chromatic
 * edge — the light lands on the group about to receive the card.
 */
@Composable
private fun BreakdownHeader(
    entry: BreakdownEntry.Header,
    count: Int,
    receiving: Boolean,
) {
    val dark = LocalDarkTheme.current
    val color = entry.color?.let { MasterToolPalette.Prism[it % MasterToolPalette.Prism.size] }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (receiving) {
                    Modifier.chromaticEdge(dark = dark, cornerRadius = 4.dp)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color ?: MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(
            "  ${entry.name}",
            style = MaterialTheme.typography.labelLarge,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  ·  $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
