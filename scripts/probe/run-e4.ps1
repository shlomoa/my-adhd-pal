<#
.SYNOPSIS
  Experiment E4 (issue 231): does the app stay the top app and top window? Two runs with the debug front screen
  (ProbeFrontActivity): run 1 with keep-screen-on off, so the screen can time out; run 2 with keep-screen-on on. You answer the
  four questions of the checklist; one result file, published as a branch.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). Wear the watch and
  look at it.

  What it does, in this order (each step is written with its time to the result file):
    - saves the screen timeout and sets it to -TimeoutMs (default 15000, so that it can time out within the run);
    - run 1 (keep-screen-on OFF): clears the log, wakes the screen, starts ProbeFrontActivity, waits -RunSec
      while you leave the arm still (-RunSec, default 60), writes the PROBE front lines (resumed, paused, focus, screen on/doze/off, inFront) and the
      activity that is resumed on the watch; the screen state is sampled by adb (dumpsys power) every PROBE_SAMPLE_SEC seconds and the
      script says how long the screen stayed ON; it asks you nothing;
    - run 2 (keep-screen-on ON, --ez keep true): the same;
    - restores the screen timeout also after a failure;
    - publishes the result file as the branch e4-results/<stamp> holding only that file and prints PUSHED e4-results/<stamp>
      (-SkipPush keeps it local; a failed push prints the retry command).
  Nothing is typed: the answers come from adb. The file holds no ip address and no heart-rate values.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-e4.sh). Not proven: that
  KEYCODE_WAKEUP wakes the screen, that the screen goes to the Always-on display or off after the shortened timeout, that the
  keep-screen-on flag keeps the screen on, and the dumpsys output used for the resumed activity. Exit code: 0 done, 1 a step
  failed (the result file says which), 3 setup problem.

.EXAMPLE
  .\scripts\probe\run-e4.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [double]$RunSec = 60,
    [int]$TimeoutMs = 15000,
    [double]$ReportWaitSec = 3,
    [switch]$SkipPush
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

# One run: starts the front screen, then samples the screen state every PROBE_SAMPLE_SEC (default 10) seconds for -RunSec seconds, and
# logs the PROBE front lines, the window focus and the resumed activity. Nothing is asked: the user only keeps the arm still.
function Run-Front([string]$title, [string[]]$extra, [string]$step) {
    Log "== $title"
    Write-Host 'Keep the arm still and the watch worn until the next line; nothing to type.'
    Connect-Watch
    Invoke-Adb @('logcat', '-c') | Out-Null
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    $started = Invoke-Adb (@('shell', 'am', 'start', '-n', "$pkg/.wear.debug.probe.ProbeFrontActivity") + $extra)
    if (($started -join ' ') -match 'Error|does not exist|Exception') { throw "the front screen could not be started: $($started -join ' ')" }
    $every = if ($env:PROBE_SAMPLE_SEC) { [double]$env:PROBE_SAMPLE_SEC } else { 10 }
    $begin = Get-Date
    $end = $begin.AddSeconds($RunSec)
    $onSec = 0.0
    $stillOn = $true
    do {
        Wait-Phase ([Math]::Min($every, [Math]::Max(0.1, ($end - (Get-Date)).TotalSeconds)))
        $state = Get-ScreenState
        $at = [int][Math]::Round(((Get-Date) - $begin).TotalSeconds)
        Log "screen state sample at ${at} s: $state"
        if ($stillOn -and $state -eq 'ON') { $onSec = ((Get-Date) - $begin).TotalSeconds } else { $stillOn = $false }
    } while ((Get-Date) -lt $end)
    Log ("screen was ON for " + [int][Math]::Round($onSec) + " s of " + [int][Math]::Round($RunSec) + " s (first sample that was not ON ends it)")
    Connect-Watch
    Start-Sleep -Seconds $ReportWaitSec
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | Where-Object { $_ -match 'PROBE front' } | ForEach-Object { Log $_ }
    Log ('window focus: ' + (Get-WindowFocus))
    Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities') | Where-Object { $_ -match 'mResumedActivity|topResumedActivity' } |
        ForEach-Object { Log ('resumed activity: ' + $_.Trim()) }
    Set-Step $step ("OBSERVED by adb (screen ON for " + [int][Math]::Round($onSec) + " s)")
}

Initialize-Probe 'e4'
Test-Preflight

$failed = $false
$savedTimeout = $null
try {
    Invoke-Adb @('logcat', '-G', '4M') | Out-Null
    $savedTimeout = ((Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout')) -join '').Trim()
    Log "screen timeout was $savedTimeout, set to $TimeoutMs"
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', "$TimeoutMs") | Out-Null

    Run-Front 'run 1 (keep-screen-on off)' @() 'run-1'
    Run-Front 'run 2 (keep-screen-on on)' @('--ez', 'keep', 'true') 'run-2'
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    try {
        Connect-Watch
        if ($savedTimeout) { Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', $savedTimeout) | Out-Null }
        Log "screen timeout restored to $savedTimeout; result file: $(Split-Path -Leaf $script:LogFile)"
    } finally {
        Finish-Run 'e4' $failed
    }
}
if ($failed) { exit 1 }
exit 0
