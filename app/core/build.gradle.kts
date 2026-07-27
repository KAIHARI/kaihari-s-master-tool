import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

val androidEnabled = gradle.extra["mastertool.androidEnabled"] as Boolean

// iOS needs the Kotlin/Native toolchain (a large download) and is only useful on
// a Mac, so it stays opt-in until there's an Xcode host to build it on.
val iosEnabled =
    providers.gradleProperty("mastertool.ios").orNull?.toBooleanStrictOrNull()
        ?: System.getProperty("os.name").startsWith("Mac")

if (androidEnabled) {
    // Applied dynamically rather than via the `plugins {}` block: declaring AGP
    // there would force it onto the script classpath, and it is only published
    // to Google's Maven. Configuring it through withGroovyBuilder keeps this
    // file compiling when that repository is unreachable.
    apply(plugin = "com.android.library")
    extensions.findByName("android")?.withGroovyBuilder {
        setProperty("namespace", "com.kaiharimoto.mastertool.core")
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
    jvm()

    if (androidEnabled) {
        androidTarget()
    }

    if (iosEnabled) {
        iosArm64()
        iosSimulatorArm64()
    }

    jvmToolchain(libs.versions.jdk.get().toInt())

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}

sqldelight {
    databases {
        create("MasterToolDatabase") {
            packageName.set("com.kaiharimoto.mastertool.core.db")
        }
    }
}
