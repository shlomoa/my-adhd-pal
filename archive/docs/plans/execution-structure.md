# Execution structure (as practised 2026-10-17 to 2026-10-19)

Status: record of how the work is organised; the authority for steps and architecture stays [`plan.md`](../plan.md), for lanes and gates
[`plans/README.md`](README.md), for rules [`conventions.md`](../conventions.md).

## 1. Plan

- `docs/plan.md`: architecture, decisions, step ids (0a.1 to 7.6). `docs/plans/*.md`: who does which step, gates, order.
  `docs/plans/gates.json`: gates as data, checked by scripts.
- The live plan: "align the product plan with the user's biometric definition" (2026-10-17): rows P1-P3, C1-C12, E1-E7 in
  `plans/README.md` and `plans/watch.md`; status per row in the body of tracking issue #235. Next milestones M0 to M3 and the
  validation plan: issue #258 (proposed, not approved).
- User's definition (2026-10-17): "Heart rate is measured continuously. Heart rate is compared to the baseline continuously. A stress
  event is reported immediately and the local response is activated." "Immediately" = 0 to 2 seconds. Validation: all scenarios top to
  bottom in 1 hour; 100% of the scenarios defined for M1 covered (2026-10-19).
- Deferred by the user (2026-10-17): schedule, scheduler, schedule builder, tasks; standing instruction: un-defer them when everything
  else is resolved and unblocked.

## 2. GitHub

- Repository shlomoa/my-adhd-pal. One issue per plan step and per experiment; status and decisions live in the issue BODY, never in
  comments. Labels: milestone, lane (S, A, W, D, V), `main-red`.
- Every PR: branch `claude/<lane>-*`, body starts with `Requires:` and `Produces:` (gates), contains `Cleanup:`; squash merge by the
  coordinator on green CI with the head sha pinned; the commit message carries the session attribution.
- CI on every PR: `build` (./gradlew check assembleDebug, release-has-no-debug), `docs-site` (index check, strict build),
  `external-links` (never blocks), `gates`, `conventions`. After each merge to main: the validation lane runs the script tests (about
  13 minutes) and opens a `main-red` issue only if red. No gh CLI; the GitHub MCP tools are used.
- Result branches written by scripts with git plumbing, no checkout change: `<prefix>-results/<stamp>` (e1, e3, e4, e6, screen, m2),
  `demo-results/<stamp>`, `probe-status/<script>-<run>` (heartbeat, force-updated).

## 3. Lane agents

Separate Claude Code cloud sessions, one per lane, started by the coordinator, each in its own container with the repository.

- S Shared and data: tooling, CI, scripts, `docs/protocol.md`, `:protocol`, phone data layer. A Android: phone UI, phone features,
  phone demo and phone debug receivers. W Watch: everything in `:wear`, biometrics, probe scripts, stress demo. D Documentation:
  `docs/design/`, spec facts, biometrics.md, index. V Validation: post-merge runs, `main-red` issues, `scripts/ci-post-merge/**`.
- File ownership per lane: table in `plans/README.md`. Each lane keeps its own catalogue `docs/test-plans/<lane>.md`, reports
  `docs/test-reports/<lane>-*.md`, work log `docs/work-log/<lane>.md`.

## 4. Other agents

- Coordinator C (orchestrator session): runs the plan, merges, keeps issue #235 and #258, relays between lanes and the user. Not a
  lane; edits no lane-owned code.
- V sub-agents: one new session per `main-red` issue, archived by V when fixed (used for #205 lint crash, #237 gradle --stop,
  post-merge speed-up).
- The user: owner. Closes the human gates H1 and H2, approves designs and plans, runs real-device cases, does only physical actions
  (watch on/off, wrist, charger, wireless debugging; phone on/off, lock, USB).

## 5. Methods

- Validation first and last per lane: test-case catalogue (TC-S/A/W/V-nnn), red tests before implementation (red commit recorded in
  `test-reports/<lane>-baseline.md`), green before merge, final report. Test names embed the id; no test is skipped or disabled.
- Experiments (E1-E7) are run by the user or by script, their results go to `plans/biometrics.md` 12.3 and the issue body; a run has
  no test-case id; a person's approval is never a test case.
- Debug code only in `src/debug/` and a `debug` package; raw biometrics never in release. Cleanup in every PR. Docs: every new file
  linked from `docs/index.md`.
- Run scripts for the devices: PowerShell 5.1/7, adb only, no prompts for the user where adb can do it, hard time limits on adb and
  git, heartbeat, pre-flight, result always published; tested only against a simulated adb (`scripts/*/fake/adb`), never proof of
  device behaviour.

## 6. Protocols

- Gates: `scripts/require-gate.sh` first in every step; a lane that is blocked writes `BLOCKED: <step> waits for <gate>` in its work
  log, pushes and stops; no polling. CI re-checks `Requires:`/`Produces:` (`scripts/check-pr-gates.sh`).
- Lane to coordinator and back: Claude Code Remote session messages (`send_message`). Coordinator to V after each merge:
  `merged <pr> <sha>`. V to coordinator: one line per red run (test ids, owning lane, sub-agent id) or silence when green. Lanes
  report PR numbers and heads; the coordinator merges.
- Scheduled checks (`send_later`) wake the coordinator to merge when CI is green; they can fire late after a worker restart, so state
  is read directly when it matters.
- User orders override everything: hold means start nothing, stop means stop, "do not archive anyone" is standing. Answers to the user:
  at most 4 lines of 80 characters when asked; facts verified before asking the user anything; estimates come from the lane and
  guesses are marked.

## 7. Roles and responsibilities

- User: requirements and their definitions, approvals (plans, designs, gates), physical actions and real-device runs, decisions on
  scope and budget (10 minutes per step, 1 hour validation).
- Coordinator: translates the user's orders into lane orders, merges on green, keeps the tracker and the plan issues true, reports
  status and gaps honestly, never adds scope without approval.
- Lanes: do only their rows and files, tests first, report facts and open items, ask the coordinator not the user. V checks main after
  every merge. D keeps documentation true to what exists.

## 8. Known weaknesses of this structure (2026-10-19)

- The plan measured before it read the specifications (see work-log learnings); no gate required a specification review before a probe.
- No milestone was tied to the user's definition as a pass test until 2026-10-19.
- The lane estimates were guesses; no measured baseline of lane speed was kept (M1: first PR in about 45 minutes; M2 probe about 2.5
  hours with interruptions).
