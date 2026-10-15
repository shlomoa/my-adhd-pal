# Real-device demo

One command runs the whole round trip on the Galaxy A55 5G and the Galaxy Watch6 Classic, with no taps: the phone publishes
a reminder, the watch receives it, buzzes and shows it, the script presses Done for you, and the acknowledgement arrives in
the phone's history. Script: [`scripts/demo/run-demo.ps1`](../scripts/demo/run-demo.ps1).

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
