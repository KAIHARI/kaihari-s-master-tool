package com.kaiharimoto.mastertool.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the platform is told a file is.
 *
 * Small enough to look obvious and worth pinning anyway: on Android this decides
 * which applications a share sheet offers, so a picture announced as text is
 * offered to a notes app and not to anything that posts images.
 */
class FileTypesTest {

    @Test
    fun aPictureOfADeckIsAnImage() {
        assertEquals("image/png", mimeTypeFor("Snake-Eye.png"))
    }

    @Test
    fun theSidingSheetIsADocument() {
        assertEquals("application/pdf", mimeTypeFor("Snake-Eye siding.pdf"))
    }

    @Test
    fun aDecklistIsTextBecauseItHasNoTypeOfItsOwn() {
        // .ydk has never been registered anywhere and never will be.
        assertEquals("text/plain", mimeTypeFor("deck.ydk"))
        assertEquals("text/plain", mimeTypeFor("deck.ydkx"))
    }

    @Test
    fun aDeckNamedAfterAFileTypeIsStillJudgedByItsEnding() {
        // Reachable: people call decks things like "png tribute" and the name
        // goes straight into the suggested filename.
        assertEquals("text/plain", mimeTypeFor("png tribute.ydk"))
        assertEquals("image/png", mimeTypeFor("pdf brigade.PNG"))
    }

    @Test
    fun somethingWithNoEndingAtAllIsNotGuessedAt() {
        assertEquals("text/plain", mimeTypeFor("deck"))
        assertEquals("text/plain", mimeTypeFor(""))
    }
}
