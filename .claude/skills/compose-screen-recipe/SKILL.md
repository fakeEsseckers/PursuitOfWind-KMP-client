---
name: compose-screen-recipe
description: The standard pattern for a Pursuit Of Wind screen in Compose Multiplatform — route, stateless screen, ViewModel with UiState and intents, previews on demo data, strings, accessibility and test tags. Use when creating or changing any screen, reusable component or the custom wind chart in shared/app.
---

# Compose screen recipe

## Files per screen (`io.github.esseckers.pursuitofwind.ui.<feature>`)

```text
TodayRoute.kt         // collects state, wires navigation and ViewModel; no layout
TodayScreen.kt        // stateless: (state, onIntent) -> UI
TodayCards.kt         // larger pieces of the screen, when TodayScreen would grow too long
TodayViewModel.kt     // StateFlow<TodayUiState>, fun onIntent(intent)
TodayUiState.kt       // immutable state, UI models, sealed TodayIntent (and TodayEffect if the screen has one-off events)
TodaySources.kt       // combines the repositories into one snapshot Flow and runs the refresh
TodayState.kt         // pure: snapshot + now + refresh status -> TodayUiState
DayCards.kt           // pure: engine results -> UI models (formatting lives here, not in composables)
TodayPreviewData.kt   // sample states for previews and UI tests
TodayPreviews.kt      // one @Preview per state, light and dark
```

Everything in a feature package is `internal`: the iOS framework exports only `App()` and `MainViewController()`.

## ViewModel rules

- `androidx.lifecycle.ViewModel` (multiplatform), registered in `presentationModule` (`ui/di/AppModules.kt`) and obtained with `koinViewModel()` in the Route. Expose `val state: StateFlow<UiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Input is one function: `fun onIntent(intent: Intent)`. No public mutable state, no suspend functions in the API.
- Repositories are the source of truth. The ViewModel combines their cached flows with the refresh status; it never keeps its own copy of the data.
- Refresh from `onStart` of the state flow when it is due (`shouldRefresh`: never refreshed, older than `REFRESH_AFTER`, or data missing), not from `init`: `WhileSubscribed` restarts the flow when the screen comes back from the background or another screen, so stale data is refreshed then too. One refresh runs at a time.
- A refresh never throws into `viewModelScope`: failures come back as `AppError`s (storage exceptions as `AppError.Unknown`, logged with Kermit). Keep errors that only affect part of the screen apart (Today: crew problems do not mark the forecast as failed).
- One-off events (navigate, show a snackbar) go through a `Channel<Effect>` exposed as `Flow`, collected in the Route with lifecycle awareness. Add one only when a screen has such events: a failed refresh on Today is state (a notice above cached data), not an event.
- Inject `Clock` and anything time-based (`TodayViewModel(sources, clock, tick)`); never hard-code `Clock.System` or `Dispatchers.IO` (tests use virtual time; demo uses `ControllableClock`).
- Map `AppError` to text with `AppError.message()` (`ui/common/Formatting.kt`); screens only render string resources.

## UiState shape

Every screen models all of these explicitly:

```kotlin
internal data class TodayUiState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val updatedAgo: Duration? = null,     // "updated 2 h ago"
    val notice: Notice? = null,           // OFFLINE, REFRESH_FAILED, STALE or CREW_FAILED above cached content
) {
    sealed interface Content {
        data object Loading : Content
        data class Empty(val missing: Missing) : Content          // what the rider still has to add
        data class Ready(val days: List<DayUi>) : Content
        data class Error(val message: StringResource) : Content   // only when nothing is cached and no refresh runs
    }
}
```

Cached data always wins over an error: the error replaces the content only when there is nothing to show. UI models (`DayUi`, `RideUi`) are formatted for display (units, local times, kite sizes) in pure mappers with unit tests; labels that need resources (`DayLabel`, `WindRange`) stay structured and are resolved in composables.

## Screen rules

- Stateless composables, `Modifier` as the first optional parameter, hoisted state.
- Strings only from Compose Multiplatform resources, EN + RU, including plurals (`pluralStringResource`). Reason copy follows the table in `docs/domain-rules.md`.
- Verdict display: icon + label + color, never color alone: `VerdictBadge`, `VerdictCard`, `ConfidenceChip`.
- Accessibility: meaningful `contentDescription` (or `null` for decorative icons next to text), merged semantics for cards and banners, headings (cards too, so screen readers can jump between them), live regions for messages that replace content, minimum touch target 48 dp, layouts that wrap (`FlowRow`) and survive 200% font scale.
- Where pull-to-refresh is the only way to refresh, add a custom "Refresh" action to nodes screen readers focus (the merged title and each card); an action on an unfocusable container is never offered.
- Times: where something happens (a session at the spot) in that place's time zone; when the rider has to act (leave, a ride departing) and the "today" label in the rider's zone.
- Lazy list keys are strings (Android saves them in a `Bundle`).
- `Modifier.testTag("today.day.2026-10-24")` style tags (`<screen>.<element>`) on elements used in UI tests.
- Previews: one per state in light and dark, from `<Screen>PreviewData`, which the UI tests use too. Demo scenarios (Issue #10) will replace the sample data.

## Design system (`io.github.esseckers.pursuitofwind.ui.design`)

- `PursuitOfWindTheme` sets Material's color scheme, type scale and shapes, plus `PursuitOfWindTheme.colors` for meaning-bearing colors: `go`, `marginal`, `noGo` (each a `VerdictPalette` of accent and container colors, contrast ≥ 4.5:1 in both themes) and `confidenceHigh/Medium/Low/Unknown`.
- Tokens: `Spacing.xs … xl`, `Sizes` (icons, border, placeholders). No raw `dp` or color literals outside this package.
- Components: `VerdictBadge`, `VerdictCard`, `ConfidenceChip`, `StatusBanner`, `MessageState`, `ActionButton`, `Skeleton`, `Disclaimer`, `IconLine`; icons are the app's own line drawings in `AppIcons` (no icon library).

## Wind chart (Spot detail)

- Custom `Canvas` composable `WindTimeline(hours, windows, units, onHourClick)`; no chart library.
- Draw: speed bars colored by verdict, gust ticks above bars, direction arrows on a lane below, model-spread band, highlighted windows, local time axis with day separators (DST-aware labels from the mapper).
- Performance: precompute geometry in `remember(hours, size)`; hit-testing via `pointerInput` maps x to hour index; no allocations inside `DrawScope` loops.
- Accessibility: expose a semantic list of hours (time, speed, verdict) so screen readers do not rely on the drawing.

## Done checklist for a screen

- [ ] All UiState branches render and have previews.
- [ ] Mapper tests in `commonTest`; ViewModel test with Turbine in `jvmTest` on the real data layer covers load, refresh, error, and the main intent.
- [ ] Screen test in `jvmTest`: each state, intents from buttons, Russian strings, 200% font at phone size.
- [ ] Strings in EN and RU.
- [ ] Test tags on interactive elements; critical path covered by a UI test if the screen is on it.
- [ ] Looks right in dark theme and at 200% font scale.
