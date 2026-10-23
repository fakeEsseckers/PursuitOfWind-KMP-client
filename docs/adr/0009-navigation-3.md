# 0009. Navigation 3 for moving between screens

- Status: Accepted
- Date: 2026-10-23

## Context

The second screen (spot detail, Issue #8) needs navigation. Today opens a spot at the day the rider tapped, and the rider goes back. Four more screens follow, and demo mode and invite links will open screens from a deep link. The navigation has to work the same on Android and iOS from common code, survive process death, give each screen's ViewModel the lifetime of the screen, and keep features unaware of each other ([ADR 0008](0008-mvi-presentation-in-feature-packages.md)).

Two libraries are paired with Compose Multiplatform 1.12: Navigation Compose 2.10 (`NavController`, `NavHost`) and Navigation 3 1.2 (`NavDisplay` over a back stack the app owns), both stable.

## Decision

- **Navigation 3** (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0) with `lifecycle-viewmodel-navigation3` 2.11.0.
- **Destinations** are a sealed `Destination : NavKey` in `app.navigation`, serializable with plain values (`SpotDetail(spotId, date)`). The back stack is `rememberNavBackStack(navigationConfiguration, Destination.Today)`. iOS has no reflection, so `navigationConfiguration` registers every destination; a test reads the subclasses of the sealed `Destination` and fails for one that is not registered.
- **One place connects the screens.** `AppNavigation` maps each destination to its feature's Route and passes it lambdas such as `onOpenSpot` and `onBack`. Features never see `Destination`, the back stack or each other. A `Navigator` makes the changes: it does not open a destination twice when the user taps twice, and never pops the first screen.
- **Navigation is an MVI effect** where a ViewModel decides it: Today's `OpenSpot` intent becomes a `TodayEffect.OpenSpot`, which the Route hands to `onOpenSpot`. Effects wait in the ViewModel while the screen is stopped, so the Route only passes `OpenSpot` on while the screen is resumed: a second tap during the exit animation is dropped instead of navigating again after back. Pure view events with nothing to decide, like back, call the lambda directly.
- **Each entry gets its own ViewModel store** (`rememberViewModelStoreNavEntryDecorator`) and saveable state (`rememberSaveableStateHolderNavEntryDecorator`). A spot opened twice for different days gets two ViewModels; a popped screen's ViewModel is cleared.

## Alternatives considered

- **Navigation Compose 2.10** — familiar and stable, but the back stack lives inside `NavController`, routes are resolved from serialized arguments, and a ViewModel is scoped through the controller's entries. Navigation 3 makes the back stack a list the app owns and can test without a UI, which matches the rest of the architecture: state that the app holds, changed in one place.
- **A hand-written back stack** (`mutableStateListOf` and a `when`) — no library, but predictive back, transitions, saved state per entry and ViewModel scoping would all be ours to build and keep working on both platforms.
- **Voyager / Decompose** — mature multiplatform navigation, but each brings its own component and lifecycle model next to the Jetpack ViewModel the screens already use.

## Consequences

- Positive: the back stack is plain data. Tests check navigation rules on a `MutableList`, and deep links (demo mode, invites) will build a back stack instead of driving a controller.
- Positive: system back and predictive back on Android come from `NavDisplay`; each screen still has a visible back button for iOS and screen readers.
- Negative: every new destination must be added to `navigationConfiguration` by hand; the test over the sealed class catches a missing one.
- Negative: Navigation 3 is younger than Navigation Compose, so fewer examples exist, especially for Compose Multiplatform.
- Revisit when: screens need adaptive layouts (list and detail side by side on tablets), which Navigation 3 supports through scene strategies.

## Update: onboarding and tabs (Issue #9)

- **Start.** The back stack starts at `Welcome` when no profile is cached and at `Today` otherwise (`startDestination`). The answer is read once and saved with the screen, so a restored back stack shows without waiting. Onboarding is `Welcome → SetUpProfile → PickSpots`; `finishSetup()` leaves Today as the only entry, so back from Today still leaves the app.
- **Tabs on one back stack.** Today and Profile are tabs in a bottom bar, shown while one of them is on top. Today is the root and any other tab sits on top of it (`selectTab`), so back from a tab returns to Today, as Android expects for a bottom bar. A stack per tab was considered: it keeps a tab's screens and drafts while another tab is shown, but needs several saved stacks, rules for back across them and tests for each; with two tabs and short flows, one stack is enough. The cost: choosing another tab closes screens opened on the old one, and the profile form's unsaved edits with them. Revisit when a tab gets deep flows of its own.

