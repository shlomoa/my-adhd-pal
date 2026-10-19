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

## Step 5.2: tile (TC-W-060 to TC-W-063; `wear/src/test/.../tile/TileModelTest.kt`)

The tile libraries were added first and compile under AGP 9.4 and Kotlin 2.4 (the stop-and-ask check from the 5.1 research).
The layout needs an Android `Context` and cannot run in a JVM unit test, so the tile is split: `tileModel(...)` (tested here) decides
what is shown and the service only draws it (TC-W-066, manual). Red commit: `75ff287` (test only). Command:
`./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../tile/TileModelTest.kt:29:21 Unresolved reference 'tileModel'.
```

## Step 5.3: tile update triggers (TC-W-064, TC-W-065; `wear/src/test/.../tile/TileUpdateTest.kt`)

Red commit: `fde0a8a` (test only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../tile/TileUpdateTest.kt:32:28 Unresolved reference 'TileUpdateTriggers'.
e: wear/src/test/.../tile/TileUpdateTest.kt:47:9 No parameter with name 'onHandled' found.
e: wear/src/test/.../tile/TileUpdateTest.kt:84:46 Unresolved reference 'tileFreshness'.
```

## Demo hooks (TC-W-100, TC-W-101; `wear/src/test/.../demo/DemoLogTest.kt`, `wear/src/testDebug/.../debug/DemoActionTest.kt`)

Red commit: `0261b13` (tests only). Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/test/.../demo/DemoLogTest.kt:30:9 Unresolved reference 'DemoLog'.
e: wear/src/testDebug/.../debug/DemoActionTest.kt:22:23 Unresolved reference 'performDemoAction'.
```

## Data Layer transport (TC-W-110 to TC-W-117; `wear/src/test/.../datalayer/`)

Red commit: `03b5d22` (tests only). Command: `./gradlew :wear:testDebugUnitTest`.
Build fails before any test runs.

```
e: wear/src/test/.../datalayer/CachedTransportTest.kt:12:28 Unresolved reference 'ItemWriter'.
e: wear/src/test/.../datalayer/CachedTransportTest.kt:43:29 Unresolved reference 'CachedTransport'.
```

## Demo script (TC-W-102; `scripts/demo/test-run-demo.sh`)

The script and its simulated `adb` were written together, because each defines the other's contract, so there is no separate red
commit. Evidence that the test can fail instead: with the late-reminder rule broken on purpose
(`if ($lateMs -gt 99999999)`) the test reported `FAIL TC-W-102 late reminder: exit 1 (expected 1, got 0)` and
`FAIL TC-W-102 late reminder: Reminder FAIL (expected FAIL, got PASS)`; with the real rule it reports no failure (48 checks, including a phone that has nothing new to publish or cannot publish).

### Phone log prefix (Android PR #141 contract check)

The phone app logs `DEMO published ...` (the tag is repeated in the message), the watch app does not. The simulated adb now
prints the phone lines that way, as on the devices. The script before the fix: 18 of 48 checks FAIL (`./scripts/demo/test-run-demo.sh`,
the parser did not strip the prefix); after the fix 48 PASS, 0 FAIL.

### `-WaitForTap` (TC-W-102)

Tests and simulated-adb tap injection (`FAKE_TAP=ACTION:MS`) written first. Against the script without the switch: 12 checks FAIL
(the parameter does not exist, exit 1). After: 61 PASS, 0 FAIL (tap Done, tap Snooze, no tap, and the unchanged default mode).

## Episode evaluator (TC-W-123 to TC-W-131; `protocol/src/test/.../alerts/EpisodeEvaluatorTest.kt`)

Step 6.5 second part. Red commit: the first commit of branch `claude/watch-6-5b` (tests only). Command: `./gradlew :protocol:test`.
Build fails before any test runs.

```
e: protocol/src/test/.../alerts/EpisodeEvaluatorTest.kt:28:9 Unresolved reference 'EpisodeEvaluator'.
```

## Resting baseline (TC-W-086 to TC-W-089, TC-W-120 to TC-W-122; `protocol/src/test/.../alerts/BaselineTrackerTest.kt`)

Step 6.5 first part. Red commit: the first commit of branch `claude/watch-6-5` (tests only). Command: `./gradlew :protocol:test`.
Build fails before any test runs.

```
e: protocol/src/test/.../alerts/BaselineTrackerTest.kt:27:23 Unresolved reference 'BaselineTracker'.
e: protocol/src/test/.../alerts/BaselineTrackerTest.kt:30:29 Unresolved reference 'HourlyLow'.
```

## Heart-rate probe statistics (TC-W-132 to TC-W-135; `wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt`)

Step 6.4 probe. Red commit: the first commit of branch `claude/watch-6-4-probe` (tests only). Command: `./gradlew :wear:testDebugUnitTest`.
Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:15:23 Unresolved reference to the probe delivery type.
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:16:23 Unresolved reference 'summarize'.
```

## Probe permissions (TC-W-139 to TC-W-141; `wear/src/testDebug/.../debug/probe/ProbePermissionsTest.kt`)

Found on the real watch (Wear OS 6): `PROBE start failed: SecurityException: Missing permissions: [android.permission.health.READ_HEART_RATE]`;
the checklist only granted the body sensor permissions. Red commit: the first commit of branch `claude/watch-6-4-permissions` (tests only).
Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/ProbePermissionsTest.kt:20:20 Unresolved reference 'requiredPermissions'.
```

## Probe display state and sample age (TC-W-134, 135, 142, 143; `ProbeStatsTest.kt`)

First real-watch run (user, Wear OS 6): one delivery of 318 samples arrived 7 min 41 s after start and was logged `screenOn=false` while the user
said the screen stayed lit on the wrist: `isInteractive` is false in the always-on (ambient) display, so the probe now records the display state
and the age of the newest sample. Red commit: the first commit of branch `claude/watch-6-4-probe-display` (tests only).
Command: `./gradlew :wear:testDebugUnitTest`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:10:9 Too many arguments for the probe delivery constructor.
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:73:16 Unresolved reference 'displayName'.
```

## Live heart-rate probe statistics (TC-W-144 to TC-W-148; `wear/src/testDebug/.../debug/probe/MeasureStatsTest.kt`)

D9 (heart rate measured only while the app runs, `docs/plans/biometrics.md` section 11): the probe for the live `MeasureClient` route counts readings, gaps,
availability changes and battery drop. Red commit: the first commit of branch `claude/watch-measure-probe` (tests only).
Command: `./gradlew :wear:testDebugUnitTest --tests '*MeasureStatsTest*'`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/MeasureStatsTest.kt:9:58 Unresolved reference 'MeasureReading'.
e: wear/src/testDebug/.../debug/probe/MeasureStatsTest.kt:29:13 Unresolved reference 'measureReport'.
```

## Probe and evaluator wording: the old delivery term removed (TC-W-121, 131 to 135, 143; `ProbeStatsTest.kt`, `BaselineTrackerTest.kt`, `EpisodeEvaluatorTest.kt`)

User order (2026-10-17): the design no longer rests on delivery in groups. The probe type, parser, log method, report text and the test names say
"delivery"; the protocol tests say "in any order". Red commit: the first commit of branch `claude/watch-measure-probe` after the merge of main
(tests renamed, source not yet).
Command: `./gradlew :wear:testDebugUnitTest --tests '*ProbeStatsTest*'`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:10:9 Unresolved reference 'ProbeDelivery'.
e: wear/src/testDebug/.../debug/probe/ProbeStatsTest.kt:15:33 Unresolved reference 'deliveries' on receiver of type 'ProbeSummary'.
```

## E4 debug front screen (TC-W-150 to TC-W-152; `wear/src/testDebug/.../debug/probe/FrontScreenStatsTest.kt`)

Experiment E4 (issue #231): the debug probe had no screen that stays in front, so keep-screen-on and the app staying the top window could not be tested.
Red commit: the first commit of branch `claude/watch-e4-front-screen` (tests only).
Command: `./gradlew :wear:testDebugUnitTest --tests '*FrontScreenStatsTest*'`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/FrontScreenStatsTest.kt:12:13 Unresolved reference 'frontLine'.
e: wear/src/testDebug/.../debug/probe/FrontScreenStatsTest.kt:34:28 Unresolved reference 'isInFront'.
```

## E1 run script (TC-W-153 to TC-W-159; `scripts/probe/test-run-e1.sh`)

Experiment E1 (issue #228): the user's manual E1 was slow and error-prone (wrong steps, the log buffer rolled over within minutes), so `scripts/probe/run-e1.ps1`
runs it unattended. Static and simulated-adb tests (`scripts/probe/fake/adb`) prove the order of the steps, the restore of the screen timeout, the reconnects and the
result file; they do not prove the watch's behaviour. Red commit: the first commit of branch `claude/watch-e1-script` (tests only).
Command: `PWSH=pwsh scripts/probe/test-run-e1.sh` (PowerShell 7). 32 checks FAIL before the script exists, for example:

```
FAIL TC-W-153 run-e1.ps1 parses without errors (expected 0, got 1)
FAIL TC-W-154 a full run exits 0 (expected 0, got 64)
FAIL TC-W-154 one result file e1-<time>.txt is written (expected 0, got 1)
```

## E3 and E6 run scripts (TC-W-160 to TC-W-167; `scripts/probe/test-run-e3-e6.sh`)

Experiments E3 (issue #230) and E6 (issue #233) in the same way as E1: `scripts/probe/run-e3.ps1` and `run-e6.ps1`, with the shared helpers moved to `scripts/probe/probe-common.ps1`
(the E1 tests TC-W-153 to 159 stay green). Red commit: the first commit of branch `claude/watch-e3-e6-script` (tests and the extended simulated adb only).
Command: `PWSH=pwsh scripts/probe/test-run-e3-e6.sh`. 24 checks FAIL before the scripts exist, for example:

```
FAIL TC-W-160 run-e3.ps1 parses without errors (expected 0, got 1)
FAIL TC-W-161 a full E3 run exits 0 (expected 0, got 64)
FAIL TC-W-165 a full E6 run exits 0 (expected 0, got 64)
```

## Publishing the probe result files (TC-W-168 to TC-W-172; `scripts/probe/test-publish.sh`)

The user does not paste results, so `run-e1.ps1`, `run-e3.ps1` and `run-e6.ps1` publish their result file as the branch `<prefix>-results/<stamp>` (one file, one commit, built with
git plumbing and a temporary index; the checkout is untouched), through `Publish-Result` in `scripts/probe/probe-common.ps1`. Red commit: the first commit of branch
`claude/watch-e1-publish` (tests only). Command: `PWSH=pwsh scripts/probe/test-publish.sh` (simulated adb, throwaway git repository with a bare origin). 10 checks FAIL before publishing
exists, for example:

```
FAIL TC-W-168 branch e1-results/<stamp> is on origin (expected 0, got 1)
FAIL TC-W-168 the script prints PUSHED <branch> (expected 0, got 1)
FAIL TC-W-172 E3 publishes e3-results/<stamp> (expected 0, got 1)
```

## E4 run script (TC-W-173 to TC-W-178; `scripts/probe/test-run-e4.sh`)

Experiment E4 (issue #231) in the same way as E1, E3 and E6: `scripts/probe/run-e4.ps1` uses `probe-common.ps1` and publishes `e4-results/<stamp>`. Red commit: the first commit of branch
`claude/watch-e4-script` (tests and the extended simulated adb only). Command: `PWSH=pwsh scripts/probe/test-run-e4.sh`. 19 checks FAIL before the script exists, for example:

```
FAIL TC-W-173 run-e4.ps1 parses without errors (expected 0, got 1)
FAIL TC-W-174 a full E4 run exits 0 (expected 0, got 64)
FAIL TC-W-176 the result is published as e4-results/<stamp> (expected 0, got 1)
```

## Probe test harness guard (TC-W-179; `scripts/probe/test-harness-guard.sh`)

Since #244 the probe run scripts publish their result file by default, and `test-run-e1.sh` and `test-run-e3-e6.sh` ran them inside the real checkout, so simulated results were pushed to the real
origin (39 branches `e1-`, `e3-`, `e6-results/` on 2026-10-17, all simulated, listed in the body of issue #228). The tests now run a copy of the scripts in a throwaway repository with an
unreachable origin (`scripts/probe/test-lib.sh`, `make_repo`) and pass `-SkipPush`; the guard test fails when a test runs a script outside such a repository. Red commit: the first commit of branch
`claude/watch-test-harness` (guard test only). Command: `scripts/probe/test-harness-guard.sh`. Fails before the fix:

```
FAIL TC-W-179 every test that runs a run script runs it inside a throwaway repository (expected 0, got 1)
runs outside a throwaway repository: test-run-e1.sh, test-run-e3-e6.sh
FAIL TC-W-179 the shared helper make_repo exists (expected 0, got 2)
```

After the fix the three suites pass (38, 32 and 17 checks) and the number of `e*-results/` branches on the real origin is unchanged (39 before and after).

## E3 without reports during power saving, verified watch paths (TC-W-180 to TC-W-183; `scripts/probe/test-run-e3-e6.sh`)

Power saving turns Wi-Fi off (coordinator, Samsung support pages), so no report is possible over wireless adb during it. `run-e3.ps1` takes a report before, none during, and one after the user turns it off, reconnecting for up to
`-ReconnectSec` (default 120); a watch that cannot be reached is logged ("no report possible"), not a failure. The prompts of E1, E3 and E6 carry the verified menu paths (`$PathSleep` and the others in `probe-common.ps1`).
Red commit: the first commit of branch `claude/watch-e3-wifi` (tests and the simulated adb with a "watch goes away" mode). Command: `PWSH=pwsh scripts/probe/test-run-e3-e6.sh`. Before the change the E3 and E6 runs failed the new checks, for example:

```
FAIL TC-W-180 a watch that comes back after power saving: exit 0
FAIL TC-W-181 a watch that never comes back does not fail the run (exit 0)
FAIL TC-W-183 the E3 prompts carry the verified path: Quick panel
```

The parse check now covers `probe-common.ps1` too (a parse error there failed every run script once during this change).

## Sample times in the live probe (TC-W-184 to TC-W-187; `MeasureStatsTest.kt`)

First real E1 run (issue #228, `e1-results/20261018-003737`): the probe records only the arrival time of each reading, so lost readings cannot be told from late ones. The probe now records the sample time
(`SampleDataPoint.timeDurationFromBoot`) next to the arrival time and `measure-report` adds the callbacks, the gap between sample times and the delay per screen state, and the arrivals and samples per minute.
TC-W-146 changed: a line `5000|on|1` was a bad line before and is a valid reading with a sample time now (the bad lines are `5000|on|x` and `5000|on|1|2`).
Red commit: the first commit of branch `claude/watch-sample-times` (tests only). Command: `./gradlew :wear:testDebugUnitTest --tests '*MeasureStatsTest*'`. Build fails before any test runs.

```
e: wear/src/testDebug/.../debug/probe/MeasureStatsTest.kt:96:52 Too many arguments for 'constructor(elapsedMs: Long, display: String): MeasureReading'.
e: wear/src/testDebug/.../debug/probe/MeasureStatsTest.kt:125:13 Unresolved reference 'sampleReport'.
```

Added after the audit of the recording (issue #228, TC-W-188 to TC-W-191): the service writes a live line every minute as it happens (`liveMinuteLine`, `minuteOfRun`), the run scripts copy those lines into the result file before they clear the log,
the E1 file shows the permission state, and `run-e1.ps1 -GrantBackgroundPermission` grants the background permission instead of revoking it (to compare a run with and without it). Red commit: the "Tests for the live per-minute log" commit of the same branch.

## Controlled-screen run script (TC-W-192 to TC-W-197; `scripts/probe/test-run-screen.sh`)

The two E1 runs could not control the screen state (issue #228): the screen timeout did not hold the screen on, and the sleep key did not turn it off in run 2. `scripts/probe/run-screen.ps1` runs 10 minutes with the screen kept on
(`ProbeFrontActivity` with keep-screen-on) and 10 minutes with the screen really off (the Always On Display switched off by adb when a candidate key exists, the sleep key repeated until `dumpsys power` says OFF), with only `-Watch` as argument,
and publishes `screen-results/<stamp>`. Red commit: the first commit of branch `claude/watch-run-screen` (tests and the extended simulated adb only). Command: `PWSH=pwsh scripts/probe/test-run-screen.sh`. 25 checks FAIL before the script exists, for example:

```
FAIL TC-W-192 run-screen.ps1 parses without errors (expected 0, got 1)
FAIL TC-W-193 a full run with only -Watch exits 0 (expected 0, got 64)
FAIL TC-W-196 the result is published as screen-results/<stamp> (expected 0, got 1)
```

## Rediscovering the watch after a port change (TC-W-198 to TC-W-201; `scripts/probe/test-run-e3-e6.sh`)

The E3 run lost adb when Sleep mode switched Wi-Fi off and did not get it back 4 minutes after the mode was off; a changed wireless-debugging port is the suspected cause. The simulated adb gets `FAKE_FAIL=portchange` (the old address never answers again,
`adb mdns services` lists the new port 42000, every call after the change needs the new address) and `nomdns`. Red commit: the first commit of branch `claude/watch-adb-rediscover` (tests and simulation only). Command: `PWSH=pwsh scripts/probe/test-run-e3-e6.sh`. 6 checks FAIL before the change, for example:

```
FAIL TC-W-198 the result file says the address changed (ports only) (expected 0, got 1)
FAIL TC-W-199 the question for the new address is asked once (expected 0, got 1)
FAIL TC-W-200 asked once, the invalid answer is logged and the report is marked impossible (expected 0, got 1)
FAIL TC-W-201 the end of the run reconnects: the service is stopped and the battery after is read (expected 0, got 1)
```

Added after the user's `adb devices` showed the watch twice (new ip:port and the mDNS name `adb-<serial>-<id>._adb-tls-connect._tcp`, which does not change with the port): TC-W-202 to TC-W-205 (mDNS name as `-Watch` and as the way back, keep-alive `adb connect` during waits, the unreachable-seconds counter).
Red commit: "Tests for the mDNS name, keep-alive and the unreachable counter (red)" on the same branch. 5 checks FAIL before the change:

```
FAIL TC-W-202 the change is logged and the report is taken (expected 0, got 1)
FAIL TC-W-203 adb connect is not run for an mDNS name (expected 0, got 1)
FAIL TC-W-204 a wait of several seconds runs adb connect periodically (PROBE_KEEPALIVE_SEC) (expected 0, got 1)
FAIL TC-W-205 the result file counts the seconds the watch was unreachable (expected 0, got 1)
FAIL TC-W-205 a run with a stable connection says 0 s (expected 0, got 1)
```

## Self-controlling probe runs (TC-W-206 to TC-W-212; `scripts/probe/test-self-control.sh`)

Unattended E3 and E4 runs stalled on questions nobody saw and on a dead address. A question gets a time limit, a banner and a beep and never blocks for ever; unanswered or unverified steps are marked UNCONFIRMED in the file and a summary;
adb verifies after Enter where it can; a time limit per wait and per run ends the run, and Ctrl+C included publishes what exists; a failed report never aborts the run. Red commit: the first commit of branch `claude/watch-self-control`
(tests, the `reportfail` mode of the simulated adb and the catalogue rows). Command: `PWSH=pwsh scripts/probe/test-self-control.sh`. 13 checks FAIL before the change, for example:

```
FAIL TC-W-206 every question that timed out is logged 'no answer, continued' (expected 0, got 1)
FAIL TC-W-207 the file and the summary mark the steps UNCONFIRMED (expected 0, got 1)
FAIL TC-W-209 a run over its time limit ends, stops the service and publishes what exists (expected 0, got 1)
FAIL TC-W-211 Ctrl+C ends the run, stops the service and publishes what exists (expected 0, got 1)
```

## Pre-flight and heartbeat of the probe runs (TC-W-213 to TC-W-224; `scripts/probe/test-preflight.sh`)

A long run must not start when the watch cannot finish it, and the coordinator must see from far away where a run is. The pre-flight (watch reachable, not charging, battery at least 40 %, debug build installed, adb stable for 30 s, `git push` possible by dry run, log buffer set)
fails fast with ONE message; the heartbeat pushes a tiny status file to `probe-status/<script>-<run-id>` every 5 minutes. Red commit: the first commit of branch `claude/watch-preflight-heartbeat` (tests, simulated adb modes `dead`, `flaky`, `nodebug`, `FAKE_BATTERY`, catalogue rows).
Command: `PWSH=pwsh scripts/probe/test-preflight.sh`. Most checks FAIL before the change, for example:

```
FAIL TC-W-213 the file says the pre-flight was ok (expected 0, got 1)
FAIL TC-W-214 an unreachable watch: ONE message, exit 3 (expected 0, got 1)
FAIL TC-W-220 several heartbeats are pushed during the run (expected 0, got 1)
FAIL TC-W-221 the final status holds state=finished (expected 0, got 1)
```

Hard time limit on every adb and git call (TC-W-225, TC-W-226; `scripts/probe/test-timeouts.sh`): a hung adb.exe stopped the E3 run silently for an hour on 2026-10-18. Red commit: "Tests for the hard time limit on adb and git calls (red)" on the same branch
(simulated adb `FAKE_FAIL=hang`, a git wrapper that hangs on push). Command: `PWSH=pwsh scripts/probe/test-timeouts.sh`. 6 checks FAIL before the change, for example:

```
FAIL TC-W-225 the hung adb call is logged (expected 0, got 1)
FAIL TC-W-225 the result is published (expected 0, got 1)
FAIL TC-W-226 a hung git push ends and names the local result file (expected 0, got 1)
```

Shorter default durations (TC-W-227; `scripts/probe/test-defaults.sh`): the user's budget of 2026-10-18 is at most 10 minutes of attended watch time per script and 60 minutes for all. Red commit: "Test for the default durations (red)" on branch `claude/watch-preflight-heartbeat`.
Command: `PWSH=pwsh scripts/probe/test-defaults.sh`. 8 checks FAIL before the change, for example:

```
FAIL TC-W-227 E1 phase default is 120 s (expected 120, got 600)
FAIL TC-W-227 E4 run default is 60 s (expected 60, got 180)
```

E4 and E6 decide from adb alone (TC-W-228 to TC-W-230; `scripts/probe/test-adb-answers.sh`): the user types nothing; E4 samples the screen state and the window focus, E6 judges each vibration and alarm route from `dumpsys vibrator_manager` (new entries), the screen state and the window focus; only the physical actions are asked.
Red commit: "Tests for adb-only answers in E4 and E6 (red)" on branch `claude/watch-adb-answers` (tests, simulated `dumpsys vibrator_manager` and `dumpsys window`, `novibrator`). Command: `PWSH=pwsh scripts/probe/test-adb-answers.sh`. 13 checks FAIL before the change, for example:

```
FAIL TC-W-228 E4 asks nothing (expected 0, got 1)
FAIL TC-W-229 only the 7 physical actions are asked (Sleep, Theater, 3 vibration strengths) (expected 0, got 1)
FAIL TC-W-230 a vibrator service that lists nothing is reported as no (expected 0, got 1)
```

## Stress monitor, M1 (TC-W-231 to TC-W-244, with TC-W-077, 081, 084, 085; `wear/.../monitor/StressMonitorTest.kt`, `MonitorHubTest.kt`, `ManifestTest.kt`, `debug/monitor/InjectSeriesTest.kt`)

The user's definition (2026-10-17): heart rate is measured continuously, compared with the baseline continuously, a stress event is reported at once and the local response is activated. The watch side of milestone M1 (issue #258): a health foreground service feeds every reading to the evaluator, the local response (vibration, visible state) and the urgent alert to the phone, and a debug injector feeds synthetic readings into the same code path.
Red commit: the first commit of branch `claude/watch-stress-monitor` (tests only). Command: `./gradlew :wear:testDebugUnitTest`. The build fails before any test runs:

```
e: wear/src/test/.../monitor/MonitorHubTest.kt:26:38 Unresolved reference 'AlertResponder'.
e: wear/src/test/.../monitor/MonitorHubTest.kt:38:23 Unresolved reference 'MonitorHub'.
e: wear/src/test/.../monitor/MonitorHubTest.kt:39:19 Unresolved reference 'StressMonitor'.
```

Stress demo script (TC-W-245 to TC-W-252; `scripts/demo/test-stress-demo.sh`): `run-stress-demo.ps1` runs the stages preflight, grants, monitoring, baseline, reading, event, response, watch-alert, phone-alert and episode-end with one command and no prompts, as a PASS/FAIL timeline, and publishes `demo-results/<stamp>`.
Red evidence: the tests were run with the script absent (commit "Tests for the stress demo script (red)" on branch `claude/watch-stress-monitor`): 22 checks FAIL, for example:

```
FAIL TC-W-245 run-stress-demo.ps1 parses without errors (expected 0, got 1)
FAIL TC-W-246 a full demo run exits 0 (expected 0, got 64)
FAIL TC-W-246 stage preflight is PASS (expected 0, got 2)
```

## Not yet written (later steps)

Cases for biometrics (TC-W-077 to TC-W-083; TC-W-070 to TC-W-076 are done).
