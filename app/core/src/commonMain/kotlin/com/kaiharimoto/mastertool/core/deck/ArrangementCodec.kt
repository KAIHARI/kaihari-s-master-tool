package com.kaiharimoto.mastertool.core.deck

import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private const val ARRANGEMENT = "arrangement"

// A sibling key rather than a richer shape under the first one: a file written
// before names existed still reads, and a reader that has never heard of names
// still finds the gaps exactly where it always did.
private const val PILE_NAMES = "arrangementNames"

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
        val named = extended[PILE_NAMES] as? JsonObject

        return DeckSection.entries.mapNotNull { section ->
            val cuts = (arrangement[section.key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.filter { it > 0 }
                ?.toSet()
                .orEmpty()
            // A section with no gaps is absent rather than present and empty,
            // so a deck nobody has arranged carries nothing at all.
            if (cuts.isEmpty()) return@mapNotNull null

            // JSON has no integer keys, so the positions are written as digits
            // and read back the same way. Anything that is not a number, or
            // names a pile that no longer starts anywhere, is dropped.
            val labels = (named?.get(section.key) as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    val start = key.toIntOrNull() ?: return@mapNotNull null
                    val text = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (start == 0 || start in cuts) start to text else null
                }
                ?.toMap()
                .orEmpty()

            section to Breaks(cuts, labels)
        }.toMap()
    }

    fun write(extended: JsonObject?, breaks: Map<DeckSection, Breaks>): JsonObject {
        val kept = breaks.filterValues { !it.isEmpty }

        val rest = extended.orEmpty().filterKeys { it != ARRANGEMENT && it != PILE_NAMES }
        if (kept.isEmpty()) return JsonObject(rest)

        val written = kept.entries.associate { (section, gaps) ->
            // Sorted, because a set has no order and a file that reorders its
            // own numbers between saves is a file that looks like it changed.
            section.key to JsonArray(gaps.before.sorted().map { JsonPrimitive(it) })
        }

        val labels = kept.entries
            .filter { (_, gaps) -> gaps.names.isNotEmpty() }
            .associate { (section, gaps) ->
                section.key to JsonObject(
                    gaps.names.entries
                        .sortedBy { it.key }
                        .associate { (start, name) -> start.toString() to JsonPrimitive(name) },
                )
            }

        val out = rest + (ARRANGEMENT to JsonObject(written))
        return JsonObject(if (labels.isEmpty()) out else out + (PILE_NAMES to JsonObject(labels)))
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
