import io.github.esseckers.pursuitofwind.buildlogic.JDK_VERSION
import io.github.esseckers.pursuitofwind.buildlogic.androidCompileSdk
import io.github.esseckers.pursuitofwind.buildlogic.androidMinSdk
import io.github.esseckers.pursuitofwind.buildlogic.androidTargetSdk
import io.github.esseckers.pursuitofwind.buildlogic.appNamespace

// Android entry point. AGP 9 compiles Kotlin itself (built-in Kotlin), so no Kotlin Android plugin is applied.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("pursuitofwind.quality")
}

android {
    namespace = appNamespace("android")
    compileSdk = androidCompileSdk

    defaultConfig {
        applicationId = appNamespace()
        minSdk = androidMinSdk
        targetSdk = androidTargetSdk
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 and release signing arrive with the release APK (Issue #12).
            isMinifyEnabled = false
        }
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true
        // Version-upgrade nags conflict with the pinned, compatibility-tested toolchain (docs/adr/0003).
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(JDK_VERSION)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(projects.shared.app)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    // The map's native engine, drawn with OpenGL ES: it runs on every device the app supports (minSdk 26).
    runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
}
