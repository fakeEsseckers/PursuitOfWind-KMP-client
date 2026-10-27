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
| Screen mappers (engine → UI models, state rules), reducers, refresh policies | `:shared:app` `commonTest` | kotlin-test, fixtures in `feature/<screen>/<Screen>Fixtures.kt` | high |
| MVI base (`MviViewModel`) | `:shared:app` `commonTest` | coroutines-test, Turbine, a test ViewModel | once |
| ViewModels | `:shared:app` `jvmTest` | Turbine, `StandardTestDispatcher`, `ControllableClock`, the real data layer through `appModules(Environment.Fake, clock)` | per screen |
| Screens | `:shared:app` `jvmTest` | Compose UI test on the desktop runtime (`androidx.compose.ui.test.v2.runComposeUiTest`) | each state, semantics, 200% font |
| Screenshots | Android/JVM | Roborazzi (optional) | key screens × preview states |

## Conventions

- Test names describe behavior in backticks: `` `gust spread over tolerance by 4 knots is NO_GO GUSTY`() ``.
- Kotlin/Native (iOS tests) rejects some characters in backticked names: no `.`, `,`, `:`, `;`, `/`, `\`, `<`, `>`, `[`, `]`, `(`, `)`. Write "0 until 360", not "[0, 360)". `./gradlew :shared:windengine:compileTestKotlinIosSimulatorArm64` catches this on Linux too.
- Property tests use a fixed seed (`PropTestConfig(seed = …)`, opt-in `ExperimentalKotest`) so every run and platform checks the same cases.
- Engine vectors are table-driven: one `data class Vector(...)` list per section of `wind-domain-rules/reference.md`, IDs in the test name (`C3`, `D4`) so failures point to the rule.
- Arrange data with builders (`forecastHour { wind(18.kn, 24.kn) }`, `hoursFromPattern(start, "N G G M G N")` in `Fixtures.kt`), not long constructors. ktlint_official allows one statement per line, so give builders helpers instead of `a = 1; b = 2` one-liners.
- No real time, no real network, no `Random` without a seed, no `Thread.sleep`.

## Coroutines

- Use `runTest` with `StandardTestDispatcher`; inject the test dispatcher into ViewModels and repositories.
- Advance time with `advanceTimeBy` / `advanceUntilIdle`; for the app's and the fake's time use `ControllableClock.advanceBy`.
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

## ViewModel and screen tests (`:shared:app`)

- Tests sit in the same package as the code they test (`feature/today/presentation/mapper/DayCardsMapperTest`).
- `testGraph(clock)` in `jvmTest/.../testing/TestGraph.kt` is the app's Koin graph for the fake environment with a steady 18 kn seed; ViewModel and navigation tests start from it instead of building their own.
- JVM and Android host tests run with `user.timezone=UTC` (set in `KmpLibraryConventionPlugin`), as CI does, while the fixtures live in Berlin. Pass the rider's zone explicitly: a ViewModel takes `riderZone`, and `testGraph` binds Berlin through `appModules(riderZone = …)`. Never read `TimeZone.currentSystemDefault()` in a test.
- A test that runs view models on `testGraph` ends with `releaseMainDispatcher()` (`jvmTest/.../testing/MainDispatcher.kt`), not `Dispatchers.resetMain()`. A Room query that finishes on an IO thread after the test still resumes its cancelled caller through Main; with Main reset to "missing" that throws, and the next test fails with `UncaughtExceptionsBeforeTest`.
- Forms are tested in three layers: the mapper's check and edits in `commonTest` (`ProfileFormMapperTest`), the ViewModel on the real data layer (`ProfileViewModelTest`: fill, save, offline save and retry; `NetworkMode.OFFLINE` proves that an unchanged save sends nothing), and the screen with `performTextInput` (filtered input never reaches an intent).
- Saves without a button (`SpotsViewModelTest`): quick taps end at the last value in the cache, an offline write reverts and posts its error, a removal is undone with the same values. Turbine collects state unconfined, so it sees every intermediate state; wait for the state you mean (`awaitState { … }`), not the next item.
- To act while a request is in flight, bind `HeldRequests.engine` (`jvmTest/.../testing/HeldRequests.kt`) in `testGraph(clock, overrides)`: it passes every request on to the fake server, counts the ones it matches, and holds the next one between `hold()`/`awaitHeld()` and `release()`. `SpotsViewModelTest` uses it to prove that taps during a save cost one more request, not one each.
- `testGraph` binds a `RecordingShareLauncher` for the platform's share sheet and a `RecordingAppLocale` for its locale; `AppNavigationTest` reads what the Crew tab shared and which languages the app asked for.
- Crew tests (`CrewViewModelTest`): `steadyBreeze` keeps `Seed.default`'s member activity, which acts only when the clock moves. A test of the last-seat race swaps in a server whose `lastSeatRace` is 1.0, so the rider's try always loses. Room may fold a pending seat into the server's answer, so wait for the end state, not for the pending one. To see members act, move the clock and let a ViewModel with a short `poll` fetch again.
- `OnboardingNavigationTest` walks a new rider from Welcome through the profile and the spot picker to Today on the real data layer; `checkTexts()` (`jvmTest/.../testing/TextFit.kt`) finds text cut off at 200% font on a phone-sized window.
- Navigation rules are tested without UI: `Navigator` works on a plain `MutableList<NavKey>` (`NavigatorTest`). `DestinationSavedStateTest` (`jvmTest`: saved state on Android is a `Bundle`, which host tests lack) checks that every subclass of the sealed `Destination` is registered in `navigationConfiguration` and that a back stack survives saving and restoring.
- A UI test with real ViewModels and navigation (`AppNavigationTest`) wraps the content in `KoinIsolatedContext(testGraph(clock))` and a `LocalViewModelStoreOwner`, and sets `Dispatchers.setMain(Dispatchers.Unconfined)`: the desktop test runtime has no main dispatcher, and lifecycle checks that entries move on the main thread. Wait for screens with `waitUntil(timeoutMillis = …)`, because Room and HTTP run on real threads.
- Reducers are pure: give them a state and a result and compare the next state. Test the rules in the state mapper, and in the reducer only what the reducer itself records (Today: the refresh status and the inputs).
- `commonTest` has coroutines-test and Turbine, so the MVI base is tested on every runtime: `MviViewModelTest` checks that `bind()` runs only while the state is collected and stops after `STOP_AFTER`, and that effects are delivered once.
- `:shared:app` has a JVM target for tests only. A ViewModel test builds the app's own Koin modules for `Environment.Fake` with a `ControllableClock`, and overrides `FakePursuitOfWindServer` with a `Seed` whose `ForecastProfile` makes verdicts known (steady wind, no noise). No fake repositories.
- Create ViewModels through a `ViewModelStore` (`ViewModelProvider.create(store, viewModelFactory { initializer { … } })`) and `store.clear()` in `@AfterTest` before `koin.close()`. Otherwise a ViewModel from the previous test keeps reading a database that is already closed, and `runTest` reports that error in the next test.
- Room and DataStore run on real IO threads here, so the cache settles after a refresh ends: wait for the state you need (`awaitState { … }` over `awaitItem()`) instead of assuming the order of emissions.
- ViewModels tick and poll at their own constants (`TICK`, `POLL`), in tests too: repeated readings of a stopped `ControllableClock` are dropped, so ticking costs nothing in a test that does not move the clock, and a test that does sees the next tick.
- Screen tests render the stateless screen with `TodayPreviewData` states: assert merged card text (`hasText`), test tags, intents sent by buttons, custom accessibility actions, and the Russian strings (`Locale.setDefault`, restored after the test). The 200% font check runs at phone size (`runSkikoComposeUiTest(size, density)`) and fails on text that is clipped or pushed off screen.

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
./gradlew :shared:app:jvmTest                   # screen mappers, ViewModels on the real data layer, Compose UI tests
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
