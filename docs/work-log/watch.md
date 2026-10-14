# Work log: watch lane (W)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-11 - W1a Watch test-case catalogue

- Issue and PR: #11, PR #19
- Changed: added `docs/test-plans/watch.md` (TC-W-001 to TC-W-099).
- Validation: docs only; ran `scripts/require-gate.sh G-BOOT` (exit 0). Ids extract with the catalogue grep in
  `scripts/check-test-cases.sh`; tests and the red baseline come in W1c.
- Open items: none.

## 2026-10-11 - W1b Watch research notes

- Issue and PR: #13, branch `claude/watch-w1b-research`
- Changed: added `docs/research/watch.md` (reminder mechanism, tile API, passive monitoring, API 36 permissions,
  Galaxy Watch6 Classic heart rate).
- Validation: docs only; `scripts/require-gate.sh G-BOOT` exit 0; sources cited, unverified items marked.
- Open items: exact-alarm permission choice on Wear OS (3.4), tile dependency versions (5.2), Samsung background heart
  rate support, which needs a probe on the real watch and a user decision (6.1/6.2).

## 2026-10-11 - 3.1 Manifest and dependencies

- Issue and PR: #42, branch `claude/watch-3.1`
- Changed: `VIBRATE` and `POST_NOTIFICATIONS` permissions and `NO_BRIDGING` (`com.google.android.wearable.notificationBridgeMode`)
  in the wear manifest; `play-services-wearable` 20.0.1 (version catalog, `# W` entries); `PlayServicesStatus` availability
  check. Runtime request of `POST_NOTIFICATIONS` and the exact-alarm permission come with 3.5 and 3.4.
- Validation: tests first. Red: `:wear:testDebugUnitTest` failed TC-W-050 and TC-W-051 on assertions and TC-W-052 did
  not compile (no dependency, no type). Green: same tests pass; `./gradlew check` BUILD SUCCESSFUL (Spotless, detekt,
  lint, unit tests, pairing, docs checks) covers TC-W-053. `scripts/require-gate.sh G0` exit 0.
- Open items: none.

## 2026-10-11 - 3.8 Debug source set

- Issue and PR: #49, branch `claude/watch-3.8`
- Changed: `Logger`/`LogSink` and `ItemIngest` seam in main (no sink in release, so nothing is logged); in
  `wear/src/debug/` and package `...wear.debug`: `RingBufferLogSink`, `DebugApplication` (installs the sink), `DebugLogActivity`
  (log view), `InjectedMessageReceiver` (requires `android.permission.DUMP`, so only `adb shell` can send) with
  `forwardInjected`; debug manifest. Tests: `wear/src/testDebug/` (TC-W-056) and `wear/src/test/scripts/test-debug-separation.sh`
  (TC-W-054, 055, 057).
- Validation: red first: `:wear:testDebugUnitTest` did not compile (no debug code) and the script failed TC-W-055
  "debug APK contains the debug log view". Green: 4 unit tests pass, the script passes 5 of 5 (a deliberate `wear.debug.Leak`
  class in `src/main` fails `checkNoDebugInRelease` and is named; the real build passes; the release APK and manifest hold no
  debug class), `./gradlew check` BUILD SUCCESSFUL.
- Open items: TC-W-056's "one applied schedule" half comes with 3.3 (the schedule handler installed in `ItemIngest`);
  raw-biometrics checks for TC-W-057/080/081 come with 6.7. The script is not run by CI (`.github/**` and `scripts/**` are S's):
  S can add it to the workflow. detekt excludes only `src/test`, so `testDebug` needs a `@Suppress("FunctionNaming")`.

## 2026-10-12 - 3.8 follow-up: drop the detekt suppression

- Issue and PR: #49 (follow-up), branch `claude/watch-drop-suppress`
- Changed: removed `@Suppress("FunctionNaming")` from `DebugLoggingTest`, since the detekt config now skips `src/testDebug`.
- Validation: `scripts/require-gate.sh G0` exit 0; `./gradlew check` BUILD SUCCESSFUL locally.
- Open items: none.

## 2026-10-14 - W1c-a Tests and red baseline for the G1-only cases

- Issue and PR: #92 (part a; the issue stays open for part b), branch `claude/watch-w1c`
- Changed: scope agreed with the coordinator (W2 does not exist; part a on G1 only, part b after G2a). Tests first for the alert
  evaluator (TC-W-070..076, `protocol/src/test/.../alerts/`), the haptic catalogue (TC-W-030) and the reminder actions (TC-W-031);
  then `AlertEvaluator` and `Reading` in `:protocol` (`alerts/`), `Haptic` and `ReminderAction` in `:wear`, four action strings,
  and the `:wear` dependency on `:protocol`.
- Validation: red commit `d53e22e` (tests only, build fails on unresolved references), evidence in
  `docs/test-reports/watch-baseline.md`. Green: `./gradlew check` BUILD SUCCESSFUL, 10 new tests pass. One test edit after red:
  `labelResName` became `R.string` ids (still unresolved at red). Thresholds and cooldown are inputs of the evaluator, so H1 does not
  change it.
- Open items: TC-W-042 (prompt expiry) needs a decision with the Shared lane on `expiresAt` (a receiver never computes a delay from
  the two clocks). W1c-b (link state, schedule logic, transport cases) waits for G2a.

## 2026-10-14 - W1c-a2 Prompt expiry (TC-W-042, 043)

- Issue and PR: #92 (part a), branch `claude/watch-prompt-expiry` (stacked on `claude/watch-w1c`, PR #93)
- Changed: `Prompt.isExpiredAt` and `Prompt.millisUntilExpiry` in `:wear`, following the coordinator's decision recorded in
  `docs/test-reports/watch-baseline.md`. The selection screen that uses them is 3.7.
- Validation: red commit `0beced5` (unresolved references); green: `./gradlew check` BUILD SUCCESSFUL.
- Open items: none for these cases.

## 2026-10-14 - 3.2 Link client

- Issue and PR: #43, branch `claude/watch-3.2`
- Changed: `LinkClient` and `LinkView` (publishes `/hello/watch`, keeps the state and last-sync time in step with a `LinkTransport`),
  `LinkState.watchLabelRes()`, the `LinkStatus` composable and link strings. Tests: `LinkClientTest` (TC-W-001 to TC-W-007).
  W1c-b is done per step: each step PR carries its own red commit and baseline entry, so no red code reaches `main`.
- Validation: `scripts/require-gate.sh G2a` exit 0. Red commit `2f97a7c` (unresolved `LinkClient`), then green: `./gradlew check`
  BUILD SUCCESSFUL, 8 tests pass.
- Open items: `LinkStatus` is not yet on the home screen and the real Data Layer transport is not written: it needs the link-state
  function (1.5, G2b). TC-W-008 (manual, on the watch) waits for that.

## 2026-10-14 - 3.3 Schedule receive

- Issue and PR: #44, branch `claude/watch-3.3` (stacked on `claude/watch-3.2`, PR #101)
- Changed: `ScheduleReceiver` (decode, major-version check, apply only newer, save, notify; applies a schedule already waiting
  on the node at start; never deletes `/schedule`), `ScheduleStore` with `FileScheduleStore` (one file, atomic replace, a corrupt
  file reads as nothing), `FanOutListener`. New catalogue case TC-W-016 (writer owns cleanup).
- Validation: red commit `9993415` (unresolved references), then green: `./gradlew check` BUILD SUCCESSFUL, 11 schedule tests pass.
- Open items: the Android listener service that feeds `ScheduleReceiver` from the Data Layer is part of the real transport, which
  needs the link-state function (1.5, G2b). TC-W-015 (manual) waits for it.

## 2026-10-14 - 3.4 Local scheduler (logic part)

- Issue and PR: #45 (part), branch `claude/watch-3.4` (stacked on `claude/watch-3.3`, PR #102)
- Changed: `ReminderScheduler` (one exact alarm for the earliest of the next scheduled reminder and the pending snoozes;
  `rearm` is the only place that sets alarms; `snooze`, `clearSnooze`, `onAlarm`), `AlarmPort` seam, `SnoozeStore` with
  `FileSnoozeStore`. Uses `ScheduleLogic.nextReminder` and `ScheduleLogic.snooze` from `:protocol`.
- Validation: red commit `dceb6f0` (unresolved references), then green: `./gradlew check` BUILD SUCCESSFUL, 10 scheduler tests pass.
- Open items: the Android parts of 3.4 are not started: the `AlarmManager` implementation of `AlarmPort`, the boot and alarm
  receivers and the manifest permissions. They need the choice between `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` on Wear OS
  (`docs/research/watch.md`, section 1), which I asked the coordinator to settle with the user. TC-W-025 (24-hour run) and
  TC-W-026 (reboot) are manual and wait for those parts.
