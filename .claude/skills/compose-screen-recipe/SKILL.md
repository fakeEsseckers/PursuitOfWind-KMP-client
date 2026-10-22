---
name: compose-screen-recipe
description: The standard pattern for a Pursuit Of Wind screen in Compose Multiplatform — feature package layers, MVI ViewModel with state, intents, results and a reducer, route, stateless screen, previews on demo data, strings, accessibility and test tags. Use when creating or changing any screen, reusable component or the custom wind chart in shared/app.
---

# Compose screen recipe

## Files per screen

Each screen is a package `io.github.esseckers.pursuitofwind.feature.<screen>` in `:shared:app` (`docs/adr/0008-mvi-presentation-in-feature-packages.md`). Today as the example:

```text
feature/today/
  domain/
    model/TodaySnapshot.kt         // what the screen needs from the data layer, as one value
    model/RefreshResult.kt         // what a refresh reports (forecast and crew errors kept apart)
    repository/TodayRepository.kt  // interface: snapshot: Flow, suspend fun refresh()
  data/
    repository/TodayRepositoryImpl.kt  // over the :shared:data repositories; catches and logs storage errors
  presentation/
    model/TodayState.kt         // the MVI state the view renders
    model/TodayIntent.kt        // sealed: what the user does
    model/TodayInputs.kt        // what the state is computed from (data, now, refresh status)
    model/TodayUiModels.kt      // DayUi, RideUi …, formatted for display
    mapper/DayCardsMapper.kt    // pure: engine results -> UI models (formatting lives here, not in composables)
    mapper/TodayStateMapper.kt  // pure: inputs -> TodayState
    viewmodel/TodayResult.kt    // sealed: what happened (data changed, clock ticked, refresh started / finished)
    viewmodel/TodayReducer.kt   // pure: (state, result) -> state
    viewmodel/TodayViewModel.kt // MviViewModel: intents and observed data -> results
    viewmodel/TodayRefreshPolicy.kt  // when the screen refreshes on its own
    view/TodayRoute.kt          // collects state (and effects, when the screen has them), wires navigation and ViewModel; no layout
    view/TodayScreen.kt         // stateless: (state, onIntent) -> UI
    view/TodayCards.kt          // larger pieces of the screen, when TodayScreen would grow too long
    view/TodayPreviewData.kt    // sample states for previews and UI tests
    view/TodayPreviews.kt       // one @Preview per state, light and dark
  di/TodayModule.kt             // factory<TodayRepository>, viewModel { }; listed in app/di/AppModules.kt
```

Add an `Effect` file next to the intent when the screen has one-off events. Everything in a feature package is `internal`: the iOS framework exports only `App()` and `MainViewController()`. A feature never imports another feature; what two features need moves to `core` or to `:shared:data`.

Shared code next to `feature/`:

- `core/mvi` — `MviViewModel`, `Reducer`.
- `core/designsystem` — `theme`, `component`, `icon` (see below).
- `core/ui` — formatting and labels used by several screens (`AppError.message()`, `clockTime`, `windRangeText`).
- `core/resources` — the generated `Res` (strings live in `composeResources/values` and `values-ru`).
- `app` — `App()`, `appModules()`, platform entry points.

## MVI rules

- Every screen ViewModel extends `MviViewModel<State, Intent, Result, Effect>` and is obtained with `koinViewModel()` in the Route.
- The view renders `state` and calls `onIntent(intent)`. No public mutable state, no suspend functions in the API.
- The ViewModel never sets the state. It turns intents and observed data into `Result`s and calls `dispatch(result)`; the `Reducer` folds each one into the next state. The reducer is pure: no clock, no I/O, no coroutines. It is tested with plain values.
- Data the screen observes goes in `bind(): Flow<Result>`. It runs while the view collects `state` and stops 5 s after the last collector leaves, so a screen in the background stops observing and coming back starts it again.
- When the state is derived from several inputs (data, clock, refresh status), keep the inputs in the state (`TodayState.inputs`) and let the reducer recompute the rest with the feature's state mapper. Do not combine flows in the ViewModel. The reducer keeps the previous `content` instance when the new one is equal: the state changes on every tick, and Compose compares unstable UI models by instance, so this is what lets unchanged cards skip recomposition.
- Effects: `post(effect)` for one-off events (navigate, show a snackbar), collected in the Route with lifecycle awareness. A screen without them declares `Nothing`. A failed refresh on Today is state (a notice above cached data), not an effect.
- Repositories are the source of truth. The feature's repository interface (in `domain`) exposes cached flows and a `refresh()`; the ViewModel never keeps its own copy of the data.
- Refresh from `bind()` (`onStart`) when it is due (`TodayRefreshPolicy.isDue`: never refreshed, the last one failed, older than `REFRESH_AFTER`, the clock went back, or data missing), not from `init`. One refresh runs at a time.
- A refresh never throws into `viewModelScope`: failures come back as `AppError`s (storage exceptions as `AppError.Unknown`, logged with Kermit). Keep errors that only affect part of the screen apart (Today: crew problems do not mark the forecast as failed).
- Inject `Clock` and anything time-based (`TodayViewModel(repository, clock, riderZone, tick)`); never hard-code `Clock.System` or `Dispatchers.IO` (tests use virtual time; demo uses `ControllableClock`).
- Map `AppError` to text with `AppError.message()` (`core/ui/Formatting.kt`); screens only render string resources.

## State shape

Every screen models all of these explicitly:

```kotlin
internal data class TodayState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val updatedAgo: Duration? = null,     // "updated 2 h ago"
    val notice: Notice? = null,           // OFFLINE, REFRESH_FAILED, STALE or CREW_FAILED above cached content
    val inputs: TodayInputs = TodayInputs(),  // what the reducer computes the rest from; the view does not read it
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

## Design system (`io.github.esseckers.pursuitofwind.core.designsystem`)

- `PursuitOfWindTheme` sets Material's color scheme, type scale and shapes, plus `PursuitOfWindTheme.colors` for meaning-bearing colors: `go`, `marginal`, `noGo` (each a `VerdictPalette` of accent and container colors, contrast ≥ 4.5:1 in both themes) and `confidenceHigh/Medium/Low/Unknown`.
- `theme`: colors, `PursuitOfWindTheme`, tokens `Spacing.xs … xl` and `Sizes` (icons, border, placeholders). No raw `dp` or color literals outside the design system.
- `component`: `VerdictBadge`, `VerdictCard`, `ConfidenceChip`, `StatusBanner`, `MessageState`, `ActionButton`, `Skeleton`, `Disclaimer`, `IconLine`; `icon`: the app's own line drawings in `AppIcons` (no icon library).

## Wind chart (Spot detail)

- Custom `Canvas` composable `WindTimeline(hours, windows, units, onHourClick)`; no chart library.
- Draw: speed bars colored by verdict, gust ticks above bars, direction arrows on a lane below, model-spread band, highlighted windows, local time axis with day separators (DST-aware labels from the mapper).
- Performance: precompute geometry in `remember(hours, size)`; hit-testing via `pointerInput` maps x to hour index; no allocations inside `DrawScope` loops.
- Accessibility: expose a semantic list of hours (time, speed, verdict) so screen readers do not rely on the drawing.

## Done checklist for a screen

- [ ] All state branches render and have previews.
- [ ] Mapper and reducer tests in `commonTest`; ViewModel test with Turbine in `jvmTest` on the real data layer covers load, refresh, error, and the main intent.
- [ ] The feature's Koin module is listed in `appModules()`, and a test resolves the ViewModel from the graph.
- [ ] Screen test in `jvmTest`: each state, intents from buttons, Russian strings, 200% font at phone size.
- [ ] Strings in EN and RU.
- [ ] Test tags on interactive elements; critical path covered by a UI test if the screen is on it.
- [ ] Looks right in dark theme and at 200% font scale.
