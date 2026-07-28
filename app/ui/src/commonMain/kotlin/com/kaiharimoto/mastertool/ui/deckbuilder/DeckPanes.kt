package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kaiharimoto.mastertool.core.layout.DealAnimation
import com.kaiharimoto.mastertool.core.layout.GridFit
import com.kaiharimoto.mastertool.core.layout.GridFitter
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

        gridState.animateScrollToItem(request.position)
        flashed = request.cardId
        delay(FLASH_MS)
        flashed = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            // A mat rather than a panel, bound in the section's own colour —
            // which is what keeps the three panes apart now that the cards
            // inside them touch and no background shows between.
            .tableSurface(accent, LocalMasterToolColors.current.mat)
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
                        )
                    }
                } else {
                    // Keyed by which copy, not by where it sits. A positional key
                    // changes for every tile after an insertion, so the grid would
                    // see the whole tail replaced rather than moved, and nothing
                    // could animate.
                    val copies = DeckKeys.occurrences(ids)

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
                            // Sorting a section, or dropping a card into the middle
                            // of one, now slides the cards that moved instead of
                            // redrawing the pane somewhere else.
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
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

                // A bar down the leading edge of the card the drop would land
                // before, in the colour of the section it would land in -- which
                // is the same colour that pane is bound in.
                if (insertionMarker) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .align(Alignment.CenterStart)
                            .background(section.accent()),
                    )
                }
            }
        }
    }

    Box(
        modifier.graphicsLayer {
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

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // Selection is entered from here rather than from a modifier key, so
            // it exists at all on a tablet with no keyboard.
            if (!selecting) {
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = { menuOpen = false; state.select(section, position) },
                )
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
