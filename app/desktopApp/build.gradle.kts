import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.jdk.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":ui"))
            implementation(project(":core"))

            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
            // Compose Desktop dispatches onto the AWT event thread.
            implementation(libs.kotlinx.coroutines.swing)
            // Each application picks its own SQL driver and HTTP engine.
            implementation(libs.sqldelight.driver.jvm)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

val appVersionName: String = providers.gradleProperty("mastertool.versionName").get()

compose.desktop {
    application {
        mainClass = "com.kaiharimoto.mastertool.desktop.MainKt"

        // Keeps the version in one place: the app reads it back at runtime
        // instead of carrying a second hardcoded copy.
        jvmArgs += "-Dmastertool.version=$appVersionName"

        nativeDistributions {
            // .dmg for macOS, .msi for Windows, .deb so a Linux box can run it too.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Installer tooling rejects apostrophes and spaces, so the packaged
            // identifier differs from the name shown inside the app.
            packageName = "KaiMasterTool"
            packageVersion = appVersionName
            description = "Yu-Gi-Oh! deck building and tournament preparation"
            vendor = "kaiharimoto"
        }
    }
}
