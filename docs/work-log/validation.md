# Work log: validation lane (V)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-17 - Issue 190: lane setup and review of PR 192

- Issue and PR: #190; PR 192 (started by lane S, taken over by lane V).
- Changed: lane V plan, catalogue and log; gate `G-VAL`; README rows; `lane:V` accepted by `check-issues.sh`; runner and issue handler moved to `scripts/ci-post-merge/`; `test-protocol-spec.sh` and `test-gates.sh` (about a second each, no Gradle) stay per pull request in `gates.yml` (TC-S-175); the nine slow tests run in `post-merge.yml`.
- Validation: TC-S-175 red in b9908c2, TC-V-001 and TC-V-002 red in 27ee519, then green. `test-ci.sh` fails locally only on the Gradle release builds (TC-S-020, TC-S-021), which cannot build in this sandbox.
- Open items: whether `script-tests` is a required status check (the coordinator asks the user); the first real post-merge run exercises the `gh` calls.

## 2026-10-17 - Issue 190: lane V runs the script tests, all eleven

- Issue and PR: #190; PR 201 (no post-merge workflow, lane V runs the tests and opens `main-red` issues), PR 202 (all eleven former `script-tests` and `test-post-merge.sh` run after the merge, none per pull request).
- Changed: `post-merge.yml` and `main-red-issue.sh` deleted; `run-tests.sh` lists twelve tests; `gates.yml` back to its state before PR 192; documents and catalogue follow (TC-V-004 to TC-V-006, TC-S-171 and TC-S-174 retired).
- Validation: TC-V-006 red in 8794d71, then green. First local runs on merged `main`: `c73fe04` green (nine tests, about 15 minutes, container with PowerShell 7 and the Android SDK from `scripts/bootstrap-android.sh`).
- Open items: the git push and the `gh` CLI were denied by the tool policy; PRs 201 and 202 were pushed with the GitHub file tools, which wrote some scripts without the executable bit, so the runner and the test call scripts with `bash`.

## 2026-10-17 - Issue 190: parallel groups

- Issue and PR: #190; PR linked from the coordinator message.
- Changed: `run-tests.sh` runs three groups at the same time, each in its own git worktree, reruns a test once for the known lint crash and prints the groups with `--groups`; documents and catalogue (TC-V-007 to TC-V-010).
- Validation: TC-V-007 to TC-V-010 red against the sequential runner (a scratch copy; TC-S-173 red too; commit 9c57576), then green with `test-post-merge.sh`. First real parallel run on `main` (21edc20, 4 cores, 15 GB): all twelve green in 588 s, against about 15 minutes sequentially; the two Gradle groups each build from cold, so the gain is smaller than the expected 5 minutes. The sequential run on `8159830` failed `test-checks.sh` with the lint crash (issue 205), a clean worktree passed.
- Open items: none.

## 2026-10-17 - Issue 205: script hygiene

- Issue and PR: #205; PR linked from the issue.
- Changed: `test-structure.sh` writes its Gradle log to a `mktemp` file removed on exit (it used the fixed `/tmp/structure-gradle.log`); `test-checks.sh` removes the directories its fixtures created (`phone/src/testDebug/...` stayed behind); two new cases TC-S-196 and TC-S-197.
- Validation: TC-S-196 and TC-S-197 red in 2399a60 (clean worktree, `test-checks.sh`), green in dc7d2db. The lint crash itself was not fixed and not shown to come from script state: `check --rerun-tasks` crashed in 3 of about 10 default runs in one checkout (two of them while other Gradle runs shared the machine); the lint tasks alone passed 16 of 16; `--no-parallel` is Gradle's default here and changes nothing.
- Open items: the trigger of the lint crash is still unknown; the issue stays open.
