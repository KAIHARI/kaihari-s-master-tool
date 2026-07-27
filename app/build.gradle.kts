// Intentionally declares no plugins.
//
// Two things make this the right shape rather than the usual `apply false` list:
//
//  - `apply false` still resolves the artifact, and the Android and Compose
//    plugins live only on Google's Maven. Naming them here would make this
//    script — and therefore :core — unbuildable wherever that is unreachable.
//  - Declaring `org.jetbrains.kotlin.android` here puts the Kotlin Gradle
//    Plugin's Android diagnostics on the root project, where they fail with
//    NoClassDefFoundError com/android/build/gradle/BaseExtension because AGP
//    itself is not applied.
//
// Plugin versions are pinned once in settings.gradle.kts pluginManagement, and
// every module requests them with a bare `id("...")`. Gradle warns that the
// Kotlin plugin is loaded per-subproject; that warning is the accepted cost.
