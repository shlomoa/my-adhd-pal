# Work log: planning and orchestration

Entries by the planning threads and the orchestrator session. Index and entry format: [`../work-log.md`](../work-log.md).

## 2026-10-11 - Plan for phone app, watch app and protocol in one repo

- Merged two plans into [`docs/plan.md`](../plan.md): the phone-app/protocol plan and the watch-side hand-off
  (reminders on the watch, connection state, biometrics, debug vs release, process rules).
- Decided: one monorepo in `my-adhd-pal` with `:protocol`, `:wear` and `:phone`.
- Open: transport (DataItems only vs items + messages); to be decided in the plan PR.
- No code changed.

## 2026-10-11 - Execution plans and gate bootstrap (S0)

- Added [`docs/plans/`](../plans/README.md): three parallel lane plans (shared/data, Android, watch), gates, stop rule, schedule.
- Added `scripts/require-gate.sh`, `scripts/check-pr-gates.sh`, `scripts/check-test-cases.sh`, `docs/plans/gates.json` and the `Gates` CI workflow.
- Validation: script test cases written first and recorded red (`docs/test-reports/bootstrap-baseline.md`), then green (`bootstrap-final.md`).
- Assumptions to confirm: lane split, transport = DataItems only, minimal phone scope (see plans README).
- After merging the approved `plan.md` (milestones 0a to 7), the lane plans and `gates.json` were re-mapped to its step ids (16 gates, spikes and human gates included).
- Gate G-BOOT is set to `merged` in this change; it takes effect on `main` when the change is merged.

## 2026-10-11 - Section-by-section review of the merged plan

- Reviewed [`docs/plan.md`](../plan.md) with the user, section by section, merging the architecture thread's plan with the
  watch thread's hand-off. Approved sections 1 to 8 with the changes below.
- Decided: DataItems-only transport with a decided fallback (`MessageClient` for urgent alerts only); five link states
  (added Incompatible); numeric values in alerts; Health Services passive monitoring instead of `MeasureClient`;
  `allowBackup` off; reminders within 1 minute over 24 hours; emulator pairing required before hardware testing
  (0b.2a, 0b.2b); milestone 0 split into 0a and 0b with detailed steps; a cloud bootstrap script (0a.8).
- Recorded: the test phone is a Samsung Galaxy A55 5G (SM-A556E/DS) with the Galaxy Watch6 Classic.
- Environment note: the cloud environment had no Android SDK and Maven Central answered 429. A temporary SDK install and
  a user-level Gradle mirror let `assembleDebug testDebugUnitTest lintDebug` pass locally; step 0a.8 makes it repeatable.
- Open: transport details to be proven by the spike; reminder mechanism and biometric permissions to be researched.
- No code changed.

## 2026-10-11 - Work log split per lane

- PR: [#7](https://github.com/shlomoa/my-adhd-pal/pull/7)
- Changed: `docs/work-log.md` is now the index and entry-format page; entries moved to `docs/work-log/planning.md`, with empty `shared.md`, `android.md`, `watch.md` for the parallel lanes. `scripts/require-gate.sh` and `docs/plans/README.md` point to the lane logs.
- Why: three parallel sessions appending to one file already conflicted once when merging `main`.
- Validation: relative links under `docs/` resolve (ad hoc check before and after); `scripts/test/test-scripts.sh` 19/19 pass.
- Open items: none.
