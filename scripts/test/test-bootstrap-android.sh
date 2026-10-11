#!/usr/bin/env bash
# Offline tests for scripts/bootstrap-android.sh (network and sdkmanager are stubbed).
# Run: scripts/test/test-bootstrap-android.sh
# TC-S-029 (fresh state, then ./gradlew check, real network) is scripts/test/fresh-bootstrap-check.sh.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../bootstrap-android.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}

# Fixture: a command-line-tools zip whose sdkmanager only records its arguments.
mkdir -p "$tmp/fixture/cmdline-tools/bin"
cat > "$tmp/fixture/cmdline-tools/bin/sdkmanager" <<'SH'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_SDK_LOG"
SH
chmod +x "$tmp/fixture/cmdline-tools/bin/sdkmanager"
(cd "$tmp/fixture" && zip -qr "$tmp/cmdline.zip" cmdline-tools)
sha="$(sha1sum "$tmp/cmdline.zip" | cut -d' ' -f1)"

# curl shim: "-w" means the Maven probe (status from FAKE_CENTRAL_CODE); "-o file" means a download.
mkdir -p "$tmp/shim"
cat > "$tmp/shim/curl" <<'SH'
#!/usr/bin/env bash
all="$*"; out=""; probe=0
while [ "$#" -gt 0 ]; do
  case "$1" in -o) out="$2"; shift;; -w) probe=1;; esac
  shift
done
if [ "$probe" = 1 ]; then
  case "$all" in *"${FAKE_FAIL_MATCH:-@@none@@}"*) printf 429; exit 0;; esac
  printf '%s' "${FAKE_CENTRAL_CODE:-200}"; exit 0
fi
echo download >> "$FAKE_DOWNLOADS"
cp "$FAKE_ZIP" "$out"
SH
chmod +x "$tmp/shim/curl"

run() { # extra env assignments are passed as arguments
  env PATH="$tmp/shim:$PATH" ANDROID_HOME="$tmp/sdk" GRADLE_USER_HOME="$tmp/gradle" \
    BOOTSTRAP_PROJECT_DIR="$tmp/project" CMDLINE_TOOLS_URL="http://fake/cmdline.zip" CMDLINE_TOOLS_SHA1="$sha" \
    FAKE_ZIP="$tmp/cmdline.zip" FAKE_SDK_LOG="$tmp/sdk.log" FAKE_DOWNLOADS="$tmp/downloads.log" \
    "$@" "$script" >"$tmp/out.log" 2>&1
}
mkdir -p "$tmp/project"
: > "$tmp/downloads.log"

# TC-S-026 fresh state installs command-line tools and the three packages
run FAKE_CENTRAL_CODE=200; rc=$?
check "TC-S-026 fresh run exits 0" 0 $rc
[ -x "$tmp/sdk/cmdline-tools/latest/bin/sdkmanager" ]; check "TC-S-026 command-line tools installed" 0 $?
for p in platform-tools platforms\;android-37.0 build-tools\;37.0.0; do
  grep -qF "$p" "$tmp/sdk.log" 2>/dev/null; check "TC-S-026 sdkmanager asked for $p" 0 $?
done
grep -q "^sdk.dir=$tmp/sdk\$" "$tmp/project/local.properties" 2>/dev/null; check "TC-S-026 local.properties points at the SDK" 0 $?

# TC-S-027 second run changes nothing
before="$(cat "$tmp/project/local.properties"; ls -R "$tmp/sdk" "$tmp/gradle" 2>/dev/null)"
n="$(wc -l < "$tmp/downloads.log")"
run FAKE_CENTRAL_CODE=200; rc=$?
check "TC-S-027 second run exits 0" 0 $rc
after="$(cat "$tmp/project/local.properties"; ls -R "$tmp/sdk" "$tmp/gradle" 2>/dev/null)"
[ -f "$tmp/project/local.properties" ] && [ "$before" = "$after" ]; check "TC-S-027 second run changes nothing" 0 $?
[ "$rc" -eq 0 ]; check "TC-S-027 second run succeeds before download count is judged" 0 $?
[ "$n" -ge 1 ] && [ "$n" = "$(wc -l < "$tmp/downloads.log")" ]; check "TC-S-027 second run downloads nothing" 0 $?

# TC-S-028 the Maven mirror is configured only when Maven Central refuses
init="$tmp/gradle/init.d/maven-central-mirror.gradle"
[ -d "$tmp/sdk/cmdline-tools" ] && [ ! -e "$init" ]; check "TC-S-028 no mirror when central answers 200" 0 $?
run FAKE_CENTRAL_CODE=429; rc=$?
check "TC-S-028 refusing central still exits 0" 0 $rc
[ -f "$init" ] && grep -q 'maven-central.storage-download.googleapis.com' "$init"; check "TC-S-028 mirror configured on 429" 0 $?
run FAKE_CENTRAL_CODE=200 FAKE_FAIL_MATCH=guava; rc=$?
[ "$rc" -eq 0 ] && [ -f "$init" ]; check "TC-S-028 mirror configured when only one probed artifact is refused" 0 $?
run FAKE_CENTRAL_CODE=200; rc=$?
[ "$rc" -eq 0 ] && [ ! -e "$init" ]; check "TC-S-028 mirror removed again when central recovers" 0 $?

# TC-S-030 failure paths exit non-zero with a clear message
bad="$(mktemp -d)"; mkdir -p "$tmp/project2"
run ANDROID_HOME="$bad/sdk" CMDLINE_TOOLS_SHA1=0000000000000000000000000000000000000000; rc=$?
[ "$rc" -ne 0 ] && [ "$rc" -ne 127 ]; check "TC-S-030 checksum mismatch exits non-zero" 0 $?
grep -qi 'checksum' "$tmp/out.log"; check "TC-S-030 checksum mismatch is explained" 0 $?
[ "$rc" -ne 127 ] && [ ! -e "$bad/sdk/cmdline-tools/latest" ]; check "TC-S-030 nothing half-installed after a bad download" 0 $?
mkdir -p "$tmp/nobin"
for t in bash env dirname mkdir rm mv cp cat sha1sum cut head tr grep sed mktemp date; do p="$(command -v $t)" && ln -sf "$p" "$tmp/nobin/$t"; done
env PATH="$tmp/nobin" ANDROID_HOME="$tmp/sdk3" GRADLE_USER_HOME="$tmp/gradle3" BOOTSTRAP_PROJECT_DIR="$tmp/project" \
  "$tmp/nobin/bash" "$script" >"$tmp/out.log" 2>&1; rc=$?
[ "$rc" -ne 0 ] && [ "$rc" -ne 127 ]; check "TC-S-030 missing prerequisite exits non-zero" 0 $?
grep -qiE 'curl|unzip' "$tmp/out.log"; check "TC-S-030 missing prerequisite is named" 0 $?

exit $fail
