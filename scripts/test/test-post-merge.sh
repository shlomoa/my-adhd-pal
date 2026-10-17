#!/usr/bin/env bash
# The script tests run after each merge to main, not on every pull request (issue 190).
# Fast: no Gradle, a fake gh. Run: scripts/test/test-post-merge.sh
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
post=.github/workflows/post-merge.yml
runner=scripts/post-merge-tests.sh
handler=scripts/main-red-issue.sh

# TC-S-170 the pull request workflow has no script-tests job and keeps the fast jobs
! has '^  script-tests:' "$build"; check "TC-S-170 build.yml has no script-tests job" 0 $?
has '^  build:' "$build" && has '^  external-links:' "$build" && has '^  docs-site:' "$build"
check "TC-S-170 build.yml keeps build, external-links and docs-site" 0 $?

# TC-S-171 the post-merge workflow runs on pushes to main and by hand, never on pull requests
[ -f "$post" ]; check "TC-S-171 post-merge.yml exists" 0 $?
has '^  push:' "$post" && has 'branches: \[main\]' "$post" && has '^  workflow_dispatch:' "$post"
check "TC-S-171 it runs on push to main and workflow_dispatch" 0 $?
! has 'pull_request' "$post"; check "TC-S-171 it never runs on pull requests" 0 $?
has 'issues: write' "$post" && has "$runner" "$post" && has "$handler" "$post"
check "TC-S-171 it can write issues and calls the runner and the issue handler" 0 $?

# TC-S-172 no script test is lost: each scripts/test/test-*.sh runs per pull request or after the merge
lost=""
for f in scripts/test/test-*.sh; do
  n="$(basename "$f")"
  grep -qr -- "$n" .github/workflows "$runner" 2>/dev/null || lost="$lost $n"
done
[ -z "$lost" ]; check "TC-S-172 every script test runs somewhere (not run:$lost)" 0 $?

# TC-S-175 the two Gradle-free tests that take about a second stay per pull request and are not repeated after the merge
gates=.github/workflows/gates.yml
has 'scripts/test/test-protocol-spec.sh' "$gates" && has 'scripts/test/test-gates.sh' "$gates"
check "TC-S-175 gates.yml runs test-protocol-spec.sh and test-gates.sh per pull request" 0 $?
! grep -qE 'test-(protocol-spec|gates)\.sh' "$runner"; check "TC-S-175 the post-merge runner does not repeat them" 0 $?

# TC-S-173 the runner runs every listed test, keeps going after a failure, and names what failed
dir="$tmp/tests"; mkdir -p "$dir"
printf '#!/usr/bin/env bash\necho "PASS TC-X-001 fine"\n' > "$dir/test-good.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-002 fine"\necho "FAIL TC-X-003 broken thing"\nexit 1\n' > "$dir/test-bad.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-004 fine"\n' > "$dir/test-last.sh"
chmod +x "$dir"/*.sh
POST_MERGE_TEST_DIR="$dir" POST_MERGE_TESTS="test-bad.sh test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out1" \
  "$runner" >"$tmp/run1.txt" 2>&1; rc=$?
check "TC-S-173 a failing test makes the runner exit 1" 1 $rc
grep -q 'TC-X-004' "$tmp/out1/test-last.log" 2>/dev/null; check "TC-S-173 tests after the failure still ran" 0 $?
grep -q 'test-bad.sh' "$tmp/out1/report.md" && grep -q 'FAIL TC-X-003 broken thing' "$tmp/out1/report.md"
check "TC-S-173 the report names the failing script and the failing case" 0 $?
! grep -q 'test-good.sh' "$tmp/out1/report.md"; check "TC-S-173 the report does not list passing scripts" 0 $?
POST_MERGE_TEST_DIR="$dir" POST_MERGE_TESTS="test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out2" \
  "$runner" >"$tmp/run2.txt" 2>&1; check "TC-S-173 all passing makes the runner exit 0" 0 $?

# TC-S-174 the issue handler opens or updates one main-red issue when red, closes it when green
mkdir -p "$tmp/bin"
cat > "$tmp/bin/gh" <<'GH'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_GH_LOG"
if [ "$1 $2" = "issue list" ]; then echo "${FAKE_GH_OPEN:-[]}"; fi
GH
chmod +x "$tmp/bin/gh"
printf '# Script tests failed on main\n\n- test-bad.sh\n  - FAIL TC-X-003 broken thing\n' > "$tmp/report.md"
run_handler() { # state open-json
  : > "$tmp/gh.log"
  PATH="$tmp/bin:$PATH" FAKE_GH_LOG="$tmp/gh.log" FAKE_GH_OPEN="$2" "$handler" "$1" "$tmp/report.md" "https://example.test/run/1" >/dev/null 2>&1
}
run_handler red '[]'; check "TC-S-174 red with no open issue succeeds" 0 $?
grep -q '^issue create' "$tmp/gh.log" && grep -q 'main-red' "$tmp/gh.log"; check "TC-S-174 red with no open issue creates one labelled main-red" 0 $?
! grep -q '^issue edit' "$tmp/gh.log"; check "TC-S-174 red with no open issue does not edit" 0 $?
grep -q 'TC-X-003' "$tmp/gh.log" && grep -q 'https://example.test/run/1' "$tmp/gh.log"; check "TC-S-174 the issue text has the failing case and the run link" 0 $?
run_handler red '[{"number":7}]'
grep -q '^issue edit 7' "$tmp/gh.log" && ! grep -q '^issue create' "$tmp/gh.log"; check "TC-S-174 red with an open issue updates it and opens no second one" 0 $?
run_handler green '[{"number":7}]'
grep -q '^issue close 7' "$tmp/gh.log"; check "TC-S-174 green with an open issue closes it" 0 $?
run_handler green '[]'
! grep -qE '^issue (create|edit|close)' "$tmp/gh.log"; check "TC-S-174 green with no open issue does nothing" 0 $?
exit $fail
