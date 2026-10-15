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

## Settings storage (step 2.2)

`DataStoreSettingsRepository` (`phone/.../data/datastore/`) stores `AppSettings` in Preferences DataStore (keys `thresholds` as a
JSON list, `cooldown_seconds`, `snooze_minutes`, `snooze_max_count`, `cadence_minutes`). `SettingsValidator` is the one place
that decides whether settings are acceptable (the ranges in the table above); the repository validates before it writes and
returns `Invalid(errors)` without writing. A missing value takes the default of `SettingsDefaults`, and an unreadable file
or value (including a damaged settings file) also falls back to the defaults instead of crashing. The repository takes a
`DataStore<Preferences>`; creating the file and the scope is the dependency graph's job (the Android lane's step 2.7). It
passes the same abstract `SettingsRepositoryContract` as the in-memory fake.

## Database (step 2.1)

Room database `AppDatabase` in `phone/.../data/db/`, file `my-adhd-pal.db`, with four tables:

| Table | Columns | Notes |
|---|---|---|
| `tasks` | `id`, `title`, `position` | listed by `position` (creation order) |
| `reminders` | `id`, `taskId`, `minuteOfDay`, `days` | foreign key to `tasks` with `ON DELETE CASCADE`, listed by time of day |
| `prompts` | `id`, `title` | the prompts the phone published, so a selection can show the prompt's title |
| `history` | `id`, `type`, `at`, and the per-type columns | one row per event from the watch; `id` is the primary key, so storing the same item twice leaves one row |

`RoomTaskRepository` and `RoomHistoryRepository` implement the repository interfaces and pass the same abstract contract
tests as the in-memory fakes. `AppDatabase.create(context)` builds the database; wiring it into the dependency graph is
the Android lane's step 2.7. The settings, schedule, prompt and link-status repositories follow in steps 2.2 and 2.5.

### Migration policy

- The schema is exported to `phone/schemas/<database class>/<version>.json` by the Room Gradle plugin and **committed**;
  a schema change without its new JSON file fails review, and `SchemaTest` (TC-S-097) fails when a version file is missing.
- Every version bump adds exactly one `Migration(from, to)` to `AppDatabase.MIGRATIONS` (in order) and a migration test that
  opens the old schema with `MigrationTestHelper`, migrates it and checks the data; `SchemaTest` fails when a step has no
  migration.
- Migrations are **never destructive**: no `fallbackToDestructiveMigration`, no dropping a table that holds user data
  (tasks, reminders, history). A column that must change is added next to the old one, copied, and only dropped in a
  later version once the copy is verified.
- Version 1 has no migration; `MIGRATIONS` is empty.

## Real Data Layer source (step 2.4)

`phone/.../link/`: `DataLayerWatchLinkDataSource` implements `WatchLinkDataSource` over a small `DataLayerClient`
(`GmsDataLayerClient` is the Play services implementation, a fake in the tests). The mapping of an item is the four keys
of `ItemKeys` (`v` int, `id`, `ts` long, `body`), the same as on the watch.

- **Events in.** `PhoneLinkListenerService` (declared in the manifest for `/ack/`, `/selection/`, `/alert/` and
  `/hello/watch`, so the system starts the process when the app is closed) and `scanExisting()` at start (items that
  arrived while the process was dead) hand watch-written items to `ingest`. Own writes (item host equals the local node),
  unknown paths, bad bodies and other major versions are dropped (`WatchItemDecoder`). An item is delivered once until
  `markStored` deletes it at the watch's node (writer-owns-cleanup: the phone may delete watch-written events after
  storing them). A hello is never deleted.
- **Publishing.** `/schedule` (urgent only when the reminders or checkpoints firing in the next 30 minutes change,
  `ScheduleUrgency`; the first publish after start is urgent), `/prompt/<id>` (urgent), `/hello/phone` (not urgent). A
  failing write is `PublishResult.Failed` with the reason. `removePrompt` deletes the phone's own prompt.
- **Link state.** `LinkStateLogic.derive` over `NodeClient.getConnectedNodes()`, the nodes with the capability
  `adhd_pal_watch` and the version of the watch's hello (declared by the phone as `adhd_pal_phone` in
  `res/values/wear.xml`). Refreshed every 2 s only while something collects `linkState`, once at start otherwise.
- **Not covered by unit tests:** `GmsDataLayerClient` and the service against the real Play services (only the manifest
  filters and the capability resource are checked); the hardware demo covers them.
