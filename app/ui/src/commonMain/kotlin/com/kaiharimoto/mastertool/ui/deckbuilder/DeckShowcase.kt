package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.layout.DealAnimation
import com.kaiharimoto.mastertool.core.layout.GridFitter
import com.kaiharimoto.mastertool.core.deck.Breaks
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CARD_CORNER
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.accent
import com.kaiharimoto.mastertool.ui.components.rememberScreenCapture
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.tableSurface
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle
import kotlinx.coroutines.launch

/** Space between the three blocks, and around the whole thing. */
private val BLOCK_GAP = 14.dp

/**
 * How far a group stands clear of the one above it.
 *
 * Smaller than the space between sections, which is the right order: a gap
 * inside the Main deck is a quieter statement than the end of the Main deck.
 */
private val GROUP_SPACE = 7.dp
private val MARGIN = 22.dp

/** Room set aside for the one line of writing on the screen. */
private val CAPTION_HEIGHT = 34.dp

/** How long the whole deck takes to arrive. Slower than a pane: this is a reveal. */
private const val REVEAL_MS = 900

private val SHOWCASE_ORDER =
    listOf(DeckSection.MAIN, DeckSection.EXTRA, DeckSection.SIDE)

/**
 * The deck, and nothing else.
 *
 * Every builder can show you a decklist. None of them can show you your deck —
 * the arrangement you actually made, at the size you would see it in front of
 * you, without a header or a stepper or a count badge on top of it. That is what
 * this is for, and it is the reason the free ordering matters in the first
 * place: an arrangement nobody can look at is not an arrangement.
 *
 * So there are no controls at all. Three blocks share one card size — sizing
 * them separately would give the Side deck's fifteen cards a different scale
 * from the Main's forty, and a deck laid out at two scales does not read as one
 * deck — and anything pressed or tapped closes it.
 */
@Composable
fun DeckShowcase(state: DeckBuilderState, onDismiss: () -> Unit) {
    val colors = LocalMasterToolColors.current
    val blocks = SHOWCASE_ORDER
        .map { it to state.deck[it] }
        .filter { it.second.isNotEmpty() }

    // Dealt on entry, in one pass across the whole deck rather than per section,
    // so it arrives as a deck being laid out rather than three lists appearing.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = REVEAL_MS, easing = LinearEasing))
    }

    val total = blocks.sumOf { it.second.size }
    val capture = rememberScreenCapture()
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            // No ripple and no indication: this is a backdrop that happens to be
            // dismissable, not a button the size of the screen.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // The picture is exactly what this box draws, which is why the one
        // control on the screen is outside it: a save button in the corner of
        // somebody's decklist is not a thing anybody wants to post.
        //
        // The mat is drawn by a *child* rather than by a modifier beside the
        // capture. Draw modifiers run in chain order and the recording only
        // covers what its own `drawContent` reaches, so a mat added next to it
        // would sit on the screen and not in the file -- and the picture would
        // come out as a deck floating on nothing, which is a bug that only
        // appears in the exported copy.
        Box(Modifier.fillMaxSize().then(capture.modifier)) {
            Box(Modifier.fillMaxSize().tableSurface(colors.accent, colors.mat)) {
                ShowcaseContents(state, blocks, total, reveal)
            }
        }

        if (blocks.isNotEmpty()) {
            SavePicture(
                onClick = { scope.launch { state.exportPicture(capture.png()) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(MARGIN),
            )
        }
    }
}

@Composable
private fun ShowcaseContents(
    state: DeckBuilderState,
    blocks: List<Pair<DeckSection, List<CardId>>>,
    total: Int,
    reveal: Animatable<Float, *>,
) {
    Box(Modifier.fillMaxSize()) {
        if (blocks.isEmpty()) {
            Text(
                "Nothing to show yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(MARGIN)) {
            val density = LocalDensity.current

            // Groups are laid out on their own rows, so a section with gaps in
            // it needs more rows than its card count implies -- the last row of
            // each group is usually part-full. Fitting to the section totals
            // would size the cards for a deck that packs tighter than this one
            // does, and the bottom of it would fall off the screen.
            val groups = blocks.map { (section, ids) ->
                state.breaksIn(section).groups(ids.size).map { it.count() }
            }
            val extraRows = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
            val gaps = with(density) {
                (BLOCK_GAP * (blocks.size - 1) + CAPTION_HEIGHT + GROUP_SPACE * extraRows).toPx()
            }

            val fit = with(density) {
                GridFitter.fitAll(
                    counts = groups.flatten(),
                    availableWidth = maxWidth.toPx(),
                    availableHeight = maxHeight.toPx(),
                    spacing = 0f,
                    gaps = gaps,
                    aspectRatio = CARD_ASPECT_RATIO,
                    minColumns = 4,
                    maxColumns = 24,
                )
            }
            val cardWidth = with(density) {
                GridFitter.cardWidth(maxWidth.toPx(), fit.columns, spacing = 0f).toDp()
            }

            Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
                var dealt = 0
                blocks.forEach { (section, ids) ->
                    val offset = dealt
                    ShowcaseBlock(
                        state = state,
                        section = section,
                        ids = ids,
                        columns = fit.columns,
                        cardWidth = cardWidth,
                        // Each card's place in the whole deck, not in its own
                        // block, so the reveal runs on unbroken across the seam.
                        progress = { index ->
                            DealAnimation.progressFor(offset + index, total, reveal.value)
                        },
                    )
                    dealt += ids.size
                }

                Caption(state, total)
            }
        }
    }
}

/**
 * The only control on the screen, and it keeps out of the picture.
 *
 * Faint on purpose. The showcase exists to have nothing on top of the deck, and
 * a button that asserted itself would undo that — but a picture nobody can find
 * how to save is not a feature. So it sits quiet in the corner and comes up when
 * a pointer finds it.
 */
@Composable
private fun SavePicture(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMasterToolColors.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val lit by animateFloatAsState(if (hovered || pressed) 1f else 0.4f)

    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, colors.accent.copy(alpha = 0.5f * lit), RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.3f * lit))
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            "SAVE A PICTURE",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = lit),
        )
    }
}

/** One line of cards, and whether a gap falls above it. */
private class ShowcaseRow(
    val start: Int,
    val ids: List<CardId>,
    val startsGroup: Boolean,
)

/**
 * Breaks a section into the rows it is drawn as.
 *
 * Each group is laid out on its own rows rather than flowing on from the last —
 * a gap that only moved cards along by one place would say nothing at this size,
 * and a group that starts where the eye starts is the whole point of having
 * separated it.
 */
private fun rowsOf(ids: List<CardId>, breaks: Breaks, columns: Int): List<ShowcaseRow> =
    buildList {
        breaks.groups(ids.size).forEachIndexed { group, range ->
            ids.slice(range).chunked(columns).forEachIndexed { line, chunk ->
                add(
                    ShowcaseRow(
                        start = range.first + line * columns,
                        ids = chunk,
                        // Not the very first group: nothing to stand clear of.
                        startsGroup = group > 0 && line == 0,
                    ),
                )
            }
        }
    }

@Composable
private fun ShowcaseBlock(
    state: DeckBuilderState,
    section: DeckSection,
    ids: List<CardId>,
    columns: Int,
    cardWidth: Dp,
    progress: (Int) -> Float,
) {
    val accent = section.accent()

    Column {
        // A hairline in the section's colour, the width of the cards above it.
        // Enough to say where the Main deck ends without putting a label on the
        // picture.
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .size(width = cardWidth * 1.5f, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent.copy(alpha = 0.55f)),
        )

        // Laid out by hand rather than with a lazy grid: everything is on screen
        // by construction here, so there is nothing to virtualise and a plain
        // Column of Rows keeps the reveal in one place.
        //
        // A group starts its own row and stands a little clear of the one above.
        // This is the view of a deck as a thing rather than as a list, so if the
        // piles have been pushed apart anywhere they should be pushed apart
        // here — it is the picture the arrangement was made for.
        rowsOf(ids, state.breaksIn(section), columns).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (row.startsGroup) GROUP_SPACE else 0.dp),
            ) {
                row.ids.forEachIndexed { column, id ->
                    val index = row.start + column
                    val card = state.index.byId(id)

                    Box(
                        Modifier
                            .width(cardWidth)
                            .graphicsLayer {
                                val dealt = progress(index)
                                alpha = dealt
                                translationY = (1f - dealt) * DealAnimation.RISE * size.height
                            },
                    ) {
                        if (card == null) {
                            // A card the pool has not downloaded, which is the
                            // ordinary state for a minute after a first run. It
                            // is drawn as an empty slot rather than a grey
                            // rectangle -- the same treatment an empty pane uses
                            // — so a deck opened too early reads as waiting for
                            // its cards rather than as broken.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(CARD_ASPECT_RATIO)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(CARD_CORNER))
                                    .background(Color.Black.copy(alpha = 0.22f)),
                            )
                        } else {
                            CardTile(card = card, format = state.format)
                        }
                    }
                }
            }
        }
    }
}

/** The only writing on the screen: whose deck this is and how big it is. */
@Composable
private fun Caption(state: DeckBuilderState, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.deckName.ifBlank { "Untitled deck" },
            // Deliberately not the display face. That belongs to the wordmark
            // alone; a second use of it stops it being a wordmark.
            style = MaterialTheme.typography.titleMedium,
        )
        Box(Modifier.weight(1f))
        // Says so when most of what is on screen is an empty slot, because
        // "your card database has not arrived yet" and "this program cannot
        // draw your deck" look identical otherwise.
        val missing = SHOWCASE_ORDER
            .flatMap { state.deck[it] }
            .count { state.index.byId(it) == null }

        Text(
            SHOWCASE_ORDER
                .filter { state.deck[it].isNotEmpty() }
                .joinToString("   ") { "${it.displayName.uppercase()} ${state.deck[it].size}" } +
                "   ·   $total" +
                if (missing > total / 2) "   ·   STILL DOWNLOADING" else "",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
