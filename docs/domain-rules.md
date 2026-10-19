# Decision engine rules

How `:shared:windengine` turns a forecast into GO / NO-GO. The engine is pure Kotlin for Android, iOS and the JVM: no clock, no I/O, no platform types. The current time is always a parameter (`now`). Every rule below has a test vector in [`.claude/skills/wind-domain-rules/reference.md`](../.claude/skills/wind-domain-rules/reference.md), encoded in `shared/windengine/src/commonTest`.

> The engine supports a decision. It does not replace judging conditions on the spot and is not safety advice. Kite sizing is a heuristic.

## Entry points

`DecisionEngine` in `io.github.esseckers.pursuitofwind.engine`:

| Function | Returns |
| --- | --- |
| `evaluateHour(hour, profile, spot)` | `HourVerdict`: GO / MARGINAL / NO_GO, reasons, recommended kite, consensus wind |
| `findWindows(hours, spot, profile, now)` | `SessionWindow`s of one spot, qualifying or not |
| `decideDays(forecasts, profile, now)` | one `DayDecision` (`Go` or `NoGo`) per local date from today on |

`localHourLabel(start, zone)` formats an hour slot for charts (see [Time](#time)).

## Units and types

- Wind is stored in m/s (`WindSpeed`); thresholds below are written in knots because riders think in knots. Conversion: 1 kn = 1852 / 3600 m/s.
- `Bearing` is the direction the wind blows **from**, normalized to [0°, 360°). `Sector(from, to)` is a clockwise arc, both ends included, and may wrap past north (`Sector.of(300.0, 60.0)`). Equal bounds are rejected.
- `RiderProfile`: weight, `SkillLevel`, quiver (m²), optional personal `minWind` / `maxWind`, `minSession` (default 2 h), `maxDrive` (default 3 h).
- `SpotConfig`: id, name, IANA time zone, rideable sectors (at least one), danger sectors, one-way `drive`.
- `ForecastHour`: start instant, wind per weather model (mean, gust, bearing), precipitation, temperature, thunderstorm risk, `isDaylight`. An hour lasts one hour. `isDaylight` is false when the hour starts before sunrise or ends later than 30 minutes before sunset; the data layer computes it.

## 1. Model consensus per hour

- Mean wind and gust: arithmetic means of the models' values.
- Direction: vector mean of the models' bearings (sum of unit vectors), so 350° and 10° average to 0°, not 180°. If the vectors cancel out (resultant shorter than 1e-6), the highest-priority model present wins: `ecmwf`, then `icon`, then `gfs`, then the model id that sorts first.
- Model spread: highest minus lowest model mean wind. No spread with a single model.

## 2. Wind range by level

| Level | Min (kn) | Max (kn) | Tolerated gust spread (kn) |
| --- | ---: | ---: | ---: |
| BEGINNER | 12 | 22 | 8 |
| INTERMEDIATE | 12 | 28 | 10 |
| ADVANCED | 10 | 35 | 14 |

Personal `minWind` / `maxWind` replace the level limits. The gust tolerance always comes from the level. Limits are inclusive.

## 3. Hour rules

| Rule | Condition | Result |
| --- | --- | --- |
| Thunder | thunderstorm risk HIGH | NO_GO THUNDERSTORM |
| Danger direction | bearing inside a danger sector | NO_GO OFFSHORE_DANGER |
| Darkness | `isDaylight` is false | NO_GO DARK |
| Too strong | mean above max | NO_GO TOO_STRONG |
| Gusty | gust spread over tolerance by up to 3 kn | MARGINAL GUSTY |
| Gusty | gust spread over tolerance by more than 3 kn | NO_GO GUSTY |
| Direction | bearing outside every rideable sector and not dangerous | NO_GO DIRECTION_UNRIDEABLE |
| Too light | mean below min | NO_GO TOO_LIGHT |
| Rain | more than 2.0 mm | MARGINAL RAIN |
| Cold | below 5.0 °C | MARGINAL COLD |
| Kite sizing | section 4 | GO, MARGINAL or NO_GO |

The hour gets the worst result (NO_GO > MARGINAL > GO). `reasons` lists every non-GO reason, most severe first:

THUNDERSTORM, OFFSHORE_DANGER, DARK, TOO_STRONG, OVERPOWERED_FOR_QUIVER, GUSTY, DIRECTION_UNRIDEABLE, TOO_LIGHT, UNDERPOWERED_FOR_QUIVER, RAIN, COLD.

TOO_FAR and WINDOW_TOO_SHORT only appear on decisions, never on an hour.

## 4. Kite sizing

```text
effectiveKn = mean + 0.3 × (gust − mean)
idealM2     = weightKg / effectiveKn × 2.2
fitting     = quiver sizes within [0.8 × idealM2, 1.15 × idealM2]
```

- Something fits: the size closest to `idealM2`, the smaller one on a tie. GO.
- Nothing fits and `idealM2` is below the smallest kite: no kite, NO_GO OVERPOWERED_FOR_QUIVER.
- Nothing fits and `idealM2` is above the largest kite: no kite, NO_GO UNDERPOWERED_FOR_QUIVER.
- Nothing fits and `idealM2` falls into a gap: the largest kite below it, MARGINAL UNDERPOWERED_FOR_QUIVER.
- Empty quiver: sizing is skipped.

The kite is computed for NO_GO hours too; the UI decides whether to show it.

## 5. Session windows

- Per spot, hours in time order. A window is a maximal run of GO hours in which **isolated** MARGINAL hours (GO on both sides) are bridged. A MARGINAL hour at either edge, two MARGINAL hours in a row, or a missing forecast hour ends the run. A window always starts and ends on GO.
- `leaveBy = start − drive − 30 min` (30 minutes to rig).
- If `now` is past `leaveBy`, riding starts at `now + drive + 30 min`; otherwise at `start`. `usable = end − usableStart`, never negative.
- A window qualifies if `usable ≥ minSession`; a shorter one is reported with `qualifies = false` (WINDOW_TOO_SHORT).
- The window's kite is the most frequent hour kite, the smaller one on a tie.
- A spot with `drive > maxDrive` has no windows and is reported as TOO_FAR.

## 6. Confidence and score

- Confidence from the median of the hourly model spreads in the window (even count: mean of the two middle values): up to 3 kn HIGH, up to 6 kn MEDIUM, above LOW. Hours with a single model have no spread; no spread at all is UNKNOWN.
- `score = usableHours × quality × confidenceFactor`, where `quality = 1.0 − 0.15 × marginalHours`, at least 0.1, and the factor is HIGH 1.0, MEDIUM 0.85, LOW 0.6, UNKNOWN 0.8.
- Ranking: higher score (scores equal to six decimals tie); then shorter drive; then earlier start; then spot id.

## 7. Day decision

- A day is a calendar date in the spot's time zone; an hour belongs to the date of its start there, and windows are built per day, so they never cross local midnight. Dates before today (in the spot's zone) are skipped. A date covers the favorite spots that have forecast hours on it; dates only a too-far spot covers are left out unless no spot is reachable at all.
- `Go`: the best qualifying window of the day across reachable spots, with up to two alternative windows. Kite, `leaveBy` and confidence come from the window.
- `NoGo.primaryReason`:
  1. every spot is beyond the maximum drive: TOO_FAR;
  2. some hour was GO but no window qualified: WINDOW_TOO_SHORT;
  3. otherwise the most frequent reason across the daylight hours of reachable spots, the more severe one on a tie; DARK if there is no daylight hour.
- `NoGo.details`: the same rule applied to each spot on its own (TOO_FAR for spots beyond the drive).
- `NoGo.nextGoDay`: the earliest later date in the forecast with a `Go`.

## Time

Forecast hours are instants in UTC. Day boundaries and labels use the spot's IANA time zone, so daylight saving changes are handled by the time zone database:

- On the spring-forward day (Europe/Berlin, 2026-03-29) a local day has 23 hourly slots and no slot is labelled 02:00.
- On the fall-back day (2026-10-25) it has 25 slots; the repeated hour appears twice, and `localHourLabel` adds the UTC offset to both (`02:00 UTC+2`, `02:00 UTC+1`) so labels stay distinct.

## UI copy

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
