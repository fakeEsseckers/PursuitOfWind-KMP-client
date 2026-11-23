import io.github.esseckers.pursuitofwind.buildlogic.appNamespace

// API DTOs mirroring api/openapi.yaml. No project dependencies; compiled for the JVM too so the server reuses it,
// as the Maven artifact `contract` (docs/adr/0012).
plugins {
    id("pursuitofwind.kmp.library")
    id("pursuitofwind.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

description = "The Pursuit Of Wind API contract: the DTOs, routes and limits of api/openapi.yaml as Kotlin types."

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
