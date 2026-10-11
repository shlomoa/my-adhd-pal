#!/usr/bin/env bash
# Cloud bootstrap (plan.md step 0a.8): installs the Android SDK pieces this repo builds with, so that
# ./gradlew works in a fresh Linux session (also callable from an environment setup script).
#
#   - Android command-line tools (pinned version, SHA-1 from Google's repository2-3.xml manifest)
#   - platform-tools, platform 37.0, build-tools 37.0.0 (via sdkmanager)
#   - sdk.dir in local.properties (gitignored) so Gradle finds the SDK
#   - a Gradle init script that points Maven Central at Google's mirror, ONLY if repo.maven.apache.org
#     refuses requests (it answers 429 in the cloud environment); removed again when Central answers
#
# Running it accepts the Android SDK licences (sdkmanager --licenses), as an interactive install would.
# Idempotent: a second run downloads and changes nothing.
#
# Environment overrides (used by the tests): ANDROID_HOME (default ~/Android/Sdk), GRADLE_USER_HOME
# (default ~/.gradle), BOOTSTRAP_PROJECT_DIR (default: repo root), CMDLINE_TOOLS_URL, CMDLINE_TOOLS_SHA1,
# MAVEN_PROBE_URLS (space-separated).
set -euo pipefail

CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip}"
CMDLINE_TOOLS_SHA1="${CMDLINE_TOOLS_SHA1:-e025545c62a8e64c7559119566a569fb1dec5f60}"
# Central's rate limiting is intermittent (one artifact answered 200 while Gradle got 429 on another), so probe
# several artifacts the build needs: any answer other than 200 counts as a refusal.
MAVEN_PROBE_URLS="${MAVEN_PROBE_URLS:-https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom https://repo.maven.apache.org/maven2/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.21/kotlin-stdlib-2.4.21.pom}"
MAVEN_MIRROR_URL="https://maven-central.storage-download.googleapis.com/maven2/"
PLATFORM_PACKAGE="platforms;android-37.0"
BUILD_TOOLS_PACKAGE="build-tools;37.0.0"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
project="${BOOTSTRAP_PROJECT_DIR:-$here/..}"
log() { echo "bootstrap-android: $*"; }
die() { echo "bootstrap-android: ERROR: $*" >&2; exit 1; }

missing=""
for tool in curl unzip sha1sum java; do
  command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
[ -z "$missing" ] || die "missing prerequisite(s):$missing. Install them (for example apt-get install curl unzip, and a JDK 21) and run again."

work=""
cleanup() { [ -z "$work" ] || rm -rf "$work"; }
trap cleanup EXIT

# 1. Command-line tools
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$sdkmanager" ]; then
  log "command-line tools already installed in $sdk"
else
  mkdir -p "$sdk"
  work="$(mktemp -d "$sdk/.bootstrap.XXXXXX")"
  log "downloading command-line tools"
  curl -fsSL --retry 3 --retry-delay 2 -o "$work/cmdline-tools.zip" "$CMDLINE_TOOLS_URL" \
    || die "download of $CMDLINE_TOOLS_URL failed"
  actual="$(sha1sum "$work/cmdline-tools.zip" | cut -d' ' -f1)"
  [ "$actual" = "$CMDLINE_TOOLS_SHA1" ] \
    || die "checksum mismatch for command-line tools: expected $CMDLINE_TOOLS_SHA1, got $actual. Nothing was installed."
  unzip -q "$work/cmdline-tools.zip" -d "$work/unpacked"
  mkdir -p "$sdk/cmdline-tools"
  mv "$work/unpacked/cmdline-tools" "$sdk/cmdline-tools/latest"
  log "installed command-line tools"
fi

# 2. SDK packages
if [ -d "$sdk/platform-tools" ] && [ -d "$sdk/platforms/android-37.0" ] && [ -d "$sdk/build-tools/37.0.0" ]; then
  log "platform-tools, $PLATFORM_PACKAGE and $BUILD_TOOLS_PACKAGE already installed"
else
  log "accepting licences and installing platform-tools, $PLATFORM_PACKAGE, $BUILD_TOOLS_PACKAGE"
  # `yes` gets SIGPIPE when sdkmanager stops reading; that is expected, so only sdkmanager's status counts.
  set +o pipefail
  yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null
  set -o pipefail
  "$sdkmanager" --sdk_root="$sdk" "platform-tools" "$PLATFORM_PACKAGE" "$BUILD_TOOLS_PACKAGE"
fi

# 3. Point Gradle at the SDK
props="$project/local.properties"
if ! grep -qxF "sdk.dir=$sdk" "$props" 2>/dev/null; then
  { grep -v '^sdk\.dir=' "$props" 2>/dev/null || true; echo "sdk.dir=$sdk"; } > "$props.tmp"
  mv "$props.tmp" "$props"
  log "wrote sdk.dir to $props"
fi

# 4. Maven Central mirror, only when Central refuses requests
init_dir="$gradle_home/init.d"
init_script="$init_dir/maven-central-mirror.gradle"
status=200
for url in $MAVEN_PROBE_URLS; do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$url" || true)"
  [ "$code" = "200" ] || { status="${code:-no response}"; break; }
done
if [ "$status" = "200" ]; then
  if [ -e "$init_script" ]; then rm -f "$init_script"; log "Maven Central answers 200; removed the mirror init script"; fi
  log "Maven Central answers 200; no mirror needed"
else
  log "Maven Central answered '${status:-no response}'; using Google's Maven Central mirror"
  mkdir -p "$init_dir"
  cat > "$init_script" <<GRADLE
// Written by scripts/bootstrap-android.sh because repo.maven.apache.org refused requests.
// Remove this file (or rerun the script when Central answers) to use Maven Central directly.
def central = 'https://repo.maven.apache.org/maven2'
def mirror = '$MAVEN_MIRROR_URL'
beforeSettings { settings ->
    [settings.pluginManagement.repositories, settings.dependencyResolutionManagement.repositories].each { repos ->
        repos.all { repo ->
            if (repo instanceof MavenArtifactRepository && repo.url.toString().startsWith(central)) {
                repo.url = mirror
            }
        }
    }
}
GRADLE
fi

log "done. Android SDK: $sdk"
