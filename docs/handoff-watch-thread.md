# Hand-off: watch-side decisions for the ADHD Android app architecture plan

Status: input document for the thread that owns the combined plan ("ADHD Android app architecture"). It is
self-contained: that thread has none of the context in which these decisions were made.

Scope of this document: decisions, research findings and working rules gathered while planning the Wear OS side. It
contains no code and does not change the build.

## A. Facts already on `main`

- JDK 21, Gradle 9.8.1 wrapper (distribution checksum pinned), AGP 9.4.1, Kotlin 2.4.21. CI
  (`.github/workflows/build.yml`) builds with Temurin 21 (PR #2).
- `.gitignore` expanded for Android/Gradle/Kotlin (PR #3).
- The `my-adhd-app` repo was removed by the user. The monorepo is `my-adhd-pal`, which answers the "which repo" question.
- Module name: use `:phone` (the architecture thread's name), not `:mobile`.

## B. Decisions made by the user

- Watch needs: time blindness, getting started, forgetting things, impulsivity and emotions.
- Watch role:
  - **Reminders fired locally on the watch** from a schedule synced by the phone, so they work with the phone away.
  - Haptics from one catalogue (tick, warning, calm-breath) and a glanceable tile.
  - **Acknowledgements**: Done, Snooze, Start 2 min, Dismiss.
  - **Simple selections**: the phone offers options, the user taps one on the watch.
  - **Alerts from biometrics with no user interaction**.
- Biometrics: **official Health Services only** (`measureClient`, `DataType.HEART_RATE_BPM`, `BODY_SENSORS` runtime
  permission, as in the Android fitness guide). Not verified: measure client versus passive monitoring for always-on
  alerts, and the current permission names. Research before coding. Metrics and thresholds are agreed with the user in
  that milestone's issue.
- Phone app: native Kotlin, calling the Data Layer directly. No Capacitor, Angular or third-party watch plugins.
  Scope: the full app of the architecture thread.
- No backend for now.
- **Phone connection state is shown to the user on the watch**: connected (`Node.isNearby`), relayed (reachable but not
  nearby), away. Use a `CapabilityClient` listener plus `getCapability(FILTER_REACHABLE)`. The docs name no dedicated
  connect or disconnect callback, so also re-read the state on app start and periodically. Verify the behaviour on the
  real watch (Galaxy Watch6 Classic) and record it in `docs/protocol.md`. Do not use `BluetoothDevice` ACL broadcasts
  (unverified on Wear OS and Bluetooth-specific).
- **Development versus production**: verbose logs, raw biometrics, an injected-message receiver and a debug log view exist
  in debug builds only (`wear/src/debug/`). Release builds must contain none of them, enforced by a CI check. Raw
  biometrics are health data and are never logged in release.
- Standalone flag: keep `com.google.android.wearable.standalone=true`, since the watch works without the phone because it
  fires reminders locally. Set the notification bridge mode to `NO_BRIDGING` to avoid duplicate notifications.

## C. Open decision for the user (the owning thread must ask)

**Transport.** The user supplied three Google pages as the basis for communications (Data Layer overview, DataItems,
network communication) and said the previously defined Bluetooth-based solution should be deleted.

| Option | Meaning | Notes |
|---|---|---|
| DataItems only (recommended by the watch thread) | All traffic as items at paths: `/schedule`, `/prompt/<id>`, `/ack/<id>`, `/selection/<id>`, `/alert/<id>`. `setUrgent()` only where delay hurts. Items are buffered while disconnected. | Simplest. Matches the pages and the Android fitness guide. Needs verification that items persist and can be read and deleted later. |
| Items + MessageClient + ChannelClient (the architecture thread's plan) | Messages for events and RPC, items for state, channels for bulk. | More machinery. Messages are lost while disconnected. Drop channels unless bulk transfer is really needed. |

Neither option avoids the Data Layer's own transport: Google Play services uses Bluetooth when connected and routes
through Google's servers (end-to-end encrypted) when not. The app cannot choose or disable this, and no cost, quota or
billing is documented (none found, unverified). Direct Wi-Fi between watch and phone was rejected by the user because it
is not always available. FCM and HTTP need a server, and there is none.

`docs/protocol.md` is the single source of truth for the protocol and must be backed by constants and models in the
`:protocol` module.

## D. Sources

- [Data Layer overview](https://developer.android.com/training/wearables/data/overview): same package name and signing
  certificate on both apps; Bluetooth, else relay through Google's servers; use only for strictly local watch and phone
  interaction.
- [DataItems](https://developer.android.com/training/wearables/data/data-items): 100 KB payload, non-urgent sync may be
  delayed up to 30 minutes, `setUrgent()`, buffered while disconnected. The deletion API is not documented there.
- [Network communication](https://developer.android.com/training/wearables/data/network-communication): the watch's traffic
  is proxied through the phone when connected, FCM is the recommended way to notify a watch, and the Data Layer is not a
  network channel.
- [Fitness integration](https://developer.android.com/health-and-fitness/fitness/basic-app/integrate-wear-os): Health
  Services on the watch, urgent `putDataItem`, a phone `WearableListenerService`, and emulator synthetic data through
  `whs.*` broadcasts.
- [Discover devices](https://developer.android.com/training/wearables/data/discover-devices) and
  [events](https://developer.android.com/training/wearables/data/events): reachable versus nearby, and the documented
  callbacks.
- [Notifications on Wear OS](https://developer.android.com/wear/notifications/creating): bridging and `NO_BRIDGING`.

## E. The user's working rules

- KISS. DRY and a single source of truth. Stop and ask on medium or high ambiguity. Use the latest package versions after
  web research. No trial and error. Nothing written outside the repo.
- Work is reported in log files (`docs/work-log.md`) and managed with GitHub issues and pull requests.
- **Every task and step starts with a validation plan, then implementation.** Each issue has a Validation plan (what is
  checked, how: automated, CI or manual, and the pass criteria). The pull request repeats it with results.
- **Formatting and linting are part of validation and CI**: Spotless with ktlint, detekt, Android lint (failing on new
  issues), and markdownlint-cli2 with a link check for docs. They are defined once in the Gradle build behind
  `./gradlew check`, and CI calls the same commands instead of listing the tools itself. Set them up in the first
  milestone, and validate them by showing a mis-formatted change fail.
- Release builds contain no debug code (CI check). Both apps share the application ID and signing config (build-time
  check).

## F. Parts of the architecture thread's plan to keep

Two-layer phone architecture (UI layer and data layer, no domain layer), Room and DataStore, `WatchLinkDataSource` behind
an interface with a fake for tests, Hilt (verify AGP 9 and KSP compatibility first), version handshake with a
compatibility rule, and Mermaid sequence diagrams in `docs/protocol.md`. Only the transport (section C) and the process
rules (section E) need reconciling.
