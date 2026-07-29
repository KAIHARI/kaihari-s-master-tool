package com.kaiharimoto.mastertool.core.deck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatChoiceTest {

    @Test
    fun aDeckNobodyDressedFollowsTheApplication() {
        assertEquals(MatChoice.THEME, MatChoice.DEFAULT)
        assertEquals(MatChoice.THEME, MatChoice.byKey(null))
    }

    @Test
    fun aNameThatMeansNothingHereFallsBackRatherThanFailing() {
        // Files are written by other programs and edited by hand.
        assertEquals(MatChoice.THEME, MatChoice.byKey("chartreuse"))
        assertEquals(MatChoice.THEME, MatChoice.byKey(""))
    }

    @Test
    fun theNameIsReadHowSomebodyWouldHaveTypedIt() {
        assertEquals(MatChoice.WINE, MatChoice.byKey("  WINE "))
    }

    @Test
    fun everyMatHasAKeyOfItsOwn() {
        assertEquals(MatChoice.entries.size, MatChoice.entries.map { it.key }.toSet().size)
        assertTrue(MatChoice.entries.all { it.key.isNotBlank() })
    }
}

class DeckLookCodecTest {

    private fun payload(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun aMatComesBackOutTheWayItWentIn() {
        MatChoice.entries.forEach { mat ->
            assertEquals(mat, DeckLookCodec.read(DeckLookCodec.write(null, mat)), mat.name)
        }
    }

    @Test
    fun aPlainDeckGainsNoKeySayingItLooksOrdinary() {
        val out = DeckLookCodec.write(payload("""{"version":"1.0"}"""), MatChoice.THEME)

        assertTrue("look" !in out)
        assertTrue("version" in out)
    }

    @Test
    fun undressingADeckTakesTheKeyBackOutAgain() {
        val dressed = DeckLookCodec.write(payload("""{"version":"1.0"}"""), MatChoice.WINE)
        assertTrue("look" in dressed)

        val plain = DeckLookCodec.write(dressed, MatChoice.THEME)
        assertTrue("look" !in plain)
        assertTrue("version" in plain)
    }

    @Test
    fun anythingElseUnderLookSurvivesBothWays() {
        // The payload rule, one level down -- the same argument as the notes.
        val before = payload("""{"look":{"backgroundGradient":"a","mat":"wine"},"version":"1.0"}""")

        val after = DeckLookCodec.write(before, MatChoice.BAIZE)
        val look = after["look"] as JsonObject

        assertTrue("backgroundGradient" in look)
        assertEquals(MatChoice.BAIZE, DeckLookCodec.read(after))
        assertTrue("version" in after)
    }

    @Test
    fun undressingADeckKeepsWhatSomebodyElsePutThere() {
        val before = payload("""{"look":{"backgroundGradient":"a","mat":"wine"}}""")

        val after = DeckLookCodec.write(before, MatChoice.THEME)

        assertTrue("backgroundGradient" in (after["look"] as JsonObject))
        assertEquals(MatChoice.THEME, DeckLookCodec.read(after))
    }

    @Test
    fun nothingInThePayloadIsTheDefault() {
        assertEquals(MatChoice.THEME, DeckLookCodec.read(null))
        assertEquals(MatChoice.THEME, DeckLookCodec.read(payload("""{"version":"1.0"}""")))
        assertEquals(MatChoice.THEME, DeckLookCodec.read(payload("""{"look":{}}""")))
    }
}
