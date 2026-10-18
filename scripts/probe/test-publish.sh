#!/usr/bin/env bash
# Tests that the probe run scripts publish their result file as a branch <prefix>-results/<stamp> holding only that file,
# without touching the working tree, the index or HEAD of the user's checkout, against the simulated adb and a throwaway git
# repository with a bare "origin". Needs PowerShell 7 as `pwsh` (or set PWSH). It does NOT prove git behaviour with the user's
# credentials. Run: scripts/probe/test-publish.sh
set -u
export PROBE_STABLE_SEC="${PROBE_STABLE_SEC:-1}"
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
export GIT_AUTHOR_NAME=probe-test GIT_AUTHOR_EMAIL=probe-test@invalid GIT_COMMITTER_NAME=probe-test GIT_COMMITTER_EMAIL=probe-test@invalid
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }

# setup -> $tmp with a repo containing scripts/probe, a bare origin, uncommitted and staged work that must survive untouched
setup() {
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/out"
  git init -q --bare "$tmp/origin.git"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/probe"; echo base > "$tmp/repo/docs/base.md"; echo staged-base > "$tmp/repo/docs/staged.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/origin.git"
  echo "uncommitted work" > "$tmp/repo/docs/base.md"
  echo "staged work" > "$tmp/repo/docs/staged.md"; git -C "$tmp/repo" add docs/staged.md
  head_before="$(git -C "$tmp/repo" rev-parse HEAD)"
  status_before="$(git -C "$tmp/repo" status --short)"
  index_before="$(git -C "$tmp/repo" write-tree)"
}
# runit SCRIPT [args]  -> sets $rc; answers come from stdin
runit() {
  local script="$1"; shift
  yes strong | head -80 | FAKE_DIR="$tmp/state" PATH="$tmp/repo/scripts/probe/fake:$PATH" timeout 180 "$PWSH" -NoProfile -File "$tmp/repo/scripts/probe/$script" \
    -Watch 192.168.1.16:41613 -OutDir "$tmp/out" -ReportWaitSec 0.2 "$@" > "$tmp/stdout" 2>&1
  rc=${PIPESTATUS[2]}
}
branches() { git -C "$tmp/origin.git" for-each-ref --format='%(refname:short)' "refs/heads/$1-results/*"; }
E1="-PhaseSec 1 -AvailableTimeoutSec 4 -Unattended"
E3="-BlockSec 1 -StateSec 1 -AvailableTimeoutSec 4 -Unattended"

setup; runit run-e1.ps1 $E1
check "TC-W-168 E1 with publishing exits 0" 0 $rc
b="$(branches e1)"; [ -n "$b" ]; check "TC-W-168 branch e1-results/<stamp> is on origin" 0 $?
files="$(git -C "$tmp/origin.git" ls-tree -r --name-only "$b" 2>/dev/null)"
[ "$(echo "$files" | wc -l)" -eq 1 ] && echo "$files" | grep -qE '^e1-[0-9]{8}-[0-9]{6}\.txt$'; check "TC-W-168 the branch holds ONLY the result file" 0 $?
[ "$(git -C "$tmp/origin.git" rev-list --count "$b")" -eq 1 ]; check "TC-W-168 the branch is one commit with no parent" 0 $?
grep -q "^PUSHED $b\$" "$tmp/stdout"; check "TC-W-168 the script prints PUSHED <branch>" 0 $?
[ "$(git -C "$tmp/repo" rev-parse HEAD)" = "$head_before" ] && [ "$(git -C "$tmp/repo" status --short)" = "$status_before" ] && [ "$(git -C "$tmp/repo" write-tree)" = "$index_before" ]; check "TC-W-168 HEAD, working tree and index of the checkout are untouched" 0 $?
[ "$(cat "$tmp/repo/docs/base.md")" = "uncommitted work" ]; check "TC-W-168 uncommitted work is intact" 0 $?
! git -C "$tmp/origin.git" show "$b:$files" | grep -qE '192\.168|/tmp/'; check "TC-W-169 the published file holds no ip address and no local path" 0 $?
git -C "$tmp/origin.git" show "$b:$files" | grep -q 'battery before'; check "TC-W-169 the published file is the result" 0 $?

setup; runit run-e1.ps1 $E1 -SkipPush
[ -z "$(branches e1)" ] && ! grep -q '^PUSHED' "$tmp/stdout"; check "TC-W-170 -SkipPush publishes nothing" 0 $?
ls "$tmp/out"/e1-*.txt >/dev/null 2>&1; check "TC-W-170 -SkipPush still leaves the local result file" 0 $?

setup; git -C "$tmp/repo" remote set-url origin "$tmp/does-not-exist.git"; runit run-e1.ps1 $E1
check "TC-W-171 a failed push leaves the exit code unchanged (0)" 0 $rc
grep -q 'Push failed' "$tmp/stdout" && grep -q 'e1-results/' "$tmp/stdout" && grep -qE 'git push origin [0-9a-f]{40}:refs/heads/e1-results/' "$tmp/stdout"; check "TC-W-171 the failure names the branch and prints the retry command" 0 $?
ls "$tmp/out"/e1-*.txt >/dev/null 2>&1; check "TC-W-171 the local result file is kept" 0 $?

setup; FAKE_FAIL=noavail runit run-e1.ps1 $E1
[ "$rc" -ne 0 ] && [ -n "$(branches e1)" ]; check "TC-W-171 a failed run still publishes its result file and keeps its exit code" 0 $?

setup; runit run-e3.ps1 $E3
[ -n "$(branches e3)" ] && grep -q "^PUSHED e3-results/" "$tmp/stdout"; check "TC-W-172 E3 publishes e3-results/<stamp>" 0 $?
setup; runit run-e6.ps1 -AlarmWaitSec 1
[ -n "$(branches e6)" ] && grep -q "^PUSHED e6-results/" "$tmp/stdout"; check "TC-W-172 E6 publishes e6-results/<stamp>" 0 $?
exit $fail
