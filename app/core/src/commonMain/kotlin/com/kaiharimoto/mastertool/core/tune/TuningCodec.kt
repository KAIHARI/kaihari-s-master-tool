package com.kaiharimoto.mastertool.core.tune

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the panel hands back: the document, what was moved, and enough about the
 * screen it was tuned on to tell whether the numbers mean anything.
 *
 * [changed] is the whole point of the format. A tuning session moves three
 * numbers out of fifteen, and a reader — a person, or an agent asked to bake the
 * result in — should not have to diff two blobs to find out which three. It is
 * derived from [StageKnobs] at export time rather than tracked as the sliders
 * move, so it cannot disagree with the document beside it.
 */
@Serializable
data class TuningExport(
    val version: Int = FORMAT_VERSION,
    val stage: String = "play",
    val note: String = NOTE,
    val surface: TuningSurface = TuningSurface(),
    /** Every knob that differs from what ships, as `camera.lens: 1.34`. */
    val changed: List<String> = emptyList(),
    val tuning: StageTuning = StageTuning.DEFAULT,
    val reference: StageReference = StageReference(),
) {
    companion object {
        /**
         * 1: camera pose and focal length, the defocus falloff, the hand, and the
         * four card lifts.
         *
         * Bumped when a field is *removed or re-meaned*, never when one is added
         * — the reader ignores unknown keys, so a new knob is readable by an old
         * build and an old export is readable by a new one.
         */
        const val FORMAT_VERSION = 1

        private const val NOTE =
            "kai master tool — play stage tuning. Values are in the units of the constants " +
                "they replace: degrees, multiples of the shipped value (x), or fractions of a " +
                "card (cards). `changed` lists only what was moved. `reference` is read-only: " +
                "those numbers re-solve the board or are pinned by a test, and moving them is a " +
                "code change rather than a slider."
    }
}

/** The screen the tuning was done on, so a number can be sanity-checked later. */
@Serializable
data class TuningSurface(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val density: Float = 1f,
    val scene: String = "",
    val light: String = "",
    val seat: String = "",
)

/**
 * Turning the document into text and back.
 *
 * Its own [Json] instance, and that is not fastidiousness: the repository's
 * instance writes the whole preferences row on every save, and turning
 * `prettyPrint` on there would rewrite every stored document on the next write
 * for the sake of a file nobody reads. `encodeDefaults` is on here and off there
 * for the same split — an export that omitted the fields nobody moved would be
 * useless as a reference, and a stored document that spelled out every default
 * would be bytes for nothing.
 */
object TuningCodec {

    private val pretty = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val lenient = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** The document as text, ready to paste. */
    fun export(tuning: StageTuning, surface: TuningSurface = TuningSurface()): String {
        val safe = tuning.sanitised()
        return pretty.encodeToString(
            TuningExport(
                surface = surface,
                changed = changedIn(safe),
                tuning = safe,
            ),
        )
    }

    /**
     * Reads one back — either a whole export or a bare [StageTuning], because a
     * person pasting a file into a chat window trims it and a tool should not
     * care which of the two it got.
     *
     * Null when the text is not either, rather than an exception: this is fed by
     * a clipboard.
     */
    fun parse(text: String): StageTuning? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        runCatching { lenient.decodeFromString<TuningExport>(trimmed) }
            .getOrNull()
            ?.let { if (it.tuning != StageTuning.DEFAULT || trimmed.contains("\"tuning\"")) return it.tuning.sanitised() }
        return runCatching { lenient.decodeFromString<StageTuning>(trimmed) }
            .getOrNull()
            ?.sanitised()
    }

    /** Which knobs differ from what ships, as readable lines. */
    fun changedIn(tuning: StageTuning): List<String> = StageKnobs.ALL
        .filter { it.get(tuning) != it.get(StageTuning.DEFAULT) }
        .map { "${it.path}: ${trim(it.get(tuning))} (was ${trim(it.get(StageTuning.DEFAULT))})" }

    /**
     * A float as short a string as says the same thing.
     *
     * Common Kotlin has no `String.format`, and `Float.toString` gives
     * `0.6200000047683716` for a number a slider set to 0.62 — which in an
     * export is noise that reads as precision.
     */
    fun trim(value: Float, places: Int = 3): String {
        if (!value.isFinite()) return "0"
        var scale = 1.0
        repeat(places) { scale *= 10 }
        val rounded = kotlin.math.round(value.toDouble() * scale) / scale
        val whole = rounded.toLong()
        if (rounded == whole.toDouble()) return whole.toString()
        return rounded.toString().trimEnd('0').trimEnd('.')
    }
}
