package com.kaiharimoto.mastertool.core.input

/**
 * A key plus the modifiers held with it.
 *
 * [ctrl] means the platform's primary modifier — Control on Windows and Linux,
 * Command on a Mac. The two are never distinguished by a shortcut, so the UI
 * folds them together on the way in and nothing downstream has to care.
 */
data class KeyChord(
    val key: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    /** How the chord reads in the help sheet. */
    val label: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
            append(
                when (key) {
                    "escape" -> "Esc"
                    "slash" -> "/"
                    "left" -> "←"
                    "right" -> "→"
                    else -> key.uppercase()
                }
            )
        }
}

enum class ShortcutAction {
    SAVE,
    UNDO,
    REDO,
    FOCUS_SEARCH,
    TOGGLE_FILTERS,
    TOGGLE_STATS,
    TOGGLE_ISSUES,
    TOGGLE_HELP,
    TOGGLE_BREAKDOWN,
    TOGGLE_GROUP_MANAGER,
    DISMISS,
    FOCUS_MAIN,
    FOCUS_EXTRA,
    FOCUS_SIDE,
    PREVIOUS_CARD,
    NEXT_CARD,
}

/** Where a shortcut applies. Checked in the order the entries are declared. */
enum class ShortcutScope {
    /** Only while the card inspector is open. */
    INSPECTOR,

    /** Only while nothing is covering the builder. */
    BUILDER,

    /** Always, including over a sheet. */
    ANYWHERE,
}

/**
 * What is on top of the builder right now — at most one thing ever is.
 *
 * One value rather than a flag per sheet, because the flags version could not
 * express the thing that matters most to a toggle: whether the panel covering
 * the builder is *this shortcut's own*. "Press `s` again to close statistics"
 * needs the table to know the statistics sheet is what is open.
 */
enum class ShortcutLayer {
    BUILDER,
    INSPECTOR,
    FILTERS,
    STATS,
    ISSUES,
    HELP,
    GROUPS,
    EGG,
}

/** What is on screen, which decides which shortcuts are live. */
data class ShortcutContext(
    val textInputFocused: Boolean = false,
    val topLayer: ShortcutLayer = ShortcutLayer.BUILDER,
)

data class Shortcut(
    val chord: KeyChord,
    val action: ShortcutAction,
    val scope: ShortcutScope,
    val description: String,
    /**
     * Whether the shortcut still fires while a text field has focus.
     *
     * Off for everything unmodified, because otherwise typing "side" into the
     * deck name opens the statistics panel and the issue list on the way past.
     */
    val allowedInTextInput: Boolean = false,
    /**
     * The layer this shortcut opens and closes, if it is a toggle.
     *
     * A toggle stays live while its own panel is on top — that is the whole
     * "toggle" contract. Without this, opening the panel deactivated the scope
     * the shortcut lived in, and every toggle could open but never close.
     */
    val togglesLayer: ShortcutLayer? = null,
    /**
     * Whether holding the key down should keep firing the action.
     *
     * On for stepping actions like paging and undo, where key repeat is how a
     * keyboard says "keep going". Off for everything that opens or closes
     * something, where repeat would strobe it.
     */
    val repeatable: Boolean = false,
)

/**
 * Every keyboard shortcut, as data.
 *
 * One table and one pure [resolve], rather than a listener per feature. The
 * desktop tool this replaces grew a central registry *and* about ten more raw
 * handlers scattered through the file, with Escape implemented in four separate
 * places that did not agree — so it could not say what its own shortcuts were,
 * and its help dialog was hand-maintained prose that drifted from the code.
 *
 * Keeping it here in `:core` means the whole keyboard story is a function over
 * plain data, tested in the one module whose tests run on every push, leaving
 * the UI with nothing to do but translate a key event into a [KeyChord]. The
 * help sheet renders [all] directly, so it cannot describe a binding that does
 * not exist.
 */
object ShortcutTable {

    val all: List<Shortcut> = listOf(
        // Paging the inspector has to beat anything else bound to the arrows,
        // and holding an arrow is how you walk a long result list.
        Shortcut(
            KeyChord("left"), ShortcutAction.PREVIOUS_CARD, ShortcutScope.INSPECTOR,
            "Previous card", repeatable = true,
        ),
        Shortcut(
            KeyChord("right"), ShortcutAction.NEXT_CARD, ShortcutScope.INSPECTOR,
            "Next card", repeatable = true,
        ),

        // Available even while typing: these are the ones you reach for without
        // first thinking about where the caret is.
        Shortcut(
            KeyChord("escape"), ShortcutAction.DISMISS, ShortcutScope.ANYWHERE,
            "Close what is open, then clear the search", allowedInTextInput = true,
        ),
        Shortcut(
            KeyChord("s", ctrl = true), ShortcutAction.SAVE, ShortcutScope.ANYWHERE,
            "Save deck", allowedInTextInput = true,
        ),
        Shortcut(
            KeyChord("f", ctrl = true), ShortcutAction.FOCUS_SEARCH, ShortcutScope.ANYWHERE,
            "Jump to the search box", allowedInTextInput = true,
        ),

        // Undo is deliberately not allowed while typing: renaming a deck is not a
        // deck edit and does not go on the stack, so undo there would silently
        // revert a card change instead of the text in front of you.
        Shortcut(
            KeyChord("z", ctrl = true), ShortcutAction.UNDO, ShortcutScope.BUILDER,
            "Undo", repeatable = true,
        ),
        Shortcut(
            KeyChord("z", ctrl = true, shift = true), ShortcutAction.REDO, ShortcutScope.BUILDER,
            "Redo", repeatable = true,
        ),
        Shortcut(
            KeyChord("f", ctrl = true, shift = true), ShortcutAction.TOGGLE_FILTERS,
            ShortcutScope.BUILDER, "Filters", togglesLayer = ShortcutLayer.FILTERS,
        ),
        Shortcut(
            KeyChord("slash"), ShortcutAction.FOCUS_SEARCH, ShortcutScope.BUILDER,
            "Jump to the search box",
        ),
        Shortcut(
            KeyChord("s"), ShortcutAction.TOGGLE_STATS, ShortcutScope.BUILDER,
            "Statistics", togglesLayer = ShortcutLayer.STATS,
        ),
        Shortcut(
            KeyChord("i"), ShortcutAction.TOGGLE_ISSUES, ShortcutScope.BUILDER,
            "Deck check", togglesLayer = ShortcutLayer.ISSUES,
        ),
        Shortcut(
            KeyChord("b"), ShortcutAction.TOGGLE_BREAKDOWN, ShortcutScope.BUILDER,
            "Breakdown view — the deck split into your groups",
        ),
        Shortcut(
            KeyChord("g"), ShortcutAction.TOGGLE_GROUP_MANAGER, ShortcutScope.BUILDER,
            "Manage groups", togglesLayer = ShortcutLayer.GROUPS,
        ),
        Shortcut(
            KeyChord("1"), ShortcutAction.FOCUS_MAIN, ShortcutScope.BUILDER,
            "Give the Main deck the whole column",
        ),
        Shortcut(
            KeyChord("2"), ShortcutAction.FOCUS_EXTRA, ShortcutScope.BUILDER,
            "Give the Extra deck the whole column",
        ),
        Shortcut(
            KeyChord("3"), ShortcutAction.FOCUS_SIDE, ShortcutScope.BUILDER,
            "Give the Side deck the whole column",
        ),
        Shortcut(
            KeyChord("slash", shift = true), ShortcutAction.TOGGLE_HELP, ShortcutScope.BUILDER,
            "This list", togglesLayer = ShortcutLayer.HELP,
        ),
    )

    fun resolve(chord: KeyChord, context: ShortcutContext): ShortcutAction? =
        resolveShortcut(chord, context)?.action

    /** The full entry, for callers that also need [Shortcut.repeatable]. */
    fun resolveShortcut(chord: KeyChord, context: ShortcutContext): Shortcut? =
        all.asSequence()
            .filter { it.chord == chord }
            .filter { !context.textInputFocused || it.allowedInTextInput }
            .firstOrNull { it.isActive(context) }

    private fun Shortcut.isActive(context: ShortcutContext): Boolean {
        // A toggle's own panel being on top is exactly when "press it again to
        // close" has to work.
        if (togglesLayer != null && togglesLayer == context.topLayer) return true

        return when (scope) {
            ShortcutScope.ANYWHERE -> true
            ShortcutScope.INSPECTOR -> context.topLayer == ShortcutLayer.INSPECTOR
            ShortcutScope.BUILDER -> context.topLayer == ShortcutLayer.BUILDER
        }
    }
}
