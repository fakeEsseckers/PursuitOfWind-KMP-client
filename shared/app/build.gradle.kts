import io.github.esseckers.pursuitofwind.buildlogic.appNamespace
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Shared UI (Compose Multiplatform), presentation and DI. Exported to iOS as the PursuitOfWindKit framework.
plugins {
    id("pursuitofwind.compose.multiplatform")
    // Navigation keys are @Serializable, so the back stack survives process death on every platform.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Tests only: ViewModels run against the real data layer (fake backend, in-memory Room), and Compose UI tests run
    // on the desktop runtime, so neither needs an emulator. No app ships for the JVM.
    jvm()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PursuitOfWindKit"
            isStatic = true
            binaryOption("bundleId", appNamespace("kit"))
        }
    }

    android {
        namespace = appNamespace("app")
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.data)
            implementation(projects.shared.windengine)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.navigation.suite)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    packageOfResClass = appNamespace("core.resources")
    publicResClass = false
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
