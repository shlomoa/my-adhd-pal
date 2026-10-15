# Watch end-to-end checklist (step 3.9, gate G7)

For the user. Cases TC-W-090 to TC-W-093 and TC-W-103 are **NOT yet run**; this step is not done, and G7 is not flipped, until
the results below are reported and copied into [`../work-log/watch.md`](../work-log/watch.md). Everything the script can check
is checked by [`scripts/demo/run-demo.ps1`](../../scripts/demo/run-demo.ps1) (see [`../demo.md`](../demo.md)); the rest is
for your eyes and hands.

## A. Real devices, phone present (TC-W-091, TC-W-103)

1. Run `.\scripts\demo\run-demo.ps1 -WatchAddress <ip:port>` as in `demo.md`.
2. Expect every stage PASS, the reminder on the watch within 1 minute of its time and one short tick.
3. Report: the results branch name (`demo-results/<time>`), and whether you felt the haptic and saw the notification with the
   screen off.

## B. Real devices, phone away (TC-W-092)

1. With both apps installed and the schedule synced, turn the phone's Bluetooth and Wi-Fi off (or take it out of range).
2. On the phone, before switching off, publish a reminder for 3 minutes ahead (`-MinutesAhead 3`, `-SkipBuild`).
   Do the next steps with the phone off: wait for the reminder on the watch, tap Done by hand.
3. Switch the phone's radios back on. Within about a minute the acknowledgement should show in the phone's History tab.
4. Report: reminder fired on time (yes/no), the ack appeared after reconnecting (yes/no), shown once (not twice).

## C. Bluetooth toggled (TC-W-093)

1. Watch home screen open and phone nearby: it reads "Phone nearby".
2. Switch the phone's Bluetooth off, then on. Expected: Relayed or Away within about 10 s, Nearby again within about 5 s of
   Bluetooth returning.
3. Send a schedule change while Bluetooth is off and another reminder acknowledgement from the watch. Expected: both arrive
   after Bluetooth returns, none lost, none twice.

## D. Paired emulators (TC-W-090)

Pair the phone and watch emulators as described in [`../dev-setup-windows.md`](../dev-setup-windows.md), then run steps A.1 to A.3
with the emulator serials as `-Phone` and `-Watch`. Report which hop failed if any (schedule, reminder, ack, history).

## Report template

```text
A: run-demo results branch <name>, haptic felt yes/no, notification with screen off yes/no
B: fired on time yes/no, ack after reconnect yes/no, duplicates yes/no
C: states seen, items after reconnect (lost/duplicated)
D: emulators, hops passed
```
