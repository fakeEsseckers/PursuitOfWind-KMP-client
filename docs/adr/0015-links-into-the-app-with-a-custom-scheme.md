# 0015. Links into the app with a custom scheme

- Status: Accepted
- Date: 2026-11-12

## Context

Two things need to open the app at a place: an invitation a rider sends to a friend, and a notification about a session window ([ADR 0011](0011-local-notifications-until-the-server-exists.md)). A link arrives from outside the app (a messenger, a browser, any other app on the phone), so its text cannot be trusted. Verified `https` links (Android App Links, iOS Universal Links) need a domain that serves an association file, and there is no server yet.

## Decision

- **A custom scheme, two shapes.** `pursuitofwind://invite/{code}` and `pursuitofwind://spot/{spotId}?date=2026-10-24`. `AppLink` in `:shared:app` `core/link` is the only place that writes and reads them.
- **Read strictly.** `AppLink.parse` splits the text by hand, with no pattern matching and no decoding. A code is letters, digits and `-`, up to 20 characters; a spot id is the characters `api/openapi.yaml` allows, up to 64; a spot link takes no query or the one `date`. Anything else is no link, and its text is not logged.
- **A link changes nothing by itself.** An invitation opens the Crew tab with its code in the join dialog, and the rider confirms. A spot link opens a forecast.
- **A link waits until it can be opened.** `PendingLinks` holds one link, the latest. `AppNavigation` opens it once the rider is set up (Today is the root of the back stack), so a new rider who taps an invitation goes through onboarding first and then meets the join dialog. `Navigator.open(link)` puts the forecast on top of Today, or selects the Crew tab.
- **One entry point per platform.** The platform hands the text to `openLink(uri)`, the only public function for it. On Android `MainActivity` is `singleTop` and reads `ACTION_VIEW` data in `onCreate` (a fresh start only) and `onNewIntent`, and nothing else of the intent. A notification's tap is the same link, addressed to this app's package.
- **The shared invitation keeps the code.** Messengers do not make a custom scheme tappable everywhere, so the text carries the code to type in and the link.

## Alternatives considered

- **Verified `https` links** — the right answer once there is a domain; they cannot be claimed by another app and are tappable everywhere. Not possible without a server.
- **Join at once from an invitation link** — one tap less, but then any page or app could put a rider into a crew.
- **Pass navigation arguments as intent extras** — works for the app's own notifications only, and a second path to keep safe next to the links.

## Consequences

- Positive: invitations and notifications open the right screen on Android with one small, tested parser between the outside and the navigation.
- Positive: the link logic is common code; iOS needs only its URL scheme and one call.
- Negative: any app can register the same scheme and receive a link the rider taps outside the app. The link carries an invite code and nothing else of value; notifications address this app's package, so they cannot be intercepted this way.
- Negative: the link in a shared invitation is not tappable in every messenger.
- Revisit when: the server has a domain. Add verified `https` links and keep the custom scheme for the app's own notifications.
