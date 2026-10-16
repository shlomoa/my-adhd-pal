# Documentation lane baseline (red)

Evidence that the cases of `docs/test-plans/docs.md` fail before any implementation exists.
Red commit: 4e6de0e (tests and catalogue only, no implementation).

## Script cases

Command: `scripts/test/test-design-docs.sh`. Exit 1.

```
FAIL TC-D-001 every PNG in docs/design/images is referenced exactly once (found 0)
FAIL TC-D-002 every image reference resolves (0 references)
FAIL TC-D-003 screens P1-P13 and W1-W9 each have an image
FAIL TC-D-004 docs/design/README.md links every section and the solution
FAIL TC-D-005 solution.md has a data flow and an event flow Mermaid diagram, each described
FAIL TC-D-006 relative links in docs/design resolve (0 links)
FAIL TC-D-007 four section files exist and show their assigned screens
FAIL TC-D-008 docs/design/source holds the board sources
FAIL TC-D-009 docs say 'proposed design' and mark the tile primary action undecided
FAIL TC-D-010 lane D is set up (plan, gate G-DOC, ownership, work log)
```

Command: `scripts/test/test-conventions.sh`. Exit 1.

```
FAIL TC-D-011 a lane:D label is accepted (expected 0, got 1)
```

## Not run here

- TC-D-012 runs in CI once `docs/design/**` exists (doc lint and link check).
- The design approval is approval by the user, recorded in issue #154.

`scripts/check-test-cases.sh docs baseline` exits 0 (every id appears in a test source).
