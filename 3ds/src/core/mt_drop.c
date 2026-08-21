#include "mt_drop.h"

#include <math.h>
#include <stddef.h>

const char *mt_drop_label(MtDropIntent intent) {
    switch (intent.kind) {
        case MT_DROP_FREE:       return "Place";
        case MT_DROP_ZONE:       return "Zone";
        case MT_DROP_STACK:      return "Stack";
        case MT_DROP_ATTACH:     return "Attach";
        case MT_DROP_HAND:       return "Hand";
        case MT_DROP_GRAVEYARD:  return "Graveyard";
        case MT_DROP_BANISH:     return "Banish";
        case MT_DROP_DECK:       return "Deck";
        case MT_DROP_EXTRA_DECK: return "Extra deck";
        /* Said as the thing it does rather than as the thing it declines to do.
         * It is reachable on purpose — put a card back in the spread you took it
         * out of — and "Cancel" describes a gesture failing. */
        case MT_DROP_CANCEL:     return "Put back";
        default:                 return "";
    }
}

MtCardPosition mt_set_position(bool face_down,
                               bool turned,
                               const MtDropIntent *intent,
                               MtMonsterHint monster) {
    /* Said with the fingers, so nothing below gets to argue. */
    if (turned) {
        return face_down ? MT_POS_FACE_DOWN_DEF : MT_POS_FACE_UP_DEF;
    }
    if (!face_down) return MT_POS_FACE_UP_ATK;

    bool sideways;
    if (intent != NULL && intent->kind == MT_DROP_ZONE
        && intent->slot.kind == MT_SLOT_ZONE) {
        /* The board has been asked and the board has answered. */
        sideways = mt_zone_is_monster(intent->slot.zone);
    } else {
        /* Free on the felt, onto a stack, into a pile: no zone to ask, so the
         * card speaks for itself. An unknown card resolves the way a spell
         * does rather than by guessing. */
        sideways = (monster == MT_MONSTER_YES);
    }

    return sideways ? MT_POS_FACE_DOWN_DEF : MT_POS_FACE_DOWN_ATK;
}

/* ---- where a dragged card would land ------------------------------------ */

/*
 * How close the centre of a card must come to a zone's centre to be pulled in,
 * as a fraction of the zone's width - and how far it must go to escape. Enter
 * at just over half a card so a card roughly over a zone commits to it; leave
 * at nearly a full one so nudging it about inside that zone does not keep
 * dropping it out again.
 */
static const float ZONE_ENTER = 0.55f;
static const float ZONE_LEAVE = 0.95f;

/** The same idea for landing on another card, against card width. */
static const float STACK_ENTER = 0.40f;
static const float STACK_LEAVE = 0.72f;

/*
 * How much closer the target you already have is allowed to seem.
 *
 * There are two hysteresis decisions here and they want different scales.
 * *Whether to snap at all* is the enter/leave pair, and wants to be generous.
 * *Which* zone, when several are tiled a card apart, is a different question -
 * the honest answer changes at the midline, and being generous would mean
 * dragging a card a zone and a half before the highlight admits it moved. So
 * the incumbent gets a small bias instead: enough to kill jitter exactly on the
 * boundary, not enough to lie.
 */
static const float INCUMBENT_BIAS = 0.12f;

/** And for the piles and the hand, which are bands rather than points. */
static const float PILE_ENTER = 0.60f;
static const float PILE_LEAVE = 1.00f;

/*
 * How near the gap it came out of a card must be dropped to go back into it.
 *
 * The obvious rule was "anywhere inside the open fan", and it cannot be used: a
 * spread covers `layout.field`, so its footprint covers every zone and every
 * pile on the table, and "inside the fan outranks the board" would mean that
 * while a pile is open you cannot put a card on the board at all.
 *
 * Half a card is 91% of the zone catchment it outranks - not a tighter target,
 * the same target standing in front of another one. Size cannot separate the
 * two. `MtFanHome.departed` separates them by *history*, and rank 0 separates
 * them again by asking whether a zone is pulling harder. Either alone leaves
 * aims lost.
 */
static const float PUT_BACK_ENTER = 0.50f;
static const float PUT_BACK_LEAVE = 0.88f;

static float threshold(bool sticky, float enter, float leave) {
    return sticky ? leave : enter;
}

static float distance(float ax, float ay, float bx, float by) {
    float dx = ax - bx, dy = ay - by;
    return sqrtf(dx * dx + dy * dy);
}

void mt_layout_to_pixels(const MtBoardLayout *l, MtMatPoint p, float *x, float *y) {
    *x = l->field.left + p.x * l->field.width;
    *y = l->field.top + p.y * l->field.height;
}

MtMatPoint mt_layout_to_mat(const MtBoardLayout *l, float x, float y) {
    MtMatPoint p;
    p.x = (l->field.width > 0.0f) ? (x - l->field.left) / l->field.width : 0.5f;
    p.y = (l->field.height > 0.0f) ? (y - l->field.top) / l->field.height : 0.5f;
    return p;
}

MtFanHome mt_fan_home_seeing(MtFanHome home, MtMatPoint landing,
                             const MtBoardLayout *layout) {
    if (home.departed || layout->card_width <= 0.0f) return home;
    float hx, hy, lx, ly;
    mt_layout_to_pixels(layout, home.at, &hx, &hy);
    mt_layout_to_pixels(layout, landing, &lx, &ly);
    /* Measured against the *landing* rather than the pointer, because the
     * landing is what the resolver compares. */
    if (distance(lx, ly, hx, hy) > layout->card_width * MT_FAN_DEPARTURE) {
        home.departed = true;
    }
    return home;
}

/** Which zone is pulling, and how hard once its incumbency is paid for. */
typedef struct {
    bool found;
    MtBoardSlot slot;
    MtSlot rect;
    float biased;
} Pull;

/*
 * The zone nearest the pointer, with the incumbent's bias already subtracted.
 *
 * One function because rank 0 asks it too, and two independent searches over
 * the same map with the same bias are two answers waiting to disagree.
 *
 * `cap` is the most the bias may be worth here, in pixels. Unbounded for every
 * caller but the put-back contest, which must hold its hysteresis below the
 * distance between the two things it is choosing between.
 */
static Pull nearest_zone(float px, float py, const MtBoardLayout *layout,
                         const MtDropIntent *previous, float cap) {
    Pull best;
    best.found = false;
    best.biased = 0.0f;
    best.slot = mt_slot_pile(MT_SLOT_DECK);
    best.rect = layout->field;

    bool has_incumbent = (previous != NULL && previous->kind == MT_DROP_ZONE);

    for (int i = 0; i < layout->slot_count; ++i) {
        if (layout->slots[i].slot.kind != MT_SLOT_ZONE) continue;
        const MtSlot *rect = &layout->slots[i].rect;

        float bias = 0.0f;
        if (has_incumbent && mt_board_slot_eq(layout->slots[i].slot, previous->slot)) {
            float full = rect->width * INCUMBENT_BIAS;
            bias = (full < cap) ? full : cap;
        }
        float value = distance(px, py, mt_slot_centre_x(*rect), mt_slot_centre_y(*rect)) - bias;

        /* Strictly less, so a tie goes to the earlier slot - which is the
         * insertion order the layout carries and the same rule slotAt uses. */
        if (!best.found || value < best.biased) {
            best.found = true;
            best.slot = layout->slots[i].slot;
            best.rect = *rect;
            best.biased = value;
        }
    }
    return best;
}

/*
 * Whether the pointer is over one of the piles.
 *
 * Their catchment is generous and rectangular rather than radial, because a
 * pile sits at the edge of the mat and half a radial catchment would be off the
 * table where no finger can reach.
 */
static bool pile_at(float px, float py, const MtBoardLayout *layout,
                    const MtDropIntent *previous, MtDropIntent *out) {
    static const MtBoardSlotKind SLOTS[4] = {
        MT_SLOT_GRAVEYARD, MT_SLOT_BANISHED, MT_SLOT_DECK, MT_SLOT_EXTRA_DECK,
    };
    static const MtDropKind KINDS[4] = {
        MT_DROP_GRAVEYARD, MT_DROP_BANISH, MT_DROP_DECK, MT_DROP_EXTRA_DECK,
    };

    for (int i = 0; i < 4; ++i) {
        const MtSlot *rect = mt_board_rect_of(layout, mt_slot_pile(SLOTS[i]));
        if (rect == NULL) continue;
        bool sticky = (previous != NULL && previous->kind == KINDS[i]);
        float grow = rect->width * threshold(sticky, PILE_ENTER - 0.5f, PILE_LEAVE - 0.5f);
        if (mt_slot_contains(mt_slot_inflated(*rect, grow), px, py)) {
            *out = mt_drop_simple(KINDS[i]);
            return true;
        }
    }
    return false;
}

static bool in_hand(float px, float py, const MtBoardLayout *layout,
                    const MtDropIntent *previous) {
    bool sticky = (previous != NULL && previous->kind == MT_DROP_HAND);
    float grow = layout->card_width * threshold(sticky, 0.10f, 0.45f);
    return mt_slot_contains(mt_slot_inflated(layout->hand, grow), px, py);
}

MtDropIntent mt_drop_resolve(MtMatPoint point,
                             int dragged,
                             const MtPlayField *field,
                             const MtBoardLayout *layout,
                             const MtDropIntent *previous,
                             bool attaching,
                             const MtFanHome *home,
                             const MtHandRow *hand,
                             float hand_step) {
    if (layout->card_width <= 0.0f) return mt_drop_free(point);

    float px, py;
    mt_layout_to_pixels(layout, point, &px, &py);
    float card_width = layout->card_width;

    /*
     * 0. Back into the gap it came out of - once the card has really been taken
     *    out of it, and only while no zone is pulling harder.
     *
     *    Both halves are load-bearing. The latch alone fixes an aim made
     *    straight from the slot and leaves a drag that wanders before it aims
     *    failing at the old rate. The comparison alone fixes the zones and
     *    leaves the piles, because a slot at the end of a row is nearer the
     *    graveyard than any zone.
     *
     *    And the bias on that comparison is capped at half the gap, which is
     *    what makes it a fact about geometry rather than about one seat. A bias
     *    exists so a decision does not flicker while a pointer trembles on a
     *    boundary; it must never be worth so much that it carries one target
     *    past the *centre* of the other, because then the losing gesture is
     *    unreachable no matter how exactly it is aimed.
     */
    if (home != NULL && home->departed) {
        bool sticky = (previous != NULL && previous->kind == MT_DROP_CANCEL);
        float hx, hy;
        mt_layout_to_pixels(layout, home->at, &hx, &hy);

        /* Unbiased, only to name which zone this contest is against: letting
         * hysteresis pick the candidate *and* then bounding itself by the
         * candidate it picked is a loop with no fixed point. */
        Pull unbiased = nearest_zone(px, py, layout, previous, 0.0f);
        float gap = unbiased.found
            ? distance(hx, hy, mt_slot_centre_x(unbiased.rect), mt_slot_centre_y(unbiased.rect))
            : 3.4028235e38f;
        float full = card_width * INCUMBENT_BIAS;
        float half = gap * 0.5f;
        float margin = (full < half) ? full : half;

        float to_home = distance(px, py, hx, hy) - (sticky ? margin : 0.0f);
        float reach = card_width * threshold(sticky, PUT_BACK_ENTER, PUT_BACK_LEAVE);
        Pull pull = nearest_zone(px, py, layout, previous, margin);
        if (to_home <= reach && (!pull.found || to_home <= pull.biased)) {
            return mt_drop_simple(MT_DROP_CANCEL);
        }
    }

    /* 1. The piles and the hand: unambiguous places you had to travel to. */
    MtDropIntent pile;
    if (pile_at(px, py, layout, previous, &pile)) return pile;

    if (in_hand(px, py, layout, previous)) {
        MtDropIntent into_hand = mt_drop_simple(MT_DROP_HAND);
        into_hand.target = mt_hand_insert_at(layout->hand, card_width, hand,
                                             field->hand_count, px, hand_step);
        return into_hand;
    }

    /*
     * 2. A specific card. Nearest first, so a pointer between two piles lands on
     *    the one it is actually closest to rather than whichever happens to be
     *    earlier in the list.
     */
    int held_onto = -1;
    if (previous != NULL &&
        (previous->kind == MT_DROP_STACK || previous->kind == MT_DROP_ATTACH)) {
        held_onto = previous->target;
    }

    int nearest = -1;
    float nearest_value = 0.0f;
    for (int i = 0; i < field->mat_count; ++i) {
        int id = field->mat[i].card;
        if (id == dragged) continue;
        float cx, cy;
        mt_layout_to_pixels(layout, field->mat[i].at, &cx, &cy);
        float value = distance(px, py, cx, cy)
                    - ((id == held_onto) ? card_width * INCUMBENT_BIAS : 0.0f);
        if (nearest < 0 || value < nearest_value) { nearest = i; nearest_value = value; }
    }

    if (nearest >= 0) {
        int id = field->mat[nearest].card;
        bool sticky = (held_onto == id);
        float reach = card_width * threshold(sticky, STACK_ENTER, STACK_LEAVE);
        float cx, cy;
        mt_layout_to_pixels(layout, field->mat[nearest].at, &cx, &cy);
        if (distance(px, py, cx, cy) <= reach) {
            MtDropIntent onto = mt_drop_simple(attaching ? MT_DROP_ATTACH : MT_DROP_STACK);
            onto.target = id;
            return onto;
        }
    }

    /* 3. A zone's pull. Only the field zones - the piles were handled above and
     *    have their own, larger, catchment. */
    Pull zone = nearest_zone(px, py, layout, previous, 3.4028235e38f);
    if (zone.found) {
        bool sticky = (previous != NULL && previous->kind == MT_DROP_ZONE &&
                       mt_board_slot_eq(previous->slot, zone.slot));
        float reach = zone.rect.width * threshold(sticky, ZONE_ENTER, ZONE_LEAVE);
        float zx = mt_slot_centre_x(zone.rect), zy = mt_slot_centre_y(zone.rect);
        if (distance(px, py, zx, zy) <= reach) {
            return mt_drop_zone(zone.slot, mt_layout_to_mat(layout, zx, zy));
        }
    }

    /* 4. The mat itself, which is always a valid answer. */
    return mt_drop_free(point);
}
