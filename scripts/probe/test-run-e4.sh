#!/usr/bin/env bash
# Tests scripts/probe/run-e4.ps1 (experiment E4, issue 231) against the simulated adb (scripts/probe/fake/adb) and a throwaway git
# repository with a bare "origin". The simulation proves the order of the steps, the restore of the screen timeout, the questions,
# the result file and its publishing. It does NOT prove how the Galaxy Watch6 Classic behaves (screen timeout, Always-on display,
# keep-screen-on, `dumpsys` output): that is the user's run. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-run-e4.sh
set -u
export PROBE_STABLE_SEC="${PROBE_STABLE_SEC:-1}"
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
SCRIPT="$here/run-e4.ps1"

SCRIPT_FILE="$SCRIPT" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/dev/null 2>&1
check "TC-W-173 run-e4.ps1 parses without errors" 0 $?

# run [extra args] -> sets $rc, $tmp (repo with scripts/probe, bare origin, simulation state, result folder)
run() {
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/out"
  git init -q --bare "$tmp/origin.git"; git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"; cp -r "$here" "$tmp/repo/scripts/probe"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base; git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  yes typed-answer | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 180 "$PWSH" -NoProfile \
    -File "$tmp/repo/scripts/probe/run-e4.ps1" -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -RunSec 1 -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1
  rc=${PIPESTATUS[2]}
  result="$(ls "$tmp/out"/e4-*.txt 2>/dev/null | head -1)"
}
calls() { cat "$tmp/state/calls"; }
nth() { calls | grep -nE -- "$1" | sed -n "${2}p" | cut -d: -f1 | grep . || echo 0; }
count() { calls | grep -cE -- "$1"; }
before() { [ "$1" -gt 0 ] && [ "$2" -gt "$1" ]; }

run
check "TC-W-174 a full E4 run exits 0" 0 $rc
[ -n "$result" ]; check "TC-W-174 one result file e4-<time>.txt is written" 0 $?
[ "$(count 'am start .*ProbeFrontActivity')" -eq 2 ]; check "TC-W-174 the front screen is started twice" 0 $?
calls | grep -E 'am start .*ProbeFrontActivity' | sed -n 1p | grep -vq -- '--ez keep true'; check "TC-W-174 run 1 starts it with the switch off" 0 $?
calls | grep -E 'am start .*ProbeFrontActivity' | sed -n 2p | grep -q -- '--ez keep true'; check "TC-W-174 run 2 starts it with --ez keep true" 0 $?
before "$(nth 'settings get system screen_off_timeout' 1)" "$(nth 'settings put system screen_off_timeout [0-9]' 1)"; check "TC-W-174 the screen timeout is read before it is changed" 0 $?
[ "$(cat "$tmp/state/timeout")" = 30000 ]; check "TC-W-174 the screen timeout is back to its original value at the end" 0 $?
before "$(nth 'logcat -c' 1)" "$(nth 'am start .*ProbeFrontActivity' 1)" && before "$(nth 'am start .*ProbeFrontActivity' 1)" "$(nth 'logcat -c' 2)" && before "$(nth 'logcat -c' 2)" "$(nth 'am start .*ProbeFrontActivity' 2)"; check "TC-W-174 the log is cleared before each run" 0 $?
grep -q '== run 1' "$result" && grep -q '== run 2' "$result"; check "TC-W-174 the result file names both runs" 0 $?
[ "$(grep -c 'PROBE front' "$result")" -ge 4 ] && grep -q 'keepOn=true' "$result" && grep -q 'keepOn=false' "$result"; check "TC-W-174 the PROBE front lines of both runs are in the result file" 0 $?
grep -q 'resumed activity:.*ProbeFrontActivity' "$result"; check "TC-W-174 the resumed activity is logged for each run" 0 $?
[ "$(grep -c '^[0-9-]* [0-9:]* answer: typed-answer' "$result")" -ge 4 ]; check "TC-W-175 the four answers are in the result file" 0 $?
[ "$(grep -ci 'screen timeout setting' "$tmp/stdout")" -ge 1 ] && [ "$(grep -ci 'notification' "$tmp/stdout")" -ge 1 ]; check "TC-W-175 the script asks the questions of the checklist" 0 $?
! grep -qE '192\.168|/tmp/' "$result"; check "TC-W-175 the result file holds no ip address and no local path" 0 $?
b="$(git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/e4-results/*')"; [ -n "$b" ] && grep -q "^PUSHED $b\$" "$tmp/stdout"; check "TC-W-176 the result is published as e4-results/<stamp>" 0 $?
[ "$(git -C "$tmp/origin.git" ls-tree -r --name-only "$b" | wc -l)" -eq 1 ]; check "TC-W-176 the branch holds only the result file" 0 $?

run -SkipPush
[ -z "$(git -C "$tmp/origin.git" for-each-ref 'refs/heads/e4-results/*')" ]; check "TC-W-176 -SkipPush publishes nothing" 0 $?

FAKE_FAIL=nostart run
[ "$rc" -ne 0 ] && [ "$(cat "$tmp/state/timeout")" = 30000 ]; check "TC-W-177 a front screen that cannot be started fails the run and restores the screen timeout" 0 $?
[ -n "$(git -C "$tmp/origin.git" for-each-ref 'refs/heads/e4-results/*')" ]; check "TC-W-177 a failed run still publishes its result file" 0 $?

grep -qi 'untested on the watch' "$SCRIPT"; check "TC-W-178 the script header says what is untested on the watch" 0 $?
exit $fail
