/*
 * The hand as it is actually drawn - the C form of `core/layout/HandFan.kt`.
 *
 * A row of places, left to right: each is a hand index, or held **open** for a
 * card about to land in it. That second kind is the whole point. A caret
 * painted in a gap says where a card will go; a gap that is actually there
 * shows you.
 *
 * ## One row, four readings
 *
 * The pose, the hit box, the insert index and the indicator are four readings
 * of the same row, and on the tablet they were four *reconstructions* - which
 * is exactly how they came to disagree. The hand was drawn with a place for the
 * card in the air and measured as though that place had closed up, so every gap
 * the caret named was 0.37 of a card width from the gap the card went into.
 * `MtHandRow` is the row itself, so "how many places are there" has one answer.
 *
 * The 3DS draws the hand as a strip rather than a fan, because the lean existed
 * to sell three dimensions and the top screen is where the depth actually is.
 * The row is still this: a strip with a gap open in it is the same question as
 * a fan with a gap open in it, and the arithmetic that answers it is the same.
 */
#ifndef MT_HANDFAN_H
#define MT_HANDFAN_H

#include <stdbool.h>

#include "mt_board_layout.h"
#include "mt_types.h"

/* A combo line routinely holds fourteen; this is room for a hand plus gaps. */
#define MT_MAX_HAND_PLACES 40

/** A place held open, where a card is about to land. */
#define MT_HAND_OPEN (-1)

/** How far apart two neighbours sit, as a fraction of a card - the shipped tuning. */
#define MT_HAND_STEP_FRACTION 0.74f

typedef struct {
    /** A hand index, or MT_HAND_OPEN. */
    int places[MT_MAX_HAND_PLACES];
    int count;
} MtHandRow;

/** A plain hand of `count` cards, nothing in the air and nothing open. */
MtHandRow mt_hand_row_of(int count);

/**
 * The row a hand is drawn as, given what is in the air and what is landing.
 *
 * `lifted` are hand indices being carried; their places close up, because
 * taking a card out of your hand and the rest closing behind it is what a hand
 * does. `opening` are gaps in the full hand's numbering that a card is about to
 * land in - one place is held open for each, so a card dragged along its own
 * hand sees its place travel with it rather than a caret appearing in a row
 * that never moved.
 */
MtHandRow mt_hand_row(int count,
                      const int *lifted, int lifted_count,
                      const int *opening, int opening_count);

/** Where hand card `hand_index` is drawn, or -1 while it is in the air. */
int mt_hand_place_of(const MtHandRow *row, int hand_index);

/**
 * The gap, in the full hand's numbering, after `ahead` of the drawn cards.
 *
 * Which is the index of the next drawn card - "before that one" - or the end of
 * the hand when there is no next one.
 */
int mt_hand_gap_after(const MtHandRow *row, int ahead, int count);

/**
 * The place held open for gap `at`, or -1.
 *
 * Asked as a predicate - every drawn card before it belongs before `at`, every
 * one after it belongs after - rather than by naming the gap arithmetically,
 * because two gap numbers can be the same physical place. With card 0 in the
 * air, gaps 0 and 1 are both the front of the row.
 */
int mt_hand_opening_for(const MtHandRow *row, int at, int count);

float mt_hand_step(MtSlot band, float card_width, int count, float step_fraction);

/** The centre of place `place` of `places`, in mat pixels. */
float mt_hand_centre_of(MtSlot band, float card_width,
                        int place, int places, float step_fraction);

/**
 * Which position in the hand a card released at `x` is asking to take.
 *
 * The inverse of `mt_hand_centre_of`, answering in *gaps* rather than in cards:
 * zero is before everything, `count` is after everything. That is the number an
 * insert takes, which is the whole reason it is phrased this way - an answer in
 * "which card is nearest" needs a before-or-after decision at every call site,
 * and one of them would get it wrong.
 *
 * **Counted rather than divided**, which is what makes it the true inverse of
 * the row it measures. Dividing by the step describes a row of evenly spaced
 * cards, and a row with a place held open in it is not evenly spaced. Counting
 * how many drawn cards lie left of the pointer is exact for any arrangement,
 * needs no correction, and settles in one pass: the gap it names is the gap the
 * row is already holding open.
 */
int mt_hand_insert_at(MtSlot band, float card_width, const MtHandRow *row,
                      int count, float x, float step_fraction);

/** Where place `place` of `places` sits, in the mat's own fractions. */
MtMatPoint mt_hand_point_for(const MtBoardLayout *layout,
                             int place, int places, float step_fraction);

#endif /* MT_HANDFAN_H */
