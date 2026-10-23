---
name: fake-server
description: How Pursuit Of Wind's in-app fake backend (an in-process Ktor engine) works and behaves like a live server — adding fake routes, seeds, forecast model runs, the controllable clock and network simulation. Use when touching FakePursuitOfWindServer, Seed, ForecastProfile, Environment or ControllableClock.
---

# Fake server

## Why the fake sits at the HTTP layer

The whole data layer — serialization, headers, token refresh, error mapping, retries — runs for real against `FakePursuitOfWindServer`. Switching to the real backend later means changing `Environment` (base URL + Ktor engine), nothing else. Do not add fake repositories that bypass HTTP.

## Building blocks (all in `:shared:data`, package `io.github.esseckers.pursuitofwind.data.fake`)

| Piece | Responsibility |
| --- | --- |
| `FakePursuitOfWindServer(clock, seed, dispatcher)` | public entry point: `engine` (`internal/FakeEngine`, keeps no request history), `BASE_URL`, `networkMode`; handles one request at a time under a `Mutex` |
| `NetworkMode` | `NORMAL`, `SLOW` (2 s on the injected dispatcher), `FLAKY` (30% `503` + `Retry-After: 1`, seeded), `OFFLINE` (throws `kotlinx.io.IOException`) |
| `ControllableClock` | a `kotlin.time.Clock` with `advanceBy(duration)` and `set(instant)`; tests give it to the fake and the app |
| `Seed`, `SeedSpot`, `SeedCrew`, `SeedMember`, `SeedRide`, `ForecastProfile` | deterministic initial state; `Seed.default(now)` has five spots, the Weekend Crew (`WIND-4KQ7`) and one ride |
| `internal/Router` | method + path → `ApiRoute` and path parameters |
| `internal/FakeApi` | one handler per `ApiRoute` (checked complete at startup), Bearer authentication, `Idempotency-Key` storage and replay |
| `internal/*Routes` | `AuthRoutes`, `ProfileRoutes`, `SpotRoutes`, `CrewRoutes`, `RideRoutes`: the operations |
| `internal/FakeState` | users, tokens, profiles, followed spots, crews with their feeds, rides, idempotency records |
| `internal/ForecastGenerator` | hourly forecasts for `ecmwf`, `icon`, `gfs` as a pure function of seed, spot, hour, model and model run; `SunTimes` sets `isDaylight` |
| `internal/Validation` | the spec's field limits and cross-field rules, failing with `422 VALIDATION_FAILED` |

Rules:

- Determinism: every random source takes a seed; ids are sequential (`usr_0001`, `evt_000001`). The same seed, clock and sequence of requests always produce the same responses (tests depend on it).
- Time comes only from the injected `Clock`; delays only from `delay` on the injected dispatcher. No `Clock.System`, no `Thread.sleep`.
- Errors are thrown as `ProblemException` from `problem(…)`, `notFound(…)`, `invalid(…)`, `check422(…)`; `FakeApi` turns them into `application/problem+json`. Any other exception from a handler becomes `500 INTERNAL`.
- Enforce only what `api/openapi.yaml` states (schema limits, rules in schema descriptions); a new rule goes into the spec first. Strings are stored as sent.
- `Idempotency-Key` applies to authenticated mutations only; the token endpoints ignore it.
- `Seed` validates itself (unique ids and invite codes, rides that fit their crew, seats and spot), so a broken seed fails when it is built.
- The fake validates the Bearer token and honors `Idempotency-Key` exactly as described in skill `api-contract-workflow`.
- Seeded users (Andrei, Mira, Jonas) cannot sign in; the signed-in user joins the Weekend Crew with its invite code.

## Adding or changing a fake route

1. Make sure the operation exists in the spec, the DTOs and `ApiRoute` (contract first).
2. Write the handler in the matching `*Routes` class: read path parameters with `call.pathParameter`, decode the body with `call.decode(Dto.serializer())`, validate, change `FakeState`, append feed events with `state.addEvent` when crew members should see the change, bump the ride `version` on every ride change, and return the DTO with `json(…)`.
3. Register it in the `handlers` map of `FakeApi` (startup fails if an `ApiRoute` has no handler).
4. Reach every success and error status of the operation in `Tour.kt`; `FakeServerContractTest` validates the exchanges against the spec. Test behavior beyond the schema in `commonTest` with `fakeDriver()`.

## Behaving like a live server

There is no demo mode (`docs/adr/0010-live-fake-backend-instead-of-demo-mode.md`). The app always talks to the fake as if it were the real server, so it has to feel alive:

- **Every forecast request is a new model run.** `SpotRoutes` counts runs per spot and stamps `generatedAt` with the request time. A run moves each hour's wind by up to `ForecastProfile.runDriftKn` and the direction by up to `runDriftDeg`, smoothly over six-hour stretches, mostly the same way for all three models. Hours under way move a quarter as much as hours three days out. `Seed.default` uses 4 kn and 15°, so a pull to refresh always shows a slightly different forecast and a verdict on the edge can flip, as with real model updates.
- Profiles in tests that need fixed numbers leave `runDriftKn` and `runDriftDeg` at zero (`steadyBreeze()` in the app's tests does); then every run is identical.
- Crew members acting on their own (offering rides, taking seats) arrives with the Crew screen (the third pull request of Issue #9).

## Environments

`sealed interface Environment { data object Fake; data class Remote(val baseUrl: String) }` in `:shared:data`, chosen in `androidApp` / `iosApp` and passed to `dataModule(environment, clock)`. Each environment has its own `storageName`, so its database, DataStore files and secure store are separate. Tests pass a `ControllableClock` as `clock`.

## Sample data

`Seed.default(now)`: five fictional spots on the German coast and inland, among them `Nordstrand Bay` (rideable 180–300°, danger 60–150°), `Salt Lagoon` and `Lake Weiss`, and Andrei's "Weekend Crew" (Andrei, Mira, Jonas; invite code `WIND-4KQ7`) with one ride two days after `now`. App previews and tests use the sample rider "Alex", 80 kg, INTERMEDIATE, kites 7 / 9 / 12 m².
