#!/usr/bin/env bash
# The script tests run after each merge to main, not on every pull request (issue 190).
# Fast: no Gradle. Run: bash scripts/test/test-post-merge.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
root="$here/../.."
cd "$root"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}
has() { grep -qE -- "$1" "$2" 2>/dev/null; }
build=.github/workflows/build.yml
runner=scripts/ci-post-merge/run-tests.sh

# TC-S-170 the pull request workflow has no script-tests job and keeps the fast jobs
! has '^  script-tests:' "$build"; check "TC-S-170 build.yml has no script-tests job" 0 $?
has '^  build:' "$build" && has '^  external-links:' "$build" && has '^  docs-site:' "$build"
check "TC-S-170 build.yml keeps build, external-links and docs-site" 0 $?

# TC-S-172 no script test is lost: each scripts/test/test-*.sh runs per pull request or in the validation agent's run
lost=""
for f in scripts/test/test-*.sh; do
  n="$(basename "$f")"
  grep -qr -- "$n" .github/workflows "$runner" 2>/dev/null || lost="$lost $n"
done
[ -z "$lost" ]; check "TC-S-172 every script test runs somewhere (not run:$lost)" 0 $?

# TC-V-006 the whole former script-tests job runs after the merge and no part of it runs per pull request
gates=.github/workflows/gates.yml
missing=""
for n in test-structure.sh test-pairing.sh test-checks.sh test-ci.sh test-protocol-spec.sh test-gates.sh test-spike.sh \
  test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh test-post-merge.sh; do
  has "$n" "$runner" || missing="$missing $n"
done
[ -z "$missing" ]; check "TC-V-006 the runner lists all former script-tests and this test (missing:$missing)" 0 $?
! grep -qrE 'scripts/test/test-(structure|pairing|checks|ci|protocol-spec|gates|spike|design-docs|doc-checks|docs-site|design-style|post-merge)\.sh' .github/workflows
check "TC-V-006 no workflow runs any of them per pull request" 0 $?

# TC-S-173 the runner runs every listed test, keeps going after a failure, and names what failed
dir="$tmp/tests"; mkdir -p "$dir"
printf '#!/usr/bin/env bash\necho "PASS TC-X-001 fine"\n' > "$dir/test-good.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-002 fine"\necho "FAIL TC-X-003 broken thing"\nexit 1\n' > "$dir/test-bad.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-004 fine"\n' > "$dir/test-last.sh"
POST_MERGE_TEST_DIR="$dir" POST_MERGE_TESTS="test-bad.sh test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out1" \
  bash "$runner" >"$tmp/run1.txt" 2>&1; rc=$?
check "TC-S-173 a failing test makes the runner exit 1" 1 $rc
grep -q 'TC-X-004' "$tmp/out1/test-last.log" 2>/dev/null; check "TC-S-173 tests after the failure still ran" 0 $?
grep -q 'test-bad.sh' "$tmp/out1/report.md" && grep -q 'FAIL TC-X-003 broken thing' "$tmp/out1/report.md"
check "TC-S-173 the report names the failing script and the failing case" 0 $?
! grep -q 'test-good.sh' "$tmp/out1/report.md"; check "TC-S-173 the report does not list passing scripts" 0 $?
POST_MERGE_TEST_DIR="$dir" POST_MERGE_TESTS="test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out2" \
  bash "$runner" >"$tmp/run2.txt" 2>&1; check "TC-S-173 all passing makes the runner exit 0" 0 $?

# TC-V-001 lane V is set up: plan, catalogue, work log, gate G-VAL (owner V, requires G-BOOT) and ownership
bad=0
for f in docs/plans/validation.md docs/test-plans/validation.md docs/work-log/validation.md; do [ -f "$f" ] || bad=1; done
jq -e '.gates[] | select(.id == "G-VAL" and .owner == "V" and (.requires == ["G-BOOT"]))' docs/plans/gates.json >/dev/null 2>&1 || bad=1
has 'G-VAL' docs/plans/README.md && has 'validation\.md' docs/plans/README.md && has 'ci-post-merge' docs/plans/README.md || bad=1
[ "$bad" -eq 0 ]; check "TC-V-001 lane V is set up (plan, catalogue, work log, gate G-VAL, ownership)" 0 $?

# TC-V-003 the main-red procedure (one sub-agent per issue, archived when resolved) is written down
has 'sub-agent' docs/plans/validation.md && has 'archive' docs/plans/validation.md && has 'sub-agent' docs/conventions.md
check "TC-V-003 validation plan and conventions describe the main-red sub-agent procedure" 0 $?

# TC-V-004 the validation lane agent runs the script tests itself: no workflow does, and nothing only served one
[ ! -e .github/workflows/post-merge.yml ]; check "TC-V-004 there is no post-merge workflow" 0 $?
[ ! -e scripts/ci-post-merge/main-red-issue.sh ]; check "TC-V-004 the workflow-only issue handler is gone" 0 $?

# TC-V-005 lane V opens the main-red issue itself with its issue tool and the documents say what the issue names
has 'main-red' docs/conventions.md && has 'issue tool' docs/plans/validation.md && has 'issue tool' docs/conventions.md
check "TC-V-005 documents say lane V opens the main-red issue with its issue tool" 0 $?
has 'failing test ids' docs/plans/validation.md && has 'owning lane' docs/plans/validation.md && has 'sha' docs/plans/validation.md
check "TC-V-005 the issue names the failing test ids, the sha and the owning lane" 0 $?
exit $fail
