---
name: api-contract-workflow
description: Contract-first workflow for the Pursuit Of Wind API — editing api/openapi.yaml, the DTOs in shared/contract, the fake server and contract tests, in that order. Use whenever an endpoint, field, status code or error code is added, removed or changed, or when the client and fake server disagree.
---

# API contract workflow

`api/openapi.yaml` (OpenAPI 3.1) is the single source of truth. The client, the in-app fake server and the future Ktor server all conform to it.

## Order of changes (never skip a step)

1. **Spec.** Edit `api/openapi.yaml`. Add or update an `example` for every new schema and response.
2. **DTOs.** Mirror the change in `:shared:contract` (`io.github.esseckers.pursuitofwind.contract`). DTO names end with `Dto`; enums are serialized in `SCREAMING_SNAKE_CASE` exactly as in the spec.
3. **Fake server.** Update the route in `FakePursuitOfWindServer` (see skill `fake-server-and-demo`).
4. **Contract tests.** Add or update a test in `:shared:data` `jvmTest` that calls the fake and validates request and response against the spec.
5. **Mappers and repositories.** Update DTO↔domain mappers in `:shared:data`. Domain types never leak DTOs to the UI.
6. **Changelog.** Add a line to `api/CHANGELOG.md` (date, change, breaking or not).

## Conventions

- Base path `/v1`. Breaking changes require `/v2`; within `/v1` only add optional fields.
- JSON, `camelCase` fields, ISO-8601 UTC timestamps (`2026-07-18T11:00:00Z`), wind speeds in m/s as numbers, bearings in degrees.
- Auth: `Authorization: Bearer <accessToken>`. `401` means refresh and retry once.
- Mutations (`POST`, `PUT`, `DELETE` that change state) accept `Idempotency-Key` (UUID). The same key with the same body returns the stored response; with a different body returns `422 IDEMPOTENCY_KEY_REUSED`.
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

Known codes: `UNAUTHORIZED`, `NOT_FOUND`, `VALIDATION_FAILED`, `SEATS_FULL`, `ALREADY_JOINED`, `NOT_A_MEMBER`, `INVITE_INVALID`, `IDEMPOTENCY_KEY_REUSED`, `RATE_LIMITED`, `INTERNAL`.

- Lists that can grow use cursor pagination: `?cursor=&limit=` and `nextCursor` in the response. The feed uses `?since=<eventId>`.

## Mapping errors on the client

All Ktor exceptions and problem bodies map to `AppError` in one function (`toAppError()`), with a unit test per code. `409 SEATS_FULL` and `409 ALREADY_JOINED` become `AppError.Conflict(code)` so the UI can roll back optimistic updates with a specific message.

## Definition of done for a contract change

- [ ] Spec validates (run the OpenAPI linter task in `./scripts/check.sh`).
- [ ] DTO round-trip test (serialize → deserialize) for new types.
- [ ] Contract test passes for the fake.
- [ ] `api/CHANGELOG.md` updated.
- [ ] Server-phase notes in `docs/architecture.md` updated if behavior changed.
