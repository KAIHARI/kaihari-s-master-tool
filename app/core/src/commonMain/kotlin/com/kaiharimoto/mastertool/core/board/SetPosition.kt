package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot

/**
 * Which way up, and which way round, a card being put down is lying.
 *
 * Four positions, and three things that have an opinion about them: whether the
 * card was turned over in the air, whether it was twisted, and where it is
 * landing. Written once here because the answer is needed twice — the pose of
 * the card in the air, so that what you see is what lands, and the release that
 * commits it — and two readings of one function cannot disagree.
 *
 * ## Why the destination gets a vote
 *
 * Setting a monster and setting a spell are the same motion of the hand and two
 * different results: a set monster lies sideways in defence, a set spell or trap
 * stands upright. Nobody twists their wrist to say which; the card *is* one or
 * the other, and the zone it goes into says so out loud. So a two-finger drag
 * into a monster zone lands horizontal, the same drag into a spell/trap zone
 * lands vertical, and dropped anywhere else on the felt the card's own category
 * answers — which is the same answer, arrived at from the card rather than from
 * the table.
 *
 * A **deliberate twist always wins**. Turning a card with two fingers is the
 * user saying which way round it goes, and a convenience that overrules a person
 * who has just told you the answer is not a convenience.
 */
object SetPosition {

    /**
     * @param faceDown whether the card was turned over in the air
     * @param turned whether the gesture twisted it an odd number of quarter turns
     * @param intent where letting go would put it
     * @param monster whether the card itself is a monster, when that is known —
     *   null for a card the index has never heard of, which resolves the way a
     *   spell does rather than by guessing
     */
    fun of(
        faceDown: Boolean,
        turned: Boolean,
        intent: DropIntent? = null,
        monster: Boolean? = null,
    ): CardPosition {
        // Said with the fingers, so nothing below gets to argue.
        if (turned) {
            return if (faceDown) CardPosition.FACE_DOWN_DEF else CardPosition.FACE_UP_DEF
        }
        if (!faceDown) return CardPosition.FACE_UP_ATK

        val sideways = when (val zone = (intent as? DropIntent.Zone)?.slot) {
            // The board has been asked and the board has answered.
            is BoardSlot.Zone -> when (zone.zone) {
                is FieldZone.Monster, is FieldZone.ExtraMonster -> true
                is FieldZone.SpellTrap, FieldZone.FieldSpell -> false
            }
            // Free on the felt, onto a stack, into a pile: no zone to ask, so
            // the card speaks for itself.
            else -> monster == true
        }

        return if (sideways) CardPosition.FACE_DOWN_DEF else CardPosition.FACE_DOWN_ATK
    }
}
