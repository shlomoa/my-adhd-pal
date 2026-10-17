# Watch lane test-case catalogue (W1a)

Lane W (`:wear`, tile, biometrics). Step ids are from [`../plan.md`](../plan.md). Test names in code embed the case id
(for example `TC-W-001`), and `scripts/check-test-cases.sh watch baseline|final` checks that every id has a test.

**Kinds:** `unit` = JVM test against the fakes from `:protocol`, run by `./gradlew :wear:testDebugUnitTest`;
`CI` = a build or script check in CI; `manual` = run by the user on an emulator or the watch, PASS only when reported
in `docs/work-log/watch.md`. **Pass criterion for the lane:** every case PASS, none skipped.

## Link state and sync (3.2)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-001 | 3.2 | unit | Home screen shows Nearby when the phone node is nearby and the capability is present | state model equals Nearby |
| TC-W-002 | 3.2 | unit | Relayed when the node is reachable but not nearby | state model equals Relayed |
| TC-W-003 | 3.2 | unit | Away when no phone node is connected | state model equals Away |
| TC-W-004 | 3.2 | unit | Incompatible when `/hello/phone` has an unsupported major version | state model equals Incompatible |
| TC-W-005 | 3.2 | unit | Last-sync time is shown and updated after a schedule is applied | formatted time equals the fixed-clock time of the apply |
| TC-W-006 | 3.2 | unit | State refreshes on a capability change and on the periodic re-read | two state changes observed with the fake transport |
| TC-W-007 | 3.2 | unit | `/hello/watch` is published at start with protocol and app version, overwriting the old item | one item, current versions |
| TC-W-008 | 3.2 | manual | Home screen shows the four states and last sync on the watch | user checks Nearby, Relayed (phone Wi-Fi only) and Away (phone off) |

## Schedule receive (3.3)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-010 | 3.3 | unit | A schedule with a newer version replaces the stored one | stored version equals the new one |
| TC-W-011 | 3.3 | unit | A schedule with an equal or older version is ignored | stored schedule unchanged |
| TC-W-012 | 3.3 | unit | An invalid or unsupported-major `/schedule` item is dropped | nothing stored, no crash |
| TC-W-013 | 3.3 | unit | The schedule persists and is read back after a process restart | loaded equals saved |
| TC-W-014 | 3.3 | unit | After the first sync the watch resolves reminders with no transport present | next reminder computed from storage only |
| TC-W-016 | 3.3 | unit | The watch never deletes the phone's `/schedule` item (the writer owns cleanup) | the item is still on both nodes after it is applied |
| TC-W-015 | 3.3 | manual | Schedule sent from the phone arrives, and a later reconnect delivers a newer one | user reports versions on the debug log view |

## Scheduler (3.4)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-020 | 3.4 | unit | The next alarm time is the earliest future reminder (fixed clock) | equals expected instant |
| TC-W-021 | 3.4 | unit | A new schedule cancels stale alarms and sets the new ones | fake alarm manager holds only the new set |
| TC-W-022 | 3.4 | unit | After a boot event the alarms are rebuilt from storage | fake alarm manager holds the expected set |
| TC-W-023 | 3.4 | unit | When exact alarms are not allowed the scheduler still sets an inexact alarm and reports that it is inexact | status says inexact, alarm set with exact false |
| TC-W-024 | 3.4 | unit | A snoozed reminder reschedules to snooze end and persists | alarm at snooze end, stored |
| TC-W-027 | 3.4 | unit | The manifest declares `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` up to API 32 and `RECEIVE_BOOT_COMPLETED`, and registers the alarm and boot receivers | merged-source manifest check |
| TC-W-025 | 3.4 | manual | 24-hour run, screen off, phone away: every reminder fires within 1 minute | user reports a table of scheduled vs fired time, max delta at most 60 s |
| TC-W-026 | 3.4 | manual | Reminders still fire after a watch reboot | user reports the post-reboot reminder fired in time |

## Haptics, notifications and acknowledgements (3.5, 3.6)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-030 | 3.5 | unit | The haptic catalogue defines tick, warning and calm-breath with distinct waveforms | three distinct patterns |
| TC-W-031 | 3.5 | unit | The notification carries the actions Done, Snooze, Start 2 min and Dismiss | four actions, expected labels |
| TC-W-032 | 3.5 | unit | A notification channel is created once, with the expected importance | one channel |
| TC-W-033 | 3.5 | manual | Each haptic is distinguishable on the Galaxy Watch6 Classic | user rates tick, warning and calm-breath |
| TC-W-034 | 3.5 | manual | Reminder notification appears with the screen off and each action works | user reports |
| TC-W-035 | 3.6 | unit | Done and Dismiss send an urgent `/ack/<id>` with action and time | one urgent item per action, unique id |
| TC-W-036 | 3.6 | unit | Snooze is applied locally first, then reported; the local snooze holds when the transport is away | alarm moved before the send; item buffered |
| TC-W-037 | 3.6 | unit | Start 2 min starts a 2-minute timer and reports the action | timer 120 s; item sent |
| TC-W-038 | 3.6 | unit | Acknowledging the same id twice sends one item | one item |

## Selections (3.7)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-040 | 3.7 | unit | `/prompt/<id>` shows the options on the selection screen | options match the prompt |
| TC-W-041 | 3.7 | unit | Choosing an option sends `/selection/<id>` with the key and time, urgent | one item, same id as the prompt |
| TC-W-042 | 3.7 | unit | An expired prompt is not shown and cannot be answered | no screen, no item |
| TC-W-043 | 3.7 | unit | A prompt that expires while shown is closed | screen dismissed at expiry |
| TC-W-044 | 3.7 | unit | An invalid prompt is dropped | nothing shown |
| TC-W-045 | 3.7 | manual | Prompt from the phone appears on the watch, selection arrives in phone history | user reports |

## Manifest and build (3.1, 3.8)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-050 | 3.1 | CI | Notification bridge is `NO_BRIDGING` | merged manifest contains the value |
| TC-W-051 | 3.1 | CI | Vibrate and notification permissions are declared, and `allowBackup` is off | merged manifest check |
| TC-W-052 | 3.1 | unit | Wearable availability check reports unavailable without Google Play services | unavailable state |
| TC-W-053 | 3.1 | CI | `:wear:lintDebug` passes with no new issues | green |
| TC-W-054 | 3.8 | CI | A deliberate debug leak in the release source set fails the release-has-no-debug check | check red |
| TC-W-055 | 3.8 | CI | The real release build passes the release-has-no-debug check | check green |
| TC-W-056 | 3.8 | unit | The debug `Logger` records entries and the injected-message receiver feeds the schedule path | entries and one applied schedule |
| TC-W-057 | 3.8 | CI | The release APK contains no debug log view, injected-message receiver or biometrics logging | no such classes |

## Tile (5.2, 5.3)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-060 | 5.2 | unit | The tile layout shows the current reminder title and time | layout contains both |
| TC-W-061 | 5.2 | unit | The tile shows the primary action (Done) wired to the ack path | click action present |
| TC-W-062 | 5.2 | unit | The tile shows the link state and last sync | state text present |
| TC-W-063 | 5.2 | unit | With no reminder the tile shows an empty state | empty-state text |
| TC-W-064 | 5.3 | unit | A schedule change, an ack and a link-state change each request a tile update | three update requests |
| TC-W-065 | 5.3 | unit | The next reminder time is used as the timeline or freshness boundary | boundary equals next reminder |
| TC-W-066 | 5.2 | manual | The tile is added on the watch and shows current content and the action works | user reports |

## Biometric alerts (6.x)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-070 | 6.5 | unit | A reading above the threshold produces an alert | one alert with metric, value, threshold, time |
| TC-W-071 | 6.5 | unit | A reading at or below the threshold produces none | no alert |
| TC-W-072 | 6.5 | unit | A second breach inside the cooldown is suppressed | one alert in total |
| TC-W-073 | 6.5 | unit | A breach after the cooldown alerts again | two alerts |
| TC-W-074 | 6.5 | unit | A low-side threshold (below limit) alerts when the metric supports it | alert on low breach |
| TC-W-075 | 6.5 | unit | Thresholds and cooldown come from the synced schedule settings | evaluator uses the schedule values |
| TC-W-076 | 6.5 | unit | Out-of-order or batched samples are evaluated by their timestamps | alerts follow the sample time order |
| TC-W-077 | 6.6 | unit | An alert is sent as urgent `/alert/<id>` with a unique id and the numeric value | one item, fields as above |
| TC-W-078 | 6.3 | unit | The permission flow requests the API-appropriate permissions and handles denial | denied state exposed, no registration |
| TC-W-079 | 6.4 | unit | The passive listener forwards data points to the evaluator and registration is redone after boot | fake client receives the config; re-registered |
| TC-W-080 | 6.7 | CI | Raw biometrics code and logging exist only in `wear/src/debug/` | release has none |
| TC-W-081 | 6.7 | unit | No release code path logs raw biometric values | log sink in tests receives none |
| TC-W-084 | 6.5 | unit | The end-episode item from the phone ("I'm OK" on the phone) stops the watch's repeats, closes its alert, and the next breach starts at level 1 | no further alert after the item; the next breach is level 1 (item shape defined by the Shared lane, protocol 1.2) |
| TC-W-085 | 6.6 | unit | "I'm OK" on the watch's own alert ends the episode and stops the repeats | no further alert; the next breach is level 1 |
| TC-W-123 | 6.5 | unit | A reading above baseline + offset starts an episode with a level 1 alert carrying the episode id, the value and the threshold | one alert, level 1, threshold = baseline + offset |
| TC-W-124 | 6.5 | unit | Until a baseline exists the initial value (110 by default) is the threshold; the offset is a setting from 20 to 50 | thresholds follow `initialBpm` and `offsetBpm` |
| TC-W-125 | 6.5 | unit | Inside the 5-minute cooldown no further alert is raised | none |
| TC-W-126 | 6.5 | unit | The first reading above the threshold once the cooldown has elapsed raises a level 2 alert of the same episode | level 2, same episode id |
| TC-W-127 | 6.5 | unit | Level 2 repeats at most once per 2 minutes while the rate stays above | none at 60 s, one at 120 s |
| TC-W-128 | 6.5 | unit | At most 6 level 2 alerts per episode | the 7th is not raised |
| TC-W-129 | 6.5 | unit | A reading at or below the threshold ends the episode; the next breach is a new episode at level 1 | new id, level 1 |
| TC-W-130 | 6.5 | unit | `endEpisode` with the active id ends the episode (the next breach is level 1 of a new episode); an unknown or finished id is ignored | true then false; no effect |
| TC-W-138 | 6.5 | unit | Absolute thresholds in the schedule settings are ignored: only baseline + offset (initial value until a baseline exists) counts | alert threshold is the relative one |
| TC-W-131 | 6.5 | unit | Batched and out-of-order readings are evaluated in timestamp order | same alerts as sorted |
| TC-W-086 | 6.5 | unit | The hourly low of an hour is the lowest median over any 3 consecutive samples of that hour | equals the expected value for a fixed sample series |
| TC-W-087 | 6.5 | unit | An hour with fewer than 3 samples gives no hourly low | no value for that hour |
| TC-W-088 | 6.5 | unit | The baseline is unknown with 23 hourly lows and known with 24 | null, then a value |
| TC-W-089 | 6.5 | unit | The baseline is the 20th percentile (nearest rank) of the hourly lows | equals the expected value |
| TC-W-120 | 6.5 | unit | Only the hourly lows of the last 7 days count; older ones are dropped | an old low does not change the baseline |
| TC-W-121 | 6.5 | unit | Batched and out-of-order samples within one hour give the same hourly low as sorted ones | equal results |
| TC-W-122 | 6.5 | unit | The hourly lows (derived values only) can be saved and restored | restored baseline equals the original |
| TC-W-132 | 6.4 | unit | The probe summary counts batches and samples and reports the min, median and max gap between batches | expected numbers |
| TC-W-133 | 6.4 | unit | An empty or single-batch list has no gaps | no gap values |
| TC-W-134 | 6.4 | unit | The report has one line for all batches and one per screen state, in seconds, with no bpm values | exact lines |
| TC-W-135 | 6.4 | unit | A probe record is one line and reads back; a malformed line is skipped | round trip, null for bad lines |
| TC-W-139 | 6.4 | unit | The probe needs the health permissions on Wear OS 6 and later, the body sensor ones on API 33 to 35 | expected lists |
| TC-W-140 | 6.4 | unit | The permission report names every granted and missing permission | exact lines |
| TC-W-141 | 6.4 | unit | The probe starts only when no permission is missing | empty missing list |
| TC-W-136 | 6.4 | manual | On the real watch: capability, arrival intervals with screen on, off and the watch off the wrist | user reports the three report lines (`docs/test-reports/watch-probe-checklist.md`) |
| TC-W-137 | 6.6 | manual | On the real watch: the strongest vibration under normal, Do Not Disturb, Bedtime, Theater mode and each vibration-intensity setting | user reports felt or not felt per case |
| TC-W-082 | 6.8 | manual | Emulator synthetic heart-rate data triggers an alert that reaches the phone | user reports |
| TC-W-083 | 6.8 | manual | The real watch registers passive heart rate and an alert reaches the phone | user reports capability and result |

## End to end (3.9)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-090 | 3.9 | manual | Paired emulators: schedule, reminder, acknowledgement, phone history | user reports each hop |
| TC-W-091 | 3.9 | manual | Real devices, phone present | same round trip works |
| TC-W-092 | 3.9 | manual | Real devices, phone away: reminder fires locally, ack arrives on reconnect | ack in phone history after reconnect |
| TC-W-093 | 3.9 | manual | Real devices, Bluetooth toggled off and on | link state follows, nothing lost or duplicated |

## Demo (real-device demo path)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-100 | demo | unit | The `DEMO` log lines (schedule applied, reminder fired, ack sent, ack not sent) have the format the demo script reads and hold no reminder text | exact line text |
| TC-W-101 | demo | unit | The debug demo action applies the action to the last fired reminder with its occurrence and snooze count; unknown action or nothing fired does nothing | handler called once with the expected arguments, or not at all |
| TC-W-102 | demo | script | `scripts/demo/run-demo.ps1` against a simulated adb: readiness gate, each stage PASS or FAIL, exit codes, results published | script test passes |
| TC-W-103 | demo | manual | The user runs the demo with the real phone and watch and sees the notification, feels the haptic, and the ack is in the phone history | user reports in the work log |

## Data Layer transport (demo path)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-110 | demo | unit | The cached transport lists nothing before the first load and the loaded items after it | `items()` equals the loaded set |
| TC-W-111 | demo | unit | `put` updates the cache at once and writes asynchronously; a newer item at the same path replaces the older | cache and writer both hold the newer item |
| TC-W-112 | demo | unit | `delete` removes the item from the cache at once and deletes it in the Data Layer | cache empty, one delete written |
| TC-W-113 | demo | unit | An item changed by the other node is cached and reported once; one written by this node is cached already and not reported | one callback for the other node, none for the own |
| TC-W-114 | demo | unit | An item deleted by the other node is removed and reported; a deletion by this node is not reported | one callback for the other node, none for the own |
| TC-W-115 | demo | unit | A load that finishes after a local `put` or `delete` keeps the local change | the local state wins |
| TC-W-116 | demo | unit | The link state is read from the injected observation, and a changed state reaches the listener once | one callback per change |
| TC-W-117 | demo | unit | The `DataMap` mapping round-trips the four keys and rejects an item with a missing or mistyped key | equal envelope; null for a bad item |
| TC-W-119 | demo | unit | Prompt, ack, selection and alert items are written urgent; hello, schedule and unknown paths are not | `isUrgent` per path |
| TC-W-118 | demo | manual | On the real watch the phone's schedule arrives with the app closed and an ack reaches the phone | user reports (part of TC-W-015, TC-W-103) |

## Final (W-final)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-W-099 | W-final | CI | `./gradlew :wear:check` and the docs lint are green on merged `main` | green |
