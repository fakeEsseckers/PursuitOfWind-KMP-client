# 0010. A live fake backend instead of a demo mode

- Status: Accepted
- Date: 2026-10-23

## Context

The plan for the client phase had a demo mode (Issue #10): six scripted scenarios on the fake backend, a controllable clock shared by the fake and the app, a database and settings of its own, a control panel for time travel and network trouble, and a `pursuitofwind://demo/{scenario}` deep link. Its job was to show the app's decisions without waiting for real wind.

Two things changed. The owner will show the product with onboarding flows and spotlight hints on the real server later, so a second, scripted way into the app would be thrown away. And the fake backend itself was too still: forecasts were a pure function of the seed and the hour, so pulling to refresh brought exactly the same numbers back, and the app never had to deal with data that changes under it.

## Decision

- **No demo mode.** No scenarios, demo environment, demo database, control panel, DEMO badge or demo deep link. `Environment` stays `Fake` or `Remote`.
- **The fake backend behaves like a live server.** Every forecast request is a new model run, stamped with the request time (`generatedAt`). A run moves each hour's mean wind by up to `ForecastProfile.runDriftKn` and the direction by up to `runDriftDeg`. The change is smooth over six-hour stretches and mostly shared by the three models, as when a weather pattern shifts. Hours under way move a quarter as much as hours three days out. The sample spots use 4 kn and 15°, so a pull to refresh always shows a slightly different forecast, and a verdict on the edge can flip.
- **Still deterministic.** A run's change is seeded by the spot and the run number. The same seed, clock and sequence of requests give the same answers, so tests stay exact, and profiles with zero drift give identical runs. This replaces the rule in [ADR 0005](0005-contract-first-api-with-in-app-fake-backend.md) that forecasts are a pure function of seed, spot, hour and model.
- **Crew activity next.** Crew members acting on their own (offering rides, taking seats, which also produces a `409 SEATS_FULL` race) arrives with the Crew screen (the third pull request of Issue #9), where it can be seen and tested.
- `ControllableClock` and `NetworkMode` stay: tests use them.

## Alternatives considered

- **Keep the demo mode as planned** — a guided tour that always works, but it is a second environment with its own storage, clock and UI to maintain, and the owner plans onboarding and spotlights on the real server instead.
- **Change forecasts with time only** (a new run every hour, as real providers do) — truer to real providers, but pulling to refresh within the hour would still show the same numbers, which is the behavior the owner wants gone.
- **Random noise per request** — simple, but hours would jump independently, the chart would look jagged, and tests would lose exact answers.

## Consequences

- Positive: one environment to build and test; every screen meets changing data, refresh states and "updated just now" in everyday use.
- Positive: the fake's behavior is a property of its seed, so tests choose between a steady and a live forecast.
- Negative: no scripted showcase of edge cases (gusty trap, offshore trap, models disagreeing). The engine's tests cover these rules, and previews show the screens' states.
- Negative: numbers on screen change between refreshes, so screenshots and manual checks cannot rely on exact values.
- Negative: a background check that runs every hour gets a new run each time, so a GO window's start can move by an hour. Notifications (ADR 0011) must recognize the same window by spot, date and overlap, not by its exact start.
- ADRs 0004–0009 mention demo mode as part of the plan at the time. They are records and stay as written; only 0005 links here, because its forecast rule changed.
- Revisit when: the real server exists; then the fake stays for tests and offline development only.
