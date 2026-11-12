# 0011. Local notifications until the server exists

- Status: Accepted
- Date: 2026-11-12

## Context

A rider who lives hours from the water wants to hear that a session window has appeared, without opening the app. There is no server yet, so there is no push. [ADR 0010](0010-live-fake-backend-instead-of-demo-mode.md) made the fake backend live: every forecast request is a new model run, so a window's start can move by an hour between two checks. A check that announces windows by their exact start would tell the rider about the same session every hour. The contract already has `PUT /v1/me/push-token`, but nothing sends pushes in this phase.

## Decision

- **The app checks by itself in the background**, about once an hour as the system allows. The check is common code (`WindowAlertsCheck`). It fetches the forecasts of the favorite spots only, decides the days exactly as Today does (`DayDecisionsMapper`: the rider's today and the next two days) and shows one notification for each GO day whose window is news. The profile and the followed spots come from the cache, because only this device changes them.
- **A window is announced once.** `NewWindowsMapper` recognises it by spot, spot-local date and shared time (overlap), not by its exact start. The remembered entry follows the window to where the forecast has it now, so a window that keeps drifting stays the same window. A window the forecast dropped stays remembered until it would have ended, so it is not announced again if it comes back. Windows that only touch (one ends at 17:00, the other starts at 17:00) are different windows.
- **Remembered before shown.** If the process dies in between, one notification is lost. The other order would show the same window every hour while the device cannot store what it showed. A check that cannot fetch the forecast decides nothing on the old one and asks the scheduler for another try, for a network error or a 5xx only.
- **Off by default, switched in the profile.** There is no Settings screen: units and language are in the profile too. The system's permission is asked only when the rider turns the switch on. A refusal shows the reason and a button to the system's settings, and the rider's wish is stored once the system allows notifications. The choice is a device choice and stays when another user's data replaces the cache; the announced windows belong to the user.
- **One place follows the choice.** `AlertsSchedule` schedules or cancels the periodic check as the stored choice changes. `startKoinOnce` starts it with the Koin graph from the choice as stored, so every start of the app's process puts the schedule right. Screens only change the choice.
- **Platform services follow the platform-code policy**: `LocalNotifier`, `NotificationPermission` and `BackgroundRefreshScheduler` are interfaces in `:shared:app` `core/platform`. The Android implementations, including the WorkManager worker, live in `shared/app/src/androidMain` next to `AndroidShareLauncher` and are bound in Koin. WorkManager runs unique periodic work once an hour, only with a network connection, with exponential backoff and `ExistingPeriodicWorkPolicy.KEEP`. Notifications use the platform `Notification.Builder` with one channel (minSdk 26), and `POST_NOTIFICATIONS` is asked through the visible activity. `:androidApp` keeps the manifest, the activity and, later, signing.
- **The text is in the rider's app language** (`AppLocale.use`) and gives the facts of the Today card: day and spot in the title, then the riding time in the spot's zone, the kite, and when to leave in the rider's zone.
- **iOS follows in the next change.** Until then `IosBackgroundRefreshScheduler` is a placeholder with `available = false`, and the profile does not offer the alerts on iOS. The real implementation uses `BGTaskScheduler` and `UNUserNotificationCenter`.

## Alternatives considered

- **Identify a window by its exact start** — a live forecast moves the start, so the same session is announced again with every model run.
- **Check only while the app is open** — the rider gets nothing while the app is closed, which is the whole point.
- **Exact alarms** — need a special permission and give no benefit to an hourly check that is best effort anyway.
- **A notification for every change of a window** — noise; the rider wants to hear that a window exists, and Today has the current numbers.

## Consequences

- Positive: the rider hears about a window with no server, and the rule is the same as on Today because both call `DayDecisionsMapper`.
- Positive: the check is tested on the real data layer and the fake backend (`WindowAlertsCheckTest`), not only the rule.
- Negative: at best hourly. Android's Doze and battery optimisation, and iOS, decide when a check really runs.
- Negative: a shown notification is not withdrawn when a later forecast changes its mind.
- Negative: nothing is announced while notifications are blocked, and those windows stay news until they are allowed.
- Negative: a notification lost between "remembered" and "shown" is not shown later.
- Negative: no alerts on iOS until its implementation lands.
- Notifications are decision support, like the verdicts they repeat, not safety advice.
- Revisit when: the server exists and sends pushes through `PUT /v1/me/push-token`. Whether the local check is then removed or kept as a fallback is undecided.
