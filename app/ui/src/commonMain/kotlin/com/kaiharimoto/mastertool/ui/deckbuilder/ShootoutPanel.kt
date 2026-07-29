package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.deck.HandTally
import com.kaiharimoto.mastertool.core.deck.OpeningHand
import com.kaiharimoto.mastertool.core.deck.Preference
import com.kaiharimoto.mastertool.core.deck.ShootoutGame
import com.kaiharimoto.mastertool.core.deck.ShootoutReport
import com.kaiharimoto.mastertool.core.deck.ShootoutRun
import com.kaiharimoto.mastertool.core.deck.SideVerdict
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.ui.components.CardTile
import com.kaiharimoto.mastertool.ui.components.HoverPreview
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.ui.components.percent
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.tacticalStyle

/**
 * Two decks, two opening hands, at the same time.
 *
 * The test-hand panel answers "does my list open"; this answers the question
 * that actually decides a match, which is "does my list open *against theirs*".
 * Sitting them one above the other is most of the answer — you can see whether
 * your interruption is the one their board cares about before a single card is
 * played.
 *
 * The opponent's deck is loaded from a file and kept entirely separate from the
 * one being built. Nothing here can edit either list.
 *
 * Two ways of using it. Loose, you deal and judge until you have seen enough —
 * which is how anybody tests a list against a deck they just thought of. In a
 * run, the panel walks a fixed number of trials with game one off the registered
 * list and the rest off the sided one, and the report is the gap between those
 * two numbers: whether the side deck is doing any work at all.
 */
@Composable
fun ShootoutPanel(state: DeckBuilderState, onPlayItOut: (List<CardId>) -> Unit = {}) {
    MasterToolSheet(onDismiss = { state.shootoutVisible = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shootout", style = MaterialTheme.typography.headlineSmall)

                Box(Modifier.weight(1f))

                FilterChip(
                    selected = state.youGoFirst,
                    onClick = { state.dealShootout(goingFirst = true) },
                    label = { Text("You go 1st") },
                )
                Box(Modifier.width(8.dp))
                FilterChip(
                    selected = !state.youGoFirst,
                    onClick = { state.dealShootout(goingFirst = false) },
                    label = { Text("You go 2nd") },
                )
            }

            if (state.opponentDeck.main.isEmpty()) {
                Text(
                    "Load the deck you are testing against. A .ydk or .ydkx file — " +
                        "nothing here can change either list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { state.loadOpponent() }) { Text("Load opponent…") }
                return@Column
            }

            Side(
                state = state,
                title = state.deckName.ifBlank { "Your deck" },
                hand = state.yourOpening,
                onDraw = { state.drawOneMoreShootout(yours = true) },
            )
            Side(
                state = state,
                title = state.opponentName.ifBlank { "Opponent" },
                hand = state.theirOpening,
                onDraw = { state.drawOneMoreShootout(yours = false) },
            )

            val run = state.shootoutRun
            if (run != null) RunPrompt(state, run)

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same judgement the test-hand panel asks for, against a
                // real deck instead of an imagined one.
                Button(
                    onClick = { state.judgeShootout(playable = true) },
                    enabled = run?.finished != true,
                ) { Text("Playable") }
                OutlinedButton(
                    onClick = { state.judgeShootout(playable = false) },
                    enabled = run?.finished != true,
                ) { Text("Brick") }

                if (run == null) {
                    // Not offered inside a run. Dealing until the hand looks
                    // good and only then judging it is how a sample stops
                    // meaning anything, and a run is nothing but its sample.
                    TextButton(onClick = { state.dealShootout(state.youGoFirst) }) {
                        Text("Deal again")
                    }
                }
                if (state.canUndoVerdict) {
                    // Puts the count right after a misclick. It does not bring
                    // the hand back -- the next one has already been dealt --
                    // and the label says only what it does.
                    TextButton(onClick = { state.undoVerdict() }) { Text("Undo that verdict") }
                }
                if (run != null && run.played > 0) {
                    TextButton(onClick = { state.undoTrial() }) { Text("Undo this trial") }
                }
                state.yourOpening?.let { opening ->
                    // The hand in front of you, on a board. Half of judging an
                    // opening against a real deck is finding out whether it
                    // actually assembles into anything.
                    TextButton(
                        onClick = {
                            state.shootoutVisible = false
                            onPlayItOut(opening.cards)
                        },
                    ) { Text("Play it out") }
                }
                TextButton(onClick = { state.loadOpponent() }) { Text("Change opponent…") }

                Box(Modifier.weight(1f))

                if (run == null) {
                    TextButton(onClick = { state.resetMatchup() }) { Text("Reset") }
                } else {
                    TextButton(onClick = { state.endRun() }) { Text("End run") }
                }
            }

            if (run == null) {
                Record(state)
                StartRun(state)
            } else {
                RunSheet(run, run.report())
            }
        }
    }
}

/**
 * Offers a run, which is the other way of using this panel.
 *
 * Deliberately a choice rather than the only mode. Dealing and judging until
 * you have seen enough is how anybody tests a list against a deck they just
 * thought of, and making that go through a ten-trial ceremony would be worse
 * for the common case in order to be better for the careful one.
 */
@Composable
private fun StartRun(state: DeckBuilderState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Or run it properly:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(5, 10, 20).forEach { trials ->
            OutlinedButton(onClick = { state.startRun(trials) }) { Text("$trials trials") }
        }
        Text(
            "Game one off the list you registered, the rest off the sided one.",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Where the run is, and what it wants next.
 *
 * The one line that has to be right: after game one it asks for the sided deck,
 * and says whether it has one. Nothing forces the swap — the report records what
 * actually dealt the hand — so this is the only place a player finds out they
 * are about to file a pre-side hand as post-side.
 */
@Composable
private fun RunPrompt(state: DeckBuilderState, run: ShootoutRun) {
    if (run.finished) {
        Text(
            "That is the run. The last hand is still on the table.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "TRIAL ${run.nextTrial} OF ${run.trials}   ·   GAME ${run.nextGame}",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.weight(1f))

        when {
            !run.nextIsSided && state.deckIsSided -> Text(
                "game one comes off the registered list",
                style = tacticalStyle(),
                color = MasterToolPalette.Warning,
            )
            run.nextIsSided && !state.deckIsSided -> {
                Text(
                    "side for this matchup",
                    style = tacticalStyle(),
                    color = MasterToolPalette.Warning,
                )
                TextButton(onClick = { state.sidingVisible = true }) { Text("Siding…") }
            }
            run.nextIsSided -> Text(
                "sided",
                style = tacticalStyle(),
                color = MasterToolPalette.Success,
            )
            else -> Unit
        }
    }

    if (run.nextIsSided) TheySideToo(state)
}

/**
 * What the deck across the table does between games.
 *
 * Only offered when their own file carries the answer. A `.ydkx` written by the
 * desktop tool keeps that deck's siding plans in it, so a list downloaded from
 * somebody who plays the deck often arrives with what they actually side — which
 * is a far better answer than a guess, and it is already in the file.
 *
 * When the file says nothing, this says nothing. Inventing their fifteen would
 * be putting a made-up board across the table and then drawing conclusions from
 * how your hand fared against it.
 */
@Composable
private fun TheySideToo(state: DeckBuilderState) {
    if (state.opponentPlans.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (state.opponentSided) "THEY SIDED" else "THEY SIDE",
            style = tacticalStyle(),
            color = if (state.opponentSided) {
                MasterToolPalette.Success
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (state.opponentSided) {
            TextButton(onClick = { state.unsideOpponent() }) { Text("Put their list back") }
            return@Row
        }

        // Says so when there are more. A downloaded list can carry eight
        // matchups; showing four and nothing else made the other four look like
        // they did not exist, which is a worse lie than a crowded row.
        val hidden = (state.opponentPlans.size - MAX_OPPONENT_PLANS).coerceAtLeast(0)

        state.opponentPlans.values.take(MAX_OPPONENT_PLANS).forEach { plan ->
            TextButton(
                onClick = {
                    // Against you, so the half of their plan that matters is the
                    // one for the side of the die roll they are on -- which is
                    // the opposite of yours.
                    state.sideOpponent(plan, goingFirst = !state.youGoFirst)
                },
            ) {
                Text(plan.deckName.ifBlank { "their plan" })
            }
        }

        if (hidden > 0) {
            Text(
                "+$hidden more in their file",
                style = tacticalStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Enough to choose from without the row becoming a menu. */
private const val MAX_OPPONENT_PLANS = 4

/**
 * The run as a score sheet.
 *
 * One mark per opening, game one along the top and everything post-side beneath
 * it, so the two rows *are* the two numbers underneath — a glance down the sheet
 * says whether the bottom band is greener than the top, which is the entire
 * question a side deck exists to answer.
 *
 * Position carries the pre/post distinction rather than a shape or a colour,
 * because it is the one encoding that survives being glanced at. An earlier
 * version notched the corner of post-side marks and it was invisible at size.
 */
@Composable
private fun RunSheet(run: ShootoutRun, report: ShootoutReport) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                RowLabel("GAME 1")
                repeat(run.gamesPerTrial - 1) { RowLabel("SIDED") }
                // Keeps the labels level with the marks, past the trial numbers.
                Box(Modifier.height(NUMBER_HEIGHT))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..run.trials).forEach { trial ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        (1..run.gamesPerTrial).forEach { game ->
                            val index = (trial - 1) * run.gamesPerTrial + (game - 1)
                            GameMark(
                                game = run.games.getOrNull(index),
                                isNext = index == run.played && !run.finished,
                            )
                        }
                        Box(Modifier.height(NUMBER_HEIGHT)) {
                            Text(
                                trial.toString(),
                                style = tacticalStyle().copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = LocalMasterToolColors.current.mat.weft)

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BigRate("GAME ONE", report.preSide.brickRate, report.preSide.total)
            Text(
                "→",
                style = tacticalStyle().copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            BigRate("AFTER SIDING", report.postSide.brickRate, report.postSide.total)

            Box(Modifier.weight(1f))

            Verdict(report)
        }
    }
}

/**
 * One opening: filled if it played, struck through if it bricked.
 *
 * Dashed for one not yet dealt, so an unfinished run reads as unfinished rather
 * than as a run in which nothing happened.
 */
@Composable
private fun GameMark(game: ShootoutGame?, isNext: Boolean) {
    val pendingLine = LocalMasterToolColors.current.mat.weft
    val next = LocalMasterToolColors.current.accentBright

    Box(
        Modifier.size(MARK_SIZE).drawBehind {
            val corner = CornerRadius(2.dp.toPx())
            val stroke = 1.5.dp.toPx()
            val inset = stroke / 2f
            val box = Size(size.width - stroke, size.height - stroke)

            when {
                game == null -> drawRoundRect(
                    color = if (isNext) next else pendingLine,
                    topLeft = Offset(inset, inset),
                    size = box,
                    cornerRadius = corner,
                    style = Stroke(
                        width = stroke,
                        pathEffect = if (isNext) null else PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                        ),
                    ),
                )

                game.playable -> drawRoundRect(
                    color = MasterToolPalette.Success,
                    topLeft = Offset(inset, inset),
                    size = box,
                    cornerRadius = corner,
                )

                else -> {
                    drawRoundRect(
                        color = MasterToolPalette.Danger,
                        topLeft = Offset(inset, inset),
                        size = box,
                        cornerRadius = corner,
                        style = Stroke(width = stroke),
                    )
                    val pad = size.width * 0.28f
                    drawLine(
                        color = MasterToolPalette.Danger,
                        start = Offset(pad, size.height - pad),
                        end = Offset(size.width - pad, pad),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
}

@Composable
private fun RowLabel(text: String) {
    Box(Modifier.height(MARK_SIZE), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A brick rate, sized to be read from across the desk. */
@Composable
private fun BigRate(label: String, rate: Double?, total: Int) {
    Column {
        Text(label, style = tacticalStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (rate == null) "—" else percent(rate),
            style = tacticalStyle().copy(
                fontSize = 30.sp,
                letterSpacing = (-1.5).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = when {
                rate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                rate >= 0.25 -> MasterToolPalette.Danger
                rate >= 0.15 -> MasterToolPalette.Warning
                else -> MasterToolPalette.Success
            },
        )
        Text(
            "BRICKS IN $total",
            style = tacticalStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the run came to, or nothing at all.
 *
 * "No difference" is printed as loudly as the other two, because a side deck
 * that measurably changes nothing against this matchup is fifteen cards doing no
 * work — a finding, and usually the one worth acting on.
 */
@Composable
private fun Verdict(report: ShootoutReport) {
    val verdict = report.sideVerdict() ?: return
    val (text, colour) = when (verdict) {
        SideVerdict.HELPS -> "the side deck is working" to MasterToolPalette.Success
        SideVerdict.HURTS -> "siding is making this worse" to MasterToolPalette.Danger
        SideVerdict.NO_DIFFERENCE ->
            "siding changes nothing here" to MasterToolPalette.Warning
    }

    Box(
        Modifier
            .drawBehind {
                drawRoundRect(
                    color = colour,
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text.uppercase(), style = tacticalStyle().copy(letterSpacing = 0.5.sp), color = colour)
    }
}

private val MARK_SIZE = 20.dp
private val ROW_GAP = 6.dp
private val NUMBER_HEIGHT = 14.dp

/**
 * The record so far, and what it says to do about the die roll.
 *
 * Two rates, kept apart, because the decision a player makes at the start of a
 * match is *whether to go first* — and against some decks the answer is the
 * opposite of the usual one. Saying which is better is the whole point; leaving
 * two percentages side by side to be compared by eye is doing three-quarters of
 * the work and stopping.
 */
@Composable
private fun Record(state: DeckBuilderState) {
    val matchup = state.matchup
    if (matchup.total == 0) {
        Text(
            "Judge each opening and the record builds, kept apart for going first and second.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rate("GOING 1ST", matchup.goingFirst)
        Rate("GOING 2ND", matchup.goingSecond)

        Box(Modifier.weight(1f))

        val advice = when (matchup.preference()) {
            Preference.FIRST -> "go first against this"
            Preference.SECOND -> "go second against this"
            // Quiet until there is something to say. Five hands a side is not
            // statistics, it is the point past which somebody would have formed
            // an opinion anyway.
            null -> null
        }
        if (advice != null) {
            Text(advice, style = tacticalStyle(), color = MasterToolPalette.Success)
        }
    }
}

@Composable
private fun Rate(label: String, tally: HandTally) {
    val rate = tally.brickRate

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = tacticalStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.width(8.dp))
        Text(
            if (rate == null) "—" else "${percent(rate)} bricks in ${tally.total}",
            style = tacticalStyle(),
            color = when {
                rate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                rate >= 0.25 -> MasterToolPalette.Danger
                rate >= 0.15 -> MasterToolPalette.Warning
                else -> MasterToolPalette.Success
            },
        )
    }
}

/** One player's name and opening, which is the same shape on both sides. */
@Composable
private fun Side(
    state: DeckBuilderState,
    title: String,
    hand: OpeningHand?,
    onDraw: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Box(Modifier.width(10.dp))
            Text(
                if (hand == null) "" else if (hand.goingFirst) "GOING 1ST" else "GOING 2ND",
                style = tacticalStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f))
            // Both sides, because what their turn-two draw does to your board
            // is half of what a shootout is for.
            if (hand != null && hand.remaining.isNotEmpty()) {
                TextButton(onClick = onDraw) { Text("Draw one") }
            }
        }

        if (hand == null || hand.size == 0) {
            Text(
                "Nothing to shuffle — that Main deck is empty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hand.cards.forEach { id ->
                val card = state.index.byId(id)
                Box(Modifier.width(88.dp)) {
                    if (card == null) {
                        Text(id.value.toString(), style = tacticalStyle())
                    } else {
                        HoverPreview(card) {
                            // Inspects. Nothing in this panel edits a deck —
                            // least of all the opponent's.
                            CardTile(
                                card = card,
                                format = state.format,
                                onClick = { state.inspect(listOf(card), 0) },
                            )
                        }
                    }
                }
            }
        }
    }
}
