package io.github.esseckers.pursuitofwind.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog, for use inside convention plugins where the generated accessor is unavailable. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()

/**
 * Root package of the project, read from `pursuitofwind.namespace` in `gradle.properties`.
 * Module packages and Android namespaces are derived from it so the root can be changed in one place.
 */
fun Project.appNamespace(suffix: String? = null): String {
    val root = providers.gradleProperty("pursuitofwind.namespace").get()
    return if (suffix == null) root else "$root.$suffix"
}

/** Android compile SDK shared by every module, from the version catalog. */
val Project.androidCompileSdk: Int get() = libs.intVersion("android-compileSdk")

/** Android minimum SDK shared by every module, from the version catalog. */
val Project.androidMinSdk: Int get() = libs.intVersion("android-minSdk")

/** Android target SDK of the application, from the version catalog. */
val Project.androidTargetSdk: Int get() = libs.intVersion("android-targetSdk")

/** JDK used to compile every Kotlin and Java source set. */
const val JDK_VERSION: Int = 21
