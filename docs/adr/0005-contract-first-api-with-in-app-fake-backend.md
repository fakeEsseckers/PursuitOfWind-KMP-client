# 0005. Contract-first API with an in-app fake backend at the HTTP layer

- Status: Accepted; the rule that forecasts are a pure function of seed, spot, hour and model is superseded by [0010](0010-live-fake-backend-instead-of-demo-mode.md)
- Date: 2026-10-22

## Context

The client is built before the server exists. The app still has to work end to end (sign-in, profile, spots, forecasts, crews, rides, seats, the crew feed), and the data layer that the real server will need (serialization, auth headers, token refresh, idempotent retries, error mapping, offline handling) should be exercised from day one, not when the server arrives. The same behavior also drives demo mode and screenshot tests, so it has to be deterministic. The risk of any stand-in is drift: a fake that answers what the client expects instead of what the server will send hides bugs until integration.

## Decision

- `api/openapi.yaml` (OpenAPI 3.1) is written first and is the single source of truth. `:shared:contract` mirrors it as DTOs and the route table `ApiRoute`; contract tests require every schema and its DTO to have the same fields, required fields and types, every example to validate, and the route table to match the spec. Changes go spec → DTOs → fake → tests, and are logged in `api/CHANGELOG.md`.
- `FakePursuitOfWindServer` in `:shared:data` `commonMain` implements every operation as an in-process Ktor engine with in-memory state (its own small engine since Issue #6: Ktor's `MockEngine` keeps every request and response it served). The app's real `HttpClient` talks to it; switching to the real backend is a different engine and base URL. It runs on Android, iOS and the JVM, so the app, demo mode and common tests use the same fake.
- The fake is deterministic: ids, tokens and invite codes come from a seeded sequence, forecasts are a pure function of seed, spot, hour and model, `FLAKY` failures come from a seeded random, time comes from an injected `Clock` (`ControllableClock` in tests and demo mode), and delays run on an injected dispatcher. Requests are handled one at a time. `NetworkMode` simulates `SLOW`, `FLAKY` (30% `503` with `Retry-After`) and `OFFLINE` (`IOException`).
- The fake enforces only what the spec says: field limits from the schemas, and cross-field rules (for example `departAt` not after `windowStart`) written into the schema descriptions. A rule the fake needs goes into the spec first.
- The fake is checked against the spec on every build. A tour test calls every route with its success and error cases. A JVM test validates every recorded exchange with networknt json-schema-validator (`:testing:openapi`; swagger-parser only checks that the spec itself parses): path, query and header parameters, request and response bodies, documented statuses and required headers. It also requires every documented status of every operation and every crew event type to be reached, and every request the spec rejects to be refused with `422` (or `401` on an authenticated route, where authentication comes first). `:testing:openapi` is a test-only module: `checkModuleGraph` fails if production code depends on it.
- A bug in a handler answers `500 INTERNAL` with a problem body, as a real server would, instead of throwing inside the HTTP client.

## Alternatives considered

- Fake repositories behind interfaces — the simplest stand-in, but nothing between the repository and the wire runs until the server exists, and network failures can only be imitated, not produced.
- A local mock server (MockWebServer, WireMock, an embedded Ktor server) — real sockets, but JVM-only: it runs neither in the iOS app nor in common tests, and demo mode would need a process.
- Static JSON fixtures per endpoint — easy to write, but stateless: seats do not fill up, the feed does not move, idempotent retries cannot be tested.
- Generating DTOs and a client from the spec — removes hand-written DTOs, but generated Kotlin Multiplatform code handles the polymorphic crew feed and unknown event types poorly; the parity tests give the same guarantee for hand-written DTOs.

## Consequences

- Positive: the production data layer is what runs in tests and in demo mode; there is no second, fake path through the app.
- Positive: drift between the fake and the spec fails the build, with the exact operation, status and schema path in the message.
- Positive: retries, token expiry, slow and flaky networks are reproducible in virtual time.
- Negative: the fake is a second implementation of server behavior (seat counting, idempotency, feed paging) and has to be kept in step with the spec by hand.
- Negative: the fake ships in the app binary, because demo mode uses it. Its engine implements Ktor's engine interface, which is marked `InternalAPI`, so a Ktor upgrade can require small changes there.
- Negative: schema validation of exchanges runs on the JVM only; Android host and iOS runs of the same tests check statuses and decoded bodies, not schemas.
- Negative: the tour proves conformance only for the requests it sends; a rule the tour never triggers is checked by the behavior tests alone.
- Revisit when: the real server exists. Run the same tour against it as a smoke test, and decide whether the fake stays only for demo mode and tests.
