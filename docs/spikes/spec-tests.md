# Specification of the tests

Two runners, one per stage, plus a simulated adb for each. Source: `scripts/spike/run-0b2a.ps1`,
`scripts/spike/run-0b2b.ps1`, `scripts/spike/test-run-0b2a.sh`, `scripts/spike/test-run-0b2b.sh`. Both runners work with
Windows PowerShell 5.1 syntax and PowerShell 7 (only PowerShell 7 was used; **5.1 not verified**), need `adb` and `git`
on `PATH`, never ask the devices to restart (the 0b.2b runner never stops or kills a device; the 0b.2a runner stops and
restarts the emulators itself in C5), and publish only their own results folder.

Notation: *sender* writes an item, *receiver* gets it. "Round trip" is measured on the pinger's own clock (see
[Why](why.md)). Default timeouts are the parameter defaults below. Item paths and log patterns are in
[Spike apps](spec-apps.md).

## 0b.2b: real devices (`run-0b2b.ps1`)

### Flow of every run

1. Optional `-PairWatch`/`-PairCode` (`adb pair`), `adb connect` to `-WatchAddress`.
2. Detect serials (the device reporting `watch` in `ro.build.characteristics` is the watch; USB phone is the other one),
   unless `-Phone`/`-Watch` are given.
3. **Readiness gate** (before the build, after the install): per device `adb` state, boot completed, battery at least 20 %,
   app installed; any problem is printed with the device, the reason and what the user does, then **exit 2 with nothing
   changed**. A timed-out `adb` call is reported as a timeout, never as "not installed".
4. Build `:phone:assembleDebug :wear:assembleDebug` and `adb install -r` both APKs (skipped by `-SkipBuild`).
5. Check both devices see the other node (`NODES count>=1` on each side), measure the clock skew (median of five samples
   of `date +%s%N` on each device against the host), save the phone's radio and power settings, note the standby bucket
   and the Doze allow-list.
6. Run the selected cases. An unexpected error inside a case is a **FAIL with its message**, and the run continues.
7. Always (also after a failure): `Restore-Phone` ends forced Doze (`dumpsys deviceidle unforce`), resets the battery
   simulation, sets the standby bucket back to `active`, ends airplane mode, switches Bluetooth, Wi-Fi and mobile data
   back on if they were on, restores stay-awake.
8. Write `results.txt` and the per-case logs, redacting e-mail addresses, tokens, device serials and IP addresses, then
   publish (below).

### Parameters

| Parameter | Default | Meaning |
|---|---|---|
| `-Phone`, `-Watch` | auto | serials; pass both when `adb devices` lists the watch twice |
| `-WatchAddress` | none | `ip:port` of the watch's Wireless debugging **connect** port; `adb connect` is run when it is not listed |
| `-PairWatch`, `-PairCode` | none | one-time `adb pair` (pairing port and 6-digit code) |
| `-ResultsRoot` | `docs/spikes/results` | where the run folder is created |
| `-WaitSec` | 60 | wait for an urgent item, `RECV` and `DELETE-OK` |
| `-NonUrgentWaitSec` | 90 | wait for a non-urgent round trip |
| `-DeletionRepeats` | 3 | items per group in C1b |
| `-DeletionSettleSec` | 30 | wait before listing items in C1b |
| `-DelayedDeleteMs` | 6000 | delay of the delayed deletes in C1b |
| `-NearbyMaxRttMs` | 10000 | L1 target (median urgent round trip) |
| `-RelayedMaxRttMs` | 60000 | L2 target |
| `-RelayedDetectSec` | 90 | how long to wait for the watch to show the phone as connected but not nearby |
| `-AwayDetectSec` | 180 | how long to wait for the watch to show no phone |
| `-AwayRecoverSec` | 180 | how long all four L3 items may take after the radios come back |
| `-DozeWaitSec` | 120 | wait in C3c and C3d |
| `-AdbTimeoutSec` | 30 | hard limit per `adb` call; a client that does not return is killed |
| `-PollSec` | 2 | log polling period (it is also the resolution of the host-clock seconds) |
| `-SettleScale` | 1 | multiplies fixed settle delays (the tests use 0) |
| `-SkipBuild`, `-SkipPush` | off | reuse installed apps; keep results local |
| `-NonInteractive` | off | never ask the user to tap; such a step is NOT RUN |
| `-RestorePhone` | off | only restore the phone's radios, power settings and standby bucket, then exit |
| `-OnlyCases` | all | e.g. `C1,L2`; valid names `C1 C1b C2a C3 C3c C3d L1 L2 L3`; others are reported NOT SELECTED; the results branch name ends with the cases |

### Exit codes

| Code | Meaning |
|---|---|
| 0 | every selected case PASS |
| 1 | at least one case FAIL (a finding; results are still published) |
| 2 | a device is not ready (nothing changed), or a case could not run (NOT RUN: a setting could not be changed, a manual step was skipped, the app process was still running) |
| 3 | setup problem: `adb` or `git` missing, device not found, pairing or build failed, more than one phone, unknown case name |

### Cases

"Measured" is the value that goes into `results.txt`; the hardware values are the ones from the six runs in
[`results/`](results/) and [`../protocol.md`](../protocol.md).

| Case | Purpose | Steps | Measured | PASS when | Hardware result |
|---|---|---|---|---|---|
| C1 | Items cross the pairing in both directions; the receiver can delete; the deletion reaches the sender | for watch to phone then phone to watch: send an urgent `del` item, wait for `RECV`, the receiver's `DELETE-OK`, the sender's `EVENT-DELETED` | `delayMs` per direction (two clocks, **not** a delay) | per direction: `SEND` logged, `RECV` within `-WaitSec`, `DELETE-OK` within `-WaitSec`, `EVENT-DELETED` on the sender within 10 s | PASS (`delayMs` 460 and 176; skew -76 ms) |
| C1b | Deletion symmetry: the 0b.2a finding | for each direction and for immediate and delayed (`-DelayedDeleteMs`) deletes: `-DeletionRepeats` items; wait `-DeletionSettleSec`; list the items on both devices | per group: items still listed on the writer, on the deleter, deletion events seen by the writer | no id is still listed on either device, every item received and deleted | PASS (0 left, 3 of 3 events in all four groups; skew -90 ms) |
| C2a | A KEEP item stays | watch sends `keep`; phone `RECV`; after 3 s both devices list it | | listed on both | PASS |
| C3a | Listener wakes with the app closed (killed) | HOME, `am kill`, no process (`pidof`), watch sends urgent `del` | `processUptimeMs` of the receiving process | `fg=false RECV` within `-WaitSec`; if not, the item must at least appear after opening the app (reported as FAIL with that remark) | PASS (`processUptimeMs=221`) |
| C3b | The same after `am force-stop` | same | | same | PASS (`processUptimeMs=244`) |
| C3c | The same under forced Doze | stay-awake off, `KEYCODE_SLEEP`, `dumpsys battery unplug`, `dumpsys deviceidle force-idle`, require `deviceidle get deep` = `IDLE`, app force-stopped; the watch sends an urgent and then a non-urgent item | host-clock seconds until `fg=false RECV` (resolution: one poll) | the **urgent** item arrives within `-DozeWaitSec` | PASS (urgent 1 s, non-urgent 1 s) |
| C3d-rare, C3d-restricted | The same in the `rare` and `restricted` standby buckets | `am set-standby-bucket` to `rare` (40) and `restricted` (45), require `am get-standby-bucket` to agree, app force-stopped; urgent then non-urgent | as C3c | as C3c | PASS (rare 4 s and 3 s; restricted 3 s and 3 s) |
| L1 Nearby | Round trip with Bluetooth connected | needs `nearby=true` in the watch's node list (else NOT RUN); 2 urgent warm-up pings from each side; then 3 urgent and 3 non-urgent pings from each side | round trips (ms) and medians | each side's median urgent round trip is at most `-NearbyMaxRttMs`; every ping answered | PASS (urgent medians 201 and 223 ms) |
| L2 Relayed | Round trip over the relay | phone Bluetooth off; poll the watch's node list until it shows a connected node without `nearby=true`; the same ping set | seconds until Relayed is seen; round trips | state seen within `-RelayedDetectSec`, every ping answered, medians at most `-RelayedMaxRttMs` | PASS (seen after 8 s; urgent medians 1721 and 1815 ms) |
| L3 Away | Items sent while away arrive on their own | phone Bluetooth, Wi-Fi and mobile data off; wait until the watch shows `NODES count=0`; phone sends `del` and `keep`, then its app is force-stopped; watch sends `del` and `keep`; radios restored; the script does nothing else for `-AwayRecoverSec`; then waits up to 120 s for `nearby=true` again | seconds until Away is seen; per item, seconds from the radio restore to `RECV` (host clock) | Away seen within `-AwayDetectSec`, all four items arrive within `-AwayRecoverSec`, radios restorable by `adb` | PASS (Away after 4 s; items after 16, 2, 9 and 12 s; `fg=false` on the phone; Nearby again 2 s after the restore) |
| C2b | After L3: deleted items did not come back | list items on both devices | | no `del` id listed, the `keep` id still listed, on both | PASS |

The radios are switched with `svc bluetooth|wifi|data enable|disable` and each change is read back with
`settings get global bluetooth_on|wifi_on|mobile_data`; Bluetooth has a second attempt with
`cmd bluetooth_manager`. If a setting does not change, the runner prints a **MANUAL STEP** naming what to tap
(`Press Enter when done, or type s to skip this case`); skipping, or `-NonInteractive`, makes the case NOT RUN.

Not covered by any case: the watch's own Doze or Away (the watch stayed on its charger with adb on its Wi-Fi), the
Galaxy Wearable pairing flow and Samsung's sleeping-apps list (user checklist items), a watch out of Bluetooth range
(radios are switched with adb, which is not the same radio state), Windows PowerShell 5.1.

### Publishing

`Publish-Results` creates a temporary git worktree at `HEAD`, copies the run folder into it, creates the branch
`spike-results/<run name>`, runs `git add -f -- docs/spikes/results/<run name>` (`-f` because `.gitignore` ignores
`*.log`; before this fix the logs were dropped silently, see [Learnings](learnings.md)), commits only that folder,
pushes the branch with the user's own credentials and removes the worktree. The user's branch and uncommitted files are
untouched. With `-SkipPush` the branch stays local; a failed push is reported with the command to retry.

## 0b.2a: emulators (`run-0b2a.ps1`)

Same structure (auto-detect `emulator-*` serials, build, install, broadcast-driven cases, results branch
`spike-results/0b2a-<timestamp>`), with these differences: the phone and the watch are emulators, **C5** stops and
restarts one of them with `adb emu kill` and `emulator -avd <name> -port <n>`, and there is no radio or Doze control.

| Case | What it does | Gating |
|---|---|---|
| C1, C1b, C2a | as in 0b.2b (C1b waits `-DeletionSettleSec`, default 45) | yes |
| C3 (a kill, b force-stop) | as C3a and C3b | yes |
| C5 watch-away, phone-away | stop the emulator, wait until it is offline, the other side sends a `del` and a `keep` item, restart the emulator (normal boot), wait `-ReconnectTimeoutSec` (180) for both to arrive without any action | yes |
| C2b | after C5: the `del` items did not reappear, the `keep` item is still there; creates its own items when C1 and C2a were not selected | yes |
| C4 | urgent and non-urgent round trips, 3 each way after 2 warm-ups; no target, passes when every ping is answered | no (optional, `-SkipC4`) |

Exit codes: 0 all gating cases PASS; 1 at least one gating case FAIL; 2 no FAIL but a gating case NOT RUN, or an emulator
does not respond. Results of this stage: [`../protocol.md`](../protocol.md), section "Transport verification: spike 0b.2a".

## The simulated-adb suites

`test-run-0b2a.sh` (66 PASS lines) and `test-run-0b2b.sh` (71) run the real `.ps1` file with `pwsh` against a shell
script named `adb` placed first on `PATH` (`scripts/spike/fake/adb`, `scripts/spike/fake-hw/adb`) inside a temporary git
repository with a bare "origin". The fake keeps the state of two devices in a folder, delivers items, produces the link
states from the simulated radios, and takes failures from `FAKE_FAIL` (for 0b.2b: `unauth`, `nowatch`, `deadwatch`,
`btfail`, `norelay`, `noaway`, `nodoze`, `dozeblock`, `bucketfail`, `lowbattery`, `slowrtt`; `FAKE_LEAK=1` injects an
e-mail address, the watch address and a token into the logs).

What the 71 checks of `test-run-0b2b.sh` cover:

- the script parses, and a complete run with all cases ends with exit 0 and a report with the expected lines (device
  versions, C1, C1b, C2, C3 with all five variants, L1 to L3 with medians, detection and arrival times);
- radios, Doze and the standby bucket are restored after a run, after a run that could not change them and after an
  unexpected error; no call is made to a device that is not listed;
- the published branch contains only the run folder, the results table **and the raw logs even when the repository
  ignores `*.log`**, no e-mail, token, serial or IP address, the redaction markers are present, the working branch and
  uncommitted files are untouched, the temporary worktree is removed;
- the readiness gate: unauthorized, never listed, hangs, 5 % battery, an explicit serial that is not connected, each
  with exit 2, the right message and nothing changed; a timeout is not reported as "not installed";
- radios `adb` cannot switch: the manual step text, NOT RUN under `-NonInteractive`, skipping gives exit 2;
- unobservable states (Relayed or Away never shows) are FAIL with an explanation, not silent passes;
- a round trip above the target, items held back under Doze, Doze that cannot be forced, a bucket that cannot be set;
- C3d alone (the regression of the closure bug) and an unexpected error reported as a FAIL with its message;
- `-RestorePhone`, `-PairWatch` with the right and a wrong code, an unknown case name (exit 3).

`test-run-0b2a.sh` covers the equivalent for the emulator runner: detection, the report of every case, the results
branch and `-OnlyCases` naming, the readiness retry and the unresponsive emulator, an asymmetric deletion, a receiver
that never deletes, no wake after force-stop, no recovery after the restart, the hung `adb emu kill` (bounded run time),
the optional C4 failing without gating, `-SkipC5`, a push failure leaving the results committed locally, and a missing
watch emulator.

### What the simulation cannot show

- how the Galaxy A55 5G, the Galaxy Watch6 Classic or Google Play services behave: whether `svc` and `dumpsys` take
  effect, what the node list reports, how long the Data Layer takes;
- anything specific to Windows or to Windows PowerShell 5.1: the suites run on PowerShell 7 on Linux, and the C3d
  closure bug showed that the two can differ;
- the exact text real `adb`, `dumpsys` and `logcat` print on other Android versions (the fake reproduces what the
  runner parses, not the full output);
- whether the moved spike apps (debug source sets, package `...debug.spike`) run on the devices: only the component
  names the runner passes are checked against the fake, and the manifest placement by `test-spike.sh`.

The suites also have a limit of their own: a test repository is not the real repository. The `.gitignore` case above
shows what that can hide; the suites now create their temporary repositories with the same `*.log` rule.
