#!/usr/bin/env bash
# Lane D (issue #157): the markdown lint and format checks are separate, cover the central documentation and run in CI.
# Negative fixtures live in the repository for the duration of the test and are removed on exit. Case ids TC-D-nnn are
# catalogued in docs/test-plans/docs.md. Run: scripts/test/test-doc-checks.sh
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
lint="scripts/docs-lint.sh"
tmp="$(mktemp -d)"
cp README.md "$tmp/README.md.orig"
fixtures=()
cleanup() { cp "$tmp/README.md.orig" README.md; rm -f "${fixtures[@]}"; rm -rf "$tmp"; }
trap cleanup EXIT
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}
fixture() { # path content
  mkdir -p "$(dirname "$1")"; printf '%b' "$2" > "$1"; fixtures+=("$1")
}
# passes <mode>: exit 0. fails <mode> <file>: non-zero and the output names the file.
passes() { "$lint" "$1" >"$tmp/out" 2>&1; }
fails() { "$lint" "$1" >"$tmp/out" 2>&1; local rc=$?; [ "$rc" -eq 1 ] && grep -qF "$2" "$tmp/out"; }

FMT='docs/zz-fixture-format.md'
LNT='docs/zz-fixture-lint.md'
fixture "$FMT" '# Format fixture\n\nTrailing spaces   \n\n\n\nNo final newline'
fixture "$LNT" '# Lint fixture\n\n### Skipped a heading level\n'

# TC-D-014 a format violation fails the format check and names the file; the lint check does not report it
fails format "$FMT"; check "TC-D-014 format check fails on trailing spaces, blank lines and a missing final newline" $?
lint_only() { "$lint" lint >"$tmp/out" 2>&1; [ "$?" -eq 1 ] && grep -qF "$LNT" "$tmp/out" && ! grep -qF "$FMT" "$tmp/out"; }
lint_only; check "TC-D-014 the lint check reports the lint fixture but not the format fixture" $?

# TC-D-015 a lint violation fails the lint check and names the file; the format check does not report it
fails lint "$LNT"; check "TC-D-015 lint check fails on a skipped heading level" $?
format_only() { "$lint" format >"$tmp/out" 2>&1; [ "$?" -eq 1 ] && grep -qF "$FMT" "$tmp/out" && ! grep -qF "$LNT" "$tmp/out"; }
format_only; check "TC-D-015 the format check reports the format fixture but not the lint fixture" $?

# TC-D-016 the existing entry point runs both checks
fails markdown "$FMT"; check "TC-D-016 markdown runs the format check" $?
fails markdown "$LNT"; check "TC-D-016 markdown runs the lint check" $?

# TC-D-017 coverage of docs/design and of the root README.md
DES='docs/design/zz-fixture-design.md'
fixture "$DES" '# Design fixture\n\nTrailing   \n'
fails format "$DES"; check "TC-D-017 a format violation in docs/design is caught" $?
printf '\n# Second title\n\n#### Skipped\n' >> README.md
fails lint README.md; check "TC-D-017 a lint violation in the root README.md is caught" $?
cp "$tmp/README.md.orig" README.md

# TC-D-018 fix repairs the format violation and leaves the lint violation alone
"$lint" fix >"$tmp/out" 2>&1
passes format; check "TC-D-018 fix makes the format check pass" $?
fails lint "$LNT"; check "TC-D-018 fix does not hide a lint violation" $?

# TC-D-019 removing the fixtures leaves both checks green on the real documents
rm -f "${fixtures[@]}"; fixtures=()
passes lint; check "TC-D-019 the lint check passes on the repository documents" $?
passes format; check "TC-D-019 the format check passes on the repository documents" $?
passes markdown; check "TC-D-019 markdown passes on the repository documents" $?

# TC-D-020 CI runs the checks: Gradle check calls markdown (lint and format), and this test runs in script-tests
grep -q 'docs-lint.sh markdown' build.gradle.kts; check "TC-D-020 ./gradlew check calls docs-lint.sh markdown" $?
grep -q 'scripts/test/test-doc-checks.sh' .github/workflows/build.yml; check "TC-D-020 the script-tests job runs test-doc-checks.sh" $?
exit $fail
