package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.layout.DockMetrics
import com.kaiharimoto.mastertool.core.layout.PoolDock
import com.kaiharimoto.mastertool.core.layout.PoolStop
import com.kaiharimoto.mastertool.core.layout.Posture
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.ui.dnd.DragController
import com.kaiharimoto.mastertool.ui.dnd.DragSession
import com.kaiharimoto.mastertool.ui.dnd.DropHover
import com.kaiharimoto.mastertool.ui.egg.ChibiLogo
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.update.UpdateState

// The dock's own furniture, to the pixel, because the fitter that decides where
// PEEK sits is told these numbers and a bar it has not been told about is a
// search field pushed off the bottom of the screen. Same contract as
// `chromeFor` in DeckPanes.
private val DOCK_GRABBER = 20.dp
// 44, not the 36 this row needs to draw in: Material's own minimum interactive
// size is 48, and the Filters button inside it is a tap target on the surface
// that is reached by thumb more than any other in the app.
private val DOCK_META = 44.dp
private val DOCK_FIELD = 56.dp
private val DOCK_GAP = 6.dp
private val DOCK_BOTTOM = 8.dp
private val DOCK_SIDE = 10.dp

/**
 * Everything in the dock that is not results, plus two dp of slack.
 *
 * The slack is not decoration. PEEK is exactly this number, so without it the
 * weighted results grid is asked for precisely zero and a dp-to-pixel rounding
 * of a fraction the wrong way squeezes the search field instead — the one
 * element in here that must never lose a pixel.
 */
private val DOCK_CHROME =
    DOCK_GRABBER + DOCK_META + DOCK_FIELD + DOCK_GAP * 3 + DOCK_BOTTOM + 2.dp

/**
 * How much deck must survive when the pool is half open.
 *
 * Two rows of six-across cards and their header, which is the smallest thing
 * that still reads as a deck rather than as a strip.
 */
private val MIN_DECK = 220.dp

/** How tall the portrait header is, and it is deliberately one line. */
private val TALL_BAR_HEIGHT = 52.dp

/**
 * The deck builder in a portrait window.
 *
 * ## The arrangement, and the one sentence behind it
 *
 * *The pool goes where the thumbs are.* On a tablet held in landscape the card
 * database is a pane down the left, because the hand holding that edge can
 * reach it. Turn the same app portrait on a phone and the left-hand pane is
 * across the top of the screen, which is the one place on a phone a thumb
 * cannot go — so the pool comes off the side and docks along the bottom, and
 * its search field sits at the very bottom of the dock, immediately above the
 * keyboard that will open under it.
 *
 * Everything else follows from that. The deck takes what is left above, sized
 * width-first and scrolling (see [com.kaiharimoto.mastertool.core.layout.DeckFitter.stack]),
 * because a deck solved to fit a third of a phone's height is a deck drawn as
 * lint. The header keeps one line and puts almost everything behind the
 * overflow — shared with the tablet's header, so a setting cannot exist on one
 * device and not the other.
 *
 * ## What typing does
 *
 * Typing means the pool: a finger in the search field takes the dock to
 * [PoolStop.FULL] and the deck out of the way, which on a 780dp phone is the
 * difference between four results above the keyboard and twenty. Letting go of
 * the field brings the deck back — but never all the way to [PoolStop.PEEK],
 * because closing the dock over the results of the search that was just typed
 * throws away the answer. That rule is [PoolDock.afterTyping], and it is in
 * core with a test on it.
 */
@Composable
fun TallBuilderBody(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    drag: DragController,
    searchFocus: FocusRequester,
    onDropped: (DragSession, DropHover?) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlay: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TallTopBar(state, layout, updateState, onOpenLibrary, onOpenPlay)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val metrics = with(density) {
                DockMetrics(
                    windowHeight = maxHeight.toPx(),
                    chrome = DOCK_CHROME.toPx(),
                    minDeck = MIN_DECK.toPx(),
                )
            }

            // Typing is a posture the dock is *in*, not one it is *set* to: it
            // never reaches the stored preference, so a process killed with the
            // keyboard up does not come back with the pool over the whole deck.
            var typing by remember { mutableStateOf(false) }
            val settled = if (typing) PoolDock.whileTyping() else layout.preferences.poolStop

            // Non-null only while a thumb is on the grabber. The deck is sized
            // against the settled stop rather than this, so a drag moves one
            // surface instead of re-solving the whole deck sixty times a second.
            var dragHeight by remember { mutableStateOf<Float?>(null) }
            val animated by animateFloatAsState(
                PoolDock.height(settled, metrics),
                spring(dampingRatio = 0.85f, stiffness = 420f),
                label = "poolDock",
            )
            val liveHeight = dragHeight ?: animated
            val deckHeight = with(density) {
                PoolDock.deckHeight(settled, metrics).toDp()
            }

            // Composed at zero height rather than dropped, so the deck's scroll
            // position survives every trip to the keyboard and back.
            DeckPanes(
                state = state,
                layout = layout,
                drag = drag,
                onDropped = onDropped,
                posture = Posture.TALL,
                modifier = Modifier.fillMaxWidth().height(deckHeight),
            )

            PoolDockSurface(
                state = state,
                layout = layout,
                drag = drag,
                searchFocus = searchFocus,
                onDropped = onDropped,
                heightPx = liveHeight,
                onDrag = { delta ->
                    val from = dragHeight ?: liveHeight
                    dragHeight = (from - delta).coerceIn(
                        PoolDock.height(PoolStop.PEEK, metrics),
                        PoolDock.height(PoolStop.FULL, metrics),
                    )
                },
                onDragStopped = { velocity ->
                    val released = dragHeight
                    dragHeight = null
                    if (released != null) {
                        // A flick is an aim: project the release a twelfth of a
                        // second forward so a short fast drag reaches the stop
                        // it was thrown at rather than the one it started from.
                        val projected = released - velocity * FLICK_SECONDS
                        layout.update { it.copy(poolStop = PoolDock.nearest(projected, metrics)) }
                    }
                },
                onTypingChanged = { focused ->
                    typing = focused
                    if (!focused) {
                        layout.update { it.copy(poolStop = PoolDock.afterTyping(it.poolStop)) }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** How far ahead of a released flick the dock aims. */
private const val FLICK_SECONDS = 1f / 12f

/**
 * The portrait header: one line, and nothing hidden off the end of it.
 *
 * The tablet's bar holds about 1400dp of content. There is no arrangement of
 * that which fits 360dp, so this is a different bar rather than a scrolling
 * copy of that one — a horizontally scrolling primary header is a header whose
 * controls you have to go looking for. What survives on the line is what you
 * touch while building: the deck's name, whether it is legal, and Save. The
 * rest is one tap away behind the overflow, which is the *same* overflow the
 * tablet has.
 */
@Composable
private fun TallTopBar(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    updateState: UpdateState,
    onOpenLibrary: () -> Unit,
    onOpenPlay: () -> Unit,
) {
    val validation = state.validation
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TALL_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChibiLogo(onWake = { state.eggVisible = true })

        OutlinedTextField(
            value = state.deckName,
            onValueChange = state::rename,
            singleLine = true,
            placeholder = { Text("Deck name", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
        )

        if (state.isSyncing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        // The legality readout is the way in to the issue list, and on a phone
        // it is also the only place the deck's own counts are visible without
        // scrolling to a section header.
        AssistChip(
            onClick = { state.issuesVisible = true },
            label = {
                Text(
                    if (validation.isLegal) {
                        "${state.deck.main.size}"
                    } else {
                        "${validation.errors.size}!"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = when {
                        !validation.isLegal -> MasterToolPalette.Danger
                        validation.warnings.isNotEmpty() -> MasterToolPalette.Warning
                        else -> MasterToolPalette.SideAccent
                    },
                )
            },
        )

        IconButton(onClick = { state.save() }) {
            Icon(Icons.Filled.Save, contentDescription = "Save deck")
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // The controls the tablet's bar has room to keep as buttons.
                DropdownMenuItem(
                    text = { Text("Undo") },
                    leadingIcon = { Icon(Icons.Filled.Undo, contentDescription = null) },
                    enabled = state.canUndo,
                    onClick = { menuOpen = false; state.undo() },
                )
                DropdownMenuItem(
                    text = { Text("Redo") },
                    leadingIcon = { Icon(Icons.Filled.Redo, contentDescription = null) },
                    enabled = state.canRedo,
                    onClick = { menuOpen = false; state.redo() },
                )
                DropdownMenuItem(
                    text = { Text("Deck statistics") },
                    onClick = { menuOpen = false; state.statsVisible = true },
                )
                DropdownMenuItem(
                    text = { Text("Opening-hand odds") },
                    onClick = { menuOpen = false; state.openGoal() },
                )
                DropdownMenuItem(
                    text = { Text("Play") },
                    enabled = state.deck.main.size >= 5,
                    onClick = { menuOpen = false; onOpenPlay() },
                )
                DropdownMenuItem(
                    text = { Text("Library") },
                    onClick = { menuOpen = false; onOpenLibrary() },
                )
                DropdownMenuItem(
                    text = { Text("Format: ${state.format.name}") },
                    onClick = {
                        val next = if (state.format == Format.TCG) Format.OCG else Format.TCG
                        state.onFormatChange(next)
                        layout.update { it.copy(format = next) }
                    },
                )

                HorizontalDivider()
                DeckFileMenuItems(state) { menuOpen = false }
                HorizontalDivider()
                AppSettingsMenuItems(state, layout, updateState) { menuOpen = false }
            }
        }
    }
}

/**
 * The pool, docked along the bottom edge.
 *
 * Read bottom-up, because that is the order a thumb meets it: the search field,
 * then what is narrowing the pool and how much it found, then the results, then
 * the grabber that decides how much of the screen all of that gets.
 *
 * What keeps the field above the keyboard rather than under it is one
 * `imePadding`, and where it sits in the modifier chain matters — see the
 * comment on the `Surface`.
 */
@Composable
private fun PoolDockSurface(
    state: DeckBuilderState,
    layout: DeckLayoutState,
    drag: DragController,
    searchFocus: FocusRequester,
    onDropped: (DragSession, DropHover?) -> Unit,
    heightPx: Float,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val draggableState = rememberDraggableState(onDelta = onDrag)

    Surface(
        // `imePadding` outside the height, not inside it, and the order is the
        // whole of the bug it fixes. Inside, the keyboard eats the dock's own
        // content: focus the *deck name* field while the pool is at PEEK and
        // the search field it is holding open gets squeezed to nothing. Outside,
        // the padding lifts the dock above the keyboard and the height is
        // coerced into what is left, so the dock shrinks to fit rather than
        // losing its furniture.
        //
        // The app's SafeArea deliberately leaves the IME out of its insets —
        // padding the *whole app* by the keyboard re-solves every deck pane the
        // moment anybody types — so the one surface that has to move for it
        // asks for it here.
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .height(with(density) { heightPx.toDp() }),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // The whole dock is where cards come from, so it is also where
                // a card dragged out of the deck can be dropped to leave it.
                .onGloballyPositioned { drag.registerSearchPane(it.boundsInRoot()) }
                .padding(start = DOCK_SIDE, end = DOCK_SIDE, bottom = DOCK_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(DOCK_GAP),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DOCK_GRABBER)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        // Taking hold of the dock is a decision to look at it
                        // rather than to type into it, and a keyboard left up
                        // over a dock being resized is a keyboard in the way.
                        onDragStarted = { focusManager.clearFocus() },
                        onDragStopped = { velocity -> onDragStopped(velocity) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
            }

            // Results take everything the chrome does not, which at PEEK is
            // nothing — the grid collapses and the field stays whole, which is
            // the point of telling the dock's own metrics about this furniture.
            PoolGrid(
                state = state,
                layout = layout,
                drag = drag,
                gridState = gridState,
                onDropped = onDropped,
                columns = layout.preferences.tallSearchColumns,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            )

            SearchMetaRow(
                state = state,
                layout = layout,
                modifier = Modifier.height(DOCK_META),
            )

            SearchField(
                state = state,
                searchFocus = searchFocus,
                modifier = Modifier.fillMaxWidth().height(DOCK_FIELD),
                onFocusChanged = onTypingChanged,
                // The IME's Search key means "I have found it": the keyboard
                // goes down and the deck comes back up, which is the whole
                // reason the dock ever left.
                onSubmit = { onTypingChanged(false) },
            )
        }
    }
}
