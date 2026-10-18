#!/usr/bin/env bash
# Tests scripts/probe/run-screen.ps1 (controlled screen states: screen kept on, then screen off) against the simulated adb
# (scripts/probe/fake/adb) and a throwaway git repository with a bare "origin". The simulation proves the order of the steps,
# the restores, the fallback and the result file. It does NOT prove how the Galaxy Watch6 Classic behaves (the Always On Display
# key, the sleep key, `dumpsys power`, keep-screen-on): that is the user's run. Needs PowerShell 7 as `pwsh` (or set PWSH).
# Run: scripts/probe/test-run-screen.sh
set -u
export PROBE_STABLE_SEC="${PROBE_STABLE_SEC:-1}"
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
SCRIPT="$here/run-screen.ps1"

SCRIPT_FILE="$SCRIPT" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
check "TC-W-192 run-screen.ps1 parses without errors" 0 $?

# run [extra args] -> sets $rc, $tmp (repo with scripts/probe, bare origin, simulation state, result folder)
run() {
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/out"
  git init -q --bare "$tmp/origin.git"; git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"; cp -r "$here" "$tmp/repo/scripts/probe"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base; git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  yes typed | head -20 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 180 "$PWSH" -NoProfile \
    -File "$tmp/repo/scripts/probe/run-screen.ps1" -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -PhaseSec 1 -OffTrySec 3 -OffRetrySec 0.5 \
    -ReportWaitSec 0.2 -AvailableTimeoutSec 4 "$@" > "$tmp/stdout" 2>&1
  rc=${PIPESTATUS[2]}
  result="$(ls "$tmp/out"/screen-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls"; }
nth() { calls | grep -nE -- "$1" | sed -n "${2}p" | cut -d: -f1 | grep . || echo 0; }
count() { calls | grep -cE -- "$1"; }
before() { [ "$1" -gt 0 ] && [ "$2" -gt "$1" ]; }

run
check "TC-W-193 a full run with only -Watch exits 0" 0 $rc
[ -n "$result" ]; check "TC-W-193 one result file screen-<time>.txt is written" 0 $?
before "$(nth 'settings get secure doze_always_on' 1)" "$(nth 'settings put secure doze_always_on 0' 1)"; check "TC-W-193 the Always On Display key is read before it is changed" 0 $?
grep -q 'aod: secure:doze_always_on was 1, set to 0, read back 0' "$result"; check "TC-W-193 the file shows the key, the old value and the read-back" 0 $?
[ "$(cat "$tmp/state/set-secure-doze_always_on")" = 1 ]; check "TC-W-193 the Always On Display setting is back to its old value at the end" 0 $?
[ "$(count 'pm grant .*BACKGROUND')" -eq 0 ] && [ "$(count 'pm revoke .*READ_HEALTH_DATA_IN_BACKGROUND')" -ge 1 ]; check "TC-W-193 the background permission is revoked by default" 0 $?
before "$(nth 'cmd measure-clear' 1)" "$(nth 'am start .*ProbeMeasureActivity' 1)" && before "$(nth 'am start .*ProbeMeasureActivity' 1)" "$(nth 'am start .*ProbeFrontActivity.*--ez keep true' 1)"; check "TC-W-193 the service starts, then the front screen with keep-screen-on" 0 $?
before "$(nth 'am start .*ProbeFrontActivity' 1)" "$(nth 'cmd measure-report' 1)" && before "$(nth 'cmd measure-report' 1)" "$(nth 'KEYCODE_HOME' 1)" && before "$(nth 'KEYCODE_HOME' 1)" "$(nth 'KEYCODE_SLEEP' 1)" && before "$(nth 'KEYCODE_SLEEP' 1)" "$(nth 'cmd measure-report' 2)"; check "TC-W-193 order: front screen, report keep-on, home, sleep key, report screen-off" 0 $?
[ "$(count 'cmd measure-report')" -eq 2 ]; check "TC-W-193 two reports (keep-on and screen-off)" 0 $?
grep -q '== report keep-on' "$result" && grep -q '== report screen-off' "$result"; check "TC-W-193 the file names both reports" 0 $?
grep -q 'screen state before the report keep-on: ON' "$result" && grep -q 'screen state before the report screen-off: OFF' "$result"; check "TC-W-193 the screen state is logged before each report" 0 $?
grep -q 'live: .*PROBE measure live' "$result"; check "TC-W-193 the live lines are in the file" 0 $?
b="$(git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/screen-results/*')"; [ -n "$b" ] && grep -q "^PUSHED $b\$" "$tmp/stdout"; check "TC-W-196 the result is published as screen-results/<stamp>" 0 $?
! grep -qE '192\.168|/tmp/' "$result"; check "TC-W-196 the file holds no ip address and no local path" 0 $?
grep -q 'aod: secure:doze_always_on was 1' "$tmp/stdout" && ! grep -q 'Press Enter' "$tmp/stdout"; check "TC-W-193 no prompt when the Always On Display can be switched by adb" 0 $?

run -SkipPush
[ -z "$(git -C "$tmp/origin.git" for-each-ref 'refs/heads/screen-results/*')" ]; check "TC-W-196 -SkipPush publishes nothing" 0 $?

FAKE_FAIL=noaod run
check "TC-W-194 without a usable key the run goes on (exit 0)" 0 $rc
grep -q 'aod: no setting key found' "$result" && grep -q 'Settings > Display > Always On Display' "$tmp/stdout"; check "TC-W-194 the fallback asks the user, with the path" 0 $?
[ "$(count 'settings put secure doze_always_on')" -eq 0 ]; check "TC-W-194 no setting is written when no key exists" 0 $?
FAKE_FAIL=noaod run -Unattended
grep -q 'aod: no setting key found' "$result" && ! grep -q 'Press Enter' "$tmp/stdout"; check "TC-W-194 -Unattended skips the fallback prompt" 0 $?

FAKE_FAIL=nosleep run
check "TC-W-195 a screen that never goes off does not fail the run (exit 0)" 0 $rc
grep -q 'screen did not reach OFF' "$result"; check "TC-W-195 the file says the screen did not reach OFF" 0 $?
[ "$(count 'KEYCODE_SLEEP')" -ge 2 ]; check "TC-W-195 the sleep key is repeated" 0 $?
[ "$(cat "$tmp/state/set-secure-doze_always_on")" = 1 ]; check "TC-W-195 the Always On Display setting is restored after it" 0 $?

FAKE_FAIL=noavail run
[ "$rc" -ne 0 ] && [ "$(cat "$tmp/state/set-secure-doze_always_on")" = 1 ] && [ "$(count 'cmd measure-report')" -eq 0 ]; check "TC-W-195 a service that never becomes available fails the run and restores the setting" 0 $?

FAKE_CHARGING=1 run
[ "$rc" -ne 0 ] && [ "$(count 'am start')" -eq 0 ] && [ "$(count 'settings put')" -eq 0 ]; check "TC-W-195 a watch on its charger is refused before anything is changed" 0 $?

run -GrantBackgroundPermission
[ "$(count 'pm grant .*READ_HEALTH_DATA_IN_BACKGROUND')" -ge 1 ]; check "TC-W-193 -GrantBackgroundPermission grants it" 0 $?

grep -qi 'untested on the watch' "$SCRIPT"; check "TC-W-197 the script header says what is untested on the watch" 0 $?
exit $fail
