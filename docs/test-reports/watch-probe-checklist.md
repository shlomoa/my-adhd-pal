# Watch probe checklist (step 6.4, cases TC-W-136, TC-W-137 and TC-W-149)

For the user, on the real Galaxy Watch6 Classic. **Not yet run.** The probe answers the open questions of
[`../plans/biometrics.md`](../plans/biometrics.md) section 6 before anything is built on them: does the watch deliver
heart-rate readings to a background app, how often, with the screen on and off and off the wrist, and how strong can a
vibration be under Do Not Disturb, Bedtime and Theater mode. Each step is one command (paste it in PowerShell) and takes
5 to 10 minutes. The probe records only counts and times, never heart-rate values, and exists in debug builds only.

Copy the lines that start with `PROBE` into your answer after each step (or the whole result as one message).

## 0. Once: connect the watch and set two variables

Connect the watch for wireless debugging as in [`../dev-setup-windows.md`](../dev-setup-windows.md), then in PowerShell
from the repository folder (use the address the watch shows):

```powershell
$env:ANDROID_SERIAL = "192.168.1.57:41203"; $P = "com.shlomoa.myadhdpal/.wear.debug.probe.ProbeReceiver"
```

## 1. Install, allow, ask what the watch supports (about 5 minutes)

```powershell
.\gradlew.bat :wear:installDebug; foreach ($n in "android.permission.health.READ_HEART_RATE", "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND", "android.permission.BODY_SENSORS", "android.permission.BODY_SENSORS_BACKGROUND") { Write-Host "grant $n"; adb shell pm grant com.shlomoa.myadhdpal $n }; adb shell am broadcast -n $P --es cmd capabilities; adb shell am broadcast -n $P --es cmd permissions; Start-Sleep 3; adb logcat -d -s PROBE:I
```

Wear OS 6 needs the two `health` permissions; Wear OS 5 and earlier need the two `BODY_SENSORS` ones. A `pm grant` error for the
pair your watch does not use is expected ("has not requested permission" or similar). Report: the `PROBE capabilities
heartRatePassive=...` line, every `PROBE permission granted:` / `PROBE permission missing:` line, and the full text of any
`pm grant` error for a `health` permission.

If a `PROBE permission missing:` line remains, show the permission prompt on the watch and tap Allow on it (the foreground
permission first, then the background one), then run the `permissions` command again:

```powershell
adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbePermissionActivity; Start-Sleep 30; adb shell am broadcast -n $P --es cmd permissions; Start-Sleep 3; adb logcat -d -s PROBE:I
```

`start` (step 2) refuses to run while a permission is missing and prints which one.

## 2. Worn on the wrist, normal use (30 minutes)

Wear the watch normally and leave it alone, letting the screen time out.

```powershell
adb shell am broadcast -n $P --es cmd clear; adb shell am broadcast -n $P --es cmd start; Start-Sleep 1800; adb shell am broadcast -n $P --es cmd report; Start-Sleep 3; adb logcat -d -s PROBE:I
```

The first run (10 minutes) delivered one delivery only, so this step now runs 30 minutes to see several deliveries and their gaps. Report: every `PROBE report screen=...` line (all, then on, doze, off as they occur; doze is the always-on display) and any `PROBE start failed` line. `newestAgeSec` is how old the newest sample was when the delivery arrived. `gapSec` is the time between
deliveries in seconds. No `delivery` lines at all means the watch delivered nothing to the app.

## 3. Off the wrist, on a table (10 minutes)

Take the watch off, put it on a table, screen off.

```powershell
adb shell am broadcast -n $P --es cmd clear; Start-Sleep 600; adb shell am broadcast -n $P --es cmd report; Start-Sleep 3; adb logcat -d -s PROBE:I
```

Report: the `PROBE report` lines. Few or no deliveries off the wrist is expected; it shows what the watch does then.

## 4. How strong is the vibration (10 minutes)

The same command in each state below. Run it, then say whether you felt it and how strong (none, weak, strong):

```powershell
adb shell am broadcast -n $P --es cmd vibrate
```

| # | State | Felt / strength |
|---|---|---|
| a | Normal | felt, between weak and strong (reported) |
| b | Do Not Disturb on | nothing (reported) |
| c | Bedtime mode on | |
| d | Theater mode on | |
| e | Normal, watch vibration intensity set to the lowest in the watch settings | |
| f | Normal, watch vibration intensity set to the highest | |

Also report the `PROBE vibrate amplitudeControl=...` line (`adb logcat -d -s PROBE:I`). Put every setting back afterwards.

## 4b. Can anything get through Do Not Disturb? (10 minutes)

Two routes to try with Do Not Disturb on (`adb shell cmd notification set_dnd priority`; remember to turn it off afterwards with
`adb shell cmd notification set_dnd off`). First allow notifications once:

```powershell
adb shell pm grant com.shlomoa.myadhdpal android.permission.POST_NOTIFICATIONS
```

Route 1, an alarm notification that asks to bypass Do Not Disturb with a full-screen alert. Run it, say whether the watch vibrated and the
screen woke with a big "Probe alert", and copy the `PROBE notify-alarm posted ...` line:

```powershell
adb shell am broadcast -n $P --es cmd notify-alarm; Start-Sleep 5; adb logcat -d -s PROBE:I
```

Then on the watch open Settings and look for the Do Not Disturb exceptions or "Override Do Not Disturb for" and allow the app "ADHD Pal"
(say if no such setting exists), and run the same command again.

Route 2, an alarm clock that fires after 15 seconds (keep the watch still and wait):

```powershell
adb shell am broadcast -n $P --es cmd alarm-clock; Start-Sleep 25; adb logcat -d -s PROBE:I
```

Report for each: felt or not, strength, whether the screen woke, and the `PROBE` lines (`alarm-clock fired`, `alert screen shown`).

## 5. Stop the probe

```powershell
adb shell am broadcast -n $P --es cmd stop; Start-Sleep 2; adb logcat -d -s PROBE:I
```

Report: `PROBE stop cleared`. The probe stays installed (debug build) and does nothing until started again.

## 6. The live heart-rate route, worn for 30 minutes (case TC-W-149)

For [`../plans/biometrics.md`](../plans/biometrics.md) section 11: the app measures only while it runs. Do step 5 first, so the old probe does not
confuse the numbers. Wear the watch, with the screen on, on the wrist.

```powershell
adb shell pm grant com.shlomoa.myadhdpal android.permission.health.READ_HEART_RATE
adb shell pm grant com.shlomoa.myadhdpal android.permission.POST_NOTIFICATIONS
adb shell am broadcast -n $P --es cmd measure-clear
adb shell am broadcast -n $P --es cmd measure-capabilities; Start-Sleep 2; adb logcat -d -s PROBE:I
adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.probe.ProbeMeasureActivity; Start-Sleep 5; adb logcat -d -s PROBE:I
```

Do not grant `READ_HEALTH_DATA_IN_BACKGROUND` (it must not be needed). Expect `PROBE measure capabilities heartRateMeasure=true` and `PROBE measure start registered`.
If it says `permission missing`, run the permission step of step 1 and start again. A "Probe: measuring heart rate" notification appears on the watch.

Then, with the watch worn the whole time:

1. 10 minutes: keep the screen lit (touch it now and then).
2. 10 minutes: leave it alone so the screen dims and goes to the always-on display.
3. 10 minutes: hold the wrist down (or lay your arm still) so the screen goes off.

```powershell
adb shell am broadcast -n $P --es cmd measure-report; Start-Sleep 2; adb logcat -d -s PROBE:I
adb shell am broadcast -n $P --es cmd measure-stop
```

Report the `PROBE measure` lines (one for all readings, one per screen state, any `availability` lines, and the `battery` line) and say whether the
notification was still there at the end, and whether you started anything else on the watch during the 30 minutes. The report shows about one reading
per second if the live route works; long gaps in the `doze` or `off` lines would mean readings stop, or arrive late, with the screen dim or off.

## What happens with the answers

- Capability or deliveries missing: the user decides whether biometric alerts are still worth building
  ([`../plans/biometrics.md`](../plans/biometrics.md), section 6) and the Samsung health route is researched.
- Deliveries present: the gaps set what "best effort" means in the documents, and 6.4 (the real listener) is built.
- Vibration results: written into section 4 of the decision document as verified.
