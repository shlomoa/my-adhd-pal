# Work log: validation lane (V)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-17 - Issue 190: lane setup and review of PR 192

- Issue and PR: #190; PR 192 (started by lane S, taken over by lane V).
- Changed: lane V plan, catalogue and log; gate `G-VAL`; README rows; `lane:V` accepted by `check-issues.sh`; runner and issue handler moved to `scripts/ci-post-merge/`; `test-protocol-spec.sh` and `test-gates.sh` (about a second each, no Gradle) stay per pull request in `gates.yml` (TC-S-175); the nine slow tests run in `post-merge.yml`.
- Validation: TC-S-175 red in b9908c2, TC-V-001 and TC-V-002 red in 27ee519, then green. `test-ci.sh` fails locally only on the Gradle release builds (TC-S-020, TC-S-021), which cannot build in this sandbox.
- Open items: whether `script-tests` is a required status check (the coordinator asks the user); the first real post-merge run exercises the `gh` calls.
