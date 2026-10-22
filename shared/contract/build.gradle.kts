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
        jvmTest {
            // The contract tests load api/openapi.yaml from the test classpath.
            resources.srcDir(layout.settingsDirectory.dir("api"))
            resources.include("openapi.yaml")
            dependencies {
                implementation(libs.swagger.parser)
                implementation(libs.json.schema.validator)
                implementation(libs.jackson.dataformat.yaml)
            }
        }
    }
}
