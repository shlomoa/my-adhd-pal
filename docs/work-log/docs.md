# Work log: documentation lane (D)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-16 - D1 Lane setup

- Issue and PR: #154; PR `claude/docs-lane-setup` (linked from the coordinator message).
- Changed: `docs/plans/docs.md`, lane D rows in `docs/plans/README.md`, gate `G-DOC` in `gates.json`,
  `docs/test-plans/docs.md` (TC-D-001..013), `scripts/test/test-design-docs.sh`, `lane:D` accepted by
  `scripts/check-issues.sh` and the `Conventions` workflow checks this log. Minimal `scripts/**` edit, authorized by the
  coordinator for this PR only.
- Validation: `scripts/require-gate.sh G-BOOT` exited 0. Tests first: red baseline `docs/test-reports/docs-baseline.md`
  (commit 4e6de0e). `test-design-docs.sh` is not yet in CI because its content cases stay red until D2 and D3.
- Open items: D2 images, D3 sections and solution, D4 final run and user approval.

## 2026-10-16 - D2 Design images

- Issue and PR: #154; PR `claude/docs-images` (stacked on D1, PR 155).
- Changed: 22 screen PNGs (P1-P13, W1-W9) and `overview-screens.png` in `docs/design/images/`, board sources,
  `ui.css`, `canvas.json` and `render.mjs` in `docs/design/source/`, rendered from the published canvas.
- Validation: `scripts/require-gate.sh G-BOOT` exited 0. TC-D-003 and TC-D-008 PASS in
  `scripts/test/test-design-docs.sh`; the other content cases stay red until D3. Docs lint (markdown, links) green.
- Open items: the Atkinson Hyperlegible font was unreachable, so images use the fallback font. D3 sections and solution.

## 2026-10-16 - D3 Design sections and solution

- Issue and PR: #154; PR `claude/docs-sections` (stacked on D2).
- Changed: `docs/design/README.md` (overview, table of contents), `phone-main-flow.md`,
  `phone-settings-prompts-alerts.md`, `watch-main-flow.md`, `watch-tile-alerts.md`, `solution.md` (data flow and event
  flow diagrams with descriptions, derived from protocol, data layer and biometrics documents); links from `README.md`
  and `docs/plan.md`; `test-design-docs.sh` runs in the `script-tests` CI job.
- Validation: `scripts/test/test-design-docs.sh` TC-D-001..010 PASS, docs lint (markdown, links) green.
- Open items: canvas says 2 to 4 prompt options, the data-layer contract 2 to 6; the tile primary action is undecided.
  D4 final run and the user's approval (recorded in issue #154).

## 2026-10-16 - D1.3 Rename of lane D steps

- Issue and PR: #154; coordinator message (naming clash): D1 #154 design, D2 #156 index, D3 #157 lint.
- Changed: plan steps renamed D1.1 setup, D1.2 images, D1.3 sections, D1.4 final; rows D2 and D3 and gate G-INDEX
  (D2, requires G-DOC) added to `docs/plans/docs.md`, `docs/plans/README.md` and `gates.json`. Entries above keep the
  step names they were written with (D1, D2, D3 mean D1.1, D1.2, D1.3).
- Validation: `scripts/test/test-gates.sh` and `scripts/check-work-log.sh docs/work-log/docs.md`.
- Open items: TC-D ids for issues #156 and #157 come in their own red-first PRs after D1.

## 2026-10-16 - D1.4 Final run

- Issue and PR: #154; PR `claude/docs-final`.
- Changed: `docs/test-reports/docs-final.md` with TC-D-001..012 PASS on `main` (cf0b097).
- Validation: `scripts/test/test-design-docs.sh`, `test-conventions.sh`, `test-gates.sh`, docs lint (markdown, links), all green
  locally. `check-test-cases.sh docs final` needs every id PASS in the report.
- Open items: the user reads `docs/design/` and approves or asks for changes (approval by the user, recorded in issue #154); the decision goes in the issue
  body. Gate G-DOC flips only after that. D2 (#156) and D3 (#157) follow.

## 2026-10-16 - D3 Documentation lint and format checks (issue #157)

- Issue and PR: #157; PR `claude/docs-checks`. Gap list and the option B proposal are in the issue body.
- Changed: `scripts/docs-lint.sh` has `lint`, `format` and `fix` modes next to `markdown` (which runs both, so Gradle and CI
  are unchanged); `scripts/test/test-doc-checks.sh` (TC-D-014..020, negative fixtures) runs in the `script-tests` job;
  `docs/test-plans/docs.md`, `docs/test-reports/docs-checks-baseline.md` and `docs-final.md` updated. No document was
  rewritten. `scripts/**` edited because the issue is about those checks (coordinator asked to start D3).
- Validation: red baseline commit b2c6ca2, then `scripts/test/test-doc-checks.sh` all PASS, `docs-lint.sh markdown` and
  `links` green. Gradle-based checks (TC-S-015, TC-S-016) cannot run in this cloud session (no Android SDK); CI runs them.
- Open items: the user may switch the format check to option A (Prettier, would rewrite 28 files) in the issue body.

## 2026-10-16 - D1.4 Approval is not a test case

- Issue and PR: #154; PR `claude/docs-approval-step`. User rule relayed by the coordinator: a human approval is never a test case.
- Changed: removed TC-D-013 from `docs/test-plans/docs.md`, `docs/test-reports/docs-baseline.md`, `docs-final.md`,
  `scripts/test/test-design-docs.sh` and the plan row of D1.4; the approval is described as approval by the user, recorded in
  issue #154. `docs-final.md` lists each case as `TC-D-nnn PASS` so `check-test-cases.sh docs final` can read it. Other ids are not renumbered.
- Validation: `scripts/check-test-cases.sh docs final` and `baseline` exit 0, `scripts/test/test-design-docs.sh` 10 PASS,
  docs lint (markdown, links) green.
- Open items: G-DOC flips only after the user approves the design (recorded in issue #154).
