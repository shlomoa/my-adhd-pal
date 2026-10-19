#!/usr/bin/env bash
# Tests scripts/demo/run-stress-demo.ps1 (the stress demo of milestone M1: reading -> baseline comparison -> stress event -> local
# response -> alert on the phone) against the simulated adb of the probe tests (scripts/probe/fake/adb: a watch and a phone with
# serial PHONE123) inside a throwaway git repository with a bare "origin". It proves the order of the stages, the PASS/FAIL timeline,
# the failures, that nothing is asked and that the result is published without any address or serial. It does NOT prove how the apps
# or the real devices behave (that is the user's run). Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/demo/test-stress-demo.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
probe="$(cd "$here/../probe" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=demo-test GIT_AUTHOR_EMAIL=demo-test@invalid GIT_COMMITTER_NAME=demo-test GIT_COMMITTER_EMAIL=demo-test@invalid
export PROBE_STABLE_SEC=1 PROBE_POLL_SEC=0.2
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
SCRIPT="$here/run-stress-demo.ps1"

SCRIPT_FILE="$SCRIPT" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
check "TC-W-245 run-stress-demo.ps1 parses without errors" 0 $?

# run [extra args]: stdin is closed (nothing may be asked); sets $rc, $tmp, $result
run() {
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/out" "$tmp/apk/wear" "$tmp/apk/phone"
  git init -q --bare "$tmp/origin.git"; git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"; cp -r "$probe" "$tmp/repo/scripts/probe"; cp -r "$here" "$tmp/repo/scripts/demo"
  echo base > "$tmp/repo/docs/base.md"; git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 200 "$PWSH" -NoProfile -File "$tmp/repo/scripts/demo/run-stress-demo.ps1" \
    -Phone PHONE123 -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -SkipInstall -RiseSeconds 2 -WaitSec 3 -AvailableTimeoutSec 4 "$@" > "$tmp/stdout" 2>&1 < /dev/null
  rc=$?
  result="$(ls "$tmp/out"/demo-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls" 2>/dev/null; }

run
check "TC-W-246 a full demo run exits 0" 0 $rc
for stage in preflight grants monitoring baseline reading event response watch-alert phone-alert episode-end; do
  grep -qE "stage $stage: PASS" "$result"; check "TC-W-246 stage $stage is PASS" 0 $?
done
grep -qE 'stage event: PASS \(at [0-9:.]+, \+[0-9]+ ms' "$result"; check "TC-W-246 each stage line has its time and the time since the previous stage" 0 $?
order="$(grep -oE 'stage [a-z-]+: (PASS|FAIL)' "$result" | awk '{print $2}' | tr -d ':' | tr '\n' ' ')"
[ "$order" = "preflight grants monitoring baseline reading event response watch-alert phone-alert episode-end " ]; check "TC-W-246 the stages come in the order of the user's definition" 0 $?
grep -q 'phone latency' "$result" && grep -q 'reading to response' "$result"; check "TC-W-246 the timeline gives the latencies" 0 $?
! grep -q 'ACTION NEEDED' "$tmp/stdout"; check "TC-W-247 the demo asks nothing and needs no tap" 0 $?
[ -n "$(git -C "$tmp/origin.git" for-each-ref 'refs/heads/demo-results/*')" ] && grep -q '^PUSHED demo-results/' "$tmp/stdout"; check "TC-W-248 the result is published as demo-results/<stamp>" 0 $?
! grep -qE '192\.168|PHONE123|/tmp/' "$result"; check "TC-W-248 the file holds no ip address, no serial and no local path" 0 $?
[ "$(calls | grep -c 'MonitorDemoReceiver.*series')" -ge 1 ] && [ "$(calls | grep -c 'MonitoringActivity')" -ge 1 ] && [ "$(calls | grep -c 'DUMP_ALERTS')" -ge 1 ]; check "TC-W-246 the watch is driven with am start/broadcast and the phone dump is asked for" 0 $?

FAKE_FAIL=noevent run
[ "$rc" -eq 1 ] && grep -qE 'stage event: FAIL' "$result" && grep -qE 'stage phone-alert: (FAIL|NOT RUN)' "$result"; check "TC-W-249 no stress event: the stage fails, the later stages say so, exit 1" 0 $?
grep -q 'summary: run' "$result" && grep -q '^PUSHED demo-results/' "$tmp/stdout"; check "TC-W-249 a failed demo still ends with a summary and a published file" 0 $?

FAKE_FAIL=nophonealert run
[ "$rc" -eq 1 ] && grep -qE 'stage watch-alert: PASS' "$result" && grep -qE 'stage phone-alert: FAIL' "$result"; check "TC-W-250 an alert the phone did not raise fails only the phone stage" 0 $?

run -Real
grep -qE 'stage event: (PASS|FAIL)' "$result" && ! calls | grep -q 'cmd series'; check "TC-W-251 with -Real nothing is injected: the demo waits for a real event" 0 $?
[ "$(calls | grep -c 'cmd baseline')" -eq 0 ]; check "TC-W-251 with -Real no baseline is injected either" 0 $?

FAKE_FAIL=hangdemo PROBE_ADB_TIMEOUT_SEC=2 run
[ "$rc" -eq 1 ] && grep -q 'adb timed out after 2 s' "$result" && grep -q 'summary: run' "$result"; check "TC-W-252 a hung adb call does not hang the demo: the stage fails, the summary is written" 0 $?
exit $fail
