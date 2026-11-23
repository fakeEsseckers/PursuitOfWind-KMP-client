# 0011. Local notifications until the server exists

- Status: Accepted
- Date: 2026-11-12

## Context

A rider who lives hours from the water wants to hear that a session window has appeared, without opening the app. There is no server yet, so there is no push. [ADR 0010](0010-live-fake-backend-instead-of-demo-mode.md) made the fake backend live: every forecast request is a new model run, so a window's start can move by an hour between two checks. A check that announces windows by their exact start would tell the rider about the same session every hour. The contract already has `PUT /v1/me/push-token`, but nothing sends pushes in this phase.

## Decision

- **The app checks by itself in the background**, about once an hour as the system allows. The check is common code (`WindowAlertsCheck`). It fetches the forecasts of the favorite spots only, decides the days exactly as Today does (`DayDecisionsMapper`: the rider's today and the next two days) and shows one notification for each GO day that is news. The profile and the followed spots come from the cache, because only this device changes them.
- **A day is announced once.** `NewWindowsMapper` recognises an announced window by spot, spot-local date and shared time (overlap), not by its exact start, and a GO day is news only when none of its windows, neither the best one nor an alternative, was announced. So a model run that moves the window by an hour, or ranks another favorite first that day, announces nothing. The remembered entry follows the window to where the forecast has it now, so a window that keeps drifting stays the same window. A window the forecast dropped stays remembered until it would have ended, so it is not announced again if it comes back. Windows that only touch (one ends at 17:00, the other starts at 17:00) are different windows, and a day that is a GO at another spot only after the announced window is gone is news.
- **Remembered before shown.** If the process dies in between, one notification is lost. The other order would show the same window every hour while the device cannot store what it showed.
- **A forecast that cannot be fetched costs that spot only.** The check decides on the favorites whose forecasts it fetched and never on an old forecast. For a network error or a 5xx it also asks the scheduler for another try; a spot the server no longer has (404) is just left out.
- **Off by default, switched in the profile.** There is no Settings screen: units and language are in the profile too. The system's permission is asked only when the rider turns the switch on. The switch shows what the rider wants, so it can always be taken back; when the system does not show the app's notifications, the profile says so under the switch and offers the system's settings. A wish the system refused becomes the stored choice once the system allows notifications, for as long as the profile stays open. The choice is a device choice and stays when another user's data replaces the cache; the announced windows belong to the user.
- **One place follows the choice.** `AlertsSchedule` schedules or cancels the periodic check as the stored choice changes, and keeps trying while the choice cannot be read. `startKoinOnce` starts it with the Koin graph from the choice as stored, so every start of the app's process puts the schedule right. Screens only change the choice.
- **Platform services follow the platform-code policy**: `LocalNotifier`, `NotificationPermission` and `BackgroundRefreshScheduler` are interfaces in `:shared:app` `core/platform`. The Android implementations, including the WorkManager worker, live in `shared/app/src/androidMain` next to `AndroidShareLauncher` and are bound in Koin. WorkManager runs unique periodic work once an hour, only with a network connection, with exponential backoff and `ExistingPeriodicWorkPolicy.UPDATE`, which keeps the next run time and lets a later version change the request. Notifications use the platform `Notification.Builder` with one channel (minSdk 26), and `POST_NOTIFICATIONS` is asked through the visible activity. Notifications count as blocked when the app's notifications or that one channel are turned off. `:androidApp` keeps the manifest, the activity and, later, signing.
- **The text is in the rider's app language** (`AppLocale.use`) and gives the facts of the Today card: day and spot in the title, then the riding time in the spot's zone, the kite, and the time to leave in the rider's zone (always a time: a notification may be read hours later).
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
- Negative, in this phase only: the fake backend keeps its state in the app's process. A check that Android runs in a new process meets a backend that has forgotten the rider, as any restart of the app does: it still announces from what the phone had cached, the sign-in that follows drops the rider's cache, and later checks find no profile. Hourly alerts therefore last only while the app's process lives. A real server does not forget, so this goes with the fake.
- Notifications are decision support, like the verdicts they repeat, not safety advice.
- Revisit when: the server exists and sends pushes through `PUT /v1/me/push-token`. Whether the local check is then removed or kept as a fallback is undecided.
