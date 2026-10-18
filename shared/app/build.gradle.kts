import io.github.esseckers.pursuitofwind.buildlogic.appNamespace
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Shared UI (Compose Multiplatform), presentation and DI. Exported to iOS as the PursuitOfWindKit framework.
plugins {
    id("pursuitofwind.compose.multiplatform")
}

kotlin {
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

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
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
