package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaiharimoto.mastertool.core.input.Gesture
import com.kaiharimoto.mastertool.core.input.GestureGroup
import com.kaiharimoto.mastertool.core.input.MatGuide
import com.kaiharimoto.mastertool.core.input.ShortcutScope
import com.kaiharimoto.mastertool.core.input.ShortcutTable
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette

/**
 * The house rules, on a table that has none printed on it.
 *
 * The play stage is deliberately affordance-free — no handles on the cards, no
 * hint text, nothing at all saying what a second finger might mean — because
 * that is what a table is, and because every affordance drawn on the felt is a
 * thing between you and the cards. The price of that stance is that it has to be
 * *payable*: somewhere, on a press, the table has to tell you what it knows.
 * This is that somewhere, and the reason it is a screen rather than a tooltip is
 * that the gestures make sense as a set — fingers on a card move the card,
 * fingers on the felt move the camera — and a set is not learnable a tooltip at
 * a time.
 *
 * Every line of it is read off [MatGuide] and [ShortcutTable]. Nothing is
 * written out here, so this cannot describe a gesture that does not exist or
 * miss one that does, which is the failure mode of every hand-kept control list
 * ever shipped.
 *
 * It is a plain overlay rather than a `ModalBottomSheet` on purpose. The mat
 * below it owns one `pointerInput` covering the whole stage and reads raw
 * pointer events; a sheet that animates in over that leaves the arbiter holding
 * a gesture it will never see the end of. A scrim that swallows everything is
 * both simpler and the thing that actually protects the mat.
 */
@Composable
internal fun PlayGuide(onDismiss: () -> Unit) {
    // No ripple and no indication: this is a scrim, and a scrim that flashes
    // when you dismiss it draws attention to itself on the way out.
    val press = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MasterToolPalette.Ink.copy(alpha = 0.88f))
            .clickable(interactionSource = press, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 820.dp)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("How the table is played", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap anywhere to close",
                    style = MaterialTheme.typography.labelMedium,
                    color = MasterToolPalette.TextMuted,
                )
            }

            Text(
                "Fingers on a card move the card. Fingers on the mat move you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MasterToolPalette.Accent,
            )

            // Every group, by walking them. Naming the ones this file happens to
            // know about is how a whole group goes missing the day one is added.
            GestureGroup.entries.forEach { group ->
                val gestures = MatGuide.of(group)
                if (gestures.isEmpty()) return@forEach

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Heading(group.heading)
                    gestures.forEach { GestureRow(it) }
                }
            }

            val keys = ShortcutTable.all.filter { it.scope == ShortcutScope.PLAY }
            if (keys.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Heading("With a keyboard")
                    keys.forEach { shortcut ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Chip(shortcut.chord.label, Modifier.weight(0.9f))
                            Text(
                                shortcut.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(3.1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MasterToolPalette.Accent)
}

/**
 * One gesture, in every language it can be made in.
 *
 * Both idioms on one line rather than two lists, and that is the point being
 * made as much as it is a layout: the standing rule is that a gesture ships with
 * a touch idiom *and* a pointer one, and a reader who can see the two side by
 * side can see a missing one.
 */
@Composable
private fun GestureRow(gesture: Gesture) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            gesture.what,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(2.2f),
        )
        Chip(gesture.touch, Modifier.weight(1.4f))
        Chip(gesture.pointer, Modifier.weight(1.4f))
        Box(Modifier.weight(0.6f)) {
            gesture.key?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MasterToolPalette.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MasterToolPalette.SurfaceRaised)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MasterToolPalette.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
