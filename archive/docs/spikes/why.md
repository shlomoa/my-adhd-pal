# Why this validation, and why built this way

## The question that needed an answer

[`plan.md`](../plan.md) section 3 chose DataItems of the Wearable Data Layer as the only transport between the phone
(master of the schedule) and the watch (shows prompts, sends acknowledgements, selections and alerts). That choice rests
on behaviour that is documented only loosely and differs by device: whether an item written while the receiver is away
arrives when it returns, whether the phone's listener service starts with the app closed, whether a deleted item stays
deleted, and how long an urgent item takes on each link state. A protocol locked on a wrong assumption would have been
rebuilt later, so the plan made verification a gate (G-SPK1 for the emulators, G-DEC for the hardware) before any code
depended on the transport.

The fallback was decided **before** the test, in the plan: if the listener wake-up or deletion fails, or urgent alerts
are too slow, `MessageClient` is added for urgent alerts only and everything else stays items. Deciding it first keeps
the result from being argued after the fact.

## Why two stages

| Stage | Why it is there | What it cannot show |
|---|---|---|
| Emulator pair (0b.2a) | Cheap and repeatable; needs no hardware or the user's devices; finds gross errors and tooling problems early | Samsung battery management, the Galaxy Wearable pairing, real Bluetooth and the relay, real clocks |
| Real devices (0b.2b) | The only way to see Nearby, Relayed and Away, Samsung background limits and real delays | Days-long behaviour (sleeping apps), a watch out of range |

The split also put the risky human step, the user's own devices (gate H2), as late as possible: by the time the user
was asked, the scripts had been exercised on emulators and against a simulated adb.

## Why a scripted runner instead of tap-through buttons

The first version of the spike was a checklist the user walked through by tapping buttons on two emulators and pasting
log lines back ([`0b2a-checklist.md`](0b2a-checklist.md)). It worked, but it cost the user hours, produced results that
were hard to compare, and could not repeat a case cheaply. A runner (`scripts/spike/run-0b2a.ps1`, then
`run-0b2b.ps1`) replaced it because:

- every action is an `adb shell am broadcast` and every observation is a `logcat` line, so a case is exactly the same
  each time and takes seconds instead of minutes of attention;
- the run ends with one results table and the raw per-case logs, committed on a branch, so nothing is pasted by hand;
- the same cases could be re-run alone (`-OnlyCases`) after a failure;
- the runner itself could be tested against a simulated adb before the user ever ran it.

## Why the spike apps live inside the real apps

The spike needs the real `applicationId` (the phone and watch apps must share it for the Data Layer to route items) and
the real signing and manifest-listener behaviour (a listener declared in the manifest is what starts the process with
the app closed). A separate throwaway app would have tested something else. So the spike is an **extra launcher entry**
(*Spike*), a command receiver and a listener service **inside** `:phone` and `:wear`, driven by `adb shell am broadcast`
(no UI taps), logging one line per event under the logcat tag `SPIKE` that the runner greps. The UI buttons exist only
for a human who wants to look. See [Spike apps](spec-apps.md).

## Why the results are published as branches

The runner commits only its results folder on a new branch `spike-results/0b2b-<timestamp>` from a separate git
worktree and pushes it with the user's existing credentials. The user's working branch and uncommitted files are never
touched, nothing needs to be copied between chat and repository, and a result that is later disputed has a commit
behind it. The cost: raw logs depended on a `.gitignore` rule that silently dropped them, see
[Learnings](learnings.md).

## Why PowerShell

The user's PC is Windows and already has `adb` from Android Studio; PowerShell needs nothing else installed, and the
runner uses only syntax valid in both Windows PowerShell 5.1 and PowerShell 7. In practice only PowerShell 7 was used
(**Windows PowerShell 5.1 not verified**). The cost, again in [Learnings](learnings.md): the developer's Linux machine
could test the runner only with PowerShell 7 on Linux, which hid a bug that appeared on the user's host.

## Why the radios, Doze and standby buckets are driven with adb

The link states and the background limits had to be produced on demand, without root and without the user touching the
phone: `svc bluetooth|wifi|data` for Nearby, Relayed and Away; `dumpsys deviceidle force-idle` with
`dumpsys battery unplug` for Doze; `am set-standby-bucket` for the `rare` and `restricted` buckets. Each action is
followed by a read-back (`settings get`, `dumpsys deviceidle get deep`, `am get-standby-bucket`); if the device does not
confirm it, the case is **NOT RUN** (exit code 2) rather than a silent pass, and a manual fallback step names exactly
what to tap. On the Galaxy A55 5G with Android 16 all of them took effect without root.

## Why round trips are measured on one clock

The first measure, `delayMs = receive time - send time`, compares two device clocks and was off by up to 1.5 s on the
emulators (and -76 to -1054 ms on the hardware). The runner therefore measures a **round trip on the sender's own clock**:
the pinger writes `/spike/ping/<id>` with its own elapsed-realtime, the peer answers `/spike/pong/<id>` echoing that
number, and the pinger logs `RTT = now - echoed value`. Half a round trip is an upper bound for one way. The `delayMs`
values are still logged but are labelled clock-skew contaminated everywhere they appear.
