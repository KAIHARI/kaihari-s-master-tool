package com.kaiharimoto.mastertool.core.input

/**
 * One thing you can do at the table, in each of the languages you can say it in.
 *
 * [touch] and [pointer] are both required and neither may be blank, which is the
 * standing "every gesture ships with both idioms" rule written down somewhere a
 * test can hold it — see `MatGuideTest`. A gesture with no pointer idiom is a
 * gesture a desktop user cannot make, and the way that happens is never a
 * decision: it is somebody adding a touch gesture and nobody noticing.
 *
 * [key] is optional, because most of these are about a *particular card* and a
 * keyboard has no way to say "this one" on a table where cards are anywhere
 * rather than in numbered zones. Where there is a key, it is the same chord
 * [ShortcutTable] binds, spelled the same way — the guide renders both lists and
 * they must not disagree with each other on the same screen.
 */
data class Gesture(
    val group: GestureGroup,
    val what: String,
    val touch: String,
    val pointer: String,
    val key: String? = null,
)

/**
 * The headings the guide is laid out under, in the order they are shown.
 *
 * Ordered by what somebody opening the guide is most likely to be stuck on,
 * which is not the order the code is written in: the camera is last in the
 * implementation and first here, because "how do I get back to where I was" is
 * the question a table you can orbit creates and never answers by itself.
 */
enum class GestureGroup(val heading: String) {
    CAMERA("Looking around"),
    MOVING("Moving cards"),
    TURNING("Turning cards"),
    READING("Reading the board"),
}

/**
 * What the play stage does, written down once.
 *
 * This exists because the table has no affordances on it — no buttons on the
 * cards, no drop shadows saying "drag me", nothing at all telling you what you
 * may do — and that is deliberate: a table does not either. But a table you have
 * never sat at has a person beside it who tells you the house rules, and this is
 * that person. It is data rather than prose in a composable for the same reason
 * [ShortcutTable] is: a guide written by hand beside the code it describes is a
 * guide that is wrong within two changes, and a wrong guide is worse than none.
 *
 * It is not, and should not become, a list of everything. Anything reachable by
 * a button you can see on the bar is already discoverable and does not need a
 * line here; what belongs is the things you would otherwise have to be told.
 */
object MatGuide {

    val all: List<Gesture> = listOf(
        // ---- the camera ------------------------------------------------------
        Gesture(
            GestureGroup.CAMERA,
            what = "Walk round the table",
            touch = "Drag anywhere on the mat",
            pointer = "Drag anywhere on the mat",
        ),
        Gesture(
            GestureGroup.CAMERA,
            what = "Come closer, or step back",
            touch = "Pinch on the mat",
            pointer = "Scroll wheel",
        ),
        Gesture(
            GestureGroup.CAMERA,
            what = "Sit back down in a known seat",
            touch = "Overhead / Table / Seated on the bar",
            pointer = "Overhead / Table / Seated on the bar",
            key = "1 · 2 · 3",
        ),

        // ---- moving ----------------------------------------------------------
        Gesture(
            GestureGroup.MOVING,
            what = "Pick a card up and put it down",
            touch = "Drag the card",
            pointer = "Drag the card",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Take a whole stack with you",
            touch = "Two fingers, drag the stack",
            pointer = "Shift and drag",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Put a card underneath another one",
            touch = "Drag it over and hold still",
            pointer = "Drag it over and hold still",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Bring a buried card back to the top",
            touch = "Tap it",
            pointer = "Click it",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Draw a card",
            touch = "Drag the top card off the deck",
            pointer = "Drag the top card off the deck",
            key = "D",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Search a pile, or a stack on the table",
            touch = "Tap it — a pile, or any card with cards under it",
            pointer = "Click it — a pile, or any card with cards under it",
            key = "F",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Take a card out of a pile you are searching",
            touch = "Tap it for your hand, or drag it anywhere",
            pointer = "Click it for your hand, or drag it anywhere",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Put a pile back the way it was",
            touch = "Tap the table anywhere outside it",
            pointer = "Click the table anywhere outside it, or press Esc",
        ),
        Gesture(
            GestureGroup.MOVING,
            what = "Shuffle the deck or the extra deck",
            touch = "Tap the mark under it",
            pointer = "Click the mark under it",
            key = "S",
        ),

        // ---- turning ---------------------------------------------------------
        Gesture(
            GestureGroup.TURNING,
            what = "Turn a card face-down, or set it on the way down",
            touch = "Two-finger tap",
            pointer = "Right-click it, then Turn over",
        ),
        Gesture(
            GestureGroup.TURNING,
            what = "Turn a card to defence",
            touch = "Two fingers, twist",
            pointer = "Right-click it, then Rotate",
        ),

        // ---- reading ---------------------------------------------------------
        Gesture(
            GestureGroup.READING,
            what = "Look at a card, or the top of a pile, without moving it",
            touch = "Press and hold it",
            pointer = "Press and hold it",
        ),
        Gesture(
            GestureGroup.READING,
            what = "Everything else a card can be told to do",
            touch = "Two fingers, hold",
            pointer = "Right-click it",
        ),
    )

    /** The gestures under one heading, in the order they were written. */
    fun of(group: GestureGroup): List<Gesture> = all.filter { it.group == group }
}
