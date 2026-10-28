# 0014. Riding hours, and the longest window first

- Status: Accepted
- Date: 2026-10-28

## Context

The engine counted every daylight hour. On a summer morning that meant a GO from 05:00, a time to leave in the middle of the night, and windows that ran from dawn to dusk; riders do not plan a session like that. It also ranked a day's windows by a score, usable time lowered for bridged MARGINAL hours and weighted by confidence, made the best one the GO and offered the next two as alternatives. The owner rides, and asked for three things: windows between 9:00 and 21:00 by default, the longest session recommended, and the day's other windows offered as well.

## Decision

- **Riding hours in the rider profile**, whole hours in each spot's local time: 9 to 21 by default, any range from 0 to 24 with the start before the end (`RidingHours`, `ridingFromHour` and `ridingToHour` in `RiderProfile`). An hour counts when it starts within them, so in a time zone half an hour off UTC, where forecast hours start at half past, 9 to 21 counts from 09:30 to 21:30 and 0 to 24 is still the whole day.
- **An hour outside them is NO_GO with the reason `OUTSIDE_RIDING_HOURS`**, an hour rule like `DARK`. Windows are built from hour verdicts, so they stop at the riding hours with no other change, and a window clipped below the minimum session is `WINDOW_TOO_SHORT` as before.
- **A day whose wind is good only outside them is a NO-GO with `OUTSIDE_RIDING_HOURS`** ("Wind only outside your riding hours"): the wind came, at hours the rider does not ride. A day with any GO hour within them whose window is too short stays `WINDOW_TOO_SHORT`; any other day gives the most frequent reason within the hours.
- **Windows rank by usable riding time**, then confidence (HIGH, MEDIUM, UNKNOWN, LOW), the shorter drive, the earlier start and the spot id. The score is gone.
- **Every other qualifying window of the day is an alternative**, best first.
- In the API both hours are optional with the defaults 9 and 21 (changelog 2026-10-28), so a profile saved before them, on the server or in the app's cache, reads as 9 to 21.

## Alternatives considered

- **A filter on top of the windows** (find windows over every hour, then clip or drop them) — a second rule beside the hour verdicts, and the hour chart would show GO for hours that never count. As an hour rule, the chart, the windows and the day agree.
- **Riding hours in the rider's own time zone** — the same for a rider at home, but a spot abroad would get hours shifted by the time difference; the spot's local time is what "riding at 9:00" means there.
- **Keep the score and only raise the weight of length** — still a number nobody can explain on a card; "the longest session wins" is what riders asked for and can check.
- **Keep the cap of two alternatives** — a cap hides the evening window behind two spots the rider will not drive to; the list is short in practice, as it holds only qualifying windows of one day.

## Consequences

- Positive: GO days start at sensible hours, and the time to leave is no longer before dawn.
- Positive: ranking is one sentence on the card and in the docs; a rider can see why a window won.
- Negative: confidence only breaks ties, and a bridged MARGINAL hour no longer lowers a window's rank: a long window the models disagree on beats a shorter sure one. The confidence chip and the hour chart still show the difference.
- Negative: a day with many spots and broken-up wind lists many alternatives on Today.
- Negative: `SessionWindow.score` is removed and `Reason` gains a value: a breaking change for the engine's users (the future server) before its first release.
- Revisit when: riders ask for different hours on different days, such as weekends, or for a session length they prefer over "as long as possible".
