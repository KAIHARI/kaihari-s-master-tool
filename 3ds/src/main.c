/*
 * kai's master tool on the 3DS - the fishbowl.
 *
 * The top screen is the stage, in real geometry and real stereo: a card is a
 * box with a thickness, the camera is a projection matrix, and the two eyes
 * differ by an interaxial the user sets with the 3D slider.
 *
 * The bottom screen is a **control surface, not a view**. It never draws
 * perspective. `MtMatPoint` is a fraction of the mat, so the map is a
 * multiplication rather than an unprojection - and everything the tablet needs
 * to reconcile the place a card is *drawn* with the place it is *touched*
 * (`StagePlane.raise`, `CarryHeight`, `MatInput.handQuad`) has no counterpart
 * here, because on an orthographic map that gap is zero.
 *
 * The status bar is *declared to the fitter* rather than drawn over the top of
 * it. That is the rule `DeckFit` and `PoolDock` both learned the hard way and
 * CLAUDE.md states twice: chrome a layout spends without telling the solver is
 * space the cards were promised and did not get.
 *
 * ## This file decides nothing
 *
 * Where a card lands is `mt_drop_resolve`'s answer, what that does to the board
 * is `mt_drop_commit`'s, where a hand card is drawn is `mt_hand_centre_of`'s,
 * and how it gets there is `mt_spring_step`'s. All four are in `src/core/`,
 * held to `:core` by the golden vectors, and none of them is re-derived here.
 *
 * That is not tidiness. This file *did* re-derive the hand - a different step
 * cap, left-aligned instead of centred - and re-derive the drop as a rectangle
 * test, which is the "four readings, four reconstructions" bug `MtHandRow`
 * exists to prevent and the promise-one-thing-do-another bug `MtDropIntent`
 * exists to prevent, both reintroduced in the port's own app layer where the
 * conformance suite structurally cannot see them. `tools/check-app-layer.sh` is
 * the tripwire against doing it again.
 */
#include <3ds.h>
#include <citro2d.h>
#include <citro3d.h>

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "core/mt_board_layout.h"
#include "core/mt_drop.h"
#include "core/mt_handfan.h"
#include "core/mt_input.h"
#include "core/mt_playfield.h"
#include "core/mt_spring.h"
#include "core/mt_types.h"
#include "core/mt_ydk.h"
#include "gfx/mt_gfx.h"

#define STATUS_H 14.0f
#define DECK_DIR "sdmc:/3ds/kaimastertool/decks"

#define INK        C2D_Color32(0xFF, 0xFF, 0xFF, 0xFF)
#define TRUE_BLACK C2D_Color32(0x00, 0x00, 0x00, 0xFF)
#define DIM        C2D_Color32(0x4A, 0x4A, 0x50, 0xFF)
#define FELT       C2D_Color32(0x1E, 0x1E, 0x24, 0xFF)
#define FOIL_A     C2D_Color32(0x5A, 0xD2, 0xFF, 0xFF)
#define BACK       C2D_Color32(0x21, 0x2A, 0x42, 0xFF)
#define BAR        C2D_Color32(0x12, 0x12, 0x16, 0xFF)
#define SCRIM      C2D_Color32(0x00, 0x00, 0x00, 0xE8)

/** How far off the felt a carried card is drawn, in card widths. */
#define CARRY_LIFT 0.45f

/*
 * Undo is a ring of whole fields.
 *
 * `PlayField` on the tablet is immutable, so a list of them *is* the undo
 * stack and costs nothing to keep. Here the field is one mutable struct, and
 * the equivalent is to copy it before every move that changes something. About
 * 17KB a level, sixteen levels, which is a quarter of a megabyte against 124.
 */
#define UNDO_DEPTH 16

typedef struct {
    MtPlayField field;
    MtYdkDocument doc;
    char *text;

    MtPlayField undo[UNDO_DEPTH];
    int undo_count;

    int64_t seed;
} Table;

/** Remembers the field so the next move can be taken back. */
static void checkpoint(Table *t) {
    if (t->undo_count == UNDO_DEPTH) {
        for (int i = 0; i < UNDO_DEPTH - 1; ++i) t->undo[i] = t->undo[i + 1];
        --t->undo_count;
    }
    t->undo[t->undo_count++] = t->field;
}

static bool undo(Table *t) {
    if (t->undo_count <= 0) return false;
    t->field = t->undo[--t->undo_count];
    return true;
}

/* ---- motion ------------------------------------------------------------- */

/*
 * One spring per instance, per axis, stepped once a frame.
 *
 * The sanctioned pattern from `EasterEgg.kt` and `ui/play/StageCard.kt`: bulk
 * state in a plain array, keyed by instance id, read at draw time. Nothing here
 * allocates and nothing is per-card state in a tree.
 *
 * A **carried** card does not spring horizontally. A drag is direct
 * manipulation and a card that lagged the stylus would feel like drag on the
 * card rather than mass in it; what springs is the lift, and then the landing,
 * which is where the weight belongs. `live` is what stops a card that has just
 * arrived on the mat flying in from the corner it was zeroed at.
 */
typedef struct {
    MtSpringValue x, y, lift;
    bool live;
} CardMotion;

static CardMotion motion[MT_MAX_INSTANCES];

static void motion_forget_all(void) {
    memset(motion, 0, sizeof motion);
}

/** Puts a card exactly where it is asked, with no motion left over. */
static void motion_snap(int id, MtMatPoint at, float lift) {
    if (id < 0 || id >= MT_MAX_INSTANCES) return;
    CardMotion *m = &motion[id];
    m->x.value = at.x;  m->x.velocity = 0.0f;
    m->y.value = at.y;  m->y.velocity = 0.0f;
    m->lift.value = lift; m->lift.velocity = 0.0f;
    m->live = true;
}

static void motion_towards(int id, MtMatPoint at, float lift, bool carried, float dt) {
    if (id < 0 || id >= MT_MAX_INSTANCES) return;
    CardMotion *m = &motion[id];
    if (!m->live) { motion_snap(id, at, lift); return; }

    if (carried) {
        m->x.value = at.x;  m->x.velocity = 0.0f;
        m->y.value = at.y;  m->y.velocity = 0.0f;
    } else {
        MtSpringSpec snappy = mt_spring_snappy();
        m->x = mt_spring_step(m->x, at.x, snappy, dt);
        m->y = mt_spring_step(m->y, at.y, snappy, dt);
    }
    m->lift = mt_spring_step(m->lift, lift, mt_spring_bouncy(), dt);
}

static MtMatPoint motion_point(int id, MtMatPoint fallback) {
    if (id < 0 || id >= MT_MAX_INSTANCES || !motion[id].live) return fallback;
    MtMatPoint p;
    p.x = motion[id].x.value;
    p.y = motion[id].y.value;
    return p;
}

static float motion_lift(int id) {
    if (id < 0 || id >= MT_MAX_INSTANCES || !motion[id].live) return 0.0f;
    return motion[id].lift.value;
}

/* ---- the drag ----------------------------------------------------------- */

/*
 * One stylus, one gesture, and it belongs to whatever it landed on.
 *
 * The same split `MatDesk` makes with ten lanes, made here with one, because a
 * stylus cannot be two fingers. What the tablet's arbiter buys that this does
 * not need is arbitration; what it buys that this *does* need is that the
 * decision is made once, on the press, and cannot change under the gesture.
 */
typedef struct {
    bool active;
    MtDragOrigin from;
    /** The instance being carried - always known, from any origin. */
    int carried;
    /** Where the stylus is, in the mat's own fractions. */
    MtMatPoint at;

    /** What letting go right now would do. Shown, then committed - one value. */
    MtDropIntent intent;
    bool has_intent;

    /** Where it came out of, for the put-back latch. Piles only, for now. */
    MtFanHome home;
    bool has_home;

    /**
     * The gap the hand is holding open, or -1.
     *
     * Carried across frames on purpose: the row is drawn with this gap open,
     * and `mt_hand_insert_at` counts drawn cards, so the gap it names is the
     * gap already open. That is the fixed point `MtHandRow` exists to have -
     * a row that re-asked and got a different answer would flicker every frame.
     */
    int opening;
} Drag;

/** Which instance a drag is carrying, whatever it came out of. */
static int carried_instance(const MtPlayField *f, MtDragOrigin from) {
    switch (from.kind) {
        case MT_FROM_HAND:
            return (from.index >= 0 && from.index < f->hand_count)
                ? f->hand[from.index] : -1;
        case MT_FROM_MAT:
            return from.id;
        case MT_FROM_PILE: {
            int count = 0;
            const int *pile = mt_field_pile(f, from.pile, &count);
            return (pile != NULL && from.index >= 0 && from.index < count)
                ? pile[from.index] : -1;
        }
        case MT_FROM_BURIED:
        default:
            return -1;
    }
}

/* ---- text --------------------------------------------------------------- */

typedef struct { C2D_TextBuf buf; C2D_Text text; } Label;

static void label_set(Label *l, const char *s) {
    C2D_TextBufClear(l->buf);
    C2D_TextParse(&l->text, l->buf, s);
    C2D_TextOptimize(&l->text);
}

static void stroke(float x, float y, float w, float h, u32 c) {
    C2D_DrawRectSolid(x, y, 0, w, 1, c);
    C2D_DrawRectSolid(x, y + h - 1, 0, w, 1, c);
    C2D_DrawRectSolid(x, y, 0, 1, h, c);
    C2D_DrawRectSolid(x + w - 1, y, 0, 1, h, c);
}

/* ---- the deck ---------------------------------------------------------- */

/** Reads the first deck file on the SD card, or leaves the table empty. */
static bool load_deck(Table *t, char *name, size_t name_cap) {
    DIR *dir = opendir(DECK_DIR);
    if (!dir) return false;

    char path[512];
    bool found = false;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        const char *dot = strrchr(entry->d_name, '.');
        if (!dot) continue;
        if (strcmp(dot, ".ydk") != 0 && strcmp(dot, ".ydkx") != 0) continue;
        snprintf(path, sizeof path, "%s/%s", DECK_DIR, entry->d_name);
        snprintf(name, name_cap, "%s", entry->d_name);
        found = true;
        break;
    }
    closedir(dir);
    if (!found) return false;

    FILE *f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0) { fclose(f); return false; }

    t->text = malloc((size_t)n + 1);
    if (!t->text) { fclose(f); return false; }
    size_t got = fread(t->text, 1, (size_t)n, f);
    fclose(f);
    t->text[got] = '\0';

    mt_ydk_parse(t->text, got, &t->doc);
    return t->doc.deck.main_count > 0;
}

/** A stand-in deck, so the stage is never empty on a console with no SD deck. */
static void placeholder_deck(Table *t) {
    static const int SAMPLE[] = {
        46986414, 89631139, 38033121, 1861629, 73602965,
        60990740, 42141493, 74018812, 37629703, 2511,
    };
    for (int i = 0; i < 40; ++i) {
        t->doc.deck.main[i] = SAMPLE[i % 10];
    }
    t->doc.deck.main_count = 40;
}

/** Sets the table up and deals an opening hand. */
static void deal(Table *t, int64_t seed) {
    mt_field_set_up(&t->field,
                    t->doc.deck.main, t->doc.deck.main_count,
                    t->doc.deck.extra, t->doc.deck.extra_count);
    mt_field_shuffle_deck(&t->field, seed);
    t->undo_count = 0;
    motion_forget_all();
    for (int i = 0; i < 5; ++i) mt_field_draw(&t->field);
}

/* ---- the bottom screen ------------------------------------------------- */

/** The card's drawn rectangle on the map. A turned card lies on its side. */
static void card_rect(const MtBoardLayout *l, MtCardPosition position, MtMatPoint at,
                      float *x, float *y, float *w, float *h) {
    bool turned = mt_position_turned(position);
    *w = turned ? l->card_height : l->card_width;
    *h = turned ? l->card_width : l->card_height;
    float cx, cy;
    mt_layout_to_pixels(l, at, &cx, &cy);
    *x = cx - *w * 0.5f;
    *y = cy - *h * 0.5f;
}

/**
 * Which card the stylus is on, front-most first.
 *
 * Front-most because the mat is painted back to front, so the last card drawn
 * is the one you can see - and the one you can see is the one you meant. On
 * the tablet this question needs `StagePlane.raise` to undo a projection; here
 * the map is orthographic and it is a rectangle test.
 *
 * Against the *sprung* position rather than the stored one, because a card
 * still sliding into a zone is not yet where the board says it is, and the
 * house rule is that you get the card you pointed at.
 */
static int card_at(const MtBoardLayout *l, const MtPlayField *f, float px, float py) {
    for (int i = f->mat_count - 1; i >= 0; --i) {
        int id = f->mat[i].card;
        float x, y, w, h;
        card_rect(l, f->instances[id].position, motion_point(id, f->mat[i].at),
                  &x, &y, &w, &h);
        if (px >= x && px <= x + w && py >= y && py <= y + h) return id;
    }
    return -1;
}

/** The row the hand is *drawn* as, which is the row everything else measures. */
static MtHandRow hand_row_now(const MtPlayField *f, const Drag *drag) {
    int lifted[1]; int lifted_count = 0;
    int opening[1]; int opening_count = 0;

    if (drag->active) {
        if (drag->from.kind == MT_FROM_HAND) {
            lifted[0] = drag->from.index;
            lifted_count = 1;
        }
        if (drag->opening >= 0) {
            opening[0] = drag->opening;
            opening_count = 1;
        }
    }
    return mt_hand_row(f->hand_count, lifted, lifted_count, opening, opening_count);
}

/** Which place of the drawn row the stylus is over, front-most first, or -1. */
static int hand_place_at(const MtBoardLayout *l, const MtHandRow *row,
                         float px, float py) {
    if (py < l->hand.top || py > l->hand.top + l->hand.height) return -1;
    /* Backwards: the row is drawn left to right and overlaps, so the card on
     * top of the pair under the stylus is the right-hand one. */
    for (int p = row->count - 1; p >= 0; --p) {
        float cx = mt_hand_centre_of(l->hand, l->card_width, p, row->count,
                                     MT_HAND_STEP_FRACTION);
        if (px >= cx - l->card_width * 0.5f && px <= cx + l->card_width * 0.5f) return p;
    }
    return -1;
}

/** Which pile the stylus is inside, if it holds anything. */
static bool pile_under(const MtBoardLayout *l, const MtPlayField *f,
                       float px, float py, MtBoardSlot *out) {
    static const MtBoardSlotKind KINDS[4] = {
        MT_SLOT_GRAVEYARD, MT_SLOT_BANISHED, MT_SLOT_DECK, MT_SLOT_EXTRA_DECK,
    };
    for (int i = 0; i < 4; ++i) {
        MtBoardSlot slot = mt_slot_pile(KINDS[i]);
        const MtSlot *rect = mt_board_rect_of(l, slot);
        if (rect == NULL || !mt_slot_contains(*rect, px, py)) continue;
        int count = 0;
        if (mt_field_pile(f, slot, &count) == NULL || count <= 0) continue;
        *out = slot;
        return true;
    }
    return false;
}

/* ---- the guide ---------------------------------------------------------- */

/*
 * Rendered from `MT_CONTROLS`, never written out here.
 *
 * The mat has almost no affordances drawn on it and needs `MatGuide` for the
 * same reason; the bottom screen is an abstract control surface, where a drawn
 * list is not decoration but the surface doing its job.
 */
static void draw_guide(Label *line) {
    C2D_DrawRectSolid(0, 0, 0, MT_BOTTOM_W, MT_SCREEN_H, SCRIM);
    char row[96];
    for (int i = 0; i < MT_CONTROL_COUNT; ++i) {
        snprintf(row, sizeof row, "%-9s %s", MT_CONTROLS[i].button, MT_CONTROLS[i].meaning);
        label_set(line, row);
        C2D_DrawText(&line->text, C2D_WithColor, 6.0f, 4.0f + (float)i * 13.5f, 0,
                     0.36f, 0.36f, INK);
    }
}

/* ---- the life-point buttons --------------------------------------------- */

/*
 * Two boxes at the right of the status bar, and the only drawn buttons on the
 * whole console. They are here rather than on the mat because the bottom screen
 * *is* the control surface - the rule against affordances is a rule about the
 * table, and the table is the top screen.
 */
#define LP_BOX_W 17.0f
#define LP_BOX_H 11.0f
static const float LP_MINUS_X = MT_BOTTOM_W - 2.0f - LP_BOX_W * 2.0f - 3.0f;
static const float LP_PLUS_X  = MT_BOTTOM_W - 2.0f - LP_BOX_W;
#define LP_BOX_Y 1.5f

static int life_button_at(float px, float py) {
    if (py < LP_BOX_Y || py > LP_BOX_Y + LP_BOX_H) return 0;
    if (px >= LP_MINUS_X && px <= LP_MINUS_X + LP_BOX_W) return -1;
    if (px >= LP_PLUS_X && px <= LP_PLUS_X + LP_BOX_W) return 1;
    return 0;
}

/* ---- drawing the control surface ---------------------------------------- */

static void draw_control_surface(const MtPlayField *f, const MtBoardLayout *layout,
                                 const Drag *drag, const MtHandRow *row, int held,
                                 Label *status, Label *readout, Label *scratch) {
    C2D_DrawRectSolid(0, 0, 0, MT_BOTTOM_W, STATUS_H, BAR);
    C2D_DrawText(&status->text, C2D_WithColor, 4, 1, 0, 0.38f, 0.38f, INK);

    stroke(LP_MINUS_X, LP_BOX_Y, LP_BOX_W, LP_BOX_H, DIM);
    stroke(LP_PLUS_X, LP_BOX_Y, LP_BOX_W, LP_BOX_H, DIM);
    label_set(scratch, "-");
    C2D_DrawText(&scratch->text, C2D_WithColor, LP_MINUS_X + 6.5f, LP_BOX_Y, 0,
                 0.42f, 0.42f, INK);
    label_set(scratch, "+");
    C2D_DrawText(&scratch->text, C2D_WithColor, LP_PLUS_X + 5.5f, LP_BOX_Y, 0,
                 0.42f, 0.42f, INK);

    C2D_DrawRectSolid(layout->field.left, layout->field.top, 0,
                      layout->field.width, layout->field.height, FELT);

    /* The zone the resolver has actually chosen, not the one under the stylus.
     * They differ - that is what hysteresis and precedence are for - and
     * showing the second would be the table promising one thing and doing
     * another, which is the whole reason `MtDropIntent` is a value. */
    bool aiming_at_zone = drag->has_intent && drag->intent.kind == MT_DROP_ZONE;
    for (int i = 0; i < layout->slot_count; ++i) {
        const MtSlot *r = &layout->slots[i].rect;
        bool lit = aiming_at_zone &&
                   mt_board_slot_eq(layout->slots[i].slot, drag->intent.slot);
        stroke(r->left, r->top, r->width, r->height, lit ? FOIL_A : DIM);
    }

    /* A pile the drag is aimed at lights the same way a zone does. */
    if (drag->has_intent) {
        static const MtDropKind KINDS[4] = {
            MT_DROP_GRAVEYARD, MT_DROP_BANISH, MT_DROP_DECK, MT_DROP_EXTRA_DECK,
        };
        static const MtBoardSlotKind SLOTS[4] = {
            MT_SLOT_GRAVEYARD, MT_SLOT_BANISHED, MT_SLOT_DECK, MT_SLOT_EXTRA_DECK,
        };
        for (int i = 0; i < 4; ++i) {
            if (drag->intent.kind != KINDS[i]) continue;
            const MtSlot *r = mt_board_rect_of(layout, mt_slot_pile(SLOTS[i]));
            if (r != NULL) stroke(r->left - 1, r->top - 1, r->width + 2, r->height + 2, FOIL_A);
        }
    }

    /* Back to front, the same order the stage paints in - so a pile reads the
     * same way on both screens. */
    for (int i = 0; i < f->mat_count; ++i) {
        const MtPlacedCard *placed = &f->mat[i];
        const MtBoardCard *card = &f->instances[placed->card];
        if (drag->active && placed->card == drag->carried) continue;
        float x, y, w, h;
        card_rect(layout, card->position, motion_point(placed->card, placed->at),
                  &x, &y, &w, &h);

        /* A pile is drawn as its own edge, offset, so depth reads without a
         * third dimension to put it in. */
        for (int d = placed->beneath_count; d > 0; --d) {
            float o = (float)d * 1.5f;
            C2D_DrawRectSolid(x + o, y + o, 0, w, h, C2D_Color32(0x33, 0x33, 0x3C, 0xFF));
        }
        C2D_DrawRectSolid(x, y, 0, w, h, mt_position_face_up(card->position) ? INK : BACK);
        stroke(x, y, w, h, DIM);
        if (card->counters > 0) {
            C2D_DrawRectSolid(x + w - 5, y + 1, 0, 4, 4, FOIL_A);
        }
        if (card->material_count > 0) {
            C2D_DrawRectSolid(x + 1, y + h - 3, 0, w - 2, 2, C2D_Color32(0xFF, 0x69, 0xB4, 0xFF));
        }
    }

    /*
     * The hand as a strip, not a fan. HandFan's lean exists to sell three
     * dimensions; in two, a row is the honest form of the same row - and the
     * top screen is where the depth actually is.
     *
     * Drawn from `MtHandRow`, so a place held open for a card about to land is
     * a place that is actually there rather than a caret painted in a gap.
     */
    for (int p = 0; p < row->count; ++p) {
        int index = row->places[p];
        float cx = mt_hand_centre_of(layout->hand, layout->card_width, p, row->count,
                                     MT_HAND_STEP_FRACTION);
        float x = cx - layout->card_width * 0.5f;
        if (index == MT_HAND_OPEN) {
            stroke(x, layout->hand.top, layout->card_width, layout->hand.height, FOIL_A);
            continue;
        }
        bool picked = (index == held);
        float y = layout->hand.top - (picked ? 4.0f : 0.0f);
        C2D_DrawRectSolid(x, y, 0, layout->card_width, layout->hand.height, INK);
        stroke(x, y, layout->card_width, layout->hand.height, picked ? FOIL_A : DIM);
        if (picked) C2D_DrawRectSolid(x, y, 0, layout->card_width, 2.0f, FOIL_A);
    }

    /* The carried card last, because it is above everything it is over. */
    if (drag->active && drag->carried >= 0) {
        MtCardPosition position = f->instances[drag->carried].position;
        float x, y, w, h;
        card_rect(layout, position, drag->at, &x, &y, &w, &h);
        C2D_DrawRectSolid(x, y, 0, w, h,
                          mt_position_face_up(position) ? INK : BACK);
        stroke(x, y, w, h, FOIL_A);
    }

    C2D_DrawText(&readout->text, C2D_WithColor, 4, layout->readout.top, 0,
                 0.36f, 0.36f, INK);
}

/* ---- the seats --------------------------------------------------------- */

/*
 * Four seats, and the fourth is the one the room is for.
 *
 * `tiltDegrees` on the tablet is measured off the table's normal, so elevation
 * is ninety minus it: Overhead 85, Table 69, Seated 56 - and POV, kai's own
 * chair, at 32. Those are the same four numbers `StageSeat` carries.
 */
static const MtCamera SEATS[4] = {
    { 85.0f, 0.0f, 1.30f, { 0.5f, 0.5f } },
    { 69.0f, 0.0f, 1.35f, { 0.5f, 0.5f } },
    { 56.0f, 0.0f, 1.45f, { 0.5f, 0.5f } },
    { 32.0f, 0.0f, 1.70f, { 0.5f, 0.5f } },
};
static const char *SEAT_NAMES[4] = { "Overhead", "Table", "Seated", "POV" };

int main(void) {
    bool is_new_3ds = false;
    APT_CheckNew3DS(&is_new_3ds);
    if (is_new_3ds) osSetSpeedupEnable(true);

    gfxInitDefault();
    gfxSet3D(true);
    C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
    C2D_Init(C2D_DEFAULT_MAX_OBJECTS);
    C2D_Prepare();
    romfsInit();

    if (!mt_gfx_init()) { gfxExit(); return 1; }
    C3D_RenderTarget *bottom = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    Label status = { C2D_TextBufNew(256), {0} };
    Label readout = { C2D_TextBufNew(256), {0} };
    Label scratch = { C2D_TextBufNew(256), {0} };

    static Table table;
    memset(&table, 0, sizeof table);
    motion_forget_all();

    char deck_name[64] = "built-in";
    if (!load_deck(&table, deck_name, sizeof deck_name)) {
        placeholder_deck(&table);
        snprintf(deck_name, sizeof deck_name, "no deck on SD");
    }
    table.seed = 20260820;
    deal(&table, table.seed);

    /*
     * Solved once, against the height left after the status bar. Declaring the
     * chrome is the whole discipline: the fitter puts the hand band inside what
     * it was given, so a bar added here and not subtracted there is a hand row
     * pushed off the bottom of the screen.
     */
    MtBoardLayout layout = mt_board_solve((float)MT_BOTTOM_W,
                                          (float)MT_SCREEN_H - STATUS_H,
                                          CARD_ASPECT_DEFAULT, 1.0f, 0.0f);
    for (int i = 0; i < layout.slot_count; ++i) layout.slots[i].rect.top += STATUS_H;
    layout.field.top += STATUS_H;
    layout.hand.top += STATUS_H;
    layout.readout.top += STATUS_H;

    mt_gfx_use_layout(&layout);

    int seat = 2;
    MtCamera camera = SEATS[seat];
    int held = 0;              /* which hand card the d-pad has selected */
    Drag drag;
    memset(&drag, 0, sizeof drag);
    drag.opening = -1;
    drag.carried = -1;

    float last_x = MT_BOTTOM_W * 0.5f, last_y = MT_SCREEN_H * 0.5f;
    int life_repeat = 0;
    char buf[192];
    label_set(&readout, "Drag a card.  Select: the controls.");

    TickCounter clock;
    osTickCounterStart(&clock);

    while (aptMainLoop()) {
        osTickCounterUpdate(&clock);
        float dt = (float)(osTickCounterRead(&clock) / 1000.0);
        if (dt <= 0.0f) dt = 1.0f / 60.0f;

        hidScanInput();
        u32 down = hidKeysDown();
        u32 kheld = hidKeysHeld();
        u32 up = hidKeysUp();
        if (down & KEY_START) break;

        bool guide = (kheld & KEY_SELECT) != 0;

        /* ---- buttons ---------------------------------------------------- */

        if (down & KEY_A) { checkpoint(&table); if (!mt_field_draw(&table.field)) undo(&table); }
        if (down & KEY_B) { if (undo(&table)) label_set(&readout, "Undo"); }
        if (down & KEY_X) {
            if (kheld & KEY_L) {
                table.seed += 977;
                deal(&table, table.seed);
                label_set(&readout, "New hand");
            } else {
                checkpoint(&table);
                table.seed += 31;
                mt_field_shuffle_deck(&table.field, table.seed);
                label_set(&readout, "Shuffled");
            }
        }
        if (down & KEY_Y) {
            if (kheld & KEY_L) {
                seat = (seat + 1) % 4;
                camera = SEATS[seat];
            } else {
                int id = card_at(&layout, &table.field, last_x, last_y);
                if (id >= 0) {
                    checkpoint(&table);
                    bool ok = (kheld & KEY_R) ? mt_field_rotate(&table.field, id)
                                              : mt_field_flip(&table.field, id);
                    if (ok) label_set(&readout, (kheld & KEY_R) ? "Turned" : "Flipped");
                    else undo(&table);
                }
            }
        }
        if (down & KEY_UP) { mt_field_next_phase(&table.field); }
        if (down & KEY_DOWN) { checkpoint(&table); mt_field_end_turn(&table.field); }
        if ((down & KEY_LEFT) && held > 0) --held;
        if ((down & KEY_RIGHT) && held < table.field.hand_count - 1) ++held;
        if (held >= table.field.hand_count) held = table.field.hand_count - 1;
        if (held < 0) held = 0;

        /* The camera is a camera. The pad orbits it; the shoulders dolly -
         * except while the stylus holds a card, when they are modifiers on the
         * drop instead. One stylus and one gesture: the drag owns them. */
        circlePosition pad;
        hidCircleRead(&pad);
        if (abs(pad.dx) > 20) camera.spin += (float)pad.dx * 0.0006f;
        if (abs(pad.dy) > 20) {
            camera.elevation += (float)pad.dy * 0.0004f;
            if (camera.elevation < 12.0f) camera.elevation = 12.0f;
            if (camera.elevation > 89.0f) camera.elevation = 89.0f;
        }
        if (!drag.active) {
            if (kheld & KEY_ZL) camera.distance += 0.006f;
            if (kheld & KEY_ZR) camera.distance -= 0.006f;
            if (camera.distance < 0.70f) camera.distance = 0.70f;
            if (camera.distance > 3.00f) camera.distance = 3.00f;
        }

        /* ---- the stylus -------------------------------------------------- */

        MtHandRow row = hand_row_now(&table.field, &drag);
        touchPosition touch;

        if ((down & KEY_TOUCH) && !guide) {
            hidTouchRead(&touch);
            float px = (float)touch.px, py = (float)touch.py;
            last_x = px; last_y = py;

            int life = life_button_at(px, py);
            if (life != 0) {
                checkpoint(&table);
                mt_field_adjust_life(&table.field, life * 100);
                life_repeat = 22;
            } else {
                /*
                 * The gesture belongs to whatever it landed on, decided here
                 * and nowhere else. Hand, then a card on the mat, then a pile:
                 * aiming at a particular card is a more particular act than
                 * aiming at the pile it is sitting on.
                 */
                int place = hand_place_at(&layout, &row, px, py);
                int hand_index = (place >= 0) ? row.places[place] : MT_HAND_OPEN;
                MtBoardSlot pile;
                int id;

                if (hand_index >= 0) {
                    drag.from = mt_from_hand(hand_index);
                    held = hand_index;
                    drag.active = true;
                    drag.has_home = false;
                } else if ((id = card_at(&layout, &table.field, px, py)) >= 0) {
                    drag.from = mt_from_mat(id);
                    drag.active = true;
                    drag.has_home = false;
                } else if (pile_under(&layout, &table.field, px, py, &pile)) {
                    /* Index 0, the top card, because there is no spread yet to
                     * name a deeper one. `PileFan` is what makes the rest of a
                     * pile reachable, and it lands in P3. */
                    drag.from = mt_from_pile(pile, 0);
                    drag.active = true;
                    /* The put-back latch, armed at the pile the card came out
                     * of: taking a card out and setting it back down is a
                     * change of mind, and `mt_fan_home_seeing` is what makes it
                     * one rather than a move to where it already was. */
                    const MtSlot *rect = mt_board_rect_of(&layout, pile);
                    drag.home.at = mt_layout_to_mat(&layout,
                                                    mt_slot_centre_x(*rect),
                                                    mt_slot_centre_y(*rect));
                    drag.home.departed = false;
                    drag.has_home = true;
                }

                if (drag.active) {
                    drag.carried = carried_instance(&table.field, drag.from);
                    drag.at = mt_layout_to_mat(&layout, px, py);
                    drag.has_intent = false;
                    drag.opening = -1;
                    if (drag.carried < 0) { drag.active = false; }
                }
            }
        }

        if (kheld & KEY_TOUCH) {
            hidTouchRead(&touch);
            last_x = (float)touch.px;
            last_y = (float)touch.py;
            if (life_repeat > 0 && --life_repeat == 0) {
                int life = life_button_at(last_x, last_y);
                if (life != 0) {
                    mt_field_adjust_life(&table.field, life * 100);
                    life_repeat = 6;
                }
            }
            if (drag.active) {
                drag.at = mt_layout_to_mat(&layout, last_x, last_y);

                if (drag.has_home) {
                    drag.home = mt_fan_home_seeing(drag.home, drag.at, &layout);
                }

                /* One resolve a frame, and its answer is the only thing shown
                 * and the only thing committed. */
                MtDropIntent resolved = mt_drop_resolve(
                    drag.at,
                    (drag.from.kind == MT_FROM_MAT) ? drag.carried : -1,
                    &table.field, &layout,
                    drag.has_intent ? &drag.intent : NULL,
                    (kheld & KEY_ZR) != 0,
                    drag.has_home ? &drag.home : NULL,
                    &row,
                    mt_hand_step(layout.hand, layout.card_width, row.count,
                                 MT_HAND_STEP_FRACTION));

                drag.intent = resolved;
                drag.has_intent = true;
                drag.opening = (resolved.kind == MT_DROP_HAND) ? resolved.target : -1;
                label_set(&readout, mt_drop_label(resolved));
            }
        }

        if (up & KEY_TOUCH) {
            life_repeat = 0;
            if (drag.active && drag.has_intent) {
                /* Setting a monster and setting a spell are the same motion of
                 * the hand; the zone answers. SetPosition, unchanged. */
                MtCardPosition position = mt_set_position(
                    (kheld & KEY_L) != 0, (kheld & KEY_R) != 0,
                    &drag.intent, MT_MONSTER_UNKNOWN);
                /* A plain slide keeps the card the way it already was, which is
                 * what MT_POS_KEEP is for and what a hand that said nothing
                 * means. */
                bool spoken = (kheld & (KEY_L | KEY_R)) != 0;
                if (!spoken && drag.from.kind == MT_FROM_MAT) position = MT_POS_KEEP;

                checkpoint(&table);
                if (mt_drop_commit(&table.field, drag.from, drag.intent, position)) {
                    snprintf(buf, sizeof buf, "%s%s", mt_drop_label(drag.intent),
                             mt_position_face_up(
                                 table.field.instances[drag.carried].position)
                                 ? "" : "  set");
                    label_set(&readout, buf);
                } else {
                    undo(&table);
                    label_set(&readout, "-");
                }
                if (held >= table.field.hand_count) held = table.field.hand_count - 1;
                if (held < 0) held = 0;
            }
            drag.active = false;
            drag.has_intent = false;
            drag.has_home = false;
            drag.opening = -1;
            drag.carried = -1;
        }

        /* ---- motion ------------------------------------------------------ */

        row = hand_row_now(&table.field, &drag);

        for (int i = 0; i < table.field.mat_count; ++i) {
            const MtPlacedCard *placed = &table.field.mat[i];
            bool carried = drag.active && placed->card == drag.carried;
            motion_towards(placed->card,
                           carried ? drag.at : placed->at,
                           carried ? CARRY_LIFT : 0.0f,
                           carried, dt);
        }
        /* A carried card that is not on the mat - out of the hand, out of a
         * pile - is in the air all the same, and the stage draws it there. */
        if (drag.active && drag.carried >= 0 &&
            mt_field_placed(&table.field, drag.carried) < 0) {
            if (!motion[drag.carried].live) motion_snap(drag.carried, drag.at, 0.0f);
            motion_towards(drag.carried, drag.at, CARRY_LIFT, true, dt);
        }

        /* ---- the status line --------------------------------------------- */

        snprintf(buf, sizeof buf, "LP %d  %s  T%d  D%d G%d B%d  %s",
                 table.field.life_points, mt_phase_label(table.field.phase),
                 table.field.turn, table.field.deck_count,
                 table.field.graveyard_count, table.field.banished_count,
                 SEAT_NAMES[seat]);
        label_set(&status, buf);

        float slider = osGet3DSliderState();

        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);

        for (int e = 0; e < 2; ++e) {
            if (!mt_gfx_begin_stage((MtEye)e, slider, &camera)) continue;
            mt_gfx_draw_table(&layout);
            for (int i = 0; i < table.field.mat_count; ++i) {
                const MtPlacedCard *placed = &table.field.mat[i];
                MtStageCard c;
                c.at = motion_point(placed->card, placed->at);
                c.position = table.field.instances[placed->card].position;
                /* A card in the air is lifted, which is what the tablet spends
                 * CarryHeight on and what a depth buffer gives here for free. */
                c.lift = motion_lift(placed->card);
                c.pile_depth = placed->beneath_count;
                c.lit = drag.active && placed->card == drag.carried;
                mt_gfx_draw_card(&c);
            }
            if (drag.active && drag.carried >= 0 &&
                mt_field_placed(&table.field, drag.carried) < 0) {
                MtStageCard c;
                c.at = motion_point(drag.carried, drag.at);
                c.position = table.field.instances[drag.carried].position;
                c.lift = motion_lift(drag.carried);
                c.pile_depth = 0;
                c.lit = true;
                mt_gfx_draw_card(&c);
            }
        }

        mt_gfx_begin_2d();
        C2D_TargetClear(bottom, TRUE_BLACK);
        C2D_SceneBegin(bottom);
        if (guide) {
            draw_guide(&scratch);
        } else {
            draw_control_surface(&table.field, &layout, &drag, &row, held,
                                 &status, &readout, &scratch);
        }

        C3D_FrameEnd(0);
    }

    C2D_TextBufDelete(scratch.buf);
    C2D_TextBufDelete(readout.buf);
    C2D_TextBufDelete(status.buf);
    mt_gfx_exit();
    free(table.text);
    romfsExit();
    C2D_Fini();
    C3D_Fini();
    gfxExit();
    return 0;
}
