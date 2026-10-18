import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.github.esseckers.pursuitofwind.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "pursuitofwind.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "pursuitofwind.compose.multiplatform"
            implementationClass = "ComposeMultiplatformConventionPlugin"
        }
        register("quality") {
            id = "pursuitofwind.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("root") {
            id = "pursuitofwind.root"
            implementationClass = "RootConventionPlugin"
        }
    }
}
