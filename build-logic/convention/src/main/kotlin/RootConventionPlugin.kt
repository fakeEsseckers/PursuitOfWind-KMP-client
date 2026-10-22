import io.github.esseckers.pursuitofwind.buildlogic.ModuleGraphCheckTask
import io.github.esseckers.pursuitofwind.buildlogic.ModuleGraphExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Root project conventions: the `moduleGraph { }` rules and the `checkModuleGraph` task wired into `check`.
 *
 * The task reads other projects' dependency declarations from the root project. That is compatible with the
 * configuration cache, but not with Isolated Projects; move the check into each module before enabling that.
 */
class RootConventionPlugin : Plugin<Project> {
    private companion object {
        val TEST_CONFIGURATION = Regex("^test|Test(?=[A-Z]|$)")
    }

    /**
     * Project dependencies per module, from test configurations or from all others. A test configuration starts with
     * `test` or has `Test` as a whole camel-case word (`testImplementation`, `jvmTestImplementation`,
     * `androidHostTestCompileClasspath`), so names like `latestRelease` are not mistaken for one.
     * Folders such as `:shared` are containers without a build file, not modules.
     */
    private fun Project.projectEdges(test: Boolean): Map<String, List<String>> =
        subprojects.filter { it.buildFile.isFile }.associate { module ->
            module.path to
                module.configurations
                    .filter { TEST_CONFIGURATION.containsMatchIn(it.name) == test }
                    .flatMap { configuration -> configuration.dependencies.withType<ProjectDependency>().map { it.path } }
                    .filter { it != module.path }
                    .distinct()
                    .sorted()
        }

    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "pursuitofwind.root must be applied to the root project only" }
            pluginManager.apply("base")

            val moduleGraph = extensions.create<ModuleGraphExtension>("moduleGraph")

            val checkModuleGraph =
                tasks.register<ModuleGraphCheckTask>("checkModuleGraph") {
                    group = "verification"
                    description = "Verifies that modules only depend on the projects allowed by moduleGraph { }."
                    allowedEdges.set(moduleGraph.rules)
                    testSupportModules.set(moduleGraph.testSupportModules)
                    actualEdges.set(provider { projectEdges(test = false) })
                    actualTestEdges.set(provider { projectEdges(test = true) })
                    report.set(layout.buildDirectory.file("reports/module-graph/report.txt"))
                }

            tasks.named("check") { dependsOn(checkModuleGraph) }
        }
    }
}
