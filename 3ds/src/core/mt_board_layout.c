#include "mt_board_layout.h"

#include <math.h>
#include <stddef.h>

/*
 * The lane between two zones, as a fraction of a card's width. Chosen to look
 * like a mat rather than to prevent overlap — a monster set in defence is a
 * turned card and overlaps its neighbours on a real mat too.
 */
static const float GAP_FRACTION = 0.11f;

/** How far the hand sits below the field, in gaps. Off the table, not on it. */
static const float HAND_GAP_FRACTION = 2.5f;

/** The band that says what the hand is, as a fraction of a card's height. */
static const float READOUT_FRACTION = 0.30f;

/** Which columns the two extra monster zones sit above: over M2 and M4. */
static const int EXTRA_MONSTER_COLUMNS[2] = { 2, 4 };

MtSlot mt_board_bounds(const MtBoardLayout *layout) {
    MtSlot out;
    out.left   = layout->field.left;
    out.top    = layout->field.top;
    out.width  = layout->field.width;
    out.height = mt_slot_bottom(layout->hand) - layout->field.top;
    return out;
}

MtBoardLayout mt_board_solve(float width,
                             float height,
                             float aspect_ratio,
                             float perspective_growth,
                             float room_above) {
    MtBoardLayout out;
    float ratio  = (aspect_ratio > 0.0f) ? aspect_ratio : 1.0f;
    float growth = (perspective_growth < 1.0f) ? 1.0f : perspective_growth;

    /* Clamped rather than trusted: a caller asking for most of the screen would
     * solve a board of nothing and then report that it fits. */
    float reserved = room_above;
    if (reserved < 0.0f) reserved = 0.0f;
    if (reserved > 0.4f) reserved = 0.4f;

    float usable = height * (1.0f - reserved);

    /* Width:  7 cards + 6 lanes.
     * Height: 3 rows of card + 2 lanes, then the hand's own lane and a fourth
     *         card's worth of band for the fan to sit in. */
    float by_width = (width / growth)
        / ((float)MT_BOARD_COLUMNS + (float)(MT_BOARD_COLUMNS - 1) * GAP_FRACTION);
    float by_height = (usable / growth)
        / (((float)MT_BOARD_ROWS + 1.0f + READOUT_FRACTION) / ratio
           + ((float)(MT_BOARD_ROWS - 1) + HAND_GAP_FRACTION + 1.0f) * GAP_FRACTION);

    float card_width = fminf(by_width, by_height);
    if (!(card_width >= 0.0f)) card_width = 0.0f;   /* also catches NaN */

    float card_height = card_width / ratio;
    float gap = card_width * GAP_FRACTION;

    float pitch_x = card_width + gap;
    float pitch_y = card_height + gap;

    float field_width  = (float)MT_BOARD_COLUMNS * card_width
                       + (float)(MT_BOARD_COLUMNS - 1) * gap;
    float field_height = (float)MT_BOARD_ROWS * card_height
                       + (float)(MT_BOARD_ROWS - 1) * gap;

    float hand_height    = card_height;
    float readout_height = card_height * READOUT_FRACTION;
    float hand_gap       = gap * HAND_GAP_FRACTION;
    float stack_height   = field_height + hand_gap + readout_height + gap + hand_height;

    float origin_x = (width - field_width) * 0.5f;

    /* Centred in what is left, then pushed down past the reserved band — so the
     * room is above the desk, which is where a room is. */
    float slack = (usable - stack_height) * 0.5f;
    if (slack < 0.0f) slack = 0.0f;
    float origin_y = height * reserved + slack;

    float readout_top = origin_y + field_height + hand_gap;

    out.card_width  = card_width;
    out.card_height = card_height;
    out.gap         = gap;

#define SLOT_AT(col, row) ((MtSlot){                       \
        origin_x + (float)(col) * pitch_x,                 \
        origin_y + (float)(row) * pitch_y,                 \
        card_width, card_height })

    /*
     * Insertion order, and it must match `BoardLayouter.solve`'s buildMap
     * exactly — see the note on MtBoardLayout.slots. Any point on a midline
     * between two inflated neighbours goes to whichever appears first here.
     */
    int n = 0;
    for (int i = 0; i < 2; ++i) {
        out.slots[n].slot = mt_slot_zone(mt_zone(MT_ZONE_EXTRA_MONSTER, i));
        out.slots[n].rect = SLOT_AT(EXTRA_MONSTER_COLUMNS[i], 0);
        ++n;
    }
    out.slots[n].slot = mt_slot_pile(MT_SLOT_BANISHED);
    out.slots[n].rect = SLOT_AT(MT_BOARD_COLUMNS - 1, 0);
    ++n;

    out.slots[n].slot = mt_slot_zone(mt_zone(MT_ZONE_FIELD_SPELL, 0));
    out.slots[n].rect = SLOT_AT(0, 1);
    ++n;
    for (int i = 0; i < 5; ++i) {
        out.slots[n].slot = mt_slot_zone(mt_zone(MT_ZONE_MONSTER, i));
        out.slots[n].rect = SLOT_AT(i + 1, 1);
        ++n;
    }
    out.slots[n].slot = mt_slot_pile(MT_SLOT_GRAVEYARD);
    out.slots[n].rect = SLOT_AT(MT_BOARD_COLUMNS - 1, 1);
    ++n;

    out.slots[n].slot = mt_slot_pile(MT_SLOT_EXTRA_DECK);
    out.slots[n].rect = SLOT_AT(0, 2);
    ++n;
    for (int i = 0; i < 5; ++i) {
        out.slots[n].slot = mt_slot_zone(mt_zone(MT_ZONE_SPELL_TRAP, i));
        out.slots[n].rect = SLOT_AT(i + 1, 2);
        ++n;
    }
    out.slots[n].slot = mt_slot_pile(MT_SLOT_DECK);
    out.slots[n].rect = SLOT_AT(MT_BOARD_COLUMNS - 1, 2);
    ++n;

#undef SLOT_AT

    out.slot_count = n;

    out.hand.left   = origin_x;
    out.hand.top    = readout_top + readout_height + gap;
    out.hand.width  = field_width;
    out.hand.height = hand_height;

    out.readout.left   = origin_x;
    out.readout.top    = readout_top;
    out.readout.width  = field_width;
    out.readout.height = readout_height;

    out.field.left   = origin_x;
    out.field.top    = origin_y;
    out.field.width  = field_width;
    out.field.height = field_height;

    out.fits = card_width >= MT_MIN_CARD_WIDTH;
    return out;
}

const MtPlacedSlot *mt_board_slot_at(const MtBoardLayout *layout, float x, float y) {
    for (int i = 0; i < layout->slot_count; ++i) {
        if (mt_slot_contains(layout->slots[i].rect, x, y)) return &layout->slots[i];
    }
    /* Second pass: each slot claims the gap around it, so the lanes between
     * zones are not dead space a card can be dropped into and lost. The
     * inflation is half the gap, so two neighbours meet exactly on the midline
     * and the first in insertion order wins it. */
    float by = layout->gap * 0.5f;
    for (int i = 0; i < layout->slot_count; ++i) {
        if (mt_slot_contains(mt_slot_inflated(layout->slots[i].rect, by), x, y)) {
            return &layout->slots[i];
        }
    }
    return NULL;
}

const MtSlot *mt_board_rect_of(const MtBoardLayout *layout, MtBoardSlot slot) {
    for (int i = 0; i < layout->slot_count; ++i) {
        if (mt_board_slot_eq(layout->slots[i].slot, slot)) return &layout->slots[i].rect;
    }
    return NULL;
}
