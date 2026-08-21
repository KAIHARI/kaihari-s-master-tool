#include "mt_handfan.h"

MtHandRow mt_hand_row_of(int count) {
    MtHandRow row;
    row.count = 0;
    if (count < 0) count = 0;
    for (int i = 0; i < count && row.count < MT_MAX_HAND_PLACES; ++i) {
        row.places[row.count++] = i;
    }
    return row;
}

MtHandRow mt_hand_row(int count,
                      const int *lifted, int lifted_count,
                      const int *opening, int opening_count) {
    MtHandRow row;
    row.count = 0;
    if (count < 0) count = 0;

    /* The shown cards: everything not currently in the air. */
    int shown[MT_MAX_HAND_PLACES];
    int shown_count = 0;
    for (int i = 0; i < count && shown_count < MT_MAX_HAND_PLACES; ++i) {
        bool in_air = false;
        for (int k = 0; k < lifted_count; ++k) if (lifted[k] == i) { in_air = true; break; }
        if (!in_air) shown[shown_count++] = i;
    }

    if (opening_count <= 0) {
        for (int i = 0; i < shown_count; ++i) row.places[row.count++] = shown[i];
        return row;
    }

    /* Sorted, because the walk below consumes them in order. Insertion sort:
     * there are at most ten of these, one per lane of the tablet's arbiter. */
    int gaps[MT_MAX_HAND_PLACES];
    int gap_count = 0;
    for (int i = 0; i < opening_count && gap_count < MT_MAX_HAND_PLACES; ++i) {
        int value = opening[i];
        int at = gap_count;
        while (at > 0 && gaps[at - 1] > value) { gaps[at] = gaps[at - 1]; --at; }
        gaps[at] = value;
        ++gap_count;
    }

    int next = 0;
    for (int i = 0; i < shown_count; ++i) {
        while (next < gap_count && gaps[next] <= shown[i] && row.count < MT_MAX_HAND_PLACES) {
            row.places[row.count++] = MT_HAND_OPEN;
            ++next;
        }
        if (row.count < MT_MAX_HAND_PLACES) row.places[row.count++] = shown[i];
    }
    while (next < gap_count && row.count < MT_MAX_HAND_PLACES) {
        row.places[row.count++] = MT_HAND_OPEN;
        ++next;
    }
    return row;
}

int mt_hand_place_of(const MtHandRow *row, int hand_index) {
    for (int i = 0; i < row->count; ++i) {
        if (row->places[i] == hand_index) return i;
    }
    return -1;
}

int mt_hand_gap_after(const MtHandRow *row, int ahead, int count) {
    int drawn = 0;
    for (int i = 0; i < row->count; ++i) {
        if (row->places[i] == MT_HAND_OPEN) continue;
        if (drawn == ahead) return row->places[i];
        ++drawn;
    }
    return count;
}

int mt_hand_opening_for(const MtHandRow *row, int at, int count) {
    if (at < 0 || at > count) return -1;
    for (int place = 0; place < row->count; ++place) {
        if (row->places[place] != MT_HAND_OPEN) continue;

        bool ok = true;
        for (int i = 0; i < place && ok; ++i) {
            int card = row->places[i];
            if (card != MT_HAND_OPEN && card >= at) ok = false;
        }
        for (int i = place + 1; i < row->count && ok; ++i) {
            int card = row->places[i];
            if (card != MT_HAND_OPEN && card < at) ok = false;
        }
        if (ok) return place;
    }
    return -1;
}

float mt_hand_step(MtSlot band, float card_width, int count, float step_fraction) {
    if (count <= 1) return 0.0f;
    float capped = card_width * step_fraction;
    float fitted = (band.width - card_width) / (float)(count - 1);
    return capped < fitted ? capped : fitted;
}

float mt_hand_centre_of(MtSlot band, float card_width,
                        int place, int places, float step_fraction) {
    float step = mt_hand_step(band, card_width, places, step_fraction);
    float spread = card_width + step * (float)(places - 1);
    return band.left + (band.width - spread) / 2.0f + card_width / 2.0f
         + (float)place * step;
}

int mt_hand_insert_at(MtSlot band, float card_width, const MtHandRow *row,
                      int count, float x, float step_fraction) {
    if (card_width <= 0.0f || row->count == 0) return 0;
    int ahead = 0;
    for (int place = 0; place < row->count; ++place) {
        if (row->places[place] == MT_HAND_OPEN) continue;
        if (mt_hand_centre_of(band, card_width, place, row->count, step_fraction) < x) ++ahead;
    }
    return mt_hand_gap_after(row, ahead, count);
}

MtMatPoint mt_hand_point_for(const MtBoardLayout *layout,
                             int place, int places, float step_fraction) {
    MtSlot band = layout->hand;
    float x = mt_hand_centre_of(band, layout->card_width, place, places, step_fraction);

    MtMatPoint p;
    p.x = (layout->field.width > 0.0f)
        ? (x - layout->field.left) / layout->field.width : 0.5f;
    p.y = (layout->field.height > 0.0f)
        ? (mt_slot_centre_y(band) - layout->field.top) / layout->field.height : 1.0f;
    return p;
}
