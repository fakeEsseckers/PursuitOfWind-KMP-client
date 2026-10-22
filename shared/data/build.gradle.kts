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
            // The in-app fake backend ships with the app (FAKE and DEMO environments), so the mock engine is not test-only.
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(projects.testing.openapi)
        }
    }
}
