# Work log: watch lane (W)

Entries for this lane only. Format and index: [`../work-log.md`](../work-log.md).

## 2026-10-11 - W1a Watch test-case catalogue

- Issue and PR: #11, PR #19
- Changed: added `docs/test-plans/watch.md` (TC-W-001 to TC-W-099).
- Validation: docs only; ran `scripts/require-gate.sh G-BOOT` (exit 0). Ids extract with the catalogue grep in
  `scripts/check-test-cases.sh`; tests and the red baseline come in W1c.
- Open items: none.

## 2026-10-11 - W1b Watch research notes

- Issue and PR: #13, branch `claude/watch-w1b-research`
- Changed: added `docs/research/watch.md` (reminder mechanism, tile API, passive monitoring, API 36 permissions,
  Galaxy Watch6 Classic heart rate).
- Validation: docs only; `scripts/require-gate.sh G-BOOT` exit 0; sources cited, unverified items marked.
- Open items: exact-alarm permission choice on Wear OS (3.4), tile dependency versions (5.2), Samsung background heart
  rate support, which needs a probe on the real watch and a user decision (6.1/6.2).

## 2026-10-11 - 3.1 Manifest and dependencies

- Issue and PR: #42, branch `claude/watch-3.1`
- Changed: `VIBRATE` and `POST_NOTIFICATIONS` permissions and `NO_BRIDGING` (`com.google.android.wearable.notificationBridgeMode`)
  in the wear manifest; `play-services-wearable` 20.0.1 (version catalog, `# W` entries); `PlayServicesStatus` availability
  check. Runtime request of `POST_NOTIFICATIONS` and the exact-alarm permission come with 3.5 and 3.4.
- Validation: tests first. Red: `:wear:testDebugUnitTest` failed TC-W-050 and TC-W-051 on assertions and TC-W-052 did
  not compile (no dependency, no type). Green: same tests pass; `./gradlew check` BUILD SUCCESSFUL (Spotless, detekt,
  lint, unit tests, pairing, docs checks) covers TC-W-053. `scripts/require-gate.sh G0` exit 0.
- Open items: none.
