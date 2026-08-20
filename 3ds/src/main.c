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

#define MAX_MAT  24
#define MAX_HAND 10

typedef struct {
    int card_id;
    MtMatPoint at;
    MtCardPosition position;
    int pile_depth;
} Placed;

typedef struct {
    MtYdkDocument doc;
    char *text;

    int deck[MT_DECK_MAIN_MAX];
    int deck_count;

    int hand[MAX_HAND];
    int hand_count;

    Placed mat[MAX_MAT];
    int mat_count;

    int life;
    MtDuelPhase phase;
    int turn;
    uint32_t seed;
} Table;

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

/**
 * Fisher-Yates over the ported XorWow, so the seed deals the same hand here as
 * it does on the tablet. `PlayField.riffled`, in C.
 */
static void shuffle(Table *t, int64_t seed) {
    MtRandom r;
    mt_random_seed(&r, seed);
    for (int i = t->deck_count - 1; i > 0; --i) {
        int j = mt_random_next_int_bound(&r, i + 1);
        int swap = t->deck[i];
        t->deck[i] = t->deck[j];
        t->deck[j] = swap;
    }
}

static void deal(Table *t, int64_t seed) {
    t->deck_count = t->doc.deck.main_count;
    for (int i = 0; i < t->deck_count; ++i) t->deck[i] = t->doc.deck.main[i];
    shuffle(t, seed);
    t->hand_count = 0;
    t->mat_count = 0;
    t->life = 8000;
    t->phase = MT_PHASE_MAIN1;
    t->turn = 1;
    for (int i = 0; i < 5 && t->deck_count > 0; ++i) {
        t->hand[t->hand_count++] = t->deck[--t->deck_count];
    }
}

static void draw_one(Table *t) {
    if (t->deck_count <= 0 || t->hand_count >= MAX_HAND) return;
    t->hand[t->hand_count++] = t->deck[--t->deck_count];
}

/** Plays hand card `index` at a mat point. */
static void play(Table *t, int index, MtMatPoint at, MtCardPosition position) {
    if (index < 0 || index >= t->hand_count || t->mat_count >= MAX_MAT) return;
    Placed *p = &t->mat[t->mat_count++];
    p->card_id = t->hand[index];
    p->at = at;
    p->position = position;
    p->pile_depth = 0;
    for (int i = index; i < t->hand_count - 1; ++i) t->hand[i] = t->hand[i + 1];
    --t->hand_count;
}

/* ---- the bottom screen ------------------------------------------------- */

static MtMatPoint screen_to_mat(const MtBoardLayout *l, float x, float y) {
    MtMatPoint p;
    p.x = (x - l->field.left) / l->field.width;
    p.y = (y - l->field.top) / l->field.height;
    return p;
}

static void draw_control_surface(const Table *t, const MtBoardLayout *layout,
                                 const MtPlacedSlot *hit, int held,
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

    /* Cards on the mat: the map is a multiplication, nothing more. */
    for (int i = 0; i < t->mat_count; ++i) {
        const Placed *p = &t->mat[i];
        bool turned = mt_position_turned(p->position);
        float w = turned ? layout->card_height : layout->card_width;
        float h = turned ? layout->card_width : layout->card_height;
        float cx = layout->field.left + p->at.x * layout->field.width;
        float cy = layout->field.top + p->at.y * layout->field.height;
        u32 c = mt_position_face_up(p->position) ? INK : BACK;
        C2D_DrawRectSolid(cx - w / 2, cy - h / 2, 0, w, h, c);
        stroke(cx - w / 2, cy - h / 2, w, h, DIM);
    }

    /*
     * The hand as a strip, not a fan. `HandFan`'s lean exists to sell three
     * dimensions; in two, a row is the honest form of the same row - and the
     * top screen is where the depth actually is.
     */
    float hx = layout->hand.left;
    float step = (t->hand_count > 0)
        ? (layout->hand.width - layout->card_width) / (float)(t->hand_count > 1 ? t->hand_count - 1 : 1)
        : 0.0f;
    if (step > layout->card_width * 1.06f) step = layout->card_width * 1.06f;

    for (int i = 0; i < t->hand_count; ++i) {
        float x = hx + step * (float)i;
        float y = layout->hand.top - (i == held ? 4.0f : 0.0f);
        C2D_DrawRectSolid(x, y, 0, layout->card_width, layout->hand.height, INK);
        stroke(x, y, layout->card_width, layout->hand.height, i == held ? FOIL_A : DIM);
        if (i == held) {
            C2D_DrawRectSolid(x, y, 0, layout->card_width, 2.0f, FOIL_A);
        }
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

    if (!mt_gfx_init()) {
        gfxExit();
        return 1;
    }
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
    deal(&table, 20260820);

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
    int held = 0;
    char buf[192];

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        u32 kheld = hidKeysHeld();
        if (down & KEY_START) break;

        if (down & KEY_A) draw_one(&table);
        if (down & KEY_X) { table.seed += 977u; deal(&table, 20260820 + table.seed); }
        if (down & KEY_Y) { seat = (seat + 1) % 4; camera = SEATS[seat]; }
        if (down & KEY_LEFT && held > 0) --held;
        if (down & KEY_RIGHT && held < table.hand_count - 1) ++held;

        /* The camera is a camera. One stick orbits it; the shoulders dolly. */
        circlePosition pad;
        hidCircleRead(&pad);
        if (abs(pad.dx) > 20) camera.spin += (float)pad.dx * 0.0006f;
        if (abs(pad.dy) > 20) {
            camera.elevation += (float)pad.dy * 0.0004f;
            if (camera.elevation < 12.0f) camera.elevation = 12.0f;
            if (camera.elevation > 89.0f) camera.elevation = 89.0f;
        }
        if (kheld & KEY_L) camera.distance += 0.006f;
        if (kheld & KEY_R) camera.distance -= 0.006f;
        if (camera.distance < 0.70f) camera.distance = 0.70f;
        if (camera.distance > 3.00f) camera.distance = 3.00f;

        if (kheld & KEY_TOUCH) {
            touchPosition touch;
            hidTouchRead(&touch);
            hit = mt_board_slot_at(&layout, (float)touch.px, (float)touch.py);
        }
        if ((hidKeysUp() & KEY_TOUCH) && hit != NULL) {
            MtMatPoint at = screen_to_mat(&layout, mt_slot_centre_x(hit->rect),
                                          mt_slot_centre_y(hit->rect));
            MtDropIntent intent = mt_drop_zone(hit->slot, at);
            /* Held R sets the card. Where it lands decides how it lies, and the
             * zone answers - `SetPosition`, unchanged from the tablet. */
            bool set = (kheld & KEY_R) != 0;
            MtCardPosition position =
                mt_set_position(set, false, &intent, MT_MONSTER_UNKNOWN);
            play(&table, held, at, position);
            if (held >= table.hand_count) held = table.hand_count - 1;
            if (held < 0) held = 0;
            snprintf(buf, sizeof buf, "%s  %s", mt_drop_label(intent),
                     mt_position_face_up(position) ? "face up" : "set");
            label_set(&readout, buf);
            hit = NULL;
        }

        snprintf(buf, sizeof buf, "LP %d   %s   T%d   deck %d   %s   %s",
                 table.life, mt_phase_label(table.phase), table.turn,
                 table.deck_count, SEAT_NAMES[seat], deck_name);
        label_set(&status, buf);

        float slider = osGet3DSliderState();

        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);

        for (int e = 0; e < 2; ++e) {
            if (!mt_gfx_begin_stage((MtEye)e, slider, &camera)) continue;
            mt_gfx_draw_table(&layout);
            for (int i = 0; i < table.mat_count; ++i) {
                MtStageCard c;
                c.at = table.mat[i].at;
                c.position = table.mat[i].position;
                c.lift = 0.0f;
                c.pile_depth = table.mat[i].pile_depth;
                c.lit = false;
                mt_gfx_draw_card(&c);
            }
        }

        mt_gfx_begin_2d();
        C2D_TargetClear(bottom, TRUE_BLACK);
        C2D_SceneBegin(bottom);
        draw_control_surface(&table, &layout, hit, held, &status, &readout);

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
