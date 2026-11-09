# Real-device demo

One command runs the whole round trip on the Galaxy A55 5G and the Galaxy Watch6 Classic, with no taps: the phone publishes
a reminder, the watch receives it, buzzes and shows it, the script presses Done for you, and the acknowledgement arrives in
the phone's history. Script: [`scripts/demo/run-demo.ps1`](https://github.com/shlomoa/my-adhd-pal/blob/main/scripts/demo/run-demo.ps1).

## Run it

Set up the phone (USB debugging) and the watch (Wireless debugging) as in [`dev-setup-windows.md`](dev-setup-windows.md),
then in PowerShell from the repository folder:

```text
.\scripts\demo\run-demo.ps1 -WatchAddress 192.168.1.57:41203
```

Use the ip and port that the watch shows on its Wireless debugging screen. The script checks that both devices are ready
(and says which one is not and what to do), builds and installs the debug apps, starts them and runs the stages below.
Leave the watch alone; the reminder is due after two minutes (`-MinutesAhead` changes that). It prints PASS or FAIL for
every stage, what you should have seen on the watch, and pushes the results as a branch `demo-results/<time>` (only the
results folder; your own branch and files are untouched). Add `-SkipBuild` to reuse the installed apps, `-SkipPush` to keep
the results local, `-Phone` and `-Watch` to name the devices.

To feel and see the reminder yourself, add `-WaitForTap`: the script does not press Done. After the Notification stage it prints
"Feel and look at the watch now, then tap Done" and waits up to `-TapWaitSec` seconds (default 90) for you to tap an action on
the watch (Done, Snooze, Start 2 min or Dismiss). The Done stage then names the action you tapped and the Ack stage checks that
action in the phone history. Without a tap in time the Done stage fails and the Ack stage is not run.

## Stress demo (milestone M1)

One command shows the user's definition of stress detection end to end, with no prompts and no taps: a heart-rate reading is compared with
the baseline, a stress event is reported at once, the local response runs on the watch and the alert reaches the phone, as a PASS/FAIL
timeline with the time of each stage. Script:
[`scripts/demo/run-stress-demo.ps1`](https://github.com/shlomoa/my-adhd-pal/blob/main/scripts/demo/run-stress-demo.ps1).

```text
.\gradlew.bat :wear:assembleDebug :phone:assembleDebug
.\scripts\demo\run-stress-demo.ps1 -Phone <phone adb serial> -Watch <watch ip:port or adb-...._adb-tls-connect._tcp name>
```

Wear the watch and keep its screen on; keep the phone unlocked. Stages: preflight, grants, monitoring (the Monitoring screen starts the
foreground health service), baseline, reading, event, response, watch-alert, phone-alert, episode-end ("I'm OK" on the watch). The result
is published as `demo-results/<time>`; the file holds no address, serial or heart-rate value. With `-Real` nothing is injected and the demo
waits (`-WaitSec`) for your own heart rate to cross baseline + offset.

What is real and what is injected: the Monitoring service reads the heart rate with the live route and hands every reading to the same
evaluator ([`MonitorHub`](https://github.com/shlomoa/my-adhd-pal/blob/main/wear/src/main/java/com/shlomoa/myadhdpal/wear/monitor/MonitorHub.kt))
as the debug injector (a broadcast receiver in the debug build only) does; the injector is a real code path into the evaluator, not a mock. The
baseline is injected too (a real baseline needs 24 hours of wear, and the hourly lows are not yet saved across restarts).

Honest limit: this works with the screen on. With the screen dim or off the readings arrive in bursts, minutes late (experiment E2,
[`plans/biometrics.md`](plans/biometrics.md) section 12.3); making detection work then is a separate follow-up (milestone M2).

## Phone walkthrough with pictures

One command walks through the phone app on the Galaxy A55 5G with no taps and no watch needed:

```text
.\scripts\demo\run-phone-demo.ps1
```

Unlock the phone, keep its screen on and connect it by USB (accept the debugging prompt). The script builds and installs the
debug app with its permissions, puts example data on it (two tasks with reminders, an acknowledgement, a snooze and an
answer), opens Watch, Tasks, Ask, Settings and History and takes a screenshot of each, stores an alert like one from the
watch and checks that the phone raises it and shows the full-screen alert (screenshot), sends "I'm OK" and photographs the
screen after it, and records the whole walk-through (`screenrecord`, at most `-RecordSec` seconds, default 150, never more
than 180). It prints PASS or FAIL for the stages Ready, Install, Seed, Screens, Alert and Recording and publishes the
results as a branch `demo-results/phone-<time>` (only `docs/demo/results/phone-<time>/`: `index.md` with the pictures,
the PNG files, `phone-demo.mp4`, `results.txt` and `phone.log` without the device serial). `-SkipBuild` reuses the installed
app, `-SkipPush` keeps the results local, `-Phone` names the phone. Every adb call has a hard timeout
(`-AdbTimeoutSec`). Exit codes: 0 all stages pass, 1 a stage failed, 2 the phone is not ready or a stage could not run,
3 setup problem.

The five states of the watch link cannot be forced on a real phone; they are in the rendered images above. The alert stage
needs the notification permission and the full-screen permission (the script grants both; if Samsung's settings still block
the full-screen alert the stage fails and says where to switch it on). `scripts/demo/test-run-phone-demo.sh` runs the script
against a simulated `adb` (needs PowerShell 7 as `pwsh`); it does not prove how the real phone behaves.

## Phone screens without a device

[`demo/phone/index.md`](demo/phone/index.md) shows every screen of the phone app as an image: the five watch link states,
the alert permission card, history (full, empty, failed), settings, the Ask form, tasks (full, empty) and the alert screen.
They are rendered from the real Compose code with fixed example data, on a JVM with no phone. To refresh them:

```text
./gradlew :phone:testDebugUnitTest --tests "*PhoneScreensTest*"
```

then copy `phone/build/screens/*` to `docs/demo/phone/`.

## Stages

| Stage | What is checked | Where it is read from |
|---|---|---|
| Ready | both devices answer, boot is complete, battery is at least 20% | `adb` |
| Install | the debug apps are installed and start | `adb` |
| Publish | the phone publishes the demo schedule (reminder id `demo`) | phone log line `published schedule` |
| Schedule | the schedule reaches the watch | watch log line `schedule applied` |
| Reminder | the reminder fires within 1 minute of its time | watch log line `reminder fired` |
| Notification | the watch has the reminder notification | `dumpsys notification` |
| Done | the Done action is sent on the watch | watch log line `ack sent` |
| Ack | the acknowledgement is in the phone history | phone log line `history ack` |

The log lines have the tag `DEMO` and are written by debug builds only; they hold ids and times, never reminder text. The
haptic cannot be read with `adb`, so it is a line to feel, not a PASS or FAIL: one short tick when the reminder fires.

## What it needs from the apps

- Watch (debug build): the `DEMO` log lines and `DemoActionReceiver`
  (`adb shell am broadcast -n com.shlomoa.myadhdpal/.wear.debug.DemoActionReceiver --es action DONE`).
- Phone (debug build, agreed with the Android lane): `PUBLISH_DEMO_SCHEDULE` and `DUMP_HISTORY` broadcasts, and their `DEMO`
  log lines. The names are at the top of the script in one place.
- A working link: the real Data Layer transport on the watch and the phone's listener. Until both exist the Schedule stage
  fails, and the script says so.

## Test of the script

`scripts/demo/test-run-demo.sh` runs the script against a simulated `adb` (needs PowerShell 7 as `pwsh`) and checks every
stage, the failure messages, the exit codes and the publishing. It proves the script's logic, not how the real devices behave;
that is the user's run (TC-W-103).
