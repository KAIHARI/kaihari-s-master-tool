/*
 * The duel table's geometry — the C form of `core/layout/BoardLayout.kt`.
 *
 * Solved rather than negotiated, for the reason that file gives: the seven
 * columns are the input, the rows follow from the game, and the size a card is
 * drawn at is the single free variable.
 *
 * On the 3DS this is solved **twice against different surfaces** — once for the
 * top screen's perspective view and once for the bottom screen's orthographic
 * map — and the two results are not required to agree, because they are
 * answering "how big is a card here". What must agree is the *mat* coordinates
 * a card is stored at, and those are fractions (`MtMatPoint`) that neither
 * solve touches.
 */
#ifndef MT_BOARD_LAYOUT_H
#define MT_BOARD_LAYOUT_H

#include "mt_types.h"

/** Field spell / extra deck, five zones, graveyard / deck. */
#define MT_BOARD_COLUMNS 7

/** Extra monster zones, monsters, spells and traps. */
#define MT_BOARD_ROWS 3

/*
 * 2 extra monster + banished + field spell + 5 monster + graveyard
 * + extra deck + 5 spell/trap + deck.
 */
#define MT_BOARD_SLOT_COUNT 17

/** Below this a card is a coloured rectangle rather than a card. */
#define MT_MIN_CARD_WIDTH 28.0f

typedef struct {
    MtBoardSlot slot;
    MtSlot rect;
} MtPlacedSlot;

typedef struct {
    float card_width;
    float card_height;
    float gap;

    /*
     * Insertion-ordered, and the order is load-bearing.
     *
     * `mt_board_slot_at` resolves ties by taking the first match, exactly as
     * the Kotlin's `slots.entries.firstOrNull` does over a `buildMap`, which is
     * a LinkedHashMap and therefore ordered by insertion. Reordering this array
     * silently changes which zone claims a point on a shared midline.
     */
    MtPlacedSlot slots[MT_BOARD_SLOT_COUNT];
    int slot_count;

    /** The band the hand is fanned across. Not a slot: a fan is not a grid. */
    MtSlot hand;
    /** A line above the hand, for saying what is in it. */
    MtSlot readout;
    /** The three rows of zones, without the hand — what the felt covers. */
    MtSlot field;
    bool fits;
} MtBoardLayout;

/** Everything the table occupies, field and hand together. */
MtSlot mt_board_bounds(const MtBoardLayout *layout);

/**
 * Solve the table.
 *
 * @param perspective_growth how much larger the near edge will be once the
 *   table is tilted, as a multiplier. Pass 1 for a flat table — which is what
 *   the bottom screen's map always passes, since an orthographic view has no
 *   near edge to grow.
 * @param room_above the fraction of the surface's height kept clear above the
 *   board, for the room to be seen in. Clamped to 0..0.4.
 */
MtBoardLayout mt_board_solve(float width,
                             float height,
                             float aspect_ratio,
                             float perspective_growth,
                             float room_above);

/** Which slot a point lands in, or NULL. Each slot claims the gap around it. */
const MtPlacedSlot *mt_board_slot_at(const MtBoardLayout *layout, float x, float y);

/** The rectangle for a given slot, or NULL if the layout has no such slot. */
const MtSlot *mt_board_rect_of(const MtBoardLayout *layout, MtBoardSlot slot);

#endif /* MT_BOARD_LAYOUT_H */
