# Shared and data lane test-case catalogue (S)

Lane S owns tooling, restructure, spikes, `docs/protocol.md`, `:protocol` and the phone data layer
([`../plans/shared.md`](../plans/shared.md)). Case ids are `TC-S-nnn`; test names embed the id (see
[`../plans/README.md`](../plans/README.md), "Validation first and last"). Step ids are from [`../plan.md`](../plan.md).

**Kinds:** `unit` = JVM test, `android` = Robolectric or instrumented-free Android unit test, `script` = shell fixture,
`CI` = checked by a CI job, `manual` = run by the user and reported in `docs/work-log/shared.md`.
**Evidence:** red baseline in `docs/test-reports/shared-baseline.md` (each id red, with command, output excerpt and
commit SHA); final in `docs/test-reports/shared-final.md` (each id `PASS`, zero skipped).
**Parts:** `a` = needs no upstream gate (written now); `b` = compiles only after the named gate.

## Part a: tooling and environment (steps 0a.1-0a.8, needs only G-ENV)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-S-001 | 0a.1 | script | Baseline on `main`: `assembleDebug testDebugUnitTest lintDebug` | exit 0, result recorded in the work log |
| TC-S-002 | 0a.2 | CI | Debug APK path after the rename | `wear/build/outputs/apk/debug/*.apk` exists; CI uploads it |
| TC-S-003 | 0a.2 | script | `allowBackup` is off in both app manifests | merged manifests of `:wear` and `:phone` contain `android:allowBackup="false"` |
| TC-S-004 | 0a.2 | script | Namespaces | `:wear` is `...myadhdpal.wear`, `:phone` is `...myadhdpal.phone` |
| TC-S-005 | 0a.3 | unit | `:protocol` is pure Kotlin/JVM | `:protocol:test` runs; the module applies no Android plugin and has no `android.*` import |
| TC-S-006 | 0a.3 | CI | Both APKs build | `:wear:assembleDebug` and `:phone:assembleDebug` exit 0 |
| TC-S-007 | 0a.4 | script | Pairing check passes on the scaffold | `check` green with equal `applicationId` and signing config |
| TC-S-008 | 0a.4 | script | Negative fixture: mismatched `applicationId` | changing one app's `applicationId` makes `check` fail with a message naming both ids; revert is green |
| TC-S-009 | 0a.4 | script | Negative fixture: mismatched signing config | differing signing config makes `check` fail; revert is green |
| TC-S-010 | 0a.4 | script | SDK levels in one place | `minSdk 30`, `targetSdk 36`, `compileSdk 37` come from one source; both apps report the same values |
| TC-S-011 | 0a.5 | script | Negative fixture: mis-formatted Kotlin file | `spotlessCheck` (via `check`) fails and names the file; `spotlessApply` then passes |
| TC-S-012 | 0a.5 | script | Negative fixture: detekt violation | `detekt` (via `check`) fails with the rule id; removal is green |
| TC-S-013 | 0a.5 | script | Negative fixture: new Android lint issue | `lint` fails on a new issue not in the baseline; removal is green |
| TC-S-014 | 0a.5 | script | Negative fixture: broken internal link in docs | internal link check fails naming file and target; fix is green |
| TC-S-015 | 0a.5 | script | Negative fixture: markdownlint violation | markdownlint-cli2 fails naming rule and line; fix is green |
| TC-S-016 | 0a.5 | script | Docs lint and link check run behind `./gradlew check` | the Gradle task graph of `check` includes them; no tool list in the workflow |
| TC-S-017 | 0a.5 | script | Tool compatibility with AGP 9.4 and Kotlin 2.4 | each of Spotless, ktlint, detekt, markdownlint-cli2 runs; an incompatibility stops the lane (plan.md rule) |
| TC-S-018 | 0a.6 | CI | CI jobs appear on a PR | the jobs `check assembleDebug`, docs lint and external links are listed on the PR |
| TC-S-019 | 0a.6 | CI | External link job is non-blocking | a broken external link produces a warning; the PR stays mergeable |
| TC-S-020 | 0a.6 | script | Negative fixture: debug class in release | a class from `wear/src/debug/` found in the release build fails the release-has-no-debug check; removal is green |
| TC-S-021 | 0a.6 | script | Release has no debug code (positive) | the check passes on a clean release build of both apps |
| TC-S-022 | 0a.7 | manual | Issue template renders | the "Validation plan" section shows on GitHub's new-issue page |
| TC-S-023 | 0a.7 | script | One issue per plan.md step, no duplicates | every step id `0a.1` ... `7.6` has exactly one issue, labelled with its milestone and lane, starting with "Validation plan" |
| TC-S-024 | 0a.7 | script | Labels | one label per milestone (0a, 0b, 1-7) and per lane (S, A, W) exist |
| TC-S-025 | 0a.7 | script | Work-log entry format | each entry in `docs/work-log/shared.md` has date, step, Issue and PR, Changed, Validation, Open items |
| TC-S-026 | 0a.8 | script | Bootstrap script in a fresh state | with no Android SDK present, the script installs command-line tools, platform 37.0, build-tools 37.0.0 and platform-tools |
| TC-S-027 | 0a.8 | script | Bootstrap script is idempotent | a second run changes nothing and exits 0 |
| TC-S-028 | 0a.8 | script | Maven mirror fallback only on refusal | the mirror is configured only when `repo.maven.apache.org` refuses requests, and not when it answers 200 |
| TC-S-029 | 0a.8 | script | Fresh state then `./gradlew check` | green |
| TC-S-030 | 0a.8 | script | Script failure paths | a missing prerequisite (no `curl` or `unzip`) or an interrupted download exits non-zero with a clear message |
| TC-S-143 | G2 split | script | Gate graph is consistent and G2 is split | `scripts/test/test-gates.sh`: unique ids, existing requirements, no cycle, README table equals `gates.json`, lane plans name existing gates; G2a requires G1 only, G2b requires G2a and G-DEC, G3a and G5 require G2a, G3 requires G2b |
| TC-S-144 | issue 113 | unit | Compose UI tests run on the JVM | a `createComposeRule()` test finds and clicks a node under Robolectric in `:phone:testDebugUnitTest`; `./gradlew check` stays green |

## Part b: protocol (milestone 1, `:protocol`; needs G-SPK1 for 1.1-1.3; G1 for 1.4, 1.6, 1.7 and 1.8a (gate G2a); G2a and G-DEC for 1.5 and 1.8b (gate G2b))

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-S-040 | 1.1 | CI | Spec skeleton passes doc lint | `docs/protocol.md` has item catalog, item format, versioning policy, link states; markdownlint and link check green |
| TC-S-041 | 1.1 | script | Constants match the spec | every path in the item catalog table of `docs/protocol.md` has a constant in `:protocol` (single source of truth) |
| TC-S-042 | 1.2 | unit | Path builders | `/schedule`, `/prompt/<id>`, `/ack/<id>`, `/selection/<id>`, `/alert/<id>`, `/hello/phone`, `/hello/watch`, `/debug/biometrics` build exactly as the spec |
| TC-S-043 | 1.2 | unit | Path parsers | each builder output parses back to its kind and id; unknown paths and empty ids return null |
| TC-S-044 | 1.2 | unit | Path edge cases | ids with `/`, whitespace or non-UUID text are rejected; trailing slash is rejected |
| TC-S-045 | 1.2 | unit | Keys and version constants | `v`, `id`, `ts`, `body` and the protocol major version equal the spec values |
| TC-S-046 | 1.3 | unit | Round trip: schedule | encode then decode equals the original, including thresholds, cooldown and version |
| TC-S-047 | 1.3 | unit | Round trip: prompt | with and without expiry, one to n options |
| TC-S-048 | 1.3 | unit | Round trip: acknowledgement | each action (Done, Snooze, Start 2 min, Dismiss) |
| TC-S-049 | 1.3 | unit | Round trip: selection | chosen key and time |
| TC-S-050 | 1.3 | unit | Round trip: alert | metric, numeric value, threshold, time |
| TC-S-051 | 1.3 | unit | Round trip: hello | protocol and app version |
| TC-S-052 | 1.3 | unit | Unknown fields tolerated | a body with extra fields decodes; known fields are kept |
| TC-S-053 | 1.3 | unit | Invalid body is dropped | missing required field, wrong type or malformed JSON returns a typed failure, never throws |
| TC-S-054 | 1.3 | unit | Size limit | the largest legal schedule and a maximal prompt encode to well under 100 KB (asserted bound documented in the test) |
| TC-S-055 | 1.3 | unit | Item envelope | `v`, `id`, `ts`, `body` all present; `body` is a JSON string; `:protocol` imports no `DataMap` |
| TC-S-056 | 1.4 | unit | Major version equal | accepted |
| TC-S-057 | 1.4 | unit | Major version higher or lower | rejected, result is Incompatible, item ignored |
| TC-S-058 | 1.4 | unit | Minor and patch differences | accepted |
| TC-S-059 | 1.4 | unit | Missing or malformed hello version | treated as Incompatible, never a crash |
| TC-S-060 | 1.5 | unit | Link state: Nearby | table row: capability present, node nearby, hello compatible |
| TC-S-061 | 1.5 | unit | Link state: Relayed | table row: capability present, node not nearby, hello compatible |
| TC-S-062 | 1.5 | unit | Link state: Away | table row: no connected node |
| TC-S-063 | 1.5 | unit | Link state: No watch app | table row: paired node without the app capability |
| TC-S-064 | 1.5 | unit | Link state: Incompatible | table row: hello with an unsupported major version |
| TC-S-065 | 1.5 | unit | Link state table is exhaustive | every combination of inputs maps to exactly one of the five states |
| TC-S-066 | 1.5 | unit | Link state precedence | Incompatible overrides Nearby and Relayed; No watch app overrides Away when a node is paired |
| TC-S-067 | 1.6 | unit | Next reminder | fixed clock; next reminder after now, none when the schedule is empty |
| TC-S-068 | 1.6 | unit | Next checkpoint | fixed clock; checkpoints inside and across midnight |
| TC-S-069 | 1.6 | unit | Snooze rules | snooze moves the reminder by the configured amount; repeated snooze, snooze past expiry and snooze at the cap behave as specified |
| TC-S-070 | 1.6 | unit | Time-zone and DST change | schedule recomputed with a fixed clock on both sides of a DST change |
| TC-S-071 | 1.6 | unit | Newer-version rule | a schedule applies only if its version is greater; equal and older are ignored |
| TC-S-072 | 1.7 | unit | Fake transport: delivery when connected | an item sent while connected is received once, in order |
| TC-S-073 | 1.7 | unit | Fake transport: buffering while disconnected | items sent while disconnected arrive on reconnect, in order |
| TC-S-074 | 1.7 | unit | Fake transport: item replacement | a newer item at the same path replaces the older one (as for DataItems) |
| TC-S-075 | 1.7 | unit | Fake transport: deletion | a deleted item is not redelivered after reconnect |
| TC-S-076 | 1.7 | unit | Fake transport: repeated disconnect and reconnect | no duplicates, no loss |
| TC-S-077 | 1.8 | CI | Diagrams pass doc lint | Mermaid blocks present for each flow and the link and prompt state machines; doc lint green |
| TC-S-078 | 1.8 | manual | Diagrams render | the PR's rendered view shows every diagram without error |
| TC-S-079 | 1.8 | script | Diagram states match code | every link state in the diagram is a member of the `LinkState` type |

## Part b: phone data layer (milestone 2; 2.3 needs G2a, 2.1 and 2.2 need G3a and G-LIB, 2.4 and 2.5 also need G2b)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-S-090 | 2.3 | unit | `WatchLinkDataSource` fake delivers items | the fake emits what the test publishes |
| TC-S-091 | 2.3 | unit | Fake simulates link changes | disconnect and reconnect surface through the interface as link-state changes |
| TC-S-092 | 2.3 | unit | Repository interfaces have in-memory fakes | the fakes satisfy the interface contract tests |
| TC-S-093 | 2.1 | android | Room DAO: tasks | insert, update, delete, query by id and list |
| TC-S-094 | 2.1 | android | Room DAO: reminders | CRUD and query by task and time |
| TC-S-095 | 2.1 | android | Room DAO: history | insert acknowledgement, selection and alert; query ordered by time |
| TC-S-096 | 2.1 | android | History is idempotent by id | inserting the same item id twice leaves one row |
| TC-S-097 | 2.1 | android | Schema export and migration policy | exported schema is committed; a migration test passes for each version step; policy is in the docs |
| TC-S-098 | 2.2 | android | DataStore defaults | thresholds, cooldown and cadence have the documented defaults |
| TC-S-099 | 2.2 | android | DataStore round trip and validation | a written value is read back; out-of-range values are rejected |
| TC-S-100 | 2.4 | unit | Listener: decode, validate, store | a valid ack, selection and alert are stored with the right type |
| TC-S-101 | 2.4 | unit | Listener: idempotent by id | delivering the same id twice stores one row |
| TC-S-102 | 2.4 | unit | Listener: delete after store | the item is deleted only after the row is stored; a storage failure keeps the item |
| TC-S-103 | 2.4 | unit | Listener: invalid item | an item failing validation is dropped, nothing is stored |
| TC-S-104 | 2.4 | unit | Listener: incompatible major version | item ignored, link state becomes Incompatible |
| TC-S-105 | 2.4 | script | Manifest entries by path prefix | the listener service declares filters for `/ack`, `/selection`, `/alert` and `/hello/watch` only |
| TC-S-106 | 2.4 | manual | Listener with the phone app closed | with the app force-stopped from recents, an item from the watch is stored (user reports on emulators, then real devices) |
| TC-S-107 | 2.5 | unit | Repository publishes `/schedule` and `/hello/phone` on start | the fake source records both, with a monotonic schedule version |
| TC-S-108 | 2.5 | unit | Repository is the single source of truth | UI-facing flows reflect the DAO; the watch's items never edit master state |
| TC-S-109 | 2.5 | unit | Schedule version is monotonic across restarts | after a restart the next version is greater than the last published |
| TC-S-110 | 2.5 | unit | Debug action publishes a test schedule | only present in debug builds; absent in release (see the release-has-no-debug cases in part a) |

## Part c: spikes and verification (manual, user runs; steps 0b.1-0b.3)

| Id | Step | Kind | Case | Pass criteria |
|---|---|---|---|---|
| TC-S-120 | 0b.1 | CI | Hilt, KSP and Room build on AGP 9.4 and Kotlin 2.4 | an injection sample and a DAO test build in CI; the Hilt or manual-DI decision is in the work log (an incompatibility stops the lane) |
| TC-S-121 | 0b.2a | manual | Items cross the emulator pairing | a DataItem written on one side is read on the other |
| TC-S-122 | 0b.2a | manual | Persistence while the receiver is away | an item written while disconnected arrives on reconnect |
| TC-S-123 | 0b.2a | manual | Phone listener wakes with its app closed | the listener service runs with the app closed |
| TC-S-124 | 0b.2a | manual | Deletion | a consumed item can be deleted and does not reappear |
| TC-S-125 | 0b.2a | manual | Disconnect and reconnect | behaviour is recorded in the results table in `docs/protocol.md` |
| TC-S-126 | 0b.2b | manual | Delay targets agreed first | targets recorded before the run (proposal: about 5 s nearby, about 30 s relayed) |
| TC-S-127 | 0b.2b | manual | Nearby, Relayed and Away on the real pair | each state observed and the urgent-item delay measured against the targets |
| TC-S-128 | 0b.2b | manual | Samsung battery management with default settings | listener behaviour recorded with the battery settings used |
| TC-S-129 | 0b.2b | manual | Pairing through Samsung's Galaxy Wearable app | items cross; device, Android, One UI and Wear OS versions recorded |
| TC-S-130 | 0b.3 | manual | Decision recorded | fallback rule applied if criteria 2, 3 or urgent delay failed; transport section updated; spike code discarded |

## Final run (S-final, V-last)

| Id | Case | Pass criteria |
|---|---|---|
| TC-S-140 | Full suite on merged `main` | `./gradlew check assembleDebug assembleRelease` and docs lint are green |
| TC-S-141 | All lane cases | every `TC-S-*` is PASS in `shared-final.md` and `scripts/check-test-cases.sh shared final` passes, zero skipped |
| TC-S-142 | Consumers compile against the final protocol | `:wear` and `:phone` compile against the final `:protocol` |
