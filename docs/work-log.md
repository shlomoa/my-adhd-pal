# Work log

## 2026-10-11 - Plan for phone app, watch app and protocol in one repo

- Merged two plans into [`docs/plan.md`](plan.md): the phone-app/protocol plan and the watch-side hand-off
  (reminders on the watch, connection state, biometrics, debug vs release, process rules).
- Decided: one monorepo in `my-adhd-pal` with `:protocol`, `:wear` and `:phone`.
- Open: transport (DataItems only vs items + messages); to be decided in the plan PR.
- No code changed.
