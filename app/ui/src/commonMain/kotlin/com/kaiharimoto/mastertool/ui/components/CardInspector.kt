package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.search.CardFilter
import kotlinx.coroutines.launch

/**
 * The card inspector, opened onto a list.
 *
 * Swipe on a tablet, arrows on a desktop: either way you can walk the whole
 * result set without closing and reopening, which is what comparing two
 * candidates for the same slot actually requires.
 *
 * Pages are not pre-composed — the neighbouring pages would each fetch a
 * full-resolution card image, and that is the one thing in this screen large
 * enough to be worth not doing speculatively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInspector(
    cards: List<Card>,
    initialIndex: Int,
    format: Format,
    copiesBySection: (Card) -> Map<DeckSection, Int>,
    mainDeckSize: Int,
    openingHandOdds: (copies: Int, handSize: Int) -> Double,
    onDismiss: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onSetCount: (Card, DeckSection, Int) -> Unit,
    onMove: (Card, DeckSection, DeckSection) -> Unit,
    onBrowse: (CardFilter) -> Unit,
) {
    if (cards.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, cards.lastIndex),
        pageCount = { cards.size },
    )
    val scope = rememberCoroutineScope()

    // Two-way, and settled by both sides checking before acting: the arrow keys
    // move the stored page and the pager follows, a swipe moves the pager and the
    // stored page follows, and neither can start the other off again.
    LaunchedEffect(initialIndex) {
        if (pagerState.currentPage != initialIndex) pagerState.animateScrollToPage(initialIndex)
    }
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    MasterToolSheet(onDismiss = onDismiss) {
        if (cards.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    enabled = pagerState.currentPage > 0,
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous card")
                }

                Text(
                    "${pagerState.currentPage + 1} of ${cards.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(Modifier.weight(1f))

                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    enabled = pagerState.currentPage < cards.lastIndex,
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next card")
                }
            }
        }

        HorizontalPager(state = pagerState) { page ->
            val card = cards[page]
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardDetailBody(
                    card = card,
                    format = format,
                    copiesBySection = copiesBySection(card),
                    mainDeckSize = mainDeckSize,
                    openingHandOdds = openingHandOdds,
                    onSetCount = { section, count -> onSetCount(card, section, count) },
                    onMove = { from, to -> onMove(card, from, to) },
                    onBrowse = onBrowse,
                )
            }
        }
    }
}
