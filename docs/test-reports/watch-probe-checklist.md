# Watch probe checklist (step 6.4, cases TC-W-136 and TC-W-137)

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

## 2. Worn on the wrist, normal use (10 minutes)

Wear the watch normally and leave it alone, letting the screen time out.

```powershell
adb shell am broadcast -n $P --es cmd clear; adb shell am broadcast -n $P --es cmd start; Start-Sleep 600; adb shell am broadcast -n $P --es cmd report; Start-Sleep 3; adb logcat -d -s PROBE:I
```

Report: the three `PROBE report screen=...` lines (all, on, off) and any `PROBE start failed` line. `gapSec` is the time between
deliveries in seconds. No `batch` lines at all means the watch delivered nothing to the app.

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
| a | Normal | |
| b | Do Not Disturb on | |
| c | Bedtime mode on | |
| d | Theater mode on | |
| e | Normal, watch vibration intensity set to the lowest in the watch settings | |
| f | Normal, watch vibration intensity set to the highest | |

Also report the `PROBE vibrate amplitudeControl=...` line (`adb logcat -d -s PROBE:I`). Put every setting back afterwards.

## 5. Stop the probe

```powershell
adb shell am broadcast -n $P --es cmd stop; Start-Sleep 2; adb logcat -d -s PROBE:I
```

Report: `PROBE stop cleared`. The probe stays installed (debug build) and does nothing until started again.

## What happens with the answers

- Capability or deliveries missing: the user decides whether biometric alerts are still worth building
  ([`../plans/biometrics.md`](../plans/biometrics.md), section 6) and the Samsung health route is researched.
- Deliveries present: the gaps set what "best effort" means in the documents, and 6.4 (the real listener) is built.
- Vibration results: written into section 4 of the decision document as verified.
