# API changelog

The contract is [`openapi.yaml`](openapi.yaml). Within `/v1` only optional fields, new endpoints and new crew event types are added; anything else, including a new enum value, is a breaking change and needs `/v2`.

| Date | Change | Breaking |
| --- | --- | --- |
| 2026-10-19 | First version of `/v1`: anonymous auth and token refresh, rider profile, spot catalog and followed spots, hourly forecast per weather model, crews, rides and seats (with `version` for reconciliation), crew feed with `hasMore` and `410 FEED_RESET`, push token. | — |
| 2026-10-22 | Document `422 VALIDATION_FAILED` on every operation that takes a body, an `Idempotency-Key` or constrained query parameters. State the cross-field rules of `RiderProfile` (`minWindMps` < `maxWindMps`) and `CreateRideRequest` (catalog spot, window order, `departAt`), require a non-space character in `displayName`, crew `name` and `meetingPoint`, and describe invite codes as case-insensitive. | No — `/v1` has no server or released client yet |
| 2026-10-27 | Remove `Spot.dataQuality` and the `DataQuality` schema: riders will add spots themselves, so there is no checked or unchecked catalog any more. | No — `/v1` has no server or released client yet |
