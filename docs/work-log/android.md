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

## 2026-10-14 - A1b Red baseline for ViewModel/UI-state tests

- Issue and PR: none yet (2.7 merged as #83).
- Changed: nothing; `scripts/require-gate.sh G3a G-ENV` exited 3.
- Validation: n/a. The tests are written against the phone data contract (`WatchLinkDataSource`, repository interfaces and fakes), which G3a delivers; G1 alone provides only `:protocol` models.
- Open items: the coordinator message suggested G1 unblocks A1b, but `docs/plans/android.md` lists G3a and G-ENV as its requirements; I followed the lane file.

## 2026-10-14 - A1b Red baseline recorded (resumed after G3a)

- Issue and PR: no PR by design (see Validation); thread branch `claude/android-A1b`. Resumed after G3a merged.
- Changed: ViewModel and UI-state tests for TC-A-001..008, 013..029, 031..037, 039..043, 045..051, 053..057 under `phone/src/test/.../ui/`, written against the G3a contract (`docs/data-layer.md`) and its fakes; `ManualCases.kt` lists the manual, CI, docs and Compose-UI ids so `scripts/check-test-cases.sh android baseline` sees every id.
- Validation: `./gradlew :phone:testDebugUnitTest` fails to compile with 67 errors, all "Unresolved reference" to the not-yet-written ViewModels and UI states (`ListState`, `LinkStateViewModel`, `TaskListViewModel`, ...). Details in `docs/test-reports/android-baseline.md`. `scripts/check-test-cases.sh android baseline` exits 0. Red commit precedes any implementation. A step PR merges only when green, so each step (2.6, 4.5, 4.1-4.4, 4.6) takes over its own test files from this branch together with its implementation.
- Open items: TC-A-058 and TC-A-059 (Compose accessibility tests) need `androidx.compose.ui:ui-test-junit4` and `ui-test-manifest` in `phone/build.gradle.kts`, which the Shared lane owns; an issue labelled lane S asks for them before step 4.6.

## 2026-10-14 - 2.6 Link-state screen

- Issue and PR: #40; PR linked from the coordinator message.
- Changed: `ui/ListState` (shared list states), `ui/link/LinkStateViewModel` (+ `LinkStateUiState`, `LastSync`), stateless `LinkStateScreen` and its strings. It also brings in the A1b test support (`TestSupport.kt`, `ManualCases.kt`) and the baseline report.
- Validation: `LinkStateViewModelTest` (TC-A-001 to TC-A-008) red first (does not compile, `docs/test-reports/android-baseline.md`), then 8 of 8 PASS; `./gradlew check assembleDebug` green. A first run failed 6 of 8 because the test built the ViewModel in a field initializer, before `MainDispatcherRule` installed the test dispatcher; the test now creates it lazily. The other A1b test files on `claude/android-A1b` have the same pattern and are fixed when their step lands.
- Open items: the screen is not wired into `MainActivity` yet: the real `LinkStatusRepository` binding comes with the data layer (2.5, G3), and a `@HiltViewModel` without a binding would break the graph, so the annotation is added then. Manual TC-A-009 (A55 with the Watch6 Classic: Nearby, Relayed, Away) waits for the user once the screen is wired.

## 2026-10-14 - 4.5 History screen

- Issue and PR: #67; PR linked from the coordinator message. Stacked on the 2.6 branch (#116): it reuses `ListState` and the test support, so merge #116 first.
- Changed: `ui/history/HistoryViewModel` (+ `HistoryUiState`: filter and `ListState`, retry), stateless `HistoryScreen` with type filter chips and the empty, loading and error states, strings. Completes G4 together with 2.7 (#83) and 2.6 (#116).
- Validation: `HistoryViewModelTest` (TC-A-045 to 051, 054, 056) red first (does not compile), now 9 of 9 PASS; `./gradlew check assembleDebug` green.
- Open items: not wired into `MainActivity` until the data layer binds `HistoryRepository` (G3); then the manual TC-A-052 (history stored with the app closed) can run. The Selected row shows the prompt title only if the repository fills it (data layer, lane S).

## 2026-10-14 - G4 flip

- Issue and PR: gate flip after #83 (2.7), #116 (2.6) and #117 (4.5) merged.
- Changed: `docs/plans/gates.json`: G4 to `merged`, `pr` 117 (the PR that completed it).
- Validation: `scripts/check-pr-gates.sh` with `Requires: G3a,G-LIB` and `Produces: G4`; the Gates workflow enforces it.
- Open items: 4.1 to 4.4 wait for G3; 4.6 and G6 follow them.

## 2026-10-14 - 4.6 (part 1) Accessibility tests for the existing screens

- Issue and PR: part of #68 (4.6); the PR references it and does not close it. 4.6 is complete only with the screens of 4.1 to 4.4.
- Changed: `ui/AccessibilityTest` (Compose UI test under Robolectric) covering TC-A-058 (every actionable node has a label) and TC-A-059 (every touch target at least 48 dp) for the link-state screen and the history screen in its content, filtered-empty and failed states. `ManualCases.kt` no longer lists the two ids. Each screen added by 4.1 to 4.4 joins the `screens` map.
- Validation: 2 of 2 PASS; `./gradlew check assembleDebug` green. Not red first, and I say so: the screens were already built with text labels and Material's minimum touch target, so the tests pass once they measure correctly. The only red was a test bug while writing them: TC-A-059 first measured the drawn size (36 px for a `FilterChip`) instead of `touchBoundsInRoot`, which is the 48 dp touch area Material provides.
- Open items: manual TC-A-060 (large font and TalkBack) and TC-A-061 (contrast, dark theme) wait for the user once the screens are wired in; the empty/error/loading states (TC-A-053 to 057) for tasks and the schedule arrive with their screens. `scripts/check-test-cases.sh android baseline` still lists those ids as without a test because their tests sit on `claude/android-A1b` until their step lands.

## 2026-10-15 - Demo schedule publisher (debug), part 1

- Issue and PR: demo priority from the coordinator; no plan.md step id. Issue: see the PR.
- Changed: `phone/src/debug/.../debug/DemoSchedulePublisher` (debug source set, `debug` package so release builds exclude it): puts one reminder N minutes ahead (default 2, 1 to 1440) with a title into the phone's data and publishes through the `ScheduleRepository` interface; fixed ids so repeats replace it. Catalogue rows TC-A-073 to TC-A-080.
- Validation: `DemoSchedulePublisherTest` red first (does not compile, `docs/test-reports/android-baseline.md`), now 6 of 6 PASS; `./gradlew check assembleDebug` green (includes `checkNoDebugInRelease`, TC-A-079). detekt flagged a magic number, fixed with a named constant.
- Open items: the broadcast receiver `PUBLISH_DEMO_SCHEDULE` is part 2: it needs the real `ScheduleRepository` and `WatchLinkDataSource` (G3) to build the publisher, and a Hilt entry point without a binding would break the graph. Manual TC-A-080 runs on the devices once the receiver exists.
- Follow-up in the same PR: the Watch lane's demo script needs a known reminder id, so the fixed id is now `demo` (TC-A-073 asserts it; red first with the old id `demo-reminder`, then green).

## 2026-10-15 - Demo schedule publisher (debug), part 2: broadcast receivers

- Issue and PR: demo priority from the coordinator; PR linked from the coordinator message. Builds on #129.
- Changed (all in `phone/src/debug`, `phone.debug` package): `DemoScheduleReceiver` (`com.shlomoa.myadhdpal.debug.PUBLISH_DEMO_SCHEDULE`, extras `minutes_ahead`, `title`) and `DemoHistoryReceiver` (`...DUMP_HISTORY`), both exported and protected by `android.permission.DUMP` in the debug manifest; `DemoCommands` (the logged `DEMO ...` lines, the contract with the Watch lane's `scripts/demo`); `DemoSchedulePublisher.publish()` now returns the outcome and the reminder time. The receivers are thin Hilt entry points over the real repositories, off the main thread with `goAsync()`.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), then `DemoCommandsTest` 5 of 5 and `DemoSchedulePublisherTest` 6 of 6 PASS (TC-A-073 to 078, 081 to 085); `./gradlew detekt spotlessCheck check assembleDebug` green, including `checkNoDebugInRelease`.
- Open items: manual TC-A-080 (the broadcast, then the reminder on the watch) is for the user with `scripts/demo` from the Watch lane. The receivers are not exercised end to end by a unit test: the repositories and the Data Layer are real only on the device. Wiring the screens into `MainActivity` is the next PR.

## 2026-10-15 - Screens wired into MainActivity (2.6 and 4.5 on the real graph)

- Issue and PR: demo priority from the coordinator; PR linked from the coordinator message.
- Changed: `LinkStateViewModel` and `HistoryViewModel` are `@HiltViewModel`; `ui/HomeScreen` (two tabs, Watch and History, bottom navigation bar, no extra navigation library); `MainActivity` shows it from the two ViewModels; the placeholder greeting string is gone. `AccessibilityTest` also covers the home screen.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), then TC-A-086 (`HomeScreenTest`) and TC-A-087 (`MainActivityWiringTest`: the real Hilt graph with Room and DataStore, only the Play services client replaced by `FakeDataLayerClient`) PASS; accessibility tests still 2 of 2; `./gradlew detekt spotlessCheck check assembleDebug` green.
- Open items: manual TC-A-088 (link state changes with Bluetooth and distance; an acknowledgement from the watch appears in History) for the user on the A55 and the Watch6 Classic. Steps 4.1 to 4.4 add further tabs.
- CI fix on the same PR: `MainActivityWiringTest` (TC-A-087) failed once on the CI runner at the History-tab assertion while passing locally. Cause: the History screen starts in `Loading` and Room answers on its own executor, which Compose's idling does not track, so a slow runner could assert before the data arrived (the screen was right, the test did not wait). The test now waits for the expected content with `waitUntil` (10 s) instead of asserting at once; no skip or retry. I could not reproduce the failure locally, even with all four cores busy, so the cause is argued from the code path, not observed.

## 2026-10-15 - 4.1a Task list and task edit (ViewModels and screens)

- Issue and PR: first part of #62 (4.1); the PR references it and does not close it. Reminders (4.1b) and the wiring into the app (a tab) are the next PRs.
- Changed: `ui/tasks/TaskListViewModel` (loading, empty, content, failed with retry; delete), `TaskEditViewModel` (create or edit by `taskId`; trims, rejects blank or over 200 characters; the unsaved title survives recreation through `SavedStateHandle`), stateless `TaskListScreen` and `TaskEditScreen`, strings. `AccessibilityTest` covers both screens.
- Validation: `TaskViewModelsTest` (TC-A-013 to 017, 023, 053, 056, 057) red first (did not compile, `docs/test-reports/android-baseline.md`), now 9 of 9 PASS; accessibility tests 2 of 2; `./gradlew detekt spotlessCheck check assembleDebug` green.
- Open items: not in `MainActivity` yet (next PR); TC-A-023's manual rotation check and the manual round trip (a task becomes a reminder on the watch) follow with the wiring and 4.1b/4.2.

## 2026-10-15 - 4.1 wiring: a Tasks tab in the app

- Issue and PR: second part of #62 (4.1); references it and does not close it. Stacked on #148 (4.1a), so merge #148 first.
- Changed: a third tab, Tasks, in `HomeScreen` (the tab content is passed in as a slot); `ui/tasks/TasksTab` (list, then the edit form, the choice survives recreation); `MainActivity` creates one `TaskEditViewModel` per edit session with the task id as its `SavedStateHandle` argument (`ViewModelProvider.create` with creation extras, no new dependency; the build file is the Shared lane's). The test-only fake Data Layer client is now one `@TestInstallIn` module for every Hilt test (`FakeLinkTestModule`) instead of a copy per test.
- Validation: `TasksWiringTest` (TC-A-089) red first (`docs/test-reports/android-baseline.md`), now PASS: add a task through the real graph (Room included), see it listed, delete it. `MainActivityWiringTest`, `HomeScreenTest` and the accessibility tests still pass; `./gradlew detekt spotlessCheck check assembleDebug` green. One full run first crashed in lint (a detector error right after a run with a failing test) and the identical rerun passed; I read it as stale incremental output (same pattern as the KSP case earlier), CI builds clean.
- Open items: reminders for a task (4.1b), then 4.2 to 4.4. Manual round trip (a task on the phone becomes a reminder on the watch) needs 4.1b and 4.2.
- CI fix on the same PR: `TasksWiringTest` (TC-A-089) failed on the CI runner at the click on "Delete". Cause, from the test order: `waitForText("Take the pills")` was satisfied by the text field of the still-open edit form (the field contains that text), so the test moved on; once the form closed the list could still show its old state until Room answered on its own executor, and "Delete" did not exist yet. The test now waits for the form to close, then for the list row and its Delete button, before clicking. I checked the other Hilt/Room wiring test (`MainActivityWiringTest`): it already waits for content after its only click, a static tab. Not reproduced locally, so argued from the code path.

## 2026-10-15 - 4.1b Reminder edit (ViewModel and screen)

- Issue and PR: third part of #62 (4.1); references it and does not close it. Listing a task's reminders and wiring this form into the Tasks tab is the next PR.
- Changed: `ui/reminders/ReminderEditViewModel` (create for a `taskId`, or edit by `reminderId`; the time and days survive recreation; a new reminder repeats every day; a missing or out-of-range time and an empty, out-of-range or repeated day are rejected; days are stored sorted; delete), stateless `ReminderEditScreen` (24-hour `TimeInput`, day chips, Save/Cancel/Delete), strings. `AccessibilityTest` covers the form with both errors shown. No change to `data/`: an existing reminder is read through `remindersOf`.
- Validation: `ReminderEditViewModelTest` (TC-A-018 to 022) red first (did not compile, `docs/test-reports/android-baseline.md`), now 5 of 5 PASS; accessibility tests 2 of 2; `./gradlew detekt spotlessCheck check assembleDebug` green.
- Open items: not reachable in the app yet. Next small PRs: the reminder list inside the task form, 4.2 (publish the schedule from the UI), 4.3, 4.4; then the phone-side alert (design note first, after the H1 decision in #55).

## 2026-10-15 - Phone alert: design note

- Issue and PR: requirement from #55 (H1) and the decision document (PR #147, section 4a); the coordinator asked for a note before any code. No plan.md step id.
- Changed: `docs/phone-alert.md`: where the alert runs (an `AlertRaiser` started from the application class reading the stored alert rows, so no change to `data/` or `link/`), what the alert is, what can suppress it (each cause marked verified, from the decision document, or unverified), the phone onboarding screen, the offset setting, the steps with what each needs from the other lanes, four questions for the user.
- Validation: docs lint. Read for this note: the Android notifications page (heads-up instead of full screen while the device is in use; `POST_NOTIFICATIONS` from Android 13) and the AOSP full-screen-intent page (default grant only to calling and alarm apps on Android 14, `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, `canUseFullScreenIntent()`, 60 s heads-up without the permission). The `VibrationEffect` reference page did not load, so amplitude and Do Not Disturb behaviour stay marked unverified.
- Open items: the four questions in section 7 for the user. Needs from other lanes: protocol 1.2 (`Alert.level`, `offsetBpm`) and `wear-remote-interactions` (Shared), the watch permission activity and granted-state report (Watch, Shared). No code until the questions are answered and protocol 1.2 exists for the parts that need it; level 1 does not.

## 2026-10-16 - Phone alert: design note updated with the user's answers

- Issue and PR: PR #151 (the note), requirement in #55.
- Changed: `docs/phone-alert.md` records the four answers: sound on by default (a setting turns it off), flashing at most 3 per second with an off switch, "I'm OK" on the phone also ends the episode on the watch (new section 2a; needs the phone-to-watch item in protocol 1.2, Shared, handled on the watch, Watch), and no repeat count on the phone. A-alert-1b (send the episode end) is a separate step after A-alert-1.
- Validation: docs lint.
- Open items: A-alert-1 next, tests first; A-alert-1b and A-alert-3 wait for protocol 1.2.

## 2026-10-16 - A-alert-1 Phone alert (level 1)

- Issue and PR: requirement from #55 (H1); design in `docs/phone-alert.md` (PR #151, with the user's answers). No plan.md step id.
- Changed: package `phone/alert`: `AlertPlanner` (pure: level to vibration, flashing, sound), `AlertRaiser` (reads the stored alert rows, raises each new alert once, only the newest of several, told apart by id, started from the application class so it also runs in a process the listener service started), `DataStoreAlertPreferences` and `DataStoreRaisedAlerts` (own DataStore file, outside `data/`), `AndroidAlertNotifier` (alarm-category notification with a full-screen intent on a high-importance channel, sound or silent, plus the vibration with alarm attributes), `AlertActivity` (shown over the lock screen, screen on, big "I'm OK" that stops the phone's alert, background flashing at most 3 per second), manifest entries and `AlertModule`. Defaults: sound on, flashing on. Every alert is level 1 until protocol 1.2 carries a level.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: `AlertPlannerTest` 5, `AlertRaiserTest` 8, `AlertStoresTest` 2, `AndroidAlertNotifierTest` 4 (TC-A-090 to 102), accessibility tests 2 of 2 with the alert screen added; `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green. Two test slips on the way: my range check for the plan length used `coerceIn` and only passed for an exact sum (fixed to a real range), and detekt's line length in comments.
- Open items: manual TC-A-103 on the A55 (alert arrives with the app closed: vibrates, wakes the screen, shows the full-screen alert; try Do Not Disturb, silent mode, locked and in use, once each). **Before that test, grant the notification permission** (Settings, Apps, ADHD Pal, Notifications; or `adb shell pm grant com.shlomoa.myadhdpal android.permission.POST_NOTIFICATIONS`): the onboarding screen that asks for it is A-alert-2, so for now Android 13 and later shows no alert without it. Not yet: "I'm OK" telling the watch (A-alert-1b, protocol 1.2), the sound and flashing switches on the settings screen (4.3), the onboarding (A-alert-2).

## 2026-10-16 - A-alert-1b and A-alert-3: blocked on the phone data layer

- Issue and PR: lane S issue for the phone data layer; protocol 1.2 is #168 and #169.
- Changed: nothing in code. Checked main at b834195: protocol 1.2 changed only `:protocol` and the docs; nothing under `phone/**/data/**` or `phone/**/link/**` knows `Alert.level`, `Alert.episodeId`, `AlertSettings.offsetBpm` and `initialBpm`, `EpisodeEnd` or `WatchStatus`, and those paths are the Shared lane's.
- Validation: none, no code written for these steps. `claude/android-alert-1` (#170) merged with main: `detekt spotlessCheck check lintDocs assembleDebug` still green.
- Open items: the lane S issue lists four needs in order: the level and episode id in the history rows (Room migration), `WatchLinkDataSource.publishEpisodeEnd` and its cleanup, the offset and initial threshold in `AppSettings` and `ScheduleBuilder`, a read of the last watch status. Lane A continues with the reminder list (4.1c) and 4.2 meanwhile.

BLOCKED: A-alert-1b waits for the phone data layer (lane S issue)

## 2026-10-16 - 4.3 Settings screen

- Issue and PR: #65 (4.3); PR linked from the coordinator message.
- Changed: a fourth tab, Settings. `SettingsViewModel` (loads the settings, the watch status and the alert switches; `save` validates through the repository and, when it passes, publishes a new schedule version; an invalid value changes nothing and reports why), `SettingsScreen` (alert offset 20 to 50, initial threshold, cooldown in seconds, reminder cadence; a number that is not whole is refused before it is sent; the watch's baseline, threshold and heart-rate permission or "has not reported yet"; sound and flashing switches), strings, and the `AlertPreferences` interface that `DataStoreAlertPreferences` now implements (added as a separate binding so it does not touch the providers #179 edits). The absolute thresholds are not editable here (see open items).
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: `SettingsViewModelTest` 13 (TC-A-031 to 037, 110 to 114), `SettingsWiringTest` 1 (TC-A-115: the real graph, Room and DataStore; a valid offset is stored, an out-of-range one shows the error and the stored value stays); the accessibility tests cover both Settings screen states; `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green. Two findings on the way, both about the tests: DataStore runs a write's transform in the caller's context, which for the ViewModel is the main thread, so a test must let Robolectric's paused main looper run before it reads the stored value (on a device it just runs; the production code was right); and TC-A-059 now falls back to the layout size for a clickable node scrolled out of view, whose touch area is empty.
- Open items: manual TC-A-116 on the devices (a changed offset shows up in the watch's reported threshold). Question for the Shared and Watch lanes: `SettingsDefaults` still carries the absolute 150 bpm threshold next to the relative one; if the watch evaluates both, a default install alerts on two rules. Not changed here (their code).

## 2026-10-16 - A-alert-1b "I'm OK" ends the episode on the watch

- Issue and PR: lane A step of the phone alert (requirement #55, design `docs/phone-alert.md`); needs from the Shared lane were issue #171 (done: #175 to #178).
- Changed: `AlertRaiser` raises each alert at its own level (the stored `level`) and `dismiss(episodeId)` stops the phone's alert first, whatever the link does, then calls `WatchLinkDataSource.publishEpisodeEnd` and returns `Sent`, `NoEpisode` (a watch on protocol 1.1 sent no episode id) or `NotSent(reason)`. `AlertActivity` carries the episode id, waits at most 3 s for the item to be accepted, shows a short message if the watch could not be told, and closes. Design note updated.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: `AlertRaiserTest` 12 (TC-A-104 to 107 new), `AlertActivityTest` 1 (TC-A-108: the real Hilt graph, the Play services client faked; the episode end is written urgently with that episode id and the screen closes); `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green. Two things on the way: Android lint crashed once on the new test file right after the red build, and the identical rerun passed (third time this pattern after a failing build; CI builds clean); and my first version of TC-A-108 waited for the scenario to reach `DESTROYED`, which Robolectric does not report promptly, so it now checks `isFinishing`.
- Open items: manual TC-A-109 (on the A55 with the Watch6 Classic, "I'm OK" on the phone ends the episode on the watch; needs the watch side of #6.x). The offset setting comes with the settings screen (4.3).

## 2026-10-16 - 4.1c Reminders inside the task form

- Issue and PR: #62 (4.1), design in the plan; PR below.
- Changed: a saved task's form shows its reminders (`RemindersSection`: time and days as "09:00, Every day", "Add a reminder", a tap opens the reminder form to change or delete it). `TaskRemindersViewModel` lists them sorted by time with the usual loading, empty, content and failed states; `ReminderSummary` turns days into "Every day", "Weekdays" or day names. A new task reopens as an existing one after Save so reminders can be added at once (`TaskEditUiState.taskId`). `TasksTab` was split into route composables; the task form scrolls.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: TC-A-117 to 121 (`ReminderSummaryTest`, `TaskRemindersViewModelTest`, `TaskViewModelsTest`, `RemindersWiringTest` on the real Hilt graph); `TasksWiringTest` (TC-A-089) adjusted for the form staying open after Save; `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green.
- Open items: 4.2 publishes the schedule from the UI. Will conflict with #180 in `MainActivity`/`HomeScreen`; main is merged before the next step.

## 2026-10-17 - 4.2 Send the schedule to the watch from the Tasks tab

- Issue and PR: #63 (4.2); PR below.
- Changed: the Tasks tab starts with a schedule bar (`ScheduleSyncBar`): which version the watch has ("Not sent to the watch yet" before the first send), a "Send schedule to the watch" button, and what the last tap did (sending, sent with its version, already up to date, or the reason it failed). `ScheduleSyncViewModel` calls the real `ScheduleRepository.publish()`, ignores a second tap while sending, and keeps the old version on a failure so a retry is possible. Saving settings (4.3) still publishes by itself.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: `ScheduleSyncViewModelTest` 5 (TC-A-122 to 126), `ScheduleWiringTest` 1 (TC-A-127, real Hilt graph, one `/schedule` item written, the second tap writes nothing); `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green.
- Open items: cleanup check: nothing became unused. The debug demo receivers (`DemoSchedulePublisher`, history dump) are kept on purpose: they put a reminder a few minutes ahead from `adb` without typing and the history dump has no screen equivalent; the user decides if they go after the manual test TC-A-080. 4.4 (prompt publishing) is next.

## 2026-10-17 - 4.4 Ask the watch a question (prompt publishing)

- Issue and PR: #66 (4.4); PR below. Lane S request: #198 (the phone removes its own `/prompt/<id>` when answered or expired).
- Changed: a new Ask tab (`PromptComposeScreen`, `PromptComposeViewModel`): a question, 2 to 6 answers (add and remove), how long it can be answered (no limit, 5 minutes, 15 minutes, 1 hour as radio rows of at least 48 dp), and "Send to the watch". It publishes through the real `PromptRepository.publish` (validation and ids stay in the data layer); empty answers are dropped before sending; errors show next to the fields; a failure keeps the form for a retry; a sent question empties the form and says so. `HomeScreen` gets an `ask` slot.
- Validation: tests first, red (`docs/test-reports/android-baseline.md`), now green: `PromptComposeViewModelTest` 7 (TC-A-039 to 043, 128, 133), `PromptWiringTest` 1 (TC-A-135, real Hilt graph, one `/prompt/` item written), accessibility test with the Ask form in its error state, `HomeScreenTest`; `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green.
- Open items: cleanup check: nothing became unused. Found while reading the data layer: nothing calls `WatchLinkDataSource.removePrompt`, so prompts stay in the Data Layer after the answer or expiry; opened #198 (lane S). Manual TC-A-044 (the watch's answer shows in the history) still needs the user with the watch. Update: #198 landed (#203): the phone removes its own prompt when the answer is stored and for expired ones; the Ask screen needed no change, only my test builds `DefaultPromptRepository` with the new `PublishedPrompts` argument.

## 2026-10-17 - 4.6 Empty and error states, accessibility (traceability pass)

- Issue and PR: #68 (4.6); PR below.
- Changed: `EmptyStatesTest` for the states 4.1 to 4.4 added (reminder list empty, failed and loading; schedule never sent): TC-A-055, 056, 057. The schedule-send tests now carry the catalogue ids TC-A-028 and TC-A-029 instead of the duplicate rows 124 and 125 (removed). TC-A-055 reworded to what the app does (a task without reminders, a schedule never sent).
- Validation: `./gradlew detekt spotlessCheck check lintDocs assembleDebug` green; no red run, see `docs/test-reports/android-baseline.md` (existing behaviour). `scripts/check-test-cases.sh android final` still lists these ids without a test: TC-A-024 to 027 (retired: duplicates of the Shared lane's TC-S-092 and TC-S-164, decision of the coordinator), (the manual TC-A-088, 103, 109 and 116 are now listed in `ManualCases`).
- Open items: asked the coordinator how TC-A-024 to 027 should be traced. Manual 060 and 061 (large font, TalkBack, dark theme) belong to the user.
- Update: TC-A-024..027 retired, covered by TC-S-092 and TC-S-164 (rows deleted from the catalogue).
