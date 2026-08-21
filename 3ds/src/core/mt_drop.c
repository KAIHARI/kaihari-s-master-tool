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

/* ---- turning "what letting go would do" into the thing it does ---------- */

MtDragOrigin mt_from_hand(int index) {
    MtDragOrigin o = { MT_FROM_HAND, index, -1, mt_slot_pile(MT_SLOT_DECK) };
    return o;
}
MtDragOrigin mt_from_mat(int id) {
    MtDragOrigin o = { MT_FROM_MAT, -1, id, mt_slot_pile(MT_SLOT_DECK) };
    return o;
}
MtDragOrigin mt_from_pile(MtBoardSlot pile, int index) {
    MtDragOrigin o = { MT_FROM_PILE, index, -1, pile };
    return o;
}
MtDragOrigin mt_from_buried(int under, int index) {
    MtDragOrigin o = { MT_FROM_BURIED, index, under, mt_slot_pile(MT_SLOT_DECK) };
    return o;
}

/*
 * Logical equality, not memcmp.
 *
 * `movePile` in the Kotlin is "lift the card, put it somewhere, and if the
 * result equals what we started with then that was not a move" - which is how
 * dropping a pile onto itself falls out for free. Reproducing it needs the
 * comparison, and memcmp is the wrong one: the arrays here keep stale values
 * past their counts, so two logically identical fields can differ in bytes
 * nobody reads. This compares the live prefix of everything and nothing else.
 */
static bool field_equal(const MtPlayField *a, const MtPlayField *b) {
    if (a->life_points != b->life_points || a->phase != b->phase || a->turn != b->turn) return false;
    if (a->mat_count != b->mat_count) return false;
    if (a->hand_count != b->hand_count || a->deck_count != b->deck_count) return false;
    if (a->extra_deck_count != b->extra_deck_count) return false;
    if (a->graveyard_count != b->graveyard_count) return false;
    if (a->banished_count != b->banished_count) return false;
    if (a->instance_count != b->instance_count) return false;

    for (int i = 0; i < a->mat_count; ++i) {
        const MtPlacedCard *x = &a->mat[i], *y = &b->mat[i];
        if (x->card != y->card) return false;
        if (x->at.x != y->at.x || x->at.y != y->at.y) return false;
        if (x->beneath_count != y->beneath_count) return false;
        for (int k = 0; k < x->beneath_count; ++k) {
            if (x->beneath[k] != y->beneath[k]) return false;
        }
    }
    for (int i = 0; i < a->hand_count; ++i)       if (a->hand[i] != b->hand[i]) return false;
    for (int i = 0; i < a->deck_count; ++i)       if (a->deck[i] != b->deck[i]) return false;
    for (int i = 0; i < a->extra_deck_count; ++i) if (a->extra_deck[i] != b->extra_deck[i]) return false;
    for (int i = 0; i < a->graveyard_count; ++i)  if (a->graveyard[i] != b->graveyard[i]) return false;
    for (int i = 0; i < a->banished_count; ++i)   if (a->banished[i] != b->banished[i]) return false;

    /* Position and counters live here, and a pile-to-pile move that changed
     * only a card's facing would otherwise read as no move at all. */
    for (int i = 0; i < a->instance_count; ++i) {
        const MtBoardCard *x = &a->instances[i], *y = &b->instances[i];
        if (x->card_id != y->card_id || x->position != y->position) return false;
        if (x->counters != y->counters || x->material_count != y->material_count) return false;
        for (int k = 0; k < x->material_count; ++k) {
            if (x->materials[k] != y->materials[k]) return false;
        }
    }
    return true;
}

/** A card arriving on the mat, from wherever it was. */
static bool land(MtPlayField *f, MtDragOrigin from, MtMatPoint at, MtCardPosition position) {
    switch (from.kind) {
        case MT_FROM_HAND:
            return mt_field_play_from_hand(f, from.index, at, position);
        /* The position is carried here too, and it was not for two releases:
         * every other branch took it and this one moved the card and dropped
         * the answer, so a set of a card *already on the field* turned it over
         * in the air and then put it back exactly as it was. */
        case MT_FROM_MAT:
            return mt_field_move_on_mat(f, from.id, at, position);
        case MT_FROM_PILE:
            switch (from.pile.kind) {
                case MT_SLOT_DECK:       return mt_field_play_from_deck(f, from.index, at, position);
                case MT_SLOT_EXTRA_DECK: return mt_field_play_from_extra(f, from.index, at, position);
                case MT_SLOT_GRAVEYARD:  return mt_field_play_from_graveyard(f, from.index, at, position);
                case MT_SLOT_BANISHED:   return mt_field_play_from_banished(f, from.index, at, position);
                case MT_SLOT_ZONE:       return false;   /* a zone is not a pile */
            }
            return false;
        case MT_FROM_BURIED:
            return mt_field_take_from_under(f, from.id, from.index, at, position);
    }
    return false;
}

/*
 * A card landing on another card.
 *
 * Anything not already on the mat has to arrive there first - it is put down on
 * top of its target and then stacked, which is both what the hand does and what
 * keeps this to one code path.
 */
static bool pile_onto(MtPlayField *f, MtDragOrigin from, int onto,
                      MtCardPosition position, bool attach) {
    int target = mt_field_placed(f, onto);
    if (target < 0) return false;

    if (from.kind == MT_FROM_MAT) {
        return attach ? mt_field_attach_as_material(f, from.id, onto)
                      : mt_field_stack_onto(f, from.id, onto, position);
    }

    MtMatPoint at = f->mat[target].at;
    if (!land(f, from, at, position)) return false;
    if (f->mat_count <= 0) return false;
    int landed = f->mat[f->mat_count - 1].card;
    return attach ? mt_field_attach_as_material(f, landed, onto)
                  : mt_field_stack_onto(f, landed, onto, MT_POS_KEEP);
}

/** The field without `from`'s card, and that card, ready to go elsewhere. */
static bool lift_from_pile(MtPlayField *f, MtBoardSlot pile, int index, int *out) {
    int *array; int *count;
    switch (pile.kind) {
        case MT_SLOT_DECK:       array = f->deck;       count = &f->deck_count;       break;
        case MT_SLOT_EXTRA_DECK: array = f->extra_deck; count = &f->extra_deck_count; break;
        case MT_SLOT_GRAVEYARD:  array = f->graveyard;  count = &f->graveyard_count;  break;
        case MT_SLOT_BANISHED:   array = f->banished;   count = &f->banished_count;   break;
        default:                 return false;   /* a zone is not a pile */
    }
    if (index < 0 || index >= *count) return false;
    *out = array[index];
    for (int i = index; i < *count - 1; ++i) array[i] = array[i + 1];
    --(*count);
    return true;
}

/** Prepends a card to one of the piles, or inserts it into the hand. */
static bool arrive_in(MtPlayField *f, MtDropKind where, int card, int hand_at) {
    int *array; int *count;
    switch (where) {
        case MT_DROP_GRAVEYARD:  array = f->graveyard;  count = &f->graveyard_count;  break;
        case MT_DROP_BANISH:     array = f->banished;   count = &f->banished_count;   break;
        case MT_DROP_DECK:       array = f->deck;       count = &f->deck_count;       break;
        case MT_DROP_EXTRA_DECK: array = f->extra_deck; count = &f->extra_deck_count; break;
        case MT_DROP_HAND: {
            if (f->hand_count >= MT_MAX_PILE) return false;
            int at = hand_at;
            if (at < 0) at = 0;
            if (at > f->hand_count) at = f->hand_count;
            for (int i = f->hand_count; i > at; --i) f->hand[i] = f->hand[i - 1];
            f->hand[at] = card;
            ++f->hand_count;
            return true;
        }
        default: return false;
    }
    if (*count >= MT_MAX_PILE) return false;
    for (int i = *count; i > 0; --i) array[i] = array[i - 1];
    array[0] = card;
    ++(*count);
    return true;
}

/*
 * A card lifted out of one pile, or out from under another card, and put
 * somewhere else.
 *
 * Moving between piles is a real thing you do - a banished card back to the
 * graveyard, a graveyard card back onto the deck - and every one is the same
 * two steps, so they are written once. Note what does *not* happen: the card
 * keeps how it was lying. Only the mat-origin branches face a card up, which is
 * `PlayField.toGraveyard`'s doing rather than this one's, and the asymmetry is
 * the Kotlin's rather than an oversight here.
 */
static bool move_between(MtPlayField *f, const MtPlayField *before,
                         MtDragOrigin from, MtDropKind where, int hand_at) {
    int card = -1;
    if (from.kind == MT_FROM_PILE) {
        if (!lift_from_pile(f, from.pile, from.index, &card)) return false;
    } else {
        /* Taking a card out of a stack on the mat has to decide whether it came
         * from the pile or from the top card's materials, and that is a fact
         * about the board rather than about the drag. */
        int at = mt_field_placed(f, from.id);
        if (at < 0 || from.index <= 0) return false;
        MtPlacedCard *placed = &f->mat[at];
        if (from.index <= placed->beneath_count) {
            card = placed->beneath[from.index - 1];
            for (int i = from.index - 1; i < placed->beneath_count - 1; ++i) {
                placed->beneath[i] = placed->beneath[i + 1];
            }
            --placed->beneath_count;
        } else {
            MtBoardCard *top = &f->instances[placed->card];
            int m = from.index - placed->beneath_count - 1;
            if (m < 0 || m >= top->material_count) return false;
            card = top->materials[m];
            for (int i = m; i < top->material_count - 1; ++i) {
                top->materials[i] = top->materials[i + 1];
            }
            --top->material_count;
        }
    }

    if (!arrive_in(f, where, card, hand_at)) return false;
    /* Dropping a pile onto itself is not a move: the card comes out and goes
     * back in the same place, which compares equal. */
    return !field_equal(f, before);
}

static bool commit_into(MtPlayField *f, const MtPlayField *before,
                        MtDragOrigin from, MtDropIntent intent,
                        MtCardPosition position) {
    switch (intent.kind) {
        /* Somewhere on the mat, either exactly where you let go or pulled into
         * a zone. The two differ only in the point, which the resolver already
         * worked out, so they share every line. */
        case MT_DROP_FREE:
        case MT_DROP_ZONE:
            return land(f, from, intent.at, position);

        case MT_DROP_STACK:  return pile_onto(f, from, intent.target, position, false);
        case MT_DROP_ATTACH: return pile_onto(f, from, intent.target, position, true);

        /* The hand, at the place in it the pointer was over. Every branch takes
         * the index, including the one from the hand itself - moving a card
         * *within* your hand is the commonest thing anybody does to one. */
        case MT_DROP_HAND:
            switch (from.kind) {
                case MT_FROM_MAT:  return mt_field_to_hand(f, from.id, intent.target);
                case MT_FROM_HAND: return mt_field_reorder_hand(f, from.index, intent.target);
                default:           return move_between(f, before, from, MT_DROP_HAND, intent.target);
            }

        case MT_DROP_GRAVEYARD:
            if (from.kind == MT_FROM_MAT)  return mt_field_to_graveyard(f, from.id);
            if (from.kind == MT_FROM_HAND) return mt_field_hand_to_graveyard(f, from.index);
            return move_between(f, before, from, MT_DROP_GRAVEYARD, -1);

        case MT_DROP_BANISH:
            if (from.kind == MT_FROM_MAT)  return mt_field_to_banish(f, from.id, false);
            if (from.kind == MT_FROM_HAND) return mt_field_hand_to_banish(f, from.index);
            return move_between(f, before, from, MT_DROP_BANISH, -1);

        case MT_DROP_DECK:
            if (from.kind == MT_FROM_MAT)  return mt_field_to_deck_top(f, from.id);
            if (from.kind == MT_FROM_HAND) return mt_field_hand_to_deck_top(f, from.index);
            return move_between(f, before, from, MT_DROP_DECK, -1);

        case MT_DROP_EXTRA_DECK:
            if (from.kind == MT_FROM_MAT)  return mt_field_to_extra_deck(f, from.id);
            /* A hand card has no business in the extra deck. */
            if (from.kind == MT_FROM_HAND) return false;
            return move_between(f, before, from, MT_DROP_EXTRA_DECK, -1);

        /* Nothing sensible; the caller puts the card back where it came from. */
        case MT_DROP_CANCEL: return false;
    }
    return false;
}

bool mt_drop_commit(MtPlayField *field,
                    MtDragOrigin from,
                    MtDropIntent intent,
                    MtCardPosition position) {
    /*
     * On a copy, written back only on success.
     *
     * That is the whole of "leaves the field untouched when it refuses", and it
     * has to be structural rather than careful: several of these are two or
     * three operations deep, and the second one failing after the first
     * succeeded is exactly the case a hand-checked version gets wrong.
     */
    MtPlayField scratch = *field;
    /* `field` itself is the untouched original until the write-back, so it is
     * the `before` - one copy of a 17KB struct rather than two, which matters
     * on a stack of 0x40000. */
    if (!commit_into(&scratch, field, from, intent, position)) return false;
    *field = scratch;
    return true;
}
