package com.kaiharimoto.mastertool.core.prefs

import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class UiPreferencesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun sectionsAreAddressableBySection() {
        val prefs = UiPreferences.DEFAULT
            .with(DeckSection.SIDE, SectionPreferences(weight = 1.5f, columns = 6))

        assertEquals(6, prefs[DeckSection.SIDE].columns)
        assertEquals(UiPreferences.DEFAULT.main, prefs[DeckSection.MAIN])
    }

    @Test
    fun roundTripsThroughJson() {
        val prefs = UiPreferences.DEFAULT.copy(
            searchWeight = 0.4f,
            stacked = true,
            themeMode = ThemeMode.LIGHT,
        ).with(DeckSection.MAIN, SectionPreferences(2.5f, 12, sortMode = SortMode.TYPE))

        val text = json.encodeToString(UiPreferences.serializer(), prefs)
        assertEquals(prefs, json.decodeFromString(UiPreferences.serializer(), text))
    }

    @Test
    fun themeModeDefaultsToSystemOnAnOlderDocument() {
        // Documents written before the theme preference existed must not force a
        // scheme; following the system is the only honest default.
        val old = """{"searchWeight":0.5}"""
        assertEquals(
            ThemeMode.SYSTEM,
            json.decodeFromString(UiPreferences.serializer(), old).themeMode,
        )
    }

    @Test
    fun anOlderDocumentMissingFieldsStillParses() {
        // The reason settings live in one JSON document: adding a preference is a
        // field, never a schema migration, and yesterday's file still opens.
        val old = """{"searchWeight":0.5}"""
        val parsed = json.decodeFromString(UiPreferences.serializer(), old)

        assertEquals(0.5f, parsed.searchWeight)
        assertEquals(UiPreferences.DEFAULT.main, parsed.main)
    }

    @Test
    fun aDocumentWithUnknownFieldsStillParses() {
        val newer = """{"searchWeight":0.5,"somethingFromALaterBuild":42}"""
        assertEquals(
            0.5f,
            json.decodeFromString(UiPreferences.serializer(), newer).searchWeight,
        )
    }

    // A zero or NaN weight reaches `Modifier.weight`, which rejects both. These
    // values come off disk, so they are not hypothetical.

    @Test
    fun sanitisingRescuesAZeroWeight() {
        val broken = UiPreferences.DEFAULT
            .with(DeckSection.MAIN, SectionPreferences(weight = 0f, columns = 10))
            .sanitised()

        assertTrue(broken.main.weight >= SectionPreferences.MIN_WEIGHT)
    }

    @Test
    fun sanitisingRescuesNaNAndInfinity() {
        val broken = UiPreferences.DEFAULT
            .copy(searchWeight = Float.NaN)
            .with(DeckSection.EXTRA, SectionPreferences(Float.POSITIVE_INFINITY, 10))
            .sanitised()

        assertEquals(UiPreferences.DEFAULT_SEARCH_WEIGHT, broken.searchWeight)
        assertTrue(broken.extra.weight.isFinite())
        assertTrue(broken.extra.weight <= SectionPreferences.MAX_WEIGHT)
    }

    @Test
    fun sanitisingClampsColumnCounts() {
        val broken = UiPreferences.DEFAULT
            .with(DeckSection.MAIN, SectionPreferences(2f, columns = 0))
            .with(DeckSection.SIDE, SectionPreferences(1f, columns = 5000))
            .sanitised()

        assertEquals(SectionPreferences.MIN_COLUMNS, broken.main.columns)
        assertEquals(SectionPreferences.MAX_COLUMNS, broken.side.columns)
    }

    @Test
    fun sanitisingKeepsSizeToFitSearchColumns() {
        // 0 is a real value here, meaning "size to fit", not an out-of-range one.
        assertEquals(0, UiPreferences.DEFAULT.copy(searchColumns = 0).sanitised().searchColumns)
        assertEquals(0, UiPreferences.DEFAULT.copy(searchColumns = -4).sanitised().searchColumns)
    }

    @Test
    fun sanitisingLeavesAReasonableDocumentAlone() {
        assertEquals(UiPreferences.DEFAULT, UiPreferences.DEFAULT.sanitised())
    }

    // ---- layout generations -------------------------------------------------

    @Test
    fun anOlderDocumentIsBroughtUpToTodaysRowWidths() {
        // The one thing a defaulted field cannot do: a device that already has
        // a document on it keeps the row widths it was written with.
        val old = json.decodeFromString(
            UiPreferences.serializer(),
            """{"searchWeight":0.5,"extra":{"weight":1.0,"columns":10}}""",
        )
        assertEquals(10, old.extra.columns)

        val current = old.migrated()
        assertEquals(UiPreferences.DEFAULT.extra.columns, current.extra.columns)
        assertEquals(UiPreferences.DEFAULT.main.columns, current.main.columns)
        assertTrue(current.fitAll)
        // And what is the person's rather than the generation's is untouched.
        assertEquals(0.5f, current.searchWeight)
    }

    @Test
    fun migratingIsIdempotentAndLeavesTodaysDocumentAlone() {
        val once = UiPreferences.DEFAULT.copy(layoutVersion = 0).migrated()

        assertEquals(once, once.migrated())
        val mine = UiPreferences.DEFAULT.copy(fitAll = false, themeMode = ThemeMode.LIGHT)
        assertEquals(mine, mine.migrated())
    }
}
