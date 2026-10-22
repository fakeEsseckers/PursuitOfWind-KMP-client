---
name: testing-playbook
description: How tests are written and run in Pursuit Of Wind — test pyramid, naming, table-driven and property tests, coroutines with virtual time and Turbine, fake server usage, contract and screenshot tests, coverage and commands. Use when writing or fixing any test, investigating a flaky test, or checking coverage.
---

# Testing playbook

## Pyramid and where tests live

| Layer | Source set | Tools | Weight |
| --- | --- | --- | --- |
| Engine rules | `:shared:windengine` `commonTest` | kotlin-test, Kotest property testing | highest |
| Mappers, formatters | `commonTest` of the owning module | kotlin-test | high |
| Session, retries, error mapping | `:shared:data` `commonTest` | `NetworkHarness` on `FakePursuitOfWindServer`, coroutines-test | high |
| Repositories, cache, offline, feed sync | `:shared:data` `sqliteTest` (JVM + iOS) | `World` / `DataHarness`: in-memory Room, fake server, test dispatcher | high |
| Contract | `:shared:data` `jvmTest` | OpenAPI validator against `api/openapi.yaml` | every route |
| ViewModels | `:shared:app` `commonTest` | Turbine, `StandardTestDispatcher`, `ControllableClock` | per screen |
| UI critical path | `:shared:app` common UI tests or `androidApp` instrumented | Compose UI test | 1–2 flows |
| Screenshots | Android/JVM | Roborazzi (optional) | key screens × demo scenarios |

## Conventions

- Test names describe behavior in backticks: `` `gust spread over tolerance by 4 knots is NO_GO GUSTY`() ``.
- Kotlin/Native (iOS tests) rejects some characters in backticked names: no `.`, `,`, `:`, `;`, `/`, `\`, `<`, `>`, `[`, `]`, `(`, `)`. Write "0 until 360", not "[0, 360)". `./gradlew :shared:windengine:compileTestKotlinIosSimulatorArm64` catches this on Linux too.
- Property tests use a fixed seed (`PropTestConfig(seed = …)`, opt-in `ExperimentalKotest`) so every run and platform checks the same cases.
- Engine vectors are table-driven: one `data class Vector(...)` list per section of `wind-domain-rules/reference.md`, IDs in the test name (`C3`, `D4`) so failures point to the rule.
- Arrange data with builders (`forecastHour { wind(18.kn, 24.kn) }`, `hoursFromPattern(start, "N G G M G N")` in `Fixtures.kt`), not long constructors. ktlint_official allows one statement per line, so give builders helpers instead of `a = 1; b = 2` one-liners.
- No real time, no real network, no `Random` without a seed, no `Thread.sleep`.

## Coroutines

- Use `runTest` with `StandardTestDispatcher`; inject the test dispatcher into ViewModels and repositories.
- Advance time with `advanceTimeBy` / `advanceUntilIdle`; for demo time use `ControllableClock.advanceBy`.
- Collect flows with Turbine: `viewModel.state.test { … awaitItem() … cancelAndIgnoreRemainingEvents() }`.
- Assert every emitted state that matters (loading → ready), not only the last one.

## Using the fake server in tests

```kotlin
@Test
fun `a retried seat request does not take a second seat`() =
    runTest {
        val driver = fakeDriver() // fresh server, ControllableClock at THURSDAY_MORNING, engine on the test scheduler
        val token = driver.signIn()
        driver.send(ApiRoute.JOIN_CREW, token = token, value = JoinCrewRequestDto("WIND-4KQ7"))
        …
    }
```

- Pass the test dispatcher to `FakePursuitOfWindServer(clock, seed, dispatcher)` (as `fakeDriver()` does) so `SLOW` waits in virtual time.
- `server.networkMode = NetworkMode.FLAKY` fails about 30% of requests with `503`, in the same order for the same seed; `OFFLINE` throws `IOException`.
- Move time with `ControllableClock.advanceBy` to expire access tokens (1 h) or idempotency keys (24 h).
- Repository tests use the same server through the app's real HTTP clients: `world { val alex = device(); … }` in `sqliteTest` gives each device its own in-memory database, settings and token store against one fake server and clock. Two devices are two users.
- Room and DataStore take the test dispatcher (`PursuitOfWindDatabase.build(builder, dispatcher)`, `LocalSettings.create(path, scope)`), so an optimistic update can be read between virtual-time delays. With the default `Dispatchers.IO`, `runTest` skips delays while the test waits on a database thread.
- Room tests live in `sqliteTest`, a source set shared by `jvmTest` and `iosTest`: the bundled SQLite driver does not load in Android host tests.

## Property tests (Kotest)

- Bearing normalization is idempotent and always in [0, 360).
- `Sector.contains` agrees with a brute-force check on whole degrees, including sectors that wrap past 0°.
- Sizing: ideal kite size strictly decreases as mean wind increases.
- Windows from any random verdict sequence never overlap and never start or end on MARGINAL.

## Commands

```text
./scripts/check.sh                              # everything CI runs on Linux
./gradlew :shared:windengine:allTests           # engine on all available targets (JVM, Android host, iOS on macOS)
./gradlew :shared:windengine:jvmTest            # fastest loop
./gradlew :shared:windengine:testAndroidHostTest
./gradlew :shared:data:jvmTest                  # data layer, fake backend and contract tests (JVM)
./gradlew koverHtmlReport                       # coverage report (build/reports/kover/html)
./gradlew iosSimulatorArm64Test                 # macOS only
```

`commonTest` runs on three runtimes: the JVM (`jvm()` modules), the Android host (`withHostTest`, set by `pursuitofwind.kmp.library`) and the iOS simulator. Write tests that pass on all three: no `java.*` APIs in `commonTest`, and do not rely on platform-specific `Double.toString` formatting beyond simple values.

## Flaky test protocol

1. Re-run the single test 20 times (`--rerun-tasks` plus a loop) to confirm flakiness.
2. Look for real time, unseeded randomness, shared mutable state between tests, or `Dispatchers.Main` without a test dispatcher.
3. Fix the cause. Never add retries or `@Ignore` without an issue link.

## Coverage targets

`:shared:windengine` ≥ 90% line coverage (Kover rule fails the build). Other modules: no hard threshold — cover behavior, not lines.
