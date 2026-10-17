# Handoff: lane D (Documentation)

Written 2026-10-17 by the outgoing lane D session, for the new lane D session started by the coordinator
(session_01SicxwYxR11J2rccj3we8WY). Base: origin/main 69feaa5 (Docs: date the statements that Google's pages define no batch, #220).
Repo: shlomoa/my-adhd-pal. No secrets in this file.

## 1. Status

| Item | State | Branch / head | What is left |
|---|---|---|---|
| #222 C1 (D6 "Measured latency", "best effort" removed) | closed, completed | none (verified on main 69feaa5, no PR) | nothing; result is in the issue body |
| #224 C9 (baseline wording verbatim, extended flow deferred) | closed, completed | none (verified on main 69feaa5, no PR) | nothing; result is in the issue body |
| #226 C2 docs (passive-route wording) | open, not started | none | see section 5; part was already done in #219 |
| #227 C6 (urgent /alert immediate, queue is the exception path) | open, not started | none | see section 5; already done in #219, verify and close |
| #229 E2 (record E1 against the user's definition) | open, not started | none | waits for E1 (user run) and the coordinator's message |
| #154 docs/design approval, gate G-DOC | open | none | the user's approval of the design document and two open design points |
| #156 docs index / G-INDEX | open | none | Design section in docs/index.md after G-DOC; user approves the index structure |
| Open PRs of lane D | none | | |

Merged lane D PRs (all on main): #155 9534e30, #158 47c5737, #159 cf0b097, #160 2af3e68, #161 7e1f569, #162 b1cb51a,
#163 63c653e, #165 b8d2767, #166 94d37c7, #187 02dc47f, #195 f1cf08c, #204 085c5e8, #217 8bbaaa8, #219 0a58dd0, #220 69feaa5.
Stale local branches in the old container (ignore): claude/docs-wave0, claude/docs-wave1, claude/docs-wave1b, claude/docs-batch, claude/docs-w0.

## 2. Instructions held

### Messages from the coordinator of this session (session_01SicxwYxR11J2rccj3we8WY), verbatim, 2026-10-17

14:12 UTC:

> Lane D: wave 0 starts now. Two issues are yours: #222 (C1) and #224 (C9).
>
> Sender: the coordinator session for the 2026-10-17 plan "align the product plan with the user's biometric definition", session_01SicxwYxR11J2rccj3we8WY. Status of the whole plan is in the body of tracking issue #235. The P3 gate is green (#221, main 69feaa5).
>
> #222 (C1): D6 is "Measured latency" with the measured numbers in docs/plans/biometrics.md; "best effort" appears nowhere; the sentence "the user picks the route" is removed from biometrics.md and from the body of issue #55 (tell me if #55 still has it; I edit that body, you do not).
> #224 (C9): the docs record the user's wording verbatim: "Baseline will be defined by user as measured separately for now, the extended flow is deferred to later stages". No change to BaselineTracker; the extended flow (keep last good baseline, running hours) is marked deferred, low priority.
>
> Both are mostly on main already (D6 row in biometrics.md; quote at biometrics.md:534). Verify with git grep over docs/ and the code, fix only what is missing, and do not rewrite what is correct. If everything is already correct, put the grep output in the issue body and close the issue; no PR needed.
>
> Rules for any PR: branch claude/docs-*, tests-first rule where a test applies, body starts with `Requires:` and `Produces:` and has a `Cleanup:` line, the user's words verbatim with no interpretation added, Google quoted verbatim with its link, `scripts/docs-lint.sh markdown`, links and `scripts/check-docs-index.sh` pass, `git grep -n -i batch` shows only verbatim Google quotes and the user's own exception wording. Decisions and status in the issue body, never in comments. When a PR is green, tell me (PR number and head sha); I merge it and report the merge to lane V. Do not start the wave 3 docs issues (#226, #227) or E2 (#229) yet.

14:17 UTC:

> Lane D: issue #55 is fixed; please re-check it and close #222 (C1) if the grep is clean.
>
> Coordinator (session_01SicxwYxR11J2rccj3we8WY) here. I read your report; your reply cannot reach me, so I read your issue bodies and session state instead. Put results in the issue bodies as you did.
>
> What I changed in the body of #55: (1) the D6 line no longer quotes the old term ("D6 (user, 2026-10-17): measured latency, ..."), and the proposal table row D6 now says "Measured latency (see D6 above)"; (2) the 2026-10-17 "Open points for the user" paragraph now reads "Deferred to a later stage, low priority (proposals of the coordinator, not requests of the user): show a monitoring state ...; how the baseline is kept when the app is not running ..." and keeps "Open point for the user: whether the app should also run at night." Nothing else in #55 changed.
>
> Fetch the body of #55 with the issue tool, confirm `best effort` and `picks the route` have no hit in it, write that in the body of #222, and close #222 with state reason completed. Then stop; wave 3 (#226, #227) and E2 (#229) start only on my message.

14:18 UTC:

> Lane D: communication test, no work. Reply with one line "D ack" to session_01SicxwYxR11J2rccj3we8WY using send_message, or tell me the exact error if the send is refused. Do nothing else; finish closing #222 only.

(My send was refused: "From this conversation, you can send only to sessions in this same channel." Messages from the coordinator reach lanes; replies do not.)

14:24 UTC:

> Lane D: write your handoff file now (status + history), then stop. Do not start new work (wave 3 issues #226, #227 and E2 #229 stay unstarted).
> ... create branch `claude/handoff-D` from origin/main, add ONE file `handoff/D.md`, push it (git push -u origin claude/handoff-D). No PR, no change to main. Do not put secrets, tokens or full logs in it. [content list: status, instructions, history, environment, next steps, how to resume]

### Earlier coordinator (session_01TMxtFMs2xYeSu29sQZPCm1, retired), what still applies
- Execute design documentation in issue #154 and follow-ups (#156 index, #157 lint, #164 aurora restyle).
- Do not file human approval as a test case (TC-D-013 and TC-D-030 were removed on purpose).
- Cleanup rule: a PR deletes what it made unused; PR body has "Cleanup: ..." or "Cleanup: nothing became unused".
- Merge origin/main into open PRs when asked; do not merge own PRs; subscribe to PR activity, do not poll CI.
- Batch cleanup order from the user: nothing in the repo may build a design statement on batching or interpret Google text. The user's definition: "Heart rate is measured continuously. Heart rate is compared to the baseline continuously. A stress event is reported immediately and the local response is activated."

### Standing rules
- The user's words verbatim and no interpretation added. Google quoted verbatim with its link; statements that a Google page does not define something carry "(read <date>)".
- `git grep -n -i batch` must show only verbatim Google quotes (with link) and the user's own exception wording.
- Checks before a PR: `scripts/docs-lint.sh markdown`, `scripts/docs-lint.sh links`, `scripts/check-docs-index.sh` (also `lint` and `format`); tests first where a test applies.
- PR body starts with `Requires:` and `Produces:` lines and has a `Cleanup:` line; branch `claude/docs-*`.
- Decisions and status go in issue bodies, never in comments.
- Every new `docs/*.md` is linked from `docs/index.md` in the same PR.
- Stage explicit paths only (never `git add -A`). Do not merge your own PRs. Tell the coordinator the PR number and head sha when green (replies to the coordinator do not arrive; the issue body is the report channel).
- File ownership of lane D (docs/plans/README.md ownership table): `docs/design/**`, `docs/index.md`, `docs/plans/docs.md`, `docs/work-log/docs.md`, `docs/test-plans/docs.md`, `docs/test-reports/docs-*`, docs tooling scripts. Other lane files (wear/, protocol/, docs/plans/biometrics.md for Watch) only when the coordinator directs it. `docs/plans/gates.json` belongs to lane S: do not touch.

## 3. History (in order)
1. D1 (issue #154): lane setup and catalogue (#155), design images P1-P13 and W1-W9 (#158), section documents and solution diagrams (#159), final report (#160). Design approval is the user's, not a test case (#163).
2. D3 lint and format (#157): #161. D2 index and MkDocs site (#156): #162.
3. D4 aurora restyle (#164): tests first and the W4 reference (#165), restyle of all screens (#166), generated watch images and honest sources (#187), cleanup rule and unused style rules deleted (#195), status wording "the aurora look is approved" (#204). Issue #164 closed by the user.
4. Batch cleanup (user order): search outside Watch's files found no hits in my files; Watch's PR #210 had already merged; review done on main. Statements that Google's pages define no "batch" were dated (#220) after re-reading monitor-background and active-data.
5. Wave 0 (user-approved plan, relayed by the retired coordinator): #217 (D6 "Measured latency", baseline sentence verbatim, schedule/scheduler/schedule builder/tasks steps deferred in plan.md and docs/plans/*.md).
6. Wave 1: #219 (C6 section "Urgent alerts and the queue" in docs/protocol.md, pointer in docs/data-layer.md; passive route marked "measured, not used for alerts" in biometrics.md sections 6 and 10 and research/watch.md section 3; retirement note for TC-W-132..136 and 139..143 in docs/test-plans/watch.md; 6.4 is the live measure service; rows C4, C5, C10, E1-E7 in docs/plans/watch.md; plan.md 6.3-6.8 rewritten).
7. Today under the new coordinator: verified wave 0 on main 69feaa5; #224 and #222 closed with grep output in the bodies.

Open questions (not mine to decide): user approval of the design document and the two open design points (tile primary action; Settings opens from the gear) in #154, then a PR flipping G-DOC; then #156 gets the Design section; the user approves the index structure. D3 (#157) and D4 (#164) are done.

## 4. Environment facts
- Works: `scripts/docs-lint.sh markdown|lint|format|fix|links|external` (use `lint` and `format` separately; `fix` rewrites), `scripts/check-docs-index.sh`, `scripts/docs-site.sh` (MkDocs strict build to build/docs-site), `scripts/test/test-design-docs.sh`, `test-doc-checks.sh`, `test-docs-site.sh`, `test-design-style.sh`, `scripts/test/test-gates.sh`, `scripts/check-test-cases.sh docs final|baseline`, `scripts/check-work-log.sh`.
- CI: `script-tests` does not run on PRs; it runs after each merge to main (lane V). PR checks: build, docs-site, conventions, gates, external-links. The Gates workflow needs `Requires:`/`Produces:` in the PR body.
- GitHub is reached through the `mcp__github__*` tools (no gh CLI). The server sometimes disconnects and reconnects: load tools again with ToolSearch.
- WebFetch (deferred, load with ToolSearch) was used to re-read Google pages.
- The permission classifier once denied Bash/python edits to docs; the Edit tool worked.
- Known past failures: committing with `git add -A` swept in a stray fixture (release-no-debug CI failure); new reports not linked from docs/index.md fail docs-site; history ids in older work-log entries must not be rewritten; `render.mjs` needs playwright-core (CJS).
- The plan file mentioned by the retired coordinator is not available; the issue bodies (#222-#229, tracker #235) are the source of scope.

## 5. Next steps (2026-10-17 plan, tracker #235). Start only when the coordinator says so.
1. E2 (#229, wave 2, after E1 which is a user run): record the E1 result against the user's definition (measured continuously / compared continuously / reported immediately and local response activated), per screen state, using the measured numbers of `docs/plans/biometrics.md` section 12.3 only. No user decision is asked: the definition decides. If the route fails, report the failing measurement to the user as an item needing attention, in the answer, not as a question. Checks: docs lint, links, index.
2. C2 docs (#226, wave 3): reword passive-route wording that the live route contradicts: `docs/plans/biometrics.md` sections 2 to 10 (section 6 passive probe shortened to a record; remove "Nothing is open" in section 8), `docs/research/watch.md` section 3, and `docs/plan.md` lines on "Health Services passive monitoring" (scope bullet, the 6.1 research step, the risk row, the decision-table row "Health Services passive monitoring, not `MeasureClient`"). Work-log entries stay (history); `docs/spikes` stays. Verify with `git grep -n -i passive -- docs` hit by hit and the batch rule. Section 11 of biometrics.md becomes the design only once E2 is recorded. NOTE: #219 already marked the passive route "measured, not used for alerts" in biometrics.md sections 6 and 10 and research/watch.md section 3; do only what is still missing.
3. C6 (#227, wave 3): `docs/protocol.md` and `docs/data-layer.md` state, from what they already say, that an urgent `/alert` is sent immediately and that the existing queue-on-reconnect is the exception path (disconnect, phone down, watch down, pairing down, network down); no new rules, no code. NOTE: #219 already added "Urgent alerts and the queue" in protocol.md (with the user's two sentences verbatim) and a pointer in data-layer.md; verify against the issue's pass criterion, fix only what is missing, and if complete put the grep/diff evidence in the issue body and close it without a PR.
4. After the user approves #154: PR flipping G-DOC; then #156 Design section in docs/index.md.

## 6. How to resume (first three commands)
```
git fetch origin && git checkout -B claude/docs-next origin/main && git log --oneline -5
scripts/docs-lint.sh markdown && scripts/docs-lint.sh links && scripts/check-docs-index.sh
git grep -n -i "best effort"; git grep -n -i batch | cut -c1-140
```
Then read the issue (#226, #227 or #229) the coordinator names, and the tracker #235.
