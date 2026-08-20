/*
 * What letting go right now would do — the C form of `core/board/DropIntent.kt`.
 *
 * Nothing here is a gesture or a pixel: it is a pure question about where a
 * pointer is and what is already on the mat. That is what lets the indicator
 * the user sees be the *same value* the release acts on, so the table cannot
 * promise one thing and do another.
 *
 * On the 3DS the "where a pointer is" half gets simpler and the rest is
 * untouched. A stylus on the bottom screen's orthographic map unprojects by
 * dividing, so `MtMatPoint` arrives exact; the tablet's whole `StagePlane.raise`
 * apparatus, and the drawn-versus-touched offset it exists to correct, has no
 * counterpart here.
 */
#ifndef MT_DROP_H
#define MT_DROP_H

#include "mt_types.h"

typedef enum {
    MT_DROP_FREE = 0,      /* put it down exactly here */
    MT_DROP_ZONE,          /* pulled into one of the classic zones */
    MT_DROP_STACK,         /* onto the card already there, making a pile */
    MT_DROP_ATTACH,        /* tucked under it, as Xyz material */
    MT_DROP_HAND,          /* into your hand, at a particular gap in it */
    MT_DROP_GRAVEYARD,
    MT_DROP_BANISH,
    MT_DROP_DECK,
    MT_DROP_EXTRA_DECK,
    MT_DROP_CANCEL         /* nothing sensible; it goes back where it came from */
} MtDropKind;

typedef struct {
    MtDropKind kind;

    /** MT_DROP_FREE and MT_DROP_ZONE: where on the mat it lands. */
    MtMatPoint at;

    /** MT_DROP_ZONE: which zone. Meaningful only for that kind. */
    MtBoardSlot slot;

    /**
     * MT_DROP_STACK and MT_DROP_ATTACH: the instance id underneath.
     * MT_DROP_HAND: the *gap* index — 0 is before everything, hand_count is
     * after everything. A gap rather than a card, because arranging your hand
     * is the one thing every player does with one.
     */
    int target;
} MtDropIntent;

/** What the indicator should say, in the fewest words that are still true. */
const char *mt_drop_label(MtDropIntent intent);

static inline MtDropIntent mt_drop_simple(MtDropKind kind) {
    MtDropIntent d;
    d.kind = kind;
    d.at.x = 0.0f;
    d.at.y = 0.0f;
    d.slot = mt_slot_pile(MT_SLOT_DECK);
    d.target = 0;
    return d;
}

static inline MtDropIntent mt_drop_zone(MtBoardSlot slot, MtMatPoint at) {
    MtDropIntent d = mt_drop_simple(MT_DROP_ZONE);
    d.slot = slot;
    d.at = at;
    return d;
}

static inline MtDropIntent mt_drop_free(MtMatPoint at) {
    MtDropIntent d = mt_drop_simple(MT_DROP_FREE);
    d.at = at;
    return d;
}

/* ---- which way up a card being put down is lying ----------------------- */

/**
 * The C form of `core/board/SetPosition.kt`.
 *
 * Setting a monster and setting a spell are the same motion of the hand and two
 * different results, and nobody twists their wrist to say which — the zone the
 * card lands in says so out loud. So the destination gets a vote, and a
 * deliberate twist always beats it.
 *
 * @param face_down whether the card was turned over in the air
 * @param turned    whether the gesture twisted it an odd number of quarter turns
 * @param intent    where letting go would put it, or NULL when nothing is aimed at
 * @param monster   whether the card itself is a monster; MT_MONSTER_UNKNOWN for a
 *                  card the index has never heard of, which resolves the way a
 *                  spell does rather than by guessing
 */
typedef enum {
    MT_MONSTER_NO = 0,
    MT_MONSTER_YES = 1,
    MT_MONSTER_UNKNOWN = 2
} MtMonsterHint;

MtCardPosition mt_set_position(bool face_down,
                               bool turned,
                               const MtDropIntent *intent,
                               MtMonsterHint monster);

#endif /* MT_DROP_H */
