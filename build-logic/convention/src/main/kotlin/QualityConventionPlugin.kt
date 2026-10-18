import io.github.esseckers.pursuitofwind.buildlogic.libs
import io.github.esseckers.pursuitofwind.buildlogic.version
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Static analysis for every module: ktlint (formatting, `ktlint_official` style from `.editorconfig`)
 * and detekt (code smells, `config/detekt/detekt.yml`). Both run as part of `check`.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlint"))
                filter {
                    // Generated sources (Compose resources, KSP) are not ours to format.
                    exclude { element -> element.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                parallel = true
                config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
                source.setFrom(files("src"))
            }

            // detekt 1.23.x embeds the Kotlin 2.0.21 compiler and refuses to run on another one.
            // Keep its isolated classpath on that version; the project itself compiles with the catalog's Kotlin.
            configurations.matching { it.name == "detekt" }.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion(DETEKT_KOTLIN_VERSION)
                    }
                }
            }
        }
    }

    private companion object {
        const val DETEKT_KOTLIN_VERSION = "2.0.21"
    }
}
