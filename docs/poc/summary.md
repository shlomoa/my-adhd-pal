# POC summary: declared done

Result of the final run (`poc-results/20261109-174743`), on a Galaxy Watch6 Classic and a Galaxy A55, built-in sensor, no extra hardware.

| Goal | Result | Measured |
|---|---|---|
| 1. Pulse data at most 0.5 s from recording | **Achieved** with route r2 (wake-up heart-rate sensor, foreground service, wake lock) | p95 recorded-to-watch-app 12 ms (screen on, 64 readings) and 20 ms (screen off, 76 readings). Route r1 (Health Services) is fine with the screen on but delivers up to 41 s late when the display is dim or off, so it does not meet the goal |
| 2. Control of power, sensor enabling and modes | **Achieved** | Enable, disable (no readings afterwards), sensor power seen by the OS (`+`/`-` in `dumpsys sensorservice`), wake lock held and released, and route selection all pass. Batching mode (5 s report latency) is accepted by the OS but has no effect on delivery |
| 3. Live pulse to an Android app over Bluetooth, sub-second | **Achieved** over direct Bluetooth (RFCOMM) | p95 recorded-to-phone 44 ms (screen on) and 66 ms (screen off), clock uncertainty +-9 ms; the Data Layer path was about 100 ms |

282 readings judged for goal 1, 280 received over Bluetooth, 282 over the Data Layer; about one reading per second.

## Additional findings

- The pulse reaches the phone as whole beats per minute, once per second: PPG, beat-to-beat intervals and HRV cannot be calculated from it.
- The watch's own raw heart sensor (`Samsung HR Raw Sensor`) is readable by an ordinary app with the heart-rate permission: 627 events in 25 s (24.8 Hz, 16 channels). One channel carries a pulse waveform from which the app found 23 beats (mean interval 1097 ms, 54.7 bpm; SDNN 76 ms, RMSSD 72 ms, pNN50 52 %). At 24.8 Hz the timing resolution is 40 ms, so these HRV numbers are indicative, not clinical: they are not yet checked against a reference, and only the first four of the 16 channels were analysed.
- The phone app has a live indicator (status light, pulse, graph of the last minute, accuracy, latency, min/avg/max, trend); the log shows it LIVE throughout the run. Its layout was not visually tested in the build.

## Limits of this result

One run, one wrist, about 40 s per phase. The dim (always-on) display state was not reached; screen on and screen off were. Goal 2 is software control of the sensor and wake lock, not hardware power measurement.

## Where things are

- Tag `v0.1.0-pre-poc`: the project before the POC, also copied in `archive/`.
- This POC: `:wear` (watch app), `:phone` (phone app), `:core` (shared logic); how to run: `scripts/run-poc.sh`; rules: [requirements](requirements.md); record of the work: [findings](findings.md).
