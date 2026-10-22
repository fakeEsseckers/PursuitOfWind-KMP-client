import io.github.esseckers.pursuitofwind.buildlogic.appNamespace
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Shared UI (Compose Multiplatform), presentation and DI. Exported to iOS as the PursuitOfWindKit framework.
plugins {
    id("pursuitofwind.compose.multiplatform")
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
        namespace = appNamespace("ui")
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
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = appNamespace("ui.generated.resources")
    publicResClass = false
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
