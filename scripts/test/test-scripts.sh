#!/usr/bin/env bash
# Tests for require-gate.sh and check-test-cases.sh. Run: scripts/test/test-scripts.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
scripts="$here/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
check() { # name expected_exit actual_exit
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected exit $2, got $3)"; fail=1; fi
}

cat > "$tmp/gates.json" <<'JSON'
{"gates":[
 {"id":"A","name":"a","owner":"S","requires":[],"status":"merged","pr":1},
 {"id":"B","name":"b","owner":"S","requires":["A"],"status":"open","pr":null}
]}
JSON
export GATES_FILE="$tmp/gates.json"

"$scripts/require-gate.sh" A >/dev/null 2>&1;      check "TC-B-001 merged gate passes" 0 $?
"$scripts/require-gate.sh" B >/dev/null 2>&1;      check "TC-B-002 open gate blocks" 3 $?
"$scripts/require-gate.sh" A B >/dev/null 2>&1;    check "TC-B-003 one open of many blocks" 3 $?
"$scripts/require-gate.sh" ZZ >/dev/null 2>&1;     check "TC-B-004 unknown gate is an error" 2 $?
"$scripts/require-gate.sh" >/dev/null 2>&1;        check "TC-B-005 no args is an error" 2 $?
out="$("$scripts/require-gate.sh" B 2>&1)"
case "$out" in *"B"*"owner S"*) check "TC-B-006 output names blocking gate and owner" 0 0;; *) check "TC-B-006 output names blocking gate and owner" 0 1;; esac

# check-test-cases: catalogue ids must appear in tests (baseline) and as PASS in the report (final)
mkdir -p "$tmp/repo/docs/test-plans" "$tmp/repo/docs/test-reports" "$tmp/repo/src/test"
cat > "$tmp/repo/docs/test-plans/lane.md" <<'MD'
| TC-X-001 | one |
| TC-X-002 | two |
MD
echo 'fun `TC-X-001 works`() {}' > "$tmp/repo/src/test/T.kt"
export REPO_ROOT="$tmp/repo"
"$scripts/check-test-cases.sh" lane baseline >/dev/null 2>&1; check "TC-B-007 baseline fails when an id has no test" 1 $?
echo 'fun `TC-X-002 works`() {}' >> "$tmp/repo/src/test/T.kt"
"$scripts/check-test-cases.sh" lane baseline >/dev/null 2>&1; check "TC-B-008 baseline passes when all ids have tests" 0 $?
"$scripts/check-test-cases.sh" lane final >/dev/null 2>&1;    check "TC-B-009 final fails without report" 1 $?
printf 'TC-X-001 PASS\nTC-X-002 PASS\n' > "$tmp/repo/docs/test-reports/lane-final.md"
"$scripts/check-test-cases.sh" lane final >/dev/null 2>&1;    check "TC-B-010 final passes with all PASS" 0 $?
printf 'TC-X-001 PASS\nTC-X-002 FAIL\n' > "$tmp/repo/docs/test-reports/lane-final.md"
"$scripts/check-test-cases.sh" lane final >/dev/null 2>&1;    check "TC-B-011 final fails on a FAIL row" 1 $?
printf 'TC-X-001 PASS\nTC-X-002 PASS\n' > "$tmp/repo/docs/test-reports/lane-final.md"
echo '@Ignore fun `TC-X-002 skipped`() {}' >> "$tmp/repo/src/test/T.kt"
"$scripts/check-test-cases.sh" lane final >/dev/null 2>&1;    check "TC-B-012 final fails on @Ignore" 1 $?

# check-pr-gates: PR body "Requires:" must be merged on base; only "Produces:" gates may change status
mkdir -p "$tmp/pr"
cat > "$tmp/pr/base.json" <<'JSON'
{"gates":[
 {"id":"A","name":"a","owner":"S","requires":[],"status":"merged","pr":1},
 {"id":"B","name":"b","owner":"S","requires":["A"],"status":"open","pr":null},
 {"id":"C","name":"c","owner":"S","requires":["B"],"status":"open","pr":null}
]}
JSON
jq '(.gates[]|select(.id=="B")|.status)="merged"' "$tmp/pr/base.json" > "$tmp/pr/head-b.json"
jq '(.gates[]|select(.id=="C")|.status)="merged"' "$tmp/pr/base.json" > "$tmp/pr/head-c.json"
pr() { PR_BODY="$1" BASE_GATES_FILE="$tmp/pr/base.json" HEAD_GATES_FILE="$2" "$scripts/check-pr-gates.sh" >/dev/null 2>&1; }
pr $'Requires: A\nProduces: B' "$tmp/pr/head-b.json"; check "TC-B-013 met requires and own produces pass" 0 $?
pr $'Requires: B\nProduces: C' "$tmp/pr/head-c.json"; check "TC-B-014 unmet requires fail" 3 $?
pr $'Requires: A\nProduces: B' "$tmp/pr/head-c.json"; check "TC-B-015 flipping a gate not produced fails" 4 $?
pr $'Requires: A' "$tmp/pr/base.json";                 check "TC-B-016 no flips and no produces pass" 0 $?
pr 'no markers' "$tmp/pr/base.json";                    check "TC-B-017 missing Requires line fails" 2 $?
# bootstrap PR: base has no gates.json yet; flipping G-BOOT is allowed only when Produces names it
pr_nobase() { PR_BODY="$1" BASE_GATES_FILE="$tmp/pr/missing.json" HEAD_GATES_FILE="$tmp/pr/head-a.json" "$scripts/check-pr-gates.sh" >/dev/null 2>&1; }
jq '(.gates[]|select(.id=="B")|.status)="open"' "$tmp/pr/base.json" > "$tmp/pr/head-a.json"
pr_nobase $'Requires: none\nProduces: A'; check "TC-B-018 missing base file: gate merged in head must be produced" 0 $?
pr_nobase $'Requires: none';           check "TC-B-019 missing base file: unproduced merged gate fails" 4 $?
exit $fail
