#!/usr/bin/env bash
# Shared configuration and pairing check (step 0a.4). Run: scripts/test/test-pairing.sh
# Negative fixtures edit phone/build.gradle.kts in place and always restore it (git checkout) on exit.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
fail=0
out="$(mktemp)"
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}
restore() { git checkout -q -- phone/build.gradle.kts wear/build.gradle.kts; rm -f "$out"; }
trap restore EXIT
gradle() { ./gradlew --console=plain -q "$@" >"$out" 2>&1; }

# TC-S-007 the pairing check passes on the scaffold and is part of check
gradle checkPairing; check "TC-S-007 pairing check passes with equal applicationId and signing config" 0 $?
./gradlew --console=plain -q check --dry-run 2>&1 | grep -q 'checkPairing'; check "TC-S-007 pairing check is part of check" 0 $?

# TC-S-010 SDK levels and applicationId come from one place
! grep -nE '^\s*(applicationId|minSdk|targetSdk|compileSdk)\s*=' wear/build.gradle.kts phone/build.gradle.kts; check "TC-S-010 module scripts define no applicationId or SDK level" 0 $?
gradle checkPairing --info
for m in wear phone; do
  grep -q ":$m: applicationId=com.shlomoa.myadhdpal compileSdk=37 minSdk=30 targetSdk=36" "$out"; check "TC-S-010 :$m reports the shared values" 0 $?
done

# TC-S-008 a changed applicationId fails check and names both ids
printf '\nandroid { defaultConfig { applicationId = "com.example.other" } }\n' >> phone/build.gradle.kts
gradle check -x lint -x test; rc=$?
[ "$rc" -ne 0 ] && ! grep -q "not found" "$out"; check "TC-S-008 changed applicationId makes check fail" 0 $?
grep -q 'com.example.other' "$out" && grep -q 'com.shlomoa.myadhdpal' "$out" && grep -qi 'applicationId' "$out"; check "TC-S-008 message names both applicationIds" 0 $?
git checkout -q -- phone/build.gradle.kts
gradle checkPairing; check "TC-S-008 revert is green" 0 $?

# TC-S-009 a differing signing config fails the check
printf '\nandroid { signingConfigs { create("other") { storeFile = file("other.jks") } }\n  buildTypes { release { signingConfig = signingConfigs.getByName("other") } } }\n' >> phone/build.gradle.kts
gradle checkPairing; rc=$?
[ "$rc" -ne 0 ] && ! grep -q "not found" "$out"; check "TC-S-009 differing signing config makes the check fail" 0 $?
grep -qi 'signing' "$out"; check "TC-S-009 message mentions the signing config" 0 $?
git checkout -q -- phone/build.gradle.kts
gradle checkPairing; check "TC-S-009 revert is green" 0 $?
exit $fail
