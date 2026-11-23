---
name: github-issue-workflow
description: The end-to-end routine for implementing a Pursuit Of Wind GitHub issue — branch, plan, implement, verify, review with the code-reviewer subagent, Conventional Commits, PR description, and a short Russian summary for the owner. Use when starting, finishing or creating an issue or PR.
---

# GitHub issue workflow

The repository owner reads Russian; everything stored in the repo is English.

## 1. Start

1. Read the issue (GitHub MCP if connected, otherwise ask the owner to paste it).
2. Create a branch: `feat/<issue-number>-<short-slug>` (or `fix/`, `chore/`, `docs/`, `test/`).
3. Load the skills the issue touches (see the table in `CLAUDE.md`).
4. If the change touches architecture or more than 5 files, write a plan (files, approach, tests, risks) and wait for the owner's OK.

## 2. Implement

- Small vertical steps; run the relevant tests after each step.
- Contract changes follow skill `api-contract-workflow`; engine changes follow `wind-domain-rules`.
- Verify every library API against current docs (Context7) before using it.

## 3. Verify

- `./scripts/check.sh` is green.
- For UI changes: run the app and check light/dark and 200% font scale.
- Run the `code-reviewer` subagent on the diff. Fix findings or explain in the PR why they are declined.
- Optionally run the built-in `/code-review` or `/security-review` commands for a second pass.

## 4. Commit

Conventional Commits, imperative mood, English:

```text
feat(engine): bridge isolated marginal hours inside session windows
fix(data): retry token refresh once on concurrent 401s
test(engine): add DST vectors for Europe/Berlin
docs(adr): 0004 fake backend at the HTTP layer
```

Scopes: `engine`, `contract`, `data`, `fake`, `ui`, `android`, `ios`, `ci`, `docs`, `build`.

## 5. Pull request

Title = the main commit subject. Body:

```markdown
## What
One paragraph.

## Why
Link to the issue: Closes #N.

## How
Key decisions, ADR links.

## Testing
Commands run, new tests, screenshots or GIF for UI.

## Review notes
code-reviewer findings: fixed / declined (why).
```

## 6. Report to the owner (in Russian, 3–5 lines)

What was done, how to check it (command or screen), what is left or risky.

## Creating issues

Use this body for new issues (labels: `phase:client`, `area:<module>`, `priority:P0–P2`, `size:S/M/L`):

```markdown
## Goal
## Scope
## Acceptance criteria
- [ ] …
## Out of scope
## Dependencies
```

## Releasing the APK

1. All issues for the milestone closed, `main` green.
2. Tag `vX.Y.Z` → `release.yml` runs the check, publishes `windengine` and `contract` to GitHub Packages (ADR 0012), builds the release APK (R8; signed when the repository has the release key in its secrets) and publishes a GitHub Release with the APK and `openapi.yaml`. `scripts/build-release-apk.sh` is the same build locally.
3. Release notes: what is new, whether the build runs on the in-app fake backend or a server (the repository variable `API_BASE_URL`), known limitations (local notifications only, iOS build not distributed).
