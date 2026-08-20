package com.kaiharimoto.mastertool.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchQueryTest {

    @Test
    fun `a bare query is words with the last one open`() {
        val query = SearchQuery.parse("light and dark")
        assertEquals("light and dark", query.normalized)
        assertEquals(listOf("light", "and", "dark"), query.tokens)
        assertEquals("dark", query.partial)
        // Longest first, so the most selective term rejects a card first.
        assertEquals(listOf("light", "and"), query.words)
        assertTrue(query.phrases.isEmpty())
    }

    @Test
    fun `a trailing space finishes the word`() {
        val query = SearchQuery.parse("light and dark ")
        assertNull(query.partial)
        assertEquals(listOf("light", "dark", "and"), query.words)
    }

    @Test
    fun `quotes make a phrase, and the rest stays loose`() {
        val query = SearchQuery.parse("dragon \"light and darkness\" ")
        assertEquals(listOf("light and darkness"), query.phrases)
        assertEquals(listOf("dragon"), query.words)
        assertNull(query.partial)
        // The normalised whole is still every word in order, for name scoring.
        assertEquals("dragon light and darkness", query.normalized)
    }

    @Test
    fun `an unclosed quote closes at the end rather than blanking the query`() {
        val query = SearchQuery.parse("\"light and dark")
        assertEquals(listOf("light and dark"), query.phrases)
        assertNull(query.partial)
    }

    @Test
    fun `a scope prefix is stripped and reported`() {
        listOf("text:", "effect:", "e:", "t:").forEach { prefix ->
            val query = SearchQuery.parse("${prefix}ritual summon ")
            assertEquals(SearchScope.TEXT, query.scope, prefix)
            assertEquals("ritual summon", query.normalized, prefix)
        }
        assertEquals(SearchScope.NAMES, SearchQuery.parse("name:ash").scope)
        assertNull(SearchQuery.parse("ash").scope)
    }

    @Test
    fun `a prefix is not read out of the middle of a query`() {
        val query = SearchQuery.parse("summon text:thing")
        assertNull(query.scope)
        assertEquals("summon text thing", query.normalized)
    }

    @Test
    fun `an empty query is empty whatever the scope prefix says`() {
        assertTrue(SearchQuery.parse("text:").isEmpty)
        assertTrue(SearchQuery.parse("   ").isEmpty)
    }

    @Test
    fun `term count counts every separate thing the text must satisfy`() {
        assertEquals(3, SearchQuery.parse("dragon \"light and darkness\" rit").termCount)
    }
}
