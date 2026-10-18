<#
.SYNOPSIS
  Heart-rate delivery in CONTROLLED screen states (issue 228): 10 minutes with the screen kept on, then 10 minutes with the
  screen really off. One command, one result file published as a branch.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH and the watch connected for wireless debugging
  (docs/dev-setup-windows.md section 8). Install the debug build first (.\gradlew.bat :wear:installDebug). Wear the watch, OFF
  the charger (refused otherwise; -AllowCharging overrides) and do not touch it. The E1 runs could not control the screen
  state; this script does.

  What it does, in this order (each step is written with its time to the result file):
    - tries to switch the Always On Display off by adb: it reads the candidate setting keys (secure doze_always_on, system
      aod_mode, secure aod_mode, system always_on_display), takes the first that holds 0 or 1, writes 0, reads it back, and
      restores the old value at the end. If no key is found it asks you once to turn it off (Settings > Display > Always On
      Display) and goes on; -Unattended skips that question;
    - revokes the background permissions (or grants them with -GrantBackgroundPermission), grants READ_HEART_RATE and
      POST_NOTIFICATIONS, starts the measuring service and waits for AVAILABLE;
    - phase K (keep-screen-on): wakes the screen, starts the debug front screen with keep-screen-on, waits -PhaseSec (default
      600), logs the screen state (dumpsys power) and takes a report;
    - phase O (off): presses Home (so the front screen no longer holds the screen), presses the sleep key and repeats it every
      -OffRetrySec until the screen state is OFF (up to -OffTrySec, default 120; if it never is, the file says so and the run
      goes on), waits -PhaseSec, logs the screen state and takes a report;
    - restores the Always On Display setting, stops the service, publishes the result file as the branch screen-results/<stamp>
      holding only that file and prints PUSHED screen-results/<stamp> (-SkipPush keeps it local).
  The per-screen-state lines of the final report (screen=on, screen=off) are the figures of phase K and phase O; the live
  per-minute lines (copied into the file as "live:") show the arrivals of every minute. The file holds no heart-rate values and no ip
  address.

  Untested on the watch: the script is tested only against a simulated adb (scripts/probe/test-run-screen.sh). Not proven: that any
  of the candidate keys is the Always On Display switch of the Galaxy Watch6 Classic (SM-R965F) or that writing it works, that the
  sleep key turns the screen OFF, the dumpsys power output used for the screen state, and that keep-screen-on holds for 10 minutes.
  Exit code: 0 done (also when the screen did not reach OFF), 1 the service did not start, 3 setup problem (no adb, watch not
  reachable, watch on its charger).

.EXAMPLE
  .\scripts\probe\run-screen.ps1 -Watch 192.168.1.16:41613
#>
[CmdletBinding()]
param(
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    [double]$PhaseSec = 600,
    [double]$OffTrySec = 120,
    [double]$OffRetrySec = 5,
    [double]$AvailableTimeoutSec = 120,
    [double]$ReportWaitSec = 3,
    [switch]$Unattended,
    [switch]$AllowCharging,
    [switch]$GrantBackgroundPermission,
    [switch]$SkipPush
)

. (Join-Path $PSScriptRoot 'probe-common.ps1')

$aodCandidates = @(@('secure', 'doze_always_on'), @('system', 'aod_mode'), @('secure', 'aod_mode'), @('system', 'always_on_display'))
$script:aodChanged = $null

function Get-Setting([string]$ns, [string]$key) { ((Invoke-Adb @('shell', 'settings', 'get', $ns, $key)) -join '').Trim() }

# Switches the Always On Display off by adb when a candidate key exists; remembers the old value. Returns true when it is off.
function Disable-Aod {
    foreach ($c in $aodCandidates) {
        $old = Get-Setting $c[0] $c[1]
        if ($old -ne '0' -and $old -ne '1') { continue }
        if ($old -eq '0') { Log ("aod: $($c[0]):$($c[1]) was 0 (already off)"); return $true }
        Invoke-Adb @('shell', 'settings', 'put', $c[0], $c[1], '0') | Out-Null
        $now = Get-Setting $c[0] $c[1]
        Log ("aod: $($c[0]):$($c[1]) was $old, set to 0, read back $now")
        if ($now -eq '0') { $script:aodChanged = @($c[0], $c[1], $old); return $true }
        Invoke-Adb @('shell', 'settings', 'put', $c[0], $c[1], $old) | Out-Null
    }
    Log 'aod: no setting key found (or it did not take); the Always On Display stays as it is'
    return $false
}

function Restore-Aod {
    if ($script:aodChanged) {
        $c = $script:aodChanged
        Invoke-Adb @('shell', 'settings', 'put', $c[0], $c[1], $c[2]) | Out-Null
        Log ("aod: $($c[0]):$($c[1]) restored to $($c[2]), read back " + (Get-Setting $c[0] $c[1]))
    }
}

# The screen state from dumpsys power (Display Power: state=ON|OFF|DOZE), or unknown.
function Get-ScreenState {
    $text = (Invoke-Adb @('shell', 'dumpsys', 'power')) -join "`n"
    $m = $text | Select-String -Pattern 'Display Power: state=(\w+)'
    if ($m) { return $m.Matches[0].Groups[1].Value }
    $m = $text | Select-String -Pattern 'mScreenState=(\w+)'
    if ($m) { return $m.Matches[0].Groups[1].Value }
    return 'unknown'
}

Initialize-Probe 'screen'
Test-Preflight

$failed = $false
try {
    Invoke-Adb @('logcat', '-G', '4M') | Out-Null
    if (-not (Disable-Aod)) {
        Ask 'Turn the Always On Display OFF now (the screen must be able to go fully off): Settings > Display > Always On Display.' -Step 'aod-off'
    }
    Start-ProbeService
    Log-Permissions
    Wait-ServiceAvailable

    Log "== phase K (screen kept on) for $PhaseSec s"
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-n', "$pkg/.wear.debug.probe.ProbeFrontActivity", '--ez', 'keep', 'true') | Out-Null
    Wait-Phase $PhaseSec
    Log ("screen state before the report keep-on: " + (Get-ScreenState))
    Report 'keep-on' | Out-Null

    Log "== phase O (screen off) for $PhaseSec s"
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    $deadline = (Get-Date).AddSeconds($OffTrySec)
    $state = ''
    do {
        Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP') | Out-Null
        Start-Sleep -Seconds $OffRetrySec
        $state = Get-ScreenState
        Log "sleep key sent, screen state $state"
    } while ($state -ne 'OFF' -and (Get-Date) -lt $deadline)
    if ($state -ne 'OFF') { Log "screen did not reach OFF within $OffTrySec s (last state $state); the phase goes on anyway" }
    Wait-Phase $PhaseSec
    Log ("screen state before the report screen-off: " + (Get-ScreenState))
    Report 'screen-off' | Out-Null
    $script:RunDone = $true
} catch {
    $failed = $true
    Log "FAILED: $_"
} finally {
    try {
        Connect-Watch
        Restore-Aod
        Stop-ProbeService
        Log "result file: $(Split-Path -Leaf $script:LogFile)"
    } finally {
        Finish-Run 'screen' $failed
    }
}
if ($failed) { exit 1 }
exit 0
