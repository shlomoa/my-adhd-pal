# Watch-phone protocol

Status: **skeleton** (protocol 1.0, steps 1.1 to 1.3). This file is the single source of truth; the constants, models
and codec in `:protocol` follow it, and unit tests keep them equal. The compatibility function, the link-state function,
the schedule logic, the fake transport and the diagrams are added in steps 1.4 to 1.8. The transport verification below
comes first in time and changes the cleanup rule.

## Item catalog

All traffic is Data Layer items (decision in [`plan.md`](plan.md) section 3). The writer is the only node that creates,
replaces or deletes an item (writer-owns-cleanup rule, see below).

| Path | Direction | Body model | Urgent | Cleanup |
|---|---|---|---|---|
| `/schedule` | phone to watch | `Schedule` | only if it changes the next 30 minutes | replaced by the next version |
| `/prompt/<id>` | phone to watch | `Prompt` | yes | phone deletes it on the answer or at expiry |
| `/ack/<id>` | watch to phone | `Acknowledgement` | yes | watch deletes it after a delay, or the phone after storing it |
| `/selection/<id>` | watch to phone | `Selection` | yes | same as `/ack/<id>` |
| `/alert/<id>` | watch to phone | `Alert` | yes | same as `/ack/<id>` |
| `/hello/phone` | phone to watch | `Hello` | no | overwritten at start |
| `/hello/watch` | watch to phone | `Hello` | no | overwritten at start |
| `/debug/biometrics` | watch to phone | `BiometricSamples` | no | debug builds only; overwritten |

`<id>` is a lower-case canonical UUID (`123e4567-e89b-42d3-a456-426614174000`). A path with any other id, a trailing
slash or an extra segment is not a protocol path and is ignored.

## Item format

Every item has four keys:

| Key | Type | Meaning |
|---|---|---|
| `v` | int | protocol major version of the writer |
| `id` | string | UUID of the item; the same as `<id>` in the path for event items; a fresh UUID per write for the others |
| `ts` | long | sender clock in epoch milliseconds when the item was written |
| `body` | string | JSON produced by the `:protocol` codec |

`:protocol` never touches Android's `DataMap`; thin adapters in `:wear` and `:phone` convert between `DataMap` and the
`Envelope` type. Times inside a body (`at`, `expiresAt`) are epoch milliseconds on the clock of the node that wrote the
item. The two clocks differ (the emulator spike measured about 1.5 s), so a receiver never computes a delay from them.

Bodies (field names are the JSON keys):

| Model | Fields |
|---|---|
| `Schedule` | `version` (long, monotonic), `reminders` (`id`, `title`, `minuteOfDay` 0-1439, `days` 1-7 with 1 = Monday), `checkpoints` (`id`, `label`, `minuteOfDay`), `snooze` (`minutes`, `maxCount`), `alerts` (`thresholds` of `metric`, `direction`, `value`; `cooldownSeconds`) |
| `Prompt` | `title`, `options` (1 to 6 of `key`, `label`; keys unique), `expiresAt` (optional) |
| `Acknowledgement` | `action` (`DONE`, `SNOOZE`, `START_2_MIN`, `DISMISS`), `at` |
| `Selection` | `key` (an option key of the prompt with the same id), `at` |
| `Alert` | `metric` (`HEART_RATE`), `value`, `threshold`, `at` |
| `Hello` | `side` (`PHONE`, `WATCH`), `protocolVersion` (for example `1.0`), `appVersion` |
| `BiometricSamples` | `samples` (`at`, `heartRate`), at most 500 |

Limits (`Limits` in `:protocol`): 100 reminders, 24 checkpoints, 8 thresholds, 6 options, texts 1 to 200 characters,
keys and versions 1 to 32 characters, snooze 1 to 240 minutes and 0 to 10 snoozes, cooldown 0 to 86400 seconds. The
largest legal item stays below 40 KB, less than half of the 100 KB DataItem limit.

An item whose body is malformed, has a missing or mistyped required field, an unknown enum value or an out-of-limit
value is dropped, and the failure is logged in debug builds. Unknown extra fields are ignored and the known fields are
kept.

## Versioning policy

`Protocol.VERSION` is `major.minor`. The major version is the compatibility boundary and is written in `v` of every
item and in the `Hello` body. Additive changes (a new optional field, a new item path) raise the minor version and stay
compatible; a receiver ignores fields and paths it does not know. A change that removes or reinterprets a field raises
the major version. A receiver ignores items whose major version it does not support and shows Incompatible. There is no
negotiation, request or reply. The compatibility function is added in step 1.4.

## Link states

The phone shows one of five states for the watch link. The function that derives it from the connected nodes, the app
capability and the hello versions is added in step 1.5; the states are fixed now.

| State | Meaning |
|---|---|
| Nearby | the watch app is installed, the watch is directly connected, and its hello version is compatible |
| Relayed | the watch app is installed, the watch is connected only through the relay, and its hello version is compatible |
| Away | no watch is connected |
| No watch app | a watch is paired, but without the app capability |
| Incompatible | the watch's hello has an unsupported major version, or is missing or malformed |

## Transport verification: spike 0b.2a (paired emulators)

Question set from [`plan.md`](plan.md) section 3: (1) items persist while the receiver is away and arrive on reconnect,
(2) the phone's listener service wakes with its app closed, (3) a consumed item can be deleted and does not reappear,
(4) urgent-item delay. The spike code is throwaway and lives only on the branches `claude/shared-0b2a-spike` (the apps)
and `claude/shared-0b2a-spike-rtt` (the apps plus the runner `scripts/spike/run-0b2a.ps1`); it is discarded in step
0b.3. Run by the user on real Android emulators, not on hardware.

### Environment

| | |
|---|---|
| Host | Windows 11, PowerShell, Android Studio (version not reported) |
| Phone emulator | image `sdk_gphone16k_x86_64`, API 36, Google Play |
| Watch emulator | AVD `Wear_OS_Large_Round` (API level not reported) |
| Pairing | Device Manager, Pair Wearable |
| Spike build | apps from commit `5abd291` in the manual runs; runner `7476512` for the first scripted run |
| Google sign-in | not completed on either emulator, and items still crossed: **no Google sign-in is needed on emulators** |

Clock skew between the two emulators: **about -1.5 s (watch behind phone)**, measured as -1496 ms by the runner (about
1.55 s in the manual run). Every `delayMs` value in the spike compares two different clocks, so it contains this skew
and is **not** a delivery delay.

### Results

| Case | Question | Result | Evidence |
|---|---|---|---|
| C1 items cross the pairing, both ways (TC-S-121) | 3 | PASS | `RECV` on the receiver in both directions, in the manual runs and in the scripted run |
| C1b deletion symmetry (TC-S-124) | 3 | **FAIL in one direction**, see below | skew -1496 ms; table below |
| C2a a KEEP item stays on both devices | 3 | PASS | listed on phone and watch |
| C3a phone listener wakes with the app closed, background process killed, `pidof` empty (TC-S-123) | 2 | PASS | `SERVICE-CREATED`, `RECV` with `fg=false` in a fresh process, `processUptimeMs=510` |
| C3b the same after `am force-stop` | 2 | PASS | `fg=false`, `processUptimeMs=447` |
| C5 watch away: sent while the watch emulator was off, then it restarted (TC-S-122, TC-S-125) | 1 | PASS | items arrived 12 s after boot completed, the sender saw `NODES count=0` while away, and the link recovered on its own with nothing done by the user |
| C5 phone away | 1, 2 | not run | deferred to 0b.2b |
| C2b deleted items do not reappear after the reconnect | 3 | not run | deferred to 0b.2b |
| C4 urgent versus non-urgent delay | 4 | not run | deferred to 0b.2b |

Delivery after a cold start was about 2 to 3 s once the clock skew is removed (manual run, indicative only).

### Deletion finding

The scripted case C1b wrote three items per group, deleted them on the receiving side, waited 45 s, then listed the
items on both devices. "Writer" is the node that wrote the item, "deleter" the other node.

| Group | Items | Still listed on the writer | Still listed on the deleter | Writer logged the deletion event |
|---|---|---|---|---|
| watch writes, phone deletes at once | 3 | 0 | 0 | 3 of 3 |
| watch writes, phone deletes after 6 s | 3 | 0 | 0 | 3 of 3 |
| phone writes, watch deletes at once | 3 | **3** | 0 | **0 of 3** |
| phone writes, watch deletes after 6 s | 3 | 0 | 0 | 3 of 3 |

Only a delete by the watch of a phone-written item, issued straight after the item arrived, was lost on the phone:
the watch reported success (`count=1`), yet the phone still listed the item and never received a deletion event. In the
manual run such an item was still listed on the phone 27 minutes later. Every other group is clean. The spike code is
symmetric (the same `deleteDataItems` call on the item's own URI on both sides), so a spike bug is not the explanation.

**Hypothesis, not proven:** Data Layer writes and deletes are time-stamped, and the watch clock is about 1.5 s behind the
phone. A delete made within the skew window after the phone's write is then stamped as older than the write and does
not win, while the phone's delete of a watch-written item is always stamped later, and a delete 6 s later (more than the
skew) also wins. The pattern fits, but only the real devices (0b.2b, with their own clock offset) can confirm it.

### Rule that follows: the writer owns cleanup

Replaces "the consumer deletes an item after storing it" in [`plan.md`](plan.md) section 4:

- A node deletes the items it wrote itself. The **watch never deletes phone-written items** (`/prompt/<id>`,
  `/schedule`): the phone removes a prompt when it receives the answer or when the prompt expires.
- The **phone may delete watch-written events** (`/ack/<id>`, `/selection/<id>`, `/alert/<id>`) after storing them,
  because that direction worked in every run.
- Consumers stay idempotent by `id`: an item that survives a lost delete is handled once, so a surviving item is harmless.

This is decided from emulator evidence and is **final only after 0b.2b**, which repeats the deletion groups on the
Galaxy Watch6 Classic and Galaxy A55 5G.

### Other observations

- The Google Pixel Watch companion app on the phone emulator stopped once ("keeps stopping") on an earlier attempt. The
  effect on the link was not determined. Because the emulator pairing is bridged by that app, it matters for the
  reconnect cases, and 0b.2b checks the Samsung pairing path separately.
- Two spike-tooling problems cost time and are not protocol findings: the first scripted run hung at C5 because an
  `adb` call to the stopped emulator never returned (fixed in the runner with per-call timeouts), and the host laptop
  went to sleep during a run.
- The partial output of the first scripted run is kept as evidence:
  [`spikes/results/0b2a-first-real-run-partial.txt`](spikes/results/0b2a-first-real-run-partial.txt).

### Fallback-rule evaluation

Rule from [`plan.md`](plan.md) section 3: if the spike fails on (2) the listener wake-up or (3) deletion, or urgent
alerts are too slow, add `MessageClient` for urgent alerts only; everything else stays as items.

| Criterion | Emulator outcome | Consequence |
|---|---|---|
| (2) listener wakes with the app closed | PASS, both after a kill and after a force-stop | none |
| (3) a consumed item can be deleted and does not reappear | works, except a quick delete by the watch of a phone-written item | not a trigger: the writer-owns-cleanup rule avoids the failing case without changing the transport |
| (4) urgent-item delay | not measured; every `delayMs` is clock-skew contaminated | decided in 0b.2b with round trips on one clock and delay targets agreed first |
| (1) persistence while away | PASS with the watch away; phone away not run | phone away is checked in 0b.2b |

**Decision at G-SPK1:** the transport stays **DataItems only**; the `MessageClient` fallback is not applied. The decision
becomes final at G-DEC, after the hardware spike.

### Deferred to the hardware spike (0b.2b)

The laptop sleeping and the hung first runner stopped the emulator runs; these are not protocol failures. Move into the
0b.2b checklist, and reuse the same runner (`scripts/spike/run-0b2a.ps1` on `claude/shared-0b2a-spike-rtt`, with
`-OnlyCases`, which also reports round trips on the sender's own clock):

- C5 phone away (the listener started by a restarted phone), C2b (deleted items do not reappear), C4 urgent versus
  non-urgent round trips, and a repeat of the C1b deletion groups.
- The hardware-only items already in the plan: Nearby, Relayed and Away, Samsung battery management with default
  settings, and pairing through the Galaxy Wearable app. The runner's emulator restart and `adb emu kill` steps do not
  apply to a watch and phone; how the away state is produced on hardware is decided in 0b.2b.
