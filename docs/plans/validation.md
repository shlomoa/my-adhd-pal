# Lane V: Validation

Overview, gates, stop rule and ownership: [README](README.md). Lane V checks `main` after every merge and owns the
place of the slow script tests in CI. It takes no `plan.md` step; its work is issue #190 and the ongoing duty below.

| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **V1** | Lane setup: this plan, README rows, gate `G-VAL`, `docs/work-log/validation.md`, `lane:V` accepted by `scripts/check-issues.sh`, catalogue `docs/test-plans/validation.md` | TC-V-001, TC-V-002 red, then green | G-BOOT | none |
| **V2** | Issue #190 (PR #192, started by lane S): the `script-tests` job leaves the pull request checks; `post-merge.yml` runs the slow script tests after each merge and keeps one `main-red` issue | TC-S-170..175 (kept ids, in `scripts/test/test-post-merge.sh`) | G-BOOT | **G-VAL** |

## Ongoing duty

After each merge to `main` the coordinator sends `merged <pr> <sha>`. Lane V checks the post-merge run for that sha:

- Green: no report.
- Red or missing: find the failing test ids and the first bad commit, name the owning lane from the ownership table in
  the [README](README.md), and keep the `main-red` issue current (the workflow opens and closes it; a failure never
  waits for a person to notice).
- One new sub-agent per `main-red` issue, created with `create_session`. Its brief is complete: the issue, the failing
  test ids, the owning lane's files, the standing rules (tests first with a recorded red commit, a `Cleanup:` line, a PR
  body starting with `Requires:` and `Produces:`), the branch `claude/validation-fix-<n>`. It fixes in its own PR and
  reports to lane V.
- Lane V tells the coordinator in one line per red issue: failing test ids, owning lane, sub-agent id.
- When the fix is merged, a later post-merge run is green and the issue is closed, lane V archives the sub-agent
  (`archive_session`).
- A run that needs more than 30 minutes is reported once.

## Rules

- Decisions and status live in the issue body, never in issue comments.
- Lane V owns `post-merge.yml`, `scripts/ci-post-merge/**` and its own plan, catalogue, reports and work log; lane S
  keeps the rest of `.github/**` and `scripts/**`.
- A person's approval is never a test case.
