---
name: fake-server-and-demo
description: How Pursuit Of Wind's in-app fake backend (an in-process Ktor engine) and demo mode work — adding fake routes, seeds, demo scenarios, the controllable clock and network simulation. Use when touching FakePursuitOfWindServer, Environment, DemoScenario, ControllableClock or the demo control panel.
---

# Fake server and demo mode

## Why the fake sits at the HTTP layer

The whole data layer — serialization, headers, token refresh, error mapping, retries — runs for real against `FakePursuitOfWindServer`. Switching to the real backend later means changing `Environment` (base URL + Ktor engine), nothing else. Do not add fake repositories that bypass HTTP.

## Building blocks (all in `:shared:data`, package `io.github.esseckers.pursuitofwind.data.fake`)

| Piece | Responsibility |
| --- | --- |
| `FakePursuitOfWindServer(clock, seed, dispatcher)` | public entry point: `engine` (`internal/FakeEngine`, keeps no request history), `BASE_URL`, `networkMode`; handles one request at a time under a `Mutex` |
| `NetworkMode` | `NORMAL`, `SLOW` (2 s on the injected dispatcher), `FLAKY` (30% `503` + `Retry-After: 1`, seeded), `OFFLINE` (throws `kotlinx.io.IOException`) |
| `ControllableClock` | a `kotlin.time.Clock` with `advanceBy(duration)` and `set(instant)`; used by the fake **and** the engine in demo mode |
| `Seed`, `SeedSpot`, `SeedCrew`, `SeedMember`, `SeedRide`, `ForecastProfile` | deterministic initial state; `Seed.default(now)` has five spots, the Weekend Crew (`WIND-4KQ7`) and one ride |
| `internal/Router` | method + path → `ApiRoute` and path parameters |
| `internal/FakeApi` | one handler per `ApiRoute` (checked complete at startup), Bearer authentication, `Idempotency-Key` storage and replay |
| `internal/*Routes` | `AuthRoutes`, `ProfileRoutes`, `SpotRoutes`, `CrewRoutes`, `RideRoutes`: the operations |
| `internal/FakeState` | users, tokens, profiles, followed spots, crews with their feeds, rides, idempotency records |
| `internal/ForecastGenerator` | hourly forecasts for `ecmwf`, `icon`, `gfs` as a pure function of seed, spot, hour and model; `SunTimes` sets `isDaylight` |
| `internal/Validation` | the spec's field limits and cross-field rules, failing with `422 VALIDATION_FAILED` |

Rules:

- Determinism: every random source takes a seed; ids are sequential (`usr_0001`, `evt_000001`). The same seed and clock always produce the same responses (screenshot tests depend on it).
- Time comes only from the injected `Clock`; delays only from `delay` on the injected dispatcher. No `Clock.System`, no `Thread.sleep`.
- Errors are thrown as `ProblemException` from `problem(…)`, `notFound(…)`, `invalid(…)`, `check422(…)`; `FakeApi` turns them into `application/problem+json`. Any other exception from a handler becomes `500 INTERNAL`.
- Enforce only what `api/openapi.yaml` states (schema limits, rules in schema descriptions); a new rule goes into the spec first. Strings are stored as sent.
- `Idempotency-Key` applies to authenticated mutations only; the token endpoints ignore it.
- `Seed` validates itself (unique ids and invite codes, rides that fit their crew, seats and spot), so a broken scenario fails when it is built.
- The fake validates the Bearer token and honors `Idempotency-Key` exactly as described in skill `api-contract-workflow`.
- Seeded users (Andrei, Mira, Jonas) cannot sign in; the signed-in user joins the Weekend Crew with its invite code. Demo scenarios will pre-join the demo user.

## Adding or changing a fake route

1. Make sure the operation exists in the spec, the DTOs and `ApiRoute` (contract first).
2. Write the handler in the matching `*Routes` class: read path parameters with `call.pathParameter`, decode the body with `call.decode(Dto.serializer())`, validate, change `FakeState`, append feed events with `state.addEvent` when crew members should see the change, bump the ride `version` on every ride change, and return the DTO with `json(…)`.
3. Register it in the `handlers` map of `FakeApi` (startup fails if an `ApiRoute` has no handler).
4. Reach every success and error status of the operation in `Tour.kt`; `FakeServerContractTest` validates the exchanges against the spec. Test behavior beyond the schema in `commonTest` with `fakeDriver()`.

## Environments

`sealed interface Environment { data object Fake; data class Remote(val baseUrl: String) }` in `:shared:data` (`Demo(scenario)` arrives with demo mode, Issue #10) — chosen in `androidApp` / `iosApp` and passed to `dataModule(environment, clock)`. Each environment has its own `storageName`, so its database, DataStore files and secure store are separate; demo mode passes a `ControllableClock` as `clock`.

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
