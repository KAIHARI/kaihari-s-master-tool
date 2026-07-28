package com.kaiharimoto.mastertool.core.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ThemeChoiceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun theStoredNameIsNotTheEnumName() {
        // Written to disk, so the wire name is pinned separately from the Kotlin
        // one: renaming the constant must not silently reset everybody's theme.
        val text = json.encodeToString(
            UiPreferences.serializer(),
            UiPreferences.DEFAULT.copy(theme = ThemeChoice.CLASSIC),
        )

        assertTrue(text.contains("\"classic\""), "was $text")
    }

    @Test
    fun everyThemeSurvivesTheDocument() {
        ThemeChoice.entries.forEach { choice ->
            val prefs = UiPreferences.DEFAULT.copy(theme = choice)
            val text = json.encodeToString(UiPreferences.serializer(), prefs)

            assertEquals(choice, json.decodeFromString(UiPreferences.serializer(), text).theme)
        }
    }

    @Test
    fun aDocumentFromBeforeThemesExistedOpensOnTheDefault() {
        val old = """{"searchWeight":0.5}"""

        assertEquals(
            ThemeChoice.SWISS,
            json.decodeFromString(UiPreferences.serializer(), old).theme,
        )
    }

    @Test
    fun exactlyOneThemeIsLight() {
        // The mat's lighting is inverted for a bright surface rather than its
        // palette negated, so which ones are dark is a real distinction.
        assertEquals(listOf(ThemeChoice.DAYLIGHT), ThemeChoice.entries.filterNot { it.isDark })
    }

    @Test
    fun everyThemeSaysWhatItLooksLike() {
        // Both strings reach the menu: the name is the label and the description
        // is what tells you what you are about to get.
        ThemeChoice.entries.forEach {
            assertTrue(it.displayName.isNotBlank(), "$it has no name")
            assertTrue(it.description.isNotBlank(), "$it has no description")
        }
    }
}
