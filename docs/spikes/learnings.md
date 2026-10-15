# Learnings: what could have been faster, cheaper and more reliable

Honest assessment, from the work log, the result files and the failures described in [How](how.md). Time figures are
given only where they were recorded.

## What would have saved the most time

1. **Start with the scripted runner, and start on the real devices.** The tap-through checklist cost the user hours and
   was replaced by a script anyway. The emulator stage found one thing the hardware stage did not confirm (the deletion
   asymmetry: a watch delete right after a phone write was lost on emulators, symmetric on the Galaxy A55 5G and
   Galaxy Watch6 Classic) and could not show what the plan needed most (Nearby, Relayed, Away, Samsung background
   behaviour). With a runner and hardware from day one, the emulator stage would have been a smoke test for the
   runner, not a stage of its own. Counter-point, to be fair: the emulator stage is also where the runner's worst
   bugs (no timeouts, the hang on a killed emulator) surfaced at no risk to the user's devices.
2. **Give every `adb` call a timeout and a readiness gate from the first version.** The first version of the 0b.2a runner had neither; it
   hung at C5 for 12+ minutes on an `adb emu kill`. The 0b.2b runner had both and no run was lost to a hang.
3. **Test the runner on the user's shell before handing it over.** The simulated suite ran on PowerShell 7 on Linux.
   The C3d closure bug appeared on the user's Windows host and cost one repeated run (09:06:40) plus a round trip.
   A one-time smoke test of the runner on Windows PowerShell and PowerShell 7 on Windows (even with the simulated adb
   as a stand-in) would have found it. **Not verified:** that this would have caught that specific bug, since its cause
   on the host was never reproduced.
4. **Put the test repository close to the real one.** The publishing test used a temporary repository with no
   `.gitignore`. In the real repository `*.log` is ignored, so the runner's `git add` skipped every raw log and the
   `spike-results` branches carry `results.txt` only. The test asserted that the logs were in the commit and passed,
   because its repository differed from the real one in exactly the file that mattered. The runner now uses
   `git add -f`, `.gitignore` allows `docs/spikes/results/**/*.log`, and the tests' temporary repositories contain
   `*.log` in their `.gitignore` (they fail without the fix). Lesson: a test double must copy the repository's ignore
   rules, hooks and settings, or the check proves nothing about the real thing.
5. **A one-command setup script for pairing.** The first hardware setup ran into the two-port Wireless debugging scheme (pair
   port vs connect port) and `adb devices` listing the watch twice, which makes automatic serial detection see two watches. A script that
   runs `adb pair`, `adb connect`, detects both serials, deduplicates the mDNS name and prints the exact run command
   would remove that. The runner has `-PairWatch`, `-PairCode` and `-WatchAddress`, but not the deduplication (the
   workaround was passing `-Phone` and `-Watch`).
6. **Measure on one clock from the start.** The first measure was `delayMs` across two clocks (skew of 1.5 s on the
   emulators, -76 to -1054 ms on the hardware, so the values were useless as delays). The round trip on the sender's
   own clock (ping and pong) was added later; it should have been the only delay measure.
7. **Make the battery gate say what it checks.** The readiness gate refuses below 20 % but its message says "charge to
   at least 30 %", so the message and the check disagree. Report the actual threshold.

## Minutes actually taken

| Step | Recorded time |
|---|---|
| The six hardware runs | started between 08:45:57 and 09:08:43 on 2026-10-15 (22 min 46 s start to start) |
| First scripted emulator run until the hang | 07:08:23 to 07:12:16 (3 min 53 s), then 12+ minutes of nothing |
| Manual emulator tap-through | "hours" (user's report); **not recorded** |
| Device setup before the first hardware run, laptop and watch preparation | **not recorded** |
| Runner development and the simulated suites | not recorded per step; see the dates in the work log |

Recording a start and end time per step in the work log would answer the question "how long did validation take" next
time; it was not asked for until now.

## What was not worth doing

- **Perfecting the emulator pairing.** Google sign-in was never completed on the emulators and items crossed
  regardless. The effort to make the Pixel Watch companion app stable would not have changed any decision.
- **A rich spike UI.** The buttons were used a few times by hand; every measured case was driven by broadcasts.
- **Delay targets debated in advance to the second.** Every measured value was one to two orders of magnitude inside the
  proposed targets (nearby about 0.2 s against 10 s, relayed about 1.8 s against 60 s), so the targets never mattered.
  The agreement itself was needed (the plan requires it) but the precision was not.
- **Testing the watch's own Doze and Away.** Both need the watch off its charger and its Wi-Fi (which adb uses) in
  play; the watch stayed on its charger with adb connected, so only phone-side isolation was tested.

## What would be worth adding

- A smoke test of the runners on Windows (PowerShell 7 and 5.1) in CI, with the simulated adb.
- The three-day sleeping-apps recheck, which needs the runner once more (`-SkipBuild -OnlyCases C3`).
- Run the moved spike apps on the devices once (one `-OnlyCases C1` run) to close the gap noted in
  [How](how.md), step 8.
- Resolve the open question left by the deletion hypothesis: it was never proven, only no longer observed.
