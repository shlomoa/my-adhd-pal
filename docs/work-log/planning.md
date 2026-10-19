# Work log: planning and orchestration

Entries by the planning threads and the orchestrator session. Index and entry format: [`../work-log.md`](../work-log.md).

## 2026-10-11 - Plan for phone app, watch app and protocol in one repo

- Merged two plans into [`docs/plan.md`](../plan.md): the phone-app/protocol plan and the watch-side hand-off
  (reminders on the watch, connection state, biometrics, debug vs release, process rules).
- Decided: one monorepo in `my-adhd-pal` with `:protocol`, `:wear` and `:phone`.
- Open: transport (DataItems only vs items + messages); to be decided in the plan PR.
- No code changed.

## 2026-10-11 - Execution plans and gate bootstrap (S0)

- Added [`docs/plans/`](../plans/README.md): three parallel lane plans (shared/data, Android, watch), gates, stop rule, schedule.
- Added `scripts/require-gate.sh`, `scripts/check-pr-gates.sh`, `scripts/check-test-cases.sh`, `docs/plans/gates.json` and the `Gates` CI workflow.
- Validation: script test cases written first and recorded red (`docs/test-reports/bootstrap-baseline.md`), then green (`bootstrap-final.md`).
- Assumptions to confirm: lane split, transport = DataItems only, minimal phone scope (see plans README).
- After merging the approved `plan.md` (milestones 0a to 7), the lane plans and `gates.json` were re-mapped to its step ids (16 gates, spikes and human gates included).
- Gate G-BOOT is set to `merged` in this change; it takes effect on `main` when the change is merged.

## 2026-10-11 - Section-by-section review of the merged plan

- Reviewed [`docs/plan.md`](../plan.md) with the user, section by section, merging the architecture thread's plan with the
  watch thread's hand-off. Approved sections 1 to 8 with the changes below.
- Decided: DataItems-only transport with a decided fallback (`MessageClient` for urgent alerts only); five link states
  (added Incompatible); numeric values in alerts; Health Services passive monitoring instead of `MeasureClient`;
  `allowBackup` off; reminders within 1 minute over 24 hours; emulator pairing required before hardware testing
  (0b.2a, 0b.2b); milestone 0 split into 0a and 0b with detailed steps; a cloud bootstrap script (0a.8).
- Recorded: the test phone is a Samsung Galaxy A55 5G (SM-A556E/DS) with the Galaxy Watch6 Classic.
- Environment note: the cloud environment had no Android SDK and Maven Central answered 429. A temporary SDK install and
  a user-level Gradle mirror let `assembleDebug testDebugUnitTest lintDebug` pass locally; step 0a.8 makes it repeatable.
- Open: transport details to be proven by the spike; reminder mechanism and biometric permissions to be researched.
- No code changed.

## 2026-10-11 - Work log split per lane

- PR: [#7](https://github.com/shlomoa/my-adhd-pal/pull/7)
- Changed: `docs/work-log.md` is now the index and entry-format page; entries moved to `docs/work-log/planning.md`, with empty `shared.md`, `android.md`, `watch.md` for the parallel lanes. `scripts/require-gate.sh` and `docs/plans/README.md` point to the lane logs.
- Why: three parallel sessions appending to one file already conflicted once when merging `main`.
- Validation: relative links under `docs/` resolve (ad hoc check before and after); `scripts/test/test-scripts.sh` 19/19 pass.
- Open items: none.

## 2026-10-12 - Windows setup guide for the 0b.2a spike

- Issue and PR: none (coordinator documentation); PR linked from the session.
- Changed: added `docs/dev-setup-windows.md` (JDK 21, hardware acceleration, emulator creation and pairing, spike build, install and the five cases, logs, troubleshooting) and a README link. Written after the user's first attempt on Windows 11 failed with "Cannot find a Java installation ... languageVersion=21". Corrected after the user reported that the Temurin installer offers no JAVA_HOME or PATH option: the guide now finds the install folder and sets both by hand.
- Validation: `scripts/docs-lint.sh markdown` and `links` green; not yet followed end to end on Windows. The guide is updated from each report until the user has the build, the emulators and the spike running.
- Open items: results of spike 0b.2a (G-SPK1) still to come from the user.

## 2026-10-12 - Windows guide: JAVA_HOME must match the Gradle JDK

- Issue and PR: none (coordinator documentation); PR linked from the session.
- Changed: `docs/dev-setup-windows.md` now explains that `.\gradlew.bat` uses `JAVA_HOME` before `PATH`, how to set a user `JAVA_HOME` that overrides a system one, and the "Multiple Gradle daemons" warning. Found because the user's `JAVA_HOME` pointed at JDK 23 while `java -version` reported Temurin 21.
- Validation: `scripts/docs-lint.sh markdown` and `links` green.
- Open items: still waiting for the user's `.\gradlew.bat :protocol:test` result, then the emulators and the spike.

## 2026-10-12 - Windows guide: companion app is Google Pixel Watch

- Issue and PR: none (coordinator documentation); PR linked from the session.
- Changed: `docs/dev-setup-windows.md` section 5 now describes the pairing assistant's first page (install the *Google Pixel Watch* companion app on the phone emulator from the Play Store, sign in, scroll for further steps), after the user's screenshot showed the app is no longer called *Wear OS by Google*.
- Validation: `scripts/docs-lint.sh markdown` and `links` green. The user's Java 21 setup was confirmed working (`.\gradlew.bat :protocol:test` BUILD SUCCESSFUL on Temurin 21).
- Open items: finish pairing, then run the spike cases and report.

## 2026-10-12 - Windows guide: verify the spike checkout

- Issue and PR: none (coordinator documentation); PR linked from the session.
- Changed: `docs/dev-setup-windows.md` section 6.1 no longer promises a Gradle sync prompt after `git checkout` (the user saw none) and adds two commands that confirm the spike branch is checked out.
- Validation: `scripts/docs-lint.sh markdown` and `links` green.
- Open items: confirm pairing succeeded, then build and install the spike apps and run the cases.

## 2026-10-14 - Windows guide: scripted spike runs and recovery

- Issue and PR: none (coordinator documentation); PR linked from the session.
- Changed: `docs/dev-setup-windows.md` has a new section 6.6 (running a spike from a script: keep the PC awake, console click pauses, starting emulators from PowerShell, permanent `adb` path, results are published as a branch, FAIL lines are results, delay numbers are skewed on emulators) and seven new troubleshooting rows (adb timeouts after sleep, stuck `adb` client, paused console, pairing spinner, direct `am start`, window insets, IDE exception pop-up). All from the user's runs of spike 0b.2a.
- Validation: `scripts/docs-lint.sh` markdown and links green; not yet followed on a second machine.
- Open items: update again from the first hardware run (0b.2b).

## Coordinator learnings (2026-10-19)

1. Order of work was wrong. The official pages (Health Services, Wear OS) already said what a week of probe runs measured: MeasureClient is "not intended for background capture"; ExerciseClient delivers data less often with the screen off unless HEART_RATE_5_SECONDS is set; passive data: "When you receive data in the background, it is delivered in batches." ([Google, passive data](https://developer.android.com/training/wearables/health-services/passive)); only one exercise at a time. Read the specifications first, measure second.
2. A milestone must be the requirement, not a demo-able subset. The requirement is detection whenever the watch is on; M1 was screen-on only and was still called a milestone. The first milestone has to meet the user's three sentences or say plainly that it does not.
3. Estimates: numbers were given as facts without asking the lane (2-3 h, 6 h). Ask the lane, mark guesses, and treat the user's budgets (10 minutes per step, 1 hour of validation, "immediately" = 0-2 s) as requirements, not as targets to negotiate.
4. Verify before asking the user. Hours were lost to prompts that did not arrive, changed wireless-debugging ports and unattended runs that were not unattended. Automate with adb; the user does physical actions only.
5. Additions to an approved plan (M3 "day of wear", extra probes) are not mine to make; propose, wait for approval.
6. Do exactly the order given: "stop" is not "archive"; answers in at most 4 lines of 80 characters when asked.
7. Sources: the page-fetch tool returns a summary by keyword, not the page. Map the whole documentation tree, fetch every relevant page, list what was not read (watch user guide, Samsung page, Wear OS ambient and doze pages), and never claim a route that no source or measurement supports.
8. Tool scope: use only repository-scoped search; scheduled checks can fire hours late after a worker restart, so check state directly when it matters.
