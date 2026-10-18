import io.github.esseckers.pursuitofwind.buildlogic.appNamespace

// Networking, the in-app fake backend, persistence and repositories (Issues #5–#6).
plugins {
    id("pursuitofwind.kmp.library")
}

kotlin {
    // JVM target for contract tests that validate the fake backend against api/openapi.yaml.
    jvm()

    android {
        namespace = appNamespace("data")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.windengine)
            api(projects.shared.contract)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
