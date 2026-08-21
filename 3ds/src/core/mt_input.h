/*
 * The console's controls, as data — the C form of what
 * `core/input/ShortcutTable.kt` is for a keyboard and `core/input/MatGuide.kt`
 * is for a mat.
 *
 * A table rather than a paragraph for the reason both of those are: the guide
 * the user reads is *rendered from this*, so a binding that changes and a guide
 * that does not cannot happen. The table on the tablet drifted from the help
 * sheet exactly once, which is why the help sheet stopped being written by hand.
 *
 * It carries no libctru key codes on purpose. A `KEY_A` in here would make
 * `src/core/` unbuildable by the host compiler and take the whole conformance
 * suite down with it; the button is a *string*, the meaning is a string, and
 * the switch that turns one into an action lives in `main.c` where the console
 * headers already are.
 */
#ifndef MT_INPUT_H
#define MT_INPUT_H

typedef struct {
    /** What you press, written the way the console's own manuals write it. */
    const char *button;
    /** What it does, in the fewest words that are still true. */
    const char *meaning;
} MtControl;

#define MT_CONTROL_COUNT 17

extern const MtControl MT_CONTROLS[MT_CONTROL_COUNT];

#endif /* MT_INPUT_H */
