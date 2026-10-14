# Phone data layer contract

Status: **contract** (step 2.3, gate G3a). The interfaces below are what the Android lane builds ViewModels against; the
Room, DataStore and Data Layer implementations (steps 2.1, 2.2, 2.4, 2.5) must satisfy the same contract tests. The wire
format and item semantics are in [`protocol.md`](protocol.md).

Code: interfaces and models in `phone/src/main/java/com/shlomoa/myadhdpal/phone/data/`; fakes and contract tests in
`phone/src/test/java/com/shlomoa/myadhdpal/phone/data/` (fakes are test code, so production code never binds one).

## Rules

- The UI never touches the Data Layer. `WatchLinkDataSource` is the only door; repositories are the only door to storage.
- Repositories publish `Flow`s that re-emit on change; the database is the single source of truth. The watch's items
  never edit master state: they become history rows only.
- Time comes from the injected `java.time.Clock` (see `ClockModule`); ids are UUID strings.
- An event from the watch is deleted from the Data Layer only after its row is stored (`markStored`); the phone deletes
  its own prompts when the answer arrives or at expiry (`removePrompt`). Both follow the writer-owns-cleanup rule.

## Interfaces

| Interface | Purpose | Fake (test sources) |
|---|---|---|
| `WatchLinkDataSource` | `linkState` and `lastSync` flows, `events` from the watch (decoded, valid, compatible), publish schedule, prompt and hello, `removePrompt`, `markStored` | `FakeWatchLinkDataSource` (connect, disconnect, `emit`, `failNextPublish`, records) |
| `TaskRepository` | tasks and reminders: lists, queries by task, save (insert or replace), delete (a task deletes its reminders) | `InMemoryTaskRepository` |
| `HistoryRepository` | acknowledgements, selections and alerts, newest first, filter by type, `store` idempotent by id | `InMemoryHistoryRepository` |
| `SettingsRepository` | thresholds, cooldown, snooze, cadence; defaults; validation | `InMemorySettingsRepository` |
| `ScheduleRepository` | builds `/schedule` from tasks, reminders and settings, publishes with a growing version | `InMemoryScheduleRepository` + `InMemoryScheduleVersionStore` |
| `PromptRepository` | validates and publishes `/prompt/<id>` with a fresh id | `InMemoryPromptRepository` |
| `LinkStatusRepository` | link state and last sync for the link-state screen | `InMemoryLinkStatusRepository` |

`ScheduleBuilder.build` (main sources) is the pure function from the phone's data to the wire `Schedule`; every
`ScheduleRepository` uses it.

## Behaviour that the contract tests pin

- **Publishing:** a schedule is published with `version = last + 1`. If the content (everything but the version) equals the
  last published one, nothing is sent (`Unchanged`). A failed publish is `Failed` and does not consume a version. The last
  version survives a restart (`ScheduleVersionStore`; DataStore in 2.5), so versions only grow.
- **Prompts:** title not blank and up to 200 characters, 2 to 6 option labels (keys "1", "2", ...), optional expiry that
  must be in the future; each publish gets a new id. Violations are `Rejected` with the set of errors.
- **History:** `store` returns `Stored`, `Duplicate` (same id again) or `Ignored` (a hello). `at` is the time of the user's
  action or reading on the watch clock; the list is ordered by `at`, newest first. An acknowledgement entry also keeps
  the optional `reminderId` and `occurrenceAt` of protocol 1.1, so history shows which reminder was answered.
- **Settings:** `update` returns `Saved` or `Invalid(errors)` and keeps the old value on error.
- **Link status:** reflects the data source; the real source computes the state with the link-state function (step 1.5).
  The fake starts Away and holds events back until the link is up, like DataItems.

## Settings ranges and defaults

| Setting | Default | Accepted |
|---|---|---|
| Heart-rate threshold (above) | 150 (placeholder until step 6.2 agrees the values) | 30 to 220, at most 8 thresholds |
| Alert cooldown | 600 s | 60 to 86400 s |
| Snooze | 10 minutes, at most 3 | minutes 1 to 240, count 0 to 10 (the protocol limits) |
| Reminder cadence | 60 minutes | 5 to 1440 minutes |
