package com.kaiharimoto.mastertool.core.search

import com.kaiharimoto.mastertool.core.TestCards
import com.kaiharimoto.mastertool.core.model.Attribute
import com.kaiharimoto.mastertool.core.model.BanStatus
import com.kaiharimoto.mastertool.core.model.CardCategory
import com.kaiharimoto.mastertool.core.model.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardIndexTest {

    private val index = CardIndex.build(TestCards.all)

    @Test
    fun resolvesAlternateArtworkPasscodes() {
        // A deck file exported elsewhere may reference an alternate printing.
        assertEquals(TestCards.accesscode, index.byId(CardId(11111111)))
        assertEquals(TestCards.accesscode, index.byId(CardId(86066372)))
    }

    @Test
    fun unknownPasscodeReturnsNull() {
        assertNull(index.byId(CardId(1)))
    }

    @Test
    fun findsCardIgnoringPunctuation() {
        val hits = index.search("ash blossom")
        assertEquals(TestCards.ashBlossom, hits.first())
    }

    @Test
    fun findsCardBeforeTheAmpersand() {
        assertEquals(TestCards.ashBlossom, index.search("ash").first())
    }

    @Test
    fun matchesWordAfterAComma() {
        assertEquals(TestCards.nibiru, index.search("primal").first())
    }

    @Test
    fun matchesTokensOutOfOrder() {
        assertEquals(TestCards.nibiru, index.search("primal nibiru").first())
    }

    @Test
    fun handlesQuotesInNames() {
        assertEquals(TestCards.maxxC, index.search("maxx c").first())
        assertEquals(TestCards.maxxC, index.search("maxx").first())
    }

    @Test
    fun toleratesTypos() {
        assertEquals(TestCards.nibiru, index.search("nibiro").first())
        assertEquals(TestCards.accesscode, index.search("accesscode taker").first())
    }

    @Test
    fun exactMatchOutranksPrefixMatch() {
        val cards = listOf(
            TestCards.monster(1, "Dark Magician"),
            TestCards.monster(2, "Dark Magician Girl"),
        )
        val hits = CardIndex.build(cards).search("dark magician")
        assertEquals("Dark Magician", hits.first().name)
    }

    @Test
    fun emptyQueryReturnsPoolUpToLimit() {
        assertEquals(TestCards.all.size, index.search("", limit = 100).size)
        assertEquals(3, index.search("", limit = 3).size)
    }

    @Test
    fun respectsResultLimit() {
        val many = (1..500).map { TestCards.monster(it, "Test Card $it") }
        assertEquals(10, CardIndex.build(many).search("test", limit = 10).size)
    }

    @Test
    fun nonsenseQueryReturnsNothing() {
        assertTrue(index.search("zzzzqqqqxxxx").isEmpty())
    }

    @Test
    fun filtersByCategory() {
        val hits = index.search("", CardFilter(categories = setOf(CardCategory.SPELL)), limit = 100)
        assertEquals(listOf(TestCards.pot), hits)
    }

    @Test
    fun filtersByAttributeAndLevel() {
        val filter = CardFilter(attributes = setOf(Attribute.FIRE), levels = setOf(3))
        assertEquals(listOf(TestCards.ashBlossom), index.search("", filter, limit = 100))
    }

    @Test
    fun filtersByExtraDeck() {
        val extra = index.search("", CardFilter(extraDeckOnly = true), limit = 100)
        assertTrue(extra.all { it.isExtraDeck })
        assertTrue(TestCards.accesscode in extra)
        assertTrue(TestCards.pendulumFusion in extra)
    }

    @Test
    fun filtersByBanStatus() {
        val limited = index.search("", CardFilter(banStatuses = setOf(BanStatus.LIMITED)), limit = 100)
        assertEquals(listOf(TestCards.ashBlossom), limited)
    }

    @Test
    fun atkRangeExcludesCardsWithoutTheStat() {
        // Link monsters have no DEF, so a DEF range must not silently treat it as 0.
        val hits = index.search("", CardFilter(defRange = 0..1000), limit = 100)
        assertTrue(TestCards.accesscode !in hits)
    }

    @Test
    fun filterAndQueryCombine() {
        val filter = CardFilter(categories = setOf(CardCategory.MONSTER))
        assertTrue(index.search("pot", filter, limit = 100).isEmpty())
    }

    @Test
    fun byNameIsPunctuationInsensitive() {
        assertNotNull(index.byName("ash blossom joyous spring"))
        assertEquals(TestCards.ashBlossom, index.byName("Ash Blossom & Joyous Spring"))
    }

    @Test
    fun facetListsArePopulatedForFilterChips() {
        assertTrue("Zombie" in index.races)
        assertTrue(3 in index.levels)
    }
}
