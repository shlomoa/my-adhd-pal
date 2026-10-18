# Android lane baseline (red)

Evidence that the tests for step 2.7 (dependency injection) fail before any implementation exists.
Red commit: 648ec9a (tests only, no implementation). Cases TC-A-011 (CI build) is covered by the green `:phone:check`.

## 2.7 Dependency injection

Command: `./gradlew :phone:testDebugUnitTest`. Exit 1, build fails before tests run.

```
e: [ksp] .../phone/di/DiGraphTest.kt:38: [Hilt] @UninstallModules should only include modules annotated with both
@Module and @InstallIn, but found: [ClockModule].
> Execution failed for task ':phone:kspDebugUnitTestKotlin'
BUILD FAILED
```

TC-A-010 and TC-A-012 are red: `ClockModule`, the `@HiltAndroidApp` application and the `@AndroidEntryPoint`
`MainActivity` do not exist yet.

## A1b ViewModel and UI-state tests

Base: f7cb9da (main after G3a); the test files are committed on `claude/android-A1b` without implementation.

Command: `./gradlew :phone:testDebugUnitTest`. Exit 1: the test sources do not compile.

```
e: .../ui/history/HistoryViewModelTest.kt:30:22 Unresolved reference 'HistoryViewModel'.
e: .../ui/link/LinkStateViewModelTest.kt:24:22 Unresolved reference 'LinkStateViewModel'.
e: .../ui/prompts/PromptViewModelTest.kt:23:22 Unresolved reference 'PromptViewModel'.
e: .../ui/reminders/ReminderEditViewModelTest.kt:24:72 Unresolved reference 'ReminderEditViewModel'.
... 67 errors, all Unresolved reference
```

Every case below is red until its step lands:

| Step | Cases |
|---|---|
| 2.6 | TC-A-001 to TC-A-008 |
| 4.1 | TC-A-013 to TC-A-023, TC-A-053, TC-A-056, TC-A-057 (tasks and reminders) |
| 4.2 | TC-A-024 to TC-A-029, TC-A-055 |
| 4.3 | TC-A-031 to TC-A-037 |
| 4.4 | TC-A-039 to TC-A-043 |
| 4.5 | TC-A-045 to TC-A-051, TC-A-054, TC-A-056 |
| 4.6 | TC-A-058, TC-A-059 (Compose UI tests, need a dependency from lane S) |
| not unit tests | TC-A-009, 011, 030, 038, 044, 052, 060 to 072: manual, CI or docs; ids in `ManualCases.kt` |

`scripts/check-test-cases.sh android baseline` exits 0 (every id appears in a test source).

## Demo schedule publisher (debug only)

Red commit: dcce700 (tests and catalogue rows only, no implementation). Command: `./gradlew :phone:testDebugUnitTest --tests '*DemoSchedulePublisherTest*'`. Exit 1, the test sources do not compile:

```
e: DemoSchedulePublisherTest.kt:32:9 Unresolved reference 'DemoSchedulePublisher'.
```

TC-A-073 to TC-A-078 are red. TC-A-079 is the `checkNoDebugInRelease` CI check and TC-A-080 is manual on the devices (ids in `DemoManualCases`).

## Demo broadcast receivers (debug only)

Red commit: 2cf2501 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*Demo*'`. Exit 1, the test sources do not compile:

```
e: DemoCommandsTest.kt:46:28 Unresolved reference 'DemoCommands'.
e: DemoCommandsTest.kt:105:31 Unresolved reference 'DemoActions'.
e: DemoSchedulePublisherTest.kt:44:68 Unresolved reference 'reminderAt' on receiver of type 'PublishOutcome'.
```

TC-A-081 to TC-A-085 are red, and TC-A-073 to 078 are red again because `publish()` now returns the reminder time with the outcome.

## Screens wired into MainActivity

Red commit: 6e71e17 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*HomeScreenTest*' --tests '*MainActivityWiringTest*'`. Exit 1, the test sources do not compile:

```
e: HomeScreenTest.kt:26:17 Unresolved reference 'HomeScreen'.
```

TC-A-086 and TC-A-087 are red (`MainActivityWiringTest` also fails at run time: `MainActivity` still shows the greeting). TC-A-088 is manual on the devices.

## 4.1a Task ViewModels

Red commit: 5fcf014 (tests only). Command: `./gradlew :phone:testDebugUnitTest --tests '*TaskViewModelsTest*'`. Exit 1, the test sources do not compile:

```
e: TaskViewModelsTest.kt:32:9 Unresolved reference 'TaskEditViewModel'.
e: TaskViewModelsTest.kt:58:22 Unresolved reference 'TitleError'.
e: TaskViewModelsTest.kt:103:41 Unresolved reference 'TaskListViewModel'.
```

TC-A-013 to 017, 023, 053, 056 and 057 are red until this step lands.

## 4.1 wiring: the Tasks tab

Red commit: 453bee1 (tests only). `TasksWiringTest` (TC-A-089) fails at run time: no node with the text "Tasks" exists, because the home screen has only the Watch and History tabs.

## 4.1b Reminder edit ViewModel

Red commit: 99279df (tests only). Command: `./gradlew :phone:testDebugUnitTest --tests '*ReminderEditViewModelTest*'`. Exit 1, the test sources do not compile:

```
e: ReminderEditViewModelTest.kt:24:72 Unresolved reference 'ReminderEditViewModel'.
e: ReminderEditViewModelTest.kt:32:12 Unresolved reference 'setMinuteOfDay'.
```

TC-A-018 to TC-A-022 are red until this step lands.

## A-alert-1 Phone alert

Red commit: 46955cd (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*alert*'`. Exit 1, the test sources do not compile:

```
e: AlertPlannerTest.kt:11:25 Unresolved reference 'AlertPrefs'.
e: AlertPlannerTest.kt:14:9 Unresolved reference 'AlertPlanner'.
e: AlertPlannerTest.kt:51:66 Unresolved reference 'cycleMillis'.
```

TC-A-090 to TC-A-102 are red until this step lands. TC-A-103 is manual on the A55.

## 4.3 Settings screen (with the alert offset, the watch status and the alert switches)

Red commits: 60280b4 (ViewModel tests) and 3626012 (Settings tab wiring test), tests only. Command: `./gradlew :phone:testDebugUnitTest --tests '*Settings*'`. Exit 1, the test sources do not compile:

```
e: SettingsViewModelTest.kt:103:12 Unresolved reference 'save'.
e: SettingsViewModelTest.kt:113:29 Unresolved reference 'uiState'.
e: HomeScreenTest.kt:32:21 No parameter with name 'settings' found.
```

TC-A-031 to TC-A-037 and TC-A-110 to TC-A-115 are red until this step lands. TC-A-116 is manual on the devices.

## A-alert-1b "I'm OK" ends the episode on the watch

Red commit: db2ef88 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*alert*'`. Exit 1, the test sources do not compile:

```
e: AlertRaiserTest.kt:64:13 No parameter with name 'endEpisode' found.
e: AlertRaiserTest.kt:133:26 Too many arguments for 'fun dismiss(): Unit'.
e: AlertRaiserTest.kt:154:22 Unresolved reference 'DismissResult'.
```

TC-A-104 to TC-A-108 are red until this step lands. TC-A-109 is manual on the devices.

## 4.1c Reminders inside the task form

Red commit: 56c7230 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*reminders*'`. Exit 1, the test sources do not compile (`Unresolved reference 'summary'`, `Unresolved reference 'DaysSummary'`, `Unresolved reference 'TaskRemindersViewModel'`, `Unresolved reference 'taskId'`). TC-A-117 to TC-A-121 are red until this step lands.

## 4.2 Send the schedule to the watch from the Tasks tab

Red commit: see the first commit of PR 4.2 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*ScheduleSync*' --tests '*ScheduleWiring*'`. Exit 1, the test sources do not compile:

```
e: ScheduleSyncViewModelTest.kt:30:30 Unresolved reference 'ScheduleSyncViewModel'.
e: ScheduleSyncViewModelTest.kt:34:22 Unresolved reference 'ScheduleSyncUiState'.
e: ScheduleSyncViewModelTest.kt:34:48 Unresolved reference 'SyncStatus'.
```

TC-A-122 to TC-A-127 are red until this step lands.

## 4.4 Ask the watch a question (prompt publishing)

Red commit: the first commit of the 4.4 PR (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*PromptCompose*' --tests '*PromptWiring*'`. Exit 1, the test sources do not compile:

```
e: PromptComposeViewModelTest.kt:27:30 Unresolved reference 'PromptComposeViewModel'.
```

TC-A-039 to TC-A-043, TC-A-128, TC-A-133 and TC-A-135 are red until this step lands (the catalogue already had 039 to 043 for the same behaviour, so the tests carry those ids instead of new ones).

## 4.6 Empty and error states, accessibility

No red run: 4.6 checks states that earlier steps already built (the reminder list, the schedule bar), so the new `EmptyStatesTest` cases (TC-A-055, 056, 057 for the reminder list) passed on first run against existing code. The other 4.6 cases already have tests from 4.1 to 4.4 (TC-A-053, 054, 056, 057, 058, 059). The change that matters here is the traceability: TC-A-028 and TC-A-029 are now carried by the schedule-send tests, replacing the duplicate rows TC-A-124 and TC-A-125.

## Demo alert trace (debug only, stress demo)

Red commit: ba89bf0 (tests and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*AlertRaiserTest*' --tests '*DemoAlertTraceTest*'`. Exit 1, the test sources do not compile:

```
e: AlertRaiserTest.kt:60:24 Unresolved reference 'AlertObserver'.
e: AlertRaiserTest.kt:68:9 No parameter with name 'observers' found.
e: DemoAlertTraceTest.kt:28:25 Unresolved reference 'DemoAlertTrace'.
```

TC-A-140 to TC-A-145 are red until this step lands. TC-A-140 was then corrected once: the history lists newest first, so the stored order is a-2, a-1.

## Screen images (M0 phone walkthrough)

Red commit: the first commit of the PR (test and catalogue rows only). Command: `./gradlew :phone:testDebugUnitTest --tests '*PhoneScreensTest*'`. Exit 1, the test sources do not compile:

```
e: PhoneScreensTest.kt:36:23 Unresolved reference 'PhoneScreens'.
```

TC-A-146 and TC-A-147 are red until this step lands.
