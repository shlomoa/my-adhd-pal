#!/usr/bin/env bash
# TC-S-029: from a fresh state (no Android SDK), run the bootstrap script, then ./gradlew check.
# Uses the real network and downloads about 200 MB, so it is not part of the offline test run.
# Run: scripts/test/fresh-bootstrap-check.sh   (FRESH_SDK_DIR overrides the temporary SDK location)
set -eu
root="$(cd "$(dirname "$0")/../.." && pwd)"
sdk="${FRESH_SDK_DIR:-$(mktemp -d "$HOME/fresh-android-sdk.XXXXXX")}"
[ -z "$(ls -A "$sdk" 2>/dev/null)" ] || { echo "TC-S-029 needs an empty SDK dir, $sdk is not empty" >&2; exit 2; }
echo "TC-S-029: bootstrapping a fresh SDK in $sdk"
ANDROID_HOME="$sdk" "$root/scripts/bootstrap-android.sh"
cd "$root"
ANDROID_HOME="$sdk" ./gradlew check
echo "PASS TC-S-029 fresh bootstrap then ./gradlew check"
