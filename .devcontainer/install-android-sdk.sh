#!/usr/bin/env bash
# Installs the Android command-line tools and the SDK packages the build needs.
# Codespaces builds shared code, runs unit tests and assembles the APK; emulators and iOS do not run here.
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must be set}"
TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
# `yes` exits with SIGPIPE when sdkmanager stops reading; that is expected.
yes | "$sdkmanager" --licenses >/dev/null || true
# AGP downloads the build-tools version it needs on first build.
"$sdkmanager" "platforms;android-37.0" "platform-tools"
