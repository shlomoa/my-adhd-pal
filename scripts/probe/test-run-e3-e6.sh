#!/usr/bin/env bash
# Tests scripts/probe/run-e3.ps1 and run-e6.ps1 (experiments E3 issue 230, E6 issue 233) against the simulated adb
# (scripts/probe/fake/adb). The simulation proves the order of the steps, the restores, the reconnects, the questions and the
# result files. It does NOT prove how the Galaxy Watch6 Classic behaves (Bedtime, battery saver, Do Not Disturb, vibration,
# `dumpsys` output): that is the user's run. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-run-e3-e6.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
E3="$here/run-e3.ps1"; E6="$here/run-e6.ps1"
. "$here/test-lib.sh"

for f in "$E3" "$E6" "$here/probe-common.ps1"; do
  SCRIPT_FILE="$f" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
  check "TC-W-160 $(basename "$f") parses without errors" 0 $?
done

# run SCRIPT [extra args]  -> sets $rc, $tmp ($tmp/state = simulation, $tmp/out = result folder); answers come from stdin
run() {
  local script
  script="$(basename "$1")"; shift
  make_repo
  yes "${STDIN_WORD:-strong}" | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-180}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/$script" \
    -SkipPush -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1
  rc=${PIPESTATUS[2]}
  result="$(ls "$tmp/out"/e[36]-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls"; }
nth() { calls | grep -nE -- "$1" | sed -n "${2}p" | cut -d: -f1 | grep . || echo 0; }
first() { nth "$1" 1; }
count() { calls | grep -cE -- "$1"; }
before() { [ "$1" -gt 0 ] && [ "$2" -gt "$1" ]; }

# ---------------- E3
run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-161 a full E3 run exits 0" 0 $rc
[ -n "$result" ]; check "TC-W-161 one result file e3-<time>.txt is written" 0 $?
[ "$(count 'pm revoke .*READ_HEALTH_DATA_IN_BACKGROUND')" -ge 1 ] && ! calls | grep -qE 'pm grant .*BACKGROUND'; check "TC-W-161 the background permission is revoked and never granted" 0 $?
grep -q 'READ_HEALTH_DATA_IN_BACKGROUND: granted=false' "$result"; check "TC-W-161 the result file shows the background permission is not granted" 0 $?
! calls | grep -q 'settings put'; check "TC-W-161 E3 leaves the screen timeout alone (the screen behaves as it comes)" 0 $?
before "$(first 'cmd measure-stop')" "$(first 'cmd measure-clear')" && before "$(first 'cmd measure-clear')" "$(first 'am start')"; check "TC-W-161 measure-stop and measure-clear come before the start" 0 $?
[ "$(count 'cmd measure-report')" -eq 7 ]; check "TC-W-161 seven reports: 3 in the 30-minute block, 2 for Sleep mode, 2 for power saving" 0 $?
ok=0
for i in 1 2 3 4 5 6 7; do
  r="$(nth 'cmd measure-report' $i)"
  [ "$(calls | sed -n "$((r-3))p")" = "connect 192.168.1.16:41613" ] && calls | sed -n "$((r-2))p" | grep -q 'logcat -d' && calls | sed -n "$((r-1))p" | grep -q 'logcat -c' || ok=1
done
check "TC-W-161 before every report: adb connect, the live lines are read (logcat -d), then logcat -c, then the report" 0 $ok
grep -q '== report block-10' "$result" && grep -q '== report block-30' "$result" && grep -q '== report sleep-mode-before' "$result" && grep -q '== report sleep-mode-end' "$result" && grep -q '== report power-saving-before' "$result" && grep -q '== report power-saving-after-off' "$result"; check "TC-W-161 the result file names each report" 0 $?
! grep -q '== report power-saving-end' "$result"; check "TC-W-180 no report is taken while power saving is on (it turns Wi-Fi off)" 0 $?
grep -q 'service running: True' "$result" && grep -q 'readings grew: True' "$result"; check "TC-W-161 the result file says the service runs and readings grew" 0 $?
grep -q 'battery before: 68' "$result" && grep -qE 'battery after: [0-9]+' "$result"; check "TC-W-161 the battery before and after are in the result file" 0 $?
before "$(nth 'cmd measure-report' 7)" "$(nth 'cmd measure-stop' 2)"; check "TC-W-161 measure-stop is the last service command" 0 $?
! grep -q '192.168' "$result"; check "TC-W-161 the result file holds no ip address" 0 $?

FAKE_FAIL=svcgone run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-162 a service that disappears is a result, not a script failure (exit 0)" 0 $rc
grep -q 'service running: False' "$result"; check "TC-W-162 the result file says service running: False" 0 $?

FAKE_FAIL=stalled run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
grep -q 'readings grew: False' "$result"; check "TC-W-162 readings that stop growing are reported (readings grew: False)" 0 $?

FAKE_FAIL=noavail run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
[ "$rc" -ne 0 ] && [ "$(count 'cmd measure-report')" -eq 0 ] && [ "$(count 'cmd measure-stop')" -ge 2 ]; check "TC-W-163 a service that never reports AVAILABLE fails the run and is stopped" 0 $?

FAKE_CHARGING=1 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
[ "$rc" -ne 0 ] && [ "$(count 'am start')" -eq 0 ]; check "TC-W-163 a watch on its charger is refused" 0 $?

run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2
[ "$(grep -ci 'Sleep mode' "$tmp/stdout")" -ge 2 ] && [ "$(grep -ci 'Power saving' "$tmp/stdout")" -ge 2 ]; check "TC-W-164 without -Unattended the script asks the user to turn Sleep mode and Power saving on and off" 0 $?
for want in 'Quick panel' 'Modes and Routines' 'Settings > Battery > Power saving' 'turns Wi-Fi off'; do grep -q "$want" "$tmp/stdout"; check "TC-W-183 the E3 prompts carry the verified path: $want" 0 $?; done

# the watch goes away (Wi-Fi off) after the 6th report (power saving): it comes back after 3 failed connects
FAKE_AWAY_AFTER=6 FAKE_AWAY_CALLS=3 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-180 a watch that comes back after power saving: exit 0" 0 $rc
grep -q '== report power-saving-after-off' "$result" && grep -q 'power saving: readings grew: True' "$result"; check "TC-W-180 the report after power saving is taken once the watch is reachable again" 0 $?
grep -q 'no report is possible while power saving is on' "$result"; check "TC-W-180 the result file says no report was possible during the power saving block" 0 $?

FAKE_AWAY_AFTER=6 FAKE_AWAY_CALLS=1000 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-181 a watch that never comes back does not fail the run (exit 0)" 0 $rc
grep -q 'no report possible during power-saving-after-off' "$result" && grep -q 'power saving: readings grew: unknown' "$result"; check "TC-W-181 the result file says no report was possible and the growth is unknown" 0 $?
[ "$(count 'cmd measure-report')" -eq 6 ]; check "TC-W-181 no report is faked: six reports were taken" 0 $?

# the watch goes away during Sleep mode (after the 4th report) and is reachable again only after the user turned it off
FAKE_AWAY_AFTER=4 FAKE_AWAY_CALLS=13 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-182 a watch that is away during Sleep mode: exit 0" 0 $rc
grep -q 'no report possible during sleep-mode-end' "$result" && grep -q '== report sleep-mode-after-off' "$result"; check "TC-W-182 the in-mode report is logged as impossible and taken after the mode is off" 0 $?

# the watch comes back on another wireless-debugging port (42000) after the 6th report: the old address never answers again
FAKE_FAIL=portchange FAKE_AWAY_AFTER=6 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-198 a watch that comes back on another port: exit 0" 0 $rc
grep -q 'address changed: port 41613 -> 42000' "$result"; check "TC-W-198 the result file says the address changed (ports only)" 0 $?
grep -q '== report power-saving-after-off' "$result" && grep -q 'power saving: readings grew: True' "$result"; check "TC-W-198 the report after power saving is taken on the new port" 0 $?
! grep -q '192.168' "$result"; check "TC-W-198 the result file holds no ip address" 0 $?
[ ! -s "$tmp/state/rejected" ]; check "TC-W-198 after the change every adb call uses the new address (none rejected)" 0 $?
grep -q 'battery after: [0-9]' "$result"; check "TC-W-201 the end of the run reconnects: the service is stopped and the battery after is read" 0 $?
[ "$(grep -c 'Which address' "$tmp/stdout")" -eq 0 ]; check "TC-W-198 nothing is asked when the device is found again" 0 $?

# no mdns answer: the user is asked ONCE for the new address
FAKE_FAIL=portchange,nomdns FAKE_AWAY_AFTER=6 STDIN_WORD=192.168.1.16:42000 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2
check "TC-W-199 a watch that is not found but whose new address is typed: exit 0" 0 $rc
[ "$(grep -c 'Which address' "$tmp/stdout")" -eq 1 ]; check "TC-W-199 the question for the new address is asked once" 0 $?
grep -q 'address changed: port 41613 -> 42000' "$result" && grep -q 'power saving: readings grew: True' "$result"; check "TC-W-199 the typed address is used for the rest of the run" 0 $?
! grep -q '192.168' "$result"; check "TC-W-199 the result file holds no ip address" 0 $?

# an invalid or empty answer: logged, the run goes on without the report and does not fail
FAKE_FAIL=portchange,nomdns FAKE_AWAY_AFTER=6 STDIN_WORD=nonsense run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2
check "TC-W-200 an invalid address answer does not fail the run (exit 0)" 0 $rc
[ "$(grep -c 'Which address' "$tmp/stdout")" -eq 1 ] && grep -q 'no valid address given' "$result" && grep -q 'no report possible during power-saving-after-off' "$result"; check "TC-W-200 asked once, the invalid answer is logged and the report is marked impossible" 0 $?
! grep -q 'nonsense' "$result"; check "TC-W-200 the typed text is not copied into the result file" 0 $?
FAKE_FAIL=portchange,nomdns FAKE_AWAY_AFTER=6 run "$E3" -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
[ "$(grep -c 'Which address' "$tmp/stdout")" -eq 0 ] && [ "$rc" -eq 0 ]; check "TC-W-200 -Unattended never asks for the address" 0 $?

# ---------------- E6
run "$E6" -AlarmWaitSec 1
check "TC-W-165 a full E6 run exits 0" 0 $rc
[ -n "$result" ] && echo "$result" | grep -q 'e6-'; check "TC-W-165 one result file e6-<time>.txt is written" 0 $?
[ "$(count 'cmd vibrate')" -ge 6 ]; check "TC-W-165 vibrate is run in each of the six states" 0 $?
[ "$(count 'cmd notify-alarm')" -ge 3 ] && [ "$(count 'cmd alarm-clock')" -ge 3 ]; check "TC-W-165 both Do Not Disturb routes are tried under Do Not Disturb, Bedtime and Theater" 0 $?
grep -q 'priority' "$tmp/state/dnd" && tail -1 "$tmp/state/dnd" | grep -q 'set_dnd off'; check "TC-W-165 Do Not Disturb is set to priority and switched off again at the end" 0 $?
grep -q 'answer: strong' "$result"; check "TC-W-165 the user's answers are in the result file" 0 $?
grep -q 'PROBE vibrate amplitudeControl=true' "$result" && grep -q 'PROBE notify-alarm posted' "$result" && grep -q 'PROBE alarm-clock fired' "$result"; check "TC-W-165 the PROBE lines are in the result file" 0 $?
[ "$(count 'pm grant .*POST_NOTIFICATIONS')" -ge 1 ]; check "TC-W-165 the notification permission is granted" 0 $?
! grep -q '192.168' "$result"; check "TC-W-165 the result file holds no ip address" 0 $?
[ "$(grep -ci 'Theater' "$tmp/stdout")" -ge 2 ] && [ "$(grep -ci 'vibration intensity' "$tmp/stdout")" -ge 2 ]; check "TC-W-166 the script asks the user to set Theater mode and the vibration intensity" 0 $?
for want in 'Quick panel' 'clapperboard' 'Sounds and vibration' 'Always On Display'; do grep -q "$want" "$tmp/stdout"; check "TC-W-183 the E6 prompts carry the verified path: $want" 0 $?; done
grep -qi 'untested on the watch' "$E3" && grep -qi 'untested on the watch' "$E6"; check "TC-W-167 both script headers say what is untested on the watch" 0 $?
exit $fail
