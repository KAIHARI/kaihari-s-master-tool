plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

// iOS needs the Kotlin/Native toolchain (a large download) and is only useful on
// a Mac, so it stays opt-in until there's an Xcode host to build it on.
val iosEnabled =
    providers.gradleProperty("mastertool.ios").orNull?.toBooleanStrictOrNull()
        ?: System.getProperty("os.name").startsWith("Mac")

kotlin {
    // Deliberately no androidTarget(). Nothing here touches an Android API, and
    // Kotlin's jvm -> androidJvm compatibility rule lets the Android app consume
    // this module's JVM variant directly. That keeps :core free of the Android
    // Gradle Plugin, so the domain layer builds and tests anywhere.
    jvm()

    if (iosEnabled) {
        iosArm64()
        iosSimulatorArm64()
    }

    jvmToolchain(libs.versions.jdk.get().toInt())

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // `api`, not `implementation`: JsonObject appears in this module's
            // public surface (the preserved #ydkx-extended payload), so callers
            // must be able to see the type.
            api(libs.kotlinx.serialization.json)
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

        // HTTP engines and SQL drivers are chosen by each application, not here,
        // so a JDBC driver never ends up inside the APK.
        jvmTest.dependencies {
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}

sqldelight {
    databases {
        create("MasterToolDatabase") {
            packageName.set("com.kaiharimoto.mastertool.core.db")
            // `verifyMigrations` is deliberately left off: it requires a checked-in
            // .db snapshot of each old schema, and this plugin version registers no
            // task to produce one. `MigrationTest` makes the same assertion from
            // the test source set instead — it upgrades a real version 1 database
            // and compares the result against a freshly created one — which has the
            // advantage of running in the :core test job on every push.
        }
    }
}
