# Work log: android lane (A)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-11 - A1a Android test-case catalogue

- Issue and PR: see the A1a issue and its PR (closed by the PR).
- Changed: added `docs/test-plans/android.md` with TC-A-001 to TC-A-072 (link-state, DI, tasks/reminders, schedule
  builder, settings, prompts, history, empty/error/accessibility, release, final).
- Validation: docs only. `scripts/require-gate.sh G-BOOT` exited 0. Markdown lint runs in CI once G0 lands.
- Open items: manual cases (TC-A-009, 030, 038, 044, 052, 060, 061, 071, 072) wait for the user. Next: A1b waits for G0/G-ENV/G3a.

## 2026-10-11 - 2.7 Dependency injection

- Issue and PR: #41; PR linked from the coordinator message.
- Changed: `@HiltAndroidApp` `MyAdhdPalApp` (registered in the manifest), `@AndroidEntryPoint` `MainActivity`, `di/ClockModule` (the one source of `java.time.Clock`). Tests in `phone/src/test/.../di/DiGraphTest.kt`.
- Validation: tests first, red in `docs/test-reports/android-baseline.md` (commit 648ec9a); then TC-A-010 and TC-A-012 PASS and `./gradlew check assembleDebug` green after `scripts/bootstrap-android.sh`. TC-A-011 is the green CI build.
- Open items: the data layer does not exist yet (G3a), so the production graph binds only the clock. The Shared lane adds data-source bindings under `data/`; TC-A-012 is extended to those when they land. A stale KSP output made `lintAnalyzeDebugUnitTest` fail once after a failed build; `./gradlew :phone:clean` fixed it. Next: A1b, 2.6 and 4.5 wait for G3a.
