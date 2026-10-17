# Lane W: Watch app

Overview, gates, stop rule and ownership: [README](README.md). Step ids are from [`../plan.md`](../plan.md).

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **W1a** | Catalogue `docs/test-plans/watch.md` (`TC-W-*`): link state on home screen (Nearby, Relayed, Away, Incompatible) + last sync, schedule applied only if newer, reminders fire within 1 minute over 24 hours with screen off and phone away, survive reboot, haptic catalogue, notification actions (Done, Snooze, Start 2 min, Dismiss), snooze applied locally then reported, selection with expiry, `NO_BRIDGING`, debug/release separation, tile content, biometric threshold + cooldown evaluator | docs only | G-BOOT | none |
| **W1b** | Research notes (no code): reminder mechanism and permissions (3.4), current tile API (5.1), Health Services passive monitoring, API 36 permission names and heart-rate support on Galaxy Watch6 Classic (6.1) | n/a, sources cited | G-BOOT | none |
| **W1c** | Generate tests against the protocol and fakes; run; record red in `watch-baseline.md` | all `TC-W-*` red | G1, G-ENV | none |
| **3.1** | Manifest and dependencies: vibrate and notification permissions, `NO_BRIDGING`, `play-services-wearable`, availability check | lint, build | G0 | none |
| **3.8** | Debug source set: `Logger`, debug log view, injected-message receiver, raw logs | release-has-no-debug check red on a deliberate leak, green on the real build | G0 | none |
| **3.2-3.3** | Link client (`/hello/watch`, link state on the home screen); 3.3 is **Deferred (user, 2026-10-17).** Schedule listener service, local persistence, apply only newer versions | tests with the fake transport red -> green; manual | G2a | none |
| **3.4** | **Deferred (user, 2026-10-17).** Local scheduler per the W1b mechanism, surviving reboot | logic tests with fixed clock; 24-hour screen-off, phone-away run: every reminder within 1 minute (user, ■H2) | G2a | none |
| **3.5-3.7** | Haptics and notification actions; urgent `/ack/<id>` with local snooze first; `/prompt/<id>`, selection screen, `/selection/<id>`, expiry | tests against the fake; manual haptics on the watch | G2a | **G5** (with 3.1-3.4) |
| **3.9** | **Deferred (user, 2026-10-17).** End-to-end round trip (schedule, reminder, ack, phone history): paired emulators first, then real devices with the phone present, away and Bluetooth toggled | results in the work log; user-run | G3, G4, G5, H2 | **G7** |
| **5.2-5.3** | **Deferred (user, 2026-10-17).** Tile: current reminder, primary action, link state; update triggers | tests where possible; manual | G5 | none |
| **6.2** | Agree biometric metrics and thresholds with the user in an issue | decision recorded in the issue (a repository document of it is separate and does not block H1) | none (user) | **H1** |
| **6.3-6.8** | Permission flow (6.3, C10); live measure service (6.4, C4; replaces the passive listener service); threshold + cooldown evaluator as pure logic in `:protocol` (`alerts/`); `/alert/<id>` with numeric value; debug-only raw biometrics; emulator synthetic data then the real watch | evaluator unit tests red -> green first; raw biometrics never logged in release; manual | H1, G2b, 3.8 | none |
| **W-final (V-last)** | `:wear:check`, all `TC-W-*` PASS, final report, manual checklist | `watch-final.md` via `check-test-cases.sh watch final` | G5, G7, 5.2-5.3, 6.8 | none |

## Live route work (user-approved plan, 2026-10-17)

Experiments: results are recorded in [`biometrics.md`](biometrics.md) section 12.3 and the watch probe checklist, reported by the user; they have no test-case id. Changes: tests first, per the lane rules.

| ID | Type | What | Depends on | Lane |
|---|---|---|---|---|
| **E1** | Experiment | Live route, worn: screen on 10 min untouched ("step 25"), dim/always-on 10 min, screen off 10 min; battery drop; notification visible; cause of the 60 s gap | build of #210 installed | user, W |
| **E2** | Change | Record the E1 result against the user's definition (measured: does the live route meet "continuously" and "immediately", per screen state). No user decision is asked: the definition decides. If it fails, the failing measurement is reported to the user as an item needing attention | E1 | D, W |
| **E3** | Experiment | Service started from the visible app without the background permission; survives 30 min; effect of Bedtime and battery saver | E1 | user, W |
| **E4** | Experiment | App stays top app and window: ambient behaviour, screen timeout, keep-screen-on | E1 | user, W |
| **E5** | Experiment | Steps and activity state on the live route (replaces the passive step probe, biometrics.md section 10) | E2 | user, W |
| **E6** | Experiment | Do Not Disturb routes (notify-alarm, alarm-clock), Bedtime, Theater, vibration intensity (unchanged, still open) | none | user, W |
| **E7** | Experiment | End-to-end urgent alert latency, watch to phone: time from high reading to phone alert, repeated; fills the 'alert to phone' row of the latency table | C4, C5 | user, W, A |
| **C4** | Change | Live measure foreground service (MeasureClient, type health, ongoing notification) replaces the passive listener (6.4); home screen 'Monitoring' as the always-visible top window | E2, E3, E4 | W |
| **C5** | Change | Evaluator fed continuously by live readings; local response unchanged (EpisodeEvaluator, BaselineTracker stay) | C4 | W |
| **C10** | Change | Permission flow: drop the background-permission prompt if E3 shows it is not needed; phone onboarding text | E3, C4 | W, A |
