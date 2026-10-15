# How it went, step by step

Dates are from the work log and the result files. Durations that were not recorded are marked **not recorded**; they are
not estimated here.

## Stage 1: emulators (0b.2a)

### 1. Spike apps and a tap-through checklist

A small set of classes was added to `:phone` and `:wear` on a spike branch (a launcher activity with buttons, a listener
service, later a command receiver) and a checklist written for the user: install two emulators, pair them, tap the cases
([`0b2a-checklist.md`](0b2a-checklist.md); the Windows setup is in
[`../dev-setup-windows.md`](../dev-setup-windows.md) sections 1 to 6). The user ran it by hand.

What went wrong, in the order it was found:

- **The manual tap-through cost the user hours** (reported by the user through the coordinator; the time was not
  recorded). Every case needed several taps on two emulators and the log lines pasted back. Cases could not be repeated
  cheaply, and the numbers were hard to compare.
- **The laptop went to sleep during a run** and every `adb` call failed. Fix: plug in and turn sleep off before a run
  (`powercfg /change standby-timeout-ac 0`).
- **Android Studio's Device Manager showed greyed Run buttons** for emulators that were already running or half
  started. Fix: start emulators from PowerShell with fixed ports (5554 and 5556), and do not use Studio's Run buttons.
- **A click inside the console window paused the script** (Windows QuickEdit selects text and blocks the process until
  Enter or Esc). Fix: documented as rule 2 of [`../dev-setup-windows.md`](../dev-setup-windows.md) section 6.6.
- **The Google Pixel Watch companion app on the phone emulator stopped once** ("keeps stopping"). Its effect on the link
  was not determined.

### 2. A scripted runner: `run-0b2a.ps1`

The checklist was replaced by `scripts/spike/run-0b2a.ps1`: find the emulators, build, install, drive each case with
`am broadcast`, read `logcat -s SPIKE`, write `results.txt` and per-case logs, publish them as a branch. Before the
user ran it, the runner was exercised against a simulated adb (`scripts/spike/fake/adb`, 62 checks at the time,
66 now).

The **first real run** (runner commit `7476512`, 2026-10-13, [partial output](results/0b2a-first-real-run-partial.txt))
passed C2a, C3a and C3b, reported C1 and C1b as FAIL, and then **hung** at C5: the runner stopped the watch emulator
with `adb emu kill`, and the `adb` client for the killed emulator never returned. The runner had no per-call timeout.
Fixes: every `adb` call now runs with a hard timeout and is killed when it does not return (`Invoke-Adb`,
`-AdbTimeoutSec`, default 30 s), and no call is ever made to a device that is deliberately away. The simulated adb got a
case that hangs on `emu kill` so the regression is covered (`emuhang`).

### 3. Results and the finding

After the fixes the user ran the cases on the emulators (manual and scripted runs). Result tables are in
[`../protocol.md`](../protocol.md), section "Transport verification: spike 0b.2a". The finding that mattered: a delete
by the **watch** of an item the **phone** had just written was lost on the phone, while every other direction and
timing worked. The hypothesis was clock skew (the watch emulator was about 1.5 s behind). The consequence was the
**writer-owns-cleanup** rule and the deferral of C5 phone-away, C2b and C4 to the hardware stage. The work was merged as
gate G-SPK1 (2026-10-14).

## Stage 2: real devices (0b.2b)

### 4. Plan and runner

Written 2026-10-14: [`0b2b-plan.md`](0b2b-plan.md) (decisions to agree first, what the user does and what the script
does, setup for the phone over USB and the watch over Wi-Fi debugging, the one command, a nine-item checklist) and
`scripts/spike/run-0b2b.ps1`, built on the 0b.2a runner with a **readiness gate** (names the device, the reason and what
to do; exit 2 and nothing changed), `svc`-based link states, forced Doze and standby buckets, radios always restored,
`-RestorePhone` for a crash, redaction of serials and IP addresses in everything published. It was tested against a
simulated adb with hardware-style serials (`scripts/spike/fake-hw/adb`, 65 checks at the time, 71 now).

### 5. Setting up the real devices

The first contact with real devices found things the simulation could not:

- **The watch is listed twice in `adb devices`**, as `ip:port` and under its mDNS name (`adb-...-tls-connect...`), so the
  automatic detection saw two watches. Workaround: pass `-Phone` and `-Watch` explicitly.
- **Wireless debugging uses two different ports**: one to pair, another to connect. `-WatchAddress` takes the connect
  port.
- **The battery gate refuses a device below 20 % but its message says "charge to at least 30 %"**. Charge above 30 %.
- The Watch6 charges wirelessly and stays awake on its charger, which kept Wi-Fi debugging alive for the whole run.

### 6. The runs (2026-10-15, user's PC clock)

The user ran the cases in six small groups so a failure could be repeated alone. Each run published its own branch.

| Run started | Cases | Runner commit | Outcome |
|---|---|---|---|
| 08:45:57 | C1 | `bc1b4e5` | PASS, proves the chain |
| 08:51:52 | C1b, C2a | `bc1b4e5` | PASS, deletion symmetric both ways |
| 08:57:02 | C3 (a, b, c), C3d | `bc1b4e5` | C3a, C3b, C3c PASS; **C3d reported NOT SELECTED** because of a runner bug |
| 09:02:58 | L1, L2 | `bc1b4e5` | PASS, Nearby and Relayed |
| 09:06:40 | C3d | `d689afa` | PASS after the fix |
| 09:08:43 | L3 (with C2b) | `d689afa` | PASS, Away and recovery |

The six runs started within 22 min 46 s of each other (08:45:57 to 09:08:43); how long the last one took, and the time
spent on setup before 08:45, are **not recorded**.

**The C3d bug.** The standby-bucket cases were passed to the shared case function as closures (`GetNewClosure()`). On
the user's host a closure runs in a dynamic module that could not see the script's own functions, so the case
failed with *The term 'Invoke-Adb' is not recognized*, and the runner then reported it as NOT SELECTED instead of a
failure. On the developer's Linux machine, with PowerShell 7.6.6, the same code worked, so the simulated
suite never caught it. Fix `d689afa`: plain scriptblocks that read `$script:` variables,
and any unexpected error inside a case becomes a **FAIL with its message** instead of silence. A regression case in the
simulation forces such an error (`SPIKE_TEST_THROW=C3d`). The cause on the user's host was **never reproduced on
Linux**; the evidence is only that the plain-scriptblock version passes there.

### 7. Reading the results, the decision

The six `results.txt` files were copied into `docs/spikes/results/`, the numbers went into
[`../protocol.md`](../protocol.md) (section "Transport verification: spike 0b.2b") and the fallback rule was applied:
no criterion failed, so **DataItems only** (step 0b.3, gate G-DEC). The user then answered the checklist items that need a
person (pairing path, battery-management toggles, the app's battery setting); the optional **three-day sleeping-apps recheck**
(`run-0b2b.ps1 -SkipBuild -OnlyCases C3` after three days without opening the app) is **still open**.

### 8. Keeping the tooling (this change)

The spike code had first been planned as discarded. The user decided to keep the scripts, results and spike apps. They
were moved from the spike branches into the debug source sets and into `scripts/spike/`, with a check that no release
build contains them. The move was **built and checked but not run on the devices**: the manifest entries, component
names (`...debug.spike...` instead of `...spike...`) and runner constants changed together and the simulated suites
pass, but only a hardware run proves it.

While preparing this documentation a defect in the publishing step was found: the raw per-case logs of the six runs were
never pushed, because `.gitignore` ignores `*.log` and `git add` of a directory silently skips ignored files. Only
`results.txt` reached the `spike-results/*` branches. The user pushed the logs from the PC (`spike-raw-logs-0b2b`) and they
are in `docs/spikes/results/` now; the runners use `git add -f`. See [Learnings](learnings.md).
