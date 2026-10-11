# Shared lane baseline (red)

Evidence that the tests for step 0a.8 (cloud bootstrap script) fail before `scripts/bootstrap-android.sh` exists.
Red commit: 1fad4a2 (tests only, no implementation).

## 0a.8 cloud bootstrap script

Command: `scripts/test/test-bootstrap-android.sh` (offline, stubbed network and `sdkmanager`). Exit 1, 0 PASS, 19 FAIL.
Exit 127 means the script is not found; the other failures are the observable effects not happening.

```
FAIL TC-S-026 fresh run exits 0 (expected 0, got 127)
FAIL TC-S-026 command-line tools installed (expected 0, got 1)
FAIL TC-S-026 sdkmanager asked for platform-tools (expected 0, got 2)
FAIL TC-S-026 sdkmanager asked for platforms;android-37.0 (expected 0, got 2)
FAIL TC-S-026 sdkmanager asked for build-tools;37.0.0 (expected 0, got 2)
FAIL TC-S-026 local.properties points at the SDK (expected 0, got 2)
FAIL TC-S-027 second run exits 0 (expected 0, got 127)
FAIL TC-S-027 second run changes nothing (expected 0, got 1)
FAIL TC-S-027 second run succeeds before download count is judged (expected 0, got 1)
FAIL TC-S-027 second run downloads nothing (expected 0, got 1)
FAIL TC-S-028 no mirror when central answers 200 (expected 0, got 1)
FAIL TC-S-028 refusing central still exits 0 (expected 0, got 127)
FAIL TC-S-028 mirror configured on 429 (expected 0, got 1)
FAIL TC-S-028 mirror removed again when central recovers (expected 0, got 1)
FAIL TC-S-030 checksum mismatch exits non-zero (expected 0, got 1)
FAIL TC-S-030 checksum mismatch is explained (expected 0, got 1)
FAIL TC-S-030 nothing half-installed after a bad download (expected 0, got 1)
FAIL TC-S-030 missing prerequisite exits non-zero (expected 0, got 1)
FAIL TC-S-030 missing prerequisite is named (expected 0, got 1)
```

TC-S-029 (fresh state then `./gradlew check`), command `scripts/test/fresh-bootstrap-check.sh`:

```
TC-S-029: bootstrapping a fresh SDK in /root/fresh-android-sdk.ftnKvC
scripts/test/fresh-bootstrap-check.sh: line 10: /home/user/my-adhd-pal/scripts/bootstrap-android.sh: No such file or directory
```

| Id | State |
|---|---|
| TC-S-026 | red |
| TC-S-027 | red |
| TC-S-028 | red |
| TC-S-029 | red |
| TC-S-030 | red |

## 0a.8 after implementation (green)

- `scripts/test/test-bootstrap-android.sh`: 20 PASS, 0 FAIL (TC-S-026, 027, 028, 030; TC-S-028 has four cases, including refusal under burst load only).
- `scripts/test/fresh-bootstrap-check.sh` (TC-S-029, real network, empty SDK dir): bootstrap installed command-line tools, platform-tools, platform 37.0 and build-tools 37.0.0, found Maven Central refusing (429) and wrote the mirror init script, then `./gradlew check` ended `BUILD SUCCESSFUL in 2m 29s`.
- Two earlier real runs failed, which shaped the probe: a single-request probe answered 200 while Gradle got 429. A parallel burst reproduces the refusal (8 of 24 requests), so the probe is a burst; the offline test for that was written red first.

## 0a.1-0a.3 restructure and skeletons (red)

Command: `scripts/test/test-structure.sh` on `main` (still single-module `:app`). Exit 1, 0 PASS, 15 FAIL.

```
FAIL TC-S-002 app renamed to wear (expected 0, got 1)
FAIL TC-S-002 settings include :wear :protocol :phone (expected 0, got 1)
FAIL TC-S-002 settings no longer include :app (expected 0, got 1)
FAIL TC-S-002 CI uploads the wear debug APK (expected 0, got 1)
FAIL TC-S-002 no stale app/ references in CI and README (expected 0, got 1)
FAIL TC-S-002 README names the wear module (expected 0, got 1)
FAIL TC-S-003 wear allowBackup off (expected 0, got 2)
FAIL TC-S-003 phone allowBackup off (expected 0, got 2)
FAIL TC-S-004 wear namespace (expected 0, got 2)
FAIL TC-S-004 phone namespace (expected 0, got 2)
FAIL TC-S-005 protocol applies no Android plugin (expected 0, got 2)
FAIL TC-S-005 protocol sources import nothing from Android (expected 0, got 1)
FAIL TC-S-006 wear and phone build, protocol tests run (expected 0, got 1)
FAIL TC-S-002 wear debug APK exists (expected 0, got 2)
FAIL TC-S-006 phone debug APK exists (expected 0, got 2)
```

TC-S-001 baseline (`./gradlew assembleDebug testDebugUnitTest lintDebug` on unmodified `main`) was green: BUILD SUCCESSFUL in 1m 20s, debug APK built, lint clean.
