#!/usr/bin/env bash
# Builds what a release ships and collects it in dist/: the release APK (R8), the API contract it was built against
# and, for a build that talks to a server, the App Bundle Google Play takes.
#
# The APK and the bundle are signed when the release key is in the environment, as in release.yml:
#   RELEASE_KEYSTORE_BASE64    the keystore file, base64-encoded
#   RELEASE_KEYSTORE_PASSWORD  its password
#   RELEASE_KEY_ALIAS          the key's alias
#   RELEASE_KEY_PASSWORD       the key's password
# Without them the APK is built unsigned (it cannot be installed) and named so, which still proves the build.
#
# Optional:
#   API_BASE_URL   the server's origin for this build; without it the app runs on its in-app fake backend
#   VERSION_CODE   the build number, which only grows from one release to the next (default 1)
#
# Usage: ./scripts/build-release-apk.sh [version]
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:-0.0.0}"
tasks=(":androidApp:assembleRelease")
args=("-Ppursuitofwind.version=$version" "-Ppursuitofwind.versionCode=${VERSION_CODE:-1}")
if [ -n "${API_BASE_URL:-}" ]; then
  args+=("-Ppursuitofwind.apiBaseUrl=$API_BASE_URL")
  # A bundle for the store only makes sense for a build that talks to a server.
  tasks+=(":androidApp:bundleRelease")
fi

keystore_dir=""
cleanup() { if [ -n "$keystore_dir" ]; then rm -rf "$keystore_dir"; fi; }
trap cleanup EXIT

if [ -n "${RELEASE_KEYSTORE_BASE64:-}" ]; then
  keystore_dir="$(mktemp -d)"
  printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > "$keystore_dir/release.jks"
  export RELEASE_KEYSTORE_FILE="$keystore_dir/release.jks"
fi

./gradlew "${tasks[@]}" "${args[@]}"

apk="androidApp/build/outputs/apk/release"
rm -rf dist
mkdir -p dist
if [ -f "$apk/androidApp-release.apk" ]; then
  cp "$apk/androidApp-release.apk" "dist/pursuit-of-wind-$version.apk"
else
  cp "$apk/androidApp-release-unsigned.apk" "dist/pursuit-of-wind-$version-unsigned.apk"
  echo "No release key in the environment: the APK is unsigned and cannot be installed." >&2
fi
bundle="androidApp/build/outputs/bundle/release/androidApp-release.aab"
if [ -f "$bundle" ] && [ -n "${API_BASE_URL:-}" ]; then cp "$bundle" "dist/pursuit-of-wind-$version.aab"; fi
cp api/openapi.yaml "dist/openapi-$version.yaml"
ls -l dist
