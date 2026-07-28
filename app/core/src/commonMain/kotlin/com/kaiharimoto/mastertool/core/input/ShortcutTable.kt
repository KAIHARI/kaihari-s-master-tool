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
                    "up" -> "↑"
                    "down" -> "↓"
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
    TOGGLE_TEST_HAND,
    TOGGLE_SIDING,
    TOGGLE_HELP,
    TOGGLE_SHOWCASE,
    DISMISS,
    FOCUS_MAIN,
    FOCUS_EXTRA,
    FOCUS_SIDE,
    PREVIOUS_CARD,
    NEXT_CARD,

    /** Move the cursor. */
    CURSOR_LEFT,
    CURSOR_RIGHT,
    CURSOR_UP,
    CURSOR_DOWN,

    /** Move the cursor and take everything it passes over. */
    EXTEND_LEFT,
    EXTEND_RIGHT,
    EXTEND_UP,
    EXTEND_DOWN,

    /** Move the cards themselves. */
    CARRY_LEFT,
    CARRY_RIGHT,
    CARRY_UP,
    CARRY_DOWN,
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

/** What is on screen, which decides which shortcuts are live. */
data class ShortcutContext(
    val textInputFocused: Boolean = false,
    val inspectorOpen: Boolean = false,
    /** Any sheet or dialog is covering the builder, the inspector included. */
    val overlayOpen: Boolean = false,
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
     * What the help sheet prints instead of the chord.
     *
     * For a set of bindings that are one idea — the four arrow keys — printing
     * four rows that each say a quarter of it is worse than printing one that
     * says all of it.
     */
    val helpLabel: String? = null,
) {
    /**
     * Whether the help sheet lists this binding.
     *
     * Keyed off the description being blank rather than off a separate flag,
     * because a binding with nothing to say about itself is exactly the one that
     * should not take up a row — and that way the two cannot disagree.
     */
    val inHelp: Boolean get() = description.isNotBlank()
}

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
        // Paging the inspector has to beat anything else bound to the arrows.
        Shortcut(
            KeyChord("left"), ShortcutAction.PREVIOUS_CARD, ShortcutScope.INSPECTOR,
            "Previous card",
        ),
        Shortcut(
            KeyChord("right"), ShortcutAction.NEXT_CARD, ShortcutScope.INSPECTOR,
            "Next card",
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

        // Anywhere rather than builder-only, so the same key that opens it
        // closes it. Everything else here is asymmetric on purpose -- Escape
        // closes them -- but a view with no controls at all needs its key back.
        Shortcut(
            KeyChord("v"), ShortcutAction.TOGGLE_SHOWCASE, ShortcutScope.ANYWHERE,
            "See the deck with nothing on top of it",
        ),

        // Undo is deliberately not allowed while typing: renaming a deck is not a
        // deck edit and does not go on the stack, so undo there would silently
        // revert a card change instead of the text in front of you.
        Shortcut(KeyChord("z", ctrl = true), ShortcutAction.UNDO, ShortcutScope.BUILDER, "Undo"),
        Shortcut(
            KeyChord("z", ctrl = true, shift = true), ShortcutAction.REDO, ShortcutScope.BUILDER,
            "Redo",
        ),
        Shortcut(
            KeyChord("f", ctrl = true, shift = true), ShortcutAction.TOGGLE_FILTERS,
            ShortcutScope.BUILDER, "Filters",
        ),
        Shortcut(
            KeyChord("slash"), ShortcutAction.FOCUS_SEARCH, ShortcutScope.BUILDER,
            "Jump to the search box",
        ),
        Shortcut(KeyChord("s"), ShortcutAction.TOGGLE_STATS, ShortcutScope.BUILDER, "Statistics"),
        Shortcut(KeyChord("i"), ShortcutAction.TOGGLE_ISSUES, ShortcutScope.BUILDER, "Deck check"),
        Shortcut(
            KeyChord("h"), ShortcutAction.TOGGLE_TEST_HAND, ShortcutScope.BUILDER,
            "Deal a test hand",
        ),
        Shortcut(
            KeyChord("p"), ShortcutAction.TOGGLE_SIDING, ShortcutScope.BUILDER,
            "Siding plans",
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
            "This list",
        ),

        // Arranging without a mouse. The cursor is the selection, so all three
        // rows below are the same three verbs applied to it: move it, grow it,
        // or carry what it holds. Declared after the inspector's own arrow
        // bindings, which is what lets paging a card beat moving a cursor while
        // the inspector is open.
        Shortcut(
            KeyChord("left"), ShortcutAction.CURSOR_LEFT, ShortcutScope.BUILDER,
            "Move the cursor between cards", helpLabel = "← → ↑ ↓",
        ),
        Shortcut(KeyChord("right"), ShortcutAction.CURSOR_RIGHT, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("up"), ShortcutAction.CURSOR_UP, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("down"), ShortcutAction.CURSOR_DOWN, ShortcutScope.BUILDER, ""),

        Shortcut(
            KeyChord("left", shift = true), ShortcutAction.EXTEND_LEFT, ShortcutScope.BUILDER,
            "Take everything the cursor passes over", helpLabel = "Shift + arrows",
        ),
        Shortcut(KeyChord("right", shift = true), ShortcutAction.EXTEND_RIGHT, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("up", shift = true), ShortcutAction.EXTEND_UP, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("down", shift = true), ShortcutAction.EXTEND_DOWN, ShortcutScope.BUILDER, ""),

        Shortcut(
            KeyChord("left", ctrl = true), ShortcutAction.CARRY_LEFT, ShortcutScope.BUILDER,
            "Carry the held cards to a new place", helpLabel = "Ctrl + arrows",
        ),
        Shortcut(KeyChord("right", ctrl = true), ShortcutAction.CARRY_RIGHT, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("up", ctrl = true), ShortcutAction.CARRY_UP, ShortcutScope.BUILDER, ""),
        Shortcut(KeyChord("down", ctrl = true), ShortcutAction.CARRY_DOWN, ShortcutScope.BUILDER, ""),
    )

    fun resolve(chord: KeyChord, context: ShortcutContext): ShortcutAction? =
        all.asSequence()
            .filter { it.chord == chord }
            .filter { !context.textInputFocused || it.allowedInTextInput }
            .firstOrNull { it.isActive(context) }
            ?.action

    private fun Shortcut.isActive(context: ShortcutContext): Boolean = when (scope) {
        ShortcutScope.ANYWHERE -> true
        ShortcutScope.INSPECTOR -> context.inspectorOpen
        ShortcutScope.BUILDER -> !context.overlayOpen
    }
}
