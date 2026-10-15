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

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-024 | Builder lists reminders in time order | Unit | Sorted by next occurrence with a fixed clock |
| TC-A-025 | Publish sends a `/schedule` item | Unit | Fake data source receives one schedule |
| TC-A-026 | Published version is higher than the previous | Unit | Monotonic version, also after restart |
| TC-A-027 | Published schedule includes thresholds and cooldown | Unit | Settings values appear in the published model |
| TC-A-028 | Publish failure is reported and not counted as sent | Unit | Error state; version unchanged |
| TC-A-029 | Publishing an unchanged schedule is a no-op | Unit | No new item; user is told nothing changed |
| TC-A-030 | Schedule round trip to the watch | Manual | Reminder created on the phone fires on the watch |

## Settings (4.3)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-031 | Defaults shown on first run | Unit | Default thresholds, cooldown and cadence |
| TC-A-032 | Change an alert threshold | Unit | Stored through the settings repository |
| TC-A-033 | Threshold out of range is rejected | Unit | Validation error; old value kept |
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
| TC-A-055 | Empty schedule shows guidance | Unit | Empty state message |
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
| TC-A-080 | The broadcast publishes the schedule on the real devices | Manual | Watch shows the reminder at the set time after the broadcast |

## Final (A-final)

| Id | Case | How | Pass criteria |
|---|---|---|---|
| TC-A-070 | `:phone:check` green on merged `main` | CI | Green |
| TC-A-071 | End-to-end with the real watch: schedule, reminder, acknowledgement, history | Manual | Reported by the user in the work log |
| TC-A-072 | Alert from the watch is stored with the app closed | Manual | Entry in history after opening the app |
