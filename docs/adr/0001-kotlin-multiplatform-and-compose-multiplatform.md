# 0001. Kotlin Multiplatform with Compose Multiplatform for both UIs

- Status: Accepted
- Date: 2026-10-18

## Context

Pursuit Of Wind ships on Android and iOS and is built by one developer in about two weeks per phase. The product's core is a decision engine (verdicts, session windows, kite sizing) that must behave identically on both platforms and, later, on the server that sends notifications. The screens are few (six) and mostly custom: a verdict card, a wind timeline drawn on a canvas, simple forms.

## Decision

All logic and UI live in Kotlin Multiplatform modules under `shared/`. The UI is written once in Compose Multiplatform (`:shared:app`) and hosted by thin native shells: `:androidApp` and the `iosApp` Xcode project (a SwiftUI `App` wrapping `ComposeUIViewController`). Platform services that have no common API (notifications, background work, secure storage, sharing) are interfaces in common code with Android and iOS implementations.

## Alternatives considered

- Kotlin Multiplatform for logic, SwiftUI for the iOS UI — more native feel, but a second UI for six screens costs 3–4 days with no user-visible gain in this phase.
- Separate native apps — the decision engine would exist twice and drift.
- Flutter or React Native — the engine would be shared, but not with a Kotlin server, and the project is meant to show Kotlin and Android depth.

## Consequences

- Positive: one implementation of the engine and the screens; the engine and API DTOs also compile for the JVM and are reused by the server.
- Positive: iOS-specific code stays small and explicit (`iosMain`, `iosApp/`).
- Negative: iOS UI is less native in details such as text selection and navigation transitions; the iOS framework is larger.
- Negative: iOS builds need macOS and Xcode; Linux CI covers everything else.
- Revisit when: an iOS screen needs platform UI that Compose Multiplatform cannot provide well (then write that screen in SwiftUI), or the iOS app grows a separate team.
