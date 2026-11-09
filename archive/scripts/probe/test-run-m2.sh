#!/usr/bin/env bash
# Tests scripts/probe/run-m2.ps1 (milestone M2: which delivery variant keeps heart-rate readings arriving with the screen OFF) against the
# simulated adb (scripts/probe/fake/adb) inside a throwaway git repository with a bare "origin". It proves the order of the steps, the
# screen-off control, the per-variant numbers in the file and the failures. It does NOT prove how the Galaxy Watch6 Classic delivers
# readings in any variant: that is the user's run. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-run-m2.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
. "$here/test-lib.sh"
SCRIPT="$here/run-m2.ps1"

SCRIPT_FILE="$SCRIPT" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
check "TC-W-257 run-m2.ps1 parses without errors" 0 $?

# run [extra args]: nobody types anything (stdin closed); git origin works; sets $rc, $tmp, $result
run() {
  make_repo; git init -q --bare "$tmp/origin.git"; git -C "$tmp/repo" remote set-url origin "$tmp/origin.git"
  FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 200 "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-m2.ps1" \
    -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -PhaseSec 2 -OffTrySec 3 -OffRetrySec 0.2 -AvailableTimeoutSec 4 -ReportWaitSec 0.2 \
    -ReconnectSec 2 -ReconnectRetrySec 0.2 "$@" > "$tmp/stdout" 2>&1 < /dev/null
  rc=$?
  result="$(ls "$tmp/out"/m2-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls" 2>/dev/null; }
nth() { calls | grep -nE -- "$1" | sed -n "${2}p" | cut -d: -f1 | grep . || echo 0; }

run
check "TC-W-258 a full M2 run exits 0" 0 $rc
modes="$(calls | grep -oE 'ProbeMeasureActivity.*--es mode [a-z0-9]+' | awk '{print $NF}' | tr '\n' ' ')"
[ "$modes" = "exercise exercise5s " ]; check "TC-W-258 the probe is started in the two default modes, in order" 0 $?
[ "$(calls | grep -c 'input keyevent KEYCODE_SLEEP')" -ge 2 ]; check "TC-W-258 the screen is turned off by adb for each variant" 0 $?
[ "$(grep -c 'm2 variant .*screen=off' "$result")" -eq 2 ] && grep -q 'm2 variant exercise:' "$result" && grep -q 'm2 variant exercise5s' "$result"; check "TC-W-258 each variant has its screen=off numbers in the file" 0 $?
grep -q 'delaySec median=2.0 p95=4.0 max=8.0' "$result"; check "TC-W-258 the delay numbers are copied from the report" 0 $?
grep -q 'm2 capabilities reportingOverrides' "$result" && grep -q 'm2 capabilities passiveGoalHeartRate' "$result"; check "TC-W-258 the capabilities line (reporting overrides, passive goal data types) is in the file" 0 $?
run -Variants wakelock,exercise
modes="$(calls | grep -oE 'ProbeMeasureActivity.*--es mode [a-z0-9]+' | awk '{print $NF}' | tr '\n' ' ')"
[ "$modes" = "wakelock exercise " ]; check "TC-W-258 the wake lock control runs only when asked for with -Variants" 0 $?
! grep -q 'ACTION NEEDED' "$tmp/stdout"; check "TC-W-258 nothing is asked" 0 $?
[ "$(calls | grep -c 'cmd measure-stop')" -ge 3 ]; check "TC-W-258 the service is stopped between the variants and at the end" 0 $?
grep -q 'aod:.*restored' "$result" || ! grep -q 'aod:.*set to 0' "$result"; check "TC-W-258 the Always On Display is restored" 0 $?

FAKE_FAIL=noexercise run
check "TC-W-259 a variant that cannot start does not fail the run (exit 0)" 0 $rc
grep -q 'm2 variant exercise: NOT AVAILABLE' "$result" && grep -q 'm2 variant exercise5s: NOT AVAILABLE' "$result"; check "TC-W-259 both exercise variants are NOT AVAILABLE and the run still ends" 0 $?

FAKE_FAIL=nosleep run
check "TC-W-260 a screen that never goes off does not fail the run (exit 0)" 0 $rc
[ "$(grep -c 'screen not off' "$result")" -ge 2 ]; check "TC-W-260 each variant says the screen was not off" 0 $?
[ "$(calls | grep -c 'input keyevent KEYCODE_SLEEP')" -gt 2 ]; check "TC-W-260 the sleep key is repeated" 0 $?

run
b="$(git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/m2-results/*')"; [ -n "$b" ] && grep -q "^PUSHED $b\$" "$tmp/stdout"; check "TC-W-261 the result is published as m2-results/<stamp>" 0 $?
! grep -qE '192\.168|/tmp/' "$result"; check "TC-W-261 the file holds no ip address and no local path" 0 $?
grep -q 'summary: step exercise5s' "$result" && grep -q 'summary: run completed' "$result"; check "TC-W-261 the summary lists the variants" 0 $?
FAKE_CHARGING=1 run
[ "$rc" -eq 3 ] && ! calls | grep -q ProbeMeasureActivity; check "TC-W-261 a watch on its charger is refused by the pre-flight before any variant starts" 0 $?
exit $fail
