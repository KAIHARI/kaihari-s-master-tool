#include "mt_drop.h"

#include <stddef.h>

const char *mt_drop_label(MtDropIntent intent) {
    switch (intent.kind) {
        case MT_DROP_FREE:       return "Place";
        case MT_DROP_ZONE:       return "Zone";
        case MT_DROP_STACK:      return "Stack";
        case MT_DROP_ATTACH:     return "Attach";
        case MT_DROP_HAND:       return "Hand";
        case MT_DROP_GRAVEYARD:  return "Graveyard";
        case MT_DROP_BANISH:     return "Banish";
        case MT_DROP_DECK:       return "Deck";
        case MT_DROP_EXTRA_DECK: return "Extra deck";
        /* Said as the thing it does rather than as the thing it declines to do.
         * It is reachable on purpose — put a card back in the spread you took it
         * out of — and "Cancel" describes a gesture failing. */
        case MT_DROP_CANCEL:     return "Put back";
        default:                 return "";
    }
}

MtCardPosition mt_set_position(bool face_down,
                               bool turned,
                               const MtDropIntent *intent,
                               MtMonsterHint monster) {
    /* Said with the fingers, so nothing below gets to argue. */
    if (turned) {
        return face_down ? MT_POS_FACE_DOWN_DEF : MT_POS_FACE_UP_DEF;
    }
    if (!face_down) return MT_POS_FACE_UP_ATK;

    bool sideways;
    if (intent != NULL && intent->kind == MT_DROP_ZONE
        && intent->slot.kind == MT_SLOT_ZONE) {
        /* The board has been asked and the board has answered. */
        sideways = mt_zone_is_monster(intent->slot.zone);
    } else {
        /* Free on the felt, onto a stack, into a pile: no zone to ask, so the
         * card speaks for itself. An unknown card resolves the way a spell
         * does rather than by guessing. */
        sideways = (monster == MT_MONSTER_YES);
    }

    return sideways ? MT_POS_FACE_DOWN_DEF : MT_POS_FACE_DOWN_ATK;
}
