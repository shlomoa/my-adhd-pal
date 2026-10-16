# Documentation lane test-case catalogue (D)

Lane D owns `docs/design/**` and its own plan, catalogue, reports and work log ([`../plans/docs.md`](../plans/docs.md)).
Case ids are `TC-D-nnn`; test names embed the id (see [`../plans/README.md`](../plans/README.md), "Validation first and
last"). The work is issue #154 (step ids D1.x), the documentation of the proposed UI/UX design.

**Kinds:** `script` = shell fixture, `CI` = checked by a CI job. The user's approval of the design is not a test case:
it is approval by the user, recorded in issue #154.
**Evidence:** red baseline in `docs/test-reports/docs-baseline.md`; final in `docs/test-reports/docs-final.md` (each id
`PASS`, zero skipped). `scripts/test/test-design-docs.sh` runs the script cases; it joins CI with the last content
step, when its head is green.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-D-001 | script | Every image is used once | every PNG in `docs/design/images/` is referenced by exactly one section file (overview images by the README only) |
| TC-D-002 | script | Image references resolve | every `![](images/...)` in `docs/design/*.md` points to an existing file |
| TC-D-003 | script | Every screen has an image | P1-P13 and W1-W9 each have `docs/design/images/<id>-*.png` |
| TC-D-004 | script | Overview lists every section | `docs/design/README.md` links the four section files and `solution.md` |
| TC-D-005 | script | Solution diagrams | `solution.md` has a data flow and an event flow, each a Mermaid diagram followed by a description |
| TC-D-006 | script | No broken links | every relative link in `docs/design/*.md` resolves |
| TC-D-007 | script | Sections show their screens | the four section files exist and each contains the images of the screens assigned in the issue |
| TC-D-008 | script | Board sources kept | `docs/design/source/` holds the board sources |
| TC-D-009 | script | Wording | the README says "proposed design" and the tile section marks the primary action undecided |
| TC-D-010 | script | Lane set up | `docs/plans/docs.md`, gate G-DOC (owner D, requires G-BOOT), ownership row, lane table row and a valid `docs/work-log/docs.md` exist |
| TC-D-011 | script | Lane label | `scripts/check-issues.sh` accepts `lane:D` (`scripts/test/test-conventions.sh`) |
| TC-D-012 | CI | Doc lint and link check | `scripts/docs-lint.sh markdown` and `links` are green on `docs/design/**` |

## Documentation checks (issue #157, D3)

`scripts/test/test-doc-checks.sh` runs these cases with negative fixtures that exist only while it runs; it joins the
`script-tests` CI job with the implementation.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-D-014 | script | Format check | trailing spaces, extra blank lines and a missing final newline fail `scripts/docs-lint.sh format` naming the file; `lint` does not report them |
| TC-D-015 | script | Lint check | a skipped heading level fails `scripts/docs-lint.sh lint` naming the file; `format` does not report it |
| TC-D-016 | script | One entry point | `scripts/docs-lint.sh markdown` fails on a format violation and on a lint violation (both checks run) |
| TC-D-017 | script | Coverage | a violation in `docs/design/` and one in the root `README.md` are caught |
| TC-D-018 | script | Fix command | `scripts/docs-lint.sh fix` makes the format check pass and does not hide a lint violation |
| TC-D-019 | script | Clean documents | lint, format and markdown pass on the repository documents once the fixtures are removed |
| TC-D-020 | script | CI | `./gradlew check` calls `docs-lint.sh markdown`, and the `script-tests` job runs `test-doc-checks.sh` |

## Documentation index and generated site (issue #156, D2)

`scripts/test/test-docs-site.sh` runs these cases (the build needs Python 3 and network access for the pinned tools).
The decision (option A, MkDocs with the Material theme) is in the issue body. The user's approval of the structure is
an approval step of the plan (gate G-INDEX), not a test case.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-D-021 | script | No orphan | every `docs/**/*.md` is reachable from `docs/index.md` through relative links (`scripts/check-docs-index.sh`) |
| TC-D-022 | script | Index sections | `docs/index.md` has the sections Plan and lanes, Protocol and data layer, Test plans and reports, Spikes and research, Setup and demo |
| TC-D-023 | script | Generator config | `mkdocs.yml` sets `docs_dir: docs`, `strict: true`, the Material theme and a Mermaid fence |
| TC-D-024 | script | Pinned tools | `tools/docs-site/requirements.txt` pins mkdocs and mkdocs-material, and every line, with `==` |
| TC-D-025 | script | Strict build | `scripts/docs-site.sh` builds the site with `mkdocs build --strict`, exit 0, and the site has an index page |
| TC-D-026 | script | Broken link | a relative link to a missing file fails the strict build and names the file; removing it is green |
| TC-D-027 | script | Built site | the index page shows the sections and the Mermaid diagrams of `protocol.md` are rendered as diagrams |
| TC-D-028 | script | Orphan detected | a document not linked from the index makes `check-docs-index.sh` exit 1 and name it |
| TC-D-029 | CI | CI build | the `build` workflow runs `scripts/docs-site.sh`, the `script-tests` job runs `test-docs-site.sh`, and the generated site is ignored by git |

## Aurora restyle of the design images (issue #164, D4)

`scripts/test/test-design-style.sh` runs these cases (python3, standard library only). The user's approval of the look is
approval by the user, recorded in issue #164; it is not a test case.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-D-031 | script | W4 is the reference | `docs/design/images/W4-choice.png` has the sha256 of the approved reference file, is 1254x1254 RGBA, transparent in the corners and opaque in the center |
| TC-D-032 | script | Pixel sizes | P1-P6 and P8-P13 are 780x1688, P7 is 780x2000, W1-W9 are 1254x1254, the overview is 2400x1280 |
| TC-D-033 | script | Watch circles | W1-W9 are transparent outside the circle (corners) and opaque in the center |
| TC-D-034 | script | Rendered from the sources | `docs/design/source/render-manifest.json` holds, for every image except the reference W4, the hash of the board and `ui.css` it was rendered from, and it equals the current files |
| TC-D-035 | script | Contrast | the text, soft text and button label colors of `ui.css` meet 4.5:1 on their backgrounds (page, card, button gradient stops, alert backgrounds) |
| TC-D-036 | script | Alert levels | the level 1 and level 2 boards differ by text and by icon, on the phone (P10, P11) and on the watch (W7, W8) |
| TC-D-037 | script | Wording | the section texts say "aurora", keep "proposed design" and the undecided tile action, and no old color wording is left |
| TC-D-038 | script | Old palette gone | none of the legacy colors is left in `ui.css` or the boards |
