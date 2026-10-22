package io.github.esseckers.pursuitofwind.buildlogic

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty

/** Root-build DSL that declares which project dependencies each module may have. */
abstract class ModuleGraphExtension {
    internal abstract val rules: MapProperty<String, List<String>>

    internal abstract val testSupportModules: SetProperty<String>

    /** Declares [path] and the only projects it may depend on. A module with no arguments is a leaf. */
    fun module(
        path: String,
        vararg allowedDependencies: String,
    ) {
        rules.put(path, allowedDependencies.toList().sorted())
    }

    /**
     * Declares a test-support module such as `:testing:openapi`: every module may use it from its test source sets,
     * none from production code.
     */
    fun testSupport(
        path: String,
        vararg allowedDependencies: String,
    ) {
        module(path, *allowedDependencies)
        testSupportModules.add(path)
    }
}
