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

Note (retirement, docs only): TC-W-132 to 136 and TC-W-139 to 143 belong to the passive-probe code (measured, not used for alerts; the live route replaces it). They stay in the catalogue and keep running until lane W removes the code and the tests; the cases are then retired in a later change. TC-W-137 (vibration) and TC-W-138 (threshold) in the same number range are not probe cases and stay.

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
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
| TC-W-138 | 6.5 | unit | The alert settings carry no absolute threshold: only baseline + offset (initial value until a baseline exists) counts | alert threshold is the relative one |
| TC-W-131 | 6.5 | unit | Readings in any order are evaluated in timestamp order | same alerts as sorted |
| TC-W-086 | 6.5 | unit | The hourly low of an hour is the lowest median over any 3 consecutive samples of that hour | equals the expected value for a fixed sample series |
| TC-W-087 | 6.5 | unit | An hour with fewer than 3 samples gives no hourly low | no value for that hour |
| TC-W-088 | 6.5 | unit | The baseline is unknown with 23 hourly lows and known with 24 | null, then a value |
| TC-W-089 | 6.5 | unit | The baseline is the 20th percentile (nearest rank) of the hourly lows | equals the expected value |
| TC-W-120 | 6.5 | unit | Only the hourly lows of the last 7 days count; older ones are dropped | an old low does not change the baseline |
| TC-W-121 | 6.5 | unit | Samples in any order within one hour give the same hourly low as sorted ones | equal results |
| TC-W-122 | 6.5 | unit | The hourly lows (derived values only) can be saved and restored | restored baseline equals the original |
| TC-W-132 | 6.4 | unit | The probe summary counts deliveries and samples and reports the min, median and max gap between deliveries | expected numbers |
| TC-W-133 | 6.4 | unit | An empty or single-delivery list has no gaps | no gap values |
| TC-W-134 | 6.4 | unit | The report has one line for all deliveries and one per display state (on, doze, off), in seconds, with the age of the newest sample and no bpm values | exact lines |
| TC-W-135 | 6.4 | unit | A probe record is one line and reads back; a malformed line is skipped | round trip, null for bad lines |
| TC-W-139 | 6.4 | unit | The probe needs the health permissions on Wear OS 6 and later, the body sensor ones on API 33 to 35 | expected lists |
| TC-W-140 | 6.4 | unit | The permission report names every granted and missing permission | exact lines |
| TC-W-141 | 6.4 | unit | The probe starts only when no permission is missing | empty missing list |
| TC-W-142 | 6.4 | unit | A display state is named on, doze (always-on display), off or other | expected names |
| TC-W-143 | 6.4 | unit | The age of the newest sample and the span of a delivery come from the sample times | expected numbers |
| TC-W-144 | 6.4 | unit | The measure report gives readings, readings per minute and the gap between readings (median, 95th percentile, longest), overall and per screen state | exact lines |
| TC-W-145 | 6.4 | unit | No readings give zero readings and no gaps; no duration gives no rate | exact lines |
| TC-W-146 | 6.4 | unit | Availability changes and readings are one line each and read back; a malformed line is skipped; changes are listed in time order | round trip, null for bad lines, ordered lines |
| TC-W-147 | 6.4 | unit | The battery report gives the drop per hour, and none without a duration or when the level rose | exact lines |
| TC-W-148 | 6.4 | unit | The 95th percentile is the nearest rank of the sorted values | expected numbers |
| TC-W-149 | 6.4 | manual | On the real watch: the live heart-rate route over 30 minutes worn (screen on, dim, off): readings per minute, gaps, availability changes, battery drop, and that the service survives without the background permission | user reports the measure report lines (`docs/test-reports/watch-probe-checklist.md` steps 1 to 4: E1, E3, E4) |
| TC-W-150 | 6.4 | unit | The front line gives the event, the screen state (on, doze, off), whether the debug screen is in front and keep-screen-on | exact lines |
| TC-W-151 | 6.4 | unit | A screen state the probe does not know is named other | exact line |
| TC-W-152 | 6.4 | unit | The debug screen is in front only when it is resumed and has window focus | true and false cases |
| TC-W-153 | 6.4 | script | `scripts/probe/run-e1.ps1` parses | PowerShell parser, no errors |
| TC-W-154 | 6.4 | script | A full E1 run against a simulated adb: log buffer, screen timeout saved and restored, background permissions revoked, heart-rate and notification permissions granted, stop and clear before start, wake, three phases with a report each, battery before and after, notification check, one result file | order of the adb calls, content of the result file |
| TC-W-155 | 6.4 | script | Before every report the script reconnects and clears the log; a connection that drops while the screen sleeps does not stop the run | call order, three reports captured |
| TC-W-156 | 6.4 | script | A service that never reports AVAILABLE fails the run, restores the screen timeout and stops the service | exit code, call log |
| TC-W-157 | 6.4 | script | A watch on its charger is refused (the battery drop would be hidden) unless `-AllowCharging` | exit code, no start |
| TC-W-158 | 6.4 | script | Without `-Unattended` the script asks about the Always-on display before phase B and before phase C | prompt text twice |
| TC-W-159 | 6.4 | script | The script header says what is untested on the watch | text |
| TC-W-160 | 6.4 | script | `scripts/probe/run-e3.ps1` and `run-e6.ps1` parse | PowerShell parser, no errors |
| TC-W-161 | 6.4 | script | A full E3 run against a simulated adb: background permission revoked and never granted, screen timeout untouched, seven reports (30-minute block, Bedtime, battery saver) each after adb connect and logcat -c, service-running and readings-grew lines, battery before and after, one result file | order of the adb calls, content of the result file |
| TC-W-162 | 6.4 | script | A service that disappears or whose readings stop growing is a result in the file (running False, grew False), not a script failure | result file, exit 0 |
| TC-W-163 | 6.4 | script | E3: a service that never reports AVAILABLE fails the run and is stopped; a watch on its charger is refused | exit code, call log |
| TC-W-164 | 6.4 | script | Without `-Unattended` E3 asks the user to turn Bedtime and battery saver on and off | prompt text |
| TC-W-165 | 6.6 | script | A full E6 run: vibrate in six states, both Do Not Disturb routes under Do Not Disturb, Bedtime and Theater, Do Not Disturb set and switched off again, the user's answers and the PROBE lines in one result file | call log, result file |
| TC-W-166 | 6.6 | script | E6 asks the user to set Theater mode and the vibration intensity | prompt text |
| TC-W-167 | 6.4 | script | The E3 and E6 script headers say what is untested on the watch | text |
| TC-W-168 | 6.4 | script | A run script publishes its result file as the branch `e1-results/<stamp>` (E3, E6 the same with their prefix) holding ONLY that file, one commit, and prints `PUSHED <branch>`; HEAD, working tree and index of the user's checkout are untouched | origin refs, tree, checkout state |
| TC-W-169 | 6.4 | script | The published file holds no ip address and no local path | text |
| TC-W-170 | 6.4 | script | `-SkipPush` publishes nothing and keeps the local result file | origin refs, file |
| TC-W-171 | 6.4 | script | A failed push leaves the exit code unchanged, names the branch and prints the retry command; a failed run still publishes its file | output, exit code |
| TC-W-172 | 6.4 | script | E3 and E6 publish `e3-results/<stamp>` and `e6-results/<stamp>` | origin refs |
| TC-W-173 | 6.4 | script | `scripts/probe/run-e4.ps1` parses | PowerShell parser, no errors |
| TC-W-174 | 6.4 | script | A full E4 run against a simulated adb: screen timeout saved, shortened and restored; the front screen started twice (switch off, then `--ez keep true`) with the log cleared before each run; the PROBE front lines and the resumed activity of both runs in one result file | order of the adb calls, content of the result file |
| TC-W-175 | 6.4 | script | The four questions of checklist step 4 are asked and the typed answers are in the result file; the file holds no ip address and no local path | result file, prompt text |
| TC-W-176 | 6.4 | script | The E4 result is published as `e4-results/<stamp>` holding only the file; `-SkipPush` publishes nothing | origin refs |
| TC-W-177 | 6.4 | script | A front screen that cannot be started fails the run, restores the screen timeout and still publishes the file | exit code, call log, origin refs |
| TC-W-178 | 6.4 | script | The E4 script header says what is untested on the watch | text |
| TC-W-179 | 6.4 | script | Every probe test that runs a `run-e*.ps1` runs a copy of the scripts inside a throwaway git repository, never the real checkout (so a test can never publish to the real origin); the shared helper `make_repo` exists | static check of the test scripts |
| TC-W-180 | 6.4 | script | E3: no report is taken while power saving is on (it turns Wi-Fi off); the report after it is taken once the watch is reachable again; the file says no report was possible | call log, result file |
| TC-W-181 | 6.4 | script | E3: a watch that never comes back does not fail the run; the file says no report was possible and the growth is unknown | result file, exit 0 |
| TC-W-182 | 6.4 | script | E3: a watch that is away during Sleep mode: the in-mode report is logged as impossible and taken after the mode is off | result file |
| TC-W-183 | 6.4 | script | The prompts of E1, E3 and E6 carry the verified watch menu paths | prompt text |
| TC-W-184 | 6.4 | unit | A reading with a sample time is written with three fields and read back; a reading without one with two | round trip |
| TC-W-185 | 6.4 | unit | The sample report gives the callbacks and, per screen state, the longest gap between sample times and the delay (arrival minus sample time: median, 95th percentile, longest) | exact lines |
| TC-W-186 | 6.4 | unit | The arrivals and the samples are counted per minute of the run; minutes without any count as zero | exact lines |
| TC-W-187 | 6.4 | unit | Readings without sample times give the callbacks and the arrivals per minute only; no readings give one line | exact lines |
| TC-W-136 | 6.4 | manual | On the real watch: capability, arrival intervals with screen on, off and the watch off the wrist | dropped with the passive probe (retires with C2, #225); no checklist step |
| TC-W-137 | 6.6 | manual | On the real watch: the strongest vibration under normal, Do Not Disturb, Bedtime, Theater mode and each vibration-intensity setting | user reports felt or not felt per case (`docs/test-reports/watch-probe-checklist.md` step 5: E6) |
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
