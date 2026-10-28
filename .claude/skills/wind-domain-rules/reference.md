# Engine test vectors

Every vector below must exist as a test in `:shared:windengine` (`commonTest`). Wind values are written in knots for readability; convert with `WindSpeed.knots(x)` in tests. Compare doubles with a tolerance of 1e-6 unless stated otherwise.

## A. Units and angles

| ID | Input | Expected |
| --- | --- | --- |
| A1 | `WindSpeed(10.0).toKnots()` | 19.43844 (± 1e-4) |
| A2 | `WindSpeed.knots(1.0).metersPerSecond` | 0.514444 (± 1e-6) |
| A3 | Beaufort upper bounds in m/s (exclusive): 0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7 | 7.9 m/s → 4; 8.0 m/s → 5; 32.7 m/s → 12 |
| A4 | `Bearing(-10.0)` | 350.0 |
| A5 | `Bearing(370.0)` | 10.0 |
| A6 | `Bearing(360.0)` | 0.0 |
| A7 | `Sector(300, 60)` contains 0, 330, 300, 60 | true |
| A8 | `Sector(300, 60)` contains 61, 299, 180 | false |
| A9 | `Sector(90, 180)` contains 135 / 200 | true / false |
| A10 | `Sector(45, 45)` | throws IllegalArgumentException |
| A11 | vector mean of bearings 350 and 10 | 0.0 (± 1e-6), never 180 |
| A12 | vector mean of bearings 90 and 270, models `icon` and `gfs` | undefined → falls back to `icon` (bearing 90) |

## B. Kite sizing

Profile weight 80 kg unless stated. Formula: `effectiveKn = mean + 0.3 × (gust − mean)`, `ideal = weight / effectiveKn × 2.2`, candidates within [0.8 × ideal, 1.15 × ideal].

| ID | Weight | Mean / gust (kn) | Quiver (m²) | effectiveKn | ideal | Result |
| --- | ---: | --- | --- | ---: | ---: | --- |
| B1 | 80 | 18 / 24 | 7, 9, 12 | 19.8 | 8.8889 | 9, GO |
| B2 | 80 | 14 / 18 | 7, 9, 12 | 15.2 | 11.5789 | 12, GO |
| B3 | 80 | 26 / 30 | 7, 9, 12 | 27.2 | 6.4706 | 7, GO |
| B4 | 80 | 11 / 13 | 7, 9, 12 | 11.6 | 15.1724 | null, NO_GO UNDERPOWERED_FOR_QUIVER |
| B5 | 60 | 30 / 34 | 7, 9, 12 | 31.2 | 4.2308 | null, NO_GO OVERPOWERED_FOR_QUIVER |
| B6 | 84 | 17 / 19 | 9, 12 | 17.6 | 10.5 | tie (1.5 each) → 9, GO |
| B7 | 80 | 18 / 24 | 7, 12 | 19.8 | 8.8889 | gap → 7, MARGINAL UNDERPOWERED_FOR_QUIVER |
| B8 | 80 | 20 / 31 | 7, 9, 12 | 23.3 | 7.5536 | 7, GO |
| B9 | 80 | 18 / 24 | (empty) | — | — | sizing skipped, kite null, no reason |

Property: for fixed weight and gust spread, `ideal` is strictly decreasing as mean wind increases.

## C. Hour verdicts

Profile: 80 kg, INTERMEDIATE (12–28 kn, gust spread ≤ 10), quiver 7 / 9 / 12, riding hours 9–21 (the default). Spot: rideable sector 180–300, danger sector 60–150, Europe/Berlin. Defaults unless stated: hour starting 13:00 local (2026-07-18T11:00Z), bearing 240, daylight, precip 0, temp 15 °C, thunder LOW, three models agreeing.

| ID | Change from defaults | Verdict | Reasons (in order) | Kite |
| --- | --- | --- | --- | --- |
| C1 | mean 18, gust 24 | GO | — | 9 |
| C2 | mean 10, gust 13 | NO_GO | TOO_LIGHT, UNDERPOWERED_FOR_QUIVER | null |
| C3 | mean 20, gust 31 (spread 11) | MARGINAL | GUSTY | 7 |
| C4 | mean 20, gust 34 (spread 14) | NO_GO | GUSTY | 7 |
| C5 | mean 18, gust 24, bearing 100 | NO_GO | OFFSHORE_DANGER | 9 |
| C6 | mean 18, gust 24, bearing 330 | NO_GO | DIRECTION_UNRIDEABLE | 9 |
| C7 | mean 18, gust 24, precip 3.0 mm | MARGINAL | RAIN | 9 |
| C8 | mean 18, gust 24, thunder HIGH | NO_GO | THUNDERSTORM | 9 |
| C9 | mean 18, gust 24, not daylight | NO_GO | DARK | 9 |
| C10 | mean 30, gust 34 | NO_GO | TOO_STRONG, OVERPOWERED_FOR_QUIVER | null |
| C11 | mean 18, gust 24, temp 3 °C | MARGINAL | COLD | 9 |
| C12 | mean 14, gust 18, personal minWind = 15 kn | NO_GO | TOO_LIGHT | 12 |
| C13 | mean 18, gust 24, bearing 100, thunder HIGH, precip 3.0 | NO_GO | THUNDERSTORM, OFFSHORE_DANGER, RAIN | 9 |
| C14 | mean 18, gust 24, hour starting 07:00 local | NO_GO | OUTSIDE_RIDING_HOURS | 9 |
| C15 | mean 18, gust 24, hour starting 20:00 local (the last that starts before 21:00) | GO | — | 9 |
| C16 | mean 18, gust 24, hour starting 21:00 local | NO_GO | OUTSIDE_RIDING_HOURS | 9 |
| C17 | mean 18, gust 24, hour starting 07:00 local, riding hours 6–21 | GO | — | 9 |
| C18 | mean 18, gust 24, hour starting 07:00 local, not daylight | NO_GO | DARK, OUTSIDE_RIDING_HOURS | 9 |
| C19 | riding hours 21–9, 9–9, −1–5 or 5–25 | rejected (`IllegalArgumentException`); 0–24 is the whole day | — | — |
| C20 | mean 18, gust 24, spot in Asia/Kolkata (UTC+5:30): hours starting 08:30, 09:30, 20:30 and 21:30 local; with riding hours 0–24 the one starting 23:30 | NO_GO OUTSIDE_RIDING_HOURS, GO, GO, NO_GO OUTSIDE_RIDING_HOURS; GO | — | 9 |

Note on C4: the kite is still computed (7) even though the hour is NO_GO; the UI hides it for NO_GO hours.

## D. Windows

Hour verdicts as a sequence (G = GO, M = MARGINAL, N = NO_GO), hourly slots starting at index 0.

| ID | Sequence | Windows (index ranges, inclusive) | Marginal hours |
| --- | --- | --- | --- |
| D1 | N G G M G G N | 1–5 | 1 |
| D2 | N M G G N | 2–3 | 0 |
| D3 | G M M G | 0–0 and 3–3 | 0 and 0 |
| D4 | G M G M G | 0–4 | 2 |
| D5 | M G G M | 1–2 | 0 |
| D6 | N N N | none | — |

## E. Leave-by and usable time

Window 13:00–17:00 local (Europe/Berlin, summer, so 11:00Z–15:00Z) on 2026-07-18, drive 120 min, rig 30 min, minSession 2 h.

| ID | now (local) | leaveBy | usableStart | usableHours | Qualifies |
| --- | --- | --- | --- | ---: | --- |
| E1 | 09:00 | 10:30 | 13:00 | 4.0 | yes |
| E2 | 11:30 | 10:30 | 14:00 | 3.0 | yes ("leave now") |
| E3 | 13:30 | 10:30 | 16:00 | 1.0 | no → WINDOW_TOO_SHORT |
| E4 | 09:00, drive 200 min, maxDrive 180 | — | — | — | spot excluded → TOO_FAR |

## F. Confidence and ranking

| ID | Input | Expected |
| --- | --- | --- |
| F1 | hourly spreads 2, 3, 4, 2 kn | median 2.5 → HIGH |
| F2 | hourly spreads 5, 7, 8 kn | median 7 → LOW |
| F3 | hourly spreads 4, 6 kn | median 5 → MEDIUM |
| F4 | single model | UNKNOWN |
| F5 | usable 4 h HIGH; usable 5 h MEDIUM with 1 marginal hour | the 5 h window ranks first: the longest wins |
| F6 | usable 4 h each: HIGH, MEDIUM, UNKNOWN, LOW | ranked in that order |
| F7 | equal usable time and confidence, drives 60 and 90 min | 60-min spot ranks first |
| F8 | equal usable time, confidence and drive | the earlier start ranks first |

## G. Day decision

| ID | Situation | Expected |
| --- | --- | --- |
| G1 | Spot A has a 4 h HIGH window, spot B a 5 h MEDIUM window on the same day | Go(spot B), alternatives contain spot A |
| G2 | Only GO hours that day form a 1-hour window | NoGo(WINDOW_TOO_SHORT) |
| G3 | No GO hours; daylight reasons: GUSTY ×5, TOO_LIGHT ×5 | NoGo(GUSTY) — tie broken by severity |
| G4 | Every favorite spot exceeds maxDrive | NoGo(TOO_FAR) |
| G5 | Today NoGo, day after tomorrow Go | `nextGoDay` = day after tomorrow |
| G6 | Three qualifying windows on one day (3, 2 and 4 h) | Go(the 4 h window), alternatives the 3 h and the 2 h one, in that order |
| G7 | GO wind only 06:00–09:00 local, riding hours 9–21 | NoGo(OUTSIDE_RIDING_HOURS) |
| G8 | GO wind 06:00–10:00 local, riding hours 9–21, minSession 2 h | the window is 09:00–10:00, too short: NoGo(WINDOW_TOO_SHORT) |
| G9 | riding hours 22–24, light wind (8 kn) all daylight hours | no daylight hour within the riding hours, so the reason of the daylight hours: NoGo(TOO_LIGHT) |
| G10 | riding hours 9–21; 06:00–09:00 too light, 10:00–12:00 gusty (gust spread 15 kn) | only hours within the riding hours count: NoGo(GUSTY), not the more frequent TOO_LIGHT of the early hours |

## H. Time zones and DST (Europe/Berlin)

| ID | Input | Expected |
| --- | --- | --- |
| H1 | hourly slots 2026-03-28T22:00Z … 2026-03-29T23:00Z grouped by local date | local 2026-03-29 has 23 slots (first 2026-03-28T23:00Z, last 2026-03-29T21:00Z) |
| H2 | hourly slots 2026-10-24T20:00Z … 2026-10-26T00:00Z grouped by local date | local 2026-10-25 has 25 slots (first 2026-10-24T22:00Z, last 2026-10-25T22:00Z) |
| H3 | local labels on 2026-10-25 | "02:00" appears twice; labels for those two slots must stay distinguishable (show UTC offset) |
| H4 | local labels on 2026-03-29 | no slot labelled "02:00" |

## I. Sectors from the water

`DecisionEngine.sectorsFacing(water)`, where `water` is the direction from the beach to open water. Rideable: within 100° of the water. Danger: within 60° of the land (`water + 180`). The 20° between them on either side is neither.

| ID | Water | Expected |
| --- | --- | --- |
| I1 | 270 (west) | rideable `Sector(170, 10)`, danger `Sector(30, 150)` |
| I2 | 0 (north) | rideable `Sector(260, 100)`, danger `Sector(120, 240)` |
| I3 | 90; wind from 90 / 190 / 200 / 210 / 270 | rideable / rideable / neither / danger / danger |
| I4 | 350 | rideable `Sector(250, 90)`, danger `Sector(110, 230)` |
| I5 | any water; wind from `water + k`, k in [-180, 180] | \|k\| ≤ 100: rideable only; \|k\| ≥ 120: danger only; otherwise neither |
