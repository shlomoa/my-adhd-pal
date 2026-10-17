# Plan: ADHD phone + watch monorepo

Status: **approved** in a section-by-section review (2026-10-11) that merged the architecture thread's plan with the
watch thread's hand-off. Documentation only; no code changes accompany this document.

Screen designs (a proposed design until the user approves it): [`design/README.md`](design/README.md).

## 1. Context

`my-adhd-pal` started as a single-module Wear OS app (Kotlin + Compose, `com.shlomoa.myadhdpal`, minSdk 30,
targetSdk 36). `main` has JDK 21, Gradle 9.8.1 (checksum pinned), AGP 9.4.1, Kotlin 2.4.21 and CI on Temurin 21.

**Goal:** a native Kotlin phone app (full app, no backend and no internet use for now) and the watch app, in one
Gradle multi-module project in this repo (`my-adhd-app` no longer exists), linked over the Wearable Data Layer with a
documented protocol. Distribution on Google Play is a goal handled in the last milestone.

**Why:** the watch supports four needs: time blindness, getting started, forgetting things, and impulsivity and
emotions.

### Reference devices

- Watch: Samsung Galaxy Watch6 Classic 47 mm (SM-R965F), Wear OS 5 (API 34), possibly Wear OS 6 (API 36).
- Phone used for testing: Samsung Galaxy A55 5G (SM-A556E/DS). Its Android and One UI versions, and the watch's
  Wear OS and One UI Watch versions, are recorded in the spike results.

## 2. Decisions (settled)

### Structure

- One repo, three modules: `:protocol` (pure Kotlin/JVM), `:wear` (rename of `:app`), `:phone` (new).
- Same `applicationId` (`com.shlomoa.myadhdpal`) and signing config on both apps, enforced by a build-time check.
- SDK baseline for both apps: `minSdk 30`, `targetSdk 36`, `compileSdk 37`.

**Phone app** (full app, no backend, no internet in v1)

- Native Kotlin, calling the Data Layer directly. No Capacitor, Angular or third-party watch plugins.
- Two layers (UI and data), no domain layer. Room for tasks, reminders and history; DataStore for settings.
- A background listener service stores items from the watch (acknowledgements, selections, biometric alerts) even when
  the app is closed.
- v1 features: create and edit tasks, reminders and settings; history of acknowledgements, selections and alerts; a
  link-state screen.
- `WatchLinkDataSource` interface with a fake for tests. Hilt if compatible with AGP 9 / KSP (verified by a spike in
  0b.1), otherwise manual DI.

**Watch app** (needs: time blindness, getting started, forgetting, impulsivity and emotions)

- Reminders fired locally from a schedule synced by the phone. After the first sync the watch works without the
  phone; sync resumes on reconnect.
- Haptic catalogue (tick, warning, calm-breath), glanceable tile, acknowledgements (Done, Snooze, Start 2 min,
  Dismiss), simple selections, and biometric alerts with no user interaction.
- Biometrics through the live Health Services route (`MeasureClient` in a foreground health service, step 6.4) while the
  app runs; the passive background route was measured and delivers about every 10 minutes
  ([`biometrics.md`](plans/biometrics.md) section 12.3), so it is not used for alerts. Permissions follow the
  API 36 rules (`READ_HEART_RATE`, legacy `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` where needed); exact names are
  confirmed in milestone 6 and verified on the real watch.
- Standalone flag stays `true`; notification bridge `NO_BRIDGING` to avoid duplicates.
- One shared link state in `:protocol`: **Nearby, Relayed, Away, No watch app, Incompatible**. The phone shows all
  five plus the last-sync time on its link-state screen (no controls). The watch shows Nearby, Relayed, Away and
  Incompatible plus the last-sync time on the home screen and the tile. State comes from a `CapabilityClient`
  listener plus a periodic re-read; no `BluetoothDevice` broadcasts (unverified on Wear OS, Bluetooth-specific).
- Debug-only code (verbose logs, raw biometrics, an injected-message receiver, a debug log view) lives in
  `wear/src/debug/`; release builds contain none. Raw biometrics are health data and are never logged in release.

### Link

- Pairing is done by the OS. Trust boundary: same package name and signing certificate.
- The Data Layer is the only watch-phone channel. Out of scope: watches paired with an iPhone, and phones without
  Google Play services.
- Both apps set `allowBackup` off, since the data includes health readings.

## 3. Transport (decided, with a verification spike)

**Decision: DataItems only.** All traffic between watch and phone is DataItems, following the Data Layer overview,
DataItems and network-communication pages and the fitness integration guide.

| Direction | Path | Content | Urgent |
|---|---|---|---|
| phone to watch | `/schedule` | full schedule and settings (version, thresholds, cooldown), replacing the previous item | only if it changes the next 30 minutes |
| phone to watch | `/prompt/<id>` | title, options, optional expiry | yes |
| watch to phone | `/ack/<id>` | action and time | yes |
| watch to phone | `/selection/<id>` | chosen key and time | yes |
| watch to phone | `/alert/<id>` | metric, numeric value, threshold, time | yes |
| both | `/hello/phone`, `/hello/watch` | protocol and app version, overwritten at start | no |
| watch to phone | `/debug/biometrics` | raw samples, debug builds only | no |

**Rules:** every event item has a unique `<id>` and a timestamp. Consumers are idempotent by id. Cleanup follows the
writer-owns-cleanup rule in section 4 (the emulator spike showed that a quick delete by the other node can be lost).
`setUrgent()` is used only where delay hurts. Payloads stay well under the 100 KB limit.

**What this does and does not mean:** the app contains no Bluetooth or socket code and no message-based protocol.
Google Play services moves the items over Bluetooth when the devices are connected and through Google's servers
(end-to-end encrypted) otherwise. The apps cannot choose or disable that route. No cost is documented (none found,
unverified), and the user accepted the relay, including numeric health values in alerts. Direct Wi-Fi was rejected
because it is not always available. FCM and HTTP need a server, and none exists.

**Verification spike (before the protocol is locked, steps 0b.2a and 0b.2b):** record (1) items persist while the
receiver is away and arrive on reconnect, (2) the phone's listener service wakes with its app closed, (3) a consumed
item can be deleted and does not reappear, (4) urgent-item delay when nearby, relayed and away.

**Fallback rule, decided now:** if the spike fails on 2 or 3, or urgent alerts are too slow, add `MessageClient` for
urgent alerts only. Everything else stays as items.

**Result (G-SPK1, G-DEC):** the fallback rule was not triggered, on the emulators (0b.2a) or on the Galaxy Watch6 Classic
and Galaxy A55 5G (0b.2b): listener wake-up with the app closed, deletion in both directions and urgent delay (about
0.2 s nearby, about 1.8 s relayed round trip) all passed, so the transport is **DataItems only** and `MessageClient` is
not added. Details, numbers and what is not verified are in [`protocol.md`](protocol.md).

## 4. Protocol

`docs/protocol.md` is the single source of truth. `:protocol` holds the matching constants (paths, field names,
versions) and models, with unit tests.

**Item format.** Each item has the keys `v` (protocol major version), `id` (UUID), `ts` (sender time) and `body` (a
JSON string produced by a kotlinx.serialization codec in `:protocol`). `:protocol` never touches Android's `DataMap`;
thin adapters in `:wear` and `:phone` convert between `DataMap` and the models. Payloads are asserted well under
100 KB.

**Roles.** The phone is the data master (schedule, settings, history). The watch is a capture and nudge client. Its
acknowledgements, selections and alerts flow to the phone and never edit master state.

### Rules per communication type

- **Schedule sync:** `/schedule` carries a monotonic version. The watch applies it only if newer. No conflicts,
  because only the phone writes it. Alert thresholds and the alert cooldown are part of the schedule settings, set from
  the phone; the watch evaluates them locally.
- **Prompt, then acknowledgement or selection:** `/prompt/<id>` may carry an expiry. The answer uses the same `<id>`
  and is idempotent. A snooze is applied on the watch immediately and then reported to the phone, which records it.
- **Alert:** watch to phone, urgent, unique id, with a cooldown on the watch to prevent floods.
- **Version and compatibility:** each side publishes a state item (`/hello/phone`, `/hello/watch`) with protocol and app
  version, overwritten at start. A receiver ignores items whose major version it does not support and shows
  Incompatible. There is no request and reply.
- **Cleanup (writer owns cleanup; decided from the emulator spike at G-SPK1, kept after the hardware spike):** a node deletes the
  items it wrote. The watch never deletes phone-written items (`/prompt/<id>`, `/schedule`); the phone removes a prompt
  when it receives the answer or when it expires. The phone may delete watch-written events (`/ack/<id>`,
  `/selection/<id>`, `/alert/<id>`) after storing them. Consumers stay idempotent by id, so an item that survives a lost
  delete is handled once. Evidence: [`protocol.md`](protocol.md).
- **Errors:** no RPC error codes. An item that fails validation is dropped, and the failure is logged in debug builds.

**Spec contents:** item catalog, Mermaid sequence diagrams, state machines for the link and for prompts, and a
versioning policy.

## 5. Working rules

### Principles

- KISS. DRY: abstract common functionality into reusable components, reference commonalities, and keep one
  authoritative place for every rule or piece of knowledge.
- Latest package versions, features and capabilities, chosen after web research.
- No trial and error and no speculative implementation.
- Research the web before coding, for best practices, specific patterns and general approaches.
- **Stop** on context loss, unclarity, or medium to high ambiguity: state the issue and its impact, then wait for the
  user's instructions. This includes a lint or format tool that is incompatible with AGP 9.4 / Kotlin 2.4.
- Nothing is written outside the repo, except tool-managed locations: the Claude Code plan file, its scratch folder and
  tool-output dumps, and build-tool caches (such as `~/.gradle` and the Android SDK).

### Transparency

- Work is logged in `docs/work-log.md` (one entry per task: date, issue and PR, what changed, validation results, open
  items).
- Work is managed with GitHub issues and pull requests: one issue per task, labelled with its milestone, and each pull
  request closes its issue.

### Validation

- Code and documentation are both validated. Every task and step starts with a validation plan, then the
  implementation.
- Each issue has a Validation plan (what is checked, how: automated, CI or manual, and the pass criteria). The pull
  request repeats it with results.
- Formatting and linting are part of validation and CI: Spotless with ktlint, detekt, Android lint (failing on new
  issues), and markdownlint-cli2 plus a link check for docs. Internal links block CI; external links run as a
  separate non-blocking job. They are defined once in Gradle behind `./gradlew check` and called by CI, with no
  separate tool list in the workflow. The setup is validated by showing a mis-formatted change fail.
- Release builds contain no debug code (CI check).

## 6. Milestones (after this plan is approved)

Every step becomes a GitHub issue labelled with its milestone, starting with its Validation plan.

### 0a. Tooling and restructure

- **0a.1 Baseline.** Run `assembleDebug testDebugUnitTest lintDebug` on `main` and record the result.
  *Validate:* green, recorded in the work log.
- **0a.2 Restructure.** `git mv app wear`; include `:wear`, `:protocol` and `:phone` in `settings.gradle.kts`;
  namespaces `...myadhdpal.wear` and `...myadhdpal.phone`; `allowBackup` off in both apps; update the CI APK path,
  README and `.gitignore`. *Validate:* CI green, debug APK at `wear/build/outputs/apk/debug/`.
- **0a.3 Skeletons.** `:protocol` as a pure Kotlin/JVM module with a trivial test; `:phone` as an Android app with the
  same `applicationId` and a minimal activity. *Validate:* both APKs build, `:protocol:test` runs.
- **0a.4 Shared configuration.** `applicationId`, SDK levels and signing config in one place (mechanism chosen in the
  issue after research), plus a build-time pairing check. *Validate:* changing one app's `applicationId` makes `check`
  fail, then revert.
- **0a.5 Checking tools.** Spotless with ktlint, detekt, Android lint (fail on new issues), markdownlint-cli2, link
  check. Compatibility with AGP 9.4 and Kotlin 2.4 is checked first; on a conflict, stop and ask. *Validate:*
  `./gradlew check` green on the scaffold; a mis-formatted Kotlin file and a broken internal link each fail, then
  revert.
- **0a.6 CI.** The workflow calls `./gradlew check assembleDebug` and the one docs-lint command; the external link
  check runs as a separate non-blocking job; a release-has-no-debug check. *Validate:* all jobs appear on a PR, and the
  external job warns without blocking.
- **0a.7 Conventions.** The `docs/work-log.md` entry format, an issue template with a Validation plan section, a label
  per milestone, and issues for all later steps. *Validate:* the template renders; issue links are in the work log.
- **0a.8 Cloud bootstrap script.** One reviewed Linux script that installs the Android command-line tools, platform 37.0,
  build-tools 37.0.0 and platform-tools, and points Gradle at Google's Maven Central mirror only if
  `repo.maven.apache.org` refuses requests (it answered 429 in the cloud environment). It is also callable from the
  environment's setup script. *Validate:* in a fresh cloud session, the script followed by `./gradlew check` is green.

### 0b. Spikes and verification

- **0b.1 Library compatibility.** Hilt, KSP and Room with AGP 9.4 and Kotlin 2.4 in `:phone`; decide Hilt or manual DI.
  *Validate:* an injection sample and a DAO test build in CI; the decision is in the work log.
- **0b.2a Emulator-pair spike (required, first).** Throwaway code on a spike branch, run on a Wear OS emulator paired
  with a Play-Store phone emulator (Device Manager, Pair Wearable): items cross the pairing, the phone's listener wakes
  with its app closed, deletion works, disconnect and reconnect behave. *Validate:* results table committed to
  `docs/protocol.md`. Problems found here apply the fallback rule before any milestone 1 work.
- **0b.2b Real-hardware spike.** The same code on the Galaxy Watch6 Classic with the Galaxy A55 5G, for what emulators
  cannot show: Nearby, Relayed and Away, urgent-item delay, Samsung battery management with default settings, and
  pairing through Samsung's Galaxy Wearable app. Delay targets are agreed with the user before it runs (proposal: about
  5 seconds nearby and about 30 seconds relayed, calibrated by the spike). *Validate:* results table with device,
  Android, One UI and Wear OS versions and the battery settings used. If the listener is cut off, how to handle it is
  decided with the user.
- **0b.3 Decide and record.** Apply the fallback rule if needed and update the Transport section. The spike apps and runners are kept as
  debug-only test tooling (`docs/spikes/`). *Validate:* the decision and its evidence are in the work log.

### 1. Protocol (`docs/protocol.md` and `:protocol`)

- **1.1 Spec skeleton:** item catalog, item format, versioning policy, link states. *Validate:* doc lint.
- **1.2 Constants:** path builders and parsers, keys, version. *Validate:* unit tests.
- **1.3 Models and codec:** schedule (with alert thresholds and cooldown), prompt, acknowledgement, selection, alert,
  hello. *Validate:* round-trip tests; unknown fields tolerated; a size-limit test.
- **1.4 Compatibility rule:** major-version check and the Incompatible state. *Validate:* mismatch tests.
- **1.5 Link-state function** (five states) from nodes, capability and hello versions. *Validate:* table-driven tests.
- **1.6 Schedule logic:** next reminder and checkpoint, snooze rules. *Validate:* tests with a fixed clock.
- **1.7 Fake transport** with disconnect, reconnect and buffering. *Validate:* tests of its buffering behaviour.
- **1.8 Diagrams:** Mermaid sequence diagrams and state machines. *Validate:* doc lint plus a rendered check on the PR.

### 2. Phone data layer

- **2.1 Room schema** (tasks, reminders, history) and a migration policy. *Validate:* DAO tests.
- **2.2 DataStore settings** (thresholds, cooldown, cadence). *Validate:* unit tests.
- **2.3 `WatchLinkDataSource`:** interface, real implementation and fake. *Validate:* tests with the fake.
- **2.4 Background listener service:** decode, validate, store, delete, idempotent by id; manifest entries by path
  prefix. *Validate:* tests with the fake; manual with the phone app closed.
- **2.5 Repositories** as the single source of truth, publishing `/schedule` and `/hello/phone` on start, plus a debug
  action that publishes a test schedule. *Validate:* ViewModel and repository tests.
- **2.6 Link-state screen:** five states and last sync, no controls. *Validate:* ViewModel tests; manual.
- **2.7 Dependency injection** per the 0b.1 decision. *Validate:* build and tests.

### 3. Watch core

- **3.1 Manifest and dependencies:** vibrate and notification permissions, `NO_BRIDGING`, `play-services-wearable`, an
  availability check. *Validate:* lint, build.
- **3.2 Link client:** publish `/hello/watch`, compute and display the link state on the home screen. *Validate:* tests
  with the fake; manual.
- **3.3 Schedule receive:** **Deferred (user, 2026-10-17).**  listener service for `/schedule`, local persistence, apply only newer versions.
  *Validate:* unit tests; manual.
- **3.4 Local scheduler:** **Deferred (user, 2026-10-17).**  research the right Wear OS mechanism and permissions first, then fire reminders, surviving
  reboot. *Validate:* tests for the logic; a 24-hour run with the screen off and the phone away in which every reminder
  fires within 1 minute of its scheduled time.
- **3.5 Haptics and notifications:** the catalogue, a notification channel, and the actions Done, Snooze, Start 2 min and
  Dismiss. *Validate:* manual on the watch.
- **3.6 Acknowledgements:** urgent `/ack/<id>`, snooze applied locally first. *Validate:* tests; manual.
- **3.7 Selections:** `/prompt/<id>`, selection screen, `/selection/<id>`, expiry. *Validate:* tests; manual.
- **3.8 Debug source set:** `Logger`, debug log view, injected-message receiver, raw logs. *Validate:* the
  release-has-no-debug check.
- **3.9 First end-to-end round trip** **Deferred (user, 2026-10-17).**  (schedule, reminder, acknowledgement, phone history) on paired emulators, then on
  the real devices with the phone present, away, and Bluetooth toggled. *Validate:* results in the work log.

### 4. Phone features

- **4.1** **Deferred (user, 2026-10-17).** Task and reminder create/edit screens (Compose Material 3). **4.2** **Deferred (user, 2026-10-17).** Schedule builder that publishes.
  **4.3** Settings (thresholds, cooldown, cadence). **4.4** Prompt publishing (offer options). **4.5** History of
  acknowledgements, selections and alerts. **4.6** Empty and error states and basic accessibility.
  *Validate each:* ViewModel tests, `check`, manual round trip.

### 5. Watch tile

- **5.1** Research the current tile API. **5.2** **Deferred (user, 2026-10-17).** Tile with the current reminder, the primary action and the link state.
  **5.3** **Deferred (user, 2026-10-17).** Update triggers. *Validate:* tests where possible; manual on the watch.

### 6. Biometric alerts

- **6.1 Research:** the Health Services routes (live and passive), permission names for API 36, and heart-rate support on the
  Galaxy Watch6 Classic (done; the measured result is in [`biometrics.md`](plans/biometrics.md) section 12.3). **6.2** Agree metrics and thresholds with the user in an issue. **6.3** Permission flow (C10).
  **6.4** Live measure service (C4), replaces "Passive listener service". **6.5** Threshold and cooldown evaluator fed continuously (C5; the evaluator exists).
  **6.6** `/alert/<id>` with the numeric value (unchanged). **6.7** Debug-only raw biometrics (unchanged). **6.8** Emulator synthetic data, then
  the real watch, with the E7 latency. *Validate each:* unit tests, then manual.

### 7. Play readiness

- **7.1** Signing and Play App Signing. **7.2** `versionCode` scheme. **7.3** Release build checks. **7.4** Privacy
  policy and the store declarations for health data (requirements researched first). **7.5** README for both devices.
  **7.6** Release checklist.

## 7. Verification

**A step is done when:** its Validation plan passes, `./gradlew check` and the docs lint are green in CI, and a
`docs/work-log.md` entry records the results.

| Layer | Check | Where |
|---|---|---|
| Formatting, static analysis, lint | Spotless with ktlint, detekt, Android lint (fail on new issues) | `./gradlew check`, CI |
| Pure logic (`:protocol`) | `./gradlew :protocol:test` | CI |
| Phone ViewModels, repositories and watch logic | Unit tests against the fake link | CI |
| Pairing | Build-time check: same `applicationId` and signing config | `check`, CI |
| Release builds | Contain no debug code | CI |
| Docs | markdownlint-cli2 and link check (internal blocking, external non-blocking) | CI and locally |
| **Round trip on paired emulators (required, first)** | A Wear OS emulator paired with a phone emulator (Device Manager, Pair Wearable): schedule, reminder, acknowledgement, history; listener wake-up with the phone app closed; deletion; disconnect and reconnect | The user, in Android Studio, from 0b.2a onward |
| Round trip on real devices | Galaxy Watch6 Classic with the Galaxy A55 5G, present, away, and Bluetooth toggled | The user, on their devices |
| Link states and delays | Nearby, Relayed and Away, and urgent-item delay: real hardware only | The user, on their devices |
| Biometric alerts | Emulator synthetic data (Wear OS 4+; `whs.*` broadcasts on older), then the real watch | Emulator, then the user |
| Haptics | Manual on the watch | The user, on their device |

**Where checks run:** CI is the authority before merge. Cloud sessions run `./gradlew check` and the other Gradle
checks before pushing, after running the bootstrap script (0a.8); developers run the same commands locally. Each issue's
Validation plan includes a short checklist for manual tests, written by the implementer; the user runs it and reports,
and the results go in the work log.

**Spike pass criteria (0b.2):** the emulator stage (0b.2a) must show the items, listener, deletion and reconnect
behaviour working before the real-hardware stage (0b.2b). Delay targets are agreed before 0b.2b runs.

## 8. Risks

| Risk | Impact | Handled by |
|---|---|---|
| AGP 9.4 and Kotlin 2.4 compatibility of Hilt, KSP, Room, Spotless, detekt | Tooling blocks CI or the phone data layer | 0a.5 and 0b.1 compatibility checks; on a conflict, stop and ask |
| DataItems persistence, deletion, listener wake-up with the phone app closed | Alerts or acknowledgements lost, or items pile up | 0b.2a emulator spike, then 0b.2b hardware; fallback rule (MessageClient for urgent alerts only) |
| Data Layer relay through Google's servers; numeric health data in alerts; no documented cost | Privacy exposure; unknown cost | Accepted by the user; end-to-end encrypted; recorded as "none found, unverified"; revisit if documentation changes |
| No documented connect/disconnect callback; slow reconnection under Doze | Stale link state | Periodic re-read; 3.2 and 0b.2b measure it |
| Reminder reliability on the watch under Doze, battery optimisation and exact-alarm rules | Core feature fails silently | 3.4 researches the mechanism first; 24-hour run, every reminder within 1 minute |
| Samsung battery management on the phone (sleeping or restricted apps) | The background listener stops storing acknowledgements and alerts | 0b.2b on the Galaxy A55 5G with default settings; any exemption or setup step decided with the user |
| Pairing through Samsung's Galaxy Wearable app instead of Google's Wear OS app | Data Layer behaves differently from the emulator pairing | 0b.2b on this exact phone and watch pair |
| Health Services: permission names for API 36 and heart-rate support on this watch (live route measured, passive route measured) | Biometric alerts impossible or different | Milestone 6 research first; thresholds agreed with the user |
| Samsung limits on third-party sensor access | Same | Milestone 6 research; capability check on the real watch |
| Emulators do not show Nearby, Relayed or Away, or Samsung behaviour | False confidence | The emulator stage is required but not sufficient; the hardware stage settles it |
| `allowBackup` could copy health-related data to the cloud | Privacy exposure | Off in both apps (0a.2) |
| Data Layer needs Google Play services on the phone | No-GMS phones unsupported | Out of scope |
| Watches paired with an iPhone | Unsupported | Out of scope |
| Scope: eight milestones, two apps | Delay | Milestone gating; each step validated before the next |

## 9. Resolved questions

| Question raised in the plans | Resolution |
|---|---|
| Which repo is the monorepo? | `my-adhd-pal`; `my-adhd-app` was removed |
| Module name for the phone app | `:phone` |
| Phone scope and internet | Full app; no internet and no backend in v1 |
| Phone receives watch items in the background? | Yes, a listener service |
| Transport | DataItems only; `MessageClient` only as the decided fallback |
| Google relay and cost | Accepted; no cost documented (unverified) |
| Alert content | Numeric value, threshold and time |
| Handshake | State items plus an Incompatible link state |
| Biometric API | Health Services live route (`MeasureClient`) while the app runs; the passive route is measured, not used for alerts |
| Spike timing and form | End of milestone 0, as 0b.2a (emulators, required) then 0b.2b (hardware) |
| Backups | `allowBackup` off |
| Reminder reliability | Every reminder within 1 minute over 24 hours |
| Cloud environment without the Android SDK | Bootstrap script, step 0a.8 |
| Issue granularity, tool conflicts, external links, outside-repo files | Section 5 |
