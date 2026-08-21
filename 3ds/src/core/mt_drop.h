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

#include "mt_board_layout.h"
#include "mt_handfan.h"
#include "mt_playfield.h"
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

/* ---- where a dragged card would land ------------------------------------ */

/*
 * Two things make this harder than "what is under the pointer", and both are
 * the difference between a table that feels smart and one that feels twitchy.
 *
 * **Precedence.** A pointer over a card that is itself sitting in a zone could
 * mean stack-on-that-card or snap-into-that-zone, and the answer has to be the
 * same every time. The order is deliberate: the piles win over everything
 * because they are unambiguous destinations you had to travel to; then a card
 * under the pointer, because aiming at a specific card is a more particular act
 * than aiming at a region; then a zone; then the bare mat.
 *
 * **Hysteresis.** Every threshold is a pair, not a number: a target is harder
 * to enter than to leave. Without that, a pointer resting on a boundary
 * flickers between two intents several times a second, the indicator strobes,
 * and which one you get on release is luck.
 */

/**
 * The gap in an open spread a card came out of, and whether it has ever left.
 *
 * `at` alone is not enough to mean "put it back". A spread is laid out over
 * `layout.field` - the seven-by-three grid itself - and its cards are drawn
 * lifted, so the felt footprint of the slot a card came out of lands a fifth of
 * a card from a monster zone's centre. "Near where it started means put it
 * back" therefore also says "near that monster zone means put it back", and the
 * search that was supposed to end with the card on the board ends with it back
 * in the deck.
 *
 * `departed` is the missing half: a put-back is a *change of mind*, so the card
 * has to have been somewhere first. The latch arms once and stays armed, because
 * one that could un-arm would flicker exactly where the two catchments overlap.
 */
typedef struct {
    MtMatPoint at;
    bool departed;
} MtFanHome;

/** How far clear of its own slot a card must get before the latch arms. */
#define MT_FAN_DEPARTURE 1.10f

/** This home, having seen the card at `landing`. Idempotent once armed. */
MtFanHome mt_fan_home_seeing(MtFanHome home, MtMatPoint landing,
                             const MtBoardLayout *layout);

/** The mat's own coordinates against the pixels the layout is drawn in. */
void mt_layout_to_pixels(const MtBoardLayout *l, MtMatPoint p, float *x, float *y);
MtMatPoint mt_layout_to_mat(const MtBoardLayout *l, float x, float y);

/**
 * Where a card would land, given where the pointer is.
 *
 * @param dragged  the instance being dragged, which cannot land on itself; -1 for none
 * @param previous what was decided last frame, which is what makes it sticky; NULL for none
 * @param attaching true when the gesture means "tuck under" rather than "put on
 *                 top" - the same position, a different intention, and the only
 *                 one the geometry cannot tell you
 * @param home     where in an open spread this card was sitting, or NULL
 * @param hand     the hand *as it is drawn*, so the gap the pointer is over is
 *                 measured against the row the user can actually see
 */
MtDropIntent mt_drop_resolve(MtMatPoint point,
                             int dragged,
                             const MtPlayField *field,
                             const MtBoardLayout *layout,
                             const MtDropIntent *previous,
                             bool attaching,
                             const MtFanHome *home,
                             const MtHandRow *hand,
                             float hand_step);

#endif /* MT_DROP_H */
