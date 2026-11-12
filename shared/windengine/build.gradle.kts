import io.github.esseckers.pursuitofwind.buildlogic.appNamespace

// Pure-Kotlin decision engine. No project dependencies, no I/O; compiled for the JVM too so the server reuses it,
// as the Maven artifact `windengine` (docs/adr/0012).
plugins {
    id("pursuitofwind.kmp.library")
    id("pursuitofwind.publish")
    alias(libs.plugins.kover)
}

description = "The Pursuit Of Wind decision engine: hour verdicts, session windows, kite sizing and day decisions, in pure Kotlin."

kotlin {
    explicitApi()

    jvm()

    android {
        namespace = appNamespace("engine")
    }

    sourceSets {
        commonMain.dependencies {
            // Time zones and local dates for day boundaries. Instant and Duration come from the Kotlin stdlib.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

kover {
    reports {
        verify {
            rule("Decision engine line coverage") {
                minBound(90)
            }
        }
    }
}
