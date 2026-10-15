# Work log: shared lane (S)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-11 - S1a Test-case catalogue

- Issue and PR: none for S1a (not a plan.md step); PR linked from the coordinator message.
- Changed: added `docs/test-plans/shared.md` with `TC-S-001` to `TC-S-142`: tooling and environment (0a.1-0a.8),
  protocol (1.1-1.8), phone data layer (2.1-2.5), spikes (0b.1-0b.3) and the final run.
- Validation: docs only. Gate check `scripts/require-gate.sh G-BOOT` exited 0. Every id is unique; red baselines
  come with the code steps (`docs/test-reports/shared-baseline.md`, first written in 0a.8).
- Open items: next, issues for all plan.md steps (0a.7), then 0a.8.

## 2026-10-11 - 0a.8 Cloud bootstrap script

- Issue and PR: #21; PR linked from the coordinator message. Also created the issues for all plan.md steps (#12-#74, labels `milestone:*` and `lane:*`); #26 was a duplicate of #25 and is closed.
- Changed: `scripts/bootstrap-android.sh` (pinned command-line tools with SHA-1, platform-tools, platform 37.0, build-tools 37.0.0, `sdk.dir` in `local.properties`, Maven Central mirror init script only when Central refuses); offline tests `scripts/test/test-bootstrap-android.sh`; real fresh-state check `scripts/test/fresh-bootstrap-check.sh`; the Gates workflow runs the offline tests; G-ENV set to merged.
- Validation: red baseline `docs/test-reports/shared-baseline.md` (commit 1fad4a2, 19 red) then green: 20 offline PASS; TC-S-029 fresh SDK then `./gradlew check` BUILD SUCCESSFUL. Finding: Central's 429 depends on load, so the script probes with a parallel burst rather than one request. `sdkmanager` prints a deprecation notice (Android CLI) but still works; the `--licenses` call is a no-op warning in this version, packages install without it.
- Open items: the issue template and the remaining 0a.7 conventions come with the 0a.1-0a.7 PR. The environment's setup script can call `scripts/bootstrap-android.sh` (user action, if wanted).

## 2026-10-11 - 0a.1-0a.3 Baseline, restructure, skeletons

- Issue and PR: #12, #14, #15; PR linked from the coordinator message.
- Changed: `git mv app wear` with package and namespace `com.shlomoa.myadhdpal.wear`; `:protocol` (pure Kotlin/JVM, test `TC-S-005`); `:phone` (Android app, same `applicationId`, namespace `...phone`, Compose Material 3 minimal activity); `allowBackup` off in both apps; settings, version catalog (`kotlin-jvm` plugin, `material3`), CI APK path and README updated.
- Validation: 0a.1 baseline green on unmodified `main` (`assembleDebug testDebugUnitTest lintDebug`, BUILD SUCCESSFUL in 1m 20s). `scripts/test/test-structure.sh` red first (15 FAIL, commit 7ccaf7f in `docs/test-reports/shared-baseline.md`), then 15 PASS; CI command green on all three modules.
- Open items: 0a.4 shared configuration and 0a.5 checking tools next; the phone build file duplicates the wear defaults until 0a.4 centralises them.

## 2026-10-11 - 0a.4-0a.5 Shared configuration, pairing check, checking tools

- Issue and PR: #16, #17; PR linked from the coordinator message.
- Changed: root `build.gradle.kts` configures both apps from the `[versions]` block of `gradle/libs.versions.toml` (applicationId, SDK levels, Java 21, lint policy) and adds `checkPairing` (applicationId, SDK levels, signing config) to `check`; Spotless with ktlint (`.editorconfig`, android_studio style), detekt (`config/detekt/detekt.yml`), Android lint failing on new issues (per-module `lint-baseline.xml`, one pre-existing warning), `lintDocs` and `checkExternalLinks` calling `scripts/docs-lint.sh` (tools pinned in `tools/docs`); backup rules (`data_extraction_rules.xml`, `backup_rules.xml`) in both apps so no data is backed up or transferred; CI runs `./gradlew check assembleDebug`.
- Validation: red first (`docs/test-reports/shared-baseline.md`: 11 red for 0a.4, 15 red for 0a.5), then green: pairing 11 PASS, checks 20 PASS, `check` green. A restore bug in the pairing test (reverted uncommitted module edits) was found and fixed.
- Open items: detekt 2.0.0-alpha.6 is a pre-release (stable 1.23.8 does not work with Kotlin 2.4). Revisit when 2.0 is stable. markdownlint rules MD013, MD040 and MD060 are off because the lane docs use differing table and code-fence styles. 0a.6 (CI jobs, release-has-no-debug) and 0a.7 (conventions) remain before G0.

## 2026-10-11 - 0a.6-0a.7 CI and conventions (G0)

- Issue and PR: #18, #20; PR linked from the coordinator message. Step issues, by step id: 0a.1 #12, 0a.2 #14, 0a.3 #15, 0a.4 #16, 0a.5 #17, 0a.6 #18, 0a.7 #20, 0a.8 #21, 0b.1 #22, 0b.2a #23, 0b.2b #24, 0b.3 #25, 1.1-1.8 #27-#34, 2.1-2.7 #35-#41, 3.1-3.9 #42-#50, 4.1 #62, 4.2 #63, 4.3 #65, 4.4 #66, 4.5 #67, 4.6 #68, 5.1-5.3 #51-#53, 6.1-6.8 #54-#61, 7.1-7.6 #69-#74 (watch and Android lanes' own catalogue and research issues are #8, #11, #13).
- Changed: `build.yml` has a `build` job (`./gradlew check assembleDebug`) and a non-blocking `external-links` job; `checkNoDebugInRelease` in `check` (debug code lives in a `debug` package, see `docs/conventions.md`); the *Plan step* issue template; `scripts/check-issues.sh` and `scripts/check-work-log.sh`; the *Conventions* workflow; `docs/conventions.md`; G0 set to merged.
- Validation: red first (`docs/test-reports/shared-baseline.md`), then `test-ci.sh` 11 PASS, `test-conventions.sh` 15 PASS, `./gradlew check` green. G0 exit criteria 0a.1-0a.7 all met; the live issue check and the CI job list are evidenced on this PR.
- Open items: for the user, open *New issue* once to confirm the *Plan step* template renders (TC-S-022). Watch lane: put debug-only code in a `...wear.debug` package (the release check relies on it). detekt 2.0.0-alpha.6 is a pre-release. Next: 0b.1 (Hilt/KSP/Room, G-LIB) and 0b.2a (emulator spike code and run checklist for the user).

## 2026-10-11 - 0a.5 CI fix: stale Spotless result

- Issue and PR: #17; PR #77 and #78.
- Changed: ran the ktlint `android_studio` formatting that a clean run requires (no trailing commas in `MyAdhdPalApp.kt` and `build.gradle.kts`); a note in `docs/conventions.md`.
- Validation: CI on #77 failed in `spotlessKotlinCheck` while my machine passed, because Spotless reused a result computed before `.editorconfig` set the style. Reproduced by changing the style, fixed, and verified with `./gradlew --no-build-cache --rerun-tasks check assembleDebug` (BUILD SUCCESSFUL) and a fresh clone. Lesson: validate with `--no-build-cache --rerun-tasks` before pushing a tooling change.
- Open items: none.

## 2026-10-11 - 0b.1 Library compatibility (Hilt, KSP, Room)

- Issue and PR: #22; PR linked from the coordinator message.
- Changed: version catalog (`ksp`, `hilt`, `room`, `robolectric`, `androidx.test:core-ktx`) and plugins applied in `:phone`; compatibility samples `phone/src/test/.../compat/InjectionSampleTest.kt` (Hilt) and `DaoSampleTest.kt` (Room), test sources only so no Android-lane file is touched; `bootstrap-android.sh` also points Robolectric's downloads at the Maven mirror when Central refuses.
- Validation: samples red first (do not compile without the libraries), then PASS; `./gradlew --no-build-cache --rerun-tasks check assembleDebug` BUILD SUCCESSFUL; bootstrap tests 22 PASS; fresh-clone check green. Details and the working version set in `docs/test-reports/shared-baseline.md`.
- **Decision: use Hilt** (with `ksp`, no kapt) for dependency injection (plan.md: Hilt if compatible with AGP 9 / KSP). Hilt 2.60.1 + KSP 2.3.12 + Room 2.8.5 work on AGP 9.4.1 and Kotlin 2.4.21. The Android lane's step 2.7 wires the `@HiltAndroidApp` application and modules; the plugins and dependencies are already in `:phone`.
- Open items: the two compatibility samples can stay as a toolchain regression test or be removed when 2.1 adds the real Room schema.

## 2026-10-12 - CI follow-ups: regression guard, Watch script in CI, detekt test source sets

- Issue and PR: none (follow-up to #77, #78 and Watch lane #80); PR linked from the coordinator message.
- Changed: the Spotless fresh-clone regression guard (`a213784`, which had missed `main`); the Watch lane's `wear/src/test/scripts/test-debug-separation.sh` now runs in the build job; a `script-tests` job runs `test-structure.sh`, `test-pairing.sh`, `test-checks.sh` and `test-ci.sh` (they were not in CI before); `config/detekt/detekt.yml` gives the 15 rules that skip test sources by default their own default excludes plus `src/testDebug`, `src/testRelease` and `src/testFixtures`, so the Watch lane can drop its `@Suppress("FunctionNaming")`.
- Validation: tests red first (6 workflow cases and the `src/testDebug` detekt case), then green; fresh-clone dry run of the whole CI sequence green.
- Open items: Watch lane to remove the suppression after this merges.

## 2026-10-14 - 0b.2a Emulator-pair spike results (G-SPK1)

- Issue and PR: #23; PR linked from the coordinator message.
- Changed: `docs/protocol.md` created with the transport verification results, the deletion finding, the fallback-rule evaluation and the cases deferred to 0b.2b; `docs/plan.md` section 3 and section 4 now use the writer-owns-cleanup rule instead of "the consumer deletes an item after storing it", and note the emulator result; the partial output of the first scripted run is kept as evidence in `docs/spikes/results/0b2a-first-real-run-partial.txt`; G-SPK1 set to merged.
- Validation: manual and scripted runs by the user on real emulators (TC-S-121 to TC-S-125): items cross both ways, listener wakes with the app closed after a kill and after a force-stop, a KEEP item stays, the watch-away reconnect recovers on its own; a quick delete by the watch of a phone-written item is lost on the phone (writer-owns-cleanup rule). Not run on emulators: C5 phone away, C2b, C4; deferred to 0b.2b on the real devices (laptop sleep and the first runner's hang, not protocol failures). Doc lint and link check green. The spike runner was exercised 62 times against a simulated adb (`scripts/spike/test-run-0b2a.sh` on `claude/shared-0b2a-spike-rtt`).
- Open items: transport stays DataItems only, final at G-DEC after 0b.2b; the runner on `claude/shared-0b2a-spike-rtt` is reused there (with `-OnlyCases`). Steps 1.1-1.3 (G1) can start once this merges; 0b.2b also needs H2.

## 2026-10-14 - 0b.2b Real-hardware spike plan and runner

- Issue and PR: #24; PR linked from the coordinator message.
- Changed: `docs/spikes/0b2b-plan.md` (decisions needed, what the user does versus what the script does, setup for the A55 over USB and the watch over Wi-Fi debugging with the expected output of each step, the one command, a nine-item checklist, exit codes, what is not verified). The runner `scripts/spike/run-0b2b.ps1`, its simulated adb with hardware-style serials (`scripts/spike/fake-hw/adb`) and `scripts/spike/test-run-0b2b.sh` (65 checks) are on the throwaway branch `claude/shared-0b2b-spike` (never merged); no emulator or restart calls, per-call adb timeouts, a readiness gate that names the device and the fix, link states produced with `svc` on the phone, Doze and standby buckets forced with `dumpsys`/`am`, radios always restored (`-RestorePhone` after a crash), results pushed as `spike-results/0b2b-<timestamp>`.
- Validation: `test-run-0b2b.sh` 65 PASS on PowerShell 7 against the simulation (flow, readiness failures, radios that adb cannot switch, unobservable Relayed and Away, target misses, Doze, restore, redaction of serials and IP addresses, exit codes); doc lint green. Not run on the real devices or on Windows PowerShell 5.1; the plan lists what stays unverified.
- Open items: the user agrees the delay targets (proposal in the plan), then does the setup and runs the command; 0b.3 and G-DEC follow the results.

## 2026-10-14 - 1.1-1.3 Spec skeleton, constants, models and codec (G1)

- Issue and PR: #27, #28, #29; PR linked from the coordinator message.
- Changed: `docs/protocol.md` gains the item catalog, item format (keys, bodies, limits), versioning policy and the five link states; `:protocol` gets `Paths` (builders and parser, ids are lower-case canonical UUIDs), `ItemKeys`, `Protocol` (major 1, version `1.0`), `Limits`, the models (`Schedule`, `Prompt`, `Acknowledgement`, `Selection`, `Alert`, `Hello`, `BiometricSamples`) that validate their limits on construction, and `Codec` (kotlinx.serialization 1.11.0 with the serialization compiler plugin; `decode` never throws and returns `DecodeResult.Failure` for unknown paths, bad bodies and out-of-limit content); `scripts/test/test-protocol-spec.sh` (TC-S-040, TC-S-041) runs in the `script-tests` CI job.
- Validation: tests written first and red (`shared-baseline.md`); then `:protocol:test` green (TC-S-042..055) and the spec script green; doc lint green.
- Open items: 1.4 to 1.8 (compatibility, link state, schedule logic, fake transport, diagrams) need G-DEC; the Watch and Android adapters (`DataMap` to `Envelope`) are their lanes' work.

## 2026-10-14 - 1.8a Transport-neutral diagrams

- Issue and PR: #34 (1.8; 1.8b follows with G2b, so the issue stays open); PR linked from the coordinator message.
- Changed: a "Diagrams" section in `docs/protocol.md` with four Mermaid diagrams (schedule sync, prompt/acknowledgement/selection, hello and version check, prompt state machine); TC-S-077 structure checks in `scripts/test/test-protocol-spec.sh` (Mermaid block of the right type under each heading, balanced alt/loop/end, start and end of the state machine, no semicolon in a Mermaid line).
- Validation: checks written first and red (`shared-baseline.md`), then green; all four diagrams were also rendered with `@mermaid-js/mermaid-cli` 11.4.2 and Chromium (a semicolon in a message broke the first draft and is now guarded); doc lint green. TC-S-078 (rendered view on the PR) is the manual check for the reviewer. Also added the prompt-expiry clause decided by the coordinator (absolute phone-clock time, the watch hides an expired prompt on its own clock, a few seconds of tolerance).
- Open items: the link-state machine and the alert flow are 1.8b (G2b). Gate G2a is not flipped here: it needs 1.4, 1.6, 1.7 and 1.8a all merged, so a separate small PR flips it.

## 2026-10-14 - 1.7 LinkTransport and fake transport

- Issue and PR: #33; PR linked from the coordinator message.
- Changed: `LinkState` (five members), `StoredItem`, `TransportListener` and `LinkTransport` in `:protocol`; `FakeLink` and `FakeNode` in `protocol.fake` (test double with DataItem buffering semantics, deterministic and synchronous); a "Transport abstraction" section in `docs/protocol.md`.
- Validation: TC-S-072..076 written first and red (`shared-baseline.md`), then green (delivery in order, buffering while away, replacement at the same path, deletion not redelivered, repeated disconnect and reconnect without duplicates or loss), plus link-state notification and no-watch-app cases; `./gradlew check` and doc lint green. One expectation in my own test (a node also stores its own writes) was corrected after the red run.
- Open items: the listener-based interface has no coroutines, so the app lanes bridge it to Flow with `callbackFlow`; gate G2a is flipped by 1.8a (the last of the G2a steps).

## 2026-10-14 - 1.6 Schedule logic

- Issue and PR: #32; PR linked from the coordinator message.
- Changed: `ScheduleLogic` in `:protocol` (`nextReminder`, `nextCheckpoint`, `snooze`, `shouldApply`) with `ReminderOccurrence`, `CheckpointOccurrence`, `SnoozeResult`, `SnoozeDenial`; a "Schedule logic" section in `docs/protocol.md` (strictly-after rule, selected days, daylight-saving and time-zone rules, snooze cap and expiry, newer-version rule).
- Validation: TC-S-067..071 written first and red (`shared-baseline.md`), then green with a fixed clock, including a spring-forward day, a fall-back day and a time-zone change; `./gradlew check` and doc lint green.
- Open items: none for this step; gate G2a is flipped by the last of 1.7 and 1.8a.

## 2026-10-14 - 1.4 Compatibility rule

- Issue and PR: #30; PR linked from the coordinator message.
- Changed: `Compatibility` (`ofMajor`, `ofHello`), `ProtocolVersion.parse` and `Codec.decodeIfCompatible` in `:protocol`; the versioning policy in `docs/protocol.md` states the rule and the accepted version text (`major.minor[.patch]`, 1 to 4 digits per part).
- Validation: TC-S-056..059 written first and red (`shared-baseline.md`), then green with `./gradlew check`; doc lint green.
- Open items: none for this step; gate G2a is flipped by the last of 1.6, 1.7, 1.8a.

## 2026-10-14 - G2 split into G2a and G2b

- Issue and PR: refs #30-#34 (steps 1.4-1.8); PR linked from the coordinator message.
- Changed: `gates.json`, `docs/plans/README.md` (table, schedule, graph, risks, a "Why G2 is split" paragraph), `docs/plans/shared.md`, `docs/plans/watch.md` and the catalogue headings. G2a = 1.4, 1.6, 1.7, 1.8a (requires G1); G2b = 1.5, 1.8b (requires G2a, G-DEC). G3a requires G2a; G5 requires G2a; G3 requires G2b; the watch's 3.2-3.7 require G2a and its 6.3-6.8 require G2b. New `scripts/test/test-gates.sh` (TC-S-143) checks the whole graph and runs in the `script-tests` job.
- Validation: TC-S-143 red first on `main` (7 FAIL, recorded in `shared-baseline.md`), then green; doc lint and link check green; `test-scripts.sh` (gate scripts) green.
- Open items: no gate flipped. Steps 1.4, 1.6, 1.7, 1.8a start now; 1.5 and 1.8b wait for G-DEC.

## 2026-10-14 - G2a gate: 1.4, 1.6, 1.7, 1.8a merged

- Issue and PR: #30, #32, #33 closed by their PRs (#96, #97, #98); 1.8a is #99 (#34 stays open for 1.8b); flip PR linked from the coordinator message.
- Changed: gate G2a set to merged in `docs/plans/gates.json`; the status paragraph of `docs/protocol.md` lists what is done. Nothing else.
- Validation: `scripts/test/test-gates.sh` (TC-S-143) and `scripts/check-pr-gates.sh` against the base; doc lint green.
- Open items: G3a (2.3) starts now; G2b (1.5, 1.8b) waits for G-DEC.

## 2026-10-14 - 2.3 Phone data contract (G3a)

- Issue and PR: #37; PR linked from the coordinator message.
- Changed: `WatchLinkDataSource`, `WatchEvent`, `PublishResult`, the repository interfaces (`TaskRepository`, `HistoryRepository`, `SettingsRepository`, `ScheduleRepository` with `ScheduleVersionStore`, `PromptRepository`, `LinkStatusRepository`), their models, `ScheduleBuilder`, `SettingsDefaults`/`SettingsLimits` in `phone/.../data/`; `FakeWatchLinkDataSource` and in-memory repositories plus abstract contract tests in the `:phone` test sources; `:phone` now depends on `:protocol` and kotlinx-coroutines (catalog entries `kotlinxCoroutines`); `docs/data-layer.md` documents the contract.
- Validation: TC-S-090..092 written first and red (`shared-baseline.md`), then green; `./gradlew check` green.
- Open items: 2.1 and 2.2 can start (Room, DataStore) and must pass the same contract tests; 2.4 and 2.5 also need G2b. The Android lane may now write its ViewModel tests against these fakes (A1b).

## 2026-10-14 - Amendment of G1: Acknowledgement reminderId and occurrenceAt (protocol 1.1)

- Issue and PR: found by the Watch lane in 3.6 (coordinator decision); PR linked from the coordinator message.
- Changed: `Acknowledgement` gets the optional `reminderId` (1 to 32 characters, validated like the other keys) and `occurrenceAt` (not negative); `Protocol.VERSION` 1.0 to 1.1 (major stays 1, compatibility is by major only); the codec no longer writes absent optional fields (`explicitNulls = false`), so a 1.0 reader sees the body it knows; `docs/protocol.md` (Item format table, version history, ack diagram) updated. An amendment of G1's contents: no path or key changes, no gate flipped.
- Validation: tests written first and red (`shared-baseline.md`), then green: round trip with and without the fields, a 1.0 body still decodes, absent fields are not written, invalid values are dropped, the version constant; `./gradlew :protocol:test detekt` green, doc lint green.
- Open items: the phone's `HistoryEntry.Acknowledged` (PR for 2.3) should carry `reminderId` and `occurrenceAt`; the Watch lane sends them from 3.6.

## 2026-10-14 - G3a gate: 2.3 merged

- Issue and PR: #37 closed by the 2.3 PR (#104); flip PR linked from the coordinator message.
- Changed: gate G3a set to merged in `docs/plans/gates.json` (`pr` 104). Nothing else.
- Validation: `scripts/test/test-gates.sh` (TC-S-143), `scripts/check-pr-gates.sh` against the base, doc lint.
- Open items: 2.1 (Room) and 2.2 (DataStore) start against the contract tests; 2.4 and 2.5 wait for G2b.

## 2026-10-14 - 2.2 DataStore settings

- Issue and PR: step 2.2 issue; PR linked from the coordinator message.
- Changed: `DataStoreSettingsRepository` and `SettingsValidator` (shared with the in-memory fake) in `phone/.../data/`; dependency `androidx.datastore:datastore-preferences` 1.2.1 (catalog entries under the S block); `docs/data-layer.md` documents the storage and the fallbacks.
- Validation: TC-S-098 and TC-S-099 written first and red (`shared-baseline.md`), then green, together with the abstract `SettingsRepositoryContract` (TC-S-092) against a real DataStore file: documented defaults, a value survives closing and reopening the store, out-of-range values are rejected without a write, a damaged file falls back to the defaults; `./gradlew check` green.
- Open items: none for this step. 2.4 and 2.5 wait for G2b; the schedule version store (monotonic across restarts) is part of 2.5.

## 2026-10-14 - 2.1 Room schema, DAOs and migration policy

- Issue and PR: step 2.1 issue; PR linked from the coordinator message. 2.1 was BLOCKED on G3a (#111) until the coordinator merged it, then started.
- Changed: `phone/.../data/db/`: `AppDatabase` (version 1, `exportSchema`), entities `TaskEntity`, `ReminderEntity` (cascade delete), `PromptEntity`, `HistoryEntity` (id is the primary key), DAOs, `RoomTaskRepository` and `RoomHistoryRepository`; the Room Gradle plugin (`androidx.room`, catalog entry) exports the schema to `phone/schemas/`, committed (`1.json`); `docs/data-layer.md` documents the tables and the migration policy (committed schema, one migration per step, never destructive).
- Validation: TC-S-093..097 and TC-S-092 for Room written first and red (`shared-baseline.md`), then green: the DAO tests, the abstract contract tests for tasks and history run against Room on Robolectric, the schema and policy checks; `./gradlew check` green. The Room Gradle plugin works with AGP 9.4.1 and Kotlin 2.4.21.
- Open items: 2.2 (DataStore settings) next; the schedule, prompt and link-status repositories come in 2.5 (G2b); the Android lane wires `AppDatabase.create` in 2.7. No migration test yet because the schema is at version 1.

## 2026-10-14 - Compose UI-test dependencies for `:phone` (issue #113)

- Issue and PR: #113 (request from the Android lane, needed by TC-A-058 and TC-A-059 in step 4.6); PR linked from the coordinator message.
- Changed: `androidx.compose.ui:ui-test-junit4` as `testImplementation` (under the Compose BOM) and `ui-test-manifest` as `debugImplementation` in `phone/build.gradle.kts`, with catalog entries in `gradle/libs.versions.toml` (versions come from the existing BOM); a sample `ComposeUiTestSampleTest` in `phone/src/test/.../compat/` (TC-S-144).
- Validation: the sample test was written first and red (`shared-baseline.md`), then green in `:phone:testDebugUnitTest` under Robolectric on AGP 9.4.1 and Kotlin 2.4.21; `./gradlew check` green.
- Open items: none; lane A writes TC-A-058 and TC-A-059 in step 4.6.

## 2026-10-15 - 0b.2b Hardware spike results and 0b.3 transport decision

- Issue and PR: #24 (0b.2b) and the 0b.3 issue; PR linked from the coordinator message.
- Changed: `docs/protocol.md` gets the hardware results (devices and versions, all cases, numbers, what is not verified), what they mean for step 1.5, the final fallback-rule evaluation and the 0b.3 decision; `docs/plan.md` sections 3 and 4 state that the transport is DataItems only and that the writer-owns-cleanup rule is kept; the six `results.txt` files of the runs are copied to `docs/spikes/results/0b2b-*/`.
- Validation: the user ran `run-0b2b.ps1` on the Galaxy A55 5G (USB) and the Galaxy Watch6 Classic (Wi-Fi debugging), Android 16 on both: every automated case PASS (C1, C1b, C2a and C2b, C3a to C3d, L1 Nearby, L2 Relayed, L3 Away). Nearby round trip about 0.2 s, relayed about 1.8 s, Away seen after 4 s and recovered within 2 to 16 s, delivery with the app closed within 1 to 4 s under kill, force-stop, Doze and the rare and restricted standby buckets, deletion symmetric in both directions. C3d first failed on a runner bug (the closure could not call `Invoke-Adb`) and passed after the fix `d689afa`. Doc lint and link check green.
- Open items: the user's checklist answers (Galaxy Wearable pairing path, battery-management toggles, app battery setting) and the optional three-day sleeping-apps recheck are still to be added; the G-DEC flip needs gate H2 (the user's devices) merged first; then 1.5, 1.8b and G2b.

## 2026-10-15 - 0b.2b checklist answers from the user

- Issue and PR: #24 (0b.2b); PR linked from the coordinator message.
- Changed: `docs/protocol.md` records the user's answers (existing Bluetooth pairing, "Put unused apps to sleep" on at default settings, the app listed as "ADHD Pal" with Battery "Optimized" and treated as not exempt, state after the last run) and marks the days-long sleeping-apps recheck as open with its reason.
- Validation: doc lint and link check green.
- Open items: the three-day sleeping-apps recheck (`run-0b2b.ps1 -SkipBuild -OnlyCases C3` after three days without opening the app); the G-DEC flip waits for the user's H2 confirmation.

## 2026-10-15 - Gate G-DEC: 0b.2b hardware spike and 0b.3 decision

- Issue and PR: #24 (0b.2b) and the 0b.3 issue; PR linked from the coordinator message. Gate PR 124 is the 0b.2b/0b.3 results PR.
- Changed: `docs/plans/gates.json` sets G-DEC to merged with `pr` 124. G-SPK1 and H2 are merged, so the hardware results and the DataItems-only decision are on main.
- Validation: `scripts/require-gate.sh G-SPK1 H2` passes; `scripts/test/test-gates.sh` and `scripts/check-pr-gates.sh` pass.
- Open items: 1.5 and 1.8b next, then the G2b flip.

## 2026-10-15 - 1.5 Link-state function

- Issue and PR: step 1.5 issue; PR linked from the coordinator message.
- Changed: `protocol/.../LinkStateLogic.kt` (`LinkStateLogic.derive`, `LinkObservation`, `NodeObservation`, `Capabilities.WATCH_APP` = `adhd_pal_watch`); `docs/protocol.md` link-state section with the precedence table, the rules behind it and what the hardware showed.
- Validation: `LinkStateTest` written first and red (`shared-baseline.md`), then green (14 tests): TC-S-060..066, an exhaustive case over 5 x 5 x 8 input combinations checked against an independent statement of the rules, malformed and unsupported hello versions, several watches, and a capability the node list does not show yet. spotless, detekt and doc lint green. Nearby, Relayed and Away match the 0b.2b observations; No watch app and Incompatible are unit-tested only.
- Open items: 1.8b (link-state machine and alert-flow diagrams, TC-S-077..079), then the G2b flip. The watch lane declares the `adhd_pal_watch` capability resource and the data sources (2.4, 2.5) refresh the node list about every 2 s.

## 2026-10-15 - 1.8b Link-state machine and alert-flow diagrams

- Issue and PR: step 1.8b issue; PR linked from the coordinator message.
- Changed: `docs/protocol.md` gets the "Link state machine" (`stateDiagram-v2`, states named like the `LinkState` members, transition times from the hardware spike) and the "Alert flow" (`sequenceDiagram`: reading, threshold and cooldown on the watch, urgent `/alert/<id>`, listener service, store once, delete) under Diagrams; the status line no longer says the diagrams wait for the spike.
- Validation: `test-protocol-spec.sh` extended first and red (`shared-baseline.md`), then green: TC-S-077 for the two new blocks (Mermaid, diagram type, balanced blocks, no semicolons) and TC-S-079 (the diagram states equal the `LinkState` members, found by reading `LinkTransport.kt`). TC-S-078: all six diagrams of the file parsed and rendered without error with Mermaid 10.9.1 in headless Chromium; the PR's own rendered view is still for the reviewer. Doc lint green.
- Open items: the G2b flip once this PR and the 1.5 PR (#131) are merged.

## 2026-10-15 - Gate G2b: link-state function and diagrams

- Issue and PR: steps 1.5 and 1.8b; PRs #131 and #132 are merged, this PR flips the gate.
- Changed: `docs/plans/gates.json` sets G2b to merged with `pr` 131.
- Validation: `scripts/require-gate.sh G2a G-DEC` passes; `scripts/test/test-gates.sh` passes.
- Open items: 2.4 and 2.5 (phone Data Layer source and repositories) next, then the G3 flip.

## 2026-10-15 - 2.4 Real phone WatchLinkDataSource over the Data Layer

- Issue and PR: step 2.4 issue; PR linked from the coordinator message.
- Changed: `phone/.../link/` (`DataLayerClient` seam, `GmsDataLayerClient`, `DataLayerWatchLinkDataSource`, `WatchItemDecoder`, `ScheduleUrgency`, `PhoneLinkListenerService`), `di/LinkModule`, `res/values/wear.xml` (capability `adhd_pal_phone`), the service in the manifest, `play-services-wearable` and `androidx.fragment` as `implementation`, `MyAdhdPalApp` starts the source; `docs/data-layer.md` section.
- Validation: `DataLayerWatchLinkDataSourceTest` (12 tests) and `ListenerManifestTest` written first and red (`shared-baseline.md`), then green; `./gradlew check` (tests, spotless, detekt, lint, release has no debug code) green. Not run against real Play services here; the hardware demo does that.
- Open items: 2.5 (schedule, prompt and link-status repositories over this source, the event storer, `/hello/phone` at start, DI), then the G3 flip.
