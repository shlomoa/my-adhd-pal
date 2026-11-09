# Requirements, tests and pass rules

Every requirement of the POC, what provides it, how it is tested and when it passes. The verdict is computed by
`scripts/verdict.sh` from the raw logs of one run (`scripts/run-poc.sh`), never by hand.

## Goal 1: pulse data at most 0.5 s from recording

| Item | Detail |
|---|---|
| Provided by | Watch routes `r1` (Health Services `MeasureClient`) and `r2` (wake-up `TYPE_HEART_RATE` sensor, foreground health service, wake lock) in `:wear` `PocService` |
| Measured as | Recorded time to the moment the watch app's callback runs, both on the watch's elapsed-realtime clock. R2: `SensorEvent.timestamp` (same time base as `elapsedRealtimeNanos`, per the Android reference). R1: `timeDurationFromBoot` (the Health Services reference converts it with `currentTimeMillis - elapsedRealtime`) |
| Pass rule | Per route and screen state with at least 20 readings: p95 of recorded-to-app at most 500 ms. Goal MET when one route meets it in every state observed; PARTIAL when only with the screen on; NOT MET when there is data but never |
| Clock sanity | A latency below -5 ms, or a median over one hour, is reported as INVALID CLOCK BASE, never as a pass |
| Screen states | adb cannot reliably force a display state on this watch (the previous project found the same), so the display state (`on`, `doze` = dim/always-on, `off`) is recorded per reading and the verdict says which states were reached |

## Goal 2: control of power, sensor enabling and modes

Commands are sent directly to the watch (`docs/poc/commands.md`), not from the phone app.

| Check | What it proves | Pass rule |
|---|---|---|
| C1 enable | Sensor starts on command | status shows running and readings arrive |
| C2 disable | Sensor stops on command | status shows stopped and no reading arrives in the following 5 s |
| C3 power, system level | The OS sees our heart-rate sensor connection while running and not after stop | `dumpsys sensorservice` shows a connection of our package while running, none after stop. INCONCLUSIVE (does not fail the goal) if our package is not visible at all, because the dump format is not documented |
| C4 power, wake lock | CPU wake lock can be held and released | status and `dumpsys power` both show it held, then released. INCONCLUSIVE (does not fail the goal) if `dumpsys power` never lists our lock, because that text format is not documented |
| C5 mode: batching | A different delivery mode changes the stream | with a 5 s report latency the maximum gap between arrivals is at least twice that with 0 |
| C6 mode: route | The sensor route can be chosen | route `r2` and route `r1` each deliver readings |

Goal 2 is MET when C1 and C2 pass, C3 and C4 are not FAIL with at least one of them PASS (system-level evidence of power control), and C5 or C6 passes.

## Goal 3: live pulse over Bluetooth to an Android app, sub-second

| Item | Detail |
|---|---|
| Provided by | Two paths at once, for the same readings: (1) direct Bluetooth, an RFCOMM socket from the watch to a `BluetoothServerSocket` service in the phone app; (2) the Wearable Data Layer `MessageClient`. The phone app shows the live value and logs every arrival |
| Why two | Google documents that the Data Layer is "Bluetooth preferred, but can use Wi-Fi", so it cannot prove "via Bluetooth". The RFCOMM path is Bluetooth by construction |
| Measured as | Recorded time to the phone's receive callback. The watch and phone clocks differ, so the watch pings the phone every second over each path and logs every exchange (`exchanges-<label>-<path>.csv`). After the run the offset (phone minus watch) is taken from the exchange with the shortest round trip of the whole run, assuming equal travel time each way. Each path has its own offset |
| Pass rule | Bluetooth path only: per route and screen state with at least 20 readings, p95 recorded-to-phone under 1000 ms. The Data Layer result is printed next to it and is never substituted. Same MET / PARTIAL / NOT MET rules as goal 1 |
| Limit | The clock offset has an error of up to half the shortest round trip; the verdict prints it as the clock uncertainty and allows an end-to-end value that much below zero before calling the clock base invalid |

## Outcome

- SUCCESS: goals 1, 2 and 3 all MET.
- FAILURE: at least one goal NOT MET with data (the goal is shown to be unmet).
- INCOMPLETE: a goal has NO DATA or INVALID CLOCK BASE. The verdict then lists the likely cause from the logs
  (watch off-body, permission missing, no sensor, Bluetooth never connected, and so on).
- PARTIAL: only with the screen on, or similar.

## How the tests themselves are tested

- `./gradlew check`: unit tests in `:core` (wire format, Bluetooth framing in any split, clock offset, percentiles), style, lint.
- `scripts/test-verdict.sh`: the verdict rules against synthetic results (good, slow, dozing, no data, bad clock, bad
  permission, no Bluetooth, failed checks, and so on).
- `scripts/test-run-poc.sh`: the real `scripts/run-poc.sh` against a stateful fake adb in eight scenarios (good, slow,
  no readings, no Bluetooth, install failure, missing permission, phone not listening, smoke run).

## What cannot be verified without your devices

Whether the watch's sensor delivers on your wrist, the real latencies, whether the watch can open an RFCOMM connection to
the phone, whether `dumpsys sensorservice` shows our connection, and whether `timeDurationFromBoot` really is on the
elapsed-realtime base (documented by example, not stated outright). The run reports each of these as a result, never an assumption.
