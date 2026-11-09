# Transport validation spikes (0b.2a and 0b.2b)

Before the protocol was locked, the Wearable Data Layer (DataItems) was tested twice: on a paired phone and watch
**emulator** pair (0b.2a), then on the **real devices**, a Galaxy A55 5G and a Galaxy Watch6 Classic (0b.2b). This folder
documents the whole effort: why it was built this way, how it went (including what went wrong), where every piece
lives, what could have been faster, and the exact specification of the spike apps and the tests. The decision itself
(DataItems only, writer-owns-cleanup kept) and the measured numbers are in [`../protocol.md`](../protocol.md); this folder
is about the **validation**, not the protocol.

| Page | Question it answers |
|---|---|
| [Why](why.md) | Why this validation architecture, and why this implementation |
| [How](how.md) | What happened, step by step, in both stages, with the failures |
| [Where](where.md) | Where each piece lives in the repository, and the raw logs, and what lives only on the devices |
| [Learnings](learnings.md) | What would be faster, cheaper and more reliable next time, and what was not worth doing |
| [Spike apps](spec-apps.md) | Components, manifest entries, adb commands and extras, item paths, log line formats |
| [Test cases and runners](spec-tests.md) | Every case of both runners, parameters, exit codes, and the simulated-adb suites |
| [0b.2a run checklist](0b2a-checklist.md) | The original tap-through checklist for the emulator stage |
| [0b.2b plan](0b2b-plan.md) | The hardware plan: decisions, device setup, the one command, the user checklist |

## At a glance

| | 0b.2a (emulators) | 0b.2b (hardware) |
|---|---|---|
| Devices | phone emulator (image `sdk_gphone16k_x86_64`, API 36, Google Play) and the Wear OS AVD `Wear_OS_Large_Round` (API level not reported) | Galaxy A55 5G over USB, Galaxy Watch6 Classic over Wi-Fi debugging, Android 16 on both |
| Driver | tap-through checklist, then `scripts/spike/run-0b2a.ps1` | `scripts/spike/run-0b2b.ps1` |
| What it could show | persistence, deletion, listener wake with the app closed, reconnect | Nearby, Relayed and Away, round-trip delay, Doze and standby buckets, Samsung pairing path |
| Main result | a quick delete by the watch of a phone-written item was lost, so the writer-owns-cleanup rule | every automated case passed; the deletion asymmetry did not occur on hardware; the transport is DataItems only |
| Evidence in the repository | the tables in `protocol.md`, the partial first scripted run and its raw logs | six `results.txt` files, `protocol.md`, raw logs (see [Where](where.md)) |

## What the spike code is, now

The spike apps are not throwaway any more. They are kept as **debug-only test tooling** inside the real apps (an extra
launcher entry named *Spike*, a command receiver and a listener service), so the evidence can be reproduced when the
real link client changes, when a new phone or watch arrives, or for the optional days-long sleeping-apps recheck. They
are in the debug source sets only; a release build contains none of them, and CI checks that
(see [Where](where.md)).

## Status of the claims on these pages

Every statement was checked against the code, the results files or the work log at the time of writing. Where a
statement could not be checked it is marked **not verified**. The two largest gaps: the spike apps were moved into the
debug source sets after the hardware run, and the command receiver got the `DUMP` permission, and they have **not been
run on the devices since** (only built and covered by the simulated suites; the three-day recheck needs them), and **Windows PowerShell 5.1 was never used**; only PowerShell 7.6.6 on Windows and Linux.
