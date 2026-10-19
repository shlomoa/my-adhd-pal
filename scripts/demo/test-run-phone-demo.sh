#!/usr/bin/env bash
# Tests scripts/demo/run-phone-demo.ps1 (phone walkthrough: screenshots and a screen recording of every phone screen)
# against a simulated adb (scripts/demo/fake/adb) inside a throwaway git repository with a bare "origin". Needs PowerShell 7
# as `pwsh` (or set PWSH). It proves the runner's flow, the readiness gate, the stage logic, the report and index page, the
# exit codes, the publishing and that no device identifier is published. It does NOT prove how the Galaxy A55 5G, its
# screen capture or the app behave (that is the user's run, TC-A-139 and TC-A-153).
# Run: scripts/demo/test-run-phone-demo.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=demo-test GIT_AUTHOR_EMAIL=demo-test@invalid GIT_COMMITTER_NAME=demo-test GIT_COMMITTER_EMAIL=demo-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
PHONE=R58W20ABCDE

SCRIPT_FILE="$here/run-phone-demo.ps1" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/tmp/phone-demo-parse.txt 2>&1
check "TC-A-153 run-phone-demo.ps1 parses without errors" 0 $?

# run FAKE_FAIL [extra script args]   -> sets $rc, $tmp (repo in $tmp/repo, origin in $tmp/origin.git)
run() {
  local fakefail="$1"; shift
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state"
  git init -q --bare "$tmp/origin.git"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/demo"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  echo "uncommitted work" > "$tmp/repo/docs/base.md"
  [ -n "${NOT_INSTALLED:-}" ] || touch "$tmp/state/installed.phone"
  FAKE_DIR="$tmp/state" FAKE_FAIL="$fakefail" PATH="$tmp/repo/scripts/demo/fake:$PATH" \
    timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/demo/run-phone-demo.ps1" -SkipBuild \
      -PollSec 0.2 -SettleScale 0 -RecordSec 3 -AdbTimeoutSec "${ADB_TIMEOUT:-5}" "$@" > "$tmp/stdout" 2>&1 < /dev/null
  rc=$?
}
has() { grep -qE -- "$1" "$tmp/stdout"; }
results() { echo "$tmp/repo/docs/demo/results"/phone-*; }
stage() { grep -E "^$1 +" $(results)/results.txt | head -1 | awk '{print $2}'; }
branch() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/demo-results/phone-*' | head -1; }

# --- everything works
FAKE_LEAK=1 run "" ; check "TC-A-153 all stages pass: exit 0" 0 $rc
has "RESULT: ALL STAGES PASS"; check "TC-A-153 all stages pass: result line" 0 $?
for s in Ready Install Seed Screens Alert Recording; do check "TC-A-153 stage $s is PASS" PASS "$(stage $s)"; done
for f in 01-watch 02-tasks 03-ask 04-settings 05-history 06-alert 07-after-ok; do
  [ -s "$(results)/$f.png" ]; check "TC-A-154 screenshot $f.png exists and is not empty" 0 $?
done
[ -s "$(results)/phone-demo.mp4" ]; check "TC-A-154 the screen recording exists and is not empty" 0 $?
for f in 01-watch 06-alert 07-after-ok; do grep -q "$f.png" "$(results)/index.md"; check "TC-A-154 the index page lists $f.png" 0 $?; done
grep -q "phone-demo.mp4" "$(results)/index.md"; check "TC-A-154 the index page links the recording" 0 $?
[ ! -e "$tmp/state/dev/demo-01-watch.png" ]; check "TC-A-154 the screenshots are removed from the phone after the pull" 0 $?
b="$(branch)"; [ -n "$b" ]; check "TC-A-155 results are pushed on a demo-results/phone-<stamp> branch" 0 $?
files="$(git -C "$tmp/origin.git" ls-tree -r --name-only "$b" | grep -v '^docs/demo/results/phone-' | grep -v '^scripts/' | grep -v '^docs/base.md$' | wc -l)"
check "TC-A-155 the results branch adds only the results folder" 0 "$files"
for f in results.txt index.md phone.log; do
  git -C "$tmp/origin.git" show "$b:docs/demo/results/$(basename "$(results)")/$f" | grep -qE 'R58W20ABCDE|abc123secret|example\.com'
  check "TC-A-155 no device serial, e-mail or token in the published $f" 1 $?
done
check "TC-A-155 the user's uncommitted work is untouched" "uncommitted work" "$(cat "$tmp/repo/docs/base.md")"

# --- failure cases
run "noseed" -SkipPush; check "TC-A-156 no seeded line: exit 1" 1 $rc
check "TC-A-156 no seeded line: Seed FAIL" FAIL "$(stage Seed)"
check "TC-A-156 no seeded line: the screens are still taken" PASS "$(stage Screens)"
run "locked" -SkipPush; check "TC-A-156 phone locked (the app is not on top): Screens FAIL" FAIL "$(stage Screens)"
has "unlock"; check "TC-A-156 phone locked: says to unlock" 0 $?
run "noalertraise" -SkipPush; check "TC-A-157 the alert is never raised: Alert FAIL" FAIL "$(stage Alert)"
run "noalertscreen" -SkipPush; check "TC-A-157 raised but no full-screen alert on top: Alert FAIL" FAIL "$(stage Alert)"
has "full-screen"; check "TC-A-157 raised but no full-screen alert: says why" 0 $?
run "nodismiss" -SkipPush; check "TC-A-157 I'm OK not logged: Alert FAIL" FAIL "$(stage Alert)"
run "norecord" -SkipPush; check "TC-A-158 recording missing: Recording FAIL" FAIL "$(stage Recording)"
check "TC-A-158 recording missing: the other stages still pass" PASS "$(stage Alert)"
run "norecord" -SkipPush; check "TC-A-158 recording missing: exit 1" 1 $rc

# --- readiness gate: nothing is installed or changed
NOT_INSTALLED=1 run "unauth" -SkipPush; check "TC-A-159 phone unauthorized: exit 2" 2 $rc
has "unauthorized"; check "TC-A-159 phone unauthorized: says why" 0 $?
[ ! -e "$tmp/state/installed.phone" ]; check "TC-A-159 phone unauthorized: nothing installed" 0 $?
NOT_INSTALLED=1 run "" -SkipPush; check "TC-A-159 app not installed with -SkipBuild: exit 2" 2 $rc
has "app is not installed"; check "TC-A-159 app not installed: says what to do" 0 $?
ADB_TIMEOUT=2 run "deadphone" -SkipPush; check "TC-A-159 unresponsive phone: exit 2" 2 $rc
has "time out"; check "TC-A-159 unresponsive phone: says why" 0 $?
run "" -Phone NOSUCHSERIAL -SkipPush; check "TC-A-159 named phone not connected: exit 2" 2 $rc

exit $fail
