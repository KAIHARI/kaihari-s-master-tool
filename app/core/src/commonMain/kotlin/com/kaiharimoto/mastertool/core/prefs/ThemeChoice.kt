package com.kaiharimoto.mastertool.core.prefs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which surface the deck is laid out on.
 *
 * The choice lives here rather than beside the colours because it is a
 * preference — it is read off disk, written back, and has to survive a build
 * that renames a hex value. `:core` owns what was chosen; `:ui` owns what that
 * looks like.
 *
 * The four are the ones the tool this replaces offered, rebuilt rather than
 * copied: it defined each as six hex values and no surface, which is why its
 * Classic theme never became the table it was reaching for.
 */
@Serializable
enum class ThemeChoice(val displayName: String, val description: String) {

    /** Deep slate and one indigo accent. What the app has always looked like. */
    @SerialName("swiss")
    SWISS("Swiss Prismatic", "Slate and indigo"),

    /** Brown leather, gold binding. The one that reads as a table. */
    @SerialName("classic")
    CLASSIC("Classic", "Leather and gold"),

    /** Near-black under cyan and magenta. The original's only theme with nerve. */
    @SerialName("cyber")
    CYBER("Cyber", "Cyan on black"),

    /** A lit desk, for a bright room. */
    @SerialName("daylight")
    DAYLIGHT("Daylight", "Paper and ink");

    val isDark: Boolean get() = this != DAYLIGHT
}
