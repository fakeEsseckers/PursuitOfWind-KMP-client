import org.gradle.api.Plugin
import org.gradle.api.Project

/** Compose Multiplatform UI on top of [KmpLibraryConventionPlugin]: the JetBrains Compose plugin and the Kotlin Compose compiler. */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("pursuitofwind.kmp.library")
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
    }
}
