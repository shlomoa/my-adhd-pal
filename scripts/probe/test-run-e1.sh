#!/usr/bin/env bash
# Tests scripts/probe/run-e1.ps1 (experiment E1, issue 228) against a simulated adb (scripts/probe/fake/adb). The simulation
# proves the script's order of steps, its restore of the screen timeout, its reconnects and its result file. It does NOT prove
# how the Galaxy Watch6 Classic behaves (key events, Always-on display, dumpsys output): that is the user's run.
# Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-run-e1.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
SCRIPT="$here/run-e1.ps1"
. "$here/test-lib.sh"

SCRIPT_FILE="$SCRIPT" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
check "TC-W-153 run-e1.ps1 parses without errors" 0 $?

# run NAME [extra script args]  -> sets $rc, $tmp ($tmp/state = simulation, $tmp/out = result folder)
run() {
  shift
  make_repo
  FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-120}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e1.ps1" \
    -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -PhaseSec 1 -ReportWaitSec 0.2 -AvailableTimeoutSec 4 -Unattended "$@" > "$tmp/stdout" 2>&1 < /dev/null
  rc=$?
  result="$(ls "$tmp/out"/e1-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls"; }
# line number of the first call matching an extended regex, or 0
first() { calls | grep -nE -- "$1" | head -1 | cut -d: -f1 | grep . || echo 0; }
nth() { calls | grep -nE -- "$1" | sed -n "${2}p" | cut -d: -f1 | grep . || echo 0; }
count() { calls | grep -cE -- "$1"; }
before() { [ "$1" -gt 0 ] && [ "$2" -gt "$1" ]; }

run all ""
check "TC-W-154 a full run exits 0" 0 $rc
[ -n "$result" ]; check "TC-W-154 one result file e1-<time>.txt is written" 0 $?
before "$(first 'logcat.* -G 4M')" "$(first 'am start')"; check "TC-W-154 the log buffer is enlarged (-G 4M) before the service starts" 0 $?
before "$(first 'settings get system screen_off_timeout')" "$(first 'settings put system screen_off_timeout [0-9]')"; check "TC-W-154 the screen timeout is read before it is changed" 0 $?
[ "$(cat "$tmp/state/timeout")" = 30000 ]; check "TC-W-154 the screen timeout is back to its original value at the end" 0 $?
[ "$(count 'pm revoke .*READ_HEALTH_DATA_IN_BACKGROUND')" -ge 1 ] && [ "$(count 'pm revoke .*BODY_SENSORS_BACKGROUND')" -ge 1 ]; check "TC-W-154 both background permissions are revoked (revoke errors are tolerated)" 0 $?
[ "$(count 'pm grant .*READ_HEART_RATE')" -eq 1 ] && [ "$(count 'pm grant .*POST_NOTIFICATIONS')" -eq 1 ]; check "TC-W-154 READ_HEART_RATE and POST_NOTIFICATIONS are granted" 0 $?
! calls | grep -qE 'pm grant .*(BACKGROUND)'; check "TC-W-154 no background permission is ever granted" 0 $?
before "$(first 'cmd measure-stop')" "$(first 'cmd measure-clear')" && before "$(first 'cmd measure-clear')" "$(first 'am start')"; check "TC-W-154 measure-stop and measure-clear come before the service is started" 0 $?
before "$(first 'am start')" "$(first 'KEYCODE_WAKEUP')"; check "TC-W-154 the screen is woken after the service started" 0 $?
[ "$(count 'cmd measure-report')" -eq 3 ]; check "TC-W-154 exactly three reports (phases A, B, C)" 0 $?
[ "$(count 'KEYCODE_SLEEP')" -eq 2 ]; check "TC-W-154 the screen is put to sleep twice (phases B and C)" 0 $?
before "$(first 'KEYCODE_WAKEUP')" "$(nth 'cmd measure-report' 1)" && before "$(nth 'cmd measure-report' 1)" "$(nth 'KEYCODE_SLEEP' 1)" \
  && before "$(nth 'KEYCODE_SLEEP' 1)" "$(nth 'cmd measure-report' 2)" && before "$(nth 'cmd measure-report' 2)" "$(nth 'KEYCODE_SLEEP' 2)" \
  && before "$(nth 'KEYCODE_SLEEP' 2)" "$(nth 'cmd measure-report' 3)"; check "TC-W-154 order: wake, report A, sleep, report B, sleep, report C" 0 $?
ok=0
for i in 1 2 3; do
  r="$(nth 'cmd measure-report' $i)"
  [ "$(calls | sed -n "$((r-2))p")" = "connect 192.168.1.16:41613" ] && calls | sed -n "$((r-1))p" | grep -q 'logcat -c' || ok=1
done
check "TC-W-155 before every report: adb connect, then logcat -c, then the report" 0 $ok
before "$(nth 'cmd measure-report' 3)" "$(nth 'cmd measure-stop' 2)"; check "TC-W-154 measure-stop is the last service command" 0 $?
[ "$(count 'dumpsys battery')" -ge 2 ]; check "TC-W-154 the battery level is read before and after" 0 $?
grep -q 'battery before: 68' "$result" && grep -qE 'battery after: [0-9]+' "$result"; check "TC-W-154 the result file has the battery before and after" 0 $?
[ "$(grep -c '== report' "$result")" -eq 3 ]; check "TC-W-154 the result file has three report sections" 0 $?
[ "$(grep -c 'PROBE measure screen=all' "$result")" -eq 3 ]; check "TC-W-154 each report section holds the PROBE measure lines (not older ones)" 0 $?
grep -qE '^\[?[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9:]{8}' "$result"; check "TC-W-154 result lines carry a timestamp" 0 $?
grep -q 'notification visible: True' "$result"; check "TC-W-154 the result file says the notification was visible" 0 $?
grep -q 'result file' "$tmp/stdout" && grep -q "$tmp/out" "$tmp/stdout"; check "TC-W-154 the script prints where the result file is" 0 $?
! grep -q '192.168' "$result"; check "TC-W-154 the result file holds no ip address" 0 $?

FAKE_DROP=1 run drop ""
check "TC-W-155 a connection that drops when the screen sleeps does not stop the run" 0 $rc
[ "$(grep -c 'PROBE measure screen=all' "$result")" -eq 3 ]; check "TC-W-155 all three reports are captured after the drops" 0 $?
[ "$(cat "$tmp/state/timeout")" = 30000 ]; check "TC-W-155 the screen timeout is restored after the drops" 0 $?

FAKE_FAIL=noavail run noavail ""
[ "$rc" -ne 0 ]; check "TC-W-156 a service that never reports AVAILABLE fails the run" 0 $?
[ "$(cat "$tmp/state/timeout")" = 30000 ]; check "TC-W-156 the screen timeout is restored after a failed run" 0 $?
[ "$(count 'cmd measure-stop')" -ge 2 ] ; check "TC-W-156 the service is stopped after a failed run" 0 $?
[ "$(count 'cmd measure-report')" -eq 0 ]; check "TC-W-156 no report is taken when the service never became available" 0 $?

FAKE_FAIL=unavail run unavail ""
[ "$rc" -ne 0 ]; check "TC-W-156 UNAVAILABLE is not taken for AVAILABLE" 0 $?

FAKE_CHARGING=1 run charging ""
[ "$rc" -ne 0 ] && [ "$(count 'am start')" -eq 0 ]; check "TC-W-157 a watch on its charger is refused before anything is started" 0 $?
grep -qi 'charger' "$tmp/stdout"; check "TC-W-157 the refusal names the charger" 0 $?
FAKE_CHARGING=1 run charging2 "" -AllowCharging
check "TC-W-157 -AllowCharging runs anyway" 0 $rc

FAKE_FAIL=nonotif run nonotif ""
grep -q 'notification visible: False' "$result"; check "TC-W-154 a missing notification is reported as False" 0 $?

# without -Unattended the script asks about the Always-on display before phase B and phase C
make_repo
printf '\n\n' | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 120 "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e1.ps1" -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -PhaseSec 1 -ReportWaitSec 0.2 -AvailableTimeoutSec 4 > "$tmp/stdout" 2>&1
[ "$(grep -ci 'always.on display' "$tmp/stdout")" -ge 2 ]; check "TC-W-158 the script asks the user about the Always-on display twice (on before B, off before C)" 0 $?

for want in 'Settings > Display > Always On Display'; do grep -q "$want" "$tmp/stdout"; check "TC-W-183 the E1 prompts carry the verified path: $want" 0 $?; done

# the documentation of the script states what the simulation cannot prove
grep -qi 'untested on the watch' "$SCRIPT"; check "TC-W-159 the script header says what is untested on the watch" 0 $?
exit $fail
