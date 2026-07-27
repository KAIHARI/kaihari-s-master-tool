// Plugin *versions* are pinned in settings.gradle.kts pluginManagement, so the
// ids here carry none. Declaring the Kotlin plugins once at the root puts a
// single copy of the Kotlin Gradle Plugin on the build classpath — without this
// Gradle warns that it was loaded separately in every subproject.
//
// The Android and Compose plugins are deliberately absent: `apply false` still
// resolves the artifact, and both are published only to Google's Maven, which
// would make this script — and therefore :core — unbuildable wherever that
// repository is unreachable.
plugins {
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
