package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.hand.Ask
import com.kaiharimoto.mastertool.core.hand.GoalOdds
import com.kaiharimoto.mastertool.core.hand.HandGoal
import com.kaiharimoto.mastertool.ui.components.percent
import com.kaiharimoto.mastertool.ui.input.SheetShortcuts
import com.kaiharimoto.mastertool.ui.input.ShortcutRelay
import com.kaiharimoto.mastertool.ui.theme.ChromaticText
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import com.kaiharimoto.mastertool.ui.theme.archivoExpandedFamily

/**
 * One question about the opening hand, written down and kept.
 *
 * This used to be a calculator: you described your deck to it, read a number,
 * closed it, and the description was gone. Which meant the next time you wanted
 * to know whether cutting a card had cost you anything, you rebuilt the whole
 * thing from memory — and a question you have to rebuild is one you stop
 * asking. So the sheet now writes into the deck. What you state here becomes a
 * chip on the lens bar with a live number on it, and the answer is simply true
 * on screen while you work instead of being something you go and fetch.
 *
 * The asks are joined by AND, which is what makes this a question rather than a
 * list of facts — "a starter *and* no more than one dead card" is the shape of
 * every real one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSheet(
    state: DeckBuilderState,
    shortcuts: ShortcutRelay? = null,
) {
    val goal = state.editingGoal ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = state::cancelGoal, sheetState = sheetState) {
        SheetShortcuts(shortcuts) {
            val deck = state.deck.main
            val groups = state.groups.ordered()
            val loose = deck.count { state.groups.groupOf(it) == null }
            val odds = GoalOdds.probability(goal, deck, state.groups)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = goal.name,
                        onValueChange = state::setGoalName,
                        singleLine = true,
                        label = { Text("What are you asking?") },
                        placeholder = { Text("Opens") },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = goal.handSize == 5,
                            onClick = { state.setGoalHandSize(5) },
                            label = { Text("Going first · 5") },
                        )
                        FilterChip(
                            selected = goal.handSize == 6,
                            onClick = { state.setGoalHandSize(6) },
                            label = { Text("Going second · 6") },
                        )
                    }
                }

                if (groups.isEmpty()) {
                    Text(
                        "Draw some roles first — engine, handtraps, bricks — and this " +
                            "turns them into odds: the chance an opener has at least one " +
                            "starter and at least one handtrap, or dodges every brick.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                groups.forEach { group ->
                    AskRow(
                        name = group.name,
                        colorIndex = group.color,
                        copies = state.groups.countIn(deck, group.id),
                        ask = goal.ask(group.id),
                        onAsk = { state.setGoalAsk(group.id, it) },
                    )
                }

                // The half that cannot be said in terms of roles you drew,
                // because the point of it is the cards you drew none for.
                if (loose > 0 && groups.isNotEmpty()) {
                    AskRow(
                        name = "Unlabelled",
                        colorIndex = null,
                        copies = loose,
                        ask = goal.ask(HandGoal.UNGROUPED),
                        onAsk = { state.setGoalAsk(HandGoal.UNGROUPED, it) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        // The answer, set like it matters.
                        ChromaticText(
                            if (goal.isEmpty) "—" else percent(odds),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = archivoExpandedFamily(),
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (goal.isEmpty) {
                                "no asks yet, so every opener qualifies"
                            } else {
                                "of ${goal.handSize}-card openers from these " +
                                    "${deck.size} cards qualify"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.goals.byId(goal.id) != null) {
                        TextButton(onClick = state::deleteGoal) {
                            Text("Delete", color = MasterToolPalette.Danger)
                        }
                    }
                    TextButton(onClick = state::clearGoalAsks) { Text("Clear asks") }
                    TextButton(onClick = state::cancelGoal) { Text("Cancel") }
                    Button(onClick = state::saveGoal, enabled = !goal.isEmpty) { Text("Keep") }
                }
            }
        }
    }
}

@Composable
private fun AskRow(
    name: String,
    colorIndex: Int?,
    copies: Int,
    ask: Ask,
    onAsk: (Ask) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    colorIndex?.let { MasterToolPalette.Prism[it % MasterToolPalette.Prism.size] }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )

        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))

        Text(
            "$copies ${if (copies == 1) "copy" else "copies"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Ask.entries.forEach { option ->
            FilterChip(
                selected = ask == option,
                onClick = { onAsk(option) },
                label = { Text(option.label) },
            )
        }
    }
}
