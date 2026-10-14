# Watch lane baseline (red)

Evidence that the W1c-a tests fail before any implementation exists (part a: cases that need only the G1 contract).
Red commit: `d53e22e` (tests only). Part b (link state, schedule logic, transport cases) waits for G2a.
Cases from earlier steps (TC-W-050 to TC-W-057) were red-first in their own PRs (#79, #80), see `docs/work-log/watch.md`.

## TC-W-070 to TC-W-076: alert evaluator (`protocol/src/test/.../alerts/AlertEvaluatorTest.kt`)

Command: `./gradlew :protocol:test`. Build fails before any test runs.

```
e: protocol/src/test/kotlin/.../alerts/AlertEvaluatorTest.kt:27:9 Unresolved reference 'Reading'.
e: protocol/src/test/kotlin/.../alerts/AlertEvaluatorTest.kt:31:22 Unresolved reference 'AlertEvaluator'.
```

## TC-W-030, TC-W-031: haptic catalogue and reminder actions (`wear/src/test/.../reminder/ReminderUiSpecTest.kt`)

Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../reminder/ReminderUiSpecTest.kt:3:30 Unresolved reference 'protocol'.
e: wear/src/test/.../reminder/ReminderUiSpecTest.kt:12:24 Unresolved reference 'Haptic'.
```

`:wear` does not yet depend on `:protocol`, and `Haptic`, `ReminderAction` do not exist.

## TC-W-042, TC-W-043: prompt expiry (`wear/src/test/.../reminder/PromptExpiryTest.kt`)

Decision (coordinator, 2026-10-14): `Prompt.expiresAt` is an absolute time on the phone clock; the watch never shows a prompt whose
expiry is at or before its own clock and hides a shown one when its clock reaches it. Red commit: `0beced5` (test only).

Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../reminder/PromptExpiryTest.kt:20:36 Unresolved reference 'isExpiredAt' on receiver of type 'Prompt'.
```

## Step 3.2: link client (TC-W-001 to TC-W-007, `wear/src/test/.../link/LinkClientTest.kt`)

Each step after G2a records its red evidence here, in its own PR (the red commit precedes the implementation commit in the PR).
Red commit: `2f97a7c` (test only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../link/LinkClientTest.kt:30:9 Unresolved reference 'LinkClient'.
```

TC-W-008 (home screen on the watch) is manual and waits for the real transport (step 1.5, G2b).

## Step 3.3: schedule receive (TC-W-005, 010 to 014, 016; `wear/src/test/.../schedule/`)

Red commit: `9993415` (tests only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../schedule/FileScheduleStoreTest.kt:32:9 Unresolved reference 'FileScheduleStore'.
e: wear/src/test/.../schedule/FileScheduleStoreTest.kt:32:40 Unresolved reference 'StoredSchedule'.
```

TC-W-015 (schedule from the phone on the watch) is manual and waits for the real transport (1.5, G2b).

## Step 3.4, logic part: reminder scheduler (TC-W-020 to TC-W-024; `wear/src/test/.../scheduler/`)

Red commit: `dceb6f0` (tests only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../scheduler/FakeAlarmPort.kt:6:56 Unresolved reference 'AlarmPort'.
e: wear/src/test/.../scheduler/InMemorySnoozeStore.kt:3:29 Unresolved reference 'SnoozeStore'.
```

TC-W-025 (24-hour run) and TC-W-026 (reboot) are manual and wait for the Android parts of 3.4 (see the work log).

## Step 3.5, Android part: notification channel (TC-W-032; `wear/src/test/.../reminder/ReminderChannelTest.kt`)

Red commit: `095725f` (test only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../reminder/ReminderChannelTest.kt:11:35 Unresolved reference 'ReminderChannel'.
e: wear/src/test/.../reminder/ReminderChannelTest.kt:13:31 Unresolved reference 'channel_reminders'.
```

TC-W-033 and TC-W-034 are manual (the user's checklist is in the work log).

## Step 3.7: prompt inbox and selections (TC-W-040 to TC-W-044; `wear/src/test/.../prompt/PromptInboxTest.kt`)

Red commit: `b62df3f` (test only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../prompt/PromptInboxTest.kt:24:27 Unresolved reference 'PromptInbox'.
```

TC-W-045 (prompt from the phone on the watch) is manual and waits for the real transport (1.5, G2b).

## Step 3.4, Android part: inexact fallback and manifest (TC-W-023 changed, TC-W-027; `wear/src/test/.../scheduler/`, `ManifestTest.kt`)

Decision (coordinator, 2026-10-14): with no exact-alarm permission the scheduler sets an inexact alarm and reports it, instead of
setting nothing. TC-W-023 was rewritten to that behaviour. Red commit: `0dc5da4` (tests only). Command:
`./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../scheduler/FakeAlarmPort.kt:6:1 Class 'FakeAlarmPort' is not abstract and does not implement abstract member:
e: wear/src/test/.../scheduler/ReminderSchedulerTest.kt:74:57 No parameter with name 'exact' found.
```

TC-W-025 (24-hour run) and TC-W-026 (reboot) are manual (checklist in the work log).

## Step 3.6: acknowledgements (TC-W-035 to TC-W-038, TC-W-020 occurrence; `wear/src/test/.../reminder/ReminderActionHandlerTest.kt`)

Protocol 1.1 (PR #105) added `reminderId` and `occurrenceAt` to `Acknowledgement`. Red commit: `3cc8a13` (tests only).
Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../reminder/FakeTimerPort.kt:3:23 Unresolved reference 'TimerPort'.
e: wear/src/test/.../reminder/ReminderActionHandlerTest.kt:40:9 Unresolved reference 'ReminderActionHandler'.
e: wear/src/test/.../reminder/ReminderActionHandlerTest.kt:66:38 Too many arguments for 'fun snooze(reminderId: String, snoozeCount: Int): SnoozeResult'.
```

## Not yet written (later steps)

Cases for the tile (TC-W-060 to TC-W-066) and biometrics (TC-W-077 to TC-W-083).
