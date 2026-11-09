#!/usr/bin/env bash
# Watch lane (3.8): debug code stays out of release builds.
#   TC-W-054 a deliberate leak (a class in a `debug` package under src/main) fails checkNoDebugInRelease and names the class
#   TC-W-055 the real build passes checkNoDebugInRelease, and the debug APK does contain the debug classes
#   TC-W-057 the release APK has no debug log view, receiver, sink or application class, and its manifest names none
# Run from anywhere: wear/src/test/scripts/test-debug-separation.sh
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
check() { if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
leak=wear/src/main/java/com/shlomoa/myadhdpal/wear/debug/Leak.kt
trap 'rm -f "$leak"' EXIT
dex_text() { for d in $(unzip -Z1 "$1" | grep '\.dex$'); do unzip -p "$1" "$d"; done; }

# TC-W-054
mkdir -p "$(dirname "$leak")"
printf 'package com.shlomoa.myadhdpal.wear.debug\n\nclass Leak\n' >"$leak"
out="$(./gradlew --console=plain checkNoDebugInRelease 2>&1)"; rc=$?
[ "$rc" -ne 0 ] && echo "$out" | grep -q 'wear.debug.Leak'; check "TC-W-054 deliberate leak fails the check and names the class" $?
rm -f "$leak"

# TC-W-055
./gradlew --console=plain checkNoDebugInRelease :wear:assembleDebug >/dev/null 2>&1; check "TC-W-055 real build passes checkNoDebugInRelease" $?
debug_apk="$(ls wear/build/outputs/apk/debug/*.apk | head -1)"
dex_text "$debug_apk" | grep -aq 'wear/debug/DebugLogActivity'; check "TC-W-055 debug APK contains the debug log view" $?

# TC-W-057
release_apk="$(ls wear/build/outputs/apk/release/*.apk | head -1)"
! dex_text "$release_apk" | grep -aqE 'DebugLogActivity|InjectedMessageReceiver|RingBufferLogSink|DebugApplication|wear/debug/'
check "TC-W-057 release APK has no debug classes" $?
manifest="$(find wear/build/intermediates -path '*merged_manifests/release*' -name AndroidManifest.xml | head -1)"
[ -n "$manifest" ] && ! grep -q '\.debug\.' "$manifest"; check "TC-W-057 release manifest names no debug component" $?
exit $fail
