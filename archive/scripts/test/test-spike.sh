#!/usr/bin/env bash
# TC-S-145: the hardware spike apps live in the debug source sets only and the release builds stay free of them.
# TC-S-146: the spike runners parse and their simulated-adb test suites pass (needs PowerShell 7 as `pwsh`, or set PWSH).
# REPO_ROOT overrides the repository root (tests of the test). Release APKs are inspected when they have been built.
# POST_MERGE_ARG names the one simulated-adb suite to run (the post-merge run starts the two suites in separate groups); default: both.
set -u
root="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
PWSH="${PWSH:-pwsh}"
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}
parts="Spike SpikeActivity SpikeCommandReceiver SpikeListenerService"

for m in phone wear; do
  dir="$root/$m/src/debug/java/com/shlomoa/myadhdpal/$m/debug/spike"
  ok=0
  for c in $parts; do [ -f "$dir/$c.kt" ] || ok=1; done
  check "TC-S-145 $m: the four spike classes are in src/debug/.../$m/debug/spike" $ok

  [ -z "$(find "$root/$m/src/main" -ipath '*spike*' 2>/dev/null)" ]; check "TC-S-145 $m: nothing spike-related under src/main" $?
  ! grep -qi 'spike' "$root/$m/src/main/AndroidManifest.xml"; check "TC-S-145 $m: the main manifest does not mention the spike" $?
  for c in SpikeActivity SpikeCommandReceiver SpikeListenerService; do
    grep -q "android:name=\".debug.spike.$c\"" "$root/$m/src/debug/AndroidManifest.xml"; check "TC-S-145 $m: the debug manifest declares $c" $?
  done

  tr -d '\n' < "$root/$m/src/debug/AndroidManifest.xml" | grep -Eq 'SpikeCommandReceiver"[^>]*android:permission="android.permission.DUMP"'
  check "TC-S-145 $m: the command receiver is protected by the DUMP permission (only adb shell can send to it)" $?

  # the release check of build.gradle.kts finds a class whose package contains `debug`: the spike package qualifies
  grep -q "^package com.shlomoa.myadhdpal.$m.debug.spike" "$dir/Spike.kt"; check "TC-S-145 $m: the spike package is a debug package" $?

  apk="$(ls "$root"/$m/build/outputs/apk/release/*.apk 2>/dev/null | head -1)"
  if [ -n "$apk" ]; then
    ! unzip -p "$apk" 'classes*.dex' | strings | grep -q 'debug/spike/Spike'; check "TC-S-145 $m: the release APK has no spike class" $?
  else
    echo "INFO TC-S-145 $m: no release APK built, APK not inspected (checkNoDebugInRelease covers it in CI)"
  fi
done

[ -f "$root/scripts/spike/run-0b2a.ps1" ] && [ -f "$root/scripts/spike/run-0b2b.ps1" ]; check "TC-S-146 both runners are in scripts/spike" $?
for s in ${POST_MERGE_ARG:-test-run-0b2a test-run-0b2b}; do
  out="$(PWSH="$PWSH" "$root/scripts/spike/$s.sh" 2>&1)"; rc=$?
  echo "$out" | grep -c '^PASS' | sed "s/^/INFO TC-S-146 $s PASS lines: /"
  [ "$rc" -eq 0 ] && ! echo "$out" | grep -q '^FAIL'; check "TC-S-146 $s.sh passes" $?
  echo "$out" | grep '^FAIL' | head -5
done
exit $fail
