# 0003. Stable dependencies inside Kotlin's tested compatibility range

- Status: Accepted
- Date: 2026-10-18

## Context

The Kotlin Multiplatform toolchain moves fast and its parts are released independently: Kotlin, Compose Multiplatform, the Android Gradle Plugin and Gradle. On 2026-10-18 the newest releases were Kotlin 2.4.21, AGP 9.4.1 and Gradle 9.8.1, but Kotlin 2.4.21 is officially tested only with AGP up to 9.3.x and Gradle up to 9.7.x. Several libraries offer alpha versions by default: the official KMP template uses Material 3 `1.11.0-alpha07` with Compose Multiplatform 1.11, while the latest stable Material 3 for Compose Multiplatform is 1.9.0. The Compose Multiplatform 1.12.2 Gradle plugin itself still resolves `compose.material3` to 1.9.0, so that pairing is the one JetBrains ships.

## Decision

- Use only stable releases (no alpha, beta, RC, milestone or EAP artifacts).
- Within that, pick the newest versions that sit inside the [Kotlin Multiplatform compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) for the chosen Kotlin version. Today: Kotlin 2.4.21, AGP 9.3.3, Gradle 9.7.1, Compose Multiplatform 1.12.2, Material 3 1.9.0.
- All versions live in `gradle/libs.versions.toml`, with the verification date at the top. Verify against the repositories, not memory, when upgrading.
- Upgrade Kotlin, AGP and Gradle together, in a dedicated pull request, when the compatibility table covers the new combination.

## Alternatives considered

- Always the newest version of everything — AGP 9.4.1 and Gradle 9.8.1 would work in most cases, but failures outside the tested range are hard to diagnose and cost days.
- Accept library alphas when a template uses them — faster access to new APIs, at the price of breaking changes between alphas.

## Consequences

- Positive: a reviewer can trace every version to a policy; build failures are less likely to be toolchain bugs.
- Negative: new APIs arrive later (for example newer Material 3 components).
- Negative: detekt stays on 1.23.8, the last stable release. It embeds the Kotlin 2.0.21 compiler, so its classpath is pinned to that version (`pursuitofwind.quality`), and it calls `ReportingExtension.file(String)`, deprecated in Gradle 9, so every build prints one deprecation warning from the detekt plugin. Both go away with detekt 2.x once it is stable.
- Revisit when: Kotlin's compatibility table lists AGP 9.4 and Gradle 9.8, or a needed API exists only in a pre-release (then record the exception here).
