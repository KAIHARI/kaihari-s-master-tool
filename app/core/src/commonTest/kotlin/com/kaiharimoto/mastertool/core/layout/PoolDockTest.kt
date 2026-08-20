package com.kaiharimoto.mastertool.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostureTest {

    @Test
    fun `a window taller than it is wide is tall`() {
        assertEquals(Posture.TALL, Posture.of(360f, 780f))
        assertEquals(Posture.TALL, Posture.of(924f, 1480f))
    }

    @Test
    fun `every landscape window keeps the builder it had`() {
        assertEquals(Posture.WIDE, Posture.of(1480f, 924f))
        assertEquals(Posture.WIDE, Posture.of(780f, 360f))
        assertEquals(Posture.WIDE, Posture.of(640f, 360f))
        // Square counts as wide: the side-by-side split still has width to divide.
        assertEquals(Posture.WIDE, Posture.of(800f, 800f))
    }
}

class PoolDockTest {

    /** A 780dp-tall phone under a 44dp top bar, with the dock's own furniture. */
    private val phone = DockMetrics(windowHeight = 736f, chrome = 120f, minDeck = 180f)

    @Test
    fun `peek is exactly the dock's own furniture`() {
        assertEquals(120f, PoolDock.height(PoolStop.PEEK, phone))
    }

    @Test
    fun `full is the whole window`() {
        assertEquals(736f, PoolDock.height(PoolStop.FULL, phone))
    }

    @Test
    fun `half leaves the deck something to be`() {
        val half = PoolDock.height(PoolStop.HALF, phone)
        assertEquals(368f, half)
        assertTrue(PoolDock.deckHeight(PoolStop.HALF, phone) >= phone.minDeck)
    }

    @Test
    fun `a short window gives the deck its minimum before it gives the pool half`() {
        val short = DockMetrics(windowHeight = 300f, chrome = 120f, minDeck = 180f)
        assertEquals(120f, PoolDock.height(PoolStop.HALF, short))
        assertEquals(180f, PoolDock.deckHeight(PoolStop.HALF, short))
    }

    @Test
    fun `a window with no room for the deck still keeps the search field whole`() {
        val tiny = DockMetrics(windowHeight = 140f, chrome = 120f, minDeck = 180f)
        assertEquals(120f, PoolDock.height(PoolStop.HALF, tiny))
        assertEquals(120f, PoolDock.height(PoolStop.PEEK, tiny))
    }

    @Test
    fun `a released drag settles on the nearest stop`() {
        assertEquals(PoolStop.PEEK, PoolDock.nearest(130f, phone))
        assertEquals(PoolStop.HALF, PoolDock.nearest(300f, phone))
        assertEquals(PoolStop.HALF, PoolDock.nearest(420f, phone))
        assertEquals(PoolStop.FULL, PoolDock.nearest(700f, phone))
    }

    @Test
    fun `typing takes the screen and letting go never closes over the answer`() {
        assertEquals(PoolStop.FULL, PoolDock.whileTyping())
        // The one design claim: a search typed from the closed dock leaves its
        // results visible rather than shutting on them.
        assertEquals(PoolStop.HALF, PoolDock.afterTyping(PoolStop.PEEK))
        assertEquals(PoolStop.HALF, PoolDock.afterTyping(PoolStop.HALF))
        assertEquals(PoolStop.FULL, PoolDock.afterTyping(PoolStop.FULL))
    }
}
