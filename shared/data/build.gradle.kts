import io.github.esseckers.pursuitofwind.buildlogic.appNamespace
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// Networking, the in-app fake backend, the offline cache (Room 3, DataStore) and repositories.
plugins {
    id("pursuitofwind.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.kover)
}

kotlin {
    // JVM target for contract tests that validate the fake backend against api/openapi.yaml, and for fast database tests.
    jvm()

    android {
        namespace = appNamespace("data")
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            // Tests that need a real SQLite database. The bundled driver loads on the JVM and on iOS; Android host tests
            // run on a desktop JVM but resolve the Android variant, whose native library only exists for devices.
            group("sqlite") {
                withJvm()
                group("ios")
            }
        }
    }

    compilerOptions {
        // Room generates the `actual` of the `expect object` database constructor.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.windengine)
            api(projects.shared.contract)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)
            api(libs.koin.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            implementation(projects.testing.openapi)
            implementation(libs.koin.test)
        }
    }
}

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room3.compiler) }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
