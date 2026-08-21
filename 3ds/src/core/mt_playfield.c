#include "mt_playfield.h"

#include <string.h>

#include "mt_random.h"

/* ---- small array surgery, named so the operations below read as sentences -- */

static void ints_remove_at(int *a, int *n, int index) {
    if (index < 0 || index >= *n) return;   /* Kotlin's remove(index) is a no-op */
    for (int i = index; i < *n - 1; ++i) a[i] = a[i + 1];
    --(*n);
}

static void ints_insert_at(int *a, int *n, int cap, int index, int value) {
    if (*n >= cap) return;
    if (index < 0) index = 0;
    if (index > *n) index = *n;
    for (int i = *n; i > index; --i) a[i] = a[i - 1];
    a[index] = value;
    ++(*n);
}

static void ints_prepend_all(int *a, int *n, int cap, const int *src, int count) {
    if (*n + count > cap) count = cap - *n;
    if (count <= 0) return;
    for (int i = *n - 1; i >= 0; --i) a[i + count] = a[i];
    for (int i = 0; i < count; ++i) a[i] = src[i];
    *n += count;
}

static void ints_append_all(int *a, int *n, int cap, const int *src, int count) {
    for (int i = 0; i < count && *n < cap; ++i) a[(*n)++] = src[i];
}

static void mat_remove_at(MtPlayField *f, int index) {
    if (index < 0 || index >= f->mat_count) return;
    for (int i = index; i < f->mat_count - 1; ++i) f->mat[i] = f->mat[i + 1];
    --f->mat_count;
}

/* ---- reads --------------------------------------------------------------- */

int mt_field_placed(const MtPlayField *f, int id) {
    for (int i = 0; i < f->mat_count; ++i) {
        if (f->mat[i].card == id) return i;
    }
    return -1;
}

const int *mt_field_pile(const MtPlayField *f, MtBoardSlot slot, int *count) {
    switch (slot.kind) {
        case MT_SLOT_DECK:       *count = f->deck_count;       return f->deck;
        case MT_SLOT_EXTRA_DECK: *count = f->extra_deck_count; return f->extra_deck;
        case MT_SLOT_GRAVEYARD:  *count = f->graveyard_count;  return f->graveyard;
        case MT_SLOT_BANISHED:   *count = f->banished_count;   return f->banished;
        /* A zone is not a pile and never holds one. */
        case MT_SLOT_ZONE:       *count = 0;                   return NULL;
    }
    *count = 0;
    return NULL;
}

int mt_field_under(const MtPlayField *f, int id, int *out, int cap) {
    int at = mt_field_placed(f, id);
    if (at < 0) return 0;
    const MtPlacedCard *p = &f->mat[at];

    int n = 0;
    if (n < cap) out[n++] = p->card;
    for (int i = 0; i < p->beneath_count && n < cap; ++i) out[n++] = p->beneath[i];

    const MtBoardCard *top = &f->instances[p->card];
    for (int i = 0; i < top->material_count && n < cap; ++i) out[n++] = top->materials[i];
    return n;
}

/* ---- setting up ----------------------------------------------------------- */

void mt_field_set_up(MtPlayField *f,
                     const int *main_ids, int main_count,
                     const int *extra_ids, int extra_count) {
    memset(f, 0, sizeof *f);
    f->life_points = 8000;
    f->phase = MT_PHASE_MAIN1;
    f->turn = 1;

    for (int i = 0; i < main_count && f->instance_count < MT_MAX_INSTANCES; ++i) {
        int id = f->instance_count++;
        f->instances[id].instance_id = id;
        f->instances[id].card_id = main_ids[i];
        f->instances[id].position = MT_POS_FACE_UP_ATK;
        if (f->deck_count < MT_MAX_PILE) f->deck[f->deck_count++] = id;
    }
    for (int i = 0; i < extra_count && f->instance_count < MT_MAX_INSTANCES; ++i) {
        int id = f->instance_count++;
        f->instances[id].instance_id = id;
        f->instances[id].card_id = extra_ids[i];
        f->instances[id].position = MT_POS_FACE_UP_ATK;
        if (f->extra_deck_count < MT_MAX_PILE) f->extra_deck[f->extra_deck_count++] = id;
    }
}

/* ---- the deck ------------------------------------------------------------- */

/*
 * Fisher-Yates written out, over the ported XorWow.
 *
 * Both halves matter. The loop is written by hand because a standard library's
 * shuffle may change between versions and platforms; the generator is ported
 * because an identical loop fed by a different PRNG is a shuffle that is
 * correct, deterministic, and a different deal.
 */
static void riffle(int *a, int n, int64_t seed) {
    MtRandom r;
    mt_random_seed(&r, seed);
    for (int i = n - 1; i > 0; --i) {
        int j = mt_random_next_int_bound(&r, i + 1);
        int swap = a[i];
        a[i] = a[j];
        a[j] = swap;
    }
}

void mt_field_shuffle_deck(MtPlayField *f, int64_t seed) {
    riffle(f->deck, f->deck_count, seed);
}

void mt_field_shuffle_extra_deck(MtPlayField *f, int64_t seed) {
    riffle(f->extra_deck, f->extra_deck_count, seed);
}

static void face_up(MtPlayField *f, int id) {
    f->instances[id].position = MT_POS_FACE_UP_ATK;
}

bool mt_field_draw(MtPlayField *f) {
    if (f->deck_count <= 0 || f->hand_count >= MT_MAX_PILE) return false;
    int top = f->deck[0];
    ints_remove_at(f->deck, &f->deck_count, 0);
    face_up(f, top);
    f->hand[f->hand_count++] = top;
    return true;
}

/* ---- onto the mat --------------------------------------------------------- */

static void place(MtPlayField *f, int id, MtMatPoint at, MtCardPosition p) {
    if (f->mat_count >= MT_MAX_MAT) return;
    if (p != MT_POS_KEEP) f->instances[id].position = p;
    MtPlacedCard *slot = &f->mat[f->mat_count++];
    slot->card = id;
    slot->at = mt_mat_point_clamped(at, 0.0f);
    slot->beneath_count = 0;
}

static bool play_from(MtPlayField *f, int *pile, int *count, int index,
                      MtMatPoint at, MtCardPosition p) {
    if (index < 0 || index >= *count) return false;
    if (f->mat_count >= MT_MAX_MAT) return false;
    int id = pile[index];
    ints_remove_at(pile, count, index);
    place(f, id, at, p);
    return true;
}

bool mt_field_play_from_hand(MtPlayField *f, int i, MtMatPoint at, MtCardPosition p) {
    return play_from(f, f->hand, &f->hand_count, i, at, p);
}
bool mt_field_play_from_extra(MtPlayField *f, int i, MtMatPoint at, MtCardPosition p) {
    return play_from(f, f->extra_deck, &f->extra_deck_count, i, at, p);
}
bool mt_field_play_from_graveyard(MtPlayField *f, int i, MtMatPoint at, MtCardPosition p) {
    return play_from(f, f->graveyard, &f->graveyard_count, i, at, p);
}
bool mt_field_play_from_banished(MtPlayField *f, int i, MtMatPoint at, MtCardPosition p) {
    return play_from(f, f->banished, &f->banished_count, i, at, p);
}
bool mt_field_play_from_deck(MtPlayField *f, int i, MtMatPoint at, MtCardPosition p) {
    return play_from(f, f->deck, &f->deck_count, i, at, p);
}

/* ---- moving what is already there ----------------------------------------- */

bool mt_field_move_on_mat(MtPlayField *f, int id, MtMatPoint to, MtCardPosition p) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;

    MtPlacedCard moving = f->mat[at];
    moving.at = mt_mat_point_clamped(to, 0.0f);
    if (p != MT_POS_KEEP) f->instances[id].position = p;

    /* Removed and appended: a card you have just moved is the front-most thing
     * on the table, which is what picking it up and putting it down does. */
    mat_remove_at(f, at);
    f->mat[f->mat_count++] = moving;
    return true;
}

bool mt_field_stack_onto(MtPlayField *f, int id, int onto, MtCardPosition p) {
    if (id == onto) return false;
    int a = mt_field_placed(f, id);
    int b = mt_field_placed(f, onto);
    if (a < 0 || b < 0) return false;

    MtPlacedCard moving = f->mat[a];
    MtPlacedCard target = f->mat[b];

    /* The moved card lands on top carrying whatever was already under it,
     * because that is what putting a pile down on a pile does. */
    int beneath[MT_MAX_BENEATH];
    int n = 0;
    for (int i = 0; i < moving.beneath_count && n < MT_MAX_BENEATH; ++i) beneath[n++] = moving.beneath[i];
    if (n < MT_MAX_BENEATH) beneath[n++] = target.card;
    for (int i = 0; i < target.beneath_count && n < MT_MAX_BENEATH; ++i) beneath[n++] = target.beneath[i];

    if (p != MT_POS_KEEP) f->instances[id].position = p;

    /* Both leave the list, and the survivor is appended - so remove the later
     * index first or the earlier removal shifts the other one out from under. */
    int first = a < b ? a : b, second = a < b ? b : a;
    mat_remove_at(f, second);
    mat_remove_at(f, first);

    MtPlacedCard *slot = &f->mat[f->mat_count++];
    slot->card = moving.card;
    slot->at = target.at;
    slot->beneath_count = n;
    memcpy(slot->beneath, beneath, sizeof(int) * (size_t)n);
    return true;
}

bool mt_field_unstack(MtPlayField *f, int id, MtMatPoint at) {
    int index = mt_field_placed(f, id);
    if (index < 0) return false;
    MtPlacedCard pile = f->mat[index];
    if (pile.beneath_count <= 0) return false;
    if (f->mat_count + 1 > MT_MAX_MAT) return false;

    mat_remove_at(f, index);

    /* The one under it becomes the pile's new top and stays where it was. */
    MtPlacedCard *rest = &f->mat[f->mat_count++];
    rest->card = pile.beneath[0];
    rest->at = pile.at;
    rest->beneath_count = pile.beneath_count - 1;
    for (int i = 0; i < rest->beneath_count; ++i) rest->beneath[i] = pile.beneath[i + 1];

    MtPlacedCard *lifted = &f->mat[f->mat_count++];
    lifted->card = pile.card;
    lifted->at = mt_mat_point_clamped(at, 0.0f);
    lifted->beneath_count = 0;
    return true;
}

bool mt_field_bring_to_front(MtPlayField *f, int id) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    if (at == f->mat_count - 1) return false;   /* already there is not a move */
    MtPlacedCard card = f->mat[at];
    mat_remove_at(f, at);
    f->mat[f->mat_count++] = card;
    return true;
}

/**
 * The field without the card `index` deep in `id`'s stack, and that card.
 *
 * Materials come off with nothing attached to them - a material is a plain card
 * that happens to be under a monster - and a card taken out of the pile keeps
 * whatever was attached to *it*, because that is a different object that
 * happened to be resting in the same place.
 */
static bool lift_from_under(MtPlayField *f, int id, int index, int *out) {
    int at = mt_field_placed(f, id);
    if (at < 0 || index <= 0) return false;
    MtPlacedCard *p = &f->mat[at];

    if (index <= p->beneath_count) {
        *out = p->beneath[index - 1];
        ints_remove_at(p->beneath, &p->beneath_count, index - 1);
        return true;
    }

    MtBoardCard *top = &f->instances[p->card];
    int material = index - p->beneath_count - 1;
    if (material < 0 || material >= top->material_count) return false;
    *out = top->materials[material];
    ints_remove_at(top->materials, &top->material_count, material);
    return true;
}

bool mt_field_take_from_under(MtPlayField *f, int id, int index,
                              MtMatPoint at, MtCardPosition p) {
    /* Index 0 is the top card, which is already on the mat and is simply moved.
     * That keeps a fanned-out stack's first card behaving like every other one
     * rather than being a special case at the call site. */
    /*
     * The position argument is deliberately dropped here, because the Kotlin
     * drops it: `takeFromUnder` at index 0 calls `moveOnMat(id, at)` with no
     * position at all, which means "the way it already was". Passing `p`
     * through instead would turn a slide of the top card into a repose, and
     * only for index 0 - the kind of difference that shows up as one card in a
     * spread behaving unlike its neighbours.
     */
    if (index == 0) return mt_field_move_on_mat(f, id, at, MT_POS_KEEP);
    if (f->mat_count >= MT_MAX_MAT) return false;

    int card = -1;
    if (!lift_from_under(f, id, index, &card)) return false;
    place(f, card, at, p);
    return true;
}

/* ---- which way it faces ---------------------------------------------------- */

static bool repose(MtPlayField *f, int id, MtCardPosition next) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    if (f->instances[id].position == next) return false;
    f->instances[id].position = next;
    return true;
}

bool mt_field_flip(MtPlayField *f, int id) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    MtCardPosition p = f->instances[id].position;
    MtCardPosition next = p;
    switch (p) {
        case MT_POS_FACE_UP_ATK:   next = MT_POS_FACE_DOWN_ATK; break;
        case MT_POS_FACE_UP_DEF:   next = MT_POS_FACE_DOWN_DEF; break;
        case MT_POS_FACE_DOWN_ATK: next = MT_POS_FACE_UP_ATK;   break;
        case MT_POS_FACE_DOWN_DEF: next = MT_POS_FACE_UP_DEF;   break;
        default: return false;
    }
    return repose(f, id, next);
}

bool mt_field_rotate(MtPlayField *f, int id) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    MtCardPosition p = f->instances[id].position;
    MtCardPosition next = p;
    switch (p) {
        case MT_POS_FACE_UP_ATK:   next = MT_POS_FACE_UP_DEF;   break;
        case MT_POS_FACE_UP_DEF:   next = MT_POS_FACE_UP_ATK;   break;
        case MT_POS_FACE_DOWN_ATK: next = MT_POS_FACE_DOWN_DEF; break;
        case MT_POS_FACE_DOWN_DEF: next = MT_POS_FACE_DOWN_ATK; break;
        default: return false;
    }
    return repose(f, id, next);
}

bool mt_field_set_position(MtPlayField *f, int id, MtCardPosition p) {
    return repose(f, id, p);
}

/* ---- off the mat ----------------------------------------------------------- */

/*
 * Everything that leaves the mat takes its pile with it and arrives clean.
 *
 * Counters and materials belong to a card *while it is in play*; a card in the
 * graveyard has neither, and carrying them along would quietly resurrect them
 * if it came back.
 */
static int lift(MtPlayField *f, int id, int *out, int cap) {
    int at = mt_field_placed(f, id);
    if (at < 0) return -1;
    MtPlacedCard p = f->mat[at];

    int pile[1 + MT_MAX_BENEATH];
    int pile_n = 0;
    pile[pile_n++] = p.card;
    for (int i = 0; i < p.beneath_count; ++i) pile[pile_n++] = p.beneath[i];

    int n = 0;
    for (int i = 0; i < pile_n; ++i) {
        MtBoardCard *card = &f->instances[pile[i]];
        int materials[MT_MAX_MATERIALS];
        int material_n = card->material_count;
        memcpy(materials, card->materials, sizeof(int) * (size_t)material_n);

        card->material_count = 0;
        card->counters = 0;
        if (n < cap) out[n++] = pile[i];
        for (int m = 0; m < material_n && n < cap; ++m) out[n++] = materials[m];
    }

    mat_remove_at(f, at);
    return n;
}

bool mt_field_to_graveyard(MtPlayField *f, int id) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    for (int i = 0; i < n; ++i) face_up(f, cards[i]);
    ints_prepend_all(f->graveyard, &f->graveyard_count, MT_MAX_PILE, cards, n);
    return true;
}

bool mt_field_to_banish(MtPlayField *f, int id, bool face_down) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    MtCardPosition p = face_down ? MT_POS_FACE_DOWN_ATK : MT_POS_FACE_UP_ATK;
    for (int i = 0; i < n; ++i) f->instances[cards[i]].position = p;
    ints_prepend_all(f->banished, &f->banished_count, MT_MAX_PILE, cards, n);
    return true;
}

bool mt_field_to_hand(MtPlayField *f, int id, int at) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    for (int i = 0; i < n; ++i) face_up(f, cards[i]);

    int where = (at < 0) ? f->hand_count : at;
    if (where < 0) where = 0;
    if (where > f->hand_count) where = f->hand_count;
    for (int i = 0; i < n; ++i) {
        ints_insert_at(f->hand, &f->hand_count, MT_MAX_PILE, where + i, cards[i]);
    }
    return true;
}

bool mt_field_to_deck_top(MtPlayField *f, int id) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    for (int i = 0; i < n; ++i) face_up(f, cards[i]);
    ints_prepend_all(f->deck, &f->deck_count, MT_MAX_PILE, cards, n);
    return true;
}

bool mt_field_to_deck_bottom(MtPlayField *f, int id) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    for (int i = 0; i < n; ++i) face_up(f, cards[i]);
    ints_append_all(f->deck, &f->deck_count, MT_MAX_PILE, cards, n);
    return true;
}

bool mt_field_to_extra_deck(MtPlayField *f, int id) {
    int cards[1 + MT_MAX_BENEATH + MT_MAX_MATERIALS * 2];
    int n = lift(f, id, cards, (int)(sizeof cards / sizeof cards[0]));
    if (n < 0) return false;
    for (int i = 0; i < n; ++i) face_up(f, cards[i]);
    ints_prepend_all(f->extra_deck, &f->extra_deck_count, MT_MAX_PILE, cards, n);
    return true;
}

/* ---- out of the hand -------------------------------------------------------- */

static bool hand_to(MtPlayField *f, int index, int *pile, int *count, bool prepend) {
    if (index < 0 || index >= f->hand_count) return false;
    if (*count >= MT_MAX_PILE) return false;
    int card = f->hand[index];
    ints_remove_at(f->hand, &f->hand_count, index);
    if (prepend) ints_insert_at(pile, count, MT_MAX_PILE, 0, card);
    else         pile[(*count)++] = card;
    return true;
}

bool mt_field_hand_to_deck_top(MtPlayField *f, int i) {
    return hand_to(f, i, f->deck, &f->deck_count, true);
}
bool mt_field_hand_to_deck_bottom(MtPlayField *f, int i) {
    return hand_to(f, i, f->deck, &f->deck_count, false);
}
bool mt_field_hand_to_graveyard(MtPlayField *f, int i) {
    return hand_to(f, i, f->graveyard, &f->graveyard_count, true);
}
bool mt_field_hand_to_banish(MtPlayField *f, int i) {
    return hand_to(f, i, f->banished, &f->banished_count, true);
}

bool mt_field_reorder_hand(MtPlayField *f, int from, int to) {
    if (from < 0 || from >= f->hand_count) return false;
    if (to < 0 || to > f->hand_count) return false;
    /* Both no-ops out of one comparison: back in its own place, and into the
     * gap immediately after itself, are the same hand. */
    if (to == from || to == from + 1) return false;

    int card = f->hand[from];
    ints_remove_at(f->hand, &f->hand_count, from);
    int at = (to > from) ? to - 1 : to;
    ints_insert_at(f->hand, &f->hand_count, MT_MAX_PILE, at, card);
    return true;
}

/* ---- counters, materials, life, phases --------------------------------------- */

bool mt_field_add_counter(MtPlayField *f, int id, int delta) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    int counters = f->instances[id].counters + delta;
    if (counters < 0) counters = 0;
    if (counters == f->instances[id].counters) return false;
    f->instances[id].counters = counters;
    return true;
}

bool mt_field_attach_as_material(MtPlayField *f, int id, int onto) {
    if (id == onto) return false;
    int a = mt_field_placed(f, id);
    int b = mt_field_placed(f, onto);
    if (a < 0 || b < 0) return false;

    /* A material rides *with* its monster and leaves with it, where a stack is
     * two cards in the same place. The whole moving pile becomes material, and
     * arrives with nothing of its own attached. */
    MtPlacedCard moving = f->mat[a];
    int arriving[1 + MT_MAX_BENEATH];
    int n = 0;
    arriving[n++] = moving.card;
    for (int i = 0; i < moving.beneath_count; ++i) arriving[n++] = moving.beneath[i];
    for (int i = 0; i < n; ++i) {
        f->instances[arriving[i]].material_count = 0;
        f->instances[arriving[i]].counters = 0;
    }

    mat_remove_at(f, a);
    int target = mt_field_placed(f, onto);   /* re-found: the removal shifted it */
    if (target < 0) return false;

    MtBoardCard *host = &f->instances[f->mat[target].card];
    for (int i = 0; i < n && host->material_count < MT_MAX_MATERIALS; ++i) {
        host->materials[host->material_count++] = arriving[i];
    }
    return true;
}

bool mt_field_detach_material(MtPlayField *f, int id) {
    int at = mt_field_placed(f, id);
    if (at < 0) return false;
    MtBoardCard *card = &f->instances[f->mat[at].card];
    if (card->material_count <= 0) return false;

    int material = card->materials[0];
    ints_remove_at(card->materials, &card->material_count, 0);
    face_up(f, material);
    ints_insert_at(f->graveyard, &f->graveyard_count, MT_MAX_PILE, 0, material);
    return true;
}

void mt_field_adjust_life(MtPlayField *f, int delta) {
    int life = f->life_points + delta;
    f->life_points = life < 0 ? 0 : life;
}

void mt_field_next_phase(MtPlayField *f) {
    f->phase = mt_phase_next(f->phase);
}

void mt_field_end_turn(MtPlayField *f) {
    f->phase = MT_PHASE_DRAW;
    f->turn += 1;
}
