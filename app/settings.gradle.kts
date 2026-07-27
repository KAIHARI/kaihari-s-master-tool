rootProject.name = "kai-master-tool"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Android target toggle
//
// Every Android-flavoured artifact (AGP, androidx.*, the SDK itself) is served
// only from Google's Maven. In environments that cannot reach it, the Android
// modules are skipped so `:core` still compiles and its tests still run. CI and
// any machine with the SDK installed pick Android up automatically.
//
// Force either way with -Pmastertool.android=true|false.
// ---------------------------------------------------------------------------
val androidSdkDetected: Boolean =
    !System.getenv("ANDROID_HOME").isNullOrBlank() ||
        !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
        file("local.properties").takeIf { it.isFile }
            ?.readLines()
            ?.any { it.trimStart().startsWith("sdk.dir") } == true

val androidEnabled: Boolean =
    providers.gradleProperty("mastertool.android").orNull?.toBooleanStrictOrNull()
        ?: androidSdkDetected

// Compose Multiplatform depends on androidx artifacts on *every* target, desktop
// included, so the UI modules need Google's Maven even when Android is off.
val composeEnabled: Boolean =
    providers.gradleProperty("mastertool.compose").orNull?.toBooleanStrictOrNull()
        ?: androidEnabled

gradle.extra["mastertool.androidEnabled"] = androidEnabled
gradle.extra["mastertool.composeEnabled"] = composeEnabled

include(":core")

if (composeEnabled) {
    include(":ui")
    include(":desktopApp")
}

if (androidEnabled) {
    include(":androidApp")
}

if (!composeEnabled) {
    logger.lifecycle(
        "[kai's master tool] Compose/Android modules skipped (no Android SDK and no Google Maven access). " +
            "Only :core is configured. Override with -Pmastertool.compose=true."
    )
}
