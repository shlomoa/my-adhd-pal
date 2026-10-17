# Validation lane test-case catalogue (V)

Lane V runs the script tests after each merge and owns the runner ([`../plans/validation.md`](../plans/validation.md)). Case ids are
`TC-V-nnn`. The cases of issue #190 keep the ids `TC-S-170` to `TC-S-173` that lane S wrote first; they are catalogued in
[`shared.md`](shared.md). `scripts/test/test-post-merge.sh` runs them with the TC-V cases below, after each merge like every other script test.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-V-001 | script | Lane set up | `docs/plans/validation.md`, this catalogue, `docs/work-log/validation.md`, gate `G-VAL` (owner V, requires G-BOOT) and the README ownership row exist (`test-post-merge.sh`) |
| TC-V-002 | script | Lane label and log | `scripts/check-issues.sh` accepts `lane:V` and `scripts/check-work-log.sh` accepts the validation work log (`test-conventions.sh`) |
| TC-V-003 | script | Main-red procedure written down | the validation plan and `docs/conventions.md` describe one sub-agent per `main-red` issue and archiving it when resolved (`test-post-merge.sh`) |
| TC-V-004 | script | No workflow runs the post-merge tests | `.github/workflows/post-merge.yml` and `scripts/ci-post-merge/main-red-issue.sh` do not exist (`test-post-merge.sh`) |
| TC-V-005 | script | Main-red procedure by lane V | the validation plan and `docs/conventions.md` say lane V opens the `main-red` issue itself with its issue tool, naming the failing test ids, the sha, the owning lane and the sub-agent id (`test-post-merge.sh`) |
| TC-V-006 | script | The whole former `script-tests` runs after the merge | the runner lists the eleven former `script-tests` scripts and `test-post-merge.sh`, and no workflow runs any of them (`test-post-merge.sh`) |
| TC-V-007 | script | The groups cover every test once | `run-tests.sh --groups` lists at least three groups and each of the twelve tests exactly once (`test-post-merge.sh`) |
| TC-V-008 | script | Groups run at the same time | two groups of three seconds finish in at most five; a failure in one group fails the run and shows in the report, the other group still runs (`test-post-merge.sh`) |
| TC-V-009 | script | One git worktree per group | the groups run in two different directories, none of them the checkout, and no worktree is left behind (`test-post-merge.sh`) |
| TC-V-010 | script | Lint crash rule | a test with the known lint crash in its log is rerun once and counts as passed when the rerun passes; a second failure fails (`test-post-merge.sh`) |
