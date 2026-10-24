# 0008. MVI presentation in feature packages

- Status: Accepted
- Date: 2026-10-23

## Context

The first screen (Today, Issue #7) put its ViewModel, state rules, data combination and composables in one package, `ui.today`, next to `ui.design` and `ui.common`. Five more screens follow (spot detail, profile, spots, crew, settings), and demo mode will drive them with scripted time. Every screen needs the same things: a state the view renders and nothing else changes, a way to turn user actions and incoming data into that state that can be tested without Compose, and one-off events such as navigation. Without a shared shape each screen would solve this differently, and the structure would not tell a reader where a piece of logic lives.

## Decision

- **Packages, not modules.** `:shared:app` keeps one Gradle module ([ADR 0002](0002-module-structure-and-dependency-rules.md)) and is organised by package under `io.github.esseckers.pursuitofwind`:
  - `app` — `App()`, the root Koin graph (`appModules`), platform entry points;
  - `core` — shared by all screens: `core.mvi` (the MVI base), `core.designsystem` (`theme`, `component`, `icon`), `core.ui` (formatting and labels), `core.resources` (generated `Res`);
  - `feature.<screen>` — one package per screen. A feature never imports another feature; what two features share moves to `core` or to `:shared:data`.
- **Layers inside a feature.**
  - `domain`: the feature's models and the repository interface it needs (`TodayRepository`).
  - `data`: the implementation over the shared repositories of `:shared:data` (`TodayRepositoryImpl`), which stay the single source of truth.
  - `presentation`: `model` (State, Intent and UI models), `mapper` (pure functions from domain data to UI models), `view` (Route, Screen, previews), `viewmodel` (ViewModel, Result, Reducer).
  - `di`: the feature's Koin module.
- **MVI without a library.** `core.mvi.MviViewModel<State, Intent, Result, Effect>` is the base of every screen ViewModel:
  - the view renders `state: StateFlow<State>` and sends intents to `onIntent`;
  - the ViewModel turns intents, and data it observes in `bind()`, into results;
  - a pure `Reducer<State, Result>` folds each result into the next state;
  - one-off effects go out through `effects`, delivered once. A screen without them uses `Nothing`.
- **Lifecycle.** `bind()` runs while the view collects the state, and stops 5 seconds after the last collector leaves (`SharingStarted.WhileSubscribed` applied to the state's subscription count). A screen in the background stops observing data, and a configuration change does not restart it.
- **Derived state.** When a screen is computed from several inputs, as Today is from cached data, the clock and the refresh status, the state keeps those inputs (`TodayState.inputs`). The reducer records each result there and recomputes the rest with the feature's mapper, so the reducer stays pure and the mapper is tested on its own. The state then changes on every tick even when the screen does not, so the reducer keeps the previous content instance when the new one is equal, and Compose skips the unchanged cards.

## Alternatives considered

- **MVVM with several `StateFlow`s combined in the ViewModel**, as Issue #7 shipped — less code for one screen, but state changes are spread over `combine` lambdas and mutable flows with no single place to test them. It also gives no shared answer for effects.
- **An MVI library** (Orbit, MVIKotlin, FlowRedux) — mature, but each brings its own DSL, and the brief asks for plain `ViewModel` + `StateFlow`. The base class is under 100 lines, and its tests cover reducing, effects and when `bind()` starts, stays and stops.
- **Gradle module per feature** — enforced boundaries and parallel builds; for six screens and one developer the wiring costs more than it saves. [ADR 0002](0002-module-structure-and-dependency-rules.md) keeps that as the step to take when the app grows; packages laid out this way move into modules without changes.
- **Use cases in `domain` instead of a repository interface** — finer-grained, but each feature has one data need that a repository interface describes as well.

## Consequences

- Positive: every screen has the same shape, so a reader knows where to find its state rules, mapping and UI; reducers and mappers are tested with plain values on all three runtimes.
- Positive: demo mode and tests drive a screen through its repository interface and clock, without Compose.
- Negative: the view's state also carries the inputs, so it changes more often than what is shown; screens rely on the reducer keeping equal content to avoid extra recomposition.
- Negative: more files per screen (State, Intent, Result, Reducer, mapper, repository interface and implementation) than a small screen strictly needs.
- Negative: the package rule that features do not import each other is a convention, not a compile-time check, until features become modules.
- Revisit when: features need to share presentation state (then a shared store in `core`), or with the module split in [ADR 0002](0002-module-structure-and-dependency-rules.md).

## Update: mappers and Routes (October 2026)

- **The state mapper belongs to the reducer.** Only the reducer calls it; its functions take the inputs and parameters and return the state. Decisions the ViewModel acts on (which crew a ride goes to, when the car leaves) are made in the ViewModel and reach the state as results; what the ViewModel needs from the mapper (the checked profile, what the spot picker saves) comes with the state.
- **Routes live in `presentation/navigation`, previews in `presentation/preview`.** A Route connects a Screen to its ViewModel and takes the navigation lambdas; previews and their sample states serve tooling and UI tests. Both sit apart from `view`, which keeps the stateless Screens and their parts.
- **Domain types live in `domain/entity` and end in `Entity`** (`TodaySnapshotEntity`, `RideDraftEntity`). The Room rows of `:shared:data` also end in `Entity`, but they are internal to that module, so a feature never sees both.
