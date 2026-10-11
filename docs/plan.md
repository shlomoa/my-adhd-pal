# Plan: ADHD phone + watch monorepo

Status: **proposed**, for review in the plan PR. No code changes accompany this document.

## Context

`my-adhd-pal` is a Wear OS app (Kotlin + Compose, single `:app` module, `com.shlomoa.myadhdpal`, minSdk 30,
targetSdk 36, no permissions, `com.google.android.wearable.standalone=true`). `main` has JDK 21, Gradle 9.8.1
(checksum pinned), AGP 9.4.1, Kotlin 2.4.21, CI on Temurin 21.

Goal: a native Kotlin phone app (full app, no backend for now) and the watch app in one Gradle multi-module
project, linked over the Wearable Data Layer with a documented protocol, distributed on Google Play.

## Decisions (settled)

- **Monorepo in `my-adhd-pal`** with modules `:protocol` (pure Kotlin/JVM), `:wear` (rename of `:app`) and
  `:phone` (new).
- **Same `applicationId` (`com.shlomoa.myadhdpal`) and same signing config** for phone and watch, as the Data
  Layer requires; enforced by a build-time check.
- **Phone app**: native Kotlin, calls the Data Layer directly (no Capacitor, Angular or third-party watch
  plugins). Two layers (UI + data), no domain layer, per Google's architecture recommendations. Room +
  DataStore. A `WatchLinkDataSource` interface with a fake for tests. Hilt if compatible with AGP 9 / KSP
  (verify first, otherwise manual DI). **No backend** for now; the phone holds the data.
- **Watch role** (needs: time blindness, getting started, forgetting, impulsivity/emotions):
  - reminders fired **locally on the watch** from a schedule synced by the phone (works with the phone away);
  - haptic catalogue (tick, warning, calm-breath);
  - glanceable tile;
  - acknowledgements (Done / Snooze / Start 2 min / Dismiss);
  - simple selections (the phone offers options, the user taps one);
  - alerts from biometrics with no user interaction.
- **Standalone flag stays `true`**; notification bridging is set to `NO_BRIDGING` to avoid duplicates.
- **Phone connection state is shown on the watch**: connected (`Node.isNearby`), relayed (reachable, not
  nearby), away. Implemented with a `CapabilityClient` listener and `getCapability(FILTER_REACHABLE)`, plus a
  re-read on start and periodically (no connect/disconnect callback is documented). `BluetoothDevice` ACL
  broadcasts are not used. To be verified on a real Galaxy Watch6 Classic and recorded in `docs/protocol.md`.
- **Biometrics**: official Health Services only (`measureClient` / `DataType.HEART_RATE_BPM`, `BODY_SENSORS`
  runtime permission). Measure vs passive monitoring for always-on alerts and the current permission names are
  **unverified and must be researched before coding**. Metrics and thresholds are agreed in their own issue.
- **Dev vs production**: verbose logs, raw biometrics, an injected-message receiver and a debug log view live
  in `wear/src/debug/` only. Release builds contain none of it (CI check). Raw biometrics are never logged in
  release.
- **Pairing** is done by the OS; the app only observes capabilities. The authentication boundary is the same
  package name + signing certificate, plus a `HELLO` / `HELLO_ACK` version handshake (an incompatible major
  version yields `ERROR(UNSUPPORTED_VERSION)`).
- The watch must work fully without the phone; sync resumes on reconnect.

## Open decision: transport

Basis: the Data Layer overview, DataItems and network-communication pages. The Data Layer uses Bluetooth when
connected and Google's servers (end-to-end encrypted) when not; the app cannot choose this, and no cost or
quota is documented (unverified). Direct Wi-Fi was rejected; FCM/HTTP would need a server.

| Option | Shape |
|---|---|
| **DataItems only (recommended)** | Everything as items at paths `/schedule`, `/prompt/<id>`, `/ack/<id>`, `/selection/<id>`, `/alert/<id>`. `setUrgent()` only where delay hurts. Buffered while disconnected. Simplest; matches the Google pages. To verify: persistence, readability and deletion of items. |
| Items + MessageClient (+ Channel if bulk is needed) | Messages for events and request/response; lost while disconnected; more machinery. |

Milestone 0 and the protocol spec skeleton do not depend on this choice. It is fixed in `docs/protocol.md`
once decided, optionally after a small spike on the real watch.

## Protocol

`docs/protocol.md` is the single source of truth; `:protocol` holds matching constants and models.

- Envelope `{ v, id, corr?, type, ts, seq?, body }` (kotlinx.serialization JSON, codec isolated). Payload
  limit about 100 KB, asserted.
- The phone is the data master (schedule, settings, history). The watch is a capture and nudge client; its
  writes (acknowledgements, selections) flow to the phone and never edit master state directly.
- A master protocol is defined per communication type: session/handshake, schedule sync (last-writer-wins by
  version), prompt to acknowledgement/selection (idempotent by id, retry with backoff), alert (urgent) and
  link state. The API mapping follows the transport decision.
- The spec will contain a message catalog, Mermaid sequence diagrams, link and request state machines, error
  codes and a versioning policy.

## Working rules

- KISS; DRY and single source of truth; ask on medium or high ambiguity; latest package versions after
  research; no trial and error.
- Work is logged in `docs/work-log.md` and managed with GitHub issues and pull requests.
- **Every task starts with a validation plan, then implementation.** Each issue has a *Validation plan*
  (what, how: automated / CI / manual, pass criteria); the PR repeats it with results.
- **Formatting and linting are part of validation and CI**: Spotless + ktlint, detekt, Android lint (fail on
  new issues), markdownlint-cli2 and a link check for docs. Defined once in Gradle behind `./gradlew check`
  and called by CI. Validated by showing a mis-formatted change fail.
- Release builds contain no debug code (CI check).

## Milestones (after this plan is approved)

0. Tooling and restructure: rename `:app` to `:wear`, add `:protocol` and `:phone` skeletons, version
   catalog, `./gradlew check` with Spotless/ktlint/detekt/lint/markdownlint, CI updated (APK path
   `wear/build/...`), shared applicationId/signing check, release-has-no-debug check.
1. `docs/protocol.md` + `:protocol`: envelope, catalog, codec, state machines, fake transport, unit tests.
2. Phone data layer and UI skeleton: `WatchLinkDataSource` (real + fake), repositories, Room/DataStore,
   link-state screen.
3. Watch: link client, local reminder scheduling from the synced schedule, haptics,
   acknowledgements/selections, connection-state display, tile, debug source set.
4. Biometric alerts, after research and agreed thresholds.
5. Play readiness: signing and Play App Signing, versionCode scheme, release checklist, README for both
   devices.

## Verification

- This PR: documentation only; markdownlint and link check pass (manually until the tooling exists).
- Later: `./gradlew :protocol:test` (pure JVM); `./gradlew check` and
  `assembleDebug testDebugUnitTest lintDebug` in CI; ViewModel and repository tests against the fake link;
  manual test on a Galaxy Watch6 Classic (SM-R965F) with the phone present, away and Bluetooth toggled.

## Risks

- AGP 9.4 / Kotlin 2.4 compatibility of Hilt, KSP, Room, Spotless and detekt.
- Health Services permission and measurement behaviour for always-on alerts (unverified).
- The Data Layer needs Google Play services on the phone; non-GMS phones are out of scope.
