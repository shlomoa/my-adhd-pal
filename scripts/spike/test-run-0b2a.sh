#!/usr/bin/env bash
# Tests scripts/spike/run-0b2a.ps1 against a simulated adb/emulator (scripts/spike/fake) inside a throwaway git repository
# with a bare "origin". Needs PowerShell 7 as `pwsh` (or set PWSH). The simulation proves the runner's flow, parsing,
# report, exit codes and git publishing; it cannot prove how the real emulators behave. Run: scripts/spike/test-run-0b2a.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=spike-test GIT_AUTHOR_EMAIL=spike-test@invalid GIT_COMMITTER_NAME=spike-test GIT_COMMITTER_EMAIL=spike-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }

# The runner must at least parse (a bad "$var:" in a string once made it fail before doing anything).
SPIKE_FILE="$here/run-0b2a.ps1" "$PWSH" -NoProfile -Command '$e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SPIKE_FILE,[ref]$null,[ref]$e) | Out-Null; if ($e) { $e | ForEach-Object { $_.ToString() }; exit 1 }' >/tmp/spike-parse.txt 2>&1
check "run-0b2a.ps1 parses without errors" 0 $?

# run NAME FAKE_FAIL [extra script args]   -> sets $rc, $tmp (repo in $tmp/repo, origin in $tmp/origin.git)
run() {
  local fakefail="$2"; shift 2
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state"
  for s in emulator-5554 emulator-5556; do : > "$tmp/state/$s.log"; : > "$tmp/state/$s.items"; echo dead > "$tmp/state/$s.state"; done
  touch "$tmp/state/emulator-5554.online"
  [ "${NO_WATCH:-}" = 1 ] || touch "$tmp/state/emulator-5556.online"
  git init -q --bare "$tmp/origin.git"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/spike"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "${ORIGIN_URL:-$tmp/origin.git}"
  echo "uncommitted work" > "$tmp/repo/docs/base.md"      # must survive untouched
  FAKE_DIR="$tmp/state" FAKE_FAIL="$fakefail" PATH="$tmp/repo/scripts/spike/fake:$PATH" \
    timeout "${RUN_TIMEOUT:-300}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/spike/run-0b2a.ps1" -SkipBuild -EmulatorExe "$tmp/repo/scripts/spike/fake/emulator" \
      -PollSec 0.1 -SettleScale 0 -WaitSec 3 -NonUrgentWaitSec 3 -BootTimeoutSec 10 -ReconnectTimeoutSec 4 \
      -DeletionSettleSec 0 -DelayedDeleteMs 2000 "$@" > "$tmp/stdout" 2>&1
  rc=$?
}
has() { grep -qE -- "$1" "$tmp/stdout"; }
branch() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' 'refs/heads/spike-results/*' | head -1; }

# --- everything works; devices auto-detected; results published
FAKE_LEAK=1 run all-pass ""; check "all gating cases pass: exit 0" 0 $rc
has 'Phone = emulator-5554, Watch = emulator-5556' ; check "devices auto-detected (the one reporting a watch is the watch)" 0 $?
has '^C1 PASS' ; check "report: C1 PASS" 0 $?
has '^C1b deletion symmetry PASS.*phone->watch immediate: 3 items' ; check "report: C1b PASS with the per-group table" 0 $?
has 'clock skew watch-minus-phone -1[0-9]{3} ms' ; check "report: clock skew measured (about -1500 ms)" 0 $?
has '^C2 PASS' ; check "report: C2 PASS (C2a and C2b)" 0 $?
has 'C3 swipe-away \(am kill\) PASS, force-stop PASS' ; check "report: C3 both variants PASS" 0 $?
has 'C5 watch-away PASS .*phone-away PASS' ; check "report: C5 both directions PASS" 0 $?
has '^C4 \(optional, not gating\) PASS.*median' ; check "report: C4 round trips with medians" 0 $?
has 'sdk_gphone16k_x86_64 API 36' ; check "report: image name and API level" 0 $?
b="$(branch)"; [ -n "$b" ] && [ "${b#spike-results/0b2a-}" != "$b" ]; check "results branch spike-results/0b2a-<timestamp> was pushed" 0 $?
has "PUSHED results branch: spike-results/0b2a-" ; check "the branch name is printed" 0 $?
files="$(git -C "$tmp/origin.git" diff-tree --no-commit-id --name-only -r "$b" 2>/dev/null)"
[ -n "$files" ] && ! echo "$files" | grep -qv '^docs/spikes/results/0b2a-'; check "the commit contains ONLY the results folder" 0 $?
echo "$files" | grep -q 'results.txt' && echo "$files" | grep -q 'phone-C3b.log' && echo "$files" | grep -q 'watch-C1b.log'; check "results table and raw per-case logs are in the commit" 0 $?
! git -C "$tmp/origin.git" grep -q -e '@example.com' -e 'abc123secret' "$b" -- docs/spikes/results 2>/dev/null; check "e-mail addresses and tokens are stripped from the published results" 0 $?
git -C "$tmp/origin.git" grep -q -e '<redacted-email>' "$b" -- docs/spikes/results 2>/dev/null; check "the redaction markers are present (the leak was injected and caught)" 0 $?
[ "$(git -C "$tmp/repo" branch --show-current)" = main ]; check "the working branch is unchanged" 0 $?
[ "$(cat "$tmp/repo/docs/base.md")" = "uncommitted work" ] && [ -z "$(git -C "$tmp/repo" status --porcelain --untracked-files=no | grep -v 'docs/base.md')" ]; check "uncommitted files are untouched" 0 $?
[ -z "$(git -C "$tmp/repo" worktree list | grep -v "$tmp/repo ")" ]; check "the temporary worktree is removed" 0 $?

# --- deletion asymmetry is detected and named
run asym "asym"; check "asymmetric deletion: exit 1" 1 $rc
has '^C1b deletion symmetry FAIL' ; check "report: C1b FAIL" 0 $?
has 'phone->watch immediate: 1 item\(s\) still listed on the writer|phone->watch immediate: [0-9]+ item\(s\) still listed on the writer' ; check "report names the failing group (phone->watch immediate)" 0 $?
has 'phone->watch delayed: 3 items, still listed on the writer 0' ; check "report shows delayed deletes work in the same run" 0 $?

run delete-fails "delete"; check "deletion not working: exit 1" 1 $rc
has '^C1 FAIL' ; check "report: C1 FAIL when the receiver never deletes" 0 $?

run stopped-no-wake "stoppedwake"; check "no wake-up after force-stop: exit 1" 1 $rc
has 'force-stop FAIL' ; check "report: C3 force-stop FAIL" 0 $?
has 'arrived only after the app was opened' ; check "report: says it arrived only after the app was opened" 0 $?

run no-recover "norecover"; check "link does not recover after restart: exit 1" 1 $rc
has 'C5 watch-away FAIL' ; check "report: C5 watch-away FAIL" 0 $?

# adb emu kill never returns (real-run bug): the runner must time that client out and carry on
start=$SECONDS
run emu-hang "emuhang" -AdbTimeoutSec 3; check "adb emu kill hangs: the run still completes and passes (exit 0)" 0 $rc
has 'adb call timed out after 3s' ; check "the hung adb client is reported and killed" 0 $?
has 'C5 watch-away PASS .*phone-away PASS' ; check "C5 both directions still PASS after the hung client was killed" 0 $?
[ $(( SECONDS - start )) -lt 100 ]; check "and it does not wait for the hung client (bounded run time)" 0 $?

run c4-optional "nopong"; check "C4 failure is not gating: exit 0" 0 $rc
has '^C4 \(optional, not gating\) FAIL' ; check "report: C4 FAIL is shown" 0 $?

run skip "" -SkipC5 -SkipC4; check "-SkipC5: gating cases not run: exit 2" 2 $rc
has 'NOT RUN' ; check "report: lists NOT RUN" 0 $?

ORIGIN_URL=/nonexistent/origin.git run push-fails ""; check "push failure does not change the case verdict: exit 0" 0 $rc
has 'Push failed' ; check "push failure is reported clearly" 0 $?
[ -n "$(git -C "$tmp/repo" branch --list 'spike-results/*')" ]; check "results stay committed on a local branch" 0 $?
[ -s "$(ls -d "$tmp"/repo/docs/spikes/results/0b2a-*/results.txt | head -1)" ]; check "results folder stays local" 0 $?

NO_WATCH=1 run no-watch ""; [ "$rc" -ne 0 ]; check "watch emulator missing: non-zero exit" 0 $?
has 'Device for Watch not found' ; check "missing device is explained" 0 $?
exit $fail
