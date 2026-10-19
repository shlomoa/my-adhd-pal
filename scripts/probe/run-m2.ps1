<#
.SYNOPSIS
  Milestone M2 in one command and no prompts: which delivery variant keeps heart-rate readings arriving with the screen OFF
  (variants: exercise, exercise5s; wakelock as an optional control with -Variants), about 6 minutes each, one result file.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). The only physical
  action: WEAR the watch (a reading needs skin contact) and leave it alone for about 20 minutes. The watch is off the charger.

  The plain live route is bursty with the screen dim or off (E1, E2: callbacks of hundreds of readings, up to about 10 minutes late).
  The variants are tried, one after the other, with the same measurement as E1 (when each reading arrived, the display state, its
  sample time; never the value):
    exercise   the exercise client (the documented route for heart rate while the screen is off; with the screen off it may report
               only every 150 seconds or so to save power);
    exercise5s the exercise client with the 5 second heart-rate reporting (a documented override; battery cost, use only if needed);
    wakelock   control, not run by default: the live route plus a partial wake lock (the Wear power guide says to avoid wake locks).
  The live route itself is documented as not meant for background capture. A passive threshold goal is not a route: the documented
  passive goals are daily and the capabilities line below says whether heart rate is among them. Only ONE exercise can run on a
  watch at a time: end any running workout first.

  For each variant, in order (each step is written with its time to the result file):
    - heart-rate, notification and activity-recognition permission by adb; the background permission is not touched;
    - the Always On Display is switched off by adb when a setting key exists (restored at the end);
    - the probe starts in that mode from the visible app (ProbeMeasureActivity --es mode ...); a variant that cannot start is
      logged as NOT AVAILABLE and the run goes on;
    - the screen is turned off with the sleep key (repeated until dumpsys power says OFF, at most -OffTrySec), then -PhaseSec
      (default 360) of waiting; the screen state is read again before the report;
    - measure-report; the screen=off lines of the report are copied as "m2 variant <name>: ..." (readings, sample gaps, the delay of a
      reading = when it arrived minus when it was measured). The script draws no conclusion from them.
  Time limits, pre-flight, heartbeat and publishing as for the other probe runs; the result is published as m2-results/<stamp>.
  The file holds no ip address, no local path and no heart-rate value. Exit code: 0 done, 1 a step failed, 3 setup problem.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-m2.sh). Not proven: that the exercise
  client accepts a session without GPS, that the 5 second mode exists on this watch, that the sleep key turns the screen off, and the
  dumpsys output used for the screen state.

.EXAMPLE
  .\scripts\probe\run-m2.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [string[]]$Variants = @('exercise', 'exercise5s'),
    [double]$PhaseSec = 360,
    [double]$OffTrySec = 120,
    [double]$OffRetrySec = 5,
    [double]$AvailableTimeoutSec = 120,
    [double]$ReportWaitSec = 3,
    [double]$ReconnectSec = 120,
    [double]$ReconnectRetrySec = 5,
    [switch]$AllowCharging,
    [switch]$SkipPush
)

$Unattended = $true
. (Join-Path $PSScriptRoot 'probe-common.ps1')

# Starts the probe in the mode of $variant from the visible app; true when it registered (AVAILABLE), false when it could not start.
function Start-Variant([string]$variant) {
    Probe 'measure-stop'
    Probe 'measure-clear'
    Invoke-Adb @('logcat', '-c') | Out-Null
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-n', "$pkg/.wear.debug.probe.ProbeMeasureActivity", '--es', 'mode', $variant) | Out-Null
    $deadline = (Get-Date).AddSeconds($AvailableTimeoutSec)
    do {
        $lines = (Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I')) -join "`n"
        if ($lines -match 'exercise start failed|measure not started') { return $false }
        if ($lines -match 'measure availability .*\bAVAILABLE\b') { return $true }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Turn-ScreenOff {
    $deadline = (Get-Date).AddSeconds($OffTrySec)
    $state = ''
    do {
        Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP') | Out-Null
        Start-Sleep -Seconds $OffRetrySec
        $state = Get-ScreenState
        Log "sleep key sent, screen state $state"
    } while ($state -ne 'OFF' -and (Get-Date) -lt $deadline)
    return ($state -eq 'OFF')
}

$Variants = @($Variants | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
Initialize-Probe 'm2'
Test-Preflight

$failed = $false
try {
    foreach ($p in 'android.permission.health.READ_HEART_RATE', 'android.permission.POST_NOTIFICATIONS', 'android.permission.ACTIVITY_RECOGNITION') {
        Invoke-Adb @('shell', 'pm', 'grant', $pkg, $p) | Out-Null
    }
    Log-Permissions
    Probe 'm2-capabilities'
    Start-Sleep -Seconds $ReportWaitSec
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | Where-Object { $_ -match 'm2 capabilities' } | ForEach-Object { Log $_ }
    [void](Disable-Aod)
    foreach ($variant in $Variants) {
        Log "== variant $variant"
        if (-not (Start-Variant $variant)) {
            Log "m2 variant ${variant}: NOT AVAILABLE (the probe did not start in this mode)"
            Set-Step $variant 'NOT AVAILABLE'
            continue
        }
        $off = Turn-ScreenOff
        if (-not $off) { Log "screen not off for variant ${variant}: the numbers below are not screen-off numbers" }
        Wait-Phase $PhaseSec
        Log ("screen state before the report: " + (Get-ScreenState))
        Connect-Watch
        Save-LiveLines
        Invoke-Adb @('logcat', '-c') | Out-Null
        Probe 'measure-report'
        Start-Sleep -Seconds $ReportWaitSec
        $report = Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I')
        $report | ForEach-Object { Log $_ }
        $offLines = @($report | Where-Object { $_ -match 'PROBE measure (samples )?screen=off' })
        if ($offLines.Count -eq 0) { Log "m2 variant ${variant}: no screen=off reading in the report" }
        foreach ($l in $offLines) { Log ("m2 variant ${variant}: " + ($l -replace '^.*PROBE measure ', '')) }
        Set-Step $variant $(if ($off) { 'MEASURED (screen off)' } else { 'MEASURED (screen not off)' })
        Probe 'measure-stop'
        Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    }
    $script:RunDone = $true
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    try {
        Restore-Aod
        Stop-ProbeService
        Log "result file: $(Split-Path -Leaf $script:LogFile)"
    } finally {
        Finish-Run 'm2' $failed
    }
}
if ($failed) { exit 1 }
exit 0
