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
        // A release carries the version of its tag and a build number that only grows (release.yml); other builds
        // these defaults. Google Play refuses an upload whose versionCode it has seen.
        versionCode = providers.gradleProperty("pursuitofwind.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("pursuitofwind.version").getOrElse("0.1.0")

        // The server this build talks to: -Ppursuitofwind.apiBaseUrl=https://api.example.com (an origin, no path).
        // Without it the app runs on its in-app fake backend.
        val apiBaseUrl = providers.gradleProperty("pursuitofwind.apiBaseUrl").getOrElse("")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // The release key comes from the environment (CI secrets, or a developer's own shell): nothing about it is in the
    // repository. Without it the release build is still made, unsigned, so R8 is exercised on every machine.
    val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and obfuscates. The libraries ship their own keep rules (kotlinx.serialization, Room, Ktor,
            // WorkManager, MapLibre); proguard-rules.pro holds what is the app's own.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
