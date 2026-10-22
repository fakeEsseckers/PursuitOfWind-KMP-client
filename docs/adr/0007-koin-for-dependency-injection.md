# 0007. Koin for dependency injection

- Status: Accepted
- Date: 2026-10-22

## Context

The data layer is wired differently per environment (fake backend, real server, demo) and per platform (Android Keystore and OkHttp, iOS Keychain and Darwin), and the UI (Issue #7) will add ViewModels on top. The graph has to be assembled from common code, chosen at the entry point (`androidApp`, `iosApp`), replaceable in tests, and checked in CI so a missing binding fails the build rather than the app.

## Decision

- Koin 4.2.2 (`koin-core`; `koin-compose-viewmodel` arrives with the screens). `dataModule(environment, clock)` in `:shared:data` returns the data layer for one environment; a platform module (`expect fun platformDataModule`) binds the secure store, database and settings files. On Android the graph also needs the application `Context`.
- Repositories are created with constructor references (`singleOf`), which Koin can check. Classes with hand-made arguments (HTTP clients, the auth session) use lambdas.
- A JVM test runs Koin's `verify()` on the module for the fake and the remote environment, and a second test starts the real graph for the fake environment and signs in, joins a crew and reads it back through it.
- Tests of repositories do not use Koin: they build the classes directly, so each test controls its clock, dispatcher and server.

## Alternatives considered

- Compile-time DI (kotlin-inject, Metro, Dagger/Hilt on Android) — the graph is checked by the compiler, but kotlin-inject and Metro still change quickly, and Hilt does not run on iOS; for about twenty bindings the build-time cost and the extra concepts are not worth it.
- Manual wiring (a hand-written container) — no library, but per-environment and per-platform variants, scoping and ViewModel integration all become code to maintain.
- Koin annotations — compile-time checks on top of Koin, at the price of another KSP processor; `verify()` gives most of the safety for this size.

## Consequences

- Positive: one place per environment decides what runs; demo mode can swap the clock and storage without touching repositories.
- Positive: the graph is checked in CI, and the fake environment is exercised end to end through it.
- Negative: Koin resolves at runtime; `verify()` cannot see bindings created inside lambdas, so those are listed as hand-built types in the test.
- Negative: on Android the entry point must remember to register the `Context`.
- Revisit when: the graph grows past a few dozen bindings or feature modules appear (ADR 0002), or a compile-time option is stable on all targets.
