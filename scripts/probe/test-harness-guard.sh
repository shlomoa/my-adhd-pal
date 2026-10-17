#!/usr/bin/env bash
# Guard for the probe test suites: the run scripts publish their result file by default, so a test that runs one inside the REAL
# checkout would push simulated results to the real origin (it happened once: 39 branches e1-, e3-, e6-results/ on 2026-10-17).
# Every `pwsh ... -File` call in scripts/probe/test-*.sh must run a copy of the scripts inside a throwaway repository
# ("$tmp/repo/scripts/probe/..."), never "$here/..." or "$SCRIPT". Run: scripts/probe/test-harness-guard.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }

bad=""
for f in "$here"/test-*.sh; do
  [ "$(basename "$f")" = test-harness-guard.sh ] && continue
  while IFS= read -r line; do
    case "$line" in *'$tmp/repo/scripts/probe/'*) ;; *) bad="$bad
$(basename "$f"): $line";; esac
  done < <(grep -E '(PWSH|pwsh)".* -File ' "$f")
done
[ -z "$bad" ]; check "TC-W-179 every test that runs a run script runs it inside a throwaway repository" 0 $?
[ -z "$bad" ] || echo "runs outside a throwaway repository:$bad"

# the throwaway repository of the helper has an origin that cannot be reached, unless the test makes its own bare origin
grep -q 'make_repo' "$here/test-lib.sh" 2>/dev/null; check "TC-W-179 the shared helper make_repo exists" 0 $?
exit $fail
