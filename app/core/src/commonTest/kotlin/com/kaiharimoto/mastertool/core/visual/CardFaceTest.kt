package com.kaiharimoto.mastertool.core.visual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameKindTest {

    @Test
    fun everyFrameTheCardDatabaseEmits() {
        // Taken from ygoprodeck's `frameType`. Anything not here is a card type
        // that did not exist when this was written, and lands on OTHER rather
        // than on a wrong colour.
        val expected = mapOf(
            "normal" to FrameKind.NORMAL,
            "effect" to FrameKind.EFFECT,
            "ritual" to FrameKind.RITUAL,
            "fusion" to FrameKind.FUSION,
            "synchro" to FrameKind.SYNCHRO,
            "xyz" to FrameKind.XYZ,
            "link" to FrameKind.LINK,
            "spell" to FrameKind.SPELL,
            "trap" to FrameKind.TRAP,
            "token" to FrameKind.TOKEN,
            "skill" to FrameKind.OTHER,
        )

        expected.forEach { (frame, kind) ->
            assertEquals(kind, CardFace.kindOf(frame), frame)
        }
    }

    @Test
    fun aPendulumIsDrawnAsWhateverItsOtherHalfIs() {
        // The half that says where the card is summoned from wins, which is the
        // same rule `Card.isExtraDeck` uses -- and it is the one that matters,
        // because it is what decides which pane the card is in.
        assertEquals(FrameKind.NORMAL, CardFace.kindOf("normal_pendulum"))
        assertEquals(FrameKind.EFFECT, CardFace.kindOf("effect_pendulum"))
        assertEquals(FrameKind.FUSION, CardFace.kindOf("fusion_pendulum"))
        assertEquals(FrameKind.SYNCHRO, CardFace.kindOf("synchro_pendulum"))
        assertEquals(FrameKind.XYZ, CardFace.kindOf("xyz_pendulum"))
        assertEquals(FrameKind.RITUAL, CardFace.kindOf("ritual_pendulum"))
    }

    @Test
    fun theFrameIsReadLooselyBecauseItIsAStringFromAnApi() {
        assertEquals(FrameKind.EFFECT, CardFace.kindOf("  EFFECT  "))
        assertEquals(FrameKind.SPELL, CardFace.kindOf("Spell"))
        assertEquals(FrameKind.OTHER, CardFace.kindOf(""))
        assertEquals(FrameKind.OTHER, CardFace.kindOf("something new"))
    }

    @Test
    fun onlyTheFramesThatCarryStatsCountAsMonsters() {
        listOf(
            FrameKind.NORMAL,
            FrameKind.EFFECT,
            FrameKind.RITUAL,
            FrameKind.FUSION,
            FrameKind.SYNCHRO,
            FrameKind.XYZ,
            FrameKind.LINK,
            FrameKind.TOKEN,
        ).forEach { assertTrue(CardFace.isMonster(it), "$it") }

        listOf(FrameKind.SPELL, FrameKind.TRAP, FrameKind.OTHER).forEach {
            assertFalse(CardFace.isMonster(it), "$it")
        }
    }
}

class FacePipsTest {

    @Test
    fun aLevelIsThatManyStars() {
        assertEquals(4, CardFace.pips(4))
        assertEquals(1, CardFace.pips(1))
    }

    @Test
    fun aLinkMonsterHasNoLevelAndSoNoStars() {
        assertEquals(0, CardFace.pips(null))
    }

    @Test
    fun anImpossibleLevelStillFitsOnTheCard() {
        assertEquals(CardFace.MAX_PIPS, CardFace.pips(13))
        assertEquals(CardFace.MAX_PIPS, CardFace.pips(99))
        assertEquals(0, CardFace.pips(-1), "a negative level is nonsense, not a crash")
    }
}

class FaceShapesTest {

    private val ash = 14558127
    private val maxx = 23434538

    @Test
    fun aCardIsTheSamePictureEveryTimeItIsDrawn() {
        // The load-bearing property. A field that moved between draws would make
        // a deck flicker while it was being scrolled, and would make the PNG
        // exported of an arrangement disagree with the arrangement.
        assertEquals(CardFace.shapes(ash), CardFace.shapes(ash))
        assertEquals(CardFace.angle(ash), CardFace.angle(ash))
    }

    @Test
    fun twoCardsAreTwoPictures() {
        assertTrue(CardFace.shapes(ash) != CardFace.shapes(maxx))
    }

    @Test
    fun everyShapeSitsInsideTheArtWindow() {
        listOf(0, 1, ash, maxx, 99999999, Int.MAX_VALUE).forEach { seed ->
            CardFace.shapes(seed, count = 40).forEach {
                assertTrue(it.x in 0f..1f, "x $it for $seed")
                assertTrue(it.y in 0f..1f, "y $it for $seed")
                assertTrue(it.size in 0.20f..0.60f, "size $it for $seed")
                assertTrue(it.turns in 0f..0.5f, "turns $it for $seed")
                assertTrue(it.alpha in 0.05f..0.14f, "alpha $it for $seed")
            }
        }
    }

    @Test
    fun aPasscodeOfZeroIsStillAPicture() {
        // Zero is a fixed point of the step, so without the guard every shape
        // would land on top of the last one.
        val shapes = CardFace.shapes(0)

        assertEquals(CardFace.SHAPES, shapes.size)
        assertEquals(shapes.size, shapes.distinct().size, "four shapes, not one drawn four times")
    }

    @Test
    fun theAngleAndTheShapesAreDifferentDrawsFromTheSameCard() {
        // The shapes burn the angle first. If they did not, every card's first
        // shape would sit at the same fraction across as its wash angle -- a
        // correlation nobody would see and everybody would feel.
        val shapes = CardFace.shapes(ash)

        assertTrue(CardFace.angle(ash) != shapes.first().x)
    }

    @Test
    fun askingForNoShapesIsAnEmptyWindowRatherThanACrash() {
        assertEquals(emptyList(), CardFace.shapes(ash, count = 0))
        assertEquals(emptyList(), CardFace.shapes(ash, count = -3))
    }

    @Test
    fun theWashAngleIsAFractionOfATurn() {
        listOf(0, 1, ash, maxx, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertTrue(CardFace.angle(it) in 0f..1f, "$it")
        }
    }
}
