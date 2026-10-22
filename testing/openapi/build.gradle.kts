import io.github.esseckers.pursuitofwind.buildlogic.JDK_VERSION

// Test support: api/openapi.yaml on the classpath and validation of requests and responses against it. Used by the
// JVM contract tests of :shared:contract and :shared:data; checkModuleGraph keeps it out of production code.
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("pursuitofwind.quality")
}

kotlin {
    jvmToolchain(JDK_VERSION)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

sourceSets.main {
    resources.srcDir(layout.settingsDirectory.dir("api"))
    resources.include("openapi.yaml")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(libs.swagger.parser)
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.dataformat.yaml)
    testImplementation(libs.kotlin.test)
}
