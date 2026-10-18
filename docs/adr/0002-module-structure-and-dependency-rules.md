# 0002. Module structure for AGP 9 and enforced dependency rules

- Status: Accepted
- Date: 2026-10-18

## Context

Android Gradle Plugin 9 no longer lets the Kotlin Multiplatform plugin share a module with `com.android.application` or `com.android.library`: the Android app must be its own module, and multiplatform libraries use `com.android.kotlin.multiplatform.library`. The decision engine and the API DTOs must stay free of Android, iOS and I/O code because a JVM server in a separate repository will consume them as libraries. Module boundaries that live only in a README erode under deadline pressure.

## Decision

- Modules: `:shared:windengine` (engine), `:shared:contract` (DTOs), `:shared:data` (network, fake backend, storage), `:shared:app` (UI, presentation, DI; iOS framework `PursuitOfWindKit`), `:androidApp` (Android entry point), plus the `iosApp/` Xcode project.
- Allowed dependencies: `androidApp → app → data → (windengine, contract)`, and `app` may also use `windengine` and `contract` directly. `windengine` and `contract` depend on no project module.
- The rules are declared once in the root `build.gradle.kts` (`moduleGraph { … }`). The `checkModuleGraph` task reads every module's project dependencies and fails `check` on any other edge, including modules missing from the rules.
- Shared build configuration lives in convention plugins in `build-logic/`: `pursuitofwind.kmp.library` (Android, iOS targets, toolchain, warnings as errors, host tests), `pursuitofwind.compose.multiplatform`, `pursuitofwind.quality` (ktlint, detekt) and `pursuitofwind.root`. Module build files only state what is specific to the module.
- The root package (`io.github.esseckers.pursuitofwind`) is a single Gradle property; namespaces derive from it.

## Alternatives considered

- One `shared` module, as in the official template — simplest, but nothing stops the engine from importing Ktor or Compose, and the server could not depend on the engine alone.
- Feature modules per screen — useful for large teams and build times; for six screens they add wiring without a benefit.
- Checking boundaries with a third-party plugin — more features, another dependency; the rule set here is five lines.

## Consequences

- Positive: a forbidden dependency between modules fails the build, not a code review; the server can depend on two small, pure modules.
- Positive: adding a module means one line in `moduleGraph { }`, which makes the new edge visible in review.
- Negative: four Gradle modules instead of one cost some configuration time.
- Not covered: third-party dependencies. The rule that `windengine` uses only the standard library and kotlinx-datetime, and `contract` only kotlinx.serialization, is checked in review; extend `moduleGraph { }` with allowed external groups if it is ever broken.
- Revisit when: the app passes roughly fifteen screens or a second developer joins — then split `:shared:app` into feature modules.
