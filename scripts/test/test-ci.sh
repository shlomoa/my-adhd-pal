#!/usr/bin/env bash
# CI definition and the release-has-no-debug check (step 0a.6). Run: scripts/test/test-ci.sh
# Debug-only code lives in a `debug` package (com.shlomoa.myadhdpal.<module>.debug, sources in src/debug/);
# the release APKs must contain no such class.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
fail=0
out="$(mktemp)"
fixtures=()
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}
cleanup() { for f in "${fixtures[@]:-}"; do [ -z "$f" ] || rm -f "$f"; done; rm -f "$out"; }
trap cleanup EXIT
wf=.github/workflows/build.yml
has() { grep -qE -- "$1" "$2" 2>/dev/null; }

# TC-S-018 the workflow runs on pull requests with a build job (check + assembleDebug) and a separate external-links job
has '^  pull_request:|^  pull_request$|pull_request' "$wf"; check "TC-S-018 workflow triggers on pull_request" 0 $?
has '^  build:' "$wf" && has 'run: \./gradlew check assembleDebug' "$wf"; check "TC-S-018 build job runs ./gradlew check assembleDebug" 0 $?
has '^  external-links:' "$wf" && has 'run: \./gradlew checkExternalLinks' "$wf"; check "TC-S-018 external-links job runs ./gradlew checkExternalLinks" 0 $?
! has 'run: .*(markdownlint|remark|ktlint|detekt|spotless)' "$wf"; check "TC-S-018 workflow carries no tool list" 0 $?

# TC-S-018 the lane test scripts run in CI: the watch lane's debug separation script after the build, ours in their own job
has 'run: wear/src/test/scripts/test-debug-separation.sh' "$wf"; check "TC-S-018 build job runs the watch debug-separation script" 0 $?
has '^  script-tests:' "$wf"; check "TC-S-018 a script-tests job exists" 0 $?
for t in test-structure test-pairing test-checks test-ci; do
  has "scripts/test/$t.sh" "$wf"; check "TC-S-018 script-tests runs $t.sh" 0 $?
done

# TC-S-019 the external job never blocks: failures are tolerated and turned into a warning annotation
has 'continue-on-error: true' "$wf" && has '::warning::' "$wf"; check "TC-S-019 external job tolerates failure and warns" 0 $?
mkdir -p "$root/docs" && printf '# Dead link\n\n[dead](http://127.0.0.1:9/dead)\n' > docs/zz-fixture-external.md; fixtures+=(docs/zz-fixture-external.md)
git add -N docs/zz-fixture-external.md
scripts/docs-lint.sh external >"$out" 2>&1; rc=$?
[ "$rc" -ne 0 ] && grep -q 'zz-fixture-external.md' "$out"; check "TC-S-019 a dead external link makes the external check fail (and only that job)" 0 $?
git rm -q --cached docs/zz-fixture-external.md; rm -f docs/zz-fixture-external.md
./gradlew --console=plain -q check --dry-run 2>&1 | grep -q 'checkExternalLinks'; r=$?
check "TC-S-019 external links are not part of check" 1 $r

# TC-S-021 release builds contain no debug code (positive) and the check is part of check
./gradlew --console=plain -q check --dry-run 2>&1 | grep -q 'checkNoDebugInRelease'; check "TC-S-021 checkNoDebugInRelease is part of check" 0 $?
./gradlew --console=plain checkNoDebugInRelease >"$out" 2>&1; check "TC-S-021 clean release builds pass" 0 $?

# TC-S-020 a debug class in a release build fails the check and names the class
leak=wear/src/main/java/com/shlomoa/myadhdpal/wear/debug/LeakedLogger.kt
mkdir -p "$(dirname "$leak")"; fixtures+=("$leak")
printf 'package com.shlomoa.myadhdpal.wear.debug\n\nclass LeakedLogger\n' > "$leak"
./gradlew --console=plain checkNoDebugInRelease >"$out" 2>&1; rc=$?
[ "$rc" -ne 0 ] && ! grep -q 'not found in root project' "$out" && grep -q 'LeakedLogger' "$out"; check "TC-S-020 debug class in release fails and names the class" 0 $?
rm -f "$leak"
./gradlew --console=plain checkNoDebugInRelease >"$out" 2>&1; check "TC-S-020 removal is green" 0 $?
exit $fail
