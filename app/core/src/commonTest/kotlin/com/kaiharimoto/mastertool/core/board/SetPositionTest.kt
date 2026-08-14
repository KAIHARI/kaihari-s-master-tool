package com.kaiharimoto.mastertool.core.board

import com.kaiharimoto.mastertool.core.layout.BoardSlot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which way up, and which way round, a card being put down is lying.
 *
 * The interesting half is that the *destination* has a vote. Setting a monster
 * and setting a spell are the same motion of the hand and two different results
 * — sideways in defence, upright in the spell/trap row — and nobody twists their
 * wrist to say which. The zone says so, and off the zones the card says so.
 */
class SetPositionTest {

    private fun zone(zone: FieldZone) = DropIntent.Zone(BoardSlot.Zone(zone), MatPoint.Centre)

    // ---- nothing said, nothing done -------------------------------------------

    @Test
    fun aPlainDropLandsFaceUpAndUpright() {
        assertEquals(
            CardPosition.FACE_UP_ATK,
            SetPosition.of(faceDown = false, turned = false),
        )
    }

    // ---- the fingers, which always win ----------------------------------------

    @Test
    fun aTwistAloneIsAnAttackPositionCardTurnedSideways() {
        assertEquals(
            CardPosition.FACE_UP_DEF,
            SetPosition.of(faceDown = false, turned = true),
        )
    }

    @Test
    fun aTwistBeatsTheZone() {
        // Somebody who has just turned a card with two fingers has said which
        // way round it goes. A convenience that overrules them is not one.
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(
                faceDown = true,
                turned = true,
                intent = zone(FieldZone.SpellTrap(0)),
                monster = false,
            ),
        )
    }

    // ---- the zone -------------------------------------------------------------

    @Test
    fun setInAMonsterZoneItLiesSideways() {
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(faceDown = true, turned = false, intent = zone(FieldZone.Monster(2))),
        )
    }

    @Test
    fun setInTheExtraMonsterZoneItLiesSidewaysToo() {
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = zone(FieldZone.ExtraMonster(0)),
            ),
        )
    }

    @Test
    fun setInASpellTrapZoneItStandsUpright() {
        assertEquals(
            CardPosition.FACE_DOWN_ATK,
            SetPosition.of(faceDown = true, turned = false, intent = zone(FieldZone.SpellTrap(3))),
        )
    }

    @Test
    fun setInTheFieldZoneItStandsUprightAsWell() {
        assertEquals(
            CardPosition.FACE_DOWN_ATK,
            SetPosition.of(faceDown = true, turned = false, intent = zone(FieldZone.FieldSpell)),
        )
    }

    @Test
    fun theZoneOutranksTheCard() {
        // A monster dropped into a spell/trap zone is a thing the freeform table
        // allows, and the zone is the more specific statement: the user aimed at
        // it. Nothing here is enforcing the rules of the game.
        assertEquals(
            CardPosition.FACE_DOWN_ATK,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = zone(FieldZone.SpellTrap(0)),
                monster = true,
            ),
        )
    }

    // ---- off the zones, where the card speaks for itself -----------------------

    @Test
    fun aMonsterSetOnBareFeltLiesSideways() {
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = DropIntent.Free(MatPoint.Centre),
                monster = true,
            ),
        )
    }

    @Test
    fun aSpellSetOnBareFeltStandsUpright() {
        assertEquals(
            CardPosition.FACE_DOWN_ATK,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = DropIntent.Free(MatPoint.Centre),
                monster = false,
            ),
        )
    }

    @Test
    fun aMonsterSetOntoAStackLiesSideways() {
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = DropIntent.Stack(4),
                monster = true,
            ),
        )
    }

    @Test
    fun aCardNobodyCanIdentifyStandsUpright() {
        // Null is a real answer — a passcode the index has never heard of — and
        // it resolves the way a spell does rather than by guessing at a defence
        // position for a card that might not have one.
        assertEquals(
            CardPosition.FACE_DOWN_ATK,
            SetPosition.of(
                faceDown = true,
                turned = false,
                intent = DropIntent.Free(MatPoint.Centre),
                monster = null,
            ),
        )
    }

    @Test
    fun withNoIntentAtAllTheCardStillDecides() {
        // The frame a card is lifted on, before anything has resolved.
        assertEquals(
            CardPosition.FACE_DOWN_DEF,
            SetPosition.of(faceDown = true, turned = false, monster = true),
        )
    }
}
