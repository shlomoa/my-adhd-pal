# Lane A: Android (phone app)

Overview, gates, stop rule and ownership: [README](README.md). Step ids are from [`../plan.md`](../plan.md).

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **A1a** | Catalogue `docs/test-plans/android.md` (`TC-A-*`): link-state screen for Nearby / Relayed / Away / No watch app / Incompatible plus last sync (no controls), DI sample, task and reminder create/edit/validate, schedule builder publishes a versioned `/schedule`, settings (thresholds, cooldown, cadence), prompt publishing, history of acks/selections/alerts, empty and error states, accessibility labels, release checklist items | docs only | G-BOOT | none |
| **A1b** | Generate ViewModel/UI-state tests against the data contract and fakes; run; record red in `android-baseline.md` | all `TC-A-*` red | G3a, G-ENV | none |
| **2.7** | Dependency injection per the 0b.1 decision | build and tests red -> green | G-LIB, G0 | none |
| **2.6** | Link-state screen: five states and last sync, no controls | ViewModel tests red -> green; manual | G3a | none |
| **4.5** | History of acknowledgements, selections and alerts | ViewModel tests red -> green | G3a | **G4** (with 2.7 and 2.6) |
| **4.1-4.4** | Task/reminder screens (Compose Material 3), schedule builder that publishes, settings, prompt publishing | ViewModel tests, `check`, manual round trip | G4, G3 | none |
| **4.6** | Empty and error states, basic accessibility | tests, `check`, manual | G4 | **G6** (with 4.1-4.4) |
| **7.1-7.6** | Signing and Play App Signing, `versionCode` scheme, release build checks, privacy policy and health-data store declarations (requirements researched first), README for both devices, release checklist | `assembleRelease`; pairing and release-no-debug checks green; checklist reviewed | G6, G7 | none |
| **A-final (V-last)** | `:phone:check`, all `TC-A-*` PASS, final report; manual end-to-end with the real watch | `android-final.md` via `check-test-cases.sh android final`; manual checklist reported by the user | G6, G7, H2 | none |
