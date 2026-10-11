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
