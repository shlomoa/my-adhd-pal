# Work log

## 2026-10-11 - Plan for phone app, watch app and protocol in one repo

- Merged two plans into [`docs/plan.md`](plan.md): the phone-app/protocol plan and the watch-side hand-off
  (reminders on the watch, connection state, biometrics, debug vs release, process rules).
- Decided: one monorepo in `my-adhd-pal` with `:protocol`, `:wear` and `:phone`.
- Open: transport (DataItems only vs items + messages); to be decided in the plan PR.
- No code changed.

## 2026-10-11 - Execution plans and gate bootstrap (S0)

- Added [`docs/plans/`](plans/README.md): three parallel lane plans (shared/data, Android, watch), gates, stop rule, schedule.
- Added `scripts/require-gate.sh`, `scripts/check-pr-gates.sh`, `scripts/check-test-cases.sh`, `docs/plans/gates.json` and the `Gates` CI workflow.
- Validation: script test cases written first and recorded red (`docs/test-reports/bootstrap-baseline.md`), then green (`bootstrap-final.md`).
- Assumptions to confirm: lane split, transport = DataItems only, minimal phone scope (see plans README).
- Gate G-BOOT is set to `merged` in this change; it takes effect on `main` when the change is merged.
