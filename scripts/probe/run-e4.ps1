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
    - run 1 (keep-screen-on OFF): clears the log, wakes the screen, starts ProbeFrontActivity, waits -RunSec (default 180)
      while you leave the arm still, writes the PROBE front lines (resumed, paused, focus, screen on/doze/off, inFront) and the
      activity that is resumed on the watch; asks you what you saw;
    - run 2 (keep-screen-on ON, --ez keep true): the same; asks what you saw;
    - asks the remaining questions (screen timeout setting, the notification), restores the screen timeout also after a failure;
    - publishes the result file as the branch e4-results/<stamp> holding only that file and prints PUSHED e4-results/<stamp>
      (-SkipPush keeps it local; a failed push prints the retry command).
  Every answer you type is written to the result file as "answer: ...". The file holds no ip address and no heart-rate values.

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
    [double]$RunSec = 180,
    [int]$TimeoutMs = 15000,
    [double]$ReportWaitSec = 3,
    [switch]$SkipPush
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

function Run-Front([string]$title, [string[]]$extra) {
    Log "== $title"
    & adb connect $Watch 2>&1 | Out-Null
    Invoke-Adb @('logcat', '-c') | Out-Null
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    $started = Invoke-Adb (@('shell', 'am', 'start', '-n', "$pkg/.wear.debug.probe.ProbeFrontActivity") + $extra)
    if (($started -join ' ') -match 'Error|does not exist|Exception') { throw "the front screen could not be started: $($started -join ' ')" }
    Start-Sleep -Seconds $RunSec
    & adb connect $Watch 2>&1 | Out-Null
    Start-Sleep -Seconds $ReportWaitSec
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | Where-Object { $_ -match 'PROBE front' } | ForEach-Object { Log $_ }
    Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities') | Where-Object { $_ -match 'mResumedActivity|topResumedActivity' } |
        ForEach-Object { Log ('resumed activity: ' + $_.Trim()) }
}

Initialize-Probe 'e4'

$failed = $false
$savedTimeout = $null
try {
    Invoke-Adb @('logcat', '-G', '4M') | Out-Null
    $savedTimeout = ((Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout')) -join '').Trim()
    Log "screen timeout was $savedTimeout, set to $TimeoutMs"
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', "$TimeoutMs") | Out-Null

    Ask 'Run 1 (keep-screen-on OFF): wear the watch and, after you press Enter, leave the arm still for the whole run and let the screen time out.'
    Run-Front 'run 1 (keep-screen-on off)' @()
    Ask-Answer 'Run 1: after the start, was the app shown? After the screen timeout, what was shown in the always-on display: the app, the watch face or other? After waking it?' | Out-Null

    Ask 'Run 2 (keep-screen-on ON): leave the arm still again after you press Enter.'
    Run-Front 'run 2 (keep-screen-on on)' @('--ez', 'keep', 'true')
    Ask-Answer 'Run 2: did the screen stay on, and for how long? Did it dim?' | Out-Null

    Ask-Answer 'What is the screen timeout setting in the watch settings, and does any setting keep the screen on while the app runs (for example Keep screen on, Touch to wake, Wake up gesture)? Say which.' | Out-Null
    Ask-Answer 'If the measuring service of experiment E1 or E3 is running: after you tap its notification, does the app come to the front? Answer skip if it is not running.' | Out-Null
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    & adb connect $Watch 2>&1 | Out-Null
    if ($savedTimeout) { Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', $savedTimeout) | Out-Null }
    Log "screen timeout restored to $savedTimeout; result file: $(Split-Path -Leaf $script:LogFile)"
}
Publish-Result 'e4'
if ($failed) { exit 1 }
exit 0
