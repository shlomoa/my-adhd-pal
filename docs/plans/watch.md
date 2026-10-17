# Lane W: Watch app

Overview, gates, stop rule and ownership: [README](README.md). Step ids are from [`../plan.md`](../plan.md).

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **W1a** | Catalogue `docs/test-plans/watch.md` (`TC-W-*`): link state on home screen (Nearby, Relayed, Away, Incompatible) + last sync, schedule applied only if newer, reminders fire within 1 minute over 24 hours with screen off and phone away, survive reboot, haptic catalogue, notification actions (Done, Snooze, Start 2 min, Dismiss), snooze applied locally then reported, selection with expiry, `NO_BRIDGING`, debug/release separation, tile content, biometric threshold + cooldown evaluator | docs only | G-BOOT | none |
| **W1b** | Research notes (no code): reminder mechanism and permissions (3.4), current tile API (5.1), Health Services passive monitoring, API 36 permission names and heart-rate support on Galaxy Watch6 Classic (6.1) | n/a, sources cited | G-BOOT | none |
| **W1c** | Generate tests against the protocol and fakes; run; record red in `watch-baseline.md` | all `TC-W-*` red | G1, G-ENV | none |
| **3.1** | Manifest and dependencies: vibrate and notification permissions, `NO_BRIDGING`, `play-services-wearable`, availability check | lint, build | G0 | none |
| **3.8** | Debug source set: `Logger`, debug log view, injected-message receiver, raw logs | release-has-no-debug check red on a deliberate leak, green on the real build | G0 | none |
| **3.2-3.3** | Link client (`/hello/watch`, link state on the home screen), schedule listener service, local persistence, apply only newer versions | tests with the fake transport red -> green; manual | G2a | none |
| **3.4** | Local scheduler per the W1b mechanism, surviving reboot | logic tests with fixed clock; 24-hour screen-off, phone-away run: every reminder within 1 minute (user, ■H2) | G2a | none |
| **3.5-3.7** | Haptics and notification actions; urgent `/ack/<id>` with local snooze first; `/prompt/<id>`, selection screen, `/selection/<id>`, expiry | tests against the fake; manual haptics on the watch | G2a | **G5** (with 3.1-3.4) |
| **3.9** | End-to-end round trip (schedule, reminder, ack, phone history): paired emulators first, then real devices with the phone present, away and Bluetooth toggled | results in the work log; user-run | G3, G4, G5, H2 | **G7** |
| **5.2-5.3** | Tile: current reminder, primary action, link state; update triggers | tests where possible; manual | G5 | none |
| **6.2** | Agree biometric metrics and thresholds with the user in an issue | decision recorded in the issue (a repository document of it is separate and does not block H1) | none (user) | **H1** |
| **6.3-6.8** | Permission flow; passive listener service; threshold + cooldown evaluator as pure logic in `:protocol` (`alerts/`); `/alert/<id>` with numeric value; debug-only raw biometrics; emulator synthetic data then the real watch | evaluator unit tests red -> green first; raw biometrics never logged in release; manual | H1, G2b, 3.8 | none |
| **W-final (V-last)** | `:wear:check`, all `TC-W-*` PASS, final report, manual checklist | `watch-final.md` via `check-test-cases.sh watch final` | G5, G7, 5.2-5.3, 6.8 | none |
