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

## 2026-10-11 - 3.8 Debug source set

- Issue and PR: #49, branch `claude/watch-3.8`
- Changed: `Logger`/`LogSink` and `ItemIngest` seam in main (no sink in release, so nothing is logged); in
  `wear/src/debug/` and package `...wear.debug`: `RingBufferLogSink`, `DebugApplication` (installs the sink), `DebugLogActivity`
  (log view), `InjectedMessageReceiver` (requires `android.permission.DUMP`, so only `adb shell` can send) with
  `forwardInjected`; debug manifest. Tests: `wear/src/testDebug/` (TC-W-056) and `wear/src/test/scripts/test-debug-separation.sh`
  (TC-W-054, 055, 057).
- Validation: red first: `:wear:testDebugUnitTest` did not compile (no debug code) and the script failed TC-W-055
  "debug APK contains the debug log view". Green: 4 unit tests pass, the script passes 5 of 5 (a deliberate `wear.debug.Leak`
  class in `src/main` fails `checkNoDebugInRelease` and is named; the real build passes; the release APK and manifest hold no
  debug class), `./gradlew check` BUILD SUCCESSFUL.
- Open items: TC-W-056's "one applied schedule" half comes with 3.3 (the schedule handler installed in `ItemIngest`);
  raw-biometrics checks for TC-W-057/080/081 come with 6.7. The script is not run by CI (`.github/**` and `scripts/**` are S's):
  S can add it to the workflow. detekt excludes only `src/test`, so `testDebug` needs a `@Suppress("FunctionNaming")`.

## 2026-10-12 - 3.8 follow-up: drop the detekt suppression

- Issue and PR: #49 (follow-up), branch `claude/watch-drop-suppress`
- Changed: removed `@Suppress("FunctionNaming")` from `DebugLoggingTest`, since the detekt config now skips `src/testDebug`.
- Validation: `scripts/require-gate.sh G0` exit 0; `./gradlew check` BUILD SUCCESSFUL locally.
- Open items: none.
