package io.github.esseckers.pursuitofwind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a module depends on a project it is not allowed to depend on.
 *
 * The allowed graph is declared once in the root build script (`moduleGraph { … }`), so a forbidden
 * edge such as `:shared:windengine -> :shared:data` is caught by `check` instead of in code review.
 * Test-support modules may only be used from test configurations.
 */
@CacheableTask
abstract class ModuleGraphCheckTask : DefaultTask() {
    /** Project dependencies of production configurations, keyed by module path. */
    @get:Input
    abstract val actualEdges: MapProperty<String, List<String>>

    /** Project dependencies of test configurations, keyed by module path. */
    @get:Input
    abstract val actualTestEdges: MapProperty<String, List<String>>

    /** Project dependencies each module may declare, keyed by module path. */
    @get:Input
    abstract val allowedEdges: MapProperty<String, List<String>>

    /** Modules that any module may use from tests, and none from production code. */
    @get:Input
    abstract val testSupportModules: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val allowed = allowedEdges.get()
        val testSupport = testSupportModules.get()
        val modules = (actualEdges.get().keys + actualTestEdges.get().keys).sorted()
        val misdeclared =
            allowed.flatMap { (module, permitted) ->
                permitted.filter { it in testSupport }.map { "$module lists test-support module $it as an allowed dependency" }
            }
        val edges =
            modules.flatMap { module ->
                val permitted = allowed[module] ?: return@flatMap listOf("$module is not declared in moduleGraph { } of the root build script")
                val main = actualEdges.get()[module].orEmpty().filterNot { it in permitted }
                val test = actualTestEdges.get()[module].orEmpty().filterNot { it in permitted || it in testSupport }
                main.map { dependency ->
                    if (dependency in testSupport) "$module must use $dependency from tests only" else "$module must not depend on $dependency"
                } + test.map { "$module must not depend on $it, even from tests" }
            }
        val violations = misdeclared + edges

        report.get().asFile.writeText(
            if (violations.isEmpty()) "Module graph OK\n" else violations.joinToString(separator = "\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module dependency rules violated:\n" + violations.joinToString(separator = "\n") { "  - $it" },
            )
        }
    }
}
