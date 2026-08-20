/*
 * The vocabulary the table is described in.
 *
 * This is the C form of `core/board/BoardCard.kt` and the small value types
 * around it. It is deliberately dependency-free: nothing in `src/core/` may
 * include a libctru header, because the whole of it is compiled a second time
 * by the host `gcc` in `3ds/test/` and asserted against golden vectors exported
 * from the Kotlin. A `#include <3ds.h>` here is a conformance suite that stops
 * building on anything but a 3DS.
 */
#ifndef MT_TYPES_H
#define MT_TYPES_H

#include <stdbool.h>

/* ---- geometry ---------------------------------------------------------- */

typedef struct {
    float x, y;
} MtVec2;

/** A rectangle on the table, in pixels from the surface's top-left corner. */
typedef struct {
    float left, top, width, height;
} MtSlot;

static inline float mt_slot_right(MtSlot s)   { return s.left + s.width; }
static inline float mt_slot_bottom(MtSlot s)  { return s.top + s.height; }
static inline float mt_slot_centre_x(MtSlot s) { return s.left + s.width * 0.5f; }
static inline float mt_slot_centre_y(MtSlot s) { return s.top + s.height * 0.5f; }

/** Inclusive on all four edges, matching Kotlin's `x in left..right`. */
static inline bool mt_slot_contains(MtSlot s, float x, float y) {
    return x >= s.left && x <= mt_slot_right(s)
        && y >= s.top  && y <= mt_slot_bottom(s);
}

/** The same rectangle grown by `by` on every side. */
static inline MtSlot mt_slot_inflated(MtSlot s, float by) {
    MtSlot out;
    out.left   = s.left - by;
    out.top    = s.top - by;
    out.width  = s.width + by * 2.0f;
    out.height = s.height + by * 2.0f;
    return out;
}

/*
 * Where something sits on the mat, as a fraction of the mat's own size.
 *
 * Not pixels — the same argument `PlayField.kt` makes, and it is worth more
 * here than it was there. On the 3DS the board is drawn on two surfaces at once
 * (a perspective view on the top screen, an orthographic map on the bottom),
 * and a fraction is the only form that means the same thing to both. The
 * bottom screen's hit test is then a multiplication rather than an unprojection.
 */
typedef struct {
    float x, y;
} MtMatPoint;

MtMatPoint mt_mat_point_clamped(MtMatPoint p, float margin);

/* ---- what a card is ---------------------------------------------------- */

typedef enum {
    MT_POS_FACE_UP_ATK = 0,
    MT_POS_FACE_UP_DEF,
    MT_POS_FACE_DOWN_DEF,
    /** Rare, but real cards produce it; the table should not argue. */
    MT_POS_FACE_DOWN_ATK,
    MT_POS_COUNT
} MtCardPosition;

static inline bool mt_position_face_up(MtCardPosition p) {
    return p == MT_POS_FACE_UP_ATK || p == MT_POS_FACE_UP_DEF;
}

static inline bool mt_position_turned(MtCardPosition p) {
    return p == MT_POS_FACE_UP_DEF || p == MT_POS_FACE_DOWN_DEF;
}

typedef enum {
    MT_PHASE_DRAW = 0,
    MT_PHASE_STANDBY,
    MT_PHASE_MAIN1,
    MT_PHASE_BATTLE,
    MT_PHASE_MAIN2,
    MT_PHASE_END,
    MT_PHASE_COUNT
} MtDuelPhase;

static inline MtDuelPhase mt_phase_next(MtDuelPhase p) {
    return (MtDuelPhase)((p + 1) % MT_PHASE_COUNT);
}

const char *mt_phase_label(MtDuelPhase p);

/* ---- where a card may sit ---------------------------------------------- */

/*
 * `FieldZone` is a sealed interface in Kotlin; here it is a tag and an index.
 * `index` is unused for MT_ZONE_FIELD_SPELL and must be written as 0 so that
 * two field-spell zones compare equal by memcmp.
 */
typedef enum {
    MT_ZONE_MONSTER = 0,       /* index 0..4 */
    MT_ZONE_EXTRA_MONSTER,     /* index 0..1 */
    MT_ZONE_SPELL_TRAP,        /* index 0..4 */
    MT_ZONE_FIELD_SPELL        /* index always 0 */
} MtFieldZoneKind;

typedef struct {
    MtFieldZoneKind kind;
    int index;
} MtFieldZone;

typedef enum {
    MT_SLOT_ZONE = 0,
    MT_SLOT_DECK,
    MT_SLOT_EXTRA_DECK,
    MT_SLOT_GRAVEYARD,
    MT_SLOT_BANISHED
} MtBoardSlotKind;

typedef struct {
    MtBoardSlotKind kind;
    /** Meaningful only when `kind == MT_SLOT_ZONE`; zeroed otherwise. */
    MtFieldZone zone;
} MtBoardSlot;

MtFieldZone mt_zone(MtFieldZoneKind kind, int index);
MtBoardSlot mt_slot_zone(MtFieldZone zone);
MtBoardSlot mt_slot_pile(MtBoardSlotKind kind);
bool mt_board_slot_eq(MtBoardSlot a, MtBoardSlot b);

/** True for the zones a set monster lies sideways in. */
static inline bool mt_zone_is_monster(MtFieldZone z) {
    return z.kind == MT_ZONE_MONSTER || z.kind == MT_ZONE_EXTRA_MONSTER;
}

/* ---- one physical card ------------------------------------------------- */

/*
 * Xyz materials ride under the card that owns them. The Kotlin models this as a
 * nested list; a fixed cap is used here because the alternative on a console
 * with no virtual memory is an allocation in the middle of a drag. Five is the
 * most any real card attaches without an effect that also changes zones.
 */
#define MT_MAX_MATERIALS 8

typedef struct {
    int instance_id;
    int card_id;              /* Konami passcode */
    MtCardPosition position;
    int counters;
    int material_count;
    int materials[MT_MAX_MATERIALS];   /* instance ids, bottom-most last */
} MtBoardCard;

#endif /* MT_TYPES_H */
