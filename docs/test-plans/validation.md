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
| TC-V-007 | script | The groups cover every test once | `run-tests.sh --groups` lists at least four groups, each test exactly once and `test-spike.sh` as its two suites and `test-checks.sh` as its three parts, fifteen units in all (`test-post-merge.sh`) |
| TC-V-008 | script | Groups run at the same time | two groups of three seconds finish in at most five; a failure in one group fails the run and shows in the report, the other group still runs (`test-post-merge.sh`) |
| TC-V-009 | script | One git worktree per group | the groups run in two different directories, none of them the checkout, and no worktree is left behind (`test-post-merge.sh`) |
| TC-V-010 | script | Lint crash rule | a test with the known lint crash in its log is rerun once and counts as passed when the rerun passes; a second failure fails (`test-post-merge.sh`) |
| TC-V-011 | script | Units with an argument, durations | a unit `test.sh@arg` runs the test with `POST_MERGE_ARG=arg` and logs to `test@arg.log`; the run output has `== <unit> took Ns` for each unit (`test-post-merge.sh`) |
| TC-V-012 | script | Spike suites run separately | `test-spike.sh` with `POST_MERGE_ARG` runs only that simulated-adb suite, without it both (`test-post-merge.sh`) |
| TC-V-013 | script | `test-checks.sh` in parts | `POST_MERGE_ARG` `scaffold` runs only the full `check` (TC-S-016, 017), `format` only the formatter and detekt fixtures (TC-S-011, 012), `lint` only the Android lint and docs fixtures (TC-S-013 to 015); no argument runs all (`test-post-merge.sh`) |
| TC-V-014 | script | Gradle warm-up | one Gradle dry run in the checkout comes before the first test, so the groups do not install build-tools at the same time (`test-post-merge.sh`) |
