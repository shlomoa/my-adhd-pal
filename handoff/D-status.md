# Lane D (Documentation) - status and history

Written 2026-10-17 for the new coordinator (session_01SicxwYxR11J2rccj3we8WY).
Lane session: this one (repo shlomoa/my-adhd-pal). Previous coordinator: session_01TMxtFMs2xYeSu29sQZPCm1 (retired).

## Communication
- Messages from the new coordinator reach this session. Sends from this session to it are refused:
  "From this conversation, you can send only to sessions in this same channel."
- So results are written into issue bodies (as the coordinator asked). Nothing else is pending a reply.

## Current state (at main 69feaa5 and later)
- Open PRs of lane D: none.
- Closed today: #224 (C9) and #222 (C1), both verified on main, results in the issue bodies, closed as completed.
- Not started, waiting for the coordinator's message: #226, #227 (wave 3 docs), #229 (E2).
- Open for approval by the user (not mine to decide):
  - #154 / gate G-DOC: the design document `docs/design/` and two open design points (tile primary action; Settings opens from the gear).
  - #156 / gate G-INDEX: add the Design section to `docs/index.md` after G-DOC; the user approves the index structure.
- Local branches in the container (all pushed or disposable): claude/docs-w0 (no changes), claude/docs-wave0, claude/docs-wave1, claude/docs-wave1b, claude/docs-batch (no changes).

## Wave 0 verification (today, main 69feaa5)
- `git grep -n -i "best effort"`: 0 hits (code included). `git grep -n -i "picks the route"`: 0 hits.
- D6 row: docs/plans/biometrics.md:42 "Measured latency" with the section 12.3 numbers.
- C9 quote: docs/plans/biometrics.md:534, extended flow marked deferred, low priority.
- docs-lint markdown passes, links exit 0, check-docs-index exit 0.
- `git grep -n -i batch`: only the user's exception sentence (biometrics.md:15, protocol.md:155) and Google quotes with links; our statements that Google's pages define no "batch" carry "(read 2026-10-17)" (biometrics.md ~414 and ~484, research/watch.md ~128).
- Issue #55 body re-read after the coordinator's edit: clean.

## History of merged lane D PRs
- Design documentation (issue #154, steps D1.1 to D1.4): #155 (setup), #158 (images), #159 (sections and solution), #160 (final report), #163 (approval is the user's, not a test case) - lane setup and test catalogue, phone and watch boards P1-P13 and W1-W9, section documents, solution diagrams (data flow, event flow), final run. Stray fixture removed in a follow-up after a release-no-debug CI failure.
- Index and site (issue #156, D2): #162 - `docs/index.md`, MkDocs strict build (`scripts/docs-site.sh`), `scripts/check-docs-index.sh`; baseline reports linked.
- Lint and format (issue #157, D3): #161 - `scripts/docs-lint.sh` (markdown, lint, format, fix, links, external).
- Aurora restyle (issue #164, D4): #165 (D4.1 tests and W4 reference), #166 (D4.2 restyle), #187, #195, #204 - palette, generated watch images (W1-W9 supplied artwork, W4 approved reference), style tests `scripts/test/test-design-style.sh`; the look was approved by the user (issue #164 closed).
- Conventions: documentation and cleanup rules in `docs/conventions.md` (PR body needs a "Cleanup:" line).
- Wave 0 (user-approved plan, relayed by the previous coordinator): #217 - D6 "Measured latency", baseline sentence verbatim, schedule/scheduler/schedule builder/tasks steps marked deferred in plan.md and docs/plans/*.md (gates.json untouched, lane S).
- Wave 1: #219 - urgent alerts and the queue (C6, docs/protocol.md section "Urgent alerts and the queue", pointer in data-layer.md); passive route marked "measured, not used for alerts" (biometrics.md sections 6 and 10, research/watch.md section 3); TC-W-132..136 and 139..143 noted as retired later (137 and 138 are not probe cases); 6.4 is the live measure service; rows C4, C5, C10, E1-E7 in docs/plans/watch.md; plan.md 6.3-6.8 rewritten.
- #220 - "(read 2026-10-17)" on the three statements that Google's pages define no batch (re-read monitor-background and active-data; neither defines it).

## Batch-wording cleanup (user order)
- Definition (user): measured continuously, compared to the baseline continuously, stress event reported immediately and the local response activated.
- Search outside Watch's files: no hits in docs/design, docs/*.md, README, templates. Hits in issue #55 and PR bodies #211, #197, #196, #186, #172: previous coordinator edited #55 and #211; merged PR bodies stay as history.
- Review of Watch's PR #210 (merged before review): done on main instead, result above.

## Rules I follow
- Tests first where a test applies; PR body starts with Requires: and Produces: and has a Cleanup: line.
- User's words verbatim, no interpretation added; Google quoted verbatim with its link.
- Stage explicit paths only (never `git add -A`); do not merge my own PRs; subscribe to PR activity, do not poll.
- Decisions and status go in issue bodies, not comments.
- Do not touch other lanes' files (Watch owns wear/, protocol alerts, docs/plans/biometrics.md only as directed by the coordinator).

## Known quirks
- Bash/python edits of docs were denied twice by the permission classifier on the first wave 0 attempt; the Edit tool worked. The GitHub MCP server sometimes disconnects and reconnects (load tools again with ToolSearch).
- The plan file cited by the previous coordinator (/root/.claude/plans/...) is not in this container.
