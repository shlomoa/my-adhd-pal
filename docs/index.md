# ADHD Pal documentation

The single entry point for everything documented in this repository. Every document is linked from here (or from a page
linked here) and stays in one place: other pages link to it and never copy it. Decisions are made by the user in the
body of the GitHub issue of each step; the documents record what was decided and link back to the issue.

## Plan and lanes

- [Plan](plan.md): architecture, decisions, milestones and step definitions (the authority).
- [Execution plans and gates](plans/README.md): lanes, gates, stop rule, file ownership and schedule. Lane plans:
  [shared and data](plans/shared.md), [Android](plans/android.md), [watch](plans/watch.md),
  [documentation](plans/docs.md), and the [biometric alert decisions](plans/biometrics.md).
- [Conventions](conventions.md): issues, labels, work log, debug code and checks every lane follows.
- [Work log](work-log.md): the format and one log per lane:
  [shared](work-log/shared.md), [Android](work-log/android.md), [watch](work-log/watch.md),
  [documentation](work-log/docs.md), [planning](work-log/planning.md).

## Protocol and data layer

- [Watch-phone protocol](protocol.md): item catalog, format, versioning, link states, diagrams and the transport
  verification results.
- [Phone data layer contract](data-layer.md): repositories, storage, the Data Layer source and the contract tests.
- [Phone alert design](phone-alert.md): how the phone vibrates and shows a full-screen alert for a biometric alert from the
  watch, what can suppress it, and the steps.

## Test plans and reports

- Test-case catalogues: [bootstrap](test-plans/bootstrap.md), [shared](test-plans/shared.md),
  [Android](test-plans/android.md), [watch](test-plans/watch.md), [documentation](test-plans/docs.md).
- Baselines and final reports: [bootstrap baseline](test-reports/bootstrap-baseline.md),
  [bootstrap final](test-reports/bootstrap-final.md), [shared baseline](test-reports/shared-baseline.md),
  [Android baseline](test-reports/android-baseline.md), [watch baseline](test-reports/watch-baseline.md),
  [watch end-to-end checklist](test-reports/watch-e2e-checklist.md),
  [documentation baseline](test-reports/docs-baseline.md), [documentation final](test-reports/docs-final.md),
  [documentation checks baseline](test-reports/docs-checks-baseline.md),
  [documentation site baseline](test-reports/docs-site-baseline.md),
  [aurora style baseline](test-reports/docs-style-baseline.md).

## Spikes and research

- [Transport validation spikes](spikes/README.md): [why](spikes/why.md), [how](spikes/how.md), [where](spikes/where.md),
  [learnings](spikes/learnings.md), [spec of the apps](spikes/spec-apps.md), [spec of the tests](spikes/spec-tests.md),
  [emulator checklist](spikes/0b2a-checklist.md) and [hardware plan](spikes/0b2b-plan.md).
- [Watch research notes](research/watch.md).

## Setup and demo

- [Development setup on Windows](dev-setup-windows.md).
- [Hardware demo](demo.md).
