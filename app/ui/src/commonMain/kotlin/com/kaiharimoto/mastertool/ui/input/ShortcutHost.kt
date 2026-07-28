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
 * The one place a key press is turned into an action.
 *
 * `onPreviewKeyEvent` sees the event before the focused child does, which is
 * exactly what a global shortcut needs and exactly why it would otherwise eat
 * every letter typed into a text field. [ShortcutContext] is the guard: while a
 * field has focus only the handful of shortcuts marked as safe there get through,
 * and everything else falls to the field.
 *
 * All this composable does is name the key. Deciding what a chord means lives in
 * `:core`, where it is a pure function with tests.
 */
@Composable
fun ShortcutHost(
    context: ShortcutContext,
    onAction: (ShortcutAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    // Key events only arrive somewhere focused. Taking focus at the root means
    // shortcuts work before anything has been clicked.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val chord = event.toChord() ?: return@onPreviewKeyEvent false
                val action = ShortcutTable.resolve(chord, context)
                    ?: return@onPreviewKeyEvent false
                onAction(action)
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
    Key.DirectionLeft -> "left"
    Key.DirectionRight -> "right"
    Key.One -> "1"
    Key.Two -> "2"
    Key.Three -> "3"
    Key.F -> "f"
    Key.I -> "i"
    Key.S -> "s"
    Key.Z -> "z"
    else -> null
}
