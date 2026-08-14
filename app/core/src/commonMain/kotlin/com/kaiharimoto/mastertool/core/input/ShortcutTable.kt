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
    NEXT_LENS,
    PREVIOUS_LENS,
    TOGGLE_GROUP_MANAGER,
    NEW_GROUP,
    TOGGLE_SEARCH_PANE,
    TOGGLE_CONSISTENCY,
    DISMISS,
    FOCUS_MAIN,
    FOCUS_EXTRA,
    FOCUS_SIDE,
    PREVIOUS_CARD,
    NEXT_CARD,

    // The play stage. Only the actions that need no card selected — everything
    // that is *about* a particular card is reached by touching that card, or by
    // its menu, because a keyboard has no way to say "this one" on a table
    // where cards are anywhere rather than in numbered zones.
    PLAY_DRAW,
    PLAY_SHUFFLE,
    PLAY_SEARCH_DECK,
    PLAY_NEW_HAND,
    PLAY_NEXT_PHASE,
    PLAY_END_TURN,
    PLAY_SCENE,
    PLAY_CAMERA_TOUCH,
    PLAY_SEAT_OVERHEAD,
    PLAY_SEAT_TABLE,
    PLAY_SEAT_SEATED,
    PLAY_SEAT_POV,
    PLAY_GUIDE,
}

/**
 * Where a shortcut applies, with the heading it is listed under.
 *
 * The heading lives here rather than in the help sheet so the sheet can render
 * every scope by walking these entries. Naming the scopes it knows about is how
 * a help sheet ends up quietly omitting a whole screen's shortcuts the day one
 * is added — which is the drift this table exists to make impossible.
 *
 * Declaration order is display order.
 */
enum class ShortcutScope(val heading: String) {
    /** Always, including over a sheet. */
    ANYWHERE("Anywhere"),

    /** Only while nothing is covering the builder. */
    BUILDER("Building a deck"),

    /** Only on the play stage, which is a screen rather than a sheet. */
    PLAY("At the table"),

    /** Only while the card inspector is open. */
    INSPECTOR("Inspecting a card"),
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
    CONSISTENCY,
    EGG,
    PLAY,
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
            KeyChord("b"), ShortcutAction.NEXT_LENS, ShortcutScope.BUILDER,
            "Next lens — read the deck by roles, archetype or copies",
        ),
        Shortcut(
            KeyChord("b", shift = true), ShortcutAction.PREVIOUS_LENS, ShortcutScope.BUILDER,
            "Previous lens",
        ),
        Shortcut(
            KeyChord("n"), ShortcutAction.NEW_GROUP, ShortcutScope.BUILDER,
            "New group — then tap the cards that belong to it",
        ),
        Shortcut(
            KeyChord("g"), ShortcutAction.TOGGLE_GROUP_MANAGER, ShortcutScope.BUILDER,
            "Manage groups", togglesLayer = ShortcutLayer.GROUPS,
        ),
        Shortcut(
            KeyChord("d"), ShortcutAction.TOGGLE_SEARCH_PANE, ShortcutScope.BUILDER,
            "Show or hide the card database",
        ),
        Shortcut(
            KeyChord("c"), ShortcutAction.TOGGLE_CONSISTENCY, ShortcutScope.BUILDER,
            "Ask the deck a question about its opening hand",
            togglesLayer = ShortcutLayer.CONSISTENCY,
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

        // The play stage. Several of these reuse letters the builder already
        // spends, which is safe because the two scopes are never live at once —
        // and it is better than second-choice keys, since nobody holding a
        // keyboard over a table wants `d` to mean anything but draw.
        Shortcut(
            KeyChord("d"), ShortcutAction.PLAY_DRAW, ShortcutScope.PLAY,
            "Draw a card", repeatable = true,
        ),
        Shortcut(
            KeyChord("s"), ShortcutAction.PLAY_SHUFFLE, ShortcutScope.PLAY, "Shuffle the deck",
        ),
        // The hot path of the whole game, and the reason the fan exists: fetching
        // a specific card out of a sixty-card deck is the most frequent
        // non-trivial thing anybody does. `f` for find, because `s` is spent on
        // the shuffle that closing the search performs anyway.
        Shortcut(
            KeyChord("f"), ShortcutAction.PLAY_SEARCH_DECK, ShortcutScope.PLAY,
            "Search the deck",
        ),
        Shortcut(
            KeyChord("n"), ShortcutAction.PLAY_NEW_HAND, ShortcutScope.PLAY,
            "Clear the table and deal again",
        ),
        Shortcut(
            KeyChord("space"), ShortcutAction.PLAY_NEXT_PHASE, ShortcutScope.PLAY, "Next phase",
        ),
        Shortcut(
            KeyChord("t"), ShortcutAction.PLAY_END_TURN, ShortcutScope.PLAY, "End the turn",
        ),
        // `r` for room. The touch idiom is the button on the bar beside the
        // seats, which is where it belongs: choosing a room is the same kind of
        // act as choosing where to sit, and neither of them is about a card.
        Shortcut(
            KeyChord("r"), ShortcutAction.PLAY_SCENE, ShortcutScope.PLAY,
            "Change the room the table is in",
        ),
        // `c` for camera. It reads oddly on a desktop, where the felt is the
        // least of the camera's idioms — the wheel and the middle-drag never go
        // near the arbiter and are unaffected by this — but the setting is one
        // document shared by both platforms, and a preference a keyboard cannot
        // reach is half a feature.
        Shortcut(
            KeyChord("c"), ShortcutAction.PLAY_CAMERA_TOUCH, ShortcutScope.PLAY,
            "Let the felt move the camera, or stop it",
        ),

        // The four seats. Digits, reusing the builder's pane-focus muscle
        // memory — a chord means whatever the live scope says it means, which
        // is what scopes are for, and `d` and `s` already do it.
        Shortcut(
            KeyChord("1"), ShortcutAction.PLAY_SEAT_OVERHEAD, ShortcutScope.PLAY,
            "Look at the table from overhead",
        ),
        Shortcut(
            KeyChord("2"), ShortcutAction.PLAY_SEAT_TABLE, ShortcutScope.PLAY,
            "Sit back down at the table",
        ),
        Shortcut(
            KeyChord("3"), ShortcutAction.PLAY_SEAT_SEATED, ShortcutScope.PLAY,
            "Drop down into the player's chair",
        ),
        Shortcut(
            KeyChord("4"), ShortcutAction.PLAY_SEAT_POV, ShortcutScope.PLAY,
            "Sit at the desk, at your own eye level",
        ),
        // The same chord the builder's help uses, because it is the same
        // question asked on a different screen — and because a guide you have to
        // find is a guide nobody reads. The touch idiom is the button on the
        // bar, which is the half of this the table actually needed.
        Shortcut(
            KeyChord("slash", shift = true), ShortcutAction.PLAY_GUIDE, ShortcutScope.PLAY,
            "How the table is played",
        ),
        Shortcut(
            KeyChord("z", ctrl = true), ShortcutAction.UNDO, ShortcutScope.PLAY,
            "Undo", repeatable = true,
        ),
        Shortcut(
            KeyChord("z", ctrl = true, shift = true), ShortcutAction.REDO, ShortcutScope.PLAY,
            "Redo", repeatable = true,
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
            ShortcutScope.PLAY -> context.topLayer == ShortcutLayer.PLAY
        }
    }
}
