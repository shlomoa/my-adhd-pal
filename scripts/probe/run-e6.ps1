<#
.SYNOPSIS
  Experiment E6 (issue 233): which settings suppress the alert on the watch. Vibration in six states, and the two Do Not
  Disturb routes (an alarm notification, an alarm clock) under Do Not Disturb, Bedtime mode and Theater mode. You answer what
  you felt and saw; one result file.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). Wear the watch and
  keep it on the wrist; you must feel and look at it.

  What it does (every answer you type is written to the result file as "answer: ..."):
    - grants POST_NOTIFICATIONS (notification routes need it);
    - state 1 normal; state 2 Do Not Disturb (set by the script with cmd notification set_dnd priority, switched off at the
      end, also after a failure); state 3 Sleep mode (Bedtime on older firmware) and state 4 Theater mode (you turn them on and off on the watch when
      asked); state 5 and 6 the watch vibration intensity set to the LOWEST and the HIGHEST (you set it);
    - in each state: vibrate, you answer none, weak or strong;
    - under Do Not Disturb, Bedtime and Theater: also the notify-alarm route and the alarm-clock route (it fires after about
      15 seconds, the script waits -AlarmWaitSec, default 25); for each you answer what you felt and whether the screen woke.
  Before the intensity states, put the intensity back to your usual value afterwards.

  Send back the whole result file (default folder: probe-results in your home folder). It holds no ip address.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-e3-e6.sh). Not proven:
  that cmd notification set_dnd works on the Galaxy Watch6 Classic, the Bedtime, Theater and intensity settings, and what
  the routes do. Exit code: 0 done, 3 setup problem.

.EXAMPLE
  .\scripts\probe\run-e6.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [double]$AlarmWaitSec = 25,
    [double]$ReportWaitSec = 3,
    [switch]$SkipPush
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

# Sends one probe command with a clean log, logs the new PROBE lines.
function Run-Probe([string]$command, [double]$waitSec) {
    Connect-Watch
    Invoke-Adb @('logcat', '-c') | Out-Null
    Probe $command
    Start-Sleep -Seconds $waitSec
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | ForEach-Object { Log $_ }
}

function Try-Vibrate([string]$state) {
    Log "== $state : vibrate"
    Run-Probe 'vibrate' $ReportWaitSec
    Ask-Answer 'Did you feel it? Answer none, weak or strong.' | Out-Null
}

function Try-Routes([string]$state) {
    Log "== $state : notify-alarm"
    Run-Probe 'notify-alarm' $ReportWaitSec
    Ask-Answer 'Did the watch vibrate (none, weak or strong) and did the screen wake with a big "Probe alert" (yes or no)?' | Out-Null
    Log "== $state : alarm-clock (fires after about 15 seconds, keep the watch still)"
    Run-Probe 'alarm-clock' $AlarmWaitSec
    Ask-Answer 'Did the watch vibrate (none, weak or strong) and did the screen wake with a big "Probe alert" (yes or no)?' | Out-Null
}

Initialize-Probe 'e6'
Invoke-Adb @('shell', 'pm', 'grant', $pkg, 'android.permission.POST_NOTIFICATIONS') | Out-Null

$failed = $false
try {
    Try-Vibrate 'normal'

    Invoke-Adb @('shell', 'cmd', 'notification', 'set_dnd', 'priority') | Out-Null
    Log 'Do Not Disturb is now ON (priority).'
    Try-Vibrate 'Do Not Disturb'
    Try-Routes 'Do Not Disturb'
    Invoke-Adb @('shell', 'cmd', 'notification', 'set_dnd', 'off') | Out-Null

    foreach ($mode in @(@('Sleep', "Turn Sleep mode ON now. $PathSleep.", 'Turn Sleep mode OFF now (Quick panel > Modes > Sleep).'),
                        @('Theater', "Turn Theater mode ON now. $PathTheater.", 'Turn Theater mode OFF now (Quick panel clapperboard icon).'))) {
        Ask $mode[1]
        Try-Vibrate "$($mode[0]) mode"
        Try-Routes "$($mode[0]) mode"
        Ask $mode[2]
    }

    Ask "Set the watch vibration strength to the LOWEST (Light) now: $PathVibration."
    Try-Vibrate 'vibration intensity lowest'
    Ask "Set the watch vibration strength to the HIGHEST (Strong) now: $PathVibration."
    Try-Vibrate 'vibration intensity highest'
    Ask "Put the watch vibration strength back to your usual value: $PathVibration."
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    Connect-Watch
    Invoke-Adb @('shell', 'cmd', 'notification', 'set_dnd', 'off') | Out-Null
    Log "Do Not Disturb switched off; result file: $(Split-Path -Leaf $script:LogFile)"
}
Publish-Result 'e6'
if ($failed) { exit 1 }
exit 0
