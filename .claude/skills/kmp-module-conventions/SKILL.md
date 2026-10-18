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
| `:shared:data` | android, ios, jvm (tests only) | Ktor client, `FakePursuitOfWindServer`, Room, DataStore, repositories, `AppError`, DTO↔domain mappers, platform service interfaces | Compose, ViewModels |
| `:shared:app` | android, ios | ViewModels, Compose UI, navigation, theme, DI graph (`initKoin`), demo mode UI; exports iOS framework `PursuitOfWindKit` | Ktor or Room types in public APIs |
| `:androidApp` | android | `Application`, `MainActivity`, WorkManager workers, notification channel, manifest, deep links, signing | shared business logic |
| `iosApp/` | — | Xcode project, SwiftUI `App`, `Info.plist`, BG task registration | business logic |

Allowed dependency direction: `contract` and `windengine` are independent leaves and do not depend on each other. `data → windengine, contract`. `app → data, windengine, contract`. `androidApp → app`. Nothing depends on `app` except entry points. The rules live in `moduleGraph { }` in the root `build.gradle.kts`; `checkModuleGraph` (part of `check`) fails on any other project dependency. A new module needs a line there.

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
   - `ShareLauncher` — share an invite link
   - `PushTokenRegistrar` — no-op in this phase
2. **expect/actual only for small factories**: Room database builder, Ktor engine, DataStore path, platform name. No `expect class` with logic.
3. Never put Android or iOS types in `commonMain` signatures.
4. iOS-specific Kotlin lives in `iosMain` and calls platform frameworks directly (`platform.UserNotifications`, `platform.BackgroundTasks`, `platform.Security`). Swift code in `iosApp` stays thin: app entry, BG task registration, URL handling forwarded to Kotlin.

## Package layout inside a module

```text
io.github.esseckers.pursuitofwind.<module>/
  model/        # types
  <feature>/    # e.g. today, spot, crew, demo (in :shared:app)
  internal/     # implementation details, `internal` visibility
  di/           # Koin modules
```

Prefer `internal` for anything not used by another module.

## Checklist before adding a dependency

1. Is it multiplatform for every target the module has? Check Context7 or the library docs.
2. Is it the latest stable version? Add it to the version catalog with an alias.
3. Is there an existing dependency that already does the job?
4. Does it pull Android or iOS types into `commonMain`? If yes, it belongs in a platform source set.
5. Mention the new dependency and why in the PR description.
