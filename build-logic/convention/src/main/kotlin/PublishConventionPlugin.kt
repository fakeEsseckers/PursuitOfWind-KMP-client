import io.github.esseckers.pursuitofwind.buildlogic.appNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Publishes a module the server reuses as Maven artifacts (docs/adr/0012): `<namespace>:<module>`, with the release
 * tag without its `v` as the version (`-Ppursuitofwind.version=1.2.0`). Without a version it is a snapshot, good for
 * `publishToMavenLocal` only.
 *
 * The one remote repository is GitHub Packages of the repository the workflow runs in, with the workflow's own token.
 * Outside GitHub Actions there is none, so nothing can be published from a developer's machine by accident, and no
 * credentials live in the build.
 */
class PublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            group = appNamespace()
            version = providers.gradleProperty("pursuitofwind.version").getOrElse("0.0.0-SNAPSHOT")

            val repository = providers.environmentVariable("GITHUB_REPOSITORY").orNull
            extensions.configure<PublishingExtension> {
                if (repository != null) {
                    repositories.maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/$repository")
                        credentials {
                            username = providers.environmentVariable("GITHUB_ACTOR").orNull
                            password = providers.environmentVariable("GITHUB_TOKEN").orNull
                        }
                    }
                }
                publications.withType<MavenPublication>().configureEach {
                    pom {
                        name.set("Pursuit Of Wind ${target.name}")
                        description.set(provider { target.description })
                        url.set("https://github.com/${repository ?: DEFAULT_REPOSITORY}")
                        licenses {
                            license {
                                name.set("Apache-2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        scm {
                            url.set("https://github.com/${repository ?: DEFAULT_REPOSITORY}")
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_REPOSITORY = "fakeEsseckers/PursuitOfWind-KMP-client"
    }
}
