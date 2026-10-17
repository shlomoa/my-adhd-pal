# Lane V: Validation

Overview, gates, stop rule and ownership: [README](README.md). Lane V checks `main` after every merge and owns the
place of the slow script tests in CI. It takes no `plan.md` step; its work is issue #190 and the ongoing duty below.

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **V1** | Lane setup: this plan, README rows, gate `G-VAL`, `docs/work-log/validation.md`, `lane:V` accepted by `scripts/check-issues.sh`, catalogue `docs/test-plans/validation.md` | TC-V-001, TC-V-002 red, then green | G-BOOT | none |
| **V2** | Issue #190 (PR #192, started by lane S): the `script-tests` job leaves the pull request checks; all of its scripts run after each merge, run by lane V itself (no workflow), failures become `main-red` issues | TC-S-170, 172, 173 (kept ids) and TC-V-004, TC-V-005, TC-V-006, all in `scripts/test/test-post-merge.sh` | G-BOOT | **G-VAL** |
| **V3** | The post-merge run in parallel groups, each in its own git worktree (the sequential run took about 15 minutes) | TC-V-007 to TC-V-010 red, then green | G-BOOT | none |
| **V4** | The post-merge run balanced by measured time: four groups, the two spike suites in separate groups, each unit's duration printed | TC-V-007, TC-V-011, TC-V-012 red, then green | G-BOOT | none |

## Ongoing duty

After each merge to `main` the coordinator sends `merged <pr> <sha>`. Lane V runs the script tests itself, in its own
container, never in a GitHub workflow: it checks out that sha and runs `scripts/ci-post-merge/run-tests.sh` (the eleven
former `script-tests` scripts and `test-post-merge.sh`, in four groups at the same time (balanced by the durations the run prints; `test-spike.sh` runs as its two suites,
`test-spike.sh@test-run-0b2a` and `@test-run-0b2b`, in separate groups), each in its own git worktree of
the merged sha, one test after the other inside a group; none runs per pull request; it installs what
the container lacks with `scripts/bootstrap-android.sh` and PowerShell 7; the known lint crash of `docs/conventions.md`
is rerun once). Several merges in a row: run on the latest sha, bisect only when red.

- Green: no report.
- Red or not runnable: find the failing test ids and the first bad commit, name the owning lane from the ownership table
  in the [README](README.md), and open (or update) one issue labelled `main-red` with the GitHub issue tool. Its body
  names the failing test ids, the sha, the owning lane and the sub-agent id.
- One new sub-agent per `main-red` issue, created with `create_session`. Its brief is complete: the issue, the failing
  test ids, the owning lane's files, the standing rules (tests first with a recorded red commit, a `Cleanup:` line, a PR
  body starting with `Requires:` and `Produces:`), the branch `claude/validation-fix-<n>`. It fixes in its own PR and
  reports to lane V.
- Lane V tells the coordinator in one line per red issue: failing test ids, owning lane, sub-agent id.
- When the fix is merged, a later run is green and lane V has closed the issue, lane V archives the sub-agent
  (`archive_session`).
- A run that needs more than 30 minutes is reported once.

## Rules

- Decisions and status live in the issue body, never in issue comments.
- Lane V owns `scripts/ci-post-merge/**` and its own plan, catalogue, reports and work log; lane S
  keeps the rest of `.github/**` and `scripts/**`.
- A person's approval is never a test case.
