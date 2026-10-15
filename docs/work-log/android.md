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
