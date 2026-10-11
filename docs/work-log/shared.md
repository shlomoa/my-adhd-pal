# Work log: shared lane (S)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-11 - S1a Test-case catalogue

- Issue and PR: none for S1a (not a plan.md step); PR linked from the coordinator message.
- Changed: added `docs/test-plans/shared.md` with `TC-S-001` to `TC-S-142`: tooling and environment (0a.1-0a.8),
  protocol (1.1-1.8), phone data layer (2.1-2.5), spikes (0b.1-0b.3) and the final run.
- Validation: docs only. Gate check `scripts/require-gate.sh G-BOOT` exited 0. Every id is unique; red baselines
  come with the code steps (`docs/test-reports/shared-baseline.md`, first written in 0a.8).
- Open items: next, issues for all plan.md steps (0a.7), then 0a.8.
