# Research, findings and issues

A separate record of the work, not part of the test output: what was researched and found, why several runs were needed, and the issues on both sides. The test output itself (`scripts/run-poc.sh`) ends with the results report.

## Research and findings

What was researched, and what was found (sources: Android and Samsung documentation, the previous project's own probes):

- Existing project and hardware: a Kotlin Wear OS and phone app on a Galaxy Watch6 Classic and a Galaxy A55. Its own earlier probes measured Health Services `MeasureClient` at about one reading per second with the screen on, and delays of minutes with the display dimmed or off. That was the main risk to goal 1.
- Health Services (`MeasureClient`) exposes heart rate in beats per minute only. Android `SensorManager` has `TYPE_HEART_RATE`; there is no raw-pulse sensor type.
- Time bases: `SensorEvent.timestamp` is on the same base as `SystemClock.elapsedRealtimeNanos()` (stated in the Android reference). For Health Services `timeDurationFromBoot`, the reference shows converting it with `currentTimeMillis - elapsedRealtime`, so it is on that base too (shown by example, not stated outright).
- Samsung Health Sensor SDK (Galaxy Watch4 and later): raw pulse waveform and beat-to-beat intervals, developer mode for testing, partner programme to distribute. Its download redirects to a Samsung login, so it was not used; the goals ask for pulse values, not the raw waveform.
- Samsung's developer blog: a wake-up heart-rate sensor, a foreground health service and a wake lock keep readings flowing with the screen off.
- Wearable Data Layer: Google documents it as "Bluetooth preferred, but can use Wi-Fi", with best-effort delivery. So it cannot prove "over Bluetooth". A direct Bluetooth socket (RFCOMM: a server socket on the phone, a client socket on the watch) can. On Android 12 and later the runtime permission `BLUETOOTH_CONNECT` is needed; `BLUETOOTH_SCAN` is needed for discovery calls.
- Android 16 (API 36) uses `READ_HEART_RATE` for heart rate and `READ_HEALTH_DATA_IN_BACKGROUND` for background health use.
- A web search for a known Wear OS 6 bug where heart rate delivers nothing found none.
- The archived project's probe on this same watch started its service the same way as this POC and got about 2 readings per second, which ruled out one of my theories (that the starting screen must stay visible).

## What the real run showed (run 20261109-163104, corrected)

- Route r2 (wake-up sensor with a wake lock) delivered readings within about 13 ms (screen on) and 20 ms (screen off) of recording; route r1 (Health Services) was fine with the screen on but delivered readings up to 43 s late when the display was dim or off.
- Direct Bluetooth to the phone: p95 end to end 98 ms (screen on) and 121 ms (screen off); the Data Layer was a little slower (128 and 173 ms).
- The OS accepted a 5 s report-latency request for the heart-rate sensor but readings still arrived once per second, so the batching mode has no effect on this sensor; route choice, enable/disable and the wake lock do work.
- The pulse arrives as whole beats per minute once per second, so PPG, beat intervals and HRV cannot be calculated from it. The watch also lists a raw heart sensor (`Samsung HR Raw Sensor`) that the next run probes.

## Why several runs were needed

I cannot reach your watch or phone from where I work. My own tests use simulated devices, so they check my logic, not Android. Only a run on your devices shows how they really behave, and each real run showed something the simulation could not:

- Run 1 (full): no readings in any phase, no error. The cause was not isolated. Before the next run I changed several things at once (a missing notification icon, the background health permission, a visible screen-on activity, plus diagnostics), so I cannot say which one fixed it.
- Run 2 (screen-on only): readings flowed (about one per second). Bluetooth failed: the watch's `cancelDiscovery()` call needs `BLUETOOTH_SCAN` on Android 12 and later, which I had not granted. The clock offset known while the run was still going gave a negative value early in the run. Dim, screen-off and the control checks had not been part of that run.
- This run: the fixes for both, the full matrix and the control checks.
- Several attempts before the first result files failed before any measurement: the script reused an old APK left from the previous project (wrong package name), and install and permission errors were hidden by my script.

## Issues I encountered

- The remote tag could not be pushed (HTTP 403 from the git proxy and from the GitHub API); the tag exists only locally.
- The first build of the old project was killed and the user asked why it was needed; it was removed from the plan.
- Build problems: a missing Guava class, an XML comment containing `--`, style checks (detekt), a lint permission error. The code formatter rewrote my code twice, which broke later text edits.
- Bugs found by my own tests: the screen field was dropped when decoding a reading; a runaway line corrupted the next Bluetooth frame; the verdict script swallowed its own explanation lines; install errors were hidden; a stale APK was reused.
- My first full run took 10 minutes 46 seconds, over the 10-minute limit; phases were cut to 40 seconds.
- I deleted the results folder of a real run while cleaning up test output; it was restored from history.
- I asked for approvals more often than needed, and mode confusion (plan mode versus auto mode) cost time.
- Still unknown: exactly why run 1 produced no readings.

## Issues you encountered

- `adb` was not on the PATH (Windows).
- Pairing the watch failed with "protocol fault" because the pairing code and port had expired.
- The phone never showed the USB "Allow" prompt, so it was connected with wireless debugging instead.
- `adb devices` listed the watch twice (once by address, once by name).
- "Package not found" after an install that reported success (the stale APK).
- `git pull` blocked by a local `poc-results` folder.
- The phone screen locks automatically, and the test needs it awake at the start.
- A run with no readings, then a run of over 10 minutes.
- Several of my instructions were not simple enough ("keep both devices connected over adb").
