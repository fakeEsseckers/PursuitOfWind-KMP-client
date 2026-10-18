package io.github.esseckers.pursuitofwind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a module depends on a project it is not allowed to depend on.
 *
 * The allowed graph is declared once in the root build script (`moduleGraph { … }`), so a forbidden
 * edge such as `:shared:windengine -> :shared:data` is caught by `check` instead of in code review.
 */
@CacheableTask
abstract class ModuleGraphCheckTask : DefaultTask() {
    /** Project dependencies each module actually declares, keyed by module path. */
    @get:Input
    abstract val actualEdges: MapProperty<String, List<String>>

    /** Project dependencies each module may declare, keyed by module path. */
    @get:Input
    abstract val allowedEdges: MapProperty<String, List<String>>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val allowed = allowedEdges.get()
        val violations =
            actualEdges.get().toSortedMap().flatMap { (module, dependencies) ->
                val permitted = allowed[module]
                when {
                    permitted == null -> listOf("$module is not declared in moduleGraph { } of the root build script")
                    else -> dependencies.filterNot { it in permitted }.map { "$module must not depend on $it" }
                }
            }

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
