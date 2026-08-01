package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.kaiharimoto.mastertool.core.deck.GroupMarks
import com.kaiharimoto.mastertool.core.deck.GroupPresets
import com.kaiharimoto.mastertool.core.deck.KeyPaint
import com.kaiharimoto.mastertool.core.deck.KeyTone
import com.kaiharimoto.mastertool.core.deck.Lens
import com.kaiharimoto.mastertool.core.deck.LensKey
import com.kaiharimoto.mastertool.core.deck.LensKeying
import com.kaiharimoto.mastertool.core.hand.LensOdds
import com.kaiharimoto.mastertool.ui.components.percentShort
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * How tall the lens bar is — one row, fixed, always there.
 *
 * Fixed and unconditional on purpose. A bar that appears when a mode opens is
 * height the cards were promised and then had taken away, and the deck resizes
 * under your hands the moment you start reading it. Thirty dp bought once, up
 * front, is the price of the card size never moving again.
 */
val LENS_BAR_HEIGHT = 30.dp

/**
 * How wide the lens picker is, whatever it currently says.
 *
 * Fixed rather than wrapped: the keys sit immediately to its right, and a
 * control that grew from "Deck" to "Archetype" would shove the whole reading
 * sideways every time you changed how you were reading it.
 */
private val LENS_PICKER_WIDTH = 96.dp

/**
 * The strip under the main deck's header: how the deck is being read, and what
 * that reading found.
 *
 * The lenses are the whole idea of the mode. Roles is the deck as you labelled
 * it; Archetype, Type, Copies and Legality are the deck as it already is,
 * needing no labelling at all — so the tool has something to say about a list
 * you imported ten seconds ago, which is when a reading is worth most. Every
 * one of them draws the same grid in the same order; only the colour and the
 * cracks change.
 *
 * Each key then reports what it is worth in the opener. That number is why the
 * partition is worth drawing: a ratio is only ever being chosen to move it.
 *
 * It sits inside the pane rather than over it because every one of these
 * gestures means looking at the deck while you do it — a modal sheet would
 * cover the one thing they are about.
 */
@Composable
fun LensBar(state: DeckBuilderState, keying: LensKeying, modifier: Modifier = Modifier) {
    if (state.groupDraft != null) {
        DraftRow(state, modifier)
    } else {
        LensRow(state, keying, modifier)
    }
}

@Composable
private fun LensRow(state: DeckBuilderState, keying: LensKeying, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LensPicker(state)

        Box(
            Modifier
                .width(1.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.outline),
        )

        // The reading and what it is worth, computed once per partition rather
        // than per frame. Exact hypergeometric over the same counts the blocks
        // are drawn from, so the bar cannot disagree with the deck under it.
        val odds = remember(keying) {
            LensOdds.atLeastOne(keying, deckSize = keying.keyOfCell.size)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            keying.keys.forEach { key ->
                KeyChip(
                    key = key,
                    count = keying.countOf(key.id),
                    opens = odds[key.id],
                    isolated = state.isolatedKey == key.id,
                    dimmed = state.isolatedKey != null && state.isolatedKey != key.id,
                    onTap = { state.toggleIsolation(key.id) },
                    onHold = {
                        if (keying.lens.isEditable) {
                            state.groups.byId(key.id)?.let(state::editGroup)
                        }
                    },
                )
            }

            if (keying.lens == Lens.ROLES) NewGroupButton(state)

            Hint(state, keying)
        }

        GoalStrip(state)
    }
}

/**
 * What the deck is being asked, and whether it answers.
 *
 * Pinned to the right of the bar rather than scrolling with the keys, and
 * present under every lens, because a goal is a fact about the deck and not
 * about how you happen to be looking at it right now. Left is how you are
 * reading; middle is what that reading found; right is whether the deck does
 * what you asked of it.
 */
@Composable
private fun GoalStrip(state: DeckBuilderState) {
    val goals = state.goals.goals

    // Recomputed when the deck, the roles or the questions change — not per
    // frame, and not per keystroke in the search box.
    val answers = remember(goals, state.deck.main, state.groups) {
        goals.associate { it.id to state.oddsOf(it) }
    }

    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outline),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        goals.forEach { goal ->
            GoalChip(
                name = goal.name.ifBlank { "Opens" },
                odds = answers[goal.id],
                onClick = { state.openGoal(goal.id) },
            )
        }

        TextButton(
            onClick = state::newGoal,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(22.dp),
        ) {
            Text(
                if (goals.isEmpty()) "+ Ask something" else "+",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun GoalChip(name: String, odds: Double?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MasterToolPalette.SurfaceHigh)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (odds != null) {
            Text(
                percentShort(odds),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Which reading is on. One position is always taken.
 *
 * Not a toggle: "off" is a lens too, and making it one of the list means the
 * mode is never something you have to remember to turn back off.
 *
 * A menu rather than a row of buttons, for two reasons that pull the same way.
 * Six readings will not fit across a tablet beside the keys they produce, and
 * the keys are the part worth the width. And the control is a fixed size
 * whatever it says, so choosing a longer-named lens does not shove the whole
 * bar sideways under your finger.
 */
@Composable
private fun LensPicker(state: DeckBuilderState) {
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .width(LENS_PICKER_WIDTH)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable { open = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.lens.displayName,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Change lens",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Lens.entries.forEach { lens ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (lens == state.lens) "✓ ${lens.displayName}" else lens.displayName,
                        )
                    },
                    onClick = {
                        open = false
                        state.useLens(lens)
                    },
                )
            }
        }
    }
}

/**
 * One key: its mark, its name, how much of the deck it is, and how often you
 * open it.
 *
 * The last of those is the reason the rest is worth drawing. "Twelve starters"
 * is a fact about a list; "twelve starters, 89%" is an argument about a deck,
 * and it is the number the ratio was always being chosen to move. It sits on
 * the chip rather than behind a sheet because the decision it informs — cut
 * one, run one more — is made while looking at the cards.
 *
 * Tapping covers everything else, which is the read gesture — one tap to "just
 * the handtraps, where are they", one more to put the deck back. Holding one of
 * your own roles opens it for editing, because that is the write gesture and it
 * belongs behind the slower press.
 */
@Composable
private fun KeyChip(
    key: LensKey,
    count: Int,
    opens: Double?,
    isolated: Boolean,
    dimmed: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val color = key.paint.color()

    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (isolated) MasterToolPalette.SurfaceHigh else Color.Transparent)
            .then(
                if (isolated) Modifier.border(1.dp, color, RoundedCornerShape(2.dp)) else Modifier,
            )
            .pointerInput(key.id) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() })
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MarkChip(key.mark, color)
        Text(
            key.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.4f else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.4f else 1f),
        )

        if (opens != null) {
            Text(
                percentShort(opens),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                // Brighter than the count, because it is the number the
                // decision turns on and the count is only how it got there.
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.4f else 1f),
                maxLines = 1,
            )
        }
    }
}

/**
 * What the lens has left over, said plainly.
 *
 * The remainder is the reading in every lens: cards in no archetype are the
 * non-engine, cards in no role are the part you have not thought about yet.
 */
@Composable
private fun Hint(state: DeckBuilderState, keying: LensKeying) {
    val text = when {
        keying.lens == Lens.DECK -> "Read the deck by role, archetype, type, copies or legality"
        keying.keys.isEmpty() && keying.lens == Lens.ROLES ->
            "Name a role, then tap the cards that play it"
        keying.keys.isEmpty() -> "Nothing to read here"
        state.isolatedKey != null -> "Tap again to show the rest"
        else -> buildString {
            if (keying.unclaimed > 0) append("${keying.unclaimed} left over  ·  ")
            // Said once, at the end, rather than on every chip: the percentage
            // is meaningless without the hand it is drawn from, and repeating
            // "in 5" nine times across the bar would be noise.
            append("% opens in a 5-card hand")
        }
    }

    if (text.isEmpty()) return

    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * The eight words decklists are actually argued about in, one tap each.
 *
 * A menu rather than a row of chips because the bar has one line and the deck
 * has to keep the rest of the screen: naming a group is a moment, and the deck
 * behind it does not need to shrink for it.
 */
@Composable
private fun NewGroupButton(state: DeckBuilderState) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(22.dp),
        ) {
            Text("+ New role", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GroupPresets.all.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    leadingIcon = {
                        MarkChip(
                            GroupMarks.mark(preset.name),
                            MasterToolPalette.Prism[preset.color % MasterToolPalette.Prism.size],
                        )
                    },
                    onClick = {
                        open = false
                        state.startGroupDraft(preset = preset)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Something else…") },
                onClick = {
                    open = false
                    state.startGroupDraft()
                },
            )
        }
    }
}

/**
 * A group being drawn up: name it, then tap the cards.
 *
 * One line, the same line the legend was on, because the deck must not resize
 * when this opens — the cards you are about to tap have to stay exactly where
 * your eye left them.
 */
@Composable
private fun DraftRow(state: DeckBuilderState, modifier: Modifier) {
    val draft = state.groupDraft ?: return
    val color = MasterToolPalette.Prism[draft.color % MasterToolPalette.Prism.size]

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkChip(GroupMarks.mark(draft.name.ifBlank { "New group" }), color)

        BasicTextField(
            value = draft.name,
            onValueChange = state::setDraftName,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            ),
            cursorBrush = SolidColor(color),
            modifier = Modifier
                .width(150.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MasterToolPalette.SurfaceHigh)
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .onFocusChanged { state.onTextFieldFocusChanged(it.isFocused) },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.name.isEmpty()) {
                        Text(
                            "Name this role",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )

        MasterToolPalette.Prism.forEachIndexed { index, swatch ->
            Swatch(swatch, selected = index == draft.color) { state.setDraftColor(index) }
        }

        Text(
            // The count is the feedback for the tapping: nothing else on screen
            // says how many cards are in the group being made.
            "${draft.selection.size} selected · tap cards in the deck",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
        )

        if (!draft.isNew) {
            BarButton("Delete", MasterToolPalette.Danger) { state.deleteDraftGroup() }
        }
        BarButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant) { state.cancelGroupDraft() }
        BarButton("Save", MasterToolPalette.Ink, background = color) { state.saveGroupDraft() }
    }
}

@Composable
private fun BarButton(
    label: String,
    color: Color,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = color,
            maxLines = 1,
        )
    }
}

/** A key's two-letter mark, drawn exactly as it appears on the deck. */
@Composable
internal fun MarkChip(mark: String, color: Color) {
    Box(
        Modifier
            .size(width = 19.dp, height = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mark,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
            ),
            color = MasterToolPalette.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 18.dp else 13.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

/**
 * The colour a key is drawn in.
 *
 * A hue is nominal — these are different, not more or less — and comes off the
 * prismatic ramp. A grey is ordinal and reads as a scale, which is what a copy
 * count is: brightest is rarest, because the singleton is the card you are
 * looking for. A tone is a fact the app coloured before any lens existed, and
 * takes exactly the colour it already had — the type split in the statistics
 * panel, the ban badge in the corner of the card.
 */
internal fun KeyPaint.color(): Color = when (this) {
    is KeyPaint.Hue -> MasterToolPalette.Prism[prismIndex % MasterToolPalette.Prism.size]
    is KeyPaint.Grey -> Color(luminance, luminance, luminance)
    is KeyPaint.Tone -> when (tone) {
        KeyTone.MONSTER -> MasterToolPalette.Monster
        KeyTone.SPELL -> MasterToolPalette.Spell
        KeyTone.TRAP -> MasterToolPalette.Trap
        KeyTone.FORBIDDEN -> MasterToolPalette.Danger
        KeyTone.LIMITED -> MasterToolPalette.Warning
        KeyTone.SEMI_LIMITED -> MasterToolPalette.MainAccent
    }
}
