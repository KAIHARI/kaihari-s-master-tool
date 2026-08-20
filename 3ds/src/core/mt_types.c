#include "mt_types.h"

MtMatPoint mt_mat_point_clamped(MtMatPoint p, float margin) {
    MtMatPoint out = p;
    float lo = margin;
    float hi = 1.0f - margin;
    /* Kotlin's coerceIn throws when lo > hi; here the caller gets the midpoint
     * rather than a crash on a console with no exception to catch. */
    if (lo > hi) { out.x = 0.5f; out.y = 0.5f; return out; }
    if (out.x < lo) out.x = lo; else if (out.x > hi) out.x = hi;
    if (out.y < lo) out.y = lo; else if (out.y > hi) out.y = hi;
    return out;
}

const char *mt_phase_label(MtDuelPhase p) {
    switch (p) {
        case MT_PHASE_DRAW:    return "Draw";
        case MT_PHASE_STANDBY: return "Standby";
        case MT_PHASE_MAIN1:   return "Main 1";
        case MT_PHASE_BATTLE:  return "Battle";
        case MT_PHASE_MAIN2:   return "Main 2";
        case MT_PHASE_END:     return "End";
        default:               return "";
    }
}

MtFieldZone mt_zone(MtFieldZoneKind kind, int index) {
    MtFieldZone z;
    z.kind = kind;
    /* The field spell zone has no index, and writing one would make two
     * otherwise-identical zones compare unequal. */
    z.index = (kind == MT_ZONE_FIELD_SPELL) ? 0 : index;
    return z;
}

MtBoardSlot mt_slot_zone(MtFieldZone zone) {
    MtBoardSlot s;
    s.kind = MT_SLOT_ZONE;
    s.zone = zone;
    return s;
}

MtBoardSlot mt_slot_pile(MtBoardSlotKind kind) {
    MtBoardSlot s;
    s.kind = kind;
    s.zone = mt_zone(MT_ZONE_MONSTER, 0);   /* zeroed, never read */
    return s;
}

bool mt_board_slot_eq(MtBoardSlot a, MtBoardSlot b) {
    if (a.kind != b.kind) return false;
    if (a.kind != MT_SLOT_ZONE) return true;
    return a.zone.kind == b.zone.kind && a.zone.index == b.zone.index;
}
