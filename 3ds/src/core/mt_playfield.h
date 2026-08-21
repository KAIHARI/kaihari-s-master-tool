/*
 * A duel table you can put cards down on anywhere - the C form of
 * `core/board/PlayField.kt`.
 *
 * ## Why this one is mutable when the Kotlin is not
 *
 * The Kotlin returns a new `PlayField` per move and gets undo for free: a list
 * of immutable values costs nothing to keep. That trade is a good one on a JVM
 * with a generational collector and a bad one on a 268MHz ARM11 with no virtual
 * memory, where every move would allocate three lists in the middle of a drag.
 *
 * So the shape changes and the *contract* does not. Every operation still
 * answers "was that physically possible", still refuses rather than guessing,
 * and still leaves the field untouched when it refuses - which is the property
 * the Kotlin got from immutability and this has to get from discipline. Undo is
 * a memcpy of the whole struct into a ring, which is about 15KB a level.
 *
 * ## Cards live in one array and everything else is an index
 *
 * `instances[i].instance_id == i`, always. The piles and the mat hold indices
 * into it. That is the C shape of the same identity the Kotlin carries in an
 * object reference, and it is what makes `rebase` and `stillHolds` possible at
 * all: a gesture holds a card, not a place, and a place goes stale the moment
 * the other hand commits.
 */
#ifndef MT_PLAYFIELD_H
#define MT_PLAYFIELD_H

#include <stdbool.h>
#include <stdint.h>

#include "mt_types.h"

/* 60 main + 15 extra + 15 side, with room for tokens later. */
#define MT_MAX_INSTANCES 160
#define MT_MAX_MAT        64
#define MT_MAX_PILE       96
#define MT_MAX_BENEATH    24

typedef struct {
    /** Instance index of the top card - the one you can see. */
    int card;
    MtMatPoint at;
    /** The pile under it, nearest first. */
    int beneath[MT_MAX_BENEATH];
    int beneath_count;
} MtPlacedCard;

typedef struct {
    MtBoardCard instances[MT_MAX_INSTANCES];
    int instance_count;

    /** Back to front. The last one is the top one. */
    MtPlacedCard mat[MT_MAX_MAT];
    int mat_count;

    int hand[MT_MAX_PILE];       int hand_count;
    /** Index 0 is the top. */
    int deck[MT_MAX_PILE];       int deck_count;
    int extra_deck[MT_MAX_PILE]; int extra_deck_count;
    /** Most recent on top, the way a real graveyard reads. */
    int graveyard[MT_MAX_PILE];  int graveyard_count;
    int banished[MT_MAX_PILE];   int banished_count;

    int life_points;
    MtDuelPhase phase;
    int turn;
} MtPlayField;

/* ---- setting up -------------------------------------------------------- */

/** A deck, an empty mat, and eight thousand life points. */
void mt_field_set_up(MtPlayField *f,
                     const int *main_ids, int main_count,
                     const int *extra_ids, int extra_count);

/* ---- reads ------------------------------------------------------------- */

/** The mat index of the placed card whose *top* card is `id`, or -1. */
int mt_field_placed(const MtPlayField *f, int id);

/** The pile a slot names, for anything that has to treat all four alike. */
const int *mt_field_pile(const MtPlayField *f, MtBoardSlot slot, int *count);

/**
 * Everything `id` has under it, top card first, into `out`.
 *
 * The pile it is the top of, and then whatever is attached to it as material.
 * Two different relationships - one is *resting on*, the other is *part of* -
 * listed together because a hand reaching into a stack does not distinguish
 * them. Index 0 is `id` itself.
 */
int mt_field_under(const MtPlayField *f, int id, int *out, int cap);

/* ---- the deck ---------------------------------------------------------- */

void mt_field_shuffle_deck(MtPlayField *f, int64_t seed);
void mt_field_shuffle_extra_deck(MtPlayField *f, int64_t seed);
bool mt_field_draw(MtPlayField *f);

/* ---- onto the mat ------------------------------------------------------- */

bool mt_field_play_from_hand(MtPlayField *f, int index, MtMatPoint at, MtCardPosition p);
bool mt_field_play_from_extra(MtPlayField *f, int index, MtMatPoint at, MtCardPosition p);
bool mt_field_play_from_graveyard(MtPlayField *f, int index, MtMatPoint at, MtCardPosition p);
bool mt_field_play_from_banished(MtPlayField *f, int index, MtMatPoint at, MtCardPosition p);
bool mt_field_play_from_deck(MtPlayField *f, int index, MtMatPoint at, MtCardPosition p);

/* ---- moving what is already there --------------------------------------- */

/*
 * `position` is how it is lying when it gets there, and MT_POS_KEEP means "the
 * way it already was", which is what a plain slide across the felt is.
 *
 * That distinction is not decoration. `moveOnMat` could not be told a position
 * for two releases, and that is the whole of "a card set onto the field comes
 * out vertical": SetPosition solved the landing correctly, the card turned over
 * in the air where you could see it, and then the commit called this - which
 * moved the card and silently dropped the answer.
 */
#define MT_POS_KEEP ((MtCardPosition)MT_POS_COUNT)

bool mt_field_move_on_mat(MtPlayField *f, int id, MtMatPoint to, MtCardPosition p);
bool mt_field_stack_onto(MtPlayField *f, int id, int onto, MtCardPosition p);
bool mt_field_unstack(MtPlayField *f, int id, MtMatPoint at);
bool mt_field_bring_to_front(MtPlayField *f, int id);

/** Pulls the card `index` deep in `id`'s stack out onto the mat. */
bool mt_field_take_from_under(MtPlayField *f, int id, int index,
                              MtMatPoint at, MtCardPosition p);

/* ---- which way it faces -------------------------------------------------- */

bool mt_field_flip(MtPlayField *f, int id);
bool mt_field_rotate(MtPlayField *f, int id);
bool mt_field_set_position(MtPlayField *f, int id, MtCardPosition p);

/* ---- off the mat --------------------------------------------------------- */

bool mt_field_to_graveyard(MtPlayField *f, int id);
bool mt_field_to_banish(MtPlayField *f, int id, bool face_down);
/** `at` < 0 means the end, which is what every caller that is not a drag wants. */
bool mt_field_to_hand(MtPlayField *f, int id, int at);
bool mt_field_to_deck_top(MtPlayField *f, int id);
bool mt_field_to_deck_bottom(MtPlayField *f, int id);
bool mt_field_to_extra_deck(MtPlayField *f, int id);

/* ---- out of the hand ----------------------------------------------------- */

bool mt_field_hand_to_deck_top(MtPlayField *f, int index);
bool mt_field_hand_to_deck_bottom(MtPlayField *f, int index);
bool mt_field_hand_to_graveyard(MtPlayField *f, int index);
bool mt_field_hand_to_banish(MtPlayField *f, int index);

/**
 * Moves the card at `from` to the gap at `to`, so a hand can be arranged.
 *
 * `to` counts gaps in the hand *as it stands*, before the card leaves it, which
 * is what a finger is pointing at - and it makes both no-ops fall out of one
 * comparison: dropping a card back in its own place and dropping it in the gap
 * immediately after itself are the same hand.
 */
bool mt_field_reorder_hand(MtPlayField *f, int from, int to);

/* ---- counters, materials, life, phases ----------------------------------- */

bool mt_field_add_counter(MtPlayField *f, int id, int delta);
bool mt_field_attach_as_material(MtPlayField *f, int id, int onto);
bool mt_field_detach_material(MtPlayField *f, int id);
void mt_field_adjust_life(MtPlayField *f, int delta);
void mt_field_next_phase(MtPlayField *f);
void mt_field_end_turn(MtPlayField *f);

#endif /* MT_PLAYFIELD_H */
