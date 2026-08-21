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
#include "core/mt_playfield.h"
#include "core/mt_random.h"
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

/*
 * Undo is a ring of whole fields.
 *
 * `PlayField` on the tablet is immutable, so a list of them *is* the undo
 * stack and costs nothing to keep. Here the field is one mutable struct, and
 * the equivalent is to copy it before every move that changes something. About
 * 15KB a level, sixteen levels, which is a quarter of a megabyte against 124.
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

/* ---- text ------------------------------------------------------------- */

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
    for (int i = 0; i < 5; ++i) mt_field_draw(&t->field);
}

/* ---- the bottom screen ------------------------------------------------- */

static MtMatPoint screen_to_mat(const MtBoardLayout *l, float x, float y) {
    MtMatPoint p;
    p.x = (x - l->field.left) / l->field.width;
    p.y = (y - l->field.top) / l->field.height;
    return p;
}

static void mat_to_screen(const MtBoardLayout *l, MtMatPoint p, float *x, float *y) {
    *x = l->field.left + p.x * l->field.width;
    *y = l->field.top + p.y * l->field.height;
}

/** The card's drawn rectangle on the map. A turned card lies on its side. */
static void card_rect(const MtBoardLayout *l, const MtPlayField *f,
                      const MtPlacedCard *placed,
                      float *x, float *y, float *w, float *h) {
    bool turned = mt_position_turned(f->instances[placed->card].position);
    *w = turned ? l->card_height : l->card_width;
    *h = turned ? l->card_width : l->card_height;
    float cx, cy;
    mat_to_screen(l, placed->at, &cx, &cy);
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
 */
static int card_at(const MtBoardLayout *l, const MtPlayField *f, float px, float py) {
    for (int i = f->mat_count - 1; i >= 0; --i) {
        float x, y, w, h;
        card_rect(l, f, &f->mat[i], &x, &y, &w, &h);
        if (px >= x && px <= x + w && py >= y && py <= y + h) return f->mat[i].card;
    }
    return -1;
}

static void hand_slot(const MtBoardLayout *l, int count, int i, float *x, float *w) {
    *w = l->card_width;
    if (count <= 1) { *x = l->hand.left; return; }
    float step = (l->hand.width - l->card_width) / (float)(count - 1);
    if (step > l->card_width * 1.06f) step = l->card_width * 1.06f;
    *x = l->hand.left + step * (float)i;
}

static void draw_control_surface(const MtPlayField *f, const MtBoardLayout *layout,
                                 const MtPlacedSlot *hit, int held, int dragging,
                                 Label *status, Label *readout) {
    C2D_DrawRectSolid(0, 0, 0, MT_BOTTOM_W, STATUS_H, C2D_Color32(0x12, 0x12, 0x16, 0xFF));
    C2D_DrawText(&status->text, C2D_WithColor, 4, 1, 0, 0.38f, 0.38f, INK);

    C2D_DrawRectSolid(layout->field.left, layout->field.top, 0,
                      layout->field.width, layout->field.height, FELT);

    for (int i = 0; i < layout->slot_count; ++i) {
        const MtSlot *r = &layout->slots[i].rect;
        bool lit = (hit != NULL && &layout->slots[i] == hit);
        stroke(r->left, r->top, r->width, r->height, lit ? FOIL_A : DIM);
    }

    /* Back to front, the same order the stage paints in - so a pile reads the
     * same way on both screens. */
    for (int i = 0; i < f->mat_count; ++i) {
        const MtPlacedCard *placed = &f->mat[i];
        const MtBoardCard *card = &f->instances[placed->card];
        float x, y, w, h;
        card_rect(layout, f, placed, &x, &y, &w, &h);

        /* A pile is drawn as its own edge, offset, so depth reads without a
         * third dimension to put it in. */
        for (int d = placed->beneath_count; d > 0; --d) {
            float o = (float)d * 1.5f;
            C2D_DrawRectSolid(x + o, y + o, 0, w, h, C2D_Color32(0x33, 0x33, 0x3C, 0xFF));
        }
        C2D_DrawRectSolid(x, y, 0, w, h, mt_position_face_up(card->position) ? INK : BACK);
        stroke(x, y, w, h, placed->card == dragging ? FOIL_A : DIM);
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
     */
    for (int i = 0; i < f->hand_count; ++i) {
        float x, w;
        hand_slot(layout, f->hand_count, i, &x, &w);
        float y = layout->hand.top - (i == held ? 4.0f : 0.0f);
        C2D_DrawRectSolid(x, y, 0, w, layout->hand.height, INK);
        stroke(x, y, w, layout->hand.height, i == held ? FOIL_A : DIM);
        if (i == held) C2D_DrawRectSolid(x, y, 0, w, 2.0f, FOIL_A);
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

    static Table table;
    memset(&table, 0, sizeof table);

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
    const MtPlacedSlot *hit = NULL;
    int held = 0;              /* which hand card is selected */
    int dragging = -1;         /* which mat card the stylus has hold of */
    MtMatPoint drag_at = { 0.5f, 0.5f };
    char buf[192];
    label_set(&readout, "A draw  B undo  X new hand  Y seat  R set");

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        u32 kheld = hidKeysHeld();
        u32 up = hidKeysUp();
        if (down & KEY_START) break;

        if (down & KEY_A) { checkpoint(&table); if (!mt_field_draw(&table.field)) undo(&table); }
        if (down & KEY_B) { if (undo(&table)) label_set(&readout, "Undo"); }
        if (down & KEY_X) { table.seed += 977; deal(&table, table.seed); label_set(&readout, "New hand"); }
        if (down & KEY_Y) { seat = (seat + 1) % 4; camera = SEATS[seat]; }
        if ((down & KEY_LEFT) && held > 0) --held;
        if ((down & KEY_RIGHT) && held < table.field.hand_count - 1) ++held;

        /* The camera is a camera. The pad orbits it; the shoulders dolly. */
        circlePosition pad;
        hidCircleRead(&pad);
        if (abs(pad.dx) > 20) camera.spin += (float)pad.dx * 0.0006f;
        if (abs(pad.dy) > 20) {
            camera.elevation += (float)pad.dy * 0.0004f;
            if (camera.elevation < 12.0f) camera.elevation = 12.0f;
            if (camera.elevation > 89.0f) camera.elevation = 89.0f;
        }
        if (kheld & KEY_ZL) camera.distance += 0.006f;
        if (kheld & KEY_ZR) camera.distance -= 0.006f;
        if (camera.distance < 0.70f) camera.distance = 0.70f;
        if (camera.distance > 3.00f) camera.distance = 3.00f;

        touchPosition touch;
        if (down & KEY_TOUCH) {
            hidTouchRead(&touch);
            /* A finger that lands on a card takes that card; one that lands on
             * the felt is aiming a hand card at a zone. One decision, made on
             * the press - the same split MatDesk makes with ten lanes and this
             * makes with one, because a stylus cannot be two fingers. */
            dragging = card_at(&layout, &table.field, (float)touch.px, (float)touch.py);
            if (dragging >= 0) checkpoint(&table);
        }
        if (kheld & KEY_TOUCH) {
            hidTouchRead(&touch);
            hit = mt_board_slot_at(&layout, (float)touch.px, (float)touch.py);
            drag_at = screen_to_mat(&layout, (float)touch.px, (float)touch.py);
        }

        if (up & KEY_TOUCH) {
            bool set = (kheld & KEY_R) != 0;
            if (dragging >= 0) {
                int onto = -1;
                /* Released over another card: that is a stack, not a slide. */
                for (int i = table.field.mat_count - 1; i >= 0; --i) {
                    int id = table.field.mat[i].card;
                    if (id == dragging) continue;
                    float x, y, w, h;
                    card_rect(&layout, &table.field, &table.field.mat[i], &x, &y, &w, &h);
                    float px = layout.field.left + drag_at.x * layout.field.width;
                    float py = layout.field.top + drag_at.y * layout.field.height;
                    if (px >= x && px <= x + w && py >= y && py <= y + h) { onto = id; break; }
                }
                if (onto >= 0) {
                    mt_field_stack_onto(&table.field, dragging, onto, MT_POS_KEEP);
                    label_set(&readout, "Stack");
                } else {
                    mt_field_move_on_mat(&table.field, dragging, drag_at, MT_POS_KEEP);
                    label_set(&readout, "Place");
                }
                dragging = -1;
            } else if (hit != NULL && table.field.hand_count > 0) {
                MtDropIntent intent = mt_drop_zone(hit->slot, drag_at);
                /* Setting a monster and setting a spell are the same motion of
                 * the hand; the zone answers. SetPosition, unchanged. */
                MtCardPosition position =
                    mt_set_position(set, false, &intent, MT_MONSTER_UNKNOWN);
                checkpoint(&table);
                if (mt_field_play_from_hand(&table.field, held, drag_at, position)) {
                    snprintf(buf, sizeof buf, "%s  %s", mt_drop_label(intent),
                             mt_position_face_up(position) ? "face up" : "set");
                    label_set(&readout, buf);
                } else {
                    undo(&table);
                }
                if (held >= table.field.hand_count) held = table.field.hand_count - 1;
                if (held < 0) held = 0;
            }
            hit = NULL;
        }

        snprintf(buf, sizeof buf, "LP %d  %s  T%d  deck %d  gy %d  %s  %s",
                 table.field.life_points, mt_phase_label(table.field.phase),
                 table.field.turn, table.field.deck_count,
                 table.field.graveyard_count, SEAT_NAMES[seat], deck_name);
        label_set(&status, buf);

        float slider = osGet3DSliderState();

        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);

        for (int e = 0; e < 2; ++e) {
            if (!mt_gfx_begin_stage((MtEye)e, slider, &camera)) continue;
            mt_gfx_draw_table(&layout);
            for (int i = 0; i < table.field.mat_count; ++i) {
                const MtPlacedCard *placed = &table.field.mat[i];
                MtStageCard c;
                c.at = (placed->card == dragging) ? drag_at : placed->at;
                c.position = table.field.instances[placed->card].position;
                /* A card in the air is lifted, which is what the tablet spends
                 * CarryHeight on and what a depth buffer gives here for free. */
                c.lift = (placed->card == dragging) ? 0.45f : 0.0f;
                c.pile_depth = placed->beneath_count;
                c.lit = (placed->card == dragging);
                mt_gfx_draw_card(&c);
            }
        }

        mt_gfx_begin_2d();
        C2D_TargetClear(bottom, TRUE_BLACK);
        C2D_SceneBegin(bottom);
        draw_control_surface(&table.field, &layout, hit, held, dragging, &status, &readout);

        C3D_FrameEnd(0);
    }

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
