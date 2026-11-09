#!/usr/bin/env bash
# Tests the pre-flight check and the heartbeat of the probe runs (scripts/probe/probe-common.ps1) against the simulated adb
# (scripts/probe/fake/adb) and a throwaway git repository with a bare "origin". The pre-flight fails fast with ONE message listing
# everything that is wrong before any step runs; the heartbeat pushes a tiny status file to probe-status/<script>-<run-id> (force-updated,
# no checkout change) while a run waits, and marks it finished at the end. The simulation does NOT prove how the real watch, adb or
# the user's git credentials behave. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-preflight.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
. "$here/test-lib.sh"
FAST=(-BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended)

# run [extra args]: a repository with a bare origin that works ($ORIGIN=good) or does not; sets $rc, $tmp, $result
run() {
  make_repo
  if [ "${ORIGIN:-bad}" = good ]; then
    git init -q --bare "$tmp/origin.git"; git -C "$tmp/repo" remote set-url origin "$tmp/origin.git"
  fi
  if [ "${STDIN_MODE:-answers}" = never ]; then
    FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" \
      -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1 < <(sleep 60)
    rc=$?
  else
    yes strong | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" \
      -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1
    rc=${PIPESTATUS[2]}
  fi
  result="$(ls "$tmp/out"/e3-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls" 2>/dev/null; }
count() { calls | grep -cE -- "$1"; }
status_branches() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/probe-status/*' 2>/dev/null; }

# ---------------- pre-flight
ORIGIN=good run "${FAST[@]}"
check "TC-W-213 a healthy watch passes the pre-flight and the run exits 0" 0 $rc
grep -q 'preflight: ok' "$result"; check "TC-W-213 the file says the pre-flight was ok" 0 $?
[ "$(grep -c 'logcat -g' "$tmp/state/calls")" -ge 1 ] && grep -q 'log buffer' "$result"; check "TC-W-213 the log buffer is set and read back" 0 $?

FAKE_FAIL=dead ORIGIN=good run "${FAST[@]}"
[ "$rc" -eq 3 ] && [ "$(grep -c 'PREFLIGHT FAILED' "$tmp/stdout")" -eq 1 ] && grep -q 'the watch cannot be reached' "$tmp/stdout"; check "TC-W-214 an unreachable watch: ONE message, exit 3" 0 $?
[ "$(count 'pm grant|pm revoke|am start')" -eq 0 ]; check "TC-W-214 no step ran after a failed pre-flight" 0 $?
FAKE_CHARGING=1 ORIGIN=good run "${FAST[@]}"
[ "$rc" -eq 3 ] && grep -q 'PREFLIGHT FAILED.*charger' "$tmp/stdout"; check "TC-W-215 a watch on its charger fails the pre-flight" 0 $?
FAKE_BATTERY=30 ORIGIN=good run "${FAST[@]}"
[ "$rc" -eq 3 ] && grep -q 'PREFLIGHT FAILED.*battery is 30' "$tmp/stdout"; check "TC-W-215 a battery under 40 % fails the pre-flight" 0 $?
FAKE_FAIL=nodebug ORIGIN=good run "${FAST[@]}"
[ "$rc" -eq 3 ] && grep -q 'PREFLIGHT FAILED.*debug build' "$tmp/stdout"; check "TC-W-216 a missing debug probe build fails the pre-flight" 0 $?
PROBE_STABLE_SEC=2 PROBE_STABLE_EVERY_SEC=0.3 FAKE_FAIL=flaky ORIGIN=good run "${FAST[@]}"
[ "$rc" -eq 3 ] && grep -q 'PREFLIGHT FAILED.*not stable' "$tmp/stdout"; check "TC-W-217 an unstable connection fails the pre-flight" 0 $?
ORIGIN=bad run "${FAST[@]}"
[ "$rc" -eq 3 ] && grep -q 'PREFLIGHT FAILED.*git push' "$tmp/stdout"; check "TC-W-218 a git push that cannot work fails the pre-flight" 0 $?
ORIGIN=bad run "${FAST[@]}" -SkipPush
[ "$rc" -eq 0 ]; check "TC-W-218 with -SkipPush the git push is not checked" 0 $?
FAKE_CHARGING=1 FAKE_BATTERY=30 FAKE_FAIL=nodebug ORIGIN=bad run "${FAST[@]}"
[ "$rc" -eq 3 ] && [ "$(grep -c 'PREFLIGHT FAILED' "$tmp/stdout")" -eq 1 ] && grep 'PREFLIGHT FAILED' "$tmp/stdout" | grep -q 'charger' && grep 'PREFLIGHT FAILED' "$tmp/stdout" | grep -q 'battery' && grep 'PREFLIGHT FAILED' "$tmp/stdout" | grep -q 'debug build'; check "TC-W-219 several problems are listed in ONE message" 0 $?
FAKE_CHARGING=1 ORIGIN=good run "${FAST[@]}" -AllowCharging
[ "$rc" -eq 0 ]; check "TC-W-215 -AllowCharging lets a watch on its charger pass" 0 $?

# ---------------- heartbeat
PROBE_HEARTBEAT_SEC=1 PROBE_KEEPALIVE_SEC=1 ORIGIN=good run -BlockSec 4 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended
check "TC-W-220 a run with heartbeats exits 0" 0 $rc
[ "$(grep -c 'heartbeat pushed' "$result")" -ge 3 ]; check "TC-W-220 several heartbeats are pushed during the run" 0 $?
b="$(status_branches)"; [ "$(echo "$b" | grep -c '^probe-status/e3-')" -eq 1 ]; check "TC-W-220 one branch probe-status/e3-<run-id> exists" 0 $?
[ "$(git -C "$tmp/origin.git" rev-list --count "$b")" -eq 1 ]; check "TC-W-220 the branch is force-updated: one commit without history" 0 $?
st="$(git -C "$tmp/origin.git" show "$b:status.txt" 2>/dev/null)"
for want in 'script=e3' 'run=' 'step=' 'elapsed_sec=' 'waiting_on_prompt=no' 'watch_reachable=yes' 'last_reading_count=' 'battery=' 'state=finished'; do echo "$st" | grep -q "$want"; check "TC-W-221 the final status holds $want" 0 $?; done
! echo "$st" | grep -q '192.168'; check "TC-W-221 the status file holds no ip address" 0 $?
[ "$(git -C "$tmp/origin.git" ls-tree --name-only "$b" | wc -l)" -eq 1 ]; check "TC-W-221 the branch holds only the status file" 0 $?
[ -z "$(git -C "$tmp/repo" status --short)" ] && [ "$(git -C "$tmp/repo" rev-parse --abbrev-ref HEAD)" = main ]; check "TC-W-222 the checkout is untouched by the heartbeat" 0 $?

STDIN_MODE=never PROBE_HEARTBEAT_SEC=1 PROBE_ASK_TIMEOUT_SEC=3 PROBE_BEEP_SEC=1 ORIGIN=good run -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2
grep -q 'heartbeat pushed.*waiting_on_prompt=yes' "$result"; check "TC-W-223 a heartbeat during a question says waiting_on_prompt=yes" 0 $?

PROBE_HEARTBEAT_SEC=1 PROBE_KEEPALIVE_SEC=1 ORIGIN=bad run -BlockSec 3 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended -SkipPush
check "TC-W-224 with -SkipPush no heartbeat is pushed and the run exits 0" 0 $rc
! grep -q 'heartbeat pushed' "$result"; check "TC-W-224 nothing is reported as pushed under -SkipPush" 0 $?
exit $fail
