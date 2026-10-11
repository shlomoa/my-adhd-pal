#!/usr/bin/env bash
# Checking tools (step 0a.5): negative fixtures must fail `check`'s parts, then pass again once removed.
# Run: scripts/test/test-checks.sh   (needs the Android SDK and Node; see scripts/bootstrap-android.sh)
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
gradle() { ./gradlew --console=plain "$@" >"$out" 2>&1; }
# fails_with <expected text...>: the last gradle run failed for the stated reason (not because a task is missing)
fails_with() { local rc=$1; shift; [ "$rc" -ne 0 ] && ! grep -q "not found in root project\|not found in project" "$out" && for t in "$@"; do grep -qF -- "$t" "$out" || return 1; done; }
fixture() { fixtures+=("$1"); mkdir -p "$(dirname "$1")"; cat > "$1"; }

# TC-S-016 the tools run behind ./gradlew check and the workflow carries no tool list
tasks="$(./gradlew --console=plain -q check --dry-run 2>&1)"
for t in spotlessCheck detekt lintDebug lintDocs; do echo "$tasks" | grep -q "$t"; check "TC-S-016 check includes $t" 0 $?; done
! grep -qiE 'markdownlint|ktlint|detekt|spotless|remark' .github/workflows/*.yml; check "TC-S-016 workflows name no lint tool" 0 $?

# TC-S-017 every tool runs on the scaffold (compatibility with AGP 9.4 and Kotlin 2.4)
gradle check --rerun-tasks; check "TC-S-017 check is green on the scaffold" 0 $?
for t in ":spotlessCheck" ":detekt" ":lintDocs" ":phone:lintDebug"; do grep -qF "Task $t" "$out"; check "TC-S-017 $t ran as part of check" 0 $?; done

# TC-S-011 mis-formatted Kotlin fails spotlessCheck; spotlessApply repairs it
bad=wear/src/main/java/com/shlomoa/myadhdpal/wear/BadFormat.kt
fixture $bad <<'KT'
package com.shlomoa.myadhdpal.wear

class  BadFormat( val x:Int ){
 fun twice() = x*2
}
KT
gradle spotlessCheck; rc=$?
fails_with $rc BadFormat.kt; check "TC-S-011 mis-formatted Kotlin fails spotlessCheck and names the file" 0 $?
gradle spotlessApply spotlessCheck; check "TC-S-011 spotlessApply makes it pass" 0 $?
rm -f $bad

# TC-S-011 the committed tree passes the formatter in a fresh clone. Local state (stale results, untracked or ignored
# files) once made a developer machine pass while CI failed on trailing commas; a fresh clone of HEAD cannot be fooled.
clone="$(mktemp -d)"
git clone -q "$root" "$clone/repo" && git -C "$clone/repo" checkout -q "${CLONE_REF:-HEAD}" && cp local.properties "$clone/repo/" 2>/dev/null
( cd "$clone/repo" && ./gradlew --console=plain --no-build-cache --rerun-tasks spotlessCheck >"$out" 2>&1 ); check "TC-S-011 committed tree passes spotlessCheck in a fresh clone" 0 $?
rm -rf "$clone"

# TC-S-011 trailing commas are not allowed (ktlint android_studio style), the same on every machine
bad=wear/src/main/java/com/shlomoa/myadhdpal/wear/TrailingComma.kt
fixture $bad <<'KT'
package com.shlomoa.myadhdpal.wear

fun sum(
    a: Int,
    b: Int,
) = a + b
KT
gradle spotlessCheck --rerun-tasks --no-build-cache; rc=$?
fails_with $rc TrailingComma.kt; check "TC-S-011 a trailing comma fails spotlessCheck even on a clean run" 0 $?
rm -f $bad

# TC-S-012 detekt violation fails detekt and names the rule
bad=wear/src/main/java/com/shlomoa/myadhdpal/wear/EmptyBlock.kt
fixture $bad <<'KT'
package com.shlomoa.myadhdpal.wear

fun emptyOnPurpose() {}
KT
gradle detekt; rc=$?
fails_with $rc EmptyFunctionBlock; check "TC-S-012 detekt violation fails and names the rule" 0 $?
rm -f $bad
gradle detekt; check "TC-S-012 removal is green" 0 $?

# TC-S-013 a new Android lint issue (not in the baseline) fails lint
bad=phone/src/main/res/values/unused_fixture.xml
fixture $bad <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="unused_fixture">never used</string>
</resources>
XML
gradle :phone:lintDebug; rc=$?
fails_with $rc UnusedResources; check "TC-S-013 new lint issue fails lint and names the check" 0 $?
rm -f $bad
gradle :phone:lintDebug; check "TC-S-013 removal is green" 0 $?

# TC-S-014 broken internal link (missing file and missing heading) fails the docs link check
bad=docs/zz-fixture-links.md
fixture $bad <<'MD'
# Link fixture

See [missing file](zz-no-such-file.md) and [missing heading](plan.md#zz-no-such-heading).
MD
gradle lintDocs; rc=$?
fails_with $rc zz-fixture-links.md zz-no-such-file.md; check "TC-S-014 broken internal link fails and names file and target" 0 $?
rm -f $bad
gradle lintDocs; check "TC-S-014 fix is green" 0 $?

# TC-S-015 markdownlint violation fails and names rule and line
bad=docs/zz-fixture-style.md
fixture $bad <<'MD'
# Style fixture

#Heading without a space
MD
gradle lintDocs; rc=$?
fails_with $rc zz-fixture-style.md:3 MD018; check "TC-S-015 markdownlint violation fails and names rule and line" 0 $?
rm -f $bad
gradle lintDocs; check "TC-S-015 fix is green" 0 $?
exit $fail
