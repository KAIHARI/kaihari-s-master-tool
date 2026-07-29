package com.kaiharimoto.mastertool.core.deck

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The cloth a deck is laid out on.
 *
 * The theme belongs to the application and the mat belongs to the deck. That
 * split is the whole idea: somebody who runs three lists should be able to know
 * which one is open from across the room, and a deck that always looks the same
 * as every other deck is a decklist with a picture of cards next to it.
 *
 * A named choice rather than a colour, deliberately. A free colour picker in a
 * program about arrangement is a way to produce a mat that fights the cards on
 * it — every one of these is picked to sit *under* card art, which is the only
 * thing the surface has to do.
 *
 * [THEME] is the default and means "whatever the application is wearing", so a
 * deck nobody has dressed is not carrying an opinion it never expressed.
 */
enum class MatChoice(val key: String) {
    THEME("theme"),

    /** Deep blue-grey, the colour the app has always been. */
    SLATE("slate"),

    /** Brown hide, for a deck that wants to feel older than it is. */
    LEATHER("leather"),

    /** Near-black with a blue cast — the quietest of them under bright art. */
    MIDNIGHT("midnight"),

    /** Dark green baize, which is what a card table is actually covered in. */
    BAIZE("baize"),

    /** Oxblood, the one that reads as expensive. */
    WINE("wine"),

    /** Warm off-white, for anybody who has to build a deck in daylight. */
    BONE("bone"),
    ;

    companion object {
        val DEFAULT = THEME

        fun byKey(key: String?): MatChoice =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: DEFAULT
    }
}

/**
 * Reads and writes the mat inside `#ydkx-extended`.
 *
 * Merged, like everything that touches this payload. Written under `look` rather
 * than at the top level so there is somewhere obvious for the next thing about
 * how a deck is *shown* to go, and absent entirely when the deck has not been
 * dressed — a plain deck should not gain a key saying it looks ordinary.
 */
object DeckLookCodec {

    fun read(extended: JsonObject?): MatChoice {
        val look = extended?.get(LOOK) as? JsonObject ?: return MatChoice.DEFAULT
        return MatChoice.byKey((look[MAT] as? JsonPrimitive)?.contentOrNull)
    }

    fun write(extended: JsonObject?, mat: MatChoice): JsonObject {
        val root: Map<String, JsonElement> = extended ?: emptyMap()
        val existing = root[LOOK] as? JsonObject
        val rest = (existing ?: emptyMap<String, JsonElement>()).filterKeys { it != MAT }

        if (mat == MatChoice.DEFAULT) {
            // Nothing of ours left in there, and nothing of anybody else's
            // either: take the whole object out rather than leave an empty one.
            return if (rest.isEmpty()) {
                JsonObject(root.filterKeys { it != LOOK })
            } else {
                JsonObject(root + (LOOK to JsonObject(rest)))
            }
        }

        return JsonObject(root + (LOOK to JsonObject(rest + (MAT to JsonPrimitive(mat.key)))))
    }

    private const val LOOK = "look"
    private const val MAT = "mat"
}
