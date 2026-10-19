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

## 2026-10-17 - Issue 190: balanced groups

- Issue and PR: #190; PR linked from the coordinator message.
- Changed: four groups balanced by measured time (checks | structure ci pairing | spike suite 0b2a | spike suite 0b2b and the short tests); a unit `test-x.sh@arg` passes `POST_MERGE_ARG`, `test-spike.sh` reads it; every unit prints `== <unit> took Ns`; one Gradle dry run in the checkout before the groups installs the Android build-tools (three worktrees installing build-tools 36.0.0 at once raced in a fresh container and failed `test-structure.sh`). Catalogue TC-V-011, TC-V-012, TC-V-014; TC-V-007 updated.
- Validation: tests red (commit 7b95199), then green. Same sha, 4 cores, warm caches: three groups 744 s, four groups 585 s (checks 577, spike a 383, spike b 277, ci 166, design-style 140). Tried and dropped, both slower: `test-checks.sh` in two parts in a fifth group 647 s; in three parts over four groups 930 s (the scaffold part alone takes 283 s on an idle machine and 797 s beside the others). Concurrent Gradle builds in separate worktrees contend for the four cores and memory, so more Gradle units slow each other.
- Open items: `test-checks.sh` is the floor (about 580 s; `check --rerun-tasks` is 283 s of it); a faster run needs less Gradle work, not more groups.

## 2026-10-17 - Issue 205: fresh Gradle daemon for the TC-S-017 check

- Issue and PR: #205; PR linked from the issue.
- Changed: `test-checks.sh` runs `./gradlew --stop` before its TC-S-017 check; new case TC-S-198; the lint crash note in `docs/conventions.md` names the evidence.
- Validation: TC-S-198 red in b3b099b, then green. Evidence for the idea is thin: `check --rerun-tasks` in one checkout crashed in 3 of 6 runs without `--stop` and in 0 of 5 with it. This PR does not claim the crash is fixed.
- Open items: the cause of the crash is unknown; #205 stays open.

## 2026-10-17 - Issue 237: no `--stop` in the TC-S-017 check

- Issue and PR: #237; PR linked from the issue.
- Changed: `test-checks.sh` runs `gradle --no-daemon check --rerun-tasks` and no longer `./gradlew --stop`, which ended the daemons of the other post-merge groups (three cases of `test-structure.sh` failed on 444b5b1); TC-S-198 now asserts `--no-daemon`; new case TC-S-199: no script runs `gradlew --stop`.
- Validation: TC-S-198 and TC-S-199 red in 65781a5, then green. Both are static checks of the script text; the interference itself is covered by the full post-merge runs.
- Open items: the evidence that the fresh JVM avoids the lint crash of #205 is as thin as it was for `--stop`; #205 stays open.

## 2026-10-19 - Issue 258: Validation learnings

- Issue and PR: #258; PR #272.
- Changed: recorded lane V's validation learnings below.
- Validation: `scripts/check-work-log.sh docs/work-log/validation.md`; `scripts/test/test-conventions.sh`.
- Open items: see the open questions below.

Written by the second lane V session (started 2026-10-17 about 14:35 UTC). Plain facts; no new scope.

### Facts

- Runs: 28 post-merge runs on 27 main shas, 444b5b1 to e4b86ea, all 12 tests each. 444b5b1 was run twice. Three shas were red, all for issue 237 (444b5b1, ac85a91, e3994d2: `test-structure.sh` TC-S-006 twice and TC-S-002, "Gradle build daemon has been stopped: stop command received"). 34b3283 (#241) and every later sha were green. No run showed the lint crash of issue 205 (no `*.first.log` in any output); with the two runs of the first session that is 29 shas since #218 (1ffdc5b) without the crash. There was no control without the change, so this does not show the crash is gone.
- Run time: `test-checks.sh` is the floor of a run. Its time was 713 s on the second run of 444b5b1, 387 to 524 s on the 14 runs from ac85a91 to fd4dfa5, 621 to 682 s on the four runs 11d4c61 to 46471ee, 658 s on 7fa9150, 841 s on 2a79257, 758 and 760 s on 8387432 and a5090a6, then 448 to 576 s from fc1fa31 to e4b86ea. I did not measure what else used the machine, so the cause of the spread is not known. No run came near 30 minutes.
- What broke: only issue 237. The interference was timing dependent: 1ffdc5b and 69feaa5 had the same `--stop` and passed. The sub-agent's fix (#241, `--no-daemon`) was proven only by static cases TC-S-198 and TC-S-199 plus the later full runs.
- Container: a fresh container needed `scripts/bootstrap-android.sh` (it fell back to a Maven mirror after a 429) and PowerShell 7.5.1 from the release tarball into `/root/tools/pwsh`; both worked as in the handoff. `GitHub MCP` tools disconnected and reconnected several times during the session; the coordinator's messages arrived through the `claude-code-remote` `send_message` tool.
- What the validation needs, from the plan in issue 258 section 3: the post-merge set only runs the 12 script tests. It does not run `scripts/probe/*` tests (coordinator ruling 2026-10-18: temporary experiment tooling, run by lane W before each PR), nothing on a device, and no phone screen check. The low-touch plan needs state-driven waits, hard timeouts, a heartbeat, and one master command; these are not in the runner.

### My mistakes and what they cost

- First launch of the run on 444b5b1 failed at once (`git rev-parse --short origin/main HEAD` takes one revision); I saw it only when the completion notice came, so the real run started a few minutes late.
- I sent my first "V up" with `SendMessage` (the wrong transport); it said the session was not reachable. I did not try the `claude-code-remote` `send_message` tool until the coordinator's first message arrived. The handoff said my predecessor's sends to the coordinator were refused; that may have been the same mistake. Cost: no message to the coordinator for the first few minutes.
- I did not rerun `test-structure.sh` alone on the three red shas to confirm issue 237; I relied on the "stop command received" lines in its log (two or three on each run). Until 34b3283 the build of the wear and phone code merged in #240 was therefore not shown green by the set.
- I closed issue 237 after one green run (34b3283) on the coordinator's instruction although I had said two runs were my bar. Later green runs support it; one run alone was weak evidence for a timing-dependent failure.
- I quoted the count of clean runs for issue 205 twice ("8 clean runs", then "13 at last count") without recounting from the run list. The list above is the recount.
- On 60340a2 I said the new probe test passed after reading only the tail of its output, and reported the exit code of `tail`. I said so at the time; the PASS lines were real but the exit code was not.
- I misread "Try again" (user messages after a restart) twice and redid a step that was already done; each cost one tool call.

### What I would do differently

- Send the first message with `claude-code-remote` `send_message`, and test the launch command with `bash -n` or on a short run before starting the 10-minute job.
- Write the launch as a small script file in my own scratch directory that prints the sha, the exit code of `run-tests.sh` itself, `report.md`, and the `*.first.log` and "stop command received" counts, instead of an inline compound command whose exit code belongs to the last pipe.
- On a red with only the stop-command error, rerun the failed test alone once and record that result in the issue body.
- Keep one running table of sha, PR, `test-checks.sh` seconds and result in this log, so counts are never recalled from memory.

### Open questions

- Why `test-checks.sh` varied between 387 and 841 s on a machine that ran nothing else of mine; whether other containers share the host.
- Whether `--no-daemon` is the reason the lint crash has not come back, or whether it was never triggered by this run pattern; issue 205 is closed, the cause is unknown.
- Whether a docs-only merge needs the full set (8 to 14 minutes each) or a sha range check would do; the standing rule says run on every merge.
- Whether the `scripts/probe` tests should join the runner if those scripts outlive the experiments (coordinator ruling: revisit only then).
