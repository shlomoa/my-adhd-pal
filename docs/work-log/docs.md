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

## 2026-10-16 - D2 Documentation index and generated site (issue #156)

- Issue and PR: #156; PR `claude/docs-site`. The user accepted option A (MkDocs with the Material theme) in the issue body.
- Changed: `docs/index.md` (sections, links to every document), `mkdocs.yml`, `tools/docs-site/requirements.txt` (exact pins),
  `scripts/docs-site.sh` (strict build into `build/docs-site`), `scripts/check-docs-index.sh` (no orphan), a `docs-site` CI job
  and `test-docs-site.sh` in `script-tests`; the two links in `docs/demo.md` and `docs/test-reports/watch-e2e-checklist.md`
  that left `docs/` now point to the GitHub URL of `scripts/demo/run-demo.ps1` (a strict build treats them as unresolved).
  The design pages are reachable through `plan.md`; the Design section of the index is added after G-DOC.
- Validation: red baseline commit 1e16149, then `scripts/test/test-docs-site.sh` all PASS, docs lint (markdown, links) green.
- Open items: approval step: the user approves the index structure and the built site (gate G-INDEX, recorded in the issue body); the Design section of the index
  after G-DOC; publishing the site is a separate decision for the user.

## 2026-10-16 - D1.4 Approval is not a test case

- Issue and PR: #154; PR `claude/docs-approval-step`. User rule relayed by the coordinator: a human approval is never a test case.
- Changed: removed the approval from the test catalogue, the baseline and final reports, `scripts/test/test-design-docs.sh` and the
  plan row of D1.4; the approval is described as approval by the user, recorded in
  issue #154. `docs-final.md` lists each case as `TC-D-nnn PASS` so `check-test-cases.sh docs final` can read it. Other ids are not renumbered.
- Validation: `scripts/check-test-cases.sh docs final` and `baseline` exit 0, `scripts/test/test-design-docs.sh` 10 PASS,
  docs lint (markdown, links) green.
- Open items: G-DOC flips only after the user approves the design (recorded in issue #154).

## 2026-10-16 - D4.1 Aurora restyle: tests first and the W4 reference image (issue #164)

- Issue and PR: #164 (created for this order); PR `claude/docs-style-tests`.
- Changed: `docs/design/images/W4-choice.png` is the user's approved reference image (copied with `git show` from
  `handoff/W4-aurora-reference.png` on branch `claude/wonderful-darwin-gm3w0y`, not merged; 1254x1254 RGBA, sha256 74dc8655...);
  catalogue `TC-D-031..038`, `scripts/test/test-design-style.sh` (not in CI until the restyle is green), red baseline
  `docs/test-reports/docs-style-baseline.md` (commit 93adb71); D4 row in `docs/plans/docs.md`.
- Validation: `scripts/require-gate.sh G-BOOT` exited 0; the three W4 cases of TC-D-031 now PASS and the other cases stay red
  until D4.2; docs lint (markdown, links) green and `test-design-docs.sh` green.
- Open items: D4.2 restyles `ui.css` and the boards, re-renders the images and updates the color wording; the user's approval
  of the look is recorded in issue #164.

## 2026-10-16 - D4.2 Aurora restyle of the screens (issue #164)

- Issue and PR: #164; PR `claude/docs-style` (stacked on D4.1, PR 165).
- Changed: `docs/design/source/ui.css` is the aurora palette and components (variables, gradient ring, aurora faces, gradient
  pills, play, pause and forward icons); all boards restyled (alert levels now differ by text and icon, also on the watch);
  `render.mjs` renders watch boards as the round face only (1254x1254, transparent outside) and writes
  `render-manifest.json`; the 21 other images and the overview are re-rendered (W4 stays the reference file); the section texts
  and `docs/design/README.md` describe the aurora style and no old color wording is left; `test-design-style.sh` joins the
  `script-tests` CI job; the new report is linked from `docs/index.md`.
- Validation: `scripts/test/test-design-style.sh` TC-D-031..038 all PASS (red baseline commit 93adb71), `test-design-docs.sh`
  10 PASS, `check-docs-index.sh`, docs lint (markdown, links), `check-test-cases.sh docs final` green.
- Open items: approval by the user of the look, recorded in issue #164; the font is still the system sans-serif fallback.
