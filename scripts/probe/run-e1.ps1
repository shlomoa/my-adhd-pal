<#
.SYNOPSIS
  Experiment E1 (issue 228) in one command: the live heart-rate route on the worn Galaxy Watch6 Classic, 10 minutes with the
  screen on, 10 minutes with the display dim (Always-on display), 10 minutes with the screen off, one result file.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). Wear the watch,
  OFF the charger (the script refuses a watch that is charging, which would hide the battery drop; -AllowCharging overrides).

  What it does, in this order (each step is written with its time to the result file):
    - enlarges the log buffer (adb logcat -G 4M), saves the screen timeout and sets it to -ScreenOffTimeoutMs;
    - revokes the two background permissions (an error that they were not granted is fine), grants READ_HEART_RATE and
      POST_NOTIFICATIONS;
    - measure-stop, measure-clear, measure-capabilities, starts ProbeMeasureActivity and waits for the availability
      line AVAILABLE (up to -AvailableTimeoutSec);
    - phase A: wakes the screen, waits -PhaseSec (default 600), takes a report;
    - phase B: asks you to check that the Always-on display is ON, puts the screen to sleep (the display goes dim),
      waits, takes a report;
    - phase C: asks you to turn the Always-on display OFF in the watch settings, puts the screen to sleep, waits, takes a report;
    - before every report: adb connect (wireless adb may drop while the screen is off) and adb logcat -c, so the report holds
      only new lines; each report also checks that the "Probe: measuring heart rate" notification is listed;
    - the battery level before and after (dumpsys battery); measure-stop; restores the screen timeout, also after a failure.
  -Unattended skips the two Always-on display questions (use it when you already set the display as needed).

  The reports are the lines the checklist asks you to send back (PROBE measure screen=... and the battery line). Send the
  whole result file (default folder: probe-results in your home folder). The file holds no heart-rate values and no ip address.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-e1.sh). Not proven: that
  KEYCODE_SLEEP and KEYCODE_WAKEUP give the display states on and doze on this watch, that the Always-on display behaves as
  described, that dumpsys notification lists the notification title, the dumpsys battery output, and that the screen
  stays on for the length of phase A with the timeout set. Exit code: 0 done, 1 a step failed (the result file says which),
  3 setup problem (no adb, watch not reachable, watch on its charger).

.EXAMPLE
  .\scripts\probe\run-e1.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    # ip:port from the watch's Wireless debugging screen; falls back to $env:ANDROID_SERIAL.
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [double]$PhaseSec = 600,
    [double]$AvailableTimeoutSec = 120,
    [double]$ReportWaitSec = 3,
    # A little longer than a phase, so the screen does not time out before the phase report.
    [int]$ScreenOffTimeoutMs = 720000,
    [switch]$Unattended,
    [switch]$AllowCharging
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

function Phase([string]$phase, [string]$what) {
    Log "== phase $phase ($what) for $PhaseSec s"
    Start-Sleep -Seconds $PhaseSec
    Report $phase | Out-Null
}

Initialize-Probe 'e1'
Test-NotCharging

$failed = $false
$savedTimeout = $null
try {
    Invoke-Adb @('logcat', '-G', '4M') | Out-Null
    $savedTimeout = ((Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout')) -join '').Trim()
    Log "screen timeout was $savedTimeout, set to $ScreenOffTimeoutMs"
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', "$ScreenOffTimeoutMs") | Out-Null

    Start-ProbeService
    Ask 'Check that the Always-on display is ON in the watch settings (phase B needs it).'
    Wait-ServiceAvailable

    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Phase 'A' 'screen on'

    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP') | Out-Null
    Phase 'B' 'display dim'

    Ask 'Turn the Always-on display OFF in the watch settings now (phase C needs the screen off).'
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP') | Out-Null
    Phase 'C' 'screen off'
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    Stop-ProbeService
    if ($savedTimeout) { Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', $savedTimeout) | Out-Null }
    Log ("screen timeout restored to $savedTimeout; result file: $script:LogFile")
}
if ($failed) { exit 1 }
exit 0
