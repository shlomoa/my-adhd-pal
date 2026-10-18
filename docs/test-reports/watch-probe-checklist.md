# Watch probe checklist (experiments E1, E3, E4 and E6)

For the user, on the real Galaxy Watch6 Classic, with the debug build. **Not yet run.** It answers the open questions of
[`../plans/biometrics.md`](../plans/biometrics.md) sections 11 and 12 against the definition in 12.1: heart rate measured
continuously, and a stress event reported immediately. The experiments are E1 (live route per screen state, issue #228),
E3 (service started from the visible app without the background permission, #230), E4 (the app stays the top app and top
window, #231) and E6 (Do Not Disturb routes, Sleep mode (Bedtime), Theater, vibration strength, #233). A person's run is never a
test-case id: the manual cases TC-W-137 (E6) and TC-W-149 (E1, E3, E4) in [`../test-plans/watch.md`](../test-plans/watch.md)
are closed by what you report here. The passive background probe is dropped and has no step.

The probe records only counts and times, never heart-rate values, and exists in debug builds only. Each step is one
command block (paste it in PowerShell). Copy the lines that start with `PROBE` into your answer after each step, or the
whole result as one message.

## Where the watch settings are (verified against Samsung's support pages; names vary by firmware)

Sources: samsung.com/us/support/answer/ANS10002951, ANS10002905, ANS10003398, ANS10002901. The Quick panel opens by swiping down from
the top of the watch face. Anything the scripts ask you to do on the watch that is not listed here is marked "path not verified".

| What | Where |
|---|---|
| Sleep mode (called Bedtime on older firmware) | Quick panel, tap Modes, then Sleep; or Settings, Modes and Routines, Sleep, Turn on |
| Power saving | Quick panel Power saving icon, or Settings, Battery, Power saving. **It turns Wi-Fi off**, Always On Display off and the wake-up gesture off, so wireless adb stops while it is on |
| Theater mode | Quick panel clapperboard icon, then pick a duration. It turns off alerts, Always On Display and wake gestures by itself |
| Vibration | Settings, Sounds and vibration (strength Light/Strong, length Short/Long) |
| Always On Display | Settings, Display, Always On Display |
| Screen timeout, Keep screen on, Touch to wake, Wake up gesture, the notification list | path not verified |

## 0. Once: install the debug build and connect the watch

1. Take the watch off the charger for the runs (a charger hides the battery drop). Keep the laptop plugged in and awake
   ([`../dev-setup-windows.md`](../dev-setup-windows.md) section 8, steps 1 and 3 to 4: Developer options, ADB debugging and
   Wireless debugging on, Wi-Fi **On**, note the connect `ip:port`; the watch is then listed twice in `adb devices`, use the
   `ip:port` one).
2. In PowerShell, in the repository folder on current `main` (it contains the merged live probe of PR #210):

```powershell
git checkout main; git pull
adb connect 192.168.1.57:41203
$env:ANDROID_SERIAL = "192.168.1.57:41203"; $P = "com.shlomoa.myadhdpal/.wear.debug.probe.ProbeReceiver"
.\gradlew.bat :wear:installDebug
```

(Use the address the watch shows; the connect port is not the pairing port.)

## 1. Permissions for the live route (about 2 minutes)

The live route needs only the foreground heart-rate permission and the notification permission. The background permission
must **not** be granted: the experiments test that it is not needed. Revoke it first in case an earlier probe granted it
(an error saying it was not granted is fine):

```powershell
foreach ($n in "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND", "android.permission.BODY_SENSORS_BACKGROUND") { adb shell pm revoke com.shlomoa.myadhdpal $n }
foreach ($n in "android.permission.health.READ_HEART_RATE", "android.permission.POST_NOTIFICATIONS") { adb shell pm grant com.shlomoa.myadhdpal $n }
adb shell am broadcast -n $P --es cmd measure-clear
adb shell am broadcast -n $P --es cmd measure-capabilities; Start-Sleep 2; adb logcat -d -s PROBE:I
```

Expect `PROBE measure capabilities heartRateMeasure=true`. Report that line and any `pm grant` error text. On Wear OS 5 and
earlier use `android.permission.BODY_SENSORS` instead of `READ_HEART_RATE`.

## 2. E1: the live route worn, per screen state (30 minutes, issue #228)

**Run the script (recommended).** It does steps 1 and 2 below unattended: enlarges the log buffer, sets the screen timeout
and restores it, revokes the background permissions, starts the service from the visible app, waits for `AVAILABLE`,
runs the three phases (A screen on, B display dim, C screen off, 10 minutes each) with a report after each, reconnects
adb before every report, reads the battery before and after, checks the notification, and writes one result file. Wear the
watch, **off the charger**, keep the laptop awake, then:

```powershell
.\scripts\probe\run-e1.ps1 -Watch 192.168.1.16:41613
```

(use the address the watch shows). It asks twice about the Always On Display (Settings, Display, Always On Display): **ON** before the run, **OFF** before phase C
(turn it off in the watch settings, then press Enter). Do not touch the watch during a phase. At the end the script publishes the result
file itself as the branch `e1-results/<time>` (it prints `PUSHED e1-results/<time>`; `-SkipPush` keeps it local, and your
working files are not touched): tell the coordinator "done". The file is also kept in `probe-results` in your home folder. The
script is untested on the watch (tested against a simulated adb only); if it fails or you cannot use it, use the manual steps
below, or send the file.

**Manual steps (fallback).**

Wear the watch normally, off the charger, and write down the battery percentage shown on the watch (**before**). Set the
screen timeout in the watch settings to its longest value so that phase A can run untouched. Then start the service the
way the app will, from a visible activity:

```powershell
adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeMeasureActivity; Start-Sleep 5; adb logcat -d -s PROBE:I
```

Expect `PROBE measure start registered`. If it says `permission missing`, run step 1 again. A "Probe: measuring heart rate"
notification appears on the watch: swipe to the notification list now and say whether you see it (it should stay there for
the whole run). Do not use the watch or start any other app during the run, and do not touch the screen in phases A and C.

| Phase | 10 minutes | What you do |
|---|---|---|
| A | Screen on, untouched | Do not touch the watch; the screen stays on as long as it does. If it dims or goes off, note the minute |
| B | Dim / always-on display | Leave the arm still so the screen goes to the always-on display (dim). If it will not, note what it did |
| C | Screen off | Lay the arm down still so the screen goes off |

After each phase run the report (it is cumulative; run it once more at the end):

```powershell
adb shell am broadcast -n $P --es cmd measure-report; Start-Sleep 2; adb logcat -d -s PROBE:I
```

The report prints `PROBE measure screen=all readings=... perMin=... gapSec median=... p95=... max=...`, one such line per
screen state that occurred (`on`, `doze` is the always-on display, `off`), one `availability` line per sensor change with
`atSec` (seconds since start; compare with the longest gap to find the cause of the 60 s gap seen earlier) and
`PROBE measure battery start=... now=... dropPerHour=...`. The lines after the battery line tell a lost reading from a late one: `PROBE measure callbacks=...`,
`PROBE measure samples screen=... sampleGapSec max=... delaySec median=... p95=... max=...` (the gap between the times of the samples and how late each
reading arrived), and `arrivalsPerMin` / `samplesPerMin` (one number per minute of the run, so the position of every gap is visible). The service also writes a `PROBE measure live` line every minute as it happens
(arrivals, seconds since the previous line, seconds since the last arrival, screen state); the script copies them into the result file as `live:` lines. Optional second
run to compare: `.\scripts\probe\run-e1.ps1 -Watch ... -GrantBackgroundPermission` grants the background permission instead of revoking it (the result file says so). When the three phases are done, write down the battery
percentage on the watch (**after**), leave the service running for step 3 or stop it with
`adb shell am broadcast -n $P --es cmd measure-stop`.

Report: every `PROBE measure` line after phase C, the report after A and after B, the battery before and after, whether the
notification stayed visible, the minute the screen dimmed or went off in phase A, and anything else you did on the watch.

## 3. E3: without the background permission, 30 minutes, Sleep mode and Power saving (issue #230)

**Run the script (recommended).** Wear the watch, **off the charger**, keep the laptop awake, then:

```powershell
.\scripts\probe\run-e3.ps1 -Watch 192.168.1.16:41613
```

It revokes the background permissions, starts the service from the visible app, logs the permission state, takes reports
for 30 minutes worn (use the watch normally; the script does not touch the screen), then takes a report, asks you to turn **Sleep mode ON**
(10 minutes, a report in the mode, or after it if the watch cannot be reached) and **OFF**, then takes a report and asks you to turn
**Power saving ON**. Power saving turns Wi-Fi off, so no report is possible during it: turn it OFF, wait for Wi-Fi, and the script
reconnects for up to 2 minutes and takes a report. The file says when no report was possible; that is not an error. At the end the script publishes the result
file as the branch `e3-results/<time>` (it prints `PUSHED e3-results/<time>`; `-SkipPush` keeps it local): tell the coordinator
"done". "service running: False" or "readings grew: False" in it is a result, not an error. The
script is untested on the watch; if it fails, send the file and use the manual steps below.

**Manual steps (fallback).**

Step 1 already left the background permission off, so step 2 is the first half of E3. For the rest:

1. Run `measure-clear`, start the service from the visible app as in step 2, wear the watch for 30 minutes (screen as
   it comes). After 30 minutes run `measure-report` as in step 2. Report the `PROBE measure` lines and whether the
   notification is still there. **The service survived** means readings in the last minutes and no `start failed` line.
2. Sleep mode (Bedtime): with the service running, turn Sleep mode on (Quick panel > Modes > Sleep) for 10 minutes, then off. Run
   `measure-report` after turning it on (minute 0) and after the 10 minutes. Report the readings and gaps.
3. Power saving: it turns Wi-Fi off, so adb stops while it is on: turn it on for 10 minutes, off again, wait for Wi-Fi, then run the report (Quick panel Power saving icon, or Settings > Battery > Power saving; say what you
   used) for 10 minutes.

Stop with `measure-stop` at the end. State whether any permission prompt appeared on the watch during these runs.

## 3b. Delivery with the screen kept on and with the screen really off (issue #228, about 23 minutes)

The two E1 runs could not control the screen state. This script does: 10 minutes with the screen kept on (the debug front screen), 10 minutes with the screen off. Wear the watch, **off the charger**, do not touch it, then:

```powershell
.\scripts\probe\run-screen.ps1 -Watch 192.168.1.16:41613
```

It switches the Always On Display off by adb when it finds the setting key (and restores it), otherwise it asks you once to turn it off (Settings, Display, Always On Display). At the end it publishes the result as the branch `screen-results/<time>` (it prints `PUSHED screen-results/<time>`; `-SkipPush` keeps it local): tell the coordinator "done". Untested on the watch.

## 4. E4: does the app stay the top app and top window (issue #231)

**Run the script (recommended).** Wear the watch and look at it, then:

```powershell
.\scripts\probe\run-e4.ps1 -Watch 192.168.1.16:41613
```

It shortens the screen timeout (restored at the end), starts the front screen twice for 3 minutes each (run 1 keep-screen-on
off, run 2 on), writes the `PROBE front` lines and the resumed activity of both runs, and asks you the four questions below
(type the answers). At the end it publishes the result file as the branch `e4-results/<time>` (it prints
`PUSHED e4-results/<time>`; `-SkipPush` keeps it local): tell the coordinator "done". Untested on the watch; if it fails, use the
manual steps below.

**Manual steps (fallback).**

The debug build has a screen that stays in front (`ProbeFrontActivity`) with a keep-screen-on switch. It measures nothing;
it writes a `PROBE front` line when it is resumed or paused, gains or loses focus, the switch changes or the screen goes
on, dim (`doze`) or off. `inFront=true` means resumed and has focus. Do this step with the watch worn, 3 minutes per
run. Start it the way the app would start:

```powershell
adb logcat -c; adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeFrontActivity
```

Run 1, switch off: leave the arm still and let the screen time out and go to the always-on display, then off; wake it;
then read the lines:

```powershell
adb logcat -d -s PROBE:I
```

Run 2, switch on: `adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeFrontActivity --ez keep true`
(or tap the switch on the watch), leave the arm still for 3 minutes, read the lines again.

Report the `PROBE front` lines of both runs and answer, in your own words:

1. After the start, is the app shown? After the screen timeout (run 1), what is shown in the always-on display: the app,
   the watch face, or other? After waking it?
2. In run 2, does the screen stay on, and for how long? Does it dim?
3. What is the screen timeout setting, and does any setting keep the screen on while the app runs (say which: for example
   "Keep screen on", "Touch to wake", "Wake up gesture")?
4. With the measuring service of step 2 running, tap its notification: does the app come to the front?

## 5. E6: Do Not Disturb routes, Bedtime, Theater, vibration intensity (issue #233)

**Run the script (recommended).** Wear the watch on the wrist and look at it, then:

```powershell
.\scripts\probe\run-e6.ps1 -Watch 192.168.1.16:41613
```

It vibrates in six states (normal, Do Not Disturb, Sleep mode, Theater, vibration strength lowest and highest) and tries both
Do Not Disturb routes under Do Not Disturb, Sleep mode and Theater. It sets Do Not Disturb itself (and switches it off at the
end); it asks you to turn Sleep mode, Theater mode and the vibration strength on and off on the watch (paths in the table above; Theater mode turns Always On Display off by itself). After each try type what you felt
(none, weak or strong) and, for the routes, whether the screen woke (yes or no). At the end the script publishes the result
file as the branch `e6-results/<time>` (it prints `PUSHED e6-results/<time>`; `-SkipPush` keeps it local): tell the coordinator
"done". Untested on the watch; if it fails, send the file and use the manual steps below.

**Manual steps (fallback).**

### 5a. How strong is the vibration (10 minutes)

The same command in each state below. Run it, then say whether you felt it and how strong (none, weak, strong):

```powershell
adb shell am broadcast -n $P --es cmd vibrate
```

| # | State | Felt / strength |
|---|---|---|
| a | Normal | felt, between weak and strong (reported) |
| b | Do Not Disturb on | nothing (reported) |
| c | Sleep mode (Bedtime) on | |
| d | Theater mode on | |
| e | Normal, watch vibration intensity set to the lowest in the watch settings | |
| f | Normal, watch vibration intensity set to the highest | |

Also report the `PROBE vibrate amplitudeControl=...` line (`adb logcat -d -s PROBE:I`). Put every setting back afterwards.

### 5b. Can anything get through Do Not Disturb? (10 minutes)

Two routes to try with Do Not Disturb on (`adb shell cmd notification set_dnd priority`; turn it off afterwards with
`adb shell cmd notification set_dnd off`). The notification permission was granted in step 1.

Route 1, an alarm notification that asks to bypass Do Not Disturb with a full-screen alert. Run it, say whether the watch
vibrated and the screen woke with a big "Probe alert", and copy the `PROBE notify-alarm posted ...` line:

```powershell
adb shell am broadcast -n $P --es cmd notify-alarm; Start-Sleep 5; adb logcat -d -s PROBE:I
```

Then on the watch open Settings and look for the Do Not Disturb exceptions or "Override Do Not Disturb for" and allow the app
"ADHD Pal" (say if no such setting exists), and run the same command again.

Route 2, an alarm clock that fires after 15 seconds (keep the watch still and wait):

```powershell
adb shell am broadcast -n $P --es cmd alarm-clock; Start-Sleep 25; adb logcat -d -s PROBE:I
```

Report for each: felt or not, strength, whether the screen woke, and the `PROBE` lines (`alarm-clock fired`, `alert screen shown`).
Repeat both routes with Sleep mode on and with Theater mode on, and say for each which routes got through.

## 6. Stop and clean up

```powershell
adb shell am broadcast -n $P --es cmd measure-stop; adb shell cmd notification set_dnd off
```

Turn Sleep mode, Theater mode and Power saving off and put the vibration intensity and screen timeout back. The probe stays
installed (debug build) and does nothing until started again.

## What happens with the answers

- E1 numbers per screen state go into [`../plans/biometrics.md`](../plans/biometrics.md) section 12.3 and the body of issue #228;
  they are the measured latency (D6). Whether the route meets "continuously" and "immediately" per screen state is then
  written in E2 (#229) from the measured numbers only.
- E3 and E4: whether the background-permission prompt is needed, and how the app is kept in front, go into section 12.3 and
  feed the foreground service (C4) and the permission flow (C10).
- E6: written into section 4 of the decision document as verified.
