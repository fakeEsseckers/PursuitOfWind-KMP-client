# Pursuit Of Wind

Kitesurf session decision app: tells a rider whether it is worth driving to the spot today (window, kite size, when to leave, confidence) and who from their crew is driving with free seats. Kotlin Multiplatform client (Android + iOS, Compose Multiplatform). This phase is client-only: the API runs on an in-app fake backend; a Ktor server implementing `api/openapi.yaml` comes later in a separate repository and consumes `:shared:windengine` and `:shared:contract` as published Maven artifacts.

The full brief for this phase is `docs/prompts/client-phase.ru.md` (Russian). This file is the short operating manual.

## Language

- Talk to the owner in **Russian**.
- Everything committed (code, comments, KDoc, docs, ADRs, commit messages, PR and issue text) is **English**.
- After each task, give the owner a 3–5 line Russian summary: what was done, how to check, what is left.

## Commands

```text
./scripts/setup.sh                          # one-time: checks JDK, Android SDK, Xcode; writes local.properties
./scripts/check.sh                          # what Linux CI runs: ktlint, detekt, Lint, all tests, checkModuleGraph, koverVerify, debug APK
./gradlew :androidApp:installDebug          # run on a device or emulator
./gradlew :shared:windengine:allTests       # engine tests on every target available on this machine
./gradlew :shared:windengine:jvmTest        # fastest feedback loop for engine work
./gradlew :shared:windengine:testAndroidHostTest
./gradlew :shared:contract:jvmTest          # API contract: spec, examples and DTO parity
./gradlew :shared:data:jvmTest              # data layer and fake backend tests, contract validation against api/openapi.yaml
./gradlew :shared:app:jvmTest               # screens: mappers, ViewModels on the real data layer, Compose UI tests (desktop)
./gradlew iosSimulatorArm64Test             # macOS only
./gradlew ktlintFormat                      # auto-fix formatting
./gradlew checkModuleGraph                  # module dependency rules
./gradlew koverHtmlReport                   # coverage report: build/reports/kover/html
```

Toolchain versions and why: `gradle/libs.versions.toml` and `docs/adr/0003-dependency-version-policy.md`. Expect one Gradle deprecation warning from the detekt 1.23.8 plugin; it is known and documented there.

## Module map

| Module | Purpose |
| --- | --- |
| `:shared:windengine` | pure-Kotlin decision engine (android, ios, jvm) — reused by the server |
| `:shared:contract` | API DTOs mirroring `api/openapi.yaml` (android, ios, jvm) |
| `:shared:data` | Ktor client, `FakePursuitOfWindServer`, Room, DataStore, repositories, `AppError` |
| `:testing:openapi` | JVM, test-only: validates requests and responses against `api/openapi.yaml` |
| `:shared:app` | screens as `feature.<name>` packages (domain / data / presentation with MVI / di), `core` (MVI base, design system), `app` (root composable, Navigation 3, Koin graph); iOS framework `PursuitOfWindKit` |
| `:androidApp` | Android entry point, WorkManager, notifications, deep links |
| `iosApp/` | Xcode project, SwiftUI host, BG tasks, URL scheme |

Dependencies point one way: `androidApp → app → data → (windengine, contract)`; `app` may also use `windengine` and `contract` directly. `windengine` and `contract` depend on nothing project-internal. `:testing:*` modules may be used from tests only. `checkModuleGraph` enforces this.

## Hard rules

1. The engine is pure: no `Clock.System`, no I/O, no platform or DI types; `now` is a parameter. Units inside are SI.
2. Contract first: `api/openapi.yaml` → DTOs → fake route → contract test. Never the other way round.
3. The fake backend sits at the HTTP layer (an in-process Ktor engine). No fake repositories.
4. Platform services are interfaces in common code with platform implementations bound in Koin. `expect/actual` only for small factories.
5. There is no demo mode. The in-app fake backend behaves like a live server (every forecast request is a new model run) while staying deterministic for a seed and a sequence of requests (ADR 0010).
6. No secrets in the repo. Signing material and tokens come from environment variables or CI secrets.
7. Verify library APIs and versions with Context7 or official docs before using them. Use the latest stable versions via `gradle/libs.versions.toml`.
8. Do not use the deprecated `androidx.security:security-crypto`.
9. Kite sizing and verdicts are decision support, not safety advice; the disclaimer stays visible in the app and README.

## Workflow

One issue → one branch → one PR, following the `github-issue-workflow` skill. Plan first (plan mode) when a change is architectural or touches more than 5 files. Run `./scripts/check.sh` and the `code-reviewer` subagent before every PR. Use Conventional Commits.

## Who does what

| Work | Subagent | Skills |
| --- | --- | --- |
| Modules, Gradle, architecture ADRs | `kmp-architect` | `kmp-module-conventions`, `adr-writing` |
| Decision engine and its tests | `wind-engine-engineer` | `wind-domain-rules`, `testing-playbook` |
| OpenAPI, DTOs, fake server, contract tests | `api-contract-engineer` | `api-contract-workflow`, `fake-server` |
| Screens, design system, wind chart | `compose-ui-engineer` | `compose-screen-recipe` |
| Android platform code | `android-platform-engineer` | `kmp-module-conventions` |
| iOS platform code, Xcode | `ios-platform-engineer` | `kmp-module-conventions` |
| CI, scripts, devcontainer, releases | `build-ci-engineer` | `github-issue-workflow` |
| Missing tests, flaky tests | `test-engineer` | `testing-playbook` |
| Review before PR (read-only) | `code-reviewer` | all relevant |
| README, ADRs, issue texts | `docs-writer` | `adr-writing` |

Independent work (e.g. engine and contract) can run in parallel subagents.

## Environment notes

- Primary development is local on the owner's Mac (Android Studio, Xcode, Android emulator, iOS simulator). Codespaces is a fallback.
- Codespaces can build shared modules, run JVM/Android unit tests and assemble the APK. It cannot run an emulator or anything iOS.
- On machines with 8 GB of RAM, run Gradle with `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx2560m` to avoid the daemon being killed.
- iOS builds need macOS + Xcode (local Mac or the macOS CI job). Never claim an iOS build or run was verified when it was not.
