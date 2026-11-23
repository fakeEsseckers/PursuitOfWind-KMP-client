# 0012. Sharing the engine and the contract with the server

- Status: Accepted
- Date: 2026-11-23

## Context

The server lives in a separate repository. It must reach the same verdicts as the app, because it will tell riders about windows the app then shows, and it must speak the API exactly as the app does. Both already exist here as modules that depend on nothing else in the project and compile for the JVM: `:shared:windengine` (the decision engine, [ADR 0004](0004-decision-engine-as-pure-kotlin.md)) and `:shared:contract` (the DTOs, routes and limits of `api/openapi.yaml`, [ADR 0005](0005-contract-first-api-with-in-app-fake-backend.md)). The server needs them by version, without a copy of this repository.

## Decision

- **Maven artifacts, one version per tag.** A tag `vX.Y.Z` publishes `io.github.esseckers.pursuitofwind:windengine:X.Y.Z` and `…:contract:X.Y.Z` to GitHub Packages of this repository (`release.yml`). The server depends on the two coordinates; Gradle picks their JVM variant.
- **The specification travels with the same tag.** `api/openapi.yaml` is attached to the tag's GitHub Release as `openapi-X.Y.Z.yaml`. One tag names the engine, the Kotlin types of the contract and the specification they mirror.
- **One convention plugin, no credentials in the build.** `pursuitofwind.publish` sets the group and the version (`-Ppursuitofwind.version`), and adds one remote repository: GitHub Packages of the repository a workflow runs in, with that workflow's own token. Outside GitHub Actions there is no remote repository, only `publishToMavenLocal`.
- **Every target from the Linux release job.** Neither module has C interop, so Kotlin builds their iOS libraries on Linux too, and one job publishes Android, iOS and JVM consistently.
- **Semantic versions.** A change of a decision rule, or a new optional field or endpoint, is a minor version. A breaking change of the contract is a major version and `/v2` (`api/CHANGELOG.md`). The server pins a version and moves on purpose.
- **A composite build while both sides change.** During development the server may use `includeBuild("../PursuitOfWind-KMP-client")` with dependency substitution in place of the published versions; a release of the server never does.

## Alternatives considered

- **Copy the sources, or a git submodule** — the two copies drift, and the server could decide differently from the app without anyone noticing.
- **Maven Central** — the right home once someone outside the project consumes the artifacts; it needs a verified namespace and signing, which nothing here requires yet.
- **A composite build only** — no versions, and the server's CI would need this repository checked out at a matching commit.
- **One repository for client and server** — rejected by the project's plan: the client is a showcase of its own.

## Consequences

- Positive: the server computes windows with the code the app runs, and decodes the bytes the app sends with the types the app uses.
- Positive: checked from the outside. A separate JVM project that depends only on the two coordinates compiles and runs against artifacts from `publishToMavenLocal`.
- Negative: GitHub Packages asks for a token even to read a public package, so the server's build and CI need one with `read:packages`.
- Negative: a rule change reaches riders in two steps, an app release and a server release, and for a while the two can disagree. The server's verdict is the one riders are told about; the app recomputes from the forecast it fetched.
- Revisit when: someone outside the project needs the artifacts (move to Maven Central), or the two repositories release so often together that one build serves them better.
