/*
 * The stage, drawn with real geometry on the PICA200.
 *
 * ## What this replaces, and what it deletes
 *
 * On the tablet a card reaches the screen through `graphicsLayer` plus a 2-D
 * canvas, joined by `StagePlane.flatten` - an apparatus that exists because
 * Compose has no real 3-D. citro3d has matrices. So `flatten`, `raise`,
 * `CarryHeight` and `Homography`'s renderer role are not ported: a card is a
 * box, the camera is a projection matrix, and the depth buffer sorts them.
 *
 * ## The top screen is two pictures
 *
 * `Mtx_PerspStereoTilt` builds a projection carrying the interaxial offset, so
 * the two eyes differ in the projection matrix and in nothing else. The `Tilt`
 * in that name is not about the table: the 3DS's screens are physically mounted
 * rotated ninety degrees, so every framebuffer is 240x400 portrait and the
 * whole scene is drawn turned. It is the tilt variants that put it back.
 *
 * The interaxial comes from `osGet3DSliderState`, which is the user's own
 * comfort control and is the only correct source for it. At the slider's zero
 * the right eye is not drawn at all.
 */
#ifndef MT_GFX_H
#define MT_GFX_H

#include <stdbool.h>

#include "../core/mt_board_layout.h"
#include "../core/mt_types.h"

#define MT_TOP_W    400
#define MT_BOTTOM_W 320
#define MT_SCREEN_H 240

typedef enum { MT_EYE_LEFT = 0, MT_EYE_RIGHT } MtEye;

/** Where the camera is, in the mat's own units. */
typedef struct {
    /** Degrees above the felt. 90 is straight down, 32 is kai's POV seat. */
    float elevation;
    /** Degrees the table is turned on the spot. */
    float spin;
    /** How far back, in mat widths. */
    float distance;
    /** What it is aimed at, in mat fractions. */
    MtMatPoint target;
} MtCamera;

/** A card on the table, as the renderer needs it. */
typedef struct {
    MtMatPoint at;
    MtCardPosition position;
    /** How far off the felt, in card widths. Zero for a card lying down. */
    float lift;
    /** How many cards are under it, so a pile has a visible height. */
    int pile_depth;
    /** Highlighted - the one the stylus is on. */
    bool lit;
} MtStageCard;

bool mt_gfx_init(void);
void mt_gfx_exit(void);

/**
 * Begins one eye of the top screen.
 *
 * `slider` is `osGet3DSliderState()`. Returns false for the right eye when the
 * slider is at zero, which is the caller's signal to skip that pass entirely
 * rather than draw an identical picture twice.
 */
bool mt_gfx_begin_stage(MtEye eye, float slider, const MtCamera *camera);

/**
 * Tells the renderer what shape the table is.
 *
 * Separate from drawing because the camera reads these metrics too, and a
 * value set during the draw is a value the camera used the previous frame's
 * copy of. Call it whenever the layout is solved, which is once.
 */
void mt_gfx_use_layout(const MtBoardLayout *layout);

/** The desk the mat lies on, and the mat's ruled zones. */
void mt_gfx_draw_table(const MtBoardLayout *layout);

/** One card, as a solid with a thickness. */
void mt_gfx_draw_card(const MtStageCard *card);

/** Binds citro2d's state back, for the bottom screen. */
void mt_gfx_begin_2d(void);

#endif /* MT_GFX_H */
