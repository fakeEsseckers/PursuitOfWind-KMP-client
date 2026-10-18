---
name: compose-screen-recipe
description: The standard pattern for a Pursuit Of Wind screen in Compose Multiplatform — route, stateless screen, ViewModel with UiState and intents, previews on demo data, strings, accessibility and test tags. Use when creating or changing any screen, reusable component or the custom wind chart in shared/app.
---

# Compose screen recipe

## Files per screen (`io.github.esseckers.pursuitofwind.ui.<feature>`)

```text
TodayRoute.kt        // collects state, wires navigation and ViewModel; no layout
TodayScreen.kt       // stateless: (state, onIntent) -> UI; previews live here
TodayViewModel.kt    // StateFlow<TodayUiState>, fun onIntent(intent)
TodayUiState.kt      // immutable state + sealed TodayIntent + one-off TodayEffect
```

## ViewModel rules

- `androidx.lifecycle.ViewModel` (multiplatform). Expose `val state: StateFlow<UiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Input is one function: `fun onIntent(intent: Intent)`. No public mutable state, no suspend functions in the API.
- One-off events (navigate, show snackbar) go through a `Channel<Effect>` exposed as `Flow`, collected in the Route with lifecycle awareness.
- Inject dispatchers and `Clock`; never hard-code `Dispatchers.IO` or `Clock.System` (tests use virtual time; demo uses `ControllableClock`).
- Map `AppError` to user-facing messages in the ViewModel via a single `ErrorMessageMapper`; the screen only renders text resources.

## UiState shape

Every screen models all of these explicitly:

```kotlin
data class TodayUiState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val lastUpdated: Instant? = null,   // drives "updated 2 h ago" / stale banner
    val isDemo: Boolean = false,
) {
    sealed interface Content {
        data object Loading : Content
        data object Empty : Content
        data class Ready(val decision: DecisionUi, val rides: List<RideUi>) : Content
        data class Error(val message: StringResource, val canRetry: Boolean) : Content
    }
}
```

UI models (`DecisionUi`, `RideUi`) are formatted for display (units, local times, labels). Formatting lives in mappers with unit tests, not in composables.

## Screen rules

- Stateless composables, `Modifier` as the first optional parameter, hoisted state.
- Strings only from Compose Multiplatform resources, EN + RU. No hard-coded user-visible text.
- Verdict display: icon + label + color, never color alone. Use the design-system components `VerdictBadge`, `VerdictCard`, `ConfidenceChip`.
- Accessibility: meaningful `contentDescription` (or `null` for decorative), merged semantics for cards, `Role.Button` for clickable rows, minimum touch target 48 dp, layouts survive 200% font scale.
- `Modifier.testTag("today.verdictCard")` style tags (`<screen>.<element>`) on elements used in UI tests.
- Previews: one per state (`Loading`, `Empty`, `Ready` for `saturday-on`, `Ready` for `gusty-trap`, `Error`) in light and dark, built from demo seed data so previews and screenshot tests match.

## Design system (`io.github.esseckers.pursuitofwind.ui.design`)

- `PursuitOfWindTheme` with light and dark color schemes, typography and spacing tokens (`Spacing.xs … xl`). No raw `dp` or color literals outside this package.
- Semantic colors: `go`, `marginal`, `noGo`, `confidenceHigh/Medium/Low`, with sufficient contrast in both themes.

## Wind chart (Spot detail)

- Custom `Canvas` composable `WindTimeline(hours, windows, units, onHourClick)`; no chart library.
- Draw: speed bars colored by verdict, gust ticks above bars, direction arrows on a lane below, model-spread band, highlighted windows, local time axis with day separators (DST-aware labels from the mapper).
- Performance: precompute geometry in `remember(hours, size)`; hit-testing via `pointerInput` maps x to hour index; no allocations inside `DrawScope` loops.
- Accessibility: expose a semantic list of hours (time, speed, verdict) so screen readers do not rely on the drawing.

## Done checklist for a screen

- [ ] All UiState branches render and have previews.
- [ ] ViewModel test with Turbine covers load, refresh, error, and the main intent.
- [ ] Strings in EN and RU.
- [ ] Test tags on interactive elements; critical path covered by a UI test if the screen is on it.
- [ ] Looks right in dark theme and at 200% font scale.
