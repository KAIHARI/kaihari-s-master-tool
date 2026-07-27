plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Compose and Android plugins are resolved lazily by the modules that need them,
// so this root script stays usable when Google's Maven is unreachable.
