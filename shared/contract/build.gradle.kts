import io.github.esseckers.pursuitofwind.buildlogic.appNamespace

// API DTOs mirroring api/openapi.yaml. No project dependencies; compiled for the JVM too so the server reuses it.
plugins {
    id("pursuitofwind.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    jvm()

    android {
        namespace = appNamespace("contract")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(projects.testing.openapi)
        }
    }
}
