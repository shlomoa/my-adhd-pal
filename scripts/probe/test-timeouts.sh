#!/usr/bin/env bash
# Tests that every adb and git call of the probe runs has a hard time limit (PROBE_ADB_TIMEOUT_SEC, PROBE_GIT_TIMEOUT_SEC, default 30 s):
# a hung adb or git is killed, logged and treated as failed, and the run still ends with a published file. A hung adb.exe killed the
# E3 run silently on 2026-10-18. Simulated adb (scripts/probe/fake/adb, FAKE_FAIL=hang) and a git wrapper that hangs on push.
# It does NOT prove how a hung adb.exe behaves on Windows. Needs PowerShell 7 as `pwsh` (or set PWSH). Run: scripts/probe/test-timeouts.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
. "$here/test-lib.sh"

# run [extra args]: sets $rc, $tmp, $result, $secs (seconds the run took); HANGGIT=1 puts a git on the PATH that hangs on every push except a dry run
run() {
  make_repo
  git init -q --bare "$tmp/origin.git"; git -C "$tmp/repo" remote set-url origin "$tmp/origin.git"
  mkdir -p "$tmp/bin"
  real="$(command -v git)"
  printf '#!/usr/bin/env bash\ncase " $* " in *" --dry-run "*) ;; *" push "*) [ -n "${HANGGIT:-}" ] && exec sleep 600;; esac\nexec %s "$@"\n' "$real" > "$tmp/bin/git"; chmod +x "$tmp/bin/git"
  start=$SECONDS
  yes strong | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/bin:$tmp/repo/scripts/probe/fake:$PATH" timeout "${RUN_TIMEOUT:-200}" "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/run-e3.ps1" \
    -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 -BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -ReconnectSec 2 -ReconnectRetrySec 0.2 -Unattended "$@" > "$tmp/stdout" 2>&1
  rc=${PIPESTATUS[2]}; secs=$((SECONDS - start))
  result="$(ls "$tmp/out"/e3-*.txt 2>/dev/null | head -1)"
}

# a hung adb command (dumpsys notification) is killed after the limit, the run goes on and ends
FAKE_FAIL=hang PROBE_ADB_TIMEOUT_SEC=2 run
check "TC-W-225 a run with a hung adb command ends (exit 0 or 1, not killed by the test timeout)" 1 "$([ "$rc" -eq 0 ] || [ "$rc" -eq 1 ]; echo $((1 - $?)))"
grep -q 'adb timed out after 2 s' "$result"; check "TC-W-225 the hung adb call is logged" 0 $?
grep -q 'summary: run' "$result"; check "TC-W-225 the summary is written" 0 $?
grep -q 'PUSHED e3-results/' "$tmp/stdout"; check "TC-W-225 the result is published" 0 $?
[ "$secs" -lt 150 ]; check "TC-W-225 the run took less than 150 s (a hang would take 600 s per call)" 0 $?
! grep -q '192.168' "$result"; check "TC-W-225 the timeout log line holds no ip address" 0 $?

# a hung git push at the end of the run: the run still ends and says where the file is
HANGGIT=1 PROBE_GIT_TIMEOUT_SEC=2 PROBE_ADB_TIMEOUT_SEC=5 run
grep -q 'Push failed' "$tmp/stdout" && grep -q "$(basename "$result")" "$tmp/stdout" && grep -q 'timed out after 2' "$tmp/stdout"; check "TC-W-226 a hung git push ends and names the local result file" 0 $?
[ "$secs" -lt 150 ]; check "TC-W-226 the run did not wait for the hung push" 0 $?
exit $fail
