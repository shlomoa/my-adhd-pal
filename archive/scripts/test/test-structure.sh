#!/usr/bin/env bash
# Structure checks for the three-module layout (steps 0a.2, 0a.3). Run: scripts/test/test-structure.sh
# Static checks first, then the real Gradle builds (TC-S-006), so it needs the Android SDK (scripts/bootstrap-android.sh).
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
fail=0
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else
    echo "FAIL $1 (expected $2, got $3)"; fail=1
    [ -s "$log" ] && { echo "  --- last output of the Gradle build:"; tail -n 25 "$log" | sed 's/^/  | /'; }
  fi
}
has() { grep -qE -- "$1" "$2" 2>/dev/null; }

# TC-S-002 restructure: :app became :wear and the CI APK path follows
[ ! -d app ] && [ -d wear ]; check "TC-S-002 app renamed to wear" 0 $?
has 'include\(":wear"\)' settings.gradle.kts && has 'include\(":protocol"\)' settings.gradle.kts && has 'include\(":phone"\)' settings.gradle.kts
check "TC-S-002 settings include :wear :protocol :phone" 0 $?
! has 'include\(":app"\)' settings.gradle.kts; check "TC-S-002 settings no longer include :app" 0 $?
has 'wear/build/outputs/apk/debug' .github/workflows/build.yml; check "TC-S-002 CI uploads the wear debug APK" 0 $?
! grep -rqE '(^|[^a-z])app/build|:app:' .github README.md 2>/dev/null; check "TC-S-002 no stale app/ references in CI and README" 0 $?
has 'wear/build' README.md || has 'wear/' README.md; check "TC-S-002 README names the wear module" 0 $?

# TC-S-003 allowBackup off in both apps
has 'android:allowBackup="false"' wear/src/main/AndroidManifest.xml; check "TC-S-003 wear allowBackup off" 0 $?
has 'android:allowBackup="false"' phone/src/main/AndroidManifest.xml; check "TC-S-003 phone allowBackup off" 0 $?

# TC-S-004 namespaces
has 'namespace = "com.shlomoa.myadhdpal.wear"' wear/build.gradle.kts; check "TC-S-004 wear namespace" 0 $?
has 'namespace = "com.shlomoa.myadhdpal.phone"' phone/build.gradle.kts; check "TC-S-004 phone namespace" 0 $?

# TC-S-005 :protocol is pure Kotlin/JVM
has 'kotlin.jvm' protocol/build.gradle.kts && ! has 'android' protocol/build.gradle.kts; check "TC-S-005 protocol applies no Android plugin" 0 $?
! grep -rqE '^import android\.|^import androidx\.' protocol/src 2>/dev/null && [ -d protocol/src ]; check "TC-S-005 protocol sources import nothing from Android" 0 $?

# TC-S-006 both apps build and the protocol tests run
./gradlew --console=plain :wear:assembleDebug :phone:assembleDebug :protocol:test >"$log" 2>&1; check "TC-S-006 wear and phone build, protocol tests run" 0 $?
ls wear/build/outputs/apk/debug/*.apk >/dev/null 2>&1; check "TC-S-002 wear debug APK exists" 0 $?
ls phone/build/outputs/apk/debug/*.apk >/dev/null 2>&1; check "TC-S-006 phone debug APK exists" 0 $?
exit $fail
