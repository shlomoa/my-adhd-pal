#!/usr/bin/env bash
# Tests scripts/spike/run-0b2b.ps1 (real devices) against a simulated adb with hardware-style serials
# (scripts/spike/fake-hw/adb: a USB serial for the phone, 192.168.x.x:port for the watch) inside a throwaway git repository
# with a bare "origin". Needs PowerShell 7 as `pwsh` (or set PWSH). The simulation proves the runner's flow, parsing, the
# readiness gate, the radio handling and restore, the report, exit codes and git publishing. It does NOT prove how the
# Galaxy A55 5G, the Galaxy Watch6 Classic or Google Play services behave. Run: scripts/spike/test-run-0b2b.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=spike-test GIT_AUTHOR_EMAIL=spike-test@invalid GIT_COMMITTER_NAME=spike-test GIT_COMMITTER_EMAIL=spike-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
WATCH=192.168.1.57:41203; PHONE=R58W20ABCDE

SPIKE_FILE="$here/run-0b2b.ps1" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SPIKE_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/tmp/spike-parse-b.txt 2>&1
check "run-0b2b.ps1 parses without errors" 0 $?

# run NAME FAKE_FAIL [extra script args]   -> sets $rc, $tmp (repo in $tmp/repo, origin in $tmp/origin.git)
run() {
  local fakefail="$2"; shift 2
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state"
  git init -q --bare "$tmp/origin.git"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/spike"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  echo "uncommitted work" > "$tmp/repo/docs/base.md"      # must survive untouched
  FAKE_DIR="$tmp/state" FAKE_FAIL="$fakefail" PATH="$tmp/repo/scripts/spike/fake-hw:$PATH" \
    timeout "${RUN_TIMEOUT:-300}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/spike/run-0b2b.ps1" -SkipBuild \
      -PollSec 0.1 -SettleScale 0 -WaitSec 3 -NonUrgentWaitSec 3 -DeletionSettleSec 0 -DelayedDeleteMs 2000 \
      -RelayedDetectSec 3 -AwayDetectSec 3 -AwayRecoverSec 4 -DozeWaitSec 3 -AdbTimeoutSec "${ADB_TIMEOUT:-5}" "$@" > "$tmp/stdout" 2>&1 < "${STDIN_FILE:-/dev/null}"
  rc=$?
}
has() { grep -qE -- "$1" "$tmp/stdout"; }
branch() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/spike-results/*' | head -1; }
radios_on() { [ "$(cat "$tmp/state/radio.bluetooth" 2>/dev/null || echo 1)" = 1 ] && [ "$(cat "$tmp/state/radio.wifi" 2>/dev/null || echo 1)" = 1 ] && [ "$(cat "$tmp/state/radio.data" 2>/dev/null || echo 1)" = 1 ]; }

# --- everything works; the watch is connected by the script (-WatchAddress); results published without device identifiers
FAKE_LEAK=1 run all-pass "" -WatchAddress $WATCH; check "all cases pass: exit 0" 0 $rc
has "Phone: samsung SM-A556E, Android 15 \(API 35\), One UI 70000" ; check "report: phone model, Android and One UI versions" 0 $?
has "Watch: samsung SM-R965F, Android 14 \(API 34\), One UI 60100" ; check "report: watch model and versions" 0 $?
has 'adb connect said: connected to' ; check "the script ran adb connect for the watch" 0 $?
has 'Readiness \(before build and install\):' && has 'Readiness \(after install\):' ; check "the readiness table is printed twice (before build, after install)" 0 $?
has '^C1 PASS' ; check "report: C1 PASS" 0 $?
has '^C1b deletion symmetry PASS' ; check "report: C1b PASS" 0 $?
has '^C2 PASS' ; check "report: C2 PASS (C2a and C2b)" 0 $?
has 'C3 app closed: kill PASS, force-stop PASS, Doze PASS, rare bucket PASS, restricted bucket PASS' ; check "report: C3 kill, force-stop, Doze and both standby buckets PASS" 0 $?
has '^L1 Nearby PASS.*median' ; check "report: L1 Nearby PASS with medians" 0 $?
has '^L2 Relayed PASS.*not nearby after' ; check "report: L2 Relayed PASS" 0 $?
has '^L3 Away PASS.*saw no phone after.*watch-del after.*phone-keep after.*fg=false.*Nearby again' ; check "report: L3 Away PASS with detection time, arrival times, listener wake and Nearby again" 0 $?
radios_on; check "the phone's Bluetooth, Wi-Fi and mobile data are on again after the run" 0 $?
[ ! -e "$tmp/state/doze" ]; check "Doze is no longer forced after the run" 0 $?
[ "$(cat "$tmp/state/bucket" 2>/dev/null)" = active ]; check "the standby bucket is back to active" 0 $?
[ ! -s "$tmp/state/unlisted-calls" ]; check "no adb call was made to a device that is not listed" 0 $?
b="$(branch)"; [ -n "$b" ] && [ "${b#spike-results/0b2b-}" != "$b" ]; check "results branch spike-results/0b2b-<timestamp> was pushed" 0 $?
files="$(git -C "$tmp/origin.git" diff-tree --no-commit-id --name-only -r "$b" 2>/dev/null)"
[ -n "$files" ] && ! echo "$files" | grep -qv '^docs/spikes/results/0b2b-'; check "the commit contains ONLY the results folder" 0 $?
echo "$files" | grep -q 'results.txt' && echo "$files" | grep -q 'phone-L3.log' && echo "$files" | grep -q 'watch-L2.log'; check "results table and raw per-case logs (L2, L3) are in the commit" 0 $?
! git -C "$tmp/origin.git" grep -q -e '@example.com' -e 'abc123secret' -e "$PHONE" -e "$WATCH" -e '192\.168\.1\.57' "$b" -- docs/spikes/results 2>/dev/null; check "e-mail, token, device serial, ip and ip:port are stripped from the published results" 0 $?
git -C "$tmp/origin.git" grep -q -e '<redacted-device>' "$b" -- docs/spikes/results 2>/dev/null; check "the redaction markers are present (the leak was injected and caught)" 0 $?
[ "$(git -C "$tmp/repo" branch --show-current)" = main ] && [ "$(cat "$tmp/repo/docs/base.md")" = "uncommitted work" ]; check "the working branch and uncommitted files are untouched" 0 $?
[ -z "$(git -C "$tmp/repo" worktree list | grep -v "$tmp/repo ")" ]; check "the temporary worktree is removed" 0 $?

# --- readiness gate: names the device, the reason and what to do; exit 2; nothing changed, no results folder
run unauth "unauth" -WatchAddress $WATCH; check "phone unauthorized: exit 2" 2 $rc
has 'phone +adb: +unauthorized.*NOT READY: adb state is unauthorized' ; check "the table names the phone and the reason" 0 $?
has 'Do this: Unlock the device and tap Allow' ; check "the message says what to do" 0 $?
[ ! -d "$tmp/repo/docs/spikes/results" ]; check "no results folder is left behind" 0 $?
run nowatch "nowatch"; check "watch never listed: exit 2" 2 $rc
has 'no watch found in adb devices' && has 'Wireless debugging' ; check "the watch message explains Wireless debugging and adb connect" 0 $?
ADB_TIMEOUT=2 run deadwatch "deadwatch" -WatchAddress $WATCH; check "watch that never answers: exit 2" 2 $rc
has 'adb calls to the watch time out' ; check "a timeout is reported as a timeout" 0 $?
! has 'not installed' ; check "a timeout is not reported as 'not installed'" 0 $?
run lowbattery "lowbattery" -WatchAddress $WATCH; check "watch at 5 percent: exit 2" 2 $rc
has 'watch: battery is at 5%' ; check "the low battery is named" 0 $?
run nodevices "nowatch" -Phone NOPE123; check "an explicit serial that is not connected: exit 2" 2 $rc
has 'phone.*adb: +not listed' ; check "the explicit missing serial is reported as not listed" 0 $?

# --- radios that adb cannot switch: manual step, or NOT RUN with -NonInteractive; always restored
run btfail-noninteractive "btfail" -WatchAddress $WATCH -OnlyCases L2,L3 -NonInteractive; check "Bluetooth cannot be switched, -NonInteractive: exit 2" 2 $rc
has '^L2 Relayed NOT RUN.*Bluetooth could not be switched off' ; check "L2 NOT RUN with the reason" 0 $?
has '^L3 Away NOT RUN' ; check "L3 NOT RUN with the reason" 0 $?
radios_on; check "radios are on after a run that could not change them" 0 $?
printf 's\ns\n' > "$tmp/stdin"; STDIN_FILE="$tmp/stdin" run btfail-skip "btfail" -WatchAddress $WATCH -OnlyCases L2
has "MANUAL STEP: adb could not switch the phone's Bluetooth OFF. Swipe down on the phone and set Bluetooth to OFF" ; check "interactive: the manual step names exactly what to tap" 0 $?
check "interactive: skipping the manual step gives exit 2" 2 $rc

# --- states that cannot be observed are findings (FAIL), not silent passes
run norelay "norelay" -WatchAddress $WATCH -OnlyCases L2; check "Relayed not observable: exit 1" 1 $rc
has '^L2 Relayed FAIL.*did not report the phone as a connected, not-nearby node' ; check "L2 FAIL explains what was not seen" 0 $?
radios_on; check "radios restored after the L2 failure" 0 $?
run noaway "noaway" -WatchAddress $WATCH -OnlyCases L3; check "Away not observable: exit 1" 1 $rc
has '^L3 Away FAIL.*Away was not observable' ; check "L3 FAIL says Away was not observable" 0 $?
has 'C2b: (NOT RUN|PASS|FAIL)' ; check "C2b is reported after L3" 0 $?
radios_on; check "radios restored after the L3 failure" 0 $?
run slowrtt "slowrtt" -WatchAddress $WATCH -OnlyCases L1; check "urgent round trip above the nearby target: exit 1" 1 $rc
has '^L1 Nearby FAIL.*above the target 10000 ms' ; check "L1 FAIL names the target" 0 $?
run dozeblock "dozeblock" -WatchAddress $WATCH -OnlyCases C3c; check "items held back under Doze: exit 1" 1 $rc
has 'Doze FAIL.*not received within' ; check "C3c FAIL: the urgent item did not arrive" 0 $?
run nodoze "nodoze" -WatchAddress $WATCH -OnlyCases C3c; check "Doze cannot be forced: exit 2" 2 $rc
has 'could not force Doze' ; check "C3c NOT RUN explains it" 0 $?
run bucketfail "bucketfail" -WatchAddress $WATCH -OnlyCases C3d; check "standby bucket cannot be set: exit 2" 2 $rc
has 'could not set the standby bucket to rare' ; check "C3d NOT RUN explains it" 0 $?

# --- -RestorePhone and pairing
tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/repo/scripts"; cp -r "$here" "$tmp/repo/scripts/spike"; git init -q -b main "$tmp/repo"
echo 0 > "$tmp/state/radio.bluetooth"; echo 0 > "$tmp/state/radio.wifi"; echo 0 > "$tmp/state/radio.data"; touch "$tmp/state/doze"
FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/spike/fake-hw:$PATH" "$PWSH" -NoProfile -File "$tmp/repo/scripts/spike/run-0b2b.ps1" -RestorePhone > "$tmp/stdout" 2>&1; rc=$?
check "-RestorePhone: exit 0" 0 $rc
radios_on && [ ! -e "$tmp/state/doze" ]; check "-RestorePhone switches the radios back on and ends Doze" 0 $?
run pair-ok "" -PairWatch $WATCH -PairCode 123456 -WatchAddress $WATCH -OnlyCases C1; check "-PairWatch with the right code: exit 0" 0 $rc
has 'adb pair said: Successfully paired' ; check "pairing is reported" 0 $?
run pair-bad "" -PairWatch $WATCH -PairCode 000000 -WatchAddress $WATCH -OnlyCases C1; check "-PairWatch with a wrong code: exit 3" 3 $rc
has 'adb pair did not succeed' ; check "the pairing failure says the code and port change each time" 0 $?
run unknown-case "" -OnlyCases C9; check "unknown case name: exit 3" 3 $rc

[ "$fail" = 0 ] && echo "ALL PASS" || echo "SOME FAILED"
exit "$fail"
