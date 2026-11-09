#!/usr/bin/env bash
# Setup check (step 2). Run on the computer the watch and phone are connected to over adb.
# Lists the connected devices, tells the watch from the phone, prints model, Android version, API level and the
# heart-rate sensor lines as the device reports them, then writes setup/results/<timestamp>.txt.
# With --push it also commits that one file and pushes it to the current branch.
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
command -v adb >/dev/null 2>&1 || { echo "adb not found on PATH (install Android platform-tools)"; exit 1; }

push=0
[ "${1:-}" = "--push" ] && push=1
out="setup/results/$(date +%Y%m%d-%H%M%S).txt"
mkdir -p setup/results
exec > >(tee "$out") 2>&1

echo "ADHD Pal POC setup check, $(date -u +%FT%TZ)"
serials="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$serials" ] || { echo "RESULT: no device in state 'device' (check cable/Wi-Fi pairing and the authorisation prompt)"; exit 2; }

watch=0
phone=0
for s in $serials; do
  prop() { adb -s "$s" shell getprop "$1" | tr -d '\r'; }
  chars="$(prop ro.build.characteristics)"
  kind=phone
  case "$chars" in *watch*) kind=watch ;; esac
  echo
  echo "== $s ($kind)"
  echo "model:       $(prop ro.product.model)"
  echo "android:     $(prop ro.build.version.release) (API $(prop ro.build.version.sdk))"
  echo "build:       $(prop ro.build.display.id)"
  echo "characteristics: $chars"
  if [ "$kind" = watch ]; then
    watch=$((watch + 1))
    echo "-- heart-rate sensor lines from 'dumpsys sensorservice' (raw, as reported):"
    adb -s "$s" shell dumpsys sensorservice | tr -d '\r' | grep -i -E "heart|hr" | head -20
    echo "-- feature android.hardware.sensor.heartrate:"
    adb -s "$s" shell pm list features | tr -d '\r' | grep -i heartrate || echo "(not listed)"
  else
    phone=$((phone + 1))
    echo "-- Google Play services: $(adb -s "$s" shell dumpsys package com.google.android.gms | tr -d '\r' | grep -m1 versionName || echo 'not found')"
  fi
done
echo
echo "RESULT: watches=$watch phones=$phone (done when both are 1 and the watch shows a heart-rate sensor)"

if [ "$push" = 1 ]; then
  git add "$out" && git commit -q -m "Setup check result $(basename "$out")" -- "$out" && git push origin HEAD
fi
