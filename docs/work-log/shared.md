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

## 2026-10-14 - 1.1-1.3 Spec skeleton, constants, models and codec (G1)

- Issue and PR: #27, #28, #29; PR linked from the coordinator message.
- Changed: `docs/protocol.md` gains the item catalog, item format (keys, bodies, limits), versioning policy and the five link states; `:protocol` gets `Paths` (builders and parser, ids are lower-case canonical UUIDs), `ItemKeys`, `Protocol` (major 1, version `1.0`), `Limits`, the models (`Schedule`, `Prompt`, `Acknowledgement`, `Selection`, `Alert`, `Hello`, `BiometricSamples`) that validate their limits on construction, and `Codec` (kotlinx.serialization 1.11.0 with the serialization compiler plugin; `decode` never throws and returns `DecodeResult.Failure` for unknown paths, bad bodies and out-of-limit content); `scripts/test/test-protocol-spec.sh` (TC-S-040, TC-S-041) runs in the `script-tests` CI job.
- Validation: tests written first and red (`shared-baseline.md`); then `:protocol:test` green (TC-S-042..055) and the spec script green; doc lint green.
- Open items: 1.4 to 1.8 (compatibility, link state, schedule logic, fake transport, diagrams) need G-DEC; the Watch and Android adapters (`DataMap` to `Envelope`) are their lanes' work.

## 2026-10-14 - 1.6 Schedule logic

- Issue and PR: #32; PR linked from the coordinator message.
- Changed: `ScheduleLogic` in `:protocol` (`nextReminder`, `nextCheckpoint`, `snooze`, `shouldApply`) with `ReminderOccurrence`, `CheckpointOccurrence`, `SnoozeResult`, `SnoozeDenial`; a "Schedule logic" section in `docs/protocol.md` (strictly-after rule, selected days, daylight-saving and time-zone rules, snooze cap and expiry, newer-version rule).
- Validation: TC-S-067..071 written first and red (`shared-baseline.md`), then green with a fixed clock, including a spring-forward day, a fall-back day and a time-zone change; `./gradlew check` and doc lint green.
- Open items: none for this step; gate G2a is flipped by the last of 1.7 and 1.8a.
