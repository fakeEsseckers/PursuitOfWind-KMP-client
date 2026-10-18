---
name: fake-server-and-demo
description: How Pursuit Of Wind's in-app fake backend (Ktor MockEngine) and demo mode work — adding fake routes, seeds, demo scenarios, the controllable clock and network simulation. Use when touching FakePursuitOfWindServer, Environment, DemoScenario, ControllableClock or the demo control panel.
---

# Fake server and demo mode

## Why the fake sits at the HTTP layer

The whole data layer — serialization, headers, token refresh, error mapping, retries — runs for real against `FakePursuitOfWindServer`. Switching to the real backend later means changing `Environment` (base URL + Ktor engine), nothing else. Do not add fake repositories that bypass HTTP.

## Building blocks (all in `:shared:data`, package `io.github.esseckers.pursuitofwind.data.fake`)

| Piece | Responsibility |
| --- | --- |
| `FakePursuitOfWindServer` | Ktor `MockEngine` handler; tiny router on method + path template; returns spec-conformant JSON and `problem+json` errors |
| `FakeState` | in-memory state (users, profiles, spots, crews, rides, feed, idempotency records) guarded by a `Mutex` |
| `Seed` | deterministic initial state; created from a `DemoScenario` or the plain `FAKE` environment |
| `ForecastGenerator` | procedural forecasts for 3 models (`ecmwf`, `icon`, `gfs`) from scenario parameters with seeded noise |
| `NetworkSimulator` | `NORMAL`, `SLOW` (2 s), `FLAKY` (30% `503`, seeded), `OFFLINE` (throws `IOException`) |
| `ControllableClock` | implements the app's `Clock`; can `advanceBy(duration)` and `set(instant)`; used by the fake **and** the engine in demo mode |

Rules:

- Determinism: every random source takes a seed. The same scenario always renders the same screens (screenshot tests depend on it).
- Delays go through the injected clock/dispatcher, never `Thread.sleep` or wall-clock `delay` in tests.
- The fake validates the Bearer token and honors `Idempotency-Key` exactly as described in skill `api-contract-workflow`.
- Every route has a contract test (`jvmTest`) that validates its responses against `api/openapi.yaml`.

## Adding a fake route

1. Make sure the endpoint exists in the spec and DTOs (contract first).
2. Add the handler: parse path params, decode body into the DTO, mutate `FakeState` under the mutex, append feed events if relevant, return the DTO.
3. Return errors only through `problem(status, code, title)`.
4. Add the contract test and a repository test using the route.

## Environments

`sealed interface Environment { data object Fake; data class Remote(val baseUrl: String); data class Demo(val scenario: DemoScenarioId) }` — chosen in `androidApp` / `iosApp` and passed to `initKoin(environment)`.

Demo isolation: demo mode uses its own Room database name (`pursuitofwind-demo.db`) and DataStore file. Leaving demo deletes both. A persistent **DEMO** badge is shown in the top bar.

## Demo scenarios

Persona: rider "Alex", 80 kg, INTERMEDIATE, quiver 7 / 9 / 12 m². Fictional spots: `Nordstrand Bay` (drive 120 min, rideable 180–300°, danger 60–150°), `Salt Lagoon` (160 min, rideable 200–340°), `Lake Weiss` (45 min, rideable 220–320°). Crew "Weekend Crew": Alex, Andrei (driver), Mira, Jonas. Demo "now" starts on a Thursday 08:00 in Europe/Berlin.

| Id | Setup | Expected screen |
| --- | --- | --- |
| `saturday-on` | Sat 13–17 at Nordstrand Bay, 18–22 kn, bearing 240°, models agree | Today: GO, kite 9, leave 10:30, HIGH; Andrei's ride with 2 seats |
| `gusty-trap` | Sat mean 20 kn, gusts 34 kn | NO-GO "Too gusty" despite strong mean wind |
| `offshore-trap` | Nordstrand Bay bearing 100°, Salt Lagoon fine | Nordstrand NO-GO (offshore), Go at Salt Lagoon |
| `too-short` | 1.5 h window at Nordstrand Bay | NO-GO "Window too short after the drive" |
| `models-disagree` | ECMWF 20 kn, ICON 17 kn, GFS 11 kn (consensus 16 kn, spread 9 kn) | GO with a LOW confidence badge and "models disagree" hint; Lake Weiss with HIGH confidence ranks above it if both qualify |
| `car-fills-up` | scripted events: Mira joins at +2 min, Jonas at +4 min (demo time) | seats 3 → 1; Alex tries to join after the last seat is taken → optimistic join rolls back with "No seats left" |

Each scenario must have: a seed builder, a Today screenshot test, and a one-line description shown in the demo panel.

## Demo control panel (Settings → Demo)

- Scenario picker (restarts demo state).
- Time travel: +1 h, +6 h, next day 08:00 (drives `ControllableClock`).
- Inject crew event (pick from scripted events).
- Show window notification now (calls `LocalNotifier` with the current best window).
- Network mode: NORMAL / SLOW / FLAKY / OFFLINE.
- Reset demo.

Deep link `pursuitofwind://demo/{scenarioId}` opens the app straight into a scenario; document it in README.
