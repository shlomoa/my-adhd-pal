#!/usr/bin/env bash
# Tests that E4 and E6 decide from adb alone (dumpsys power, window, activity, vibrator_manager, notification): the user types nothing and
# is asked only for physical actions (Sleep mode, Theater mode, vibration strength). Simulated adb (scripts/probe/fake/adb). The
# simulation proves the order and the texts, NOT how the real `dumpsys` output of the Galaxy Watch6 Classic looks: the raw lines are kept in
# the result file so that the analysis can read them. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-adb-answers.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
. "$here/test-lib.sh"

# run SCRIPT [args]: nobody types anything (stdin never answers); sets $rc, $tmp, $result
run() {
  local script="$1"; shift
  make_repo
  FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" PROBE_ASK_TIMEOUT_SEC=1 PROBE_BEEP_SEC=0.5 timeout 200 "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/$script" \
    -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1 < <(sleep 100)
  rc=$?
  result="$(ls "$tmp/out"/e[46]-*.txt 2>/dev/null | head -1)"
}

# ---------------- E4
PROBE_SAMPLE_SEC=0.5 run run-e4.ps1 -RunSec 2
check "TC-W-228 E4 with nobody at the keyboard exits 0" 0 $rc
! grep -q 'ACTION NEEDED' "$tmp/stdout"; check "TC-W-228 E4 asks nothing" 0 $?
! grep -q 'answer:' "$result"; check "TC-W-228 no typed answer is in the result file" 0 $?
[ "$(grep -c 'screen state sample' "$result")" -ge 4 ]; check "TC-W-228 the screen state is sampled during each run" 0 $?
[ "$(grep -c 'screen was ON for' "$result")" -eq 2 ]; check "TC-W-228 each run says how long the screen was on" 0 $?
[ "$(grep -c 'window focus:' "$result")" -ge 2 ] && grep -q 'resumed activity:.*ProbeFrontActivity' "$result"; check "TC-W-228 the window focus and the resumed activity are logged per run" 0 $?
grep -q 'summary: step run-1' "$result" && grep -q 'summary: step run-2' "$result"; check "TC-W-228 the summary has both runs" 0 $?

# ---------------- E6
run run-e6.ps1 -AlarmWaitSec 1
check "TC-W-229 E6 with nobody at the keyboard exits 0" 0 $rc
! grep -q 'answer:' "$result"; check "TC-W-229 no typed answer is in the result file" 0 $?
[ "$(grep -c 'ACTION NEEDED' "$tmp/stdout")" -eq 7 ]; check "TC-W-229 only the 7 physical actions are asked (Sleep, Theater, 3 vibration strengths)" 0 $?
[ "$(grep -c 'vibration seen by adb: yes' "$result")" -ge 9 ]; check "TC-W-229 each vibrate and each alarm route is checked with the vibrator service (yes)" 0 $?
grep -q 'vibrator: .*Vibration{' "$result"; check "TC-W-229 the raw vibrator line is kept for the analysis" 0 $?
grep -q 'window focus:.*ProbeAlertActivity' "$result"; check "TC-W-229 the window focus after the alarm shows the alert screen" 0 $?
grep -q 'screen state:' "$result"; check "TC-W-229 the screen state is logged after each route" 0 $?
FAKE_FAIL=novibrator run run-e6.ps1 -AlarmWaitSec 1
grep -q 'vibration seen by adb: no' "$result" && ! grep -q 'vibration seen by adb: yes' "$result"; check "TC-W-230 a vibrator service that lists nothing is reported as no" 0 $?
[ "$rc" -eq 0 ]; check "TC-W-230 and does not fail the run" 0 $?
exit $fail
