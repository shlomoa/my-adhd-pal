<#
.SYNOPSIS
  Experiment E3 (issue 230): the live heart-rate service started from the visible app WITHOUT the background permission, worn
  for 30 minutes, then 10 minutes with Bedtime mode and 10 minutes with battery saver. One result file.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). Wear the watch,
  OFF the charger (refused otherwise; -AllowCharging overrides). Use the watch as you normally would; the script does not
  touch the screen or the screen timeout.

  What it does, in this order (each step is written with its time to the result file):
    - enlarges the log buffer (logcat -G 4M); revokes the two background permissions, grants READ_HEART_RATE and
      POST_NOTIFICATIONS, logs the permission state from dumpsys package (the background one must show granted=false);
    - measure-stop, measure-clear, measure-capabilities, starts ProbeMeasureActivity, waits for AVAILABLE;
    - 30 minutes worn in three blocks of -BlockSec (default 600) with a report after each (block-10, block-20, block-30);
      after the last it logs "service running" (the measure service is listed by dumpsys) and "readings grew"
      (the last report has more readings than the one before); a service that is gone is a RESULT, not a failure;
    - Bedtime: asks you to turn Bedtime mode ON, takes a report, waits -StateSec (default 600), takes a report, logs the same
      two lines, asks you to turn it OFF;
    - battery saver: the same (the watch's power saving mode; say what it is called on the watch);
    - before every report: adb connect and logcat -c; the battery before and after; measure-stop at the end.
  -Unattended skips the questions (use it only when you toggle the modes by yourself).

  Send back the whole result file (default folder: probe-results in your home folder). It holds no heart-rate values and no
  ip address.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-e3-e6.sh). Not proven: that
  dumpsys activity services lists the service name, the dumpsys package permission lines, how Bedtime mode and battery saver
  behave on the Galaxy Watch6 Classic, and that wireless adb stays up while they are on. Exit code: 0 done, 1 the service
  did not start, 3 setup problem (no adb, watch not reachable, watch on its charger).

.EXAMPLE
  .\scripts\probe\run-e3.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [double]$BlockSec = 600,
    [double]$StateSec = 600,
    [double]$AvailableTimeoutSec = 120,
    [double]$ReportWaitSec = 3,
    [switch]$Unattended,
    [switch]$AllowCharging
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

function Service-Running {
    $listed = (Invoke-Adb @('shell', 'dumpsys', 'activity', 'services', $pkg)) -join "`n"
    return ($listed -match 'ProbeMeasureService')
}

# Two reports around a wait: logs whether the service still runs and whether the readings grew between them.
function Judge([string]$what, [int]$before, [int]$after) {
    Log ("$what service running: " + (Service-Running))
    Log ("$what readings grew: " + ($after -gt $before) + " ($before -> $after)")
}

Initialize-Probe 'e3'
Test-NotCharging

$failed = $false
try {
    Invoke-Adb @('logcat', '-G', '4M') | Out-Null
    Start-ProbeService
    Log-Permissions
    Wait-ServiceAvailable
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null

    Log "== 30 minutes worn, three blocks of $BlockSec s"
    $counts = @()
    foreach ($i in 1, 2, 3) {
        Start-Sleep -Seconds $BlockSec
        $counts += Report ("block-" + ($i * 10))
    }
    Judge 'after 30 minutes:' $counts[1] $counts[2]

    foreach ($mode in @(@('Bedtime', 'Bedtime mode', 'bedtime'), @('battery saver', 'battery saver (power saving) mode', 'battery-saver'))) {
        Ask ("Turn $($mode[1]) ON on the watch now.")
        $a = Report ("$($mode[2])-start")
        Start-Sleep -Seconds $StateSec
        $b = Report ("$($mode[2])-end")
        Judge "$($mode[0]):" $a $b
        Ask ("Turn $($mode[1]) OFF on the watch now.")
    }
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    Stop-ProbeService
    Log "result file: $script:LogFile"
}
if ($failed) { exit 1 }
exit 0
