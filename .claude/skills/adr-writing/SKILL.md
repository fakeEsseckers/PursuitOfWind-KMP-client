---
name: adr-writing
description: When and how to write Architecture Decision Records for Pursuit Of Wind in docs/adr. Use when a decision affects module structure, a library choice, data flow, testing strategy or anything a reviewer would ask "why did you do it this way?" about.
---

# Writing ADRs

## When an ADR is required

- Choosing or replacing a library with project-wide impact (DI, database, navigation, networking).
- Module boundaries, dependency rules, or moving code between modules.
- A pattern other code must follow (error handling, fake server, demo isolation, platform service policy).
- Deliberately not doing something a reviewer would expect (no feature modules, no crash reporting yet, local notifications instead of push).

Small, local choices do not need an ADR; explain them in the PR description.

## File and numbering

`docs/adr/NNNN-kebab-title.md`, four-digit sequential numbers, never renumbered. Superseded ADRs stay and link to their replacement. Keep an index in `docs/adr/README.md`.

## Template

```markdown
# NNNN. Title in plain words

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
What forces are at play: product need, constraints (one developer, two weeks, KMP, server later), what hurts if we do nothing. 3–6 sentences.

## Decision
What we do, stated so someone can check the code against it. 2–5 sentences.

## Alternatives considered
- Option A — why not (one or two lines).
- Option B — why not.

## Consequences
- Positive: …
- Negative / costs: … (be honest — reviewers trust ADRs that admit trade-offs)
- When we would revisit: the concrete trigger (e.g. "more than 15 screens or a second team").
```

## Style

- English, short sentences, no marketing language.
- Name concrete things: module names, class names, numbers.
- One decision per ADR.
- Link the ADR from the README section "Engineering decisions" and from the PR that implements it.

## Planned ADRs for the client phase

Written in Issue #1: 0001 KMP + Compose Multiplatform for both UIs · 0002 Module structure for AGP 9 and enforced dependency rules · 0003 Stable dependencies inside Kotlin's tested compatibility range.

Written since: 0004 Decision engine as pure Kotlin for three runtimes · 0005 Fake backend at the HTTP layer and contract-first API · 0006 Room 3 as the offline cache · 0007 Koin for dependency injection · 0008 MVI presentation in feature packages.

Still to write: 0009 Demo mode with isolated data and a controllable clock · 0010 Local notifications until the server exists · 0011 Sharing the engine and contract with a server in a separate repository.

Add each new ADR to the index in `docs/adr/README.md`.
