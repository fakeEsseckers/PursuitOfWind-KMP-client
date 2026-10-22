# API changelog

The contract is [`openapi.yaml`](openapi.yaml). Within `/v1` only optional fields, new endpoints and new crew event types are added; anything else, including a new enum value, is a breaking change and needs `/v2`.

| Date | Change | Breaking |
| --- | --- | --- |
| 2026-10-19 | First version of `/v1`: anonymous auth and token refresh, rider profile, spot catalog and followed spots, hourly forecast per weather model, crews, rides and seats (with `version` for reconciliation), crew feed with `hasMore` and `410 FEED_RESET`, push token. | — |
