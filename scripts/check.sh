#!/usr/bin/env bash
# Everything the Linux CI job runs: ktlint, detekt, Android Lint, unit tests on every available target,
# the module dependency rules, coverage verification and the debug APK.
# On macOS this also runs the Kotlin tests on the iOS simulator.
# Usage: ./scripts/check.sh [extra Gradle arguments]
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew check :androidApp:assembleDebug --continue "$@"
