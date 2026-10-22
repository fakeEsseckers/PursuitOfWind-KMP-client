---
name: api-contract-workflow
description: Contract-first workflow for the Pursuit Of Wind API — editing api/openapi.yaml, the DTOs in shared/contract, the fake server and contract tests, in that order. Use whenever an endpoint, field, status code or error code is added, removed or changed, or when the client and fake server disagree.
---

# API contract workflow

`api/openapi.yaml` (OpenAPI 3.1) is the single source of truth. The client, the in-app fake server and the future Ktor server all conform to it.

## Order of changes (never skip a step)

1. **Spec.** Edit `api/openapi.yaml`. Add or update an `example` for every new schema and response.
2. **DTOs.** Mirror the change in `:shared:contract` (`io.github.esseckers.pursuitofwind.contract`). DTO names end with `Dto`; enums are serialized in `SCREAMING_SNAKE_CASE` exactly as in the spec.
3. **Fake server.** Update the handler in `FakePursuitOfWindServer` (see skill `fake-server-and-demo`).
4. **Contract tests.** Reach every new status in the tour (`Tour.kt` in `:shared:data` `commonTest`). `FakeServerContractTest` (`jvmTest`) replays every exchange against the spec and fails on an unreached documented status, a schema violation, a missing required header, or a request the spec rejects that the fake accepted. Behavior beyond the schema (seat math, paging, idempotency) gets its own `commonTest`.
5. **Mappers and repositories.** Update DTO↔domain mappers in `:shared:data`. Domain types never leak DTOs to the UI.
6. **Changelog.** Add a line to `api/CHANGELOG.md` (date, change, breaking or not).

## Conventions

- Base path `/v1`. Breaking changes require `/v2`; within `/v1` only add optional fields, new operations and new crew event types. Enum values are closed: a new value is a breaking change. Unknown crew event types decode as `UnknownCrewEventDto` (`ContractJson`), so the feed keeps working.
- Routes live in `ApiRoute` (`:shared:contract`); the client, the fake server and the server use it instead of string paths.
- JSON, `camelCase` fields, ISO-8601 UTC timestamps (`2026-07-18T11:00:00Z`), wind speeds in m/s as numbers, bearings in degrees.
- Auth: `Authorization: Bearer <accessToken>`. `401` (with `WWW-Authenticate: Bearer`) means refresh and retry once. Refresh tokens rotate; a `401` on refresh means "sign in anonymously again with the same installation id", which returns the same user. Store the installation id like a credential.
- Authenticated mutations (`POST`, `PUT`, `DELETE`) accept `Idempotency-Key` (UUID); the two token endpoints are safe to repeat without one. The same key with the same body returns the stored response with `Idempotent-Replayed: true`; with a different body `422 IDEMPOTENCY_KEY_REUSED`. Keys are scoped to user + method + path, kept 24 h, and requests with the same key run one at a time.
- Errors: `application/problem+json`:

```json
{
  "type": "/problems/seats-full",
  "title": "No seats left",
  "status": 409,
  "code": "SEATS_FULL",
  "detail": "Ride 7f3c… has no free seats."
}
```

Known codes (`ProblemCode`): `UNAUTHORIZED`, `NOT_FOUND`, `VALIDATION_FAILED`, `NOT_A_MEMBER`, `NOT_THE_DRIVER`, `INVITE_INVALID`, `SEATS_FULL`, `ALREADY_JOINED`, `RIDE_CANCELLED`, `FEED_RESET`, `IDEMPOTENCY_KEY_REUSED`, `RATE_LIMITED`, `SERVICE_UNAVAILABLE`, `INTERNAL`.

- Lists that can grow use cursor pagination: `?cursor=&limit=` and `nextCursor` in the response.
- The feed uses `?since=<lastEventId>` and `hasMore`; event ids are opaque. `410 FEED_RESET` means: reload the crew's rides, then read the feed again without `since`.
- `Ride.version` grows with every change and seat events carry `rideVersion`: keep the copy with the highest version.

## Mapping errors on the client

All Ktor exceptions and problem bodies map to `AppError` in one function (`toAppError()`), with a unit test per code. `409 SEATS_FULL` and `409 RIDE_CANCELLED` become `AppError.Conflict(code)`: the UI rolls back the optimistic seat with a specific message and reloads the ride (`GET /v1/rides/{rideId}`). `409 ALREADY_JOINED` means the rider already has the seat: keep it and reload the ride.

## How the contract is checked

`OpenApiContractTest` in `:shared:contract` `jvmTest` (`./gradlew :shared:contract:jvmTest`, part of `./scripts/check.sh`):

- the spec parses with swagger-parser without errors or warnings;
- every path is under `/v1/`; every operation has an `operationId`, a tag, a summary, a 2xx and a `default` response; authenticated operations document `401`; authenticated mutations take `Idempotency-Key`; path parameters are declared;
- `ApiRoute` lists exactly the spec's operations (method, path, operationId, whether a token is needed);
- every schema `examples` entry validates against its schema (networknt, OpenAPI 3.1 dialect, format assertions on) and survives a round trip through its DTO with `ContractJson`; every response example validates and decodes into its DTO;
- every schema maps to a DTO with exactly the same fields, required fields and field types (enums: same values, sealed `CrewEventDto`: same variants);
- every 4xx and `default` response is `application/problem+json` with the `Problem` schema; error examples use a known `ProblemCode` and the status of their response; every `ProblemCode` is listed in the `Problem` schema;
- seat counts in examples add up (`seatsFree = seatsTotal − passengers`).

A new schema needs an entry in the test's `dtoBySchema` map and at least one example. A new operation needs an `ApiRoute` entry; update the fake server in the same change.

DTOs use `ContractJson` (`ignoreUnknownKeys`, `explicitNulls = false`, `encodeDefaults = true`, `type` as class discriminator, unknown events as `UnknownCrewEventDto`). Optional fields are nullable with `= null`; required fields have no default. Timestamps are `kotlin.time.Instant`. Serialize crew events through `CrewEventDto.serializer()` (as `CrewFeedDto` does), otherwise `type` is missing.

## Definition of done for a contract change

- [ ] `./gradlew :shared:contract:jvmTest` is green (spec, examples, DTO parity).
- [ ] DTO round-trip test (serialize → deserialize) for new types in `commonTest` if the type has special serialization.
- [ ] Contract test passes for the fake.
- [ ] `api/CHANGELOG.md` updated.
