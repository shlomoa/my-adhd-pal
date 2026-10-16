# Lane D: Documentation (UI/UX design)

Overview, gates, stop rule and ownership: [README](README.md). Step ids D1.x belong to issue #154 (D1); D2 is issue #156 and D3 issue #157 (coordinator numbering). It is not a `plan.md` step: it
documents the screens of steps 2.6, 3.2-3.7, 4.1-4.6, 5.2 and H1. The design is a **proposal** until the user approves
it; only then is it the plan-on-record.

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **D1.1** | Lane setup: this plan, README rows, gate `G-DOC`, `docs/work-log/docs.md`, `lane:D` accepted by the scripts, catalogue `docs/test-plans/docs.md` (`TC-D-*`) and the red baseline `docs-baseline.md` | `scripts/test/test-design-docs.sh` red, `scripts/check-test-cases.sh docs baseline` | G-BOOT | none |
| **D1.2** | Images: each board of the design canvas rendered to `docs/design/images/<screen id>-<name>.png` (device scale 2), board sources in `docs/design/source/` | TC-D-003, TC-D-008 | G-BOOT | none |
| **D1.3** | `docs/design/README.md` (overview and table of contents), four section files, `solution.md` (data flow and event flow, Mermaid, each described); links from `docs/plan.md` and `README.md`; `test-design-docs.sh` joins CI | TC-D-001..007, 009; doc lint and link check green | G-BOOT | none |
| **D1.4 (V-last)** | Run everything, `docs-final.md` via `scripts/check-test-cases.sh docs final`; the user reads `docs/design/` and approves it (recorded in issue #154) | all `TC-D-*` PASS; approval by the user, recorded in issue #154 | G-BOOT | **G-DOC** (after the user approves) |
| **D2** | Issue #156: `docs/index.md` as the single entry point linking every document, documentation site generator (tool proposal and decisions in the issue body) built in CI; `TC-D-*` ids added in its own red-first PR | per issue #156; approval step: the user approves the structure of the index and the built site (recorded in the issue body) | G-DOC | **G-INDEX** |
| **D3** | Issue #157: markdown lint and format checks cover all central documentation and run in CI; `TC-D-*` ids added in its own red-first PR | per issue #157 | G-BOOT | none |

## Sections (from issue #154)

| Section file | Screens |
|---|---|
| `phone-main-flow.md` | P1 Watch tab, P2 Tasks, P3 Task edit, P4 Reminder edit, P5 Schedule, P6 History |
| `phone-settings-prompts-alerts.md` | P7 Settings, P8 Ask your watch, P9 Allow heart rate, P10-P11 Phone alert levels 1 and 2, P12 Loading/empty/error, P13 Five link states |
| `watch-main-flow.md` | W1 Home, W2 Link states, W3 Reminder, W4 Choice |
| `watch-tile-alerts.md` | W5-W6 Tile (with and without a reminder), W7-W8 Heart-rate alert levels 1 and 2, W9 Allow heart rate |
| `solution.md` | data flow and event flow, derived from [`../protocol.md`](../protocol.md), [`../data-layer.md`](../data-layer.md) and [`biometrics.md`](biometrics.md) |

## Rules

- Decisions and status live in the issue body, never in issue comments.
- Wording is "proposed design". The tile's primary action is undecided (Done is a placeholder). Settings opens from the
  gear on the Watch tab.
- Nothing is invented: behaviour in the diagrams comes from the protocol, data-layer and biometrics documents.
- Only lane D edits `docs/design/**`. Lane D touched `scripts/**` in D1.1 only (coordinator authorization), minimally.
