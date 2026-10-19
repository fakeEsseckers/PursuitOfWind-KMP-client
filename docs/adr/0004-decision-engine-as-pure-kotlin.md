# 0004. Decision engine as pure Kotlin for three runtimes

- Status: Accepted
- Date: 2026-10-18

## Context

The product's value is the GO / NO-GO answer: which hours are rideable, which window is worth the drive, which kite to take. The same answer has to come out of the Android app, the iOS app and, later, a JVM server in a separate repository that sends notifications. If the rules live in a ViewModel, a repository or the server, the clients and the server drift apart, and the rules can only be tested through the UI or over the network. The rules also depend on time (leave-by, "today", daylight saving), which is where untestable code usually hides.

## Decision

`:shared:windengine` holds every decision rule as pure functions behind one entry point, `DecisionEngine` (`evaluateHour`, `findWindows`, `decideDays`). It targets Android, iOS and the JVM, depends only on the Kotlin standard library and kotlinx-datetime, never reads a clock (`now` is a parameter), stores wind in m/s, and has `explicitApi()` with the implementation in an `internal` package. Every rule in `docs/domain-rules.md` has a table-driven test vector; seeded Kotest property tests cover bearings, sectors, kite sizing and window structure; Kover fails the build below 90% line coverage.

## Alternatives considered

- Rules in `:shared:data` next to the repositories — fewer modules, but the server would have to take Ktor, Room and the fake backend along to reuse them.
- Rules on the server only, clients render results — one implementation, but no offline answers and no demo mode before the server exists.
- A rules DSL or configuration file — flexible, but harder to test and review than plain functions, and nobody needs to change rules at runtime yet.

## Consequences

- Positive: the same tests run on the JVM, the Android host and the iOS simulator, and the server will reuse the module unchanged.
- Positive: time-dependent behavior (leave-by, DST days) is tested with fixed instants instead of a mocked clock.
- Negative: data has to be mapped into engine types (`ForecastHour`, `SpotConfig`, `RiderProfile`) in `:shared:data`; that mapping is extra code.
- Negative: thresholds are constants in code, so tuning them means a release.
- Revisit when: thresholds need per-user or remote tuning, or a second sport needs different rules — then extract the numbers into a configuration object passed to `DecisionEngine`.
