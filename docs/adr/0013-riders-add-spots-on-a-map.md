# 0013. Riders add spots on a map

- Status: Accepted
- Date: 2026-10-27

## Context

The client phase planned a curated spot catalog: spots with wind sectors drawn by hand, and `dataQuality` to mark the ones nobody had checked (`UNVERIFIED`). We found no such catalog to start from. OpenStreetMap tags kitesurfing beaches (`sport=kitesurfing`) but has no tag for their wind directions; the spot catalogs of forecast services such as Windguru and Windy.app are not offered for reuse; Windy once asked its community for help with an open database of kite spots, and we found no dataset that came of it. The owner also wants a group of riders to try the app on their home spots, which needs their own spots, not five fictional ones on the German coast.

A spot's sectors decide its verdicts: the same wind is GO on one shore and dangerous offshore on the other. Most riders will not draw sectors, but they know where the water is.

## Decision

- **Riders add spots.** A rider drops a pin on a map, names the spot and points an arrow at the water. `POST /v1/spots` stores it with `waterBearingDeg` and `createdBy`; `PUT /v1/spots/{spotId}` changes it, for its creator only (`403 NOT_THE_CREATOR`).
- **Sectors come from the water** (`DecisionEngine.sectorsFacing`, rule 8 in `docs/domain-rules.md`): rideable within 100° of the water, dangerous within 60° of the land, neither in between. The server works them out, so the client and the future server cannot disagree.
- **One catalog for everyone.** A spot a rider adds is in every rider's catalog at once; the app puts the crew's spots first, then the nearest. There is no checked or unchecked spot any more: `dataQuality` and the "not checked" badges are gone.
- **Weather for any place.** The fake backend makes a forecast profile from a spot's place (`PlaceForecasts`); the real server will ask a weather service for the spot's coordinates.
- **The map**: MapLibre Compose with OpenFreeMap tiles, one map in common code for Android and iOS, no API key. The rider's coarse location, asked for only when they tap "my location", centres the map and suggests a drive time.

## Alternatives considered

- **A curated catalog with `VERIFIED` and `UNVERIFIED` spots** — there is no source to fill it from, and someone has to keep checking it.
- **Import OpenStreetMap's kitesurfing spots** — gives places, not sectors; the share-alike licence would also cover the sectors added to them.
- **Riders draw both sectors on a compass** — exact, but too hard for most riders; one arrow towards the water covers a shore, and a spot can be turned later.
- **Spots private to a rider or a crew** — no duplicates from strangers, but riders who meet at a spot could not share it, and a public catalog is what the owner wants.
- **Google Maps on Android and MapKit on iOS** — mature, but two map implementations behind an interface, an API key, and Google Play services, which some testers' phones may not have.

## Consequences

- Positive: any rider anywhere can use the app on their own spots on the first day; testers do not wait for a catalog.
- Positive: one rule, in the engine, turns a spot into sectors on every platform and on the server.
- Negative: sectors from one direction are rough for lagoons, spits and bays with water on several sides, and a wrong arrow gives wrong verdicts. The disclaimer stays; turning the arrow fixes a spot.
- Negative: a public catalog collects duplicates and junk. The map offers an existing spot when a pin lands within a kilometre of one; reporting and hiding spots waits for the real server.
- Negative: placing a spot needs to see the map. Screen readers hear the map and can pick its spots, but cannot drop a pin or point at the water.
- Negative: MapLibre Compose is before 1.0 and its iOS support is in beta, so its version is pinned and upgrades need its upgrade notes.
- Revisit when: the catalog grows past what the app can load at once (then the server searches and sorts by distance), or riders ask for spots with water on several sides.
