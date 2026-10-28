---
name: wind-domain-rules
description: Source of truth for Pursuit Of Wind's decision engine — units, wind sectors, kite sizing, hour verdicts, session windows, confidence and day decisions. Use whenever writing or changing code in shared/windengine, writing engine tests, or writing UI copy that explains a GO / NO-GO verdict.
---

# Wind domain rules

The decision engine (`:shared:windengine`, package `io.github.esseckers.pursuitofwind.engine`) turns forecast hours, a rider profile and spot configs into verdicts, windows and a day decision. It is pure Kotlin, compiled for Android, iOS and JVM (the future server reuses it).

## Non-negotiable engineering rules

- Pure functions only. Every entry point takes `now: Instant` as a parameter. Never call `Clock.System` inside the engine.
- No dependencies beyond the Kotlin stdlib and kotlinx-datetime. No Android, iOS, Ktor, Koin or serialization types.
- Internal units are SI: wind in m/s, angles in degrees, time as `kotlin.time.Instant` (UTC). Convert only at the edges (UI formatting, test fixtures).
- `explicitApi()` is on. Keep the public surface small: types in `model/`, entry points in `DecisionEngine`.
- Every rule in this file has at least one test vector in [reference.md](reference.md). When you change a rule, change the vector first, watch the test fail, then change the code.

## Types (summary)

| Type | Meaning |
| --- | --- |
| `WindSpeed(metersPerSecond)` | value class; `toKnots()`, `toKmh()`, `toBeaufort()`; factories `WindSpeed.knots(x)`, `WindSpeed.kmh(x)` |
| `Bearing(degrees)` | direction the wind blows **from**, normalized to [0, 360) |
| `Sector(from, to)` / `Sector.of(fromDeg, toDeg)` | clockwise arc, inclusive on both ends, may wrap past 0°; `from == to` is invalid |
| `SkillLevel` | BEGINNER, INTERMEDIATE, ADVANCED |
| `RiderProfile` | weightKg, level, quiverM2, optional `minWind` / `maxWind` (`WindSpeed`), `minSession` (2 h), `maxDrive` (3 h) as `Duration`, `ridingHours` (`RidingHours(fromHour, toHour)`, 9–21) |
| `SpotConfig` | `SpotId`, name, IANA `timeZone`, rideableSectors (non-empty), dangerSectors, `drive: Duration` |
| `ShoreSectors` | rideable and danger `Sector` of a spot on a shore, from the direction of its water (`sectorsFacing`) |
| `ForecastHour` | start `Instant`, `byModel: Map<ModelId, ModelWind(mean, gust, bearing)>`, precipMm, tempC, `ThunderRisk`, isDaylight |
| `HourVerdict` | start, verdict, reasons (sorted by severity), `recommendedKiteM2` or null, consensus `HourWind` (mean, gust, bearing, modelSpread) |
| `SessionWindow` | spotId, start, end, hours, marginalHours, leaveBy, usableStart, `usable: Duration`, confidence, recommendedKiteM2, `qualifies`; asked with `isReachable`, `mustLeaveNow(now)`, `hasRidingIn(hourStart)` |
| `SpotForecast` | spot + its forecast hours; input of `decideDays` |
| `DayDecision` | `Go(date, window, alternatives)` or `NoGo(date, primaryReason, details: List<SpotNoGo>, nextGoDay)` |

Entry points: `DecisionEngine.evaluateHour`, `DecisionEngine.findWindows`, `DecisionEngine.decideDays`, `DecisionEngine.bestFirst`, `DecisionEngine.sectorsFacing`, plus `localHourLabel(start, zone)` for chart labels.

## Rules

### 1. Model consensus per hour

- Mean wind = arithmetic mean of model means. Gust = arithmetic mean of model gusts.
- Direction = **vector mean** of model bearings (sum of unit vectors, `atan2`). If the resultant length is below 1e-6, fall back to the highest-priority model present: `ecmwf`, then `icon`, then `gfs`, then the model id that sorts first.
- Model spread for the hour = max − min of model mean wind, in knots.

### 2. Wind range by level

| Level | Min (kn) | Max (kn) | Max gust spread (kn) |
| --- | ---: | ---: | ---: |
| BEGINNER | 12 | 22 | 8 |
| INTERMEDIATE | 12 | 28 | 10 |
| ADVANCED | 10 | 35 | 14 |

Personal `minWind` / `maxWind`, when set, replace the level defaults. Gust spread tolerance always comes from the level. Limits are inclusive (compared with a 1e-9 kn tolerance).

### 3. Hour rules (each produces GO, MARGINAL or NO_GO with a reason)

| Rule | Condition | Result |
| --- | --- | --- |
| Thunder | thunderRisk == HIGH | NO_GO THUNDERSTORM |
| Danger direction | bearing inside any dangerSector | NO_GO OFFSHORE_DANGER |
| Darkness | hour starts before sunrise or ends later than sunset − 30 min (`isDaylight == false`) | NO_GO DARK |
| Riding hours | hour does not start within `ridingHours` in the spot's local time (from ≤ start < to) | NO_GO OUTSIDE_RIDING_HOURS |
| Too strong | mean > max | NO_GO TOO_STRONG |
| Gusty | spread − tolerance in (0, 3] kn | MARGINAL GUSTY |
| Gusty | spread − tolerance > 3 kn | NO_GO GUSTY |
| Direction | bearing outside all rideableSectors (and not in danger) | NO_GO DIRECTION_UNRIDEABLE |
| Too light | mean < min | NO_GO TOO_LIGHT |
| Rain | precipMm > 2.0 | MARGINAL RAIN |
| Cold | tempC < 5.0 | MARGINAL COLD |
| Kite sizing | see section 4 | GO, MARGINAL or NO_GO |

Hour verdict = worst result (NO_GO > MARGINAL > GO). `reasons` = every non-GO reason, sorted by severity.

**Severity order (most severe first):** THUNDERSTORM, OFFSHORE_DANGER, DARK, OUTSIDE_RIDING_HOURS, TOO_STRONG, OVERPOWERED_FOR_QUIVER, GUSTY, DIRECTION_UNRIDEABLE, TOO_LIGHT, UNDERPOWERED_FOR_QUIVER, RAIN, COLD. Decision-level reasons TOO_FAR and WINDOW_TOO_SHORT never appear on an hour.

### 4. Kite sizing (heuristic, not safety advice)

```text
effectiveKn = mean + 0.3 × (gust − mean)
idealM2     = weightKg / effectiveKn × 2.2
candidates  = quiver sizes within [0.8 × idealM2, 1.15 × idealM2]
```

- Candidates exist → pick the size closest to idealM2; on a tie (|Δ| equal within 1e-9) pick the smaller kite. Result GO.
- No candidates and idealM2 < smallest kite → NO_GO OVERPOWERED_FOR_QUIVER, kite = null.
- No candidates and idealM2 > largest kite → NO_GO UNDERPOWERED_FOR_QUIVER, kite = null.
- No candidates and idealM2 falls in a gap between sizes → pick the largest kite ≤ idealM2, result MARGINAL UNDERPOWERED_FOR_QUIVER.
- Empty quiver → sizing rule is skipped (kite = null, no reason).

### 5. Windows

- Scan hours in time order per spot. A window is a maximal run of GO hours in which **isolated** MARGINAL hours (GO on both sides) are bridged. A MARGINAL hour at either edge, two MARGINAL hours in a row, or a missing forecast hour ends the run.
- `leaveBy = start − drive − 30 min` (30 min to rig).
- If `now > leaveBy`: `usableStart = now + drive + 30 min`, else `usableStart = start`. `usable = end − usableStart`, floored at 0.
- A window qualifies if `usable ≥ minSession`; otherwise it is reported with `qualifies = false` (WINDOW_TOO_SHORT).
- The window kite is the most frequent hour kite; tie → smaller.
- Spots with `drive > maxDrive` produce no windows and report TOO_FAR.

### 6. Confidence and ranking

- Window confidence from the **median** of hourly model spreads inside the window (even count → mean of the two middle values): ≤ 3 kn HIGH, ≤ 6 kn MEDIUM, otherwise LOW. Hours with one model have no spread; no spread at all → UNKNOWN.
- Ranking (`DecisionEngine.bestFirst`): longer `usable` first; tie → higher confidence (HIGH, MEDIUM, UNKNOWN, LOW); tie → shorter drive; tie → earlier start; tie → spot id ascending.

### 7. Day decision

- Days are calendar dates in the **spot's time zone**. Hours are assigned to a day by their start instant converted to that zone. Dates before today (spot zone) are skipped.
- `Go`: best qualifying window across favorite spots for that day, with recommended kite (most frequent hour recommendation in the window; tie → smaller), leaveBy, confidence, and every other qualifying window of the day as alternatives, best first.
- `NoGo.primaryReason`: if all favorite spots are TOO_FAR → TOO_FAR. If any GO hour existed that day but no window qualified → WINDOW_TOO_SHORT. If an hour's only reason is OUTSIDE_RIDING_HOURS → OUTSIDE_RIDING_HOURS. Otherwise the most frequent reason across daylight hours within the riding hours (hours without DARK or OUTSIDE_RIDING_HOURS) of reachable spots, counting every reason of an hour; tie → more severe; no daylight hour within the riding hours → the same over all daylight hours, not counting OUTSIDE_RIDING_HOURS; no daylight hour → DARK.
- `NoGo.details`: one `SpotNoGo` per spot, same rule per spot.
- `nextGoDay`: earliest later date within the forecast horizon that has a `Go`.

### 8. Sectors from the water

- A rider adds a spot by saying where its open water lies, as a direction from the beach (`water`). `sectorsFacing(water)` gives its sectors; the server stores them with the spot.
- Rideable: wind from within 100° of `water` (onshore, side-onshore, side-shore and a little past). Danger: wind from within 60° of `water + 180` (offshore, side-offshore). The 20° between them on either side is neither, so it is DIRECTION_UNRIDEABLE.

## UI copy guidance (EN, short, plain)

| Reason | Copy |
| --- | --- |
| TOO_LIGHT | Not enough wind |
| TOO_STRONG | Too strong for your level |
| GUSTY | Too gusty |
| OFFSHORE_DANGER | Offshore wind — dangerous here |
| DIRECTION_UNRIDEABLE | Wrong direction for this spot |
| THUNDERSTORM | Thunderstorm risk |
| RAIN | Rain |
| COLD | Cold |
| DARK | After dark |
| UNDERPOWERED_FOR_QUIVER | Your kites are too small |
| OVERPOWERED_FOR_QUIVER | Your kites are too big |
| TOO_FAR | Beyond your max drive |
| WINDOW_TOO_SHORT | Window too short after the drive |
| OUTSIDE_RIDING_HOURS | Outside your riding hours (day: wind only outside your riding hours) |

Always show the disclaimer: the app supports a decision; it does not replace judging conditions on the spot and is not safety advice.

## Before you finish engine work

1. All vectors in [reference.md](reference.md) are encoded as tests and pass.
2. Property tests cover: bearing normalization, sector containment across 0°, sizing monotonicity (more wind never gives a bigger ideal size), windows never overlap.
3. DST tests for Europe/Berlin pass.
4. Kover coverage for `:shared:windengine` ≥ 90%.
5. `docs/domain-rules.md` matches this file.
