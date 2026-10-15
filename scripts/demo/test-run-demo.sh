#!/usr/bin/env bash
# Tests scripts/demo/run-demo.ps1 (real devices) against a simulated adb with hardware-style serials
# (scripts/demo/fake/adb: a USB serial for the phone, 192.168.x.x:port for the watch) inside a throwaway git repository
# with a bare "origin". Needs PowerShell 7 as `pwsh` (or set PWSH). The simulation proves the runner's flow, parsing, the
# readiness gate, the stage logic, the report, exit codes and git publishing. It does NOT prove how the Galaxy A55 5G,
# the Galaxy Watch6 Classic, the apps or Google Play services behave (that is the user's run, TC-W-103).
# Run: scripts/demo/test-run-demo.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=demo-test GIT_AUTHOR_EMAIL=demo-test@invalid GIT_COMMITTER_NAME=demo-test GIT_COMMITTER_EMAIL=demo-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
WATCH=192.168.1.57:41203; PHONE=R58W20ABCDE

SCRIPT_FILE="$here/run-demo.ps1" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/tmp/demo-parse.txt 2>&1
check "TC-W-102 run-demo.ps1 parses without errors" 0 $?

# run NAME FAKE_FAIL [extra script args]   -> sets $rc, $tmp (repo in $tmp/repo, origin in $tmp/origin.git)
run() {
  local fakefail="$2"; shift 2
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state"
  git init -q --bare "$tmp/origin.git"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/demo"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  echo "uncommitted work" > "$tmp/repo/docs/base.md"      # must survive untouched
  # -SkipBuild means the debug apps are already installed (unless the test says otherwise)
  [ -n "${NOT_INSTALLED:-}" ] || touch "$tmp/state/installed.phone" "$tmp/state/installed.watch"
  FAKE_DIR="$tmp/state" FAKE_FAIL="$fakefail" FAKE_SECONDS_PER_MINUTE=1 PATH="$tmp/repo/scripts/demo/fake:$PATH" \
    timeout "${RUN_TIMEOUT:-240}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/demo/run-demo.ps1" -SkipBuild \
      -PollSec 0.2 -SettleScale 0 -PublishWaitSec 3 -ScheduleWaitSec 4 -AckWaitSec 4 -LateToleranceSec 1 \
      -AdbTimeoutSec "${ADB_TIMEOUT:-5}" "$@" > "$tmp/stdout" 2>&1 < /dev/null
  rc=$?
}
has() { grep -qE -- "$1" "$tmp/stdout"; }
branch() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/demo-results/*' | head -1; }
stage() { grep -E "^$1 +" "$tmp/repo/docs/demo/results"/*/results.txt | head -1 | awk '{print $2}'; }

# --- everything works; the watch is connected by the script (-WatchAddress); results published without device identifiers
FAKE_LEAK=1 run all-pass "" -WatchAddress $WATCH; check "TC-W-102 all stages pass: exit 0" 0 $rc
has "RESULT: ALL STAGES PASS"; check "TC-W-102 all stages pass: result line" 0 $?
for s in Ready Install Publish Schedule Reminder Notification Done Ack; do check "TC-W-102 stage $s is PASS" PASS "$(stage $s)"; done
has "What you should have seen on the watch"; check "TC-W-102 the report says what to see on the watch" 0 $?
b="$(branch)"; [ -n "$b" ]; check "TC-W-102 results are pushed on a demo-results branch" 0 $?
files="$(git -C "$tmp/origin.git" ls-tree -r --name-only "$b" | grep -v '^docs/demo/results/' | grep -v '^scripts/' | grep -v '^docs/base.md$' | wc -l)"
check "TC-W-102 the results branch adds only the results folder" 0 "$files"
git -C "$tmp/origin.git" show "$b:$(git -C "$tmp/origin.git" ls-tree -r --name-only "$b" | grep 'results.txt')" | grep -qE 'R58W20ABCDE|192\.168|abc123secret|example\.com'
check "TC-W-102 no device serial, ip, e-mail or token in the published results" 1 $?
git -C "$tmp/origin.git" ls-tree -r --name-only "$b" | grep '\.log$' | while read -r f; do git -C "$tmp/origin.git" show "$b:$f"; done | grep -qE 'R58W20ABCDE|192\.168|abc123secret|example\.com'
check "TC-W-102 no device identifiers in the published logs" 1 $?
check "TC-W-102 the user's uncommitted work is untouched" "uncommitted work" "$(cat "$tmp/repo/docs/base.md")"

# --- the schedule never reaches the watch: Schedule FAIL, Reminder and later NOT RUN
run noapply "noapply" -WatchAddress $WATCH -SkipPush; check "TC-W-102 schedule not applied: exit 1" 1 $rc
check "TC-W-102 schedule not applied: Schedule FAIL" FAIL "$(stage Schedule)"
check "TC-W-102 schedule not applied: Reminder NOT" NOT "$(stage Reminder)"

# --- the reminder never fires
run nofire "nofire" -WatchAddress $WATCH -SkipPush; check "TC-W-102 reminder never fires: exit 1" 1 $rc
check "TC-W-102 reminder never fires: Reminder FAIL" FAIL "$(stage Reminder)"
check "TC-W-102 reminder never fires: Done NOT" NOT "$(stage Done)"

# --- the reminder fires too late (limit 1 s in this test)
run late "latefire" -WatchAddress $WATCH -SkipPush; check "TC-W-102 late reminder: exit 1" 1 $rc
check "TC-W-102 late reminder: Reminder FAIL" FAIL "$(stage Reminder)"

# --- no notification in the watch's list
run nonotif "nonotif" -WatchAddress $WATCH -SkipPush; check "TC-W-102 no notification: Notification FAIL" FAIL "$(stage Notification)"
check "TC-W-102 no notification: the run goes on to Ack" PASS "$(stage Ack)"

# --- the ack never reaches the phone history
run noack "noack" -WatchAddress $WATCH -SkipPush; check "TC-W-102 ack missing on the phone: exit 1" 1 $rc
check "TC-W-102 ack missing on the phone: Done PASS" PASS "$(stage Done)"
check "TC-W-102 ack missing on the phone: Ack FAIL" FAIL "$(stage Ack)"

# --- the watch has no transport yet: Done FAIL, Ack NOT RUN
run noackdone "noackdone" -WatchAddress $WATCH -SkipPush; check "TC-W-102 ack not sent: Done FAIL" FAIL "$(stage Done)"
check "TC-W-102 ack not sent: Ack NOT" NOT "$(stage Ack)"

# --- the phone app has no demo receiver (too old)
run nopublish "nopublish" -WatchAddress $WATCH -SkipPush; check "TC-W-102 no publish receiver: exit 1" 1 $rc
check "TC-W-102 no publish receiver: Publish FAIL" FAIL "$(stage Publish)"
check "TC-W-102 no publish receiver: Schedule NOT" NOT "$(stage Schedule)"

# --- readiness gate: nothing is installed or changed
NOT_INSTALLED=1 run unauth "unauth" -WatchAddress $WATCH -SkipPush; check "TC-W-102 phone unauthorized: exit 2" 2 $rc
has "unauthorized"; check "TC-W-102 phone unauthorized: says why" 0 $?
[ ! -e "$tmp/state/installed.phone" ]; check "TC-W-102 phone unauthorized: nothing installed" 0 $?
run nowatch "nowatch" -SkipPush; check "TC-W-102 no watch: exit 2" 2 $rc
has "no watch found"; check "TC-W-102 no watch: says what to do" 0 $?
ADB_TIMEOUT=2 run deadwatch "deadwatch" -WatchAddress $WATCH -SkipPush; check "TC-W-102 unresponsive watch: exit 2" 2 $rc
has "time out"; check "TC-W-102 unresponsive watch: says why" 0 $?

NOT_INSTALLED=1 run notinstalled "" -WatchAddress $WATCH -SkipPush; check "TC-W-102 apps not installed with -SkipBuild: exit 2" 2 $rc
has "app is not installed"; check "TC-W-102 apps not installed with -SkipBuild: says what to do" 0 $?

exit $fail
