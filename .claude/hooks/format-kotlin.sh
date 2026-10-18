#!/usr/bin/env bash
# PostToolUse hook: formats a Kotlin file right after Claude edits or writes it.
# Non-blocking by design: it never fails the edit. If ktlint is not installed, it does nothing.
set -u

input="$(cat)"

file_path=""
if command -v jq >/dev/null 2>&1; then
  file_path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"
elif command -v python3 >/dev/null 2>&1; then
  file_path="$(printf '%s' "$input" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))
except Exception:
    print("")' 2>/dev/null)"
fi

case "$file_path" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac

[ -f "$file_path" ] || exit 0

if command -v ktlint >/dev/null 2>&1; then
  ktlint --format --relative "$file_path" >/dev/null 2>&1 || true
fi

exit 0
