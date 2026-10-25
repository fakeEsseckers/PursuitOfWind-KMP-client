---
name: compose-screen-recipe
description: The standard pattern for a Pursuit Of Wind screen in Compose Multiplatform — feature package layers, MVI ViewModel with state, intents, results and a reducer, route, stateless screen, previews on sample data, strings, accessibility and test tags. Use when creating or changing any screen, reusable component or the custom wind chart in shared/app.
---

# Compose screen recipe

## Files per screen

Each screen is a package `io.github.esseckers.pursuitofwind.feature.<screen>` in `:shared:app` (`docs/adr/0008-mvi-presentation-in-feature-packages.md`). Today as the example:

```text
feature/today/
  domain/
    entity/TodaySnapshotEntity.kt  // what the screen needs from the data layer, as one value
    entity/RefreshResultEntity.kt  // what a refresh reports (forecast and crew errors kept apart)
    mapper/RefreshErrorMapper.kt   // pure rules over domain types: which of several errors to show
    repository/TodayRepository.kt  // interface: snapshot: Flow, suspend fun refresh()
  data/
    repository/TodayRepositoryImpl.kt  // over the :shared:data repositories; catches and logs storage errors
  presentation/
    model/TodayState.kt         // the MVI state the view renders
    model/TodayIntent.kt        // sealed: what the user does
    model/TodayEffect.kt        // sealed: one-off events for the app (OpenSpot)
    model/TodayInputs.kt        // what the state is computed from (data, now, refresh status)
    model/TodayUiModels.kt      // DayUi, RideUi …, formatted for display
    mapper/DayCardsMapper.kt    // pure: engine results -> UI models (formatting lives here, not in composables)
    mapper/TodayStateMapper.kt  // pure: inputs -> TodayState
    viewmodel/TodayResult.kt    // sealed: what happened (data changed, clock ticked, refresh started / finished)
    viewmodel/TodayReducer.kt   // pure: (state, result) -> state
    viewmodel/TodayViewModel.kt // MviViewModel: intents and observed data -> results
    viewmodel/TodayRefreshPolicy.kt  // when the screen refreshes on its own
    navigation/TodayRoute.kt    // collects state and effects, takes navigation lambdas (onOpenSpot); no layout
    view/TodayScreen.kt         // stateless: (state, onIntent) -> UI
    view/TodayCards.kt          // larger pieces of the screen, when TodayScreen would grow too long
    preview/TodayPreviewData.kt // sample states for previews and UI tests
    preview/TodayPreviews.kt    // one @Preview per state, light and dark
  di/TodayModule.kt             // factory<TodayRepository>, viewModel { }; listed in app/di/AppModules.kt
```

A screen without one-off events has no `Effect` file and declares `Nothing`. A screen with no state at all (Welcome: text and one button) has only `view/` and calls its navigation lambda directly; it has no ViewModel. Everything in a feature package is `internal`: the iOS framework exports only `App()` and `MainViewController()`. A feature never imports another feature; what two features need moves to `core` or to `:shared:data`.

Shared code next to `feature/`:

- `core/mvi` — `MviViewModel`, `Reducer`; `core` (`Extensions.kt`) — `CollectEffects`, `launchFinishing`, `Clock.ticks`.
- `core/domain` — rules several features share, with their `*Entity` types: `RideRulesMapper` (role, departure, end, the action a ride offers).
- `core/refresh` — `RefreshStatus` (a screen's refresh: `started`, `finished`, `shownError`), `RefreshNotice` and `RefreshNoticeMapper` (offline, failed, stale); the banner is `RefreshNoticeBanner` in the design system.
- `core/designsystem` — `theme`, `component`, `icon` (see below).
- `core/ui` — formatting and labels used by several screens (`AppError.message()`, `clockTime`, `windRangeText`, `durationText`, `driveText`, `formatTenths`, `DayLabel`, `TimeRange`, `updatedText`, `Freshness`, `rideDriverText`, `rideSeatsText`), and how steppers move (`Step`, `StepRange`, `StepperUi`, `SPOT_DRIVE_STEPS`): an intent says which way, the ViewModel or reducer applies the range.
- `core/resources` — the generated `Res` (strings live in `composeResources/values` and `values-ru`).
- `app` — `App()`, `appModules()`, `navigation` (destinations, `AppNavigation`, `Navigator`), platform entry points.

## MVI rules

- Every screen ViewModel extends `MviViewModel<State, Intent, Result, Effect>` and is obtained with `koinViewModel()` in the Route.
- The view renders `state` and calls `onIntent(intent)`. No public mutable state, no suspend functions in the API.
- The ViewModel never sets the state. It turns intents and observed data into `Result`s and calls `dispatchResult(result)`; the `Reducer` folds each one into the next state. The reducer is pure: no clock, no I/O, no coroutines. It is tested with plain values.
- Data the screen observes goes in `bind(): Flow<Result>`. It runs while the view collects `state` and stops 5 s after the last collector leaves, so a screen in the background stops observing and coming back starts it again. `bind()` never collects `state`; it follows a part of it with `select { it.inputs.shown }`, which does not keep it running. What the screen decides (which crew is on screen) is worked out by the reducer and kept in the state, not in `MutableStateFlow`s of the ViewModel.
- Work a screen starts and must always end (a refresh, a save) runs in `viewModelScope.launchFinishing(unfinished, work) { result -> ... }` (`core`): the last block gets the result, or `unfinished` when the work is cancelled or throws, so a `...Started` result always gets its `...Finished` and the screen never stays refreshing or saving.
- The state mapper is used in one place only, the reducer. Its functions take the inputs and parameters and return the state or a part of it (`SpotDetailStateMapper.map(inputs, riderZone)`). The ViewModel never calls it: what it needs from it comes with the state (`ProfileState.validProfile`, `SpotPickerState.changes`), and decisions the ViewModel acts on (which crew a ride goes to, when the car leaves) are made in the ViewModel and reach the state as results (`RideOfferResult.Ticked(now, departAt)`).
- When the state is derived from several inputs (data, clock, refresh status), keep the inputs in the state (`TodayState.inputs`) and let the reducer recompute the rest with the feature's state mapper. Do not combine flows in the ViewModel. The reducer keeps the previous `content` instance when the new one is equal: the state changes on every tick, and Compose compares unstable UI models by instance, so this is what lets unchanged cards skip recomposition.
- Effects: `postEffect(effect)` for one-off events (navigate, show a snackbar), collected in the Route with lifecycle awareness. A screen without them declares `Nothing`. A failed refresh on Today is state (a notice above cached data), not an effect.
- Repositories are the source of truth. The feature's repository interface (in `domain`) exposes cached flows and a `refresh()`; the ViewModel never keeps its own copy of the data.
- Refresh from `bind()` (`onStart`) when it is due (`TodayRefreshPolicy.isDue`: never refreshed, the last one failed, older than `REFRESH_AFTER`, the clock went back, or data missing), not from `init`. One refresh runs at a time. While the screen is shown, data that needs fetching at once (a favorite chosen in the picker while Today stayed below it: `TodayRefreshPolicy.hasNewFavorite`) starts a refresh from the observed flow; remember what the last refresh covered, so a failure offline does not loop.
- A refresh never throws into `viewModelScope`: failures come back as `AppError`s (a cache read in a feature repository goes through `outcomeOf { }`, so a storage failure is `AppError.Storage`; the data layer logs it, and any unexpected failure, with its stack trace). Keep errors that only affect part of the screen apart (Today: crew problems do not mark the forecast as failed).
- A ViewModel's constructor takes its repository, the screen's arguments (`draft`, `spotId`, `date`) and, when it reads the time, the `Clock` and the rider's `TimeZone` (`TodayViewModel(repository, clock, riderZone)`); nothing else. How often it ticks or polls is a constant inside it (`TICK`, `POLL` in its companion). Never hard-code `Clock.System` or `Dispatchers.IO` (tests use virtual time and `ControllableClock`).
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
- Strings only from Compose Multiplatform resources, EN + RU, including plurals (`pluralStringResource`). The rider can pick the language in the profile (`AppLanguage`, stored by `PreferencesRepository`); `AppNavigation` wraps the screens in `AppLanguageScope`, which sets the platform locale (the `AppLocale` service each platform binds in Koin; `RecordingAppLocale` in tests) and composes them again. Anything that must survive a language change (the back stack, the ViewModel store decorator) stays outside the scope; never read a string into a `remember` that outlives it. Reason copy follows the table in `docs/domain-rules.md`.
- Verdict display: icon + label + color, never color alone: `VerdictBadge`, `VerdictCard`, `ConfidenceChip`.
- Accessibility: meaningful `contentDescription` (or `null` for decorative icons next to text), merged semantics for cards and banners, headings (cards too, so screen readers can jump between them), live regions for messages that replace content, minimum touch target 48 dp, layouts that wrap (`FlowRow`) and survive 200% font scale.
- Where pull-to-refresh is the only way to refresh, add a custom "Refresh" action to nodes screen readers focus (the merged title and each card); an action on an unfocusable container is never offered. A screen opened from another one has a refresh button in its `ScreenTopBar` instead.
- Something tappable inside a merged card (`LinkRow`, a window) is its own node: screen readers reach it after the card, and UI tests find it by its own tag, not in the card's text.
- Times: where something happens (a session at the spot) in that place's time zone; when the rider has to act (leave, a ride departing) and the "today" label in the rider's zone.
- Edge to edge: a screen's `Scaffold` takes `contentWindowInsets = screenInsets()` (system bars and cutouts above and at the sides; `ScreenTopBar`, `TabTitle` and Today's header clear the status bar themselves). The bottom is the content's: a scrolling column ends with `windowInsetsPadding(contentEndInsets())` after `verticalScroll`, so it scrolls behind the navigation bar and its end stays clear of it. A form puts `imePadding()` before `verticalScroll`; the end padding then counts only what the keyboard does not cover. A screen whose content scrolls behind the navigation bar puts `NavigationBarScrim()` last in a `Box`, at the bottom: with three-button navigation it keeps the buttons readable (the activity turns the system scrim off), with gesture navigation and above a tab bar it takes no room. A bar in the `bottomBar` slot pads itself inside its own background (the spot picker's Done bar), so its color reaches the bottom edge.
- Lazy list keys are strings (Android saves them in a `Bundle`).
- `Modifier.testTag("today.day.2026-10-24")` style tags (`<screen>.<element>`) on elements used in UI tests.
- Previews: one per state in light and dark, from `<Screen>PreviewData`, which the UI tests use too. Never the live fake backend: its forecast changes on every request.

## Design system (`io.github.esseckers.pursuitofwind.core.designsystem`)

- `PursuitOfWindTheme` sets Material's color scheme, type scale and shapes, plus `PursuitOfWindTheme.colors` for meaning-bearing colors: `go`, `marginal`, `noGo` (each a `VerdictPalette` of accent and container colors, contrast ≥ 4.5:1 in both themes) and `confidenceHigh/Medium/Low/Unknown`.
- `theme`: colors, `PursuitOfWindTheme`, tokens `Spacing.xs … xl` and `Sizes` (icons, border, placeholders). No raw `dp` or color literals outside the design system.
- `component`: `VerdictBadge`, `VerdictCard` (optionally one button: `onClick`, `onClickLabel`), `ConfidenceChip`, `StatusBanner`, `MessageState`, `ActionButton`, `AppSnackbarHost` (every screen's snackbars: gone after 3 s, an action included, unless accessibility settings ask for longer), `PrimaryButton` (full width, with a busy state), `Skeleton`, `Disclaimer`, `IconLine`, `ScreenTopBar` and `BarIconButton` for screens opened from another one, `TabTitle` for a tab's screen, `RefreshNoticeBanner` for why the data may not be current, `screenInsets()`, `contentEndInsets()` and `NavigationBarScrim` for edge to edge, `LinkRow` for a line that opens something; for forms `SectionTitle`, `DraftTextField`, `ChoiceChip` and `Stepper`; `icon`: the app's own line drawings in `AppIcons` (no icon library).
- `WindChartColors` (`windChartColors()`) and the `Sizes.chart*` tokens are the wind chart's.

## Forms (profile, spot picker)

- The draft lives in the state (`ProfileState.form`), as typed: text fields hold strings, choices hold values. It becomes a domain object only through a pure check in the mapper (`ProfileFormMapper.check` → `Valid(profile)` or `Invalid(errors)`), which applies the limits of `api/openapi.yaml`; the reducer puts the result in the state (`errors`, `validProfile`), and the ViewModel saves `validProfile`. Steppers and chips can only hold valid values; text is filtered as it is typed (`acceptsNumber`, the name's length).
- Errors show once the rider has tried to save (`inputs.submitted`) and go as fields are fixed. A hidden group with an error opens by itself.
- Text fields are `DraftTextField`s: each keeps its own text and cursor, so typing never waits for the state to come back through the flow, and starts over from the state when `formVersion` changes. The reducer bumps `formVersion` only when it rewrites text from outside (the saved profile arriving, wind limits converted to a new unit), never for the rider's own typing.
- Text written from a value (a saved wind limit, or one converted to another unit) keeps the exact value next to it (`minWindSpeed`), cleared once the rider types, so rounding for display never reaches the saved profile.
- The first cached value fills the form; later ones only update what the draft is compared with, so a save elsewhere never overwrites typing. "Nothing changed" compares meaning, not text (`62,5` is the saved 62.5 kg), and saving an unchanged form confirms without a request.
- A save is one coroutine that always ends with a `SaveFinished` result (`finally`) carrying the error or the saved value, which counts at once (the cache follows a moment later); success is an effect (`Saved`, `Finished`) the Route turns into navigation or a snackbar. Writes that replace the same cached data (the picker's refresh and save) run one after the other.
- Lists changed in place without a "Save" button (the Spots tab) keep the rider's edits on top of the cache until their save ends, whatever the cache emits meanwhile, so an earlier save on its way never shows. A write ends with the cache as read right after it, which the repository hands back, not by waiting for an emission that matches: a tab that stopped collecting would miss it and keep the overlay forever. They send one save at a time per item: later taps only change the edit in the state, and the running save reads it again after each request, so it sends the latest value when done (`SpotsRepository.save(spotId) { wanted }`). The repository runs the writes (saves, removals, undos) one at a time in the order they were made and finishes each even when the screen is left (`withContext(NonCancellable)` and a fair `Mutex` inside it, one instance for the app); the ViewModel keeps no jobs or maps of its own. A failed write puts the item back and says why in a snackbar; a removal offers "Undo" in the snackbar, which brings the item back at once in its place, marked as coming back (`SpotsInputs.restoring`), until it is followed again, so work the ViewModel starts always shows in the state and ends through `launchFinishing`. Keeping a refresh from overwriting a write is the repository's job too (`SpotRepository` runs them one at a time), not the ViewModel's: a screen does not wait for its own refresh before it saves.
- A screen that shows other people's activity (the Crew tab) polls while it is shown: `bind()` fetches again every `POLL` (30 s, a constant in `CrewViewModel`), so polling stops with the screen.
- Dialogs are state, not composable flags (`CrewDialog`): the ViewModel opens and closes them, a create or join dialog that is sending stays open when dismissed, and a failure stays in it with the reason. Text typed in a dialog lives in the dialog (`rememberSaveable`) and goes out with the confirm intent.
- Platform UI services, such as the share sheet, are interfaces in `core/platform` (`ShareLauncher`) bound in each platform's Koin module (`startPursuitOfWind`, `MainViewController`); the Route injects them with `koinInject()` and calls them for an effect, so the ViewModel stays free of platform code. Tests bind a recording one.
- A screen that needs arguments from another feature gets a plain key (`Destination.OfferRide` holds epoch seconds) that `AppNavigation` turns into the feature's own model (`RideDraft`), passed to the ViewModel with `parametersOf`.
- Errors sit under their fields, and a line next to the button says to check them, because the first field with an error may be scrolled away. Single-choice chips are radio buttons in a `selectableGroup`; chips for a set are checkboxes.

## Navigation

- Navigation 3 (`docs/adr/0009-navigation-3.md`). Destinations are the sealed `Destination` in `app.navigation`, with plain serializable values. A new screen adds a destination, registers it in `navigationConfiguration` (iOS has no reflection) and adds an `entry<…>` in `AppNavigation`.
- Features never see destinations or the back stack. A Route takes lambdas (`onOpenSpot`, `onBack`); `AppNavigation` connects them through the `Navigator`.
- One back stack. A fresh launch starts at `Welcome` when no profile is cached and at `Today` otherwise (`startDestination`); onboarding is `Welcome → SetUpProfile → PickSpots`, and `finishSetup()` leaves Today as the only entry. Tabs (`AppTab`: Today, Spots, Crew, Profile) sit on top of Today: `selectTab` keeps Today as the root and closes screens opened on the old tab, so back from a tab returns to Today. Each tab's screen carries the tab bar (`TabScreen` in `AppNavigation`), so a screen opened from a tab covers it and back shows it again with the tab; `TabScreen` takes the bar's height and consumes it, so the tab lays out above it and does not add the navigation bar again. Every other inset is the screen's own (Screen rules). Moving between tabs fades (the tab entries' metadata), and so does Today at the end of onboarding; screens opened from a tab keep `NavDisplay`'s platform transitions. Leaving a form drops its unsaved edits without asking.
- One form can serve two places with a mode (`ProfileMode.SET_UP` in onboarding, `TAB` as a tab): each back stack entry gets its own ViewModel, and only the Route and the top bar differ.
- When the ViewModel decides to navigate (it depends on state or an intent's data), it posts an `Effect`, and the Route handles it with `CollectEffects(viewModel.effects) { … }`, which collects only while the screen is started. A plain view event with nothing to decide (back) calls the lambda directly.
- A ViewModel that needs arguments gets them from Koin: `viewModel { (spotId: SpotId, date: LocalDate?) -> … }` and `koinViewModel { parametersOf(spotId, date) }` in the Route. Each back stack entry has its own ViewModel store.

## Wind chart (spot detail)

- `WindChart(chart, days, selected, onSelectHour)` in `feature/spotdetail/presentation/view`; no chart library. The mapper (`SpotForecastMapper`) gives it everything in m/s and spot-local labels (`localHourLabel` from the engine, so both hours of a clock change differ); the grid comes from `chartScale` in round values of the rider's unit.
- It shows today and the next two days at the spot (`CHART_DAYS`). A last day the cache does not cover until midnight is left out, so no window ends where the data ends; windows the rider can no longer reach stay tinted on the chart but leave the list.
- Layers, back to front: night and window tints, grid and day separators, bars in verdict colors, gust stems and ticks, the models' band, direction arrows (pointing where the wind blows), day names and time labels (labels that would overlap are left out, so 200% font works). The dashed line at the current time and the selection frame are layers of their own.
- Performance: all geometry, paths, strokes and measured text are built in `drawWithCache` and only rebuilt when the chart, the size or the theme changes. The current time (`Ready.nowOffset`) lives outside `WindChartUi`, so the minute tick does not rebuild the chart; the reducer keeps the chart's instance while it is equal; each hour cell is its own composable, so a tap recomposes two cells, not 72.
- Accessibility and taps: one invisible cell per hour lies over the drawing, with a content description ("Saturday 13:00: 20 kn, gusts 25 kn, wind from the west, GO"), `Role.Tab`, a selected state and a click label. Screen readers never depend on the drawing, and hit-testing needs no pointer math. Cells are 28 dp wide: under Android's 48 dp guideline, above WCAG's 24 px minimum, a deliberate trade for seeing a day at a glance; the day chips and window cards are the large targets.
- Time runs left to right in every language: the chart forces `LayoutDirection.Ltr`.
- The chart scrolls sideways; the wind scale stays on the left. The selected hour is scrolled into view (a jump the first time, a glide after a day chip or a window, also when that day or window is already selected).

## Done checklist for a screen

- [ ] All state branches render and have previews.
- [ ] Mapper and reducer tests in `commonTest`; ViewModel test with Turbine in `jvmTest` on the real data layer covers load, refresh, error, and the main intent.
- [ ] The feature's Koin module is listed in `appModules()`, and a test resolves the ViewModel from the graph.
- [ ] Screen test in `jvmTest`: each state, intents from buttons, Russian strings, 200% font at phone size (`checkTexts()` in `jvmTest/.../testing/TextFit.kt`).
- [ ] Strings in EN and RU.
- [ ] Test tags on interactive elements; critical path covered by a UI test if the screen is on it.
- [ ] A screen reached by navigation is in `navigationConfiguration` and in the navigation test.
- [ ] Looks right in dark theme and at 200% font scale.
