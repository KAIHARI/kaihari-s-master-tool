#include "mt_input.h"

/*
 * Ordered by how soon you need it, not by where the button is on the shell.
 *
 * The stylus line is first because it is the whole table: every destination on
 * this board - a zone, a stack, the graveyard, the deck, a particular gap in
 * your hand - is reached by dragging, and `mt_drop_resolve` decides which of
 * them you meant. Nothing else here is a destination.
 */
const MtControl MT_CONTROLS[MT_CONTROL_COUNT] = {
    { "Stylus",   "drag a card - where you let go decides" },
    { "A",        "draw" },
    { "B",        "undo" },
    { "X",        "shuffle the deck" },
    { "Y",        "flip the card under the stylus" },
    { "L drag",   "set it face-down" },
    { "R drag",   "lay it sideways" },
    { "ZR drag",  "tuck under, as material" },
    { "R + Y",    "turn the card under the stylus" },
    { "L + X",    "deal a new hand" },
    { "L + Y",    "next seat" },
    { "Pad",      "orbit" },
    { "ZL / ZR",  "dolly out / in" },
    { "D-pad",    "pick a hand card / phase / end turn" },
    { "- / +",    "life points" },
    { "Select",   "hold for this list" },
    { "Start",    "quit" },
};
