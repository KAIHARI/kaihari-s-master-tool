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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.components.MasterToolSheet
import com.kaiharimoto.mastertool.core.deck.DeckIssue
import com.kaiharimoto.mastertool.core.deck.DeckValidation
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * Everything standing between this decklist and a clean deck check.
 *
 * The top bar could already say "3 issue(s)" but not what they were. The point
 * of listing them here is less the text than the jump: every issue already
 * carries the section and passcode it came from, so each one can put the
 * offending card on screen instead of leaving you to hunt for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesPanel(
    validation: DeckValidation,
    onReveal: (DeckIssue) -> Unit,
    onDismiss: () -> Unit,
) {
    MasterToolSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Deck check", style = MaterialTheme.typography.headlineSmall)

            if (validation.issues.isEmpty()) {
                Text(
                    "No issues. This deck is tournament legal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MasterToolPalette.SideAccent,
                )
                return@Column
            }

            val errors = validation.errors
            val warnings = validation.warnings

            if (errors.isNotEmpty()) {
                Text(
                    "Not legal — ${errors.size} to fix",
                    style = MaterialTheme.typography.labelLarge,
                    color = MasterToolPalette.Danger,
                )
                errors.forEach { IssueRow(it, MasterToolPalette.Danger, onReveal) }
            }

            if (warnings.isNotEmpty()) {
                Text(
                    "Worth a look — ${warnings.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MasterToolPalette.Warning,
                )
                warnings.forEach { IssueRow(it, MasterToolPalette.Warning, onReveal) }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: DeckIssue, accent: Color, onReveal: (DeckIssue) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(accent))

        Text(
            issue.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        // A size or minimum-count issue has no single card to blame, so there is
        // nothing to jump to and the button would be a dead end.
        if (issue.cardId != null) {
            TextButton(onClick = { onReveal(issue) }) { Text("Show") }
        }
    }
}
