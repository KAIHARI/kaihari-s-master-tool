package com.kaiharimoto.mastertool.core.search

/**
 * Text normalisation and fuzzy matching shared by search and autocomplete.
 *
 * Card names are full of punctuation that players do not type: "Ash Blossom &
 * Joyous Spring", "Nibiru, the Primal Being", "Ip Masquerena". Normalising both
 * sides to lowercase alphanumeric words means a query only has to match the
 * letters someone actually typed.
 */
object TextMatching {

    /** Lowercases and reduces everything that is not a letter or digit to a space. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input) {
            val c = ch.lowercaseChar()
            if (c.isLetterOrDigit()) {
                sb.append(c)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        }
        return sb.toString().trim()
    }

    fun tokenize(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotEmpty() }

    /**
     * Levenshtein distance, abandoned as soon as it is known to exceed [max].
     *
     * Returns [max] + 1 when the strings are further apart than that. Bounding
     * the work matters: this runs against the whole card pool per keystroke, and
     * an unbounded distance on 13,000 names is the difference between a
     * responsive search field and a janky one.
     */
    fun editDistanceAtMost(a: String, b: String, max: Int): Int {
        if (max < 0) return 1
        val lengthGap = if (a.length > b.length) a.length - b.length else b.length - a.length
        if (lengthGap > max) return max + 1
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]

            // Only the band within `max` of the diagonal can produce a result <= max.
            val from = maxOf(1, i - max)
            val to = minOf(b.length, i + max)
            if (from > 1) current[from - 1] = max + 1

            for (j in from..to) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1
                val best = minOf(substitution, deletion, insertion)
                current[j] = best
                if (best < rowMin) rowMin = best
            }
            if (to < b.length) current[to + 1] = max + 1

            if (rowMin > max) return max + 1

            val swap = previous
            previous = current
            current = swap
        }

        val result = previous[b.length]
        return if (result > max) max + 1 else result
    }

    /**
     * Scores [normalizedName] against an already-normalized [query].
     *
     * Higher is better; null means no match. The tiers are ordered the way a
     * player expects: what they typed exactly, then what starts with it, then
     * what contains it, and only then a typo-tolerant match.
     */
    fun score(normalizedName: String, query: String, queryTokens: List<String>): Int? {
        if (query.isEmpty()) return 0
        if (normalizedName == query) return 1_000

        if (normalizedName.startsWith(query)) return 900
        val words = normalizedName.split(' ')
        if (words.any { it.startsWith(query) }) return 800
        if (normalizedName.contains(query)) return 700

        // Multi-word queries: every token must prefix some word, in any order.
        // Lets "blossom ash" and "primal nibiru" both find the right card.
        if (queryTokens.size > 1 &&
            queryTokens.all { token -> words.any { it.startsWith(token) } }
        ) {
            return 650
        }

        // Typo tolerance, but only once the query is long enough that a fuzzy
        // hit is likely to be intentional rather than noise.
        if (query.length < MIN_FUZZY_LENGTH) return null
        val allowance = allowanceFor(query)

        // Whole-name comparison first: it is what catches a typo in a query that
        // already spans several words, like "accesscode taker".
        val wholeName = editDistanceAtMost(normalizedName, query, allowance)
        if (wholeName <= allowance) return 520 - wholeName * 40

        if (queryTokens.size <= 1) {
            // Compare against individual words rather than a truncated prefix of
            // the name. "nibiro" should be measured against "nibiru", not against
            // "nibiru " with a trailing space that costs an extra edit.
            var best = allowance + 1
            for (word in words) {
                if (word.length + allowance < query.length) continue
                val distance = editDistanceAtMost(word, query, allowance)
                if (distance < best) best = distance
                if (best == 0) break
            }
            if (best <= allowance) return 500 - best * 40
            return null
        }

        // Multi-word query: every token must fuzzily match some word.
        val everyTokenMatches = queryTokens.all { token ->
            val tokenAllowance = allowanceFor(token)
            words.any { word ->
                if (tokenAllowance == 0) {
                    word.startsWith(token)
                } else {
                    editDistanceAtMost(word, token, tokenAllowance) <= tokenAllowance
                }
            }
        }
        return if (everyTokenMatches) 460 else null
    }

    private const val MIN_FUZZY_LENGTH = 4

    /** Longer text earns more typo budget; short text is too ambiguous to guess at. */
    private fun allowanceFor(text: String): Int = when {
        text.length >= 8 -> 2
        text.length >= MIN_FUZZY_LENGTH -> 1
        else -> 0
    }
}
