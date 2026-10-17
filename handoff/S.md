# Handoff: lane S (Shared / data), written 2026-10-17

For the next lane S session, started by coordinator `session_01SicxwYxR11J2rccj3we8WY`. You know nothing but this file and
the repo. Repo `shlomoa/my-adhd-pal`. The previous lane S session could receive messages from the coordinators but its
sends to the new coordinator were refused ("you can send only to sessions in this same channel"), so do not count on being
able to reply: the user relays. No secrets or logs are in this file.

## 1. Status (one line per issue or PR; checked 2026-10-17 14:25 UTC)

- **PR #236 / issue #223 (C3)**: MERGED, main `444b5b1` ("gates.json: gates G-E2 and G-LIVE for the live route"), branch
  `claude/shared-gates-e2-live`, head was `f4e81ab9dadb0c16a938fbb35a87411147eab3e8` (all checks green). Nothing left except
  closing/updating issue #223 if the coordinator wants (decisions go in the issue body, never a comment). New open gates:
  `G-E2` (owner `D + W`, requires G5, H2) and `G-LIVE` (owner W, requires G-E2, G5); two rows were added to the
  `docs/plans/README.md` gate table.
- **PR #216** (deferred steps in `gates.json`): merged `48020b6`.
- **PR #208** (lint crash scenario and recipe, docs): merged.
- **PR #207** (tooling freshness `docs/research/tooling.md`): merged `0cb191b`.
- **PR #203** (issue #198, phone removes its own prompts): merged `21d6d59`.
- **PR #194** (protocol 1.3, no absolute thresholds): merged `d7256ce`.
- **PR #192** (post-merge script tests): was handed to lane V by order; V's own PRs replaced it; I unsubscribed.
- **Issue #205** (main-red, lint crash, lane V's sub-agent): I added a "Recipe (lane S)" section to the body. The fix PR is the
  sub-agent's (`claude/validation-fix-lint-crash`), not mine. `#218` (V) already made `test-checks.sh` start TC-S-017 on a fresh
  Gradle daemon.
- **Issue #168 / #171 / #190 / #198 / #55**: done or handed over (see history).
- Open PRs of lane S: none. Local branch `claude/shared-lint-crash-repro` has no commits.

## 2. Instructions I hold

### 2.1 Standing rules (from the previous coordinator and the user; follow them unless a new message changes them)

- Tests first, and the red state is recorded: the tests change in a commit before the implementation; add the result to
  `docs/test-reports/shared-baseline.md` (command, error excerpt); then green. Catalogue cases go in `docs/test-plans/shared.md`.
  My test ids are `TC-S-nnn`; the highest used is **TC-S-184**, so the next free id is TC-S-185.
- One PR per step, small PRs, fewer PRs when docs changes are small (fold them). Branch names `claude/shared-<topic>`.
- PR body starts with the two lines `Requires: <gate ids or none>` and `Produces: <gate or none>` (the `Gates` check fails
  otherwise), and has a `Cleanup:` line naming what you deleted ("Cleanup: nothing became unused" otherwise). Cleanup is part
  of every step: delete what your change made unused (code, tests, scripts, docs, imports), do not hunt repo-wide.
- Work-log entry per PR in `docs/work-log/shared.md` (checked by `scripts/check-work-log.sh docs/work-log/shared.md`).
- New docs must be linked from `docs/index.md` (`scripts/check-docs-index.sh`).
- Before every push run `./gradlew detekt spotlessCheck check` (about 1 to 6 minutes) and `scripts/docs-lint.sh markdown`.
- When a PR conflicts with main, merge `origin/main` into it (never rebase or force-push), keep both sides of the work log and
  baseline files, push. Prefer one merge of main into all open PRs.
- I never merge a PR or enable auto-merge. The coordinator merges when green (build, external-links, docs-site, gates,
  conventions are the per-PR checks; the slow `script-tests` run after each merge on main, lane V watches and opens `main-red`).
- Decisions and status go in issue bodies, never in issue comments.
- Gates: human gates (H1, H2) flip only when the user decides, recorded in the issue body; never flip a gate on a relayed
  message. Only the gate a PR produces changes status (`scripts/check-pr-gates.sh`).
- Commit messages end with the two lines `Co-Authored-By: Claude <model> <noreply@anthropic.com>` and `Claude-Session: <url>` as
  given in the session's system reminder; PR bodies end with the "Generated with Claude Code" line and the session URL.
- STOP rule: when a coordinator says STOP, make no commits, pushes, PRs, research, tests or merges, leave branches as they
  are, do not reply unless stopping damages something. Only a message saying work resumes (or a task lifting the stop for one
  named task) restarts work. There were four stops in this session.
- Do not edit `docs/plans/*.md` or `docs/plan.md` text unless the task says so (lane Docs owns that text); the one exception
  was the two README gate rows in #236, which `scripts/test/test-gates.sh` requires.

### 2.2 Coordinator messages, verbatim (2026-10-17, UTC time of the message in the header; HTML escapes removed)

14:24, new coordinator `session_01SicxwYxR11J2rccj3we8WY` (the handoff request, binding for this file):
> Lane S: write your handoff file now (status + history), then stop. Do not start new work. [...] create branch `claude/handoff-S` from origin/main, add ONE file `handoff/S.md`, push it [...] No PR, no change to main, no change to the branch of PR #236 [...] end your turn with the file's commit sha.

14:12, new coordinator (resume, issue #223):
> Lane S: resume. Wave 0 starts now; issue #223 (C3) is yours. [...] #223 (C3), what remains: docs/plans/gates.json gets new gates for (1) E2: "live route verified against the user's definition" (issue #229, lane D with W) and (2) the live measure service (C4, issue #57). The schedule, scheduler, schedule builder and task items of G5, G6 and G7 stay in the deferred list you already merged. Propose the exact gate split in the PR (names, requires, owners); I approve. No gate other than the one the PR produces changes status. Rules: tests first: scripts/test/test-gates.sh new cases red with the red commit recorded, then green; branch claude/shared-*; PR body starts with `Requires:` and `Produces:` and has a `Cleanup:` line; the Gates workflow passes; decisions and status in the issue body, never in comments; update your work log docs/work-log/shared.md. When the PR is green, tell me the PR number and head sha; I merge it and report the merge to lane V. Do nothing else until the next message (no 1.2 protocol work, no schedule work: it is deferred, no new work and no new tests).

14:18, new coordinator:
> Lane S: communication test, no work. Reply with one line "S ack" [...] or tell me the exact error if the send is refused. Do nothing else; keep working on PR #236 only.
(Result: refused with "From this conversation, you can send only to sessions in this same channel".)

12:50, previous coordinator `session_01TMxtFMs2xYeSu29sQZPCm1` (wave 0 row C3, first task):
> USER-APPROVED PLAN, wave 0, row C3 [...] The stop on lane S is lifted for ONE task: gates.json. The user de-prioritized everything related to schedule, scheduler, schedule builder and tasks inside G5/G6/G7 (steps 3.3, 3.4, 3.9, 4.1, 4.2, 5.2-5.3). Propose the minimal gates.json change that marks these deferred / splits G6 so the non-schedule items (4.3 settings, 4.4 prompt publishing, 4.6 states) can close; scripts/test/test-gates.sh first (red), then green; do not edit docs/plans/*.md or docs/plan.md (lane Docs does the text). PR body: Requires:/Produces:, Cleanup: line. Reply with the PR number only; then you are stopped again.

10:30, previous coordinator: "STOP AGAIN, by order of the user, until he says otherwise. No commits, pushes, new PRs, research, tests, merges. [...] Ignore my last requests to you (definition text, lint-baseline cleanup, merge main) [...] Leave branches and open PRs (#199, #209, #210) as they are."

10:21, previous coordinator: "the user's stop was about continuing with the 10-minute batch route. The user confirms the decision D9: heart rate is measured only while the watch app is running (live route, readings about once per second). The stop is LIFTED for Shared and Android. Continue your plans; keep PRs small; the Watch lane will tell you if a protocol item is needed (for example a 'monitoring' state in /status/watch, a Shared change, only after the user decides that point). Merge main into your open PRs (#208, #199) and tell me when green."

10:20, previous coordinator: "STOP by order of the user. Stop all work now [...] Reason: the user decides the heart-rate measurement approach before more is built on it."

10:17, previous coordinator: "#203 merged (21d6d59). #208 now conflicts (work log again). Merge main into #208, push, tell me when green; I merge right away. Then your next job: wait for S-final inputs or any red V assigns; no new PRs without telling me what for."

10:11, previous coordinator: "#207 is merged (0cb191b). #203 was green but now conflicts [...] Merge main into #203 and then #208 (keep both sides), push, tell me when each is green. Next time: one merge of main into all your open PRs, and open fewer PRs that all append to the same work-log file; fold small docs changes into one PR when you can."

10:08, previous coordinator (lint crash, correction): "[...] you hold the recipe and the facts, so do it. (1) NOW send your recipe and evidence [...] to the #205 sub-agent and to V; (2) write the section in docs/conventions.md [...] put the recipe in the issue #205 BODY [...] adding a 'Recipe (lane S)' section; (3) coordinate with the #205 sub-agent so your docs PR and its fix PR do not conflict: your PR is docs only (scenario and recipe), its PR is the fix; then the freshness check I asked for follows."

10:05, previous coordinator (freshness check): "[...] a freshness check, docs only. For every pinned version in gradle/libs.versions.toml, the Gradle wrapper, the CI tool pins (Node/Python/markdownlint etc.) and tools/docs-site/requirements.txt: current value, latest STABLE release from the official source [...], and whether the newer release mentions a fix for the Kotlin Analysis API lint crash [...]. Put the table in docs/research/tooling.md [...] Do NOT upgrade anything yet; upgrades need the user's yes. One PR."

09:22 (earlier that morning), previous coordinator: "FULL STOP by order of the user. Stop all work now: no new commits, no pushes, no new PRs, no new research or tests, no merges. [...] Only lane V continues."

08:36, previous coordinator: "MERGE FREEZE by order of the user. No PR is merged by anyone until I lift it [...] Reply only if you have an auto-merge enabled or a merge in flight." (Lifted 10:03.)

The user (2026-10-17, during this session): the previous coordinator "is no longer with us"; `session_01SicxwYxR11J2rccj3we8WY` is the new coordinator. The user also asked for this handoff file to be readable by the new coordinator.

## 3. History (this session, in order; shas where known)

2026-10-16 (earlier in this lane, from my notes; PR numbers as recorded):
- Gate flips G-DEC, G2b, G3; steps 1.5, 1.8b, 2.4 (real Data Layer source), 2.5 (repositories, event storer, schedule version store).
- Spike PRs #130, #140; #126 cleanup updates; #145 DataStore reopen race fix (`cancelAndJoin`); #153 human-gate wording.
- Protocol 1.2 (#169, issue #168): `Alert.level`/`episodeId`, `AlertSettings.offsetBpm`/`initialBpm`, `EpisodeEnd`
  (`/episode-end/<id>`), `WatchStatus` (`/status/watch`). Issue #171, four needs: #175 (alert level and episode in history, Room v2
  `MIGRATION_1_2`), #176 (episode-end publishing and cleanup), #177 (settings offset, initial, cooldown 300), #178 (last
  watch status). I merged main into #177 and #178 after conflicts (docs files only).

2026-10-17 (UTC, the main events):
1. #182 (merged): removed the absolute 150 bpm default (issue 55 decision, only baseline + offset).
2. #184 (merged): first "When lint itself crashes" docs section (research: bluecard issue 227 has the same stack; no fix in AGP 9.2/9.3 notes).
3. Issue #190 (script tests after each merge): I wrote red tests TC-S-170..174 and PR #192 (post-merge workflow, runner, main-red
   handler); the coordinator then moved #190 to the new lane V, I stopped and unsubscribed. Local timings of the 11 script
   tests (seconds): structure 221, pairing 73, checks 236, ci 82, protocol-spec 0, gates 1, spike 5 (fails locally: no
   PowerShell), design-docs 0, doc-checks 16, docs-site 19, design-style 34 (fails locally: image tools).
4. #194 (merged `d7256ce`): protocol 1.3. It deleted `AlertSettings.thresholds`, `Threshold`, `Direction`, `Limits.MAX_THRESHOLDS`
   and the legacy `AlertEvaluator` (only `Reading` survived, in `alerts/Reading.kt`); phone settings lost thresholds. Tests
   TC-S-180..182. Deploy note: a 1.2 watch drops a schedule without `thresholds`, so install phone and watch together.
5. #203 (merged `21d6d59`, issue #198): `PublishedPrompts`/`DataStorePublishedPrompts`, `PromptCleaner` (removes the prompt when the
   ack or selection with the prompt's id is stored, and for expired prompts; sweep at start and every minute in
   `WatchEventProcessor`). Tests TC-S-190..195. Limit: prompts published before it are not tracked.
6. #207 (merged `0cb191b`): `docs/research/tooling.md`. Every Gradle/npm/PyPI pin equals the latest stable on 2026-10-17; only
   detekt `2.0.0-alpha.6` is an alpha pin (all 2.x are alphas); GitHub Actions are 3-4 majors behind (checkout v4->v7,
   setup-java v4->v6, setup-python v5->v7, upload-artifact v4->v7, gradle/actions v4->v6, release notes not read); local Node 22 vs
   LTS 24. No newer stable AGP (9.4.1), Kotlin (2.4.21) or Gradle (9.8.1) exists, so nothing fixes the lint crash. Nothing upgraded.
7. #208 (merged 10:22): lint crash scenario and failing recipe in `docs/conventions.md`; issue #205 body got the "Recipe (lane S)" section.
8. #216 (merged `48020b6`): `docs/plans/gates.json` has a top-level `deferred` array `{step, gate, reason}`: 3.3 and 3.4 (G5),
   4.1 and 4.2 (G6), 3.9 (G7), 5.2 and 5.3 (`gate: null`, tile steps in no gate). No gate id, owner, status or requires changed.
   Test TC-S-183 in `scripts/test/test-gates.sh`.
9. #236 (merged `444b5b1`, issue #223): gates `G-E2` and `G-LIVE`, tests TC-S-184, two README rows.

Things that failed or were hard, and why:
- Lint crash `:phone:lintAnalyzeDebugUnitTest` ("Unexpected failure during lint analysis of AlertActivityTest.kt",
  KotlinIllegalArgumentExceptionWithAttachments from LLFirModuleLazyDeclarationResolver). Intermittent; I never found a
  deterministic trigger. Seen: plain `./gradlew --console=plain check --rerun-tasks` on main in one checkout (1 crash in 4 runs;
  runs 3-4 cut off); `test-checks.sh` TC-S-017 after `test-structure.sh` and `test-pairing.sh` in the same tree (also lane V:
  twice, passes in a clean worktree); Android lane after merges. Not reproduced in clean `:phone:lintAnalyzeDebugUnitTest` x3 or
  `check` after switching a tree from 777853d to main. No upstream fix; no code retry in CI (decision). Open experiment is in
  the #205 body.
- `MigrationTestHelper` cannot find schema assets under Robolectric, so `MigrationTest` builds the v1 DB from committed `1.json`.
- A KDoc containing a slash-star opens a nested comment in Kotlin; write `<id>` in comments, not paths with a star.
- Keep detekt limits in mind: `TooManyFunctions` 11, `MaxLineLength` 120, `ReturnCount` 2, `LoopWithTooManyJumpStatements`.
- Never run `pkill -f` (it killed my own shell once).
- Earlier mistakes fixed: raw logs ignored by `*.log` in `.gitignore` (use `git add -f`), a manifest edit script that matched the
  wrong `</intent-filter>`.

Open questions at the end of this session: whether the user will approve the GitHub Actions upgrade PR; whether the Watch
lane needs a `monitoring` state in `/status/watch` (a Shared protocol change, only after the user decides); the deferred
schedule/task work (see section 5); sleeping-apps three-day recheck and a hardware `-OnlyCases C1` run of the moved spike apps
are user-run (not verified whether done).

## 4. Environment facts

- Setup in a fresh cloud container: `scripts/bootstrap-android.sh` (idempotent; installs Android command-line tools, platform
  37.0, build-tools 37.0.0 and platform-tools, writes `local.properties`; adds a Maven mirror init script if Central answers 429).
  Known failure: a half-created `build-tools/36.0.0` without `aapt` makes every Gradle run fail with "Installed Build Tools
  revision 36.0.0 is corrupted"; remove that directory and rerun the bootstrap.
- Commands that work: `./gradlew detekt spotlessCheck check` (before every push), `./gradlew spotlessApply`,
  `./gradlew :phone:testDebugUnitTest`, `./gradlew :protocol:test`, `./gradlew lintDocs spotlessCheck` (docs), `scripts/docs-lint.sh markdown|links`,
  `scripts/check-docs-index.sh`, `scripts/check-work-log.sh docs/work-log/shared.md`, `scripts/test/test-gates.sh`, `scripts/test/test-scripts.sh`,
  `scripts/test/test-post-merge.sh`, `scripts/test/test-protocol-spec.sh`.
- Slow script tests (structure, pairing, checks, ci and others) run after each merge on main in lane V's runner, not on PRs.
- Network from the container: Maven Central, Google Maven, PyPI, npm, nodejs.org, endoflife.date and `git ls-remote` work;
  the GitHub REST API is rate limited (403); use the GitHub MCP tools (`mcp__github__*`) for PRs and issues.
- Pins: AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1, detekt 2.0.0-alpha.6, Room 2.8.5, Hilt 2.60.1, Java 21 in CI.
- File ownership: lane S owns `:protocol` (including `alerts/` shared with Watch), `docs/protocol.md`, `docs/data-layer.md`,
  `phone/**/data/**`, `phone/**/link/**`, `phone/**/di/**`, `.github/**`, `scripts/**` (lane V may edit scripts for its
  fix), `docs/plans/gates.json`, `docs/test-plans/shared.md`, `docs/test-reports/shared-*`, `docs/work-log/shared.md`. Lane Docs owns
  `docs/plan.md` and `docs/plans/*.md` text; lane W owns `:wear` and `alerts/` evaluators; lane A owns phone UI; lane V owns the
  post-merge runner (`scripts/ci-post-merge/`).
- Protocol today: 1.3 (`docs/protocol.md`). Wire facts: DataItems only, writer-owns-cleanup, `/schedule`, `/prompt/<id>`,
  `/ack/<id>`, `/selection/<id>`, `/alert/<id>`, `/episode-end/<id>`, `/status/watch`, `/hello/<side>`.
- The previous session's scratch notes (may not exist for you): `/tmp/claude-0/-home-user-my-adhd-pal/3b3af283-502a-594e-af5d-b21a22443ec5/scratchpad/lane-s-status-and-history.md`.

## 5. Next steps in plan order, and what not to do

The plan is the 2026-10-17 "align the product plan with the user's biometric definition"; tracker issue #235. Lane S's rows:
1. C3 (issue #223) is done (#216, #236). Close or update the issue body if the coordinator asks.
2. S-final (needs G5, G6, G7; note G6 and G7 are open and now carry deferred steps): final test run and `docs/test-reports/shared-final.md`
   (all `TC-S-*` PASS, zero skipped, `scripts/check-test-cases.sh shared final`). Wait for the coordinator's inputs.
3. Watch-lane protocol items only when released: for example a `monitoring` state in `/status/watch` (Shared change after the user
   decides). That would be protocol 1.4 (additive) with tests first; ask first.
4. Lint crash: lane V's sub-agent owns the fix (issue #205). If asked, run the open experiment in the #205 body.
5. Tooling: the GitHub Actions major upgrade is one later PR and needs the user's yes.

Do NOT do: schedule, scheduler, schedule builder, task work, tile work 5.2/5.3 (deferred, see the `deferred` array in
`docs/plans/gates.json`); protocol 1.2/1.3 rework unless released; flipping human gates; merging PRs; new PRs without telling
the coordinator what they are for (when you can reach it); upgrading any tool without the user's yes; adding a CI retry for the lint crash.

## 6. How to resume (first three commands)

```sh
git fetch origin && git checkout -B claude/shared-<topic> origin/main && git log --oneline -8
scripts/bootstrap-android.sh && ./gradlew --console=plain detekt spotlessCheck check
sed -n '1,80p' docs/work-log/shared.md | tail -40 && jq '.gates[] | {id, status, owner, requires}' docs/plans/gates.json
```

Then read the coordinator's first message, the tracker issue #235 body, and ask the user (the relay) if you cannot reach the coordinator.
