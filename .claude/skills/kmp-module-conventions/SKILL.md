---
name: kmp-module-conventions
description: Module layout, source sets, dependency rules and platform-code policy for the Pursuit Of Wind Kotlin Multiplatform project. Use before creating or moving files, adding a Gradle module or dependency, writing androidMain/iosMain code, or deciding between expect/actual and an interface.
---

# KMP module conventions

## Modules and what lives where

| Module | Targets | Contains | Must not contain |
| --- | --- | --- | --- |
| `:shared:windengine` | android, ios (arm64, simulatorArm64), jvm | engine types and pure functions | any I/O, DI, serialization, platform API |
| `:shared:contract` | android, ios, jvm | `@Serializable` DTOs mirroring `api/openapi.yaml`, error body, enums | business logic, mapping to domain |
| `:shared:data` | android, ios, jvm (tests only) | Ktor client, `FakePursuitOfWindServer`, Room 3 (KSP), DataStore, repositories, `AppError`, DTO↔domain mappers, platform service interfaces, `dataModule(environment)` | Compose, ViewModels |
| `:shared:app` | android, ios, jvm (tests only) | screens as `feature.<name>` packages with MVI ViewModels and Compose UI, `core` (MVI base, design system), navigation, DI graph (`appModules`, started by `startPursuitOfWind` on Android and `MainViewController` on iOS); exports iOS framework `PursuitOfWindKit` | Ktor or Room types in public APIs |
| `:androidApp` | android | `Application`, `MainActivity`, WorkManager workers, notification channel, manifest, deep links, signing | shared business logic |
| `iosApp/` | — | Xcode project, SwiftUI `App`, `Info.plist`, BG task registration | business logic |
| `:testing:openapi` | jvm (plain Kotlin/JVM) | `OpenApiSpec`: loads `api/openapi.yaml`, validates JSON against its schemas, checks recorded requests and responses | anything production code needs |

Allowed dependency direction: `contract` and `windengine` are independent leaves and do not depend on each other. `data → windengine, contract`. `app → data, windengine, contract`. `androidApp → app`. Nothing depends on `app` except entry points. The rules live in `moduleGraph { }` in the root `build.gradle.kts`; `checkModuleGraph` (part of `check`) fails on any other project dependency. A new module needs a line there. Test-support modules are declared with `testSupport(":testing:x")`: any module may use them from its test source sets, none from production code. `:testing:openapi` is plain JVM, so it goes into `jvmTest`.

## Gradle rules

- All versions live in `gradle/libs.versions.toml`. Never hard-code a version in a build file.
- Shared build logic goes into convention plugins in `build-logic/`, not copy-pasted blocks:
  - `pursuitofwind.kmp.library` — KMP + `com.android.kotlin.multiplatform.library`, iOS arm64 + simulatorArm64, JDK 21 toolchain, compileSdk/minSdk from the catalog, Android host tests, warnings as errors, `kotlin-test` in `commonTest`. Modules add `jvm()` themselves when they need it.
  - `pursuitofwind.compose.multiplatform` — the above plus Compose Multiplatform and the Compose compiler.
  - `pursuitofwind.quality` — ktlint and detekt (applied by the KMP plugin and by `:androidApp`).
  - `pursuitofwind.root` — `moduleGraph { }` and `checkModuleGraph` (root project only).
- A module build file states only what is specific to it: `kotlin { android { namespace = appNamespace("engine") } }`, `explicitApi()`, `jvm()`, dependencies. `appNamespace()`, `androidCompileSdk` and friends come from `io.github.esseckers.pursuitofwind.buildlogic`.
- Android targets of KMP library modules use `com.android.kotlin.multiplatform.library` with the `kotlin { android { } }` block (`androidLibrary { }` is deprecated in AGP 9.3). Only `:androidApp` applies `com.android.application`; AGP 9 compiles its Kotlin without the Kotlin Android plugin.
- Namespace root is defined once as `pursuitofwind.namespace=io.github.esseckers.pursuitofwind` in `gradle.properties`, so it can be changed in one place. The Android `applicationId` uses the same value.
- Turn on configuration cache, build cache and parallel builds. Keep `org.gradle.jvmargs` sized for 16 GB machines (Codespaces 4-core).
- `explicitApi()` in `windengine` and `contract`.

## Platform code policy

1. **Interface + DI first.** Platform services are interfaces in `commonMain` of `:shared:data` (or `:shared:app` if UI-only), implemented in `androidMain` / `iosMain`, bound in Koin platform modules:
   - `LocalNotifier` — show / cancel local notifications
   - `BackgroundRefreshScheduler` — schedule periodic evaluation
   - `SecureTokenStore` — Keystore AES-GCM + DataStore on Android, Keychain on iOS
   - `ShareLauncher` — the system share sheet for an invitation (`:shared:app` `core/platform`, bound in `startPursuitOfWind` and `MainViewController`)
   - `PushTokenRegistrar` — no-op in this phase
2. **expect/actual only for small factories**: Room database builder, Ktor engine, DataStore path, platform name. No `expect class` with logic.
3. Never put Android or iOS types in `commonMain` signatures.
4. iOS-specific Kotlin lives in `iosMain` and calls platform frameworks directly (`platform.UserNotifications`, `platform.BackgroundTasks`, `platform.Security`). Swift code in `iosApp` stays thin: app entry, BG task registration, URL handling forwarded to Kotlin.

## Package layout inside a module

Library modules (`windengine`, `contract`, `data`):

```text
io.github.esseckers.pursuitofwind.<module>/
  model/        # types
  internal/     # implementation details, `internal` visibility
  di/           # Koin modules
```

`:shared:app` is organised by screen (`docs/adr/0008-mvi-presentation-in-feature-packages.md`):

```text
io.github.esseckers.pursuitofwind/
  app/                      # App(), di/appModules(), navigation/ (Navigation 3), platform entry points
  core/
    mvi/                    # MviViewModel, Reducer, CollectEffects
    designsystem/           # theme/, component/, icon/
    ui/                     # formatting and labels shared by screens
    resources/              # generated Res (packageOfResClass)
  feature/<screen>/         # today, spotdetail, profile, spots, crew, settings
    domain/model/           # the feature's models
    domain/repository/      # the repository interface the feature needs
    data/repository/        # its implementation over the :shared:data repositories
    presentation/model/     # State, Intent, Effect, UI models
    presentation/mapper/    # pure functions: domain -> UI models, inputs -> State
    presentation/viewmodel/ # ViewModel, Result, Reducer
    presentation/view/      # Route, Screen, previews
    di/                     # the feature's Koin module
```

Packages are not Gradle modules: a feature must not import another feature, and nothing enforces it at compile time, so reviewers check it. Test source sets mirror the same packages; shared fixtures of a feature sit at `feature/<screen>/` in `commonTest`.

Prefer `internal` for anything not used by another module.

## Checklist before adding a dependency

1. Is it multiplatform for every target the module has? Check Context7 or the library docs.
2. Is it the latest stable version? Add it to the version catalog with an alias.
3. Is there an existing dependency that already does the job?
4. Does it pull Android or iOS types into `commonMain`? If yes, it belongs in a platform source set.
5. Mention the new dependency and why in the PR description.

## Same code, different runtimes

Common code runs on three runtimes, and host tests only cover one of them: Android host tests run on the desktop JVM, not on Android's runtime. Known differences that compile and pass on the JVM but fail on a device:

- `Regex` on Android uses ICU, which rejects patterns the JVM accepts, such as an unescaped `}`. Prefer simple string parsing in common code; when a pattern is needed, escape every brace (`\{`, `\}`) and run the app on an emulator before the PR.
- Anything in `androidMain` (Keystore, file paths, `Context`) only runs on a device or emulator. Say so in the PR when it was not run.

