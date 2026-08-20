/*
 * kai's master tool on the 3DS - the entry point.
 *
 * Phase 0 of docs/PORT.md: prove the pipeline end to end. It boots, it claims
 * both screens, it turns the stereoscopic top screen on and responds to the 3D
 * slider, it reads the stylus - and, most usefully, it draws the *real* board
 * solved by the ported `mt_board_solve`, so that a wrong number shows up on the
 * console rather than only in the host conformance suite.
 *
 * The renderer here is citro2d, deliberately. Phase 2 replaces the top screen
 * with citro3d geometry and a vertex shader; until there is something worth
 * looking at in three dimensions, a real 3D pipeline would be scaffolding
 * pretending to be architecture. What phase 2 will *not* have to redo is the
 * bottom screen, which is orthographic on purpose and stays 2D forever.
 */
#include <3ds.h>
#include <citro2d.h>

#include <stdio.h>
#include <string.h>

#include "core/mt_board_layout.h"
#include "core/mt_drop.h"
#include "core/mt_types.h"

#define TOP_W    400
#define BOTTOM_W 320
#define SCREEN_H 240

/* Swiss + prismatic, from docs/DESIGN.md: sharp white on true black, colour
 * only as meaning or as light. */
#define INK        C2D_Color32(0xFF, 0xFF, 0xFF, 0xFF)
#define TRUE_BLACK C2D_Color32(0x00, 0x00, 0x00, 0xFF)
#define DIM        C2D_Color32(0x50, 0x50, 0x50, 0xFF)
#define FOIL_A     C2D_Color32(0x5A, 0xD2, 0xFF, 0xFF)   /* the two hues of */
#define FOIL_B     C2D_Color32(0xFF, 0x69, 0xB4, 0xFF)   /* drawPrismaticInset */

typedef struct {
    C2D_TextBuf buf;
    C2D_Text text;
} Label;

static void label_set(Label *l, const char *s) {
    C2D_TextBufClear(l->buf);
    C2D_TextParse(&l->text, l->buf, s);
    C2D_TextOptimize(&l->text);
}

/** A one-pixel outline, which is all a zone needs to read as a place. */
static void stroke_rect(float x, float y, float w, float h, u32 colour) {
    C2D_DrawRectSolid(x,         y,         0.0f, w,    1.0f, colour);
    C2D_DrawRectSolid(x,         y + h - 1, 0.0f, w,    1.0f, colour);
    C2D_DrawRectSolid(x,         y,         0.0f, 1.0f, h,    colour);
    C2D_DrawRectSolid(x + w - 1, y,         0.0f, 1.0f, h,    colour);
}

/*
 * The bottom screen: the board, orthographic, at the size the ported solver
 * says. Nothing here unprojects anything - `MtMatPoint` is a fraction and the
 * map is a multiplication, which is the whole reason this screen is a control
 * surface rather than a second camera.
 */
static void draw_board(const MtBoardLayout *layout, const MtPlacedSlot *hit) {
    for (int i = 0; i < layout->slot_count; ++i) {
        const MtSlot *r = &layout->slots[i].rect;
        bool lit = (hit != NULL && &layout->slots[i] == hit);
        stroke_rect(r->left, r->top, r->width, r->height, lit ? FOIL_A : DIM);
    }
    /* The hand band and its readout are part of the budget, not drawn over
     * what happened to be left - so they are drawn here too, to prove it. */
    stroke_rect(layout->hand.left, layout->hand.top,
                layout->hand.width, layout->hand.height, INK);
    stroke_rect(layout->readout.left, layout->readout.top,
                layout->readout.width, layout->readout.height, DIM);
}

/*
 * The top screen, twice.
 *
 * `depth` is signed: negative for the left eye, positive for the right. A
 * larger shift reads as *nearer*, so the card floats out of the glass and the
 * board sits behind it. Phase 2 replaces this with a real asymmetric frustum;
 * what it inherits is the sign convention, which is the half that is easy to
 * get backwards and invisible when you do - you just get a headache.
 */
static void draw_stage(float depth, Label *title, Label *status) {
    const float cx = TOP_W / 2.0f;

    /* The table's horizon, far away: shifted least. */
    C2D_DrawRectSolid(0.0f, 150.0f + depth * 0.2f, 0.0f, TOP_W, 1.0f, DIM);

    /* Three cards fanned, nearer than the table, so they shift more. */
    for (int i = -1; i <= 1; ++i) {
        float w = 46.0f, h = w / 0.686f;
        float x = cx + (float)i * 52.0f - w / 2.0f + depth * 1.6f;
        float y = 96.0f - (i == 0 ? 8.0f : 0.0f);
        C2D_DrawRectSolid(x, y, 0.0f, w, h, INK);
        /* The foil is inset inside the card, which is what makes it a foil. */
        C2D_DrawRectSolid(x + 2.0f, y + 2.0f, 0.0f, w - 4.0f, 2.0f,
                          i < 0 ? FOIL_A : FOIL_B);
        C2D_DrawRectSolid(x + 2.0f, y + h - 4.0f, 0.0f, w - 4.0f, 2.0f,
                          i < 0 ? FOIL_B : FOIL_A);
    }

    C2D_DrawText(&title->text, C2D_WithColor, 8.0f + depth * 0.4f, 8.0f, 0.0f,
                 0.62f, 0.62f, INK);
    C2D_DrawText(&status->text, C2D_WithColor, 8.0f, 214.0f, 0.0f,
                 0.44f, 0.44f, DIM);
}

int main(void) {
    /*
     * app.rsf asks for a New 3DS (SystemModeExt 124MB), so an Old 3DS will not
     * reach this code at all. The call is still made rather than assumed,
     * because the clock is not on by default even when the hardware has it -
     * homebrew stopped getting the speedup automatically years ago.
     */
    bool is_new_3ds = false;
    APT_CheckNew3DS(&is_new_3ds);
    if (is_new_3ds) osSetSpeedupEnable(true);

    gfxInitDefault();
    gfxSet3D(true);
    C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
    C2D_Init(C2D_DEFAULT_MAX_OBJECTS);
    C2D_Prepare();
    romfsInit();

    C3D_RenderTarget *top_left  = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
    C3D_RenderTarget *top_right = C2D_CreateScreenTarget(GFX_TOP, GFX_RIGHT);
    C3D_RenderTarget *bottom    = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    Label title = { C2D_TextBufNew(64), {0} };
    Label status = { C2D_TextBufNew(256), {0} };
    Label readout = { C2D_TextBufNew(256), {0} };
    label_set(&title, "kai's master tool");

    /*
     * The bottom screen's own board. Growth is 1 and roomAbove is 0: an
     * orthographic map has no near edge to grow and no room behind it to see.
     */
    MtBoardLayout layout =
        mt_board_solve((float)BOTTOM_W, (float)SCREEN_H, 0.686f, 1.0f, 0.0f);

    const MtPlacedSlot *hit = NULL;
    char status_text[192];
    char readout_text[192];

    snprintf(status_text, sizeof status_text,
             "v%s  %s  card %.1fpx  %s",
             MT_VERSION,
             is_new_3ds ? "New3DS 804MHz" : "3DS",
             (double)layout.card_width,
             layout.fits ? "fits" : "TOO SMALL");
    label_set(&status, status_text);
    label_set(&readout, "touch the board");

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        if (down & KEY_START) break;

        if (hidKeysHeld() & KEY_TOUCH) {
            touchPosition touch;
            hidTouchRead(&touch);
            /* One pointer, one gesture. The tablet needs ten lanes behind an
             * arbiter because ten fingers can land; a stylus cannot, so
             * `MatDesk`'s router collapses to this. Its *rules* still port -
             * the grace window, stillHolds, rebase - just not its fan-out. */
            hit = mt_board_slot_at(&layout, (float)touch.px, (float)touch.py);

            MtDropIntent intent = hit
                ? mt_drop_zone(hit->slot, (MtMatPoint){
                      (touch.px - layout.field.left) / layout.field.width,
                      (touch.py - layout.field.top) / layout.field.height })
                : mt_drop_free((MtMatPoint){ 0.5f, 0.5f });

            /* Setting a monster and setting a spell are the same motion of the
             * hand; the zone answers. Held R means "set", the modifier the
             * two-finger drag becomes when there is only one pointer. */
            MtCardPosition set = mt_set_position(
                true, (hidKeysHeld() & KEY_R) != 0, &intent, MT_MONSTER_UNKNOWN);

            snprintf(readout_text, sizeof readout_text, "%s  %s",
                     mt_drop_label(intent),
                     set == MT_POS_FACE_DOWN_DEF ? "set sideways" : "set upright");
            label_set(&readout, readout_text);
        }

        float slider = osGet3DSliderState();
        float iod = slider * 3.0f;   /* pixels of parallax at full slider */

        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);

        C2D_TargetClear(top_left, TRUE_BLACK);
        C2D_SceneBegin(top_left);
        draw_stage(-iod, &title, &status);

        if (iod > 0.0f) {
            C2D_TargetClear(top_right, TRUE_BLACK);
            C2D_SceneBegin(top_right);
            draw_stage(+iod, &title, &status);
        }

        C2D_TargetClear(bottom, TRUE_BLACK);
        C2D_SceneBegin(bottom);
        draw_board(&layout, hit);
        C2D_DrawText(&readout.text, C2D_WithColor, 6.0f, 226.0f, 0.0f,
                     0.42f, 0.42f, INK);

        C3D_FrameEnd(0);
    }

    C2D_TextBufDelete(readout.buf);
    C2D_TextBufDelete(status.buf);
    C2D_TextBufDelete(title.buf);
    romfsExit();
    C2D_Fini();
    C3D_Fini();
    gfxExit();
    return 0;
}
