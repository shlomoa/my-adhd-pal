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

## 2026-10-14 - 3.5 Haptics and notifications (Android part)

- Issue and PR: #46 (part), branch `claude/watch-3.5` (stacked on `claude/watch-3.4`, PR #103)
- Changed: `ReminderChannel`, `HapticPlayer` (the catalogue on the vibrator), `ReminderNotifier` (notification with the four actions
  and a haptic, nothing is shown when notifications are off), `ReminderActionReceiver` (for now it only closes the notification),
  four action icons, the `POST_NOTIFICATIONS` runtime request in `MainActivity`, and the debug-only `FireReminderReceiver` to show a
  reminder and play a haptic with `adb`. `androidx.fragment` 1.9.1 is pinned because `play-services-wearable` pulls in 1.1.0, which
  fails lint `InvalidFragmentVersionForActivityResult`.
- Validation: red commit `095725f`, then green: `./gradlew check` BUILD SUCCESSFUL; the debug-separation script passes 5 of 5.
- Open items for the user (TC-W-033, TC-W-034), with a debug build on the watch: (1) run
  `adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.FireReminderReceiver --es haptic TICK`, then WARNING, then
  CALM_BREATH: are the three haptics distinguishable? (2) With the screen off, does the notification appear with Done, Snooze,
  Start 2 min and Dismiss, and does each tap close it? (3) Does the app ask for the notification permission on first launch?
  The effects of the actions (snooze, 2-minute timer, acknowledgement) are 3.6, which waits for a protocol decision (an
  acknowledgement cannot name its reminder).

## 2026-10-14 - 3.7 Selections

- Issue and PR: #48, branch `claude/watch-3.7` (stacked on `claude/watch-3.5`, PR #106)
- Changed: `PromptInbox` (the pending prompts are derived from the items on the node: valid, supported, not expired on the watch
  clock, no `/selection/<id>` of the watch's own yet; `choose` sends `/selection/<id>` with the prompt's id, the key and the watch
  time; the watch never deletes `/prompt`) and the `SelectionScreen` composable (one button per option).
- Validation: red commit `b62df3f` (unresolved `PromptInbox`), then green: `./gradlew check` BUILD SUCCESSFUL, 10 new tests pass.
- Open items: the screen and the inbox are not yet wired to a real transport (1.5, G2b); TC-W-045 (manual) waits for it.

## 2026-10-14 - 3.4 Local scheduler (Android part)

- Issue and PR: #45, branch `claude/watch-3.4-android` (stacked on `claude/watch-3.7`, PR #107)
- Changed: `AndroidAlarmPort` (`setExactAndAllowWhileIdle`, or `setAndAllowWhileIdle` when exact alarms are not allowed),
  `AlarmReceiver` (shows the fired reminder, arms the next alarm), `BootReceiver` (re-arms after boot, time or zone change, app
  update and exact-alarm permission change; other actions are ignored), `WatchApplication` with a hand-wired `AppGraph`, the
  manifest permissions (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` up to API 32, `RECEIVE_BOOT_COMPLETED`) and receivers, a
  home-screen warning when the alarm is inexact. `SchedulerStatus.PermissionMissing` became `Armed(exact = false)` (TC-W-023
  rewritten); `docs/research/watch.md` records the decision and that it is revisited at milestone 7. New case TC-W-027.
- Validation: red commit `0dc5da4`, then green: `./gradlew check` BUILD SUCCESSFUL; the debug-separation script passes 5 of 5.
- Open items for the user (TC-W-025, TC-W-026), on a debug build on the watch, with the watch not connected to the phone: (1) after
  a first schedule exists on the watch (the debug injected-message receiver can deliver one once step 3.2 to 3.3 are wired to the real
  transport, G2b), leave the screen off for 24 hours: does every reminder fire within 1 minute of its time? Write down the scheduled
  and the actual time of each. (2) Restart the watch: does the next reminder still fire on time?

## 2026-10-14 - 3.6 Acknowledgements

- Issue and PR: #47, branch `claude/watch-3.6` (stacked on `claude/watch-3.4-android`, PR #108)
- Changed: `ReminderActionHandler` (the local effect first: Snooze moves the alarm, Start 2 min starts a 2-minute timer, Done and
  Dismiss end a pending snooze; then a fresh-UUID `/ack/<id>` with action, time, `reminderId` and `occurrenceAt`; a snooze beyond the
  cap is refused and sends nothing; the same action on the same notification twice sends one ack; without a transport the local
  effect still happens and the missing ack is logged), `TimerPort` with `AndroidTimerPort` and `TimerReceiver` (a calm haptic when the
  two minutes are over, my choice to be confirmed by the user), `ReminderActionReceiver` wired to the handler. `SnoozeEntry` and
  `FiredReminder` carry the scheduled `occurrenceAt` (also after several snoozes). `AppGraph.transport` stays empty until the Data Layer
  wiring (1.5, G2b) sets it; the watch never deletes its own acks (the phone does after storing).
- Validation: red commit `3cc8a13`, then green: `./gradlew check` BUILD SUCCESSFUL, 8 new tests pass.
- Open items for the user (TC-W-034, now complete with the actions): on a debug build, fire a reminder with
  `adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.FireReminderReceiver`, tap Snooze (the notification closes and the
  reminder returns after the snooze minutes of the schedule), tap Start 2 min (after two minutes a calm haptic), tap Done and Dismiss
  (the notification closes). Acknowledgements reach the phone only once the real transport exists (G2b), so the phone side is TC-W-090.

## 2026-10-14 - 5.1 Research the current tile API

- Issue and PR: #51, branch `claude/watch-5.1`
- Changed: `docs/research/watch.md` section 2 updated with the current artifact versions from Google Maven (tiles 1.6.2,
  protolayout 1.4.2, including `protolayout-material3`), `Material3TileService`, the update mechanisms, tap handling and a
  recommendation for 5.2 and 5.3.
- Validation: docs only; sources cited, unverified points marked; `scripts/docs-lint.sh markdown` 0 issues.
- Open items: whether work in `onTileRequest()` may outlive the response, the `tiles-testing` API and a clean build of
  `protolayout-material3` under AGP 9.4 and Kotlin 2.4 are settled by the first compile in 5.2 (a build failure there is a
  "stop and ask" case).

## 2026-10-14 - G5 Watch core flip

- Issue and PR: gate G5 (3.1 to 3.7), PR #112
- Changed: `docs/plans/gates.json` marks G5 merged. Steps 3.1 (#79), 3.2 (#101), 3.3 (#102), 3.4 logic (#103) and Android part (#108),
  3.5 (#106), 3.6 (#109), 3.7 (#107) and 3.8 (#80) are merged.
- Validation: each step has its red commit and green `./gradlew check` in its own PR. These manual checks are NOT yet run by the user and
  are pending in the user's hardware session: TC-W-025 and TC-W-026 (24-hour reminder run, reboot), TC-W-033 and TC-W-034 (haptic feel,
  notification and its actions). They must be reported by the user before W-final and G7, and nothing may claim them verified until then.
  Also not done: the real Data Layer transport (step 1.5, G2b), so TC-W-008, 015, 045 and the phone side of acknowledgements wait.
- Open items: none besides the above.
- BLOCKED: 5.2 waits for G5 (merging this PR)

## 2026-10-14 - 5.2 Tile

- Issue and PR: #52, branch `claude/watch-5.2`
- Changed: the tile libraries (tiles 1.6.2, protolayout and protolayout-material3 1.4.2, tiles-testing for tests) in the `# W`
  catalogue entries; `ReminderTileService` (a `Material3TileService`: next reminder with its time, a Start 2 min edge button, the
  link state and last sync), `TileModel`/`tileModel(...)` (what is shown, as data), `TileActionActivity` (exported because a tile can
  only open an exported activity; it validates its extras and only acts on a reminder in the schedule), a placeholder tile preview,
  and `nextDue(...)`, which the scheduler and the tile now share so that both agree on what is due next.
- Validation: the libraries compile under AGP 9.4 and Kotlin 2.4 (the stop-and-ask check passes). Red commit `75ff287`, then green:
  `./gradlew check` BUILD SUCCESSFUL, 6 tile model tests pass; the debug-separation script passes 5 of 5.
- Open items for the user (TC-W-066, needs a build on the watch): add the tile (swipe to the end of the tile carousel, add
  "Reminders"); does it show the next reminder with its time, the link state and the last sync, and does Start 2 min start the
  two minutes (a calm haptic after two minutes)? Choice to confirm: the tile's primary action is Start 2 min for the shown reminder
  (not Done), because Done on a reminder that has not fired yet would need the scheduler to skip that occurrence. The tile preview is
  a placeholder drawing, to be replaced by a screenshot. The tile is not refreshed by events yet: that is 5.3.

## 2026-10-14 - 5.3 Tile update triggers

- Issue and PR: #53, branch `claude/watch-5.3` (stacked on `claude/watch-5.2`, PR #119)
- Changed: `TileUpdateTriggers` (a schedule applied, an action handled and a link-state or last-sync change each request a new tile),
  `ReminderActionHandler.onHandled` (called after an action was carried out, not for a duplicate or a refused snooze),
  `tileFreshness` (the tile stays valid until the next reminder is due, between one minute and a day), and the wiring in `AppGraph`
  (`TileService.getUpdater(...).requestUpdate(...)`); the tile service sets the freshness interval.
- Validation: red commit `fde0a8a`, then green: `./gradlew check` BUILD SUCCESSFUL, 4 new tests pass.
- Open items: the schedule receiver and the link client are connected to the triggers once they are wired to the real transport
  (1.5, G2b); until then only the action trigger runs in the app. Manual check with TC-W-066: after Start 2 min or a new schedule
  the tile shows the next reminder without being re-added.

## 2026-10-15 - Demo hooks on the watch

- Issue and PR: none yet (demo path, coordinator priority), branch `claude/watch-demo`
- Changed: `DemoLog` (the `DEMO` logcat lines the demo script reads: schedule applied, reminder fired, ack sent or not sent, ids and
  times only), `AppGraph.lastFired`, and the debug-only `DemoActionReceiver` (`am broadcast -n com.shlomoa.myadhdpal/.wear.debug.DemoActionReceiver --es action DONE`)
  that does what a tap on the last notification would do. Phone side agreed with the Android lane: `PUBLISH_DEMO_SCHEDULE` (extras
  `minutes_ahead`, `title`, reminder id "demo") and a `DUMP_HISTORY` readback that logs `DEMO history ...` lines.
- Validation: red commit `0261b13`, then green: `./gradlew check` BUILD SUCCESSFUL; the debug-separation script passes.
- Open items: the real Data Layer transport on the watch (waiting for the Shared lane's answers), the demo script itself, and the
  phone receivers (G3).

## 2026-10-15 - Real Data Layer transport on the watch

- Issue and PR: none (demo path, coordinator priority), branch `claude/watch-datalayer`, stacked on `claude/shared-1-5` (needs
  `LinkStateLogic` and `Capabilities` from #131)
- Changed: `CachedTransport` (in-memory cache behind the synchronous `LinkTransport`, async writes through `ItemWriter`, remote events
  reported only when another node wrote them, a late `load` never overwrites a newer change), `ItemMapping` (the four keys, urgent
  paths), `DataLayerTransport` (Android: stored items read once, writes with `setUrgent` for prompt/ack/selection/alert, link state
  from the node list, the `adhd_pal_phone` capability and the phone's hello via `LinkStateLogic`), `WatchListenerService` (manifest
  filters `/schedule`, `/prompt/`, `/hello/phone`), `res/values/wear.xml` (declares `adhd_pal_watch`). `AppGraph` now builds the link
  at every process start, wires `ScheduleReceiver`, `LinkClient`, `PromptInbox` and the tile triggers through a fan-out, publishes
  `/hello/watch`, and `MainActivity` re-reads the link state every 2 s while shown. The watch deletes only its own items.
- Validation: red commit `03b5d22` (tests only), then green: `./gradlew :wear:check :wear:lintDebug :wear:assembleRelease` successful.
- Not run: TC-W-118 and the Data Layer calls themselves (manual, real devices); TC-W-008, TC-W-015, TC-W-045 and TC-W-103 stay NOT yet run.
- Open items: the phone-side `DataLayerTransport` (Shared lane, 2.4) must use the same paths and keys; `MessageClient` is not used.

## 2026-10-15 - Demo script

- Issue and PR: none yet (demo path, coordinator priority), branch `claude/watch-demo-script`
- Changed: `scripts/demo/run-demo.ps1` (one command: readiness gate, explicit `-Phone` and `-Watch`, per-call timeouts, build and
  install of the debug apps, eight stages with PASS or FAIL, what to see on the watch, results pushed as a `demo-results/<time>`
  branch without device identifiers), a simulated `adb` and `scripts/demo/test-run-demo.sh`, and `docs/demo.md`. Catalogue cases
  TC-W-102 (script test) and TC-W-103 (the user's run).
- Validation: `scripts/demo/test-run-demo.sh` with PowerShell 7.5.2: 48 checks pass; breaking the late-reminder rule on purpose makes
  two of them fail (see the baseline report).
- Open items: the script cannot pass on the real devices until the phone receivers (`PUBLISH_DEMO_SCHEDULE`, `DUMP_HISTORY`, after
  G3) and the real Data Layer transport on both sides exist. The phone receiver class names are a guess to confirm with the Android
  lane (`com.shlomoa.myadhdpal.phone.debug.*`). TC-W-103 is manual and NOT yet run.

## 2026-10-15 - Step 3.9 end-to-end checklist

- Issue and PR: step 3.9 (G7), branch `claude/watch-3-9-checklist`
- Changed: `docs/test-reports/watch-e2e-checklist.md`, the manual checklist for TC-W-090 to TC-W-093 and TC-W-103 (device runs with
  `run-demo.ps1`, phone away, Bluetooth toggled, paired emulators) and a report template. G3, G4, G5 and H2 are merged.
- Validation: docs lint only. NOT yet run: TC-W-090 to TC-W-093 and TC-W-103; G7 is not flipped until the user reports results.
- Open items: the user's runs.

## 2026-10-15 - Demo script against the phone receivers (#141)

- Issue and PR: none (demo path), branch `claude/watch-demo-contract`
- Checked `scripts/demo/run-demo.ps1` against Android #141: component names, action strings, extras (`minutes_ahead`, `title`), the
  `published schedule ... reminderAt=`, `unchanged` and `publish failed` lines, the `history ack ... reminderId= occurrenceAt=` line and the
  reminder id `demo` all match. One mismatch: the phone repeats the tag in its messages (`DEMO published ...`); the parser now drops it.
- Validation: simulated adb changed to the phone's real format first (18 checks red), then fixed: 48 PASS.
- Open items: TC-W-103 (the user's run on the devices) is NOT yet run.

## 2026-10-15 - Biometric decision document (step 6.2)

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), branch `claude/watch-biometrics-plan`
- Changed: `docs/plans/biometrics.md` with the user's decisions D1 to D8 (D3 relative threshold resting + 30 bpm with a 20 to 50
  phone setting, D4 5-minute cooldown with an escalation level, D5 strongest safe alert, D7 permission requested from the phone),
  the research behind the open points (resting baseline source, phone-triggered permission, Do Not Disturb, Bedtime and
  vibration-intensity limits, full-screen intent) each marked verified, unverified or proposal, the probe that must run on the
  watch first, and the changes owed by the Shared and Android lanes.
- Validation: docs lint. H1 is NOT flipped: it flips in a separate one-line PR after the user confirms the document.
- Open items: five questions for the user at the end of the document; the real-watch probe (H2).

## 2026-10-15 - Biometric decision document: the user's answers

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), PR #147
- Changed: `docs/plans/biometrics.md` records the answers: fallback 110 bpm until a baseline exists, alerts every 2 minutes after the
  5-minute cooldown, the phone must shake and flash too (new section 4a, Android lane), theater mode and full-screen-intent limits accepted.
- Validation: docs lint. H1 is NOT flipped. Still open: confirmation of the baseline rule and the cap on level-2 repeats.

## 2026-10-15 - Demo script: wait for the user's tap

- Issue and PR: demo path (coordinator request after the real run `demo-results/20261015-134043`), branch `claude/watch-demo-waitfortap`
- Why: the script pressed Done seconds after the reminder fired, so the notification was gone before the user looked.
- Changed: `-WaitForTap` and `-TapWaitSec` (default 90) in `run-demo.ps1`: after the Notification stage it asks the user to feel and
  look, then waits for any action in the watch log (DONE, SNOOZE, START_2_MIN, DISMISS); the Done stage names the action and the Ack
  stage checks that action in the phone history. The default mode is unchanged. `docs/demo.md` updated.
- Validation: tests first, 12 red, then 61 PASS, 0 FAIL against the simulated adb. NOT yet run on the devices (TC-W-103).

## 2026-10-16 - Gate H1 flip

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), branch `claude/watch-h1-flip`
- Changed: `docs/plans/gates.json`: H1 merged, pr 147 (the biometric decision document, confirmed by the user on 2026-10-16).
- Validation: `scripts/test/test-gates.sh` no failures, docs lint. Merges only after #147.

## 2026-10-16 - Biometric decision document: baseline is the average

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), PR #147
- Changed: `docs/plans/biometrics.md`: baseline = the average (the user's word, relayed with the exact words), initial value 110 bpm
  approved. The data set (average of the hourly lows over 7 days, known after 24 values) is my reading and marked for correction.
  Cap of 6 repeats and the "I'm OK" button stay proposals.
- Validation: docs lint. H1 is NOT flipped.

## 2026-10-16 - Biometric decision document: baseline as defined is accepted

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), PR #147
- Changed: `docs/plans/biometrics.md`: the user corrected the earlier wording and accepted the baseline as defined in the document
  (hourly low = median of the lowest 3 consecutive samples; baseline = 20th percentile of the hourly values of the last 7 days;
  known after 24 values); 110 bpm fallback Decided. Cap of 6 repeats and the "I'm OK" button stay proposals.
- Validation: docs lint. H1 is NOT flipped.

## 2026-10-16 - Biometric decision document confirmed

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55), PR #147
- Changed: `docs/plans/biometrics.md`: the user approved the last two items ("I'm OK" button and the cap of 6 repeats) on 2026-10-16;
  section 8 has nothing open and the document is the confirmed H1 document. H1 flips in a separate one-line PR.
- Validation: docs lint.

## 2026-10-16 - "I'm OK" on the phone ends the episode on the watch

- Issue and PR: [#55](https://github.com/shlomoa/my-adhd-pal/issues/55) (decision in the issue body), branch `claude/watch-end-episode`
- Changed: `docs/plans/biometrics.md` (section 4a and the table in section 7) and `docs/test-plans/watch.md` (TC-W-084, TC-W-085): when the Shared lane
  defines the phone-to-watch end-episode item (protocol 1.2), the watch stops its repeats on receiving it. Planned with 6.5 and 6.6, tests first.
- Validation: docs lint. Nothing built; waiting for the item's path and body in `docs/protocol.md`.

## 2026-10-16 - Step 6.5, part 1: the resting baseline

- Issue and PR: step 6.5 ([plan](../plans/watch.md)), branch `claude/watch-6-5`
- Changed: `BaselineTracker` and `HourlyLow` in `:protocol` (`alerts/`), pure logic: hourly low = lowest median over 3 consecutive
  samples of an hour, baseline = 20th percentile (nearest rank) of the hourly lows of the last 7 days, known from 24 lows; only the
  derived hourly lows are saved and restored. TC-W-086 to 089 and TC-W-120 to 122 added to the catalogue.
- Reading of the decision: "median of the lowest 3 consecutive samples" is implemented as the lowest median over any 3 consecutive
  samples of the hour. The nearest-rank percentile and UTC whole hours are my choices; both are in the class comment.
- Validation: red first (`docs/test-reports/watch-baseline.md`), then green: `./gradlew detekt spotlessCheck :protocol:check`.
- Open items: the episode evaluator (threshold = baseline + offset, escalation, cap, end-episode) is the next PR; the passive
  listener and the alert screens need the real-watch probe first.
