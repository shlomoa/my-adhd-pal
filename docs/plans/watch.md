# Lane W: Watch app

Overview, gates, stop rule and ownership: [README](README.md).


| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **W1a** | Catalogue `docs/test-plans/watch.md` (`TC-W-*`): local reminder fires with phone away, reschedule on schedule update, snooze / Start 2 min / dismiss idempotent, restore after reboot, haptic catalogue (tick, warning, calm-breath), tile content, ack/selection UI state, connection mapping (nearby -> connected, reachable-not-nearby -> relayed, none -> away), `NO_BRIDGING`, debug/release separation, biometric evaluator | docs only | G-BOOT | none |
| **W1b** | Research notes: exact-alarm approach and permissions, Compose UI test route on JVM, Health Services (`measureClient` vs passive monitoring, `BODY_SENSORS` and current permission names), Data Layer limits | n/a (research; cites sources) | G-BOOT | none |
| **W1c** | Generate tests; run; record red in `watch-baseline.md` | all `TC-W-*` red | G0, G1 | none |
| **W2** | Reminder engine: local scheduling from the synced schedule, boot restore, `NO_BRIDGING` manifest flag; fake clock in tests | engine cases red -> green | G1, G0 | none |
| **W3** | Haptics catalogue, prompt screen, ack (Done/Snooze/Start 2 min/Dismiss) and simple selections, emitted through `LinkTransport` | UI-state and ack cases red -> green (against `FakeTransport`) | G2 | none |
| **W4** | Link client: `HELLO`/`HELLO_ACK`, schedule receive, ack/selection send with retry, connection-state display (capability listener + periodic re-read) | link cases red -> green | G2 | **G6** |
| **W5** | Glanceable tile | tile cases red -> green | W4 (own lane) | none |
| **W6** | Debug source set `wear/src/debug/` (verbose logs, raw biometrics, injected-message receiver, log view); release contains none | G4's check red on a deliberate leak, green on the real build | G4 | none |
| **W7** | Real-watch test on Galaxy Watch6 Classic: phone present, away, Bluetooth toggled; record connect/relay/away behaviour in `docs/protocol.md` | manual checklist signed; failure feeds the `G1.1` revision | G3, G5, H2 | none |
| **W8a** | Biometric alerts, test-first: threshold evaluator unit tests (pure logic) red -> green, then Health Services wiring | evaluator cases green; raw biometrics never logged in release | H1, W6 | none |
| **W9 (V-last)** | Full `:wear:check`, all `TC-W-*` green, final report, manual checklist | `watch-final.md` | G3, G5, W7, W8a | none |

