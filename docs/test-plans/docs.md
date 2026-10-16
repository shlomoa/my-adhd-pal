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
