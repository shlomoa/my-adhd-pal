# Where everything lives, and why

## In the repository (`main`)

| What | Path | Why there |
|---|---|---|
| Spike apps, phone | `phone/src/debug/java/com/shlomoa/myadhdpal/phone/debug/spike/` (`Spike.kt`, `SpikeActivity.kt`, `SpikeCommandReceiver.kt`, `SpikeListenerService.kt`) | Debug source set: AGP leaves it out of release builds. The package contains `debug`, which the repository's own release check already looks for. |
| Spike apps, watch | `wear/src/debug/java/com/shlomoa/myadhdpal/wear/debug/spike/` (the same four files) | Same reason. |
| Manifest entries | `phone/src/debug/AndroidManifest.xml`, `wear/src/debug/AndroidManifest.xml` | Merged into debug builds only. |
| Data Layer dependency for the phone spike | `phone/build.gradle.kts`: `debugImplementation(libs.play.services.wearable)` | The watch app already has it in `main`; on the phone only the spike uses it until the real link client lands (steps 2.4 and 2.5). |
| Runner, emulators | `scripts/spike/run-0b2a.ps1` | One script per stage; the cases differ (emulators can be stopped and restarted). |
| Runner, hardware | `scripts/spike/run-0b2b.ps1` | Same. |
| Simulated adb | `scripts/spike/fake/adb`, `scripts/spike/fake/emulator` (emulators), `scripts/spike/fake-hw/adb` (hardware serials) | Lets the runner be tested on any machine with PowerShell 7 and no devices. |
| Tests of the runners | `scripts/spike/test-run-0b2a.sh`, `scripts/spike/test-run-0b2b.sh` | Run by `scripts/test/test-spike.sh` in the CI job `script-tests` (TC-S-146). |
| Placement checks | `scripts/test/test-spike.sh` (TC-S-145) | Keeps the spike classes in `src/debug` and out of release APKs. |
| Results | `docs/spikes/results/0b2b-*/results.txt` (six runs) and `docs/spikes/results/0b2a-first-real-run-partial.txt` | Evidence for [`../protocol.md`](../protocol.md). |
| Narrative and numbers | [`../protocol.md`](../protocol.md), sections "Transport verification: spike 0b.2a" and "...0b.2b" | The single source of truth for the protocol and the decision. |
| This documentation | `docs/spikes/*.md` | |

### Why debug source sets and not a separate module or a flavour

- A **spike source set in each app** needs no new Gradle module, and the code has to be inside `:phone` and `:wear`
  anyway (shared `applicationId`, manifest listener, see [Why](why.md)).
- The repository already has the rule "debug-only code lives in a `debug` package under `src/debug`, and no such class
  may be in a release APK" with the CI check `checkNoDebugInRelease` in `build.gradle.kts`. Putting the spike in
  `...debug.spike` reuses that check unchanged. A new product flavour would have changed every build variant name
  and every other lane's tasks.
- The cost: detekt skips `**/debug/spike/**` (see `build.gradle.kts`) because the code stays as it was when it produced
  the results, rather than being reworked for style rules. Spotless still formats it.

### What proves that no release build contains the spike

1. `checkNoDebugInRelease` (part of `./gradlew check`) fails when a release APK contains any class whose package
   contains `debug`.
2. `scripts/test/test-spike.sh` (TC-S-145) checks that nothing spike-related is under `src/main` or in the main manifests,
   that the debug manifests declare the three components, and, when release APKs have been built, that they contain no
   `debug/spike/Spike` class. On this change the release APKs of both apps were inspected and contained neither the
   classes nor any manifest entry; the debug APKs contained both.

## Raw logs

The raw per-case logs are in `docs/spikes/results/`: 26 files of the six 0b.2b runs (`0b2b-*/phone-<case>.log`,
`watch-<case>.log`) and 12 files of the first, partial 0b.2a scripted run (`0b2a-20261013-070822/`). They reached the
repository late: the runner committed them, but `.gitignore` ignores `*.log` and `git add` skipped them silently, so the
`spike-results/0b2b-*` branches hold `results.txt` only. The user pushed the logs from the PC on the branch
`spike-raw-logs-0b2b`, they were checked for device serials, IP and MAC addresses and e-mail addresses (none found), and
copied here; the runners now use `git add -f` and `.gitignore` allows `docs/spikes/results/**/*.log`. No other 0b.2a raw
logs were found; the result tables of the 0b.2a runs are in [`../protocol.md`](../protocol.md).

## Only on the devices

The `adb` pairing state, the phone's developer settings and the watch's Wireless debugging: reversed by the user (cleanup
checklist in [`0b2b-plan.md`](0b2b-plan.md)).

## On branches

| Branch | Content | Status |
|---|---|---|
| `spike-results/0b2b-20261015-*` (six), `spike-raw-logs-0b2b` | the `results.txt` of one run each; the raw logs | Everything in them is also in `main`; the user decides when to delete them. |
| `claude/shared-0b2a-spike`, `claude/shared-0b2a-spike-rtt`, `claude/shared-0b2b-spike` | the spike code before it moved to `main` | Superseded; deletion is the user's decision. |

## Why results are not in a database or an artifact

The runner's only dependency is git. A results folder committed on a branch survives the user's PC, reviews like code,
and can be diffed between runs.
