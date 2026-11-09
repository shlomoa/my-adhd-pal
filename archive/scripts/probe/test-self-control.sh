#!/usr/bin/env bash
# Tests that the probe runs control themselves (scripts/probe/probe-common.ps1) against the simulated adb (scripts/probe/fake/adb):
# a question has a time limit, a banner and a beep and never blocks for ever; a step that was not confirmed is marked UNCONFIRMED in the
# file and the summary; adb verifies the state after the user pressed Enter where it can (the watch is reachable again), and asks again;
# a time limit per wait and per run ends the run, which still publishes what exists; a failed report never aborts the run.
# The simulation proves the order and the texts. It does NOT prove that the beep is heard or that Enter is pressed in time.
# Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-self-control.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
E3="$here/run-e3.ps1"
. "$here/test-lib.sh"
FAST=(-BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2)

# run [extra args]: stdin is $STDIN_MODE (answers = Enter lines, never = nobody answers); sets $rc, $tmp, $result
run() {
  make_repo
  if [ "${STDIN_MODE:-answers}" = never ]; then
    FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" \
      -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1 < <(sleep 100)
    rc=$?
  else
    yes strong | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" \
      -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1
    rc=${PIPESTATUS[2]}
  fi
  result="$(ls "$tmp/out"/e3-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls"; }
count() { calls | grep -cE -- "$1"; }

for f in "$here/probe-common.ps1"; do
  SCRIPT_FILE="$f" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
  check "TC-W-206 probe-common.ps1 parses without errors" 0 $?
done

# ---- nobody answers: every question times out, the run goes on
STDIN_MODE=never PROBE_ASK_TIMEOUT_SEC=2 PROBE_BEEP_SEC=0.5 run "${FAST[@]}"
check "TC-W-206 a run in which nobody answers exits 0" 0 $rc
[ "$(grep -c 'no answer, continued' "$result")" -ge 4 ]; check "TC-W-206 every question that timed out is logged 'no answer, continued'" 0 $?
grep -q 'ACTION NEEDED' "$tmp/stdout" && [ "$(tr -cd '\a' < "$tmp/stdout" | wc -c)" -ge 4 ]; check "TC-W-206 a banner and beeps are written while waiting" 0 $?
grep -q 'summary: step sleep-mode-on: UNCONFIRMED' "$result" && grep -q 'summary: unconfirmed steps: [1-9]' "$result"; check "TC-W-207 the file and the summary mark the steps UNCONFIRMED" 0 $?

# ---- answers are given
run "${FAST[@]}"
check "TC-W-207 a run with answers exits 0" 0 $rc
grep -q 'summary: step sleep-mode-off: CONFIRMED (the watch is reachable)' "$result" && grep -q 'summary: step power-saving-off: CONFIRMED (the watch is reachable)' "$result"; check "TC-W-207 a mode turned off is CONFIRMED when adb reaches the watch again" 0 $?
grep -q 'summary: step sleep-mode-on: ANSWERED (not verifiable by adb)' "$result"; check "TC-W-207 a mode turned on is ANSWERED, not claimed as verified" 0 $?
grep -q 'summary: unconfirmed steps: 0' "$result"; check "TC-W-207 no step is unconfirmed" 0 $?
run "${FAST[@]}" -Unattended
grep -q 'summary: step sleep-mode-on: UNCONFIRMED (unattended' "$result"; check "TC-W-207 an unattended run marks the steps UNCONFIRMED (unattended)" 0 $?

# ---- the watch is not back after Enter: asked again, then confirmed
FAKE_AWAY_AFTER=4 FAKE_AWAY_CALLS=25 run "${FAST[@]}"
check "TC-W-208 a watch that comes back only after a second try: exit 0" 0 $rc
[ "$(grep -c 'adb cannot reach the watch' "$tmp/stdout")" -ge 1 ] && grep -q 'summary: step sleep-mode-off: CONFIRMED' "$result" && grep -q '== report sleep-mode-after-off' "$result"; check "TC-W-208 the user is asked again and the step is CONFIRMED when the watch is back" 0 $?
FAKE_AWAY_AFTER=4 FAKE_AWAY_CALLS=1000 run "${FAST[@]}"
check "TC-W-208 a watch that never comes back does not fail the run (exit 0)" 0 $rc
grep -q 'summary: step sleep-mode-off: UNCONFIRMED (adb cannot reach the watch' "$result"; check "TC-W-208 the step is UNCONFIRMED when adb never reaches the watch" 0 $?

# ---- time limits
PROBE_MAX_RUN_SEC=3 run -BlockSec 2 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
[ "$rc" -eq 1 ] && grep -q 'run time limit' "$result" && grep -q 'Not pushed' "$tmp/stdout"; check "TC-W-209 a run over its time limit ends, stops the service and publishes what exists" 0 $?
[ "$(count 'cmd measure-stop')" -ge 2 ]; check "TC-W-209 the service is stopped after the time limit" 0 $?
PROBE_MAX_STEP_SEC=1 PROBE_KEEPALIVE_SEC=0.4 run -BlockSec 6 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
[ "$rc" -eq 1 ] && grep -q 'phase time limit' "$result" && grep -q 'Not pushed' "$tmp/stdout"; check "TC-W-210 a wait over its time limit ends the run and publishes what exists" 0 $?

# ---- Ctrl+C (SIGINT) publishes too
make_repo
( FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" exec "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" -SkipPush -Watch 192.168.1.16:41613 \
    -OutDir "$tmp/out" -ReportWaitSec 0.2 -BlockSec 30 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended > "$tmp/stdout" 2>&1 ) &
pid=$!
for _ in $(seq 1 60); do grep -q 'Wait\|== 30 minutes' "$tmp/out"/e3-*.txt 2>/dev/null && break; sleep 0.5; done
sleep 1; kill -INT "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
result="$(ls "$tmp/out"/e3-*.txt 2>/dev/null | head -1)"
grep -q 'Not pushed' "$tmp/stdout" && grep -q 'summary:' "$result"; check "TC-W-211 Ctrl+C ends the run, stops the service and publishes what exists" 0 $?

# ---- a failed report never aborts the run
FAKE_FAIL=reportfail run "${FAST[@]}" -Unattended
check "TC-W-212 a report that fails does not abort the run (exit 0)" 0 $rc
grep -q 'report block-10 failed' "$result"; check "TC-W-212 the failed report is logged" 0 $?
exit $fail
