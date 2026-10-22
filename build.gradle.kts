plugins {
    // Put every plugin on the root classpath once; modules apply them by id through the convention plugins.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.kover)
    id("pursuitofwind.root")
}

// Allowed project dependencies. `checkModuleGraph` (part of `check`) fails on anything else.
// See docs/adr/0002-module-structure-and-dependency-rules.md.
moduleGraph {
    module(":shared:windengine")
    module(":shared:contract")
    module(":shared:data", ":shared:windengine", ":shared:contract")
    module(":shared:app", ":shared:data", ":shared:windengine", ":shared:contract")
    module(":androidApp", ":shared:app")

    // Test support: any module may use these from its tests, none from production code.
    testSupport(":testing:openapi")
}

// Aggregated JVM coverage report: ./gradlew koverHtmlReport
dependencies {
    kover(projects.shared.windengine)
    kover(projects.shared.contract)
    kover(projects.shared.data)
}
