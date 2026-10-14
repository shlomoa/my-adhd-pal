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

## Not yet written (later steps)

Cases for 3.3 schedule receive (TC-W-010 to TC-W-015), 3.4 scheduler (TC-W-020 to TC-W-026), 3.5 to 3.6 (TC-W-032 to TC-W-038), 3.7
selections (TC-W-040, 041, 044, 045), the tile (TC-W-060 to TC-W-066) and biometrics (TC-W-077 to TC-W-083).
