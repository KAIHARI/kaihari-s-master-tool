package com.kaiharimoto.mastertool.core.search

/**
 * Searching the printed text under a card's name.
 *
 * ## Why this exists
 *
 * Some archetypes are not a word in anybody's name. "Light and Darkness Ritual"
 * is a deck built out of cards that *mention* a card, and the only way to find
 * them in a tool that searches names is to already know them — which is the
 * opposite of what a search field is for.
 *
 * ## Why it does not build an index
 *
 * The obvious implementation is an inverted index: every word of every card's
 * text, mapped to the cards that use it. That is about five megabytes on a pool
 * of thirteen thousand cards, and it buys a search that was already fast enough.
 *
 * The scan here is allocation-free instead. [containsRun] walks the *raw* text
 * and normalises as it goes — lowercasing and treating every run of punctuation
 * as one word break — so nothing is copied, nothing is lowercased into a new
 * string, and no per-card state has to be kept alive between queries. A full
 * pass over the whole pool is a few million character comparisons, which is
 * nothing next to the debounce it already sits behind.
 *
 * The other half of the same trade: normalising on the fly is exactly as
 * correct as normalising up front. `"Light and Darkness Ritual"` in a card's
 * text arrives with capitals and quotation marks around it, and the query is
 * four bare lowercase words; both sides reduce to the same run of letters, and
 * this compares them without ever materialising either reduction.
 *
 * ## The tiers, and why they are below every name tier
 *
 * [TextMatching.score] hands back 460 at its lowest — a fuzzy multi-word name
 * hit. Everything here is under that, so a card *called* what you typed always
 * outranks a card that merely mentions it, and switching the text search on can
 * only ever add rows to the bottom of the list.
 */
object EffectMatching {

    /** The whole query, verbatim and contiguous, in the card's text. */
    const val PHRASE = 400

    /** Every term present, scattered anywhere in the card's text. */
    const val TERMS = 300

    /**
     * Below this the query is not a search, it is a letter.
     *
     * Two characters match most of the pool, and a pool-sized result list from
     * two keystrokes is indistinguishable from no filtering at all.
     */
    const val MIN_LENGTH = 3

    /**
     * Scores [text] against [query], or null when it does not match.
     *
     * The whole query as one contiguous run is the strong reading of "mentions
     * this", and it is tried first — both because it ranks higher and because
     * for the queries this feature exists for it is the one that hits.
     */
    fun score(text: String, query: SearchQuery): Int? {
        if (text.isEmpty()) return null
        if (query.normalized.length < MIN_LENGTH) return null

        if (containsRun(text, query.normalized, prefix = query.partial != null)) return PHRASE

        // A single term has already been tried, as the whole query, one line up.
        if (query.termCount < 2) return null

        for (phrase in query.phrases) {
            if (!containsRun(text, phrase, prefix = false)) return null
        }
        // Longest first, from the parser: the most selective term is the one
        // that should get to reject the card, before the others cost a pass.
        for (word in query.words) {
            if (!containsRun(text, word, prefix = false)) return null
        }
        query.partial?.let { if (!containsRun(text, it, prefix = true)) return null }

        return TERMS
    }

    /**
     * Does [text] contain [needle] as a run of whole words?
     *
     * [needle] is already normalised: lowercase words separated by single
     * spaces. [text] is raw card text and is normalised as it is read, which is
     * what keeps this allocation-free.
     *
     * A space in the needle means "a word break here", and matches any run of
     * one or more characters that are not letters or digits — so `light and`
     * matches `Light, and`, `LIGHT — AND` and `"Light and`. The run has to start
     * on a word boundary always, and end on one unless [prefix] is set, which is
     * how the word somebody is still typing matches the word they mean.
     */
    fun containsRun(text: String, needle: String, prefix: Boolean): Boolean {
        if (needle.isEmpty()) return false

        var start = 0
        while (start < text.length) {
            // Candidate starts are word starts only. Scanning from every index
            // would find `and` inside `Darkness and`, which is not a word.
            if (!text[start].isLetterOrDigit() ||
                (start > 0 && text[start - 1].isLetterOrDigit())
            ) {
                start++
                continue
            }
            if (matchesAt(text, start, needle, prefix)) return true
            // Skip to the end of this word: the next candidate start is the
            // start of the next word, and every index in between fails the
            // boundary test above anyway.
            while (start < text.length && text[start].isLetterOrDigit()) start++
        }
        return false
    }

    private fun matchesAt(text: String, from: Int, needle: String, prefix: Boolean): Boolean {
        var t = from
        var n = 0
        while (n < needle.length) {
            val wanted = needle[n]
            if (wanted == ' ') {
                // The needle wants a break, so the text must have at least one
                // non-alphanumeric character and then more word.
                if (t >= text.length || text[t].isLetterOrDigit()) return false
                while (t < text.length && !text[t].isLetterOrDigit()) t++
                if (t >= text.length) return false
                n++
            } else {
                if (t >= text.length) return false
                val ch = text[t]
                // Not a letter or digit means the text's word ended before the
                // needle's did, which is a different word.
                if (!ch.isLetterOrDigit()) return false
                if (ch.lowercaseChar() != wanted) return false
                t++
                n++
            }
        }
        // The needle ran out inside a longer word: `ritual` in `rituals` is a
        // match only while it is still being typed.
        return prefix || t >= text.length || !text[t].isLetterOrDigit()
    }
}
