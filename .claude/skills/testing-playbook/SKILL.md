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
| Repositories, token refresh, offline | `:shared:data` `commonTest` | `FakePursuitOfWindServer`, coroutines-test | high |
| Contract | `:shared:data` `jvmTest` | OpenAPI validator against `api/openapi.yaml` | every route |
| ViewModels | `:shared:app` `commonTest` | Turbine, `StandardTestDispatcher`, `ControllableClock` | per screen |
| UI critical path | `:shared:app` common UI tests or `androidApp` instrumented | Compose UI test | 1–2 flows |
| Screenshots | Android/JVM | Roborazzi (optional) | key screens × demo scenarios |

## Conventions

- Test names describe behavior in backticks: `` `gust spread over tolerance by 4 knots is NO_GO GUSTY`() ``.
- Engine vectors are table-driven: one `data class Vector(...)` list per section of `wind-domain-rules/reference.md`, IDs in the test name (`C3`, `D4`) so failures point to the rule.
- Arrange data with builders (`forecastHour { mean = 18.kn; gust = 24.kn; bearing = 240.0 }`), not long constructors.
- No real time, no real network, no `Random` without a seed, no `Thread.sleep`.

## Coroutines

- Use `runTest` with `StandardTestDispatcher`; inject the test dispatcher into ViewModels and repositories.
- Advance time with `advanceTimeBy` / `advanceUntilIdle`; for demo time use `ControllableClock.advanceBy`.
- Collect flows with Turbine: `viewModel.state.test { … awaitItem() … cancelAndIgnoreRemainingEvents() }`.
- Assert every emitted state that matters (loading → ready), not only the last one.

## Using the fake server in tests

```kotlin
val server = FakePursuitOfWindServer(seed = Seed.from(DemoScenarioId.SaturdayOn), clock = clock)
val client = createHttpClient(engine = server.engine, tokenStore = InMemoryTokenStore())
```

- Network modes: set `server.network = NetworkMode.Flaky(seed = 1)` to test retries, `Offline` to test cache fallback.
- Test the optimistic seat join rollback with the `car-fills-up` seed.

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
./gradlew :shared:data:jvmTest                  # contract tests
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
