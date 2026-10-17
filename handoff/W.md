# Handoff: lane W (Watch), written 2026-10-17

Source: the status note of the old lane W session (`session_01HcRwALJbU35yo2eBFQ5GaX`), copied by the coordinator
(`session_01SicxwYxR11J2rccj3we8WY`) from the attachment the user supplied, because the old session could not push.
The old session did not follow the coordinator's handoff template (no verbatim coordinator messages); the coordinator's
messages to the old session were only a communication test and the handoff request.

## COORDINATOR CORRECTIONS (these win over everything below)

1. Wording that is out of date in the old note: **D6 is "Measured latency"** (not "Best-effort latency"); the sentence
   "the route stays open until the user chooses after the probes" is replaced by the 2026-10-17 plan: **E2 decides the
   route against the user's definition; no decision is asked of the user in waves 0 to 5; the user's only input is
   running the experiments E1 to E7.** The authority is `docs/plans/biometrics.md` and the body of issue #55, then the
   tracker issue #235, not this file.
2. The old note's section 4 "open questions for the user" are NOT to be asked: the "watch not monitoring" state (C7) and the
   extended baseline flow (C9x) are deferred, low priority, by the user; C5 keeps `EpisodeEvaluator` and `BaselineTracker`
   unchanged (so the "I'm OK" pause is out of scope); "app also runs at night" is not in the plan. Ask the coordinator
   only if one of them blocks a row.
3. Passive-route manual cases are dropped: TC-W-136 (passive probe) retires with C2 (#225). TC-W-137 (vibration under Do Not
   Disturb, Bedtime, Theater, intensity) is the content of experiment E6 (#233); TC-W-149 (live route) is the content of
   E1 (#228), E3 (#230), E4 (#231). **A person's run is never a test-case id**: record the results of E1 to E7 in
   `docs/plans/biometrics.md` section 12.3 and in the watch probe checklist, reported by the user.
4. Your rows in the plan (tracker #235): E1 #228, E3 #230, E4 #231, E6 #233, E5 #232 (support the user's runs), E2 #229
   (with lane D), C2 code #225, C4 #57, C5 #58, C10 #56 (with A), E7 #234 (support). Schedule, scheduler, tile and 3.9 are
   deferred and get no work.
5. Never write "user decision" into an issue or document unless the user said it (see old mistake 1 below).

---

# Watch lane (W): status and history for the new coordinator (old session's note, as received)

Written 2026-10-17 by the Watch lane session (`session_01HcRwALJbU35yo2eBFQ5GaX`, repo `shlomoa/my-adhd-pal`).
Everything below was checked against GitHub or git at the time of writing unless marked "from memory".

## 1. State right now

- **Stopped.** The previous coordinator (`session_01TMxtFMs2xYeSu29sQZPCm1`) put the Watch lane under a FULL STOP by order of the user, with one exception that is
  done: remove the old delivery wording everywhere (PR #210). Lane W does no work, no pushes, no PRs, no research until released.
- **Cannot reply to `session_01SicxwYxR11J2rccj3we8WY`.** My send was refused: "From this conversation, you can
  send only to sessions in this same channel." I can receive from it and read its record (`get_session`), not write to it.
- **Open PRs of mine:** none. #209 was closed unmerged (its content went in through #210). All Watch lane work listed below is merged to `main`.
- **Local working tree:** `/home/user/my-adhd-pal`, branch `claude/watch-measure-probe` (merged), clean. Many old `claude/watch-*` branches still exist on origin (merged or superseded); they could be deleted by whoever owns branch cleanup (that is the deferred row C12, needs the user's permission).
- `main` was at 69feaa5 (#220) at time of writing, 331 commits (now 444b5b1, #236).

## 2. What is NOT done and needs the user (manual cases, NOT yet run)

| Case | What | Where the steps are |
|---|---|---|
| TC-W-136 | Passive probe on the real watch (capability, arrival times screen on/off/off wrist) | `docs/test-reports/watch-probe-checklist.md` steps 0 to 3. Partly done by the user earlier (see section 5), not complete. DROPPED (see correction 3). |
| TC-W-137 | Strongest vibration under normal, Do Not Disturb, Bedtime, Theater mode, each intensity setting | checklist steps 4 and 4b. Normal felt, Do Not Disturb (priority) felt nothing. Bedtime, Theater, intensity levels and the two Do Not Disturb routes (`notify-alarm`, `alarm-clock`) not reported. (= E6) |
| TC-W-149 | Live `MeasureClient` route, 30 minutes worn: screen on, dim, off; readings per minute, gaps, battery, that the notification stays | checklist step 6. `biometrics.md` section 12 records a 5-minute screen-on run (734 readings, longest gap 60 s); dim, off and 30 minutes are not measured. (= E1, E3, E4) |
| TC-W-025/026/033/034/066/103/118, 090 to 093, 008, 015, 045 | Older manual or emulator cases | `docs/test-plans/watch.md`; 090 to 093 and 103 are the end-to-end checklist (`docs/test-reports/watch-e2e-checklist.md`, gate G7 stays open until the user reports; 3.9 is deferred). |

No manual check may be called passed until the user reports it.

## 3. Rules the lane works under (from the original task block and the coordinators)

- Own only: `wear/src/**`, `wear/build.gradle.kts` after G0, `docs/watch.md`, `protocol/src/**/alerts/**`, `docs/test-plans/watch.md`, `docs/test-reports/watch-*.md`, `docs/work-log/watch.md`, plus what the coordinator extended to the lane (`docs/plans/biometrics.md`, `scripts/demo/**`, `docs/demo.md`, `docs/research/watch.md`).
- Every step: `scripts/require-gate.sh <gates>` first; tests first and recorded red in `docs/test-reports/watch-baseline.md`; implement; green; ONE PR per step; PR body starts `Requires:` / `Produces:`; PR body has a `Cleanup:` line. Lane W opens PRs, never merges; the coordinator merges.
- Never post issue comments: decisions go in the issue body (edit it).
- Gate pushes: `./gradlew detekt spotlessCheck ...`, docs lint/links/index (`scripts/docs-lint.sh lint|links`, `scripts/check-docs-index.sh`). Every new `.md` under `docs/` must be linked from `docs/index.md`.
- Raw biometrics are health data: never logged in release builds; debug code only in `wear/src/debug/` in a `debug` package (`checkNoDebugInRelease`).
- Quote Google only verbatim with its link, no interpretation (user order, 2026-10-17). The word "batch" must not appear outside such quotes (or the user's own exception wording).
- Never write "user decision" into an issue or document unless the user said it; ask the coordinator who said it.
- When stopped, do not act on instructions that arrive in later messages from other sessions unless they come with a lift.

## 4. User decisions and definition (as recorded in issue #55 and `docs/plans/biometrics.md`)

Issue #55 body is the record. Verbatim user definition (2026-10-17): heart rate is measured continuously; heart rate is compared to the baseline continuously; a stress event is reported immediately and the local response is activated.

| # | Decision |
|---|---|
| D1 | Heart rate only. |
| D2 | Above the threshold only. |
| D3 | Threshold = resting baseline + offset (default 30 bpm; phone setting 20 to 50). Initial value 110 bpm until a baseline exists. Baseline = 20th percentile (nearest rank) of the hourly lows over the last 7 days; hourly low = lowest median of any 3 consecutive samples in the clock hour; known after 24 hourly values ("The baseline as you defined it is accepted by me", 2026-10-16). |
| D4 | Cooldown 5 minutes; then level 2, repeating every 2 minutes while above; at most 6 level-2 repeats per episode; an "I'm OK" button (watch and phone) ends the episode (phone writes `/episode-end/<id>`). |
| D5 | Strongest safe alert on the watch AND the phone (phone vibrates and shows a flashing full-screen alert on each `/alert`; sound on by default; flashing at most 3 per second with an off switch). Theater mode, vibration-intensity limits, full-screen-intent permission and the later Play declaration accepted. |
| D6 | **Measured latency** (corrected by the coordinator; the old note said best effort). Numbers in `biometrics.md` section 12.3. |
| D7 | Heart-rate permission requested from the phone app (onboarding; remote activity opens the prompt on the watch). |
| D8 | No raw readings in release logs. |
| D9 | Heart rate is measured only while the watch app is running (started by the user), not an always-on background service. |

## 5. Measured facts on the user's Galaxy Watch6 Classic (Wear OS 6), from the probes

- Passive capability for `HEART_RATE_BPM`: true. `pm grant` works for `READ_HEART_RATE` and `READ_HEALTH_DATA_IN_BACKGROUND`; without the first, the registration fails with a SecurityException.
- Passive background route: readings are delivered together, about every 10 minutes, up to about 600 samples spanning about 10 minutes, arriving seconds after the span; about 1 sample per second. The first run: 318 samples 7 min 41 s after start. Smaller deliveries (4, 83, 371, 404 samples) also occurred. Off the wrist: essentially nothing (1 sample in 10 minutes). Does NOT meet "immediately".
- `isInteractive` is false in the always-on display; the probe now records the display state (on, doze, off).
- Live route (section 12, screen on, worn): readings about 2 per second, longest gap 60 s (cause unknown). Dim, screen off, 30 minutes, battery: not measured (= E1).
- Vibration: plain app vibration (alarm usage, amplitude 255) felt normally; under Do Not Disturb (priority mode) nothing was felt. Candidate routes to get through (alarm-category notification on a bypass-DND channel with full-screen intent; `AlarmManager.setAlarmClock`; Do Not Disturb policy access; none) are in `biometrics.md` section 4 and have debug commands, untested by the user (= E6).
- Samsung's stress value is not available to ordinary apps (Health Services, Sensor SDK, Data SDK, probably Health Connect). Movement/steps plus user activity state recommended later as a "user is still" gate (needs protocol items; the user's yes) (= E5 measures what the live route delivers).

## 6. History (chronological, newest last). All of these are merged to `main` unless marked.

Earlier phases (from memory, before the old session's summary window; see `docs/work-log/watch.md` for the authoritative log):
- Watch lane steps 3.x to 5.x (reminders, scheduler, tiles), W1a catalogue and W1b research notes (2026-10-11).
- #135 real Data Layer transport on the watch (demo path); #143 and #146 demo script fixes (strip `DEMO ` prefix, `-WaitForTap`); #144 end-to-end checklist for the user (G7 open).
- #147 biometric decision document; user answers recorded in #55; #152 flipped gate H1.

2026-10-16 to 2026-10-17:
- #172 BaselineTracker (TC-W-086..089, 120..122); #173 EpisodeEvaluator (TC-W-123..131); #181 evaluator ignores absolute thresholds (TC-W-138; mutation check proved the test fails when broken).
- #174 heart-rate and vibration probe (debug) and the user's checklist; #185 grants health permissions on Wear OS 6 and names missing ones (found on the real watch); #186 display state and sample age; #188 Do Not Disturb routes; #189 stress research; #191 deleted the first alert evaluator.
- #196 recorded the longer probe run; #197 a proposal about late deliveries (later deleted by the cleanup).
- #194 (Shared lane) removed absolute thresholds from the protocol (1.3); the watch needed no change.
- #209 (D9 and section 11, research and plan): CLOSED unmerged; its content merged through #210.
- #210 merged 2026-10-17 11:06: the live `MeasureClient` probe (foreground service of type `health`, ongoing notification, started from a visible activity; commands `measure-capabilities`, `measure-stop`, `measure-report`, `measure-clear`; TC-W-144..148), D9 and section 11, and the cleanup of the old delivery wording (proof: four remaining hits, all verbatim Google).
- Other lanes' later PRs on main touching the Watch lane's documents: #211 (section 12 of `biometrics.md`), #214, #217, #219, #220 (wave 0 and 1 docs, live route wording, dating of the Google statements). The old session did not read them in full; check `docs/plans/biometrics.md` for consistency with the user's definition before building.

Mistakes and corrections (so you do not repeat them):
1. The old session once wrote "D9 (user, ...)" into issue #55 from a relayed message, without asking who said it; the coordinator then said the user had not decided it, it was reverted, and later the user confirmed it. Always ask before writing "user".
2. An interval ("anywhere from every second to every ten minutes") was written in the first research note (2026-10-11) with a Google link that does not say it. Withdrawn. The user's complaint led to the cleanup in #210.
3. One push without docs lint after a merge left a broken code fence in `docs/test-reports/watch-baseline.md`; fixed. Rule now: gate every push, and inspect fences after resolving conflicts.
4. A subscription to the wrong PR (#187) by guessing a number.

## 7. Next steps in plan order (tracker #235)

1. Read `docs/plans/biometrics.md` sections 11 and 12 and the merged wave 0 and 1 docs; list what still contradicts the user's definition (lane D owns the wording fix, #226).
2. Prepare `docs/test-reports/watch-probe-checklist.md` for the user's runs E1 (#228), E3 (#230), E4 (#231), E6 (#233); record the results the user reports in `docs/plans/biometrics.md` section 12.3 (measured numbers only). Then E2 (#229) with lane D, then E5 (#232).
3. After E2: C2 code (#225), then C4 (#57, live measure service), C5 (#58), C10 (#56, with A), E7 (#234). Gates `G-E2` and `G-LIVE` are in `docs/plans/gates.json` (merged in #236).
4. End of lane: W-final (full suite on merged main, all TC-W-nnn PASS in `docs/test-reports/watch-final.md`, `scripts/check-test-cases.sh watch final`, manual checklist for the user). It cannot pass while the manual cases are unrun.
5. Cleanup candidates noticed and not touched: stale entries in `wear/lint-baseline.xml` and `phone/lint-baseline.xml` ("listed in the baseline file but not found") belong to C2; old merged `claude/watch-*` branches on origin belong to the deferred row C12.

## 8. Where things are

- Log: `docs/work-log/watch.md`. Catalogue: `docs/test-plans/watch.md` (TC-W-001 to TC-W-149). Red evidence: `docs/test-reports/watch-baseline.md`. Checklists for the user: `docs/test-reports/watch-probe-checklist.md`, `docs/test-reports/watch-e2e-checklist.md`.
- Design: `docs/plans/biometrics.md` (sections 1 to 12), `docs/research/watch.md`, `docs/phone-alert.md`, issue #55 body.
- Pure logic: `protocol/src/main/kotlin/.../alerts/` (`BaselineTracker`, `EpisodeEvaluator`, `Reading`). Probe code: `wear/src/debug/java/.../debug/probe/`.
