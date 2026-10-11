# Lane A: Android (phone app)

Overview, gates, stop rule and ownership: [README](README.md).


| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **A1a** | Catalogue `docs/test-plans/android.md` (`TC-A-*`): link-state screen for connected / relayed / away / watch-app-missing, schedule create/edit/delete -> sync request, history of acks and selections, selection offer, process death restore, Play-readiness checks (versionCode scheme, signing) | docs only | G-BOOT | none |
| **A1b** | Generate ViewModel and UI-state tests against the contract; run; record red in `android-baseline.md` | all `TC-A-*` red | G0, G1 | none |
| **A2** | App skeleton in `:phone`: `MainActivity`, theme, navigation, DI per S1b's Hilt-vs-manual decision | skeleton tests (app starts, nav graph) red -> green | G0, S1b result | none |
| **A3** | Link-state ViewModel + screen driven by `FakeWatchLinkDataSource` | link cases red -> green | G2 | none |
| **A4** | Schedule list/editor ViewModels + screens over repositories | schedule cases red -> green | G3 | none |
| **A5** | Prompt/ack/selection history, selection offering; merge-ready bundle | history cases red -> green | G3 | **G5** |
| **A6** | Play readiness: signing and Play App Signing, versionCode scheme, release checklist, `docs/phone.md`, README for both devices | release checklist; `assembleRelease`; G4 checks green | G4, G6 | none |
| **A7 (V-last)** | Full `:phone:check`, all `TC-A-*` green, final report; manual end-to-end with the real watch (schedule created on phone fires on watch; ack arrives; away -> reconnect sync) | `android-final.md`; manual checklist signed | G5, G6, H2 | none |

