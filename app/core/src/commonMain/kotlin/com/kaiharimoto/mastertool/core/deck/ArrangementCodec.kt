package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val ARRANGEMENT = "arrangement"

/**
 * Reads and writes where the gaps are, inside the `#ydkx-extended` payload.
 *
 * A `.ydk` file is a list of passcodes and has nowhere to put this — which is
 * fine, and is why it goes in the payload the format already carries for things
 * outside the standard. Opened anywhere else, the deck is exactly the same deck;
 * the gaps are the kind of thing that is worth keeping and not worth insisting
 * on, and a file that loses them still opens.
 *
 * Merged rather than replaced, like everything that touches this payload: the
 * desktop tool writes keys this program has never heard of and they have to
 * survive a round trip through it.
 */
object ArrangementCodec {

    fun read(extended: JsonObject?): Map<DeckSection, Breaks> {
        val arrangement = extended?.get(ARRANGEMENT) as? JsonObject ?: return emptyMap()

        return DeckSection.entries.mapNotNull { section ->
            val cuts = (arrangement[section.key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.filter { it > 0 }
                ?.toSet()
                .orEmpty()
            // A section with no gaps is absent rather than present and empty,
            // so a deck nobody has arranged carries nothing at all.
            if (cuts.isEmpty()) null else section to Breaks(cuts)
        }.toMap()
    }

    fun write(extended: JsonObject?, breaks: Map<DeckSection, Breaks>): JsonObject {
        val kept = breaks.filterValues { !it.isEmpty }

        val rest = extended.orEmpty().filterKeys { it != ARRANGEMENT }
        if (kept.isEmpty()) return JsonObject(rest)

        val written = kept.entries.associate { (section, gaps) ->
            // Sorted, because a set has no order and a file that reorders its
            // own numbers between saves is a file that looks like it changed.
            section.key to JsonArray(gaps.before.sorted().map { JsonPrimitive(it) })
        }

        return JsonObject(rest + (ARRANGEMENT to JsonObject(written)))
    }

    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> =
        this ?: emptyMap()

    /** The key a section is written under: the same word the file already uses. */
    private val DeckSection.key: String
        get() = when (this) {
            DeckSection.MAIN -> "main"
            DeckSection.EXTRA -> "extra"
            DeckSection.SIDE -> "side"
        }
}
