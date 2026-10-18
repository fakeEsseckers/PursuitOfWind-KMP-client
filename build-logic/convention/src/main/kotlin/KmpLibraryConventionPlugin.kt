import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.github.esseckers.pursuitofwind.buildlogic.JDK_VERSION
import io.github.esseckers.pursuitofwind.buildlogic.androidCompileSdk
import io.github.esseckers.pursuitofwind.buildlogic.androidMinSdk
import io.github.esseckers.pursuitofwind.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Kotlin Multiplatform library shared by Android and iOS.
 *
 * - Android target through AGP 9's `com.android.kotlin.multiplatform.library` plugin, with SDK levels from the
 *   version catalog and host tests, so `commonTest` also runs on the Android runtime.
 *   Each module declares only its own `kotlin { android { namespace = … } }`.
 * - iOS device and Apple-silicon simulator targets. Intel simulators are not targeted (Tier 3 in Kotlin 2.4).
 * - JVM is opt-in per module (`jvm()`): only modules the server reuses or tests on the JVM need it.
 * - Warnings fail the build.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("pursuitofwind.quality")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(JDK_VERSION)

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    compileSdk = androidCompileSdk
                    minSdk = androidMinSdk
                    withHostTest {}
                }

                iosArm64()
                iosSimulatorArm64()

                compilerOptions {
                    allWarningsAsErrors.set(true)
                }

                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.findLibrary("kotlin-test").get())
                }
            }
        }
    }
}
