#!/usr/bin/env bash
# Tests scripts/spike/run-0b2a.ps1 against a simulated adb/emulator (scripts/spike/fake). Needs PowerShell 7 as `pwsh`
# (or set PWSH). The simulation proves the runner's flow, parsing, report and exit codes; it cannot prove how the real
# emulators behave. Run: scripts/spike/test-run-0b2a.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }

run() { # name FAKE_FAIL args...   -> sets $rc, $tmp
  local name="$1" fakefail="$2"; shift 2
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state"
  : > "$tmp/state/items"
  for s in emulator-5554 emulator-5556; do : > "$tmp/state/$s.log"; echo dead > "$tmp/state/$s.state"; done
  touch "$tmp/state/emulator-5554.online"
  [ "${NO_WATCH:-}" = 1 ] || touch "$tmp/state/emulator-5556.online"
  FAKE_DIR="$tmp/state" FAKE_FAIL="$fakefail" PATH="$here/fake:$PATH" \
    "$PWSH" -NoProfile -File "$here/run-0b2a.ps1" -OutDir "$tmp/out" -EmulatorExe "$here/fake/emulator" \
      -PollSec 0.1 -SettleScale 0 -WaitSec 3 -NonUrgentWaitSec 3 -BootTimeoutSec 10 -ReconnectTimeoutSec 4 "$@" > "$tmp/stdout" 2>&1
  rc=$?
}
has() { grep -qE -- "$1" "$tmp/stdout"; }

run all-pass ""; check "all cases pass: exit 0" 0 $rc
has '^C1 PASS' ; check "report: C1 PASS" 0 $?
has '^C2 PASS' ; check "report: C2 PASS (C2a and C2b)" 0 $?
has 'C3 swipe-away \(am kill\) PASS, force-stop PASS' ; check "report: C3 both variants PASS" 0 $?
has 'fg=false' || has 'RECV without opening the app' ; check "report: C3 says RECV without opening the app" 0 $?
has '^C4 PASS.*median' ; check "report: C4 round trips with medians" 0 $?
has 'C5 watch-away PASS .*phone-away PASS' ; check "report: C5 both directions PASS" 0 $?
has 'sdk_gphone16k_x86_64 API 36' ; check "report: image name and API level" 0 $?
[ -s "$tmp/out/results.txt" ] && [ -s "$tmp/out/phone-C3b.log" ] && [ -s "$tmp/out/watch-C1.log" ]; check "results file and per-case logs are saved" 0 $?
grep -q 'skew' "$tmp/out/results.txt"; check "report warns that delayMs is clock-skew contaminated" 0 $?
cp "$tmp/stdout" /tmp/spike-sim-allpass.txt 2>/dev/null

run delete-fails "delete"; check "deletion not working: exit 1" 1 $rc
has '^C1 FAIL' ; check "report: C1 FAIL when deletion does not propagate" 0 $?

run stopped-no-wake "stoppedwake"; check "no wake-up after force-stop: exit 1" 1 $rc
has 'force-stop FAIL' ; check "report: C3 force-stop FAIL" 0 $?
has 'arrived only after the app was opened' ; check "report: says it arrived only after the app was opened" 0 $?

run no-recover "norecover"; check "link does not recover after restart: exit 1" 1 $rc
has 'C5 watch-away FAIL' ; check "report: C5 watch-away FAIL" 0 $?

run skip-c5 "" -SkipC5; check "-SkipC5: nothing failed but cases not run: exit 2" 2 $rc
has 'NOT RUN' ; check "report: lists NOT RUN" 0 $?

NO_WATCH=1 run no-watch ""; [ "$rc" -ne 0 ]; check "watch emulator missing: non-zero exit" 0 $?
has 'emulator-5556 is not listed' ; check "missing device is explained" 0 $?
exit $fail
