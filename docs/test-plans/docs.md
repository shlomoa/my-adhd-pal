# Documentation lane test-case catalogue (D)

Lane D owns `docs/design/**` and its own plan, catalogue, reports and work log ([`../plans/docs.md`](../plans/docs.md)).
Case ids are `TC-D-nnn`; test names embed the id (see [`../plans/README.md`](../plans/README.md), "Validation first and
last"). The work is issue #154, the documentation of the proposed UI/UX design.

**Kinds:** `script` = shell fixture, `CI` = checked by a CI job, `manual` = run by the user and reported in
`docs/work-log/docs.md`.
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
| TC-D-013 | manual | Design approval | the user reads `docs/design/` and approves the design; recorded in the issue body |
