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

## Not yet written (W1c-b, after G2a)

All other cases that touch the link state, the schedule logic or the transport.
