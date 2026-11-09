# Documentation checks baseline (red), issue #157

Evidence that the cases TC-D-014..020 fail before the implementation. Red commit: the first commit of the PR
`claude/docs-checks` (tests and catalogue only, no change to `scripts/docs-lint.sh`).

Command: `scripts/test/test-doc-checks.sh`. Exit 1. `docs-lint.sh` has no `format`, `lint` or `fix` mode yet.

```
FAIL TC-D-014 format check fails on trailing spaces, blank lines and a missing final newline
FAIL TC-D-014 the lint check reports the lint fixture but not the format fixture
FAIL TC-D-015 lint check fails on a skipped heading level
FAIL TC-D-015 the format check reports the format fixture but not the lint fixture
FAIL TC-D-017 a format violation in docs/design is caught
FAIL TC-D-017 a lint violation in the root README.md is caught
FAIL TC-D-018 fix makes the format check pass
FAIL TC-D-018 fix does not hide a lint violation
FAIL TC-D-019 the lint check passes on the repository documents
FAIL TC-D-019 the format check passes on the repository documents
FAIL TC-D-020 the script-tests job runs test-doc-checks.sh
```

TC-D-016 and the other TC-D-019 and TC-D-020 lines already pass because the combined `markdown` mode catches both kinds.
