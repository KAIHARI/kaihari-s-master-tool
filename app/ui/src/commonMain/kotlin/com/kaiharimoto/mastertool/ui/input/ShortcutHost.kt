package com.kaiharimoto.mastertool.ui.input

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.kaiharimoto.mastertool.core.input.KeyChord
import com.kaiharimoto.mastertool.core.input.ShortcutAction
import com.kaiharimoto.mastertool.core.input.ShortcutContext
import com.kaiharimoto.mastertool.core.input.ShortcutTable

/**
 * The keyboard story of one surface, bundled so it can travel.
 *
 * A modal sheet composes into its own window or popup, outside the main
 * hierarchy's focus chain — key events over a sheet never reach the builder's
 * [ShortcutHost]. Passing this into each sheet lets the sheet stand up its own
 * host *inside* that focus boundary, resolving against the same table and
 * feeding the same handler, so a shortcut means the same thing wherever the
 * focus happens to be.
 */
class ShortcutRelay(
    val context: ShortcutContext,
    val onAction: (ShortcutAction) -> Unit,
)

/** Wraps a sheet's content so shortcuts keep working while it is open. */
@Composable
fun SheetShortcuts(relay: ShortcutRelay?, content: @Composable () -> Unit) {
    if (relay == null) {
        content()
    } else {
        ShortcutHost(
            context = relay.context,
            onAction = relay.onAction,
            modifier = Modifier,
            content = content,
        )
    }
}

/**
 * The one place a key press is turned into an action.
 *
 * `onPreviewKeyEvent` sees the event before the focused child does, which is
 * exactly what a global shortcut needs and exactly why it would otherwise eat
 * every letter typed into a text field. [ShortcutContext] is the guard: while a
 * field has focus only the handful of shortcuts marked as safe there get through,
 * and everything else falls to the field.
 *
 * Key repeat is resolved here rather than in the table's data: the platforms do
 * not agree on how a repeat is reported, so it is tracked as "a second key-down
 * with no key-up in between". A repeat of a repeatable shortcut fires again; a
 * repeat of anything else is swallowed whole, so holding `s` cannot strobe the
 * statistics panel open and shut at the repeat rate.
 *
 * All this composable does is name the key. Deciding what a chord means lives in
 * `:core`, where it is a pure function with tests.
 */
@Composable
fun ShortcutHost(
    context: ShortcutContext,
    onAction: (ShortcutAction) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    content: @Composable () -> Unit,
) {
    // Key events only arrive somewhere focused. Taking focus at the root means
    // shortcuts work before anything has been clicked.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val heldKeys = remember { mutableSetOf<Key>() }

    Box(
        modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyUp -> {
                        heldKeys.remove(event.key)
                        return@onPreviewKeyEvent false
                    }
                    KeyEventType.KeyDown -> Unit
                    else -> return@onPreviewKeyEvent false
                }

                val isRepeat = !heldKeys.add(event.key)

                val chord = event.toChord() ?: return@onPreviewKeyEvent false
                val shortcut = ShortcutTable.resolveShortcut(chord, context)
                    ?: return@onPreviewKeyEvent false

                if (!isRepeat || shortcut.repeatable) onAction(shortcut.action)
                // Consumed either way: a swallowed repeat must not fall through
                // to whatever is underneath.
                true
            },
    ) {
        content()
    }
}

private fun KeyEvent.toChord(): KeyChord? {
    val name = key.chordName() ?: return null
    return KeyChord(
        key = name,
        // Control and Command are never told apart by a shortcut, so they are
        // folded together here rather than in every binding.
        ctrl = isCtrlPressed || isMetaPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
    )
}

/**
 * Only the keys something is actually bound to.
 *
 * An explicit list rather than a general key-to-string mapping: the set is small,
 * and a mapping that silently names keys nobody uses is a mapping that can
 * disagree with the table it feeds.
 */
private fun Key.chordName(): String? = when (this) {
    Key.Escape -> "escape"
    Key.Slash -> "slash"
    Key.Spacebar -> "space"
    Key.DirectionLeft -> "left"
    Key.DirectionRight -> "right"
    Key.One -> "1"
    Key.Two -> "2"
    Key.Three -> "3"
    Key.Four -> "4"
    Key.B -> "b"
    Key.C -> "c"
    Key.D -> "d"
    Key.F -> "f"
    Key.G -> "g"
    Key.I -> "i"
    Key.N -> "n"
    Key.R -> "r"
    Key.S -> "s"
    Key.T -> "t"
    Key.Z -> "z"
    else -> null
}
