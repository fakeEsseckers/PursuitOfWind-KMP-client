package io.github.esseckers.pursuitofwind.buildlogic

import org.gradle.api.provider.MapProperty

/** Root-build DSL that declares which project dependencies each module may have. */
abstract class ModuleGraphExtension {
    internal abstract val rules: MapProperty<String, List<String>>

    /** Declares [path] and the only projects it may depend on. A module with no arguments is a leaf. */
    fun module(
        path: String,
        vararg allowedDependencies: String,
    ) {
        rules.put(path, allowedDependencies.toList().sorted())
    }
}
