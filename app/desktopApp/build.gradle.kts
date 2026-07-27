import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            implementation(libs.sqldelight.driver.jvm)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kaiharimoto.mastertool.desktop.MainKt"

        nativeDistributions {
            // .dmg for macOS, .msi for Windows, .deb so a Linux box can run it too.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Installer tooling rejects apostrophes and spaces, so the packaged
            // identifier differs from the name shown inside the app.
            packageName = "KaiMasterTool"
            packageVersion = "1.0.0"
            description = "Yu-Gi-Oh! deck building and tournament preparation"
            vendor = "kaiharimoto"
        }
    }
}
