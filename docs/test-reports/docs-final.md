# Documentation lane final report (D1, issue #154)

Base: cf0b097 (`main` after PR 159); TC-D-014..020 (issue #157) were run on branch `claude/docs-checks`; TC-D-021..030 (issue #156) on branch `claude/docs-site`. Commands run from the repository root. Status of the user's approval is below:
the report is **not complete** until TC-D-013 is reported by the user.

| Id | Result | Evidence |
|---|---|---|
| TC-D-001 | PASS | `scripts/test/test-design-docs.sh`: 23 PNGs, each referenced once |
| TC-D-002 | PASS | same script: 23 image references resolve |
| TC-D-003 | PASS | same script: P1-P13 and W1-W9 have an image |
| TC-D-004 | PASS | same script: README links four sections and the solution |
| TC-D-005 | PASS | same script: data flow and event flow Mermaid diagrams, each described |
| TC-D-006 | PASS | same script: 53 relative links resolve |
| TC-D-007 | PASS | same script: sections show their assigned screens |
| TC-D-008 | PASS | same script: `docs/design/source/` present |
| TC-D-009 | PASS | same script: "proposed design" and tile action undecided |
| TC-D-010 | PASS | same script: lane plan, gate G-DOC, ownership, work log |
| TC-D-011 | PASS | `scripts/test/test-conventions.sh`: no FAIL lines |
| TC-D-012 | PASS | `scripts/docs-lint.sh markdown` 0 issues, `links` exit 0; CI green on PR 159 (all five jobs) |
| TC-D-013 | PENDING | manual: the user reads `docs/design/` and approves; not yet reported |
| TC-D-014 | PASS | `scripts/test/test-doc-checks.sh` (issue #157): format check fails on a format fixture, lint does not report it |
| TC-D-015 | PASS | same script: lint check fails on a lint fixture, format does not report it |
| TC-D-016 | PASS | same script: `docs-lint.sh markdown` catches both |
| TC-D-017 | PASS | same script: violations in `docs/design/` and in the root `README.md` are caught |
| TC-D-018 | PASS | same script: `docs-lint.sh fix` repairs the format fixture and leaves the lint violation |
| TC-D-019 | PASS | same script: lint, format and markdown pass on the repository documents |
| TC-D-020 | PASS | same script: `check` calls `docs-lint.sh markdown`; `script-tests` runs the script (CI run of the PR pending) |
| TC-D-021 | PASS | `scripts/test/test-docs-site.sh` (issue #156): every `docs/**/*.md` reachable from `docs/index.md` |
| TC-D-022 | PASS | same script: the index has the planned sections |
| TC-D-023 | PASS | same script: `mkdocs.yml` docs_dir, strict, Material, Mermaid fence |
| TC-D-024 | PASS | same script: requirements pinned with `==` |
| TC-D-025 | PASS | same script: strict build passes, site has an index page |
| TC-D-026 | PASS | same script: a broken relative link fails the strict build and names the file; removal is green |
| TC-D-027 | PASS | same script: index sections shown, Mermaid rendered in `protocol/index.html` |
| TC-D-028 | PASS | same script: an orphan document is reported by name |
| TC-D-029 | PASS | same script: workflow runs the build and the test, `build/` ignored (CI run of the PR pending) |
| TC-D-030 | PENDING | manual: the user approves the structure of the index and the built site; not yet reported |

`scripts/test/test-gates.sh`: no FAIL lines.
