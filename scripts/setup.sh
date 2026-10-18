#!/usr/bin/env bash
# One-time local setup: checks the toolchain and points Gradle at the Android SDK.
# Usage: ./scripts/setup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

ok() { printf '  \033[32mok\033[0m   %s\n' "$1"; }
warn() { printf '  \033[33mwarn\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mfail\033[0m %s\n' "$1"; exit 1; }

echo "Checking toolchain"

if command -v java >/dev/null 2>&1; then
  java_major=$(java -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print v[1]}')
  if [ "${java_major:-0}" -ge 21 ]; then
    ok "JDK $java_major"
  elif [ "${java_major:-0}" -ge 17 ]; then
    warn "JDK $java_major runs Gradle; compilation uses a JDK 21 toolchain that Gradle provisions if missing"
  else
    fail "JDK ${java_major:-unknown} is too old: AGP 9 needs JDK 17+ to run Gradle. Install JDK 21 (for example: sdk install java 21-tem)."
  fi
else
  fail "No JDK found. Install JDK 21 (for example with SDKMAN: sdk install java 21-tem)."
fi

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_dir" ] && [ -d "$HOME/Library/Android/sdk" ]; then sdk_dir="$HOME/Library/Android/sdk"; fi
if [ -z "$sdk_dir" ] && [ -d "$HOME/Android/Sdk" ]; then sdk_dir="$HOME/Android/Sdk"; fi
if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
  ok "Android SDK at $sdk_dir"
  if [ ! -f local.properties ]; then
    printf 'sdk.dir=%s\n' "$sdk_dir" > local.properties
    ok "wrote local.properties"
  fi
else
  fail "Android SDK not found. Install Android Studio or set ANDROID_HOME."
fi

if [ "$(uname)" = "Darwin" ]; then
  if xcode_version=$(xcodebuild -version 2>/dev/null | head -1); then ok "$xcode_version"; else warn "Xcode not found: iOS builds will not work. Install Xcode from the App Store and run it once."; fi
else
  warn "Not macOS: iOS targets are skipped on this machine."
fi

chmod +x gradlew scripts/*.sh .claude/hooks/*.sh 2>/dev/null || true

echo "Warming up Gradle"
./gradlew --quiet help
echo "Done. Next: ./scripts/check.sh"
