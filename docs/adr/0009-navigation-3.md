# 0009. Navigation 3 for moving between screens

- Status: Accepted
- Date: 2026-10-23

## Context

The second screen (spot detail, Issue #8) needs navigation. Today opens a spot at the day the rider tapped, and the rider goes back. Four more screens follow, and demo mode and invite links will open screens from a deep link. The navigation has to work the same on Android and iOS from common code, survive process death, give each screen's ViewModel the lifetime of the screen, and keep features unaware of each other ([ADR 0008](0008-mvi-presentation-in-feature-packages.md)).

Two libraries are paired with Compose Multiplatform 1.12: Navigation Compose 2.10 (`NavController`, `NavHost`) and Navigation 3 1.2 (`NavDisplay` over a back stack the app owns), both stable.

## Decision

- **Navigation 3** (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0) with `lifecycle-viewmodel-navigation3` 2.11.0.
- **Destinations** are a sealed `Destination : NavKey` in `app.navigation`, serializable with plain values (`SpotDetail(spotId, date)`). The back stack is `rememberNavBackStack(navigationConfiguration, start)`, with `start` from `startDestination` (see the update on onboarding below). iOS has no reflection, so `navigationConfiguration` registers every destination; a test reads the subclasses of the sealed `Destination` and fails for one that is not registered.
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
- **Tabs on one back stack.** Today, Spots, Crew and Profile are tabs in a bottom bar, shown while one of them is on top (now part of each tab's screen; see the last update). Today is the root and any other tab sits on top of it (`selectTab`), so back from a tab returns to Today, as Android expects for a bottom bar. A stack per tab was considered: it keeps a tab's screens and drafts while another tab is shown, but needs several saved stacks, rules for back across them and tests for each; with four tabs and short flows, one stack is enough. The cost: choosing another tab closes screens opened on the old one, and the profile form's unsaved edits with them. Revisit when a tab gets deep flows of its own.

## Update: the tab bar in the tabs, tab fades, system back (project audit)

- **The tab bar is part of each tab's screen** (`TabScreen` in `AppNavigation`), not of the screen around `NavDisplay`.
  - Before, it appeared and disappeared with the destination on top. A predictive back from Spot detail previewed Spots without its bar, and the bar popped in when the gesture completed. Now a screen opened from a tab covers the bar, and back, predictive back included, shows the tab with it.
  - The tab keeps the old layout above the bar: `TabScreen` takes the bar's height and consumes it.
  - The cost is that each tab composes its own bar. For the 250 ms of a fade there are two bars on screen and in the semantics tree, and the selection crossfades instead of Material's indicator animation.
- **Tabs are `AppTab`**, an enum in the bar's order with the destination each one shows; `selectTab` takes one.
- **Moving between tabs fades**, set in the tab entries' metadata (`NavDisplay.TransitionKey`, `PopTransitionKey`, `PredictivePopTransitionKey`).
  - `NavDisplay` takes a forward move's transition from the scene that enters and a pop's from the scene that leaves. A pop is a new stack that is a prefix of the old one.
  - So a tab that opens fades in over the one before, and on back the tab on top fades out over the one below. The bars' background stays solid; a plain crossfade would dim it halfway.
  - Finishing onboarding replaces the stack with Today, so Today fades in over the spot picker too.
  - Screens opened from a tab keep `NavDisplay`'s platform transitions.
- **Unsaved edits are dropped without asking.** This holds for back, system back and another tab alike. The forms are short, and a confirmation would stand between the rider and every back. Revisit if a form grows long.
- **Predictive back** is opted in for Android 13 to 15 (`enableOnBackInvokedCallback`), which is the default from API 36. The in-app preview needs the swipe progress, which Android sends from API 34.
- **Tests.** `AppNavigationTest` sends system back through the navigation event dispatcher, as the platforms do, and covers three things:
  - a completed back closes the screen on top and returns from a tab to Today;
  - a predictive back halfway from Spot detail shows Spots with its tab bar;
  - after the saved state is restored, the whole back stack comes back. `StateRestorationTester` does not run on desktop, so the test saves the `SaveableStateRegistry`, disposes the app and its ViewModels, and composes it again.

## Update: the tabs and Spots adapt to the window (project audit)

- **A bar on a phone, a rail on a wider window.** `TabScreen` is a `NavigationSuiteScaffold` (`material3-adaptive-navigation-suite`, which ships with Material 3 at the same version). It picks the navigation component from the window size class (`NavigationSuiteScaffoldDefaults.navigationSuiteType`): a short navigation bar at a compact width, or when the window is short or a foldable is half open; a rail along the start on a tablet, an unfolded foldable or a desktop window. The tabs stay part of each tab's screen, as above.
- **Insets.** The scaffold consumes only the navigation bar under its bar, so a form's keyboard padding would still count the bar's own height and leave an empty band above the keyboard. `TabScreen` measures what lies under the tab (the bar's height; nothing beside the rail) and consumes it. Beside the rail nothing covers the navigation bar, so `TabScreen` ends the tab above it, as the bar does on a phone: a tab's lists, buttons and snackbars never reach under the system bar. Both come from sizes, not positions: `recalculateWindowInsets` was tried and follows the tab's position, so during the iOS slide transitions a tab's content squeezed and reflowed on every frame. The cost is that beside the rail, content does not scroll behind the navigation bar.
- **Tags.** Each tab keeps its `tab.<name>` test tag. The bar's own `tabs` tag is gone: the scaffold does not take a modifier for its navigation component, and the tabs' tags say as much.
- **A spot's forecast beside the Spots list.** With room for two panes (an expanded width, and the rail), a spot opened from the Spots list shows beside it. A phone in landscape is wide enough but short, so its tabs are a bar, and a list-detail scene would put the bar under the list alone: it gets one pane (`paneDirective`). `ListDetailSceneStrategy` (`adaptive-navigation3`) builds a list-detail scene from the Spots entry, the list pane, and a `SpotDetail` opened from the list (`besideList` in its key), the detail pane. Until a spot is opened, a placeholder fills the detail pane (`NoSpotChosen`).
  - Only a forecast opened from the list is a detail pane. One opened from Today or a crew ride stays a screen of its own over its tab: a detail pane without its list would leave half the window empty.
  - Opening another spot from the list replaces the forecast on the stack (`Navigator.openBesideList`), so back returns to the list, as it does on a phone.
  - The list pane is as wide as a phone's list plus the rail. Inside the scene each pane leaves the insets of the side it does not reach to its neighbour (`PaneInsets`), and the theme's background is behind the navigation, so the gap between the panes matches the screens.
  - The forecast keeps its back arrow beside the list: it closes the forecast, as back does. Tapping Spots in the rail shows the tab alone, so it closes the forecast too.
  - Known gaps: a predictive back from the forecast beside the list shows no preview, the forecast is swapped for the placeholder when the gesture completes; and choosing Today while a forecast shows beside the list is a pop, which takes the leaving scene's transition, the forecast's: the platform's, not the tab fade. Crew and Profile fade as usual, and Spots closes the forecast in place.
  - The cost: the list-detail scene is an experimental API (`ExperimentalMaterial3AdaptiveApi`) in a stable artifact. It is opted in only in `app.navigation` (`AppNavigation`, `SpotListDetail`), so a change to it stays there.
- **Tests.**
  - `AppNavigationTest` lays the app out at a phone's size and a tablet's. The tabs are a row along the bottom on one and a column along the start on the other, with the tab beside them.
  - On a tablet, a forecast opened from Spots shows beside the list, the next one takes its place, and back returns to the list and its placeholder; it comes back beside the list after the saved state is restored. A forecast opened from Today covers the window. On a phone, in portrait or in landscape, the forecast covers the list, and a predictive back previews Spots.
  - `NavigatorTest` covers the replacement.
