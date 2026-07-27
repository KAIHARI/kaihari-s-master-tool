package com.kaiharimoto.mastertool.core

import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId

/** Fixtures modelled on real cards so the rules are exercised against real shapes. */
object TestCards {

    fun monster(
        id: Int,
        name: String,
        frameType: String = "effect",
        type: String = "Effect Monster",
        level: Int? = 4,
        attribute: Attribute = Attribute.DARK,
        race: String? = "Spellcaster",
        atk: Int? = 1500,
        def: Int? = 1200,
        tcg: BanStatus = BanStatus.UNLIMITED,
        archetype: String? = null,
        alternateIds: List<CardId> = emptyList(),
    ) = Card(
        id = CardId(id),
        name = name,
        type = type,
        frameType = frameType,
        level = level,
        attribute = attribute,
        race = race,
        atk = atk,
        def = def,
        tcgBanStatus = tcg,
        archetype = archetype,
        alternateIds = alternateIds,
    )

    val ashBlossom = monster(
        id = 14558127,
        name = "Ash Blossom & Joyous Spring",
        level = 3,
        attribute = Attribute.FIRE,
        race = "Zombie",
        atk = 0,
        def = 1800,
        tcg = BanStatus.LIMITED,
    )

    val nibiru = monster(
        id = 27204311,
        name = "Nibiru, the Primal Being",
        level = 11,
        attribute = Attribute.LIGHT,
        race = "Rock",
        atk = 3000,
        def = 600,
    )

    val maxxC = monster(
        id = 23434538,
        name = "Maxx \"C\"",
        level = 2,
        attribute = Attribute.EARTH,
        race = "Insect",
        atk = 500,
        def = 200,
        tcg = BanStatus.FORBIDDEN,
    )

    /** Extra Deck card with an alternate artwork passcode. */
    val accesscode = monster(
        id = 86066372,
        name = "Accesscode Talker",
        frameType = "link",
        type = "Link Monster",
        level = null,
        attribute = Attribute.DARK,
        race = "Cyberse",
        atk = 2300,
        def = null,
        alternateIds = listOf(CardId(86066372), CardId(11111111)),
    )

    /** Pendulum + Fusion hybrid: reads as Pendulum, belongs in the Extra Deck. */
    val pendulumFusion = monster(
        id = 60793587,
        name = "Supreme King Z-ARC",
        frameType = "fusion_pendulum",
        type = "Pendulum Effect Fusion Monster",
        level = 12,
        atk = 4000,
        def = 4000,
    )

    val pot = Card(
        id = CardId(55144522),
        name = "Pot of Prosperity",
        type = "Spell Card",
        frameType = "spell",
        race = "Normal",
    )

    val infiniteImpermanence = Card(
        id = CardId(10045474),
        name = "Infinite Impermanence",
        type = "Trap Card",
        frameType = "trap",
        race = "Normal",
    )

    val token = Card(
        id = CardId(73915052),
        name = "Sheep Token",
        type = "Token",
        frameType = "token",
    )

    val all = listOf(
        ashBlossom, nibiru, maxxC, accesscode, pendulumFusion, pot, infiniteImpermanence, token,
    )
}
