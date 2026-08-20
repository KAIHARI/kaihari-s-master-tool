package com.kaiharimoto.mastertool.core.search

/**
 * Which half of a card the query is looking at.
 *
 * [NAMES] is what search has always done. [ALL] adds the printed text under the
 * name, ranked strictly below every name hit, so turning it on can only ever
 * add rows to the bottom of the list. [TEXT] drops the names entirely, which is
 * the reading you want when the card you are hunting is not the one you can
 * spell: *every card that mentions Light and Darkness Ritual* is a different
 * question from *the card called Light and Darkness Ritual*, and with [ALL] the
 * answer to the second is sitting on top of the answer to the first.
 */
enum class SearchScope { NAMES, TEXT, ALL }

/**
 * What the user typed, taken apart once.
 *
 * Search used to hand the raw string to two functions that each normalised it
 * again; now it is parsed once and the pieces are what get passed around. That
 * matters more than it sounds, because three of these pieces are things the raw
 * string cannot answer on its own:
 *
 * - [phrases] are the runs the user put in quotes, which must appear contiguous
 *   and in order. Unquoted words may be scattered anywhere in the text.
 * - [partial] is the word still being typed. A card database is searched a
 *   keystroke at a time, and requiring the last word to be *finished* means the
 *   list stays empty until it is — so the trailing word matches as a prefix and
 *   every other one matches whole.
 * - [scope] is an override written into the query itself (`text:`, `effect:`,
 *   `name:`). One line of typing reaches a mode that would otherwise need a
 *   control on a bar that has no room for one, on the screen where room is
 *   scarcest.
 */
data class SearchQuery(
    /** The whole query, normalised: lowercase words separated by single spaces. */
    val normalized: String,
    /** Every word of [normalized], in order. What name scoring ranks against. */
    val tokens: List<String>,
    /** Quoted runs, each normalised. Must appear contiguous, in order. */
    val phrases: List<String>,
    /** Finished unquoted words, longest first — see [SearchQuery.parse]. */
    val words: List<String>,
    /** The unfinished trailing word, or null when the query ends on a break. */
    val partial: String?,
    /** A scope named in the query itself, which outranks the caller's default. */
    val scope: SearchScope?,
) {
    val isEmpty: Boolean get() = tokens.isEmpty()

    /** How many separate things the text has to satisfy. */
    val termCount: Int get() = phrases.size + words.size + if (partial == null) 0 else 1

    companion object {

        /**
         * Prefixes that name a scope, longest first so `effect:` is not read as
         * `e:` with a stray word after it.
         */
        private val SCOPES = listOf(
            "effect:" to SearchScope.TEXT,
            "text:" to SearchScope.TEXT,
            "name:" to SearchScope.NAMES,
            "t:" to SearchScope.TEXT,
            "e:" to SearchScope.TEXT,
            "n:" to SearchScope.NAMES,
        )

        fun parse(raw: String): SearchQuery {
            var rest = raw.trimStart()
            var scope: SearchScope? = null
            for ((prefix, value) in SCOPES) {
                if (rest.startsWith(prefix, ignoreCase = true)) {
                    scope = value
                    rest = rest.substring(prefix.length)
                    break
                }
            }

            val phrases = mutableListOf<String>()
            val words = mutableListOf<String>()

            // Walk the string once, splitting it into quoted runs and bare
            // words. An unclosed quote is treated as closing at the end, so the
            // moment somebody types the opening one the query does not go blank.
            var i = 0
            var quoteOpen = false
            val piece = StringBuilder()
            fun flush(quoted: Boolean) {
                val normalized = TextMatching.normalize(piece.toString())
                piece.clear()
                if (normalized.isEmpty()) return
                if (quoted) phrases.add(normalized) else words.addAll(normalized.split(' '))
            }
            while (i < rest.length) {
                val ch = rest[i]
                if (ch == '"') {
                    flush(quoteOpen)
                    quoteOpen = !quoteOpen
                } else {
                    piece.append(ch)
                }
                i++
            }
            flush(quoteOpen)

            // The trailing word is still being typed only when nothing closed
            // it: no space, no quote, no punctuation. Backspacing to a space
            // therefore finishes the word before it rather than reopening it.
            val lastChar = rest.lastOrNull()
            val openEnded = !quoteOpen && lastChar != null && lastChar.isLetterOrDigit()
            val partial = if (openEnded && words.isNotEmpty()) {
                words.removeAt(words.lastIndex)
            } else {
                null
            }

            val normalized = TextMatching.normalize(rest)
            return SearchQuery(
                normalized = normalized,
                tokens = normalized.split(' ').filter { it.isNotEmpty() },
                phrases = phrases,
                // Longest first: every term has to match, so the most selective
                // one should be the one that rejects a card, and it rejects it
                // before the others have cost a pass over the text.
                words = words.sortedByDescending { it.length },
                partial = partial,
                scope = scope,
            )
        }
    }
}
