package com.kaiharimoto.mastertool.core.siding

import com.kaiharimoto.mastertool.core.model.CardId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PATTERNS = "sidingPatterns"
private const val DECK_NAME = "deckName"
private const val ASSOCIATED_YDK = "associatedYdk"
private const val THUMBNAILS = "thumbnails"
private const val GOING_FIRST = "goingFirst"
private const val GOING_SECOND = "goingSecond"
private const val IN = "in"
private const val OUT = "out"

/**
 * Reads and writes siding patterns inside the `#ydkx-extended` payload.
 *
 * The payload is a document this application does not own. Alongside the two
 * swap lists, the desktop tool writes a background gradient, a category, tags, a
 * last-used timestamp and the opponent's entire decklist — none of which is
 * modelled here and all of which would be destroyed by decoding the object into
 * a data class and encoding it back.
 *
 * So writing **merges**: each pattern is the object that was already on disk with
 * the handful of understood keys replaced. A file edited on the tablet and
 * reopened on the desktop keeps everything the desktop put there. This is the
 * same promise `YdkCodec` already makes about the payload as a whole, one level
 * further in.
 *
 * Reading is permissive throughout, for the same reason `YdkCodec` is: a pattern
 * with a malformed thumbnail is worth having without its thumbnail, and a file
 * that is a little wrong should open.
 */
object SidingCodec {

    /** Patterns in [extended], keyed as the file keys them: by matchup name. */
    fun read(extended: JsonObject?): Map<String, SidingPattern> {
        val patterns = extended?.get(PATTERNS) as? JsonObject ?: return emptyMap()

        return patterns.mapNotNull { (name, element) ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            name to SidingPattern(
                deckName = obj.string(DECK_NAME) ?: name,
                associatedYdk = obj.string(ASSOCIATED_YDK),
                thumbnails = obj.cardIds(THUMBNAILS),
                goingFirst = obj.swap(GOING_FIRST),
                goingSecond = obj.swap(GOING_SECOND),
            )
        }.toMap()
    }

    /**
     * [extended] with [patterns] written into it, keeping every key neither this
     * codec nor the caller knows about.
     *
     * A pattern absent from [patterns] is removed, which is what makes deleting
     * one possible; a pattern present but new is created from nothing.
     */
    fun write(extended: JsonObject?, patterns: Map<String, SidingPattern>): JsonObject {
        val existing = extended?.get(PATTERNS) as? JsonObject

        val written = patterns.mapValues { (name, pattern) ->
            val before = existing?.get(name) as? JsonObject ?: JsonObject(emptyMap())
            JsonObject(
                before + buildMap {
                    put(DECK_NAME, JsonPrimitive(pattern.deckName.ifEmpty { name }))
                    pattern.associatedYdk?.let { put(ASSOCIATED_YDK, JsonPrimitive(it)) }
                    put(THUMBNAILS, pattern.thumbnails.toJson())
                    put(GOING_FIRST, pattern.goingFirst.toJson())
                    put(GOING_SECOND, pattern.goingSecond.toJson())
                },
            )
        }

        val base = extended ?: JsonObject(emptyMap())
        return if (written.isEmpty() && existing == null) {
            base
        } else {
            JsonObject(base + (PATTERNS to JsonObject(written)))
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /**
     * Passcodes, skipping anything that is not one.
     *
     * Written as strings by the tool that produces these files, but a bare number
     * reads the same way here — that difference is not worth losing a pattern
     * over. Anything else in the array, including a JSON null, is skipped rather
     * than thrown on.
     */
    private fun JsonObject.cardIds(key: String): List<CardId> =
        (this[key] as? JsonArray)
            ?.mapNotNull { element ->
                val primitive = element as? JsonPrimitive ?: return@mapNotNull null
                if (primitive is JsonNull) return@mapNotNull null
                CardId.parseOrNull(primitive.content)
            }
            .orEmpty()

    private fun JsonObject.swap(key: String): SidingSwap {
        val obj = this[key] as? JsonObject ?: return SidingSwap()
        return SidingSwap(cardsIn = obj.cardIds(IN), cardsOut = obj.cardIds(OUT))
    }

    private fun List<CardId>.toJson(): JsonArray =
        JsonArray(map { JsonPrimitive(it.value.toString()) })

    private fun SidingSwap.toJson(): JsonObject = JsonObject(
        mapOf(IN to cardsIn.toJson(), OUT to cardsOut.toJson()),
    )
}
