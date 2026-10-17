# Android (phone app) test plan: lane A

Step A1a (docs only). Case ids are `TC-A-nnn`; test names embed the id. Checked by
`scripts/check-test-cases.sh android baseline|final`. Unit = ViewModel or JVM test against the data-layer fakes
(`WatchLinkDataSource`, repositories). CI = `./gradlew :phone:check` (Spotless, detekt, lint, build). Manual = run by the
user on the Galaxy A55 5G with the Galaxy Watch6 Classic and reported in `docs/work-log/android.md`; a manual case is
PASS only when reported.

Pass criterion: every case PASS, zero skipped (`docs/test-reports/android-final.md`).

## Link-state screen (2.6)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-001 | State Nearby is shown | Unit | UI state maps Nearby to its label and icon |
| TC-A-002 | State Relayed is shown | Unit | UI state maps Relayed to its label |
| TC-A-003 | State Away is shown | Unit | UI state maps Away to its label |
| TC-A-004 | State No watch app is shown | Unit | UI state maps No watch app to its label |
| TC-A-005 | State Incompatible is shown | Unit | UI state maps Incompatible to its label |
| TC-A-006 | Last sync time shown, or "never" when absent | Unit | Formatted time with a fixed clock; "never" when null |
| TC-A-007 | Screen has no controls | Unit + manual | UI state exposes no actions; screen has no buttons or switches |
| TC-A-008 | State updates when the link state changes | Unit | Flow from the fake emits a new state; UI state follows |
| TC-A-009 | Screen on the real pair shows Nearby, then Relayed and Away | Manual | Matches the device situation (Bluetooth on, off, watch away) |

## Dependency injection (2.7)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-010 | DI sample resolves a ViewModel with fake data sources | Unit | ViewModel built through the DI graph with fakes |
| TC-A-011 | DI graph builds in CI with the chosen mechanism (0b.1) | CI | `:phone:assembleDebug` and `:phone:testDebugUnitTest` green |
| TC-A-012 | Production graph binds the real data sources | Unit | Graph lists real bindings; no fake in main sources |

## Tasks and reminders (4.1)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-013 | Create a task | Unit | Valid title is saved through the repository |
| TC-A-014 | Edit a task | Unit | Changed fields are saved; id is unchanged |
| TC-A-015 | Task with a blank title is rejected | Unit | Validation error; nothing saved |
| TC-A-016 | Task title over the length limit is rejected | Unit | Validation error at the limit plus one |
| TC-A-017 | Delete a task | Unit | Repository delete called; list updates |
| TC-A-018 | Create a reminder | Unit | Valid time and task saved |
| TC-A-019 | Edit a reminder | Unit | Changed fields saved |
| TC-A-020 | Reminder without a time is rejected | Unit | Validation error; nothing saved |
| TC-A-021 | Reminder in a malformed repeat pattern is rejected | Unit | Validation error |
| TC-A-022 | Delete a reminder | Unit | Repository delete called |
| TC-A-023 | Unsaved edit is kept across rotation | Unit | UI state survives recreation (saved state) |

## Schedule builder (4.2)

TC-A-024..027 retired, covered by TC-S-092 and TC-S-164.

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-028 | Publish failure is reported and not counted as sent | Unit | Error state; version unchanged |
| TC-A-029 | Publishing an unchanged schedule is a no-op | Unit | No new item; user is told nothing changed |
| TC-A-030 | Schedule round trip to the watch | Manual | Reminder created on the phone fires on the watch |

## Settings (4.3)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-031 | Defaults shown on first run | Unit | Default cooldown, cadence and alert offset |
| TC-A-032 | Change the alert offset | Unit | Stored through the settings repository |
| TC-A-033 | Offset out of range is rejected | Unit | Validation error; old value kept |
| TC-A-034 | Change the alert cooldown | Unit | Stored |
| TC-A-035 | Cooldown out of range is rejected | Unit | Validation error |
| TC-A-036 | Change the reminder cadence | Unit | Stored |
| TC-A-037 | Changing settings triggers a new schedule version | Unit | Publish requested with the new values |
| TC-A-038 | Settings persist across app restart | Manual | Values unchanged after force-stop |

## Prompt publishing (4.4)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-039 | Publish a prompt with a title and options | Unit | Fake receives `/prompt/<id>` with the options |
| TC-A-040 | Prompt needs a title and at least two options | Unit | Validation error otherwise |
| TC-A-041 | Option count over the limit is rejected | Unit | Validation error |
| TC-A-042 | Optional expiry is carried | Unit | Expiry in the published model; none when unset |
| TC-A-043 | Each prompt gets a unique id | Unit | Two publishes give two ids |
| TC-A-044 | Selection on the watch appears in history | Manual | Chosen key and time shown after the watch answers |

## History (4.5)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-045 | Acknowledgements are listed | Unit | Action and time shown |
| TC-A-046 | Selections are listed | Unit | Prompt title, chosen key and time shown |
| TC-A-047 | Alerts are listed with metric, value, threshold | Unit | All fields shown |
| TC-A-048 | Entries are newest first | Unit | Sorted by time descending |
| TC-A-049 | Filter by type | Unit | Only the chosen type is listed |
| TC-A-050 | New item from the watch appears without reload | Unit | Repository flow emission updates the list |
| TC-A-051 | Snooze is recorded as an acknowledgement | Unit | Snooze entry present |
| TC-A-052 | History is stored while the app is closed | Manual | Entries made with the app closed appear on open |

## Empty and error states, accessibility (4.6)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-053 | Empty task list shows guidance | Unit | Empty state with a create hint |
| TC-A-054 | Empty history shows guidance | Unit | Empty state message |
| TC-A-055 | Nothing scheduled yet shows guidance: a task without reminders, and a schedule never sent | Unit (Compose) | "No reminders yet" with "Add a reminder"; "Not sent to the watch yet" with the send button |
| TC-A-056 | Data-source error shows a message and retry | Unit | Error state; retry reloads |
| TC-A-057 | Loading state is shown while data loads | Unit | Loading state precedes content |
| TC-A-058 | Interactive elements have content descriptions or labels | Unit (Compose UI test) | Every actionable node has a label |
| TC-A-059 | Touch targets are at least 48 dp | Unit (Compose UI test) | Size assertion on actionable nodes |
| TC-A-060 | Screen works with large font and TalkBack | Manual | No clipped text; elements announced in order |
| TC-A-061 | Contrast and dark theme are legible | Manual | Checked in light and dark theme |

## Release (7.1-7.6)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-062 | `assembleRelease` succeeds | CI | Signed release build produced |
| TC-A-063 | Release APK contains no debug code | CI | Release-has-no-debug check green |
| TC-A-064 | Phone and watch share `applicationId` and signing | CI | Pairing check green |
| TC-A-065 | `versionCode` follows the documented scheme | CI | Check against the scheme |
| TC-A-066 | `allowBackup` is off | CI | Manifest check |
| TC-A-067 | Privacy policy and health-data declarations exist | Docs | Present, reviewed, requirements researched |
| TC-A-068 | README covers both devices | Docs | Reviewed; markdownlint and link check green |
| TC-A-069 | Release checklist reviewed | Docs + user | User signs off |

## Demo schedule publisher (debug only)

Priority item for the real-life demo: a debug-build way to publish a schedule from the phone without UI. The publisher
logic is pure and tested here; the broadcast receiver `PUBLISH_DEMO_SCHEDULE` that calls it is wired once the real
repositories exist (G3).

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-073 | A reminder is set the requested minutes ahead | Unit | Schedule sent with one reminder at now + N minutes on that weekday (default 2) |
| TC-A-074 | Crossing midnight uses the next weekday | Unit | 23:59 + 2 minutes gives 00:01 on the next day, Sunday wraps to Monday |
| TC-A-075 | Title and minutes come from the extras | Unit | The custom title and offset appear in the sent schedule |
| TC-A-076 | Repeating does not pile up reminders | Unit | Two publishes at different offsets leave exactly one demo reminder; the same offset sends nothing new |
| TC-A-077 | A failed publish is returned, not hidden | Unit | `PublishOutcome.Failed` with the reason |
| TC-A-078 | An offset outside 1 to 1440 minutes is rejected | Unit | `IllegalArgumentException`, nothing stored or sent |
| TC-A-079 | The receiver is absent from release builds | CI | `checkNoDebugInRelease` green (class lives in a `debug` package) |
| TC-A-089 | A task can be added, shown and deleted in the app | Unit (Hilt + Compose, fake Data Layer client) | Tasks tab empty message; Add a task, type, Save: the task is listed; Delete removes it |
| TC-A-090 | The level 1 plan vibrates at full amplitude for about 15 s with short pauses; without amplitude control it is on/off only | Unit | Timings sum to 13 to 17 s; amplitudes 255 or none |
| TC-A-091 | The level 2 plan is stronger: about 30 s, no pause over 0.3 s, more total vibration than level 1 | Unit | Timings sum to 28 to 32 s; every pause at most 300 ms |
| TC-A-092 | Flashing is at most 3 per second at both levels, faster at level 2, and absent when switched off | Unit | Cycle at least 334 ms; none when off |
| TC-A-093 | Sound is on by default and follows the setting | Unit | Plan sound true by default, false when switched off |
| TC-A-094 | A stored alert is raised once; the same alert again is not raised again | Unit | One call to the notifier |
| TC-A-095 | Several alerts arriving together raise only the newest and mark all as raised | Unit | One call, with the newest |
| TC-A-096 | An alert with an older watch time that arrives after a newer one is still raised | Unit | Raised although its time is older |
| TC-A-097 | Alerts already in the history when the feature first runs are not raised; the next one is | Unit | No call for old ones; one for the new |
| TC-A-098 | After a restart nothing raised before is raised again, and an alert stored but not raised is | Unit | Same store, new raiser |
| TC-A-099 | "I'm OK" stops the notifier | Unit | `stop` called |
| TC-A-100 | The notifier posts an alarm-category notification with a full-screen intent on a high-importance channel; sound off uses the silent channel | Unit (Robolectric) | Channel importance high; category alarm; full-screen intent present; silent channel when the plan has no sound |
| TC-A-101 | The alert screen is declared, not exported, and the permissions it needs are requested | Unit (manifest) | `AlertActivity` not exported; notification, full-screen-intent and vibrate permissions |
| TC-A-102 | Alert preferences default to sound on and flashing on, and a change survives a restart; the raised-alert ids survive a restart | Unit | DataStore round trips |
| TC-A-103 | On the A55 with the app closed, an alert from the watch vibrates, wakes the screen and shows the full-screen alert; the Do Not Disturb, silent mode, locked and in-use cases are tried once each | Manual | Result of each case recorded in the work log |
| TC-A-104 | An alert is raised at its own level: a level 2 alert uses the stronger plan | Unit | Plan level 2 with the longer vibration |
| TC-A-105 | "I'm OK" stops the phone's alert at once and tells the watch which episode to end | Unit | Notifier stopped; `publishEpisodeEnd` called with the alert's episode id; result Sent |
| TC-A-106 | "I'm OK" on an alert without an episode id (a watch on protocol 1.1) only stops the phone's alert | Unit | Notifier stopped; nothing sent; result NoEpisode |
| TC-A-107 | If telling the watch fails, the phone's alert is still stopped and the failure is reported | Unit | Stopped; result NotSent with the reason |
| TC-A-108 | Tapping "I'm OK" on the alert screen writes the episode end for that episode | Unit (Hilt + Compose, fake Data Layer client) | An `/episode-end/` item is written; the screen closes |
| TC-A-109 | On the A55 and the Watch6 Classic, "I'm OK" on the phone ends the episode on the watch | Manual | The watch stops repeating its alert |
| TC-A-110 | The alert offset is shown with its default of 30 and a change within 20 to 50 is saved and published | Unit | Stored; a new schedule version carries it |
| TC-A-111 | An offset below 20 or above 50 is rejected and the old value kept | Unit | `OFFSET_OUT_OF_RANGE`; nothing published |
| TC-A-112 | The initial threshold (used until the watch has a baseline) can be changed within its range and is rejected outside it | Unit | Stored, or `INITIAL_OUT_OF_RANGE` with the old value kept |
| TC-A-113 | The watch's baseline, threshold and heart-rate permission are shown, and "not reported yet" before the watch has sent them | Unit | State follows the watch status repository |
| TC-A-114 | The alert sound and flashing switches default to on and a change is kept | Unit | Preferences store updated |
| TC-A-115 | The Settings tab in the app saves a changed offset and says so when the value is out of range | Unit (Hilt + Compose, fake Data Layer client) | Valid value stays after Save; invalid shows the error and keeps the old value |
| TC-A-116 | On the A55 and the Watch6 Classic, a changed offset reaches the watch: the watch's reported threshold follows baseline plus the new offset | Manual | Settings screen shows the new threshold after the watch reports |
| TC-A-117 | A task's reminders are listed in time order, and only that task's | Unit | Sorted by minute of day; other tasks' reminders absent |
| TC-A-118 | A task without reminders shows the Empty state | Unit | `ListState.Empty` |
| TC-A-119 | In the app: save a task, add a reminder to it, see it listed, open it and delete it | Unit (Hilt + Compose, fake Data Layer client) | Reminder shown as time and days; gone after Delete |
| TC-A-120 | A reminder is summarised as its time (24 hours) and its days: every day, weekdays, or the listed days | Unit | `09:00` + EveryDay; `[1..5]` Weekdays; others as a sorted list |
| TC-A-121 | After a task is saved its id is in the edit state, so reminders can be added to a new task | Unit | New id for a new task; the same id when editing |
| TC-A-122 | Before anything is sent the schedule state is idle; a version sent earlier is shown | Unit | `ScheduleSyncUiState(null, Idle)`, then the stored version |
| TC-A-123 | A sent schedule is reported with its version | Unit | `Sent(version)`, last version updated |
| TC-A-126 | While sending the state says so and a second tap does not send twice | Unit | `Sending`; the repository is called once |
| TC-A-127 | The Tasks tab sends the schedule once and then says the watch is up to date | Unit (Hilt + Compose, fake Data Layer client) | One `/schedule` item written; second tap writes nothing |
| TC-A-080 | The broadcast publishes the schedule on the real devices | Manual | Watch shows the reminder at the set time after the broadcast |
| TC-A-081 | The publish command logs the published version and the reminder time | Unit | `DEMO published schedule version=<n> reminderAt=<ms>`; `unchanged` when nothing new was sent |
| TC-A-082 | A failed or invalid publish is logged, not thrown | Unit | `DEMO publish failed reason=<text>` for a Data Layer failure and for minutes_ahead outside 1 to 1440 |
| TC-A-083 | The history dump logs every row type newest first, then an end line | Unit | ack, selection and alert lines in the agreed format, `none` for absent ids, `DEMO history end count=<n>` |
| TC-A-084 | The history dump of an empty history logs only the end line | Unit | `DEMO history end count=0` |
| TC-A-085 | Both receivers are declared, exported and protected by the DUMP permission | Unit (manifest) | Only `adb shell` (which holds DUMP) can send the broadcasts |
| TC-A-086 | The home screen shows the link-state tab first and switches to the history tab | Unit (Compose) | Link state and last sync visible at start; after tapping the History tab the history content shows |
| TC-A-087 | `MainActivity` shows both screens from the real Hilt graph | Unit (Hilt + Compose, fake Data Layer client) | "Never synced" on the link tab; the empty-history message on the History tab |
| TC-A-088 | The user sees the link state and the arriving acknowledgement on the real devices | Manual | Link state changes with Bluetooth/distance; an acknowledgement from the watch appears in History |

## Final (A-final)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-070 | `:phone:check` green on merged `main` | CI | Green |
| TC-A-071 | End-to-end with the real watch: schedule, reminder, acknowledgement, history | Manual | Reported by the user in the work log |
| TC-A-072 | Alert from the watch is stored with the app closed | Manual | Entry in history after opening the app |
