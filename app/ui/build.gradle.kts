import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val androidEnabled = gradle.extra["mastertool.androidEnabled"] as Boolean

if (androidEnabled) {
    // See :core for why AGP is applied dynamically rather than via `plugins {}`.
    apply(plugin = "com.android.library")
    extensions.findByName("android")?.withGroovyBuilder {
        setProperty("namespace", "com.kaiharimoto.mastertool.ui")
        setProperty("compileSdk", libs.versions.compileSdk.get().toInt())
        "defaultConfig" {
            setProperty("minSdk", libs.versions.minSdk.get().toInt())
        }
        "compileOptions" {
            val java = JavaVersion.toVersion(libs.versions.jdk.get())
            setProperty("sourceCompatibility", java)
            setProperty("targetCompatibility", java)
        }
    }
}

kotlin {
    jvm("desktop")

    if (androidEnabled) {
        androidTarget()
    }

    jvmToolchain(libs.versions.jdk.get().toInt())

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
    }
}
