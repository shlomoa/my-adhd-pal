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

## 0a.1-0a.3 after implementation (green)

- `scripts/test/test-structure.sh`: 15 PASS, 0 FAIL (TC-S-002 to TC-S-006), including the real build of both apps and `:protocol:test`.
- `./gradlew assembleDebug testDebugUnitTest lintDebug :protocol:test` (the CI command): BUILD SUCCESSFUL; `wear/build/outputs/apk/debug/wear-debug.apk` and `phone/build/outputs/apk/debug/phone-debug.apk` exist.

## 0a.4 shared configuration and pairing check (red)

Command: `scripts/test/test-pairing.sh` on the 0a.1-0a.3 tree (module scripts still define the values; no `checkPairing` task). Exit 1, 0 PASS, 11 FAIL.

```
FAIL TC-S-007 pairing check passes with equal applicationId and signing config (expected 0, got 1)
FAIL TC-S-007 pairing check is part of check (expected 0, got 1)
FAIL TC-S-010 module scripts define no applicationId or SDK level (expected 0, got 1)
FAIL TC-S-010 :wear reports the shared values (expected 0, got 1)
FAIL TC-S-010 :phone reports the shared values (expected 0, got 1)
FAIL TC-S-008 changed applicationId makes check fail (expected 0, got 1)
FAIL TC-S-008 message names both applicationIds (expected 0, got 1)
FAIL TC-S-008 revert is green (expected 0, got 1)
FAIL TC-S-009 differing signing config makes the check fail (expected 0, got 1)
FAIL TC-S-009 message mentions the signing config (expected 0, got 1)
FAIL TC-S-009 revert is green (expected 0, got 1)
```

## 0a.4 after implementation (green)

- `scripts/test/test-pairing.sh`: 11 PASS, 0 FAIL (TC-S-007 to TC-S-010) with the negative fixtures (changed `applicationId`, differing signing config) failing `check` and the revert green.
- `./gradlew assembleDebug testDebugUnitTest lintDebug :protocol:test check`: BUILD SUCCESSFUL; `scripts/test/test-structure.sh` still 15 PASS.
- Test-harness fix: the restore hook first used `git checkout`, which also reverted uncommitted module edits; it now restores from a backup copy.
- Mechanism: root `build.gradle.kts` configures both apps from the `[versions]` block of `gradle/libs.versions.toml` (single place); `checkPairing` (part of `check`) compares applicationId, SDK levels and signing config of the evaluated apps. A convention plugin in an included `build-logic` build was rejected for now: it compiles with Gradle's embedded Kotlin against AGP 9.4, a metadata-version risk, for no gain at two modules.

## 0a.5 checking tools (red)

Command: `scripts/test/test-checks.sh` before any tool is configured. Exit 1.

```
FAIL TC-S-016 check includes spotlessCheck (expected 0, got 1)
FAIL TC-S-016 check includes detekt (expected 0, got 1)
PASS TC-S-016 check includes lintDebug
FAIL TC-S-016 check includes lintDocs (expected 0, got 1)
PASS TC-S-016 workflows name no lint tool
PASS TC-S-017 check is green on the scaffold
FAIL TC-S-017 :spotlessCheck ran as part of check (expected 0, got 1)
FAIL TC-S-017 :detekt ran as part of check (expected 0, got 1)
FAIL TC-S-017 :lintDocs ran as part of check (expected 0, got 1)
PASS TC-S-017 :phone:lintDebug ran as part of check
FAIL TC-S-011 mis-formatted Kotlin fails spotlessCheck and names the file (expected 0, got 1)
FAIL TC-S-011 spotlessApply makes it pass (expected 0, got 1)
FAIL TC-S-012 detekt violation fails and names the rule (expected 0, got 1)
FAIL TC-S-012 removal is green (expected 0, got 1)
FAIL TC-S-013 new lint issue fails lint and names the check (expected 0, got 1)
PASS TC-S-013 removal is green
FAIL TC-S-014 broken internal link fails and names file and target (expected 0, got 1)
FAIL TC-S-014 fix is green (expected 0, got 1)
FAIL TC-S-015 markdownlint violation fails and names rule and line (expected 0, got 1)
FAIL TC-S-015 fix is green (expected 0, got 1)
```

The PASS rows are existing or vacuous behaviour (lint already ran in `check`; a fixture-free revert is trivially green); the rows that matter are the FAILs.

## 0a.5 after implementation (green)

- `scripts/test/test-checks.sh`: 20 PASS, 0 FAIL (TC-S-011 to TC-S-017): mis-formatted Kotlin fails `spotlessCheck` and is repaired by `spotlessApply`; an `EmptyFunctionBlock` fixture fails `detekt`; an unused-resource fixture fails Android lint (baseline holds only the pre-existing monochrome-icon warning); a broken internal file link and heading, and a `MD018` style violation, fail `lintDocs` naming file and target or rule and line; every fixture removed afterwards leaves `check` green.
- `./gradlew check --rerun-tasks`: BUILD SUCCESSFUL with `spotlessCheck`, `detekt`, `lintDocs`, `lintDebug` and `checkPairing` in the task graph; `scripts/test/test-structure.sh` 15 PASS, `test-pairing.sh` 11 PASS, `test-bootstrap-android.sh` 21 PASS, `test-scripts.sh` 19 PASS.
- Compatibility (AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1): Spotless 8.10.4 with ktlint 1.8.0 and detekt 2.0.0-alpha.6 (`dev.detekt`) both run. detekt 2 is a pre-release; the stable 1.23.8 cannot type-resolve against the Kotlin 2.4 stdlib, so no stable choice exists yet. Docs: markdownlint-cli2 0.23.3, remark-validate-links 13.1.0 (internal files and headings), markdown-link-check 3.15.0 (external, not in `check`), pinned by `tools/docs/package-lock.json`. lychee was not usable: cloud sessions cannot download release binaries of other GitHub repositories.

## 0a.6 CI (red)

Command: `scripts/test/test-ci.sh` before the external-links job, the warning step and `checkNoDebugInRelease` exist. Exit 1. The PASS rows are behaviour already present (the build job already ran `check assembleDebug`; `scripts/docs-lint.sh external` already fails on a dead link); the FAIL rows are the new work.

```
PASS TC-S-018 workflow triggers on pull_request
PASS TC-S-018 build job runs ./gradlew check assembleDebug
FAIL TC-S-018 external-links job runs ./gradlew checkExternalLinks (expected 0, got 1)
PASS TC-S-018 workflow carries no tool list
FAIL TC-S-019 external job tolerates failure and warns (expected 0, got 1)
PASS TC-S-019 a dead external link makes the external check fail (and only that job)
PASS TC-S-019 external links are not part of check
FAIL TC-S-021 checkNoDebugInRelease is part of check (expected 0, got 1)
FAIL TC-S-021 clean release builds pass (expected 0, got 1)
FAIL TC-S-020 debug class in release fails and names the class (expected 0, got 1)
FAIL TC-S-020 removal is green (expected 0, got 1)
```

## 0a.7 conventions (red)

Command: `scripts/test/test-conventions.sh` before the issue template, `scripts/check-issues.sh` and `scripts/check-work-log.sh` exist. Exit 1, 0 PASS, 15 FAIL (script not found = exit 127; template missing).

```
FAIL TC-S-022 issue template has front matter and a Validation plan section (expected 0, got 1)
FAIL TC-S-022 template asks what, how and pass criteria (expected 0, got 2)
FAIL TC-S-023 complete, labelled issues pass (expected 0, got 127)
FAIL TC-S-023 duplicate issue for a step fails and names the step (expected 0, got 1)
FAIL TC-S-023 a closed not-planned duplicate is ignored (expected 0, got 127)
FAIL TC-S-023 missing issue fails and names the step (expected 0, got 1)
FAIL TC-S-024 missing milestone label fails (expected 0, got 1)
FAIL TC-S-024 missing lane label fails (expected 0, got 1)
FAIL TC-S-024 wrong milestone label fails (expected 0, got 1)
FAIL TC-S-023 an issue not starting with a Validation plan fails (expected 0, got 1)
FAIL TC-S-023 plan.md yields 59 step ids (expected 0, got 1)
FAIL TC-S-025 a complete entry passes (expected 0, got 127)
FAIL TC-S-025 an entry without Validation fails (expected 0, got 1)
FAIL TC-S-025 an entry heading without date and step fails (expected 0, got 1)
FAIL TC-S-025 the shared work log follows the format (expected 0, got 127)
```

## 0a.6-0a.7 after implementation (green)

- `scripts/test/test-ci.sh`: 11 PASS, 0 FAIL (TC-S-018 to TC-S-021): the workflow has a `build` job running `./gradlew check assembleDebug` and a separate `external-links` job (`continue-on-error` plus a `::warning::` step); a dead external link fails `scripts/docs-lint.sh external` but external links are not part of `check`; `checkNoDebugInRelease` (part of `check`) passes on clean release builds and fails, naming the class, for a `...wear.debug.LeakedLogger` fixture.
- `scripts/test/test-conventions.sh`: 15 PASS, 0 FAIL (TC-S-022 to TC-S-025): issue template, `scripts/check-issues.sh` (duplicate, missing, milestone, lane and Validation plan fixtures; plan.md yields 59 step ids), `scripts/check-work-log.sh` (the shared log passes).
- `./gradlew check` green. The live issue check (TC-S-023 and TC-S-024 against the repository's real issues) runs in the *Conventions* workflow on this PR. TC-S-022's rendering on GitHub's new-issue page is manual: the user opens *New issue* and sees the *Plan step* template.

## 0b.1 library compatibility (red)

Command: `./gradlew :phone:testDebugUnitTest` with the injection sample and DAO sample tests (TC-S-120) added and no Hilt, KSP, Room or Robolectric dependency. Exit 1:

```
e: phone/src/test/java/com/shlomoa/myadhdpal/phone/compat/DaoSampleTest.kt:4:17 Unresolved reference 'room'.
e: phone/src/test/java/com/shlomoa/myadhdpal/phone/compat/DaoSampleTest.kt:5:17 Unresolved reference 'room'.
e: phone/src/test/java/com/shlomoa/myadhdpal/phone/compat/DaoSampleTest.kt:6:17 Unresolved reference 'room'.
e: phone/src/test/java/com/shlomoa/myadhdpal/phone/compat/DaoSampleTest.kt:7:17 Unresolved reference 'room'.
```

## 0b.1 after implementation (green)

- `./gradlew --no-build-cache --rerun-tasks check assembleDebug`: BUILD SUCCESSFUL (2m 55s) with TC-S-120 in `InjectionSampleTest` (Hilt) and `DaoSampleTest` (Room), 1 test each, both PASS under Robolectric, plus Spotless, detekt, lint, docs and pairing checks.
- Versions that work together (AGP 9.4.1 built-in Kotlin, Kotlin 2.4.21, Gradle 9.8.1): Hilt 2.60.1 (plugin `com.google.dagger.hilt.android`, `hilt-android`, `hilt-compiler` through `ksp`), KSP 2.3.12 (`com.google.devtools.ksp`), Room 2.8.5 (`room-runtime`, `room-ktx`, `room-compiler` through `ksp`), Robolectric 4.17.
- Findings: (1) no `kapt` and no `org.jetbrains.kotlin.android` plugin are needed; (2) Robolectric 4.17 on Android 16 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` for the unit-test JVM and `isIncludeAndroidResources = true`, otherwise it falls back to SDK 23 and fails; (3) Robolectric downloads its Android runtime jar from Maven Central at test time, so the bootstrap mirror script now also sets `robolectric.dependency.repo.url` when Central refuses (TC-S-028, red first); (4) detekt and ktlint run on the new code without changes.

## 0a.5 CI divergence (Spotless) and its regression guard

- Symptom: CI failed `:spotlessKotlinCheck` on PR #77 (trailing commas in `MyAdhdPalApp.kt`) while the local run had been green. Reproduced faithfully with a fresh clone of the failing commit 16414ba (also with an empty Gradle user home): `spotlessCheck --no-build-cache --rerun-tasks` exits 1, as CI did. Earlier green local runs came from state in the original working directory that I could not pin down (my attempts: stale Spotless/Gradle caches and `.editorconfig` as an input were tried and did not explain it, so no speculative fix was kept).
- Fix: the code is formatted as ktlint `android_studio` requires (no trailing commas).
- Guard: TC-S-011 now includes "committed tree passes spotlessCheck in a fresh clone" (`scripts/test/test-checks.sh`, `CLONE_REF` selects the commit). Red proof: the same steps at 16414ba exit 1; on the fixed tree PASS. A second TC-S-011 case pins the policy: a trailing comma fails `spotlessCheck` on a clean run.
- `scripts/test/test-checks.sh`: 22 PASS, 0 FAIL.
- The `Deprecated Gradle features ... incompatible with Gradle 10` line is `Configuration.setVisible` (removal in Gradle 11) emitted during configuration by a plugin, not by our scripts; it does not affect the exit status (CI failed only on Spotless).

## CI follow-ups (red)

Before wiring the lane scripts into the workflow and widening the detekt test exclusions: `scripts/test/test-ci.sh` 6 new FAIL (watch debug-separation step and the `script-tests` job with four scripts), and TC-S-012 `src/testDebug` backticked test name flagged by detekt (FAIL), with the control (same name in `src/main` is flagged) PASS.

## CI follow-ups (green)

- `scripts/test/test-ci.sh` all PASS (the workflow runs the Watch debug-separation script after the build and a `script-tests` job with the four lane scripts); `bash` run of the new TC-S-012 cases: `src/testDebug` backticked test names are not flagged, the same name in `src/main` still is.
- CI-faithful dry run in a fresh clone with `--no-build-cache`: `check assembleDebug`, `wear/src/test/scripts/test-debug-separation.sh`, `test-structure.sh`, `test-pairing.sh`, `test-checks.sh`, `test-ci.sh`, all exit 0.

## CI follow-ups: script-tests job red on its first CI run

- Red: in a fresh clone, `test-structure.sh` then `test-pairing.sh` (the CI job's order, `-Dorg.gradle.caching=false`) gives `FAIL TC-S-008 changed applicationId makes check fail`, as on CI. Cause: the guard `! grep -q "not found"` meant to catch a missing Gradle task also matched Android lint's notice "...listed in the baseline file but not found in the project; perhaps they have been fixed?", which prints only when lint actually executes (clean runner), not from cache.
- Fix: the guard now matches Gradle's missing-task message (`Task '...' not found`), the same intent without the false match; failing cases print the last 25 lines of the command output; the four scripts are separate CI steps so one failure no longer hides the others (the job's single multi-line step had stopped after `test-pairing.sh`, so `test-checks.sh` and `test-ci.sh` had not run on CI).

## 1.1-1.3 protocol spec, constants, models and codec (red)

Red commit: tests only (`PathsTest`, `ConstantsTest`, `CodecTest` in `:protocol`, `scripts/test/test-protocol-spec.sh`); no implementation.

- `./gradlew :protocol:test`: BUILD FAILED at `:protocol:compileTestKotlin`, `Unresolved reference 'Message'` (and the other protocol types: `Paths`, `Codec`, `Schedule`, ...). TC-S-042..055 cannot run.
- `scripts/test/test-protocol-spec.sh` (TC-S-040, TC-S-041): exit 1, 7 FAIL, 0 PASS (no `Item catalog`, `Item format`, `Versioning policy` or `Link states` section in `docs/protocol.md`, no `Paths.kt`).

## 1.8a transport-neutral diagrams (red)

Red commit: the TC-S-077 structure checks in `scripts/test/test-protocol-spec.sh` only, before the `## Diagrams` section exists in `docs/protocol.md`: exit 1, 9 FAIL (each of the four diagrams missing as a Mermaid block of the right type, and the prompt state machine without a start and an end); the balanced-block checks pass trivially on empty input.

## 1.7 LinkTransport and fake transport (red)

Red commit: `FakeTransportTest` only (TC-S-072..076), no implementation. `./gradlew :protocol:test`: `:protocol:compileTestKotlin` fails, `Unresolved reference 'fake'`, `'TransportListener'`, `'StoredItem'`, `'LinkState'`.

## 1.6 schedule logic (red)

Red commit: `ScheduleLogicTest` only (TC-S-067..071), no implementation. `./gradlew :protocol:test`: `:protocol:compileTestKotlin` fails, `Unresolved reference 'ScheduleLogic'`, `'SnoozeResult'`, `'SnoozeDenial'`.

## 1.4 compatibility rule (red)

Red commit: `CompatibilityTest` only (TC-S-056..059), no implementation. `./gradlew :protocol:test`: `:protocol:compileTestKotlin` fails, `Unresolved reference 'Compatibility'` and `'decodeIfCompatible'`.

## G2 split into G2a and G2b (red)

Red commit: `scripts/test/test-gates.sh` only (TC-S-143, new), before `gates.json`, the plans and the README change.

- `scripts/test/test-gates.sh` on the current `main`: exit 1, 7 FAIL, with the generic graph checks (unique ids, existing requirements, no cycle, README table equals `gates.json`, lane plans name existing gates) PASS. The FAILs are the split itself: `G2 is replaced by G2a and G2b`, `G2a requires G1 only (got '')`, `G2b requires G2a and G-DEC (got '')`, `G3a requires G2a (got 'G2')`, `G3 ... requires G3a, G-LIB and G2b (got 'G-LIB,G3a')`, `G5 requires G2a (got 'G2')`.

## 2.3 data-layer contract (red)

Red commit: tests only (`FakeWatchLinkDataSourceTest`, the repository contract classes and their in-memory-fake subclasses in `phone/src/test/.../data/`; TC-S-090..092), no implementation. `./gradlew :phone:testDebugUnitTest`: compilation of the test source set fails, `Unresolved reference` for `WatchLinkDataSource`, `WatchEvent`, `PublishResult`, `TaskRepository`, `HistoryRepository`, `SettingsRepository`, `ScheduleRepository`, `PromptRepository`, `LinkStatusRepository` and the fakes.

## Acknowledgement amendment, protocol 1.1 (red)

Red commit: tests only (`ConstantsTest` expects `Protocol.VERSION` 1.1; `CodecTest` cases for `Acknowledgement.reminderId` and `occurrenceAt`: round trip with and without, a 1.0 body still decodes, absent fields are not written, blank, over-long and negative values are invalid). `./gradlew :protocol:test`: compilation fails, `No parameter with name 'reminderId' found`.

## 2.2 DataStore settings (red)

Red commit: `DataStoreSettingsRepositoryTest` only (TC-S-098, TC-S-099 and the abstract `SettingsRepositoryContract` of TC-S-092 against a DataStore), no implementation and no dependency. `./gradlew :phone:testDebugUnitTest`: test compilation fails, `Unresolved reference 'datastore'` and `'DataStoreSettingsRepository'`.

## 2.1 Room schema, DAOs and migration policy (red)

Red commit: tests only (`DaoTest`, `RoomRepositoriesTest` running the existing abstract contract tests against Room, `SchemaTest`; TC-S-093..097 and TC-S-092 for the Room repositories), no implementation. `./gradlew :phone:testDebugUnitTest`: test compilation fails, `Unresolved reference 'AppDatabase'`, `'TaskEntity'`, `'RoomTaskRepository'`, ...

## Compose UI-test dependencies (issue 113, red)

Red commit: `ComposeUiTestSampleTest` only (TC-S-144), before the dependencies exist. `./gradlew :phone:testDebugUnitTest`: test compilation fails, `Unresolved reference 'test'` for `androidx.compose.ui.test`.

## Spike content kept in main (red)

Red commit: `scripts/test/test-spike.sh` only (TC-S-145, TC-S-146), run against the tree without the spike apps and runners: every case FAILs (no classes under `src/debug/.../debug/spike`, no debug manifests declaring the components, no runner scripts, `test-run-0b2a.sh` and `test-run-0b2b.sh` missing).

## Spike content: raw logs and receiver permission (red)

Red: `test-run-0b2b.sh` with a `.gitignore` containing `*.log` in its temporary repository (as in the real repository), against the runner without the fix: FAIL "results table and raw per-case logs (L2, L3) are in the commit" and FAIL "the redaction markers are present" (the logs were never committed because `git add` skips ignored files). `test-spike.sh` gets the check "the command receiver is protected by the DUMP permission", which the receiver did not have (CI lint reported `ExportedReceiver`, and `WearRecents` for the watch activity without `taskAffinity`).

## 1.5 Link-state function (red)

Red commit: `LinkStateTest` only (TC-S-060..066, one table-driven exhaustive case over 5 x 5 x 8 input combinations, the capability name), no implementation. `./gradlew :protocol:test`: test compilation fails, `Unresolved reference 'NodeObservation'`, `'LinkStateLogic'`, `'LinkObservation'`.

## 1.5 follow-up: both capability names (red)

Red commit: `LinkStateTest` asserts `Capabilities.PHONE_APP == "adhd_pal_phone"`, before the constant exists. `./gradlew :protocol:test`: test compilation fails, `Unresolved reference 'PHONE_APP'`.

## 1.8b Link-state and alert diagrams (red)

Red commit: `scripts/test/test-protocol-spec.sh` extended only (TC-S-077 requires the "Link state machine" and "Alert flow" Mermaid blocks, TC-S-079 compares the diagram's states with the `LinkState` members). Run against `docs/protocol.md` without the two diagrams: FAIL for "Link state machine is a Mermaid block", "is a stateDiagram-v2", "Alert flow is a Mermaid block", "is a sequenceDiagram", "diagram states equal the LinkState members (found: )" and "link-state diagram has a start".

## 2.4 Real phone WatchLinkDataSource (red)

Red commit: `DataLayerWatchLinkDataSourceTest` and `FakeDataLayerClient` only (events from the watch in order, own writes and bad items dropped, one delivery per item until stored, `markStored` deletes at the watch node and not for a hello, publish paths and urgent flags, the schedule urgent rule, `Failed` on a failing write, link state from node lists and hello, scan of items stored while away), no implementation. `./gradlew :phone:testDebugUnitTest`: test compilation fails, `Unresolved reference 'DataLayerWatchLinkDataSource'` and `'RemoteItem'`.

## 2.5 Repositories over the real source, event storer, schedule version store (red)

Red commit: `DefaultRepositoriesTest` (the existing contract tests TC-S-092 for the production schedule, prompt and link-status repositories), `WatchEventProcessorTest` (row first, then `markStored`; duplicates and hellos cleaned up; a failed store is not marked; the phone hello at start) and `DataStoreScheduleVersionStoreTest` (persists across a new store), no implementation. `./gradlew :phone:testDebugUnitTest`: test compilation fails, `Unresolved reference 'DefaultScheduleRepository'`, `'WatchEventProcessor'`, `'DataStoreScheduleVersionStore'`.

## DataStore reopen flake (TC-S-099)

Not reproduced locally: with `cancel()` only, the new 100-round reopen test and the whole class passed on this machine. CI failed once with `IllegalStateException` at `FileStorage.kt:52` (two active DataStores for one file). Cause, by construction: DataStore frees the file when its scope's job has completed, and `cancel()` does not wait for that, so reopening right after it can race. The new test `reopening the file right after closing it works every time` uses `cancelAndJoin()` (both DataStore tests), which removes the race.
