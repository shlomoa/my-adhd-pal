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
