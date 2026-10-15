<#
.SYNOPSIS
  Runs the real-device demo (Galaxy A55 5G over USB, Galaxy Watch6 Classic over Wi-Fi debugging) with ONE command and
  no taps, prints PASS or FAIL for every stage and what you should see on the watch, and publishes the results.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH. It never restarts or kills a device and
  it never changes radios or power settings. It checks that both devices are ready (and says which one is not and why),
  builds and installs the DEBUG apps of :phone and :wear, starts both apps, then drives the whole round trip with adb
  broadcasts (no taps):

    1 Ready        both devices ready (adb state, boot, battery) before and after the install
    2 Install      the debug APKs of :phone and :wear are installed and the apps start
    3 Publish      the phone publishes a demo schedule with one reminder due in -MinutesAhead minutes
    4 Schedule     the schedule reaches the watch (watch log: schedule applied)
    5 Reminder     the reminder fires on the watch within 1 minute of its time (watch log: reminder fired)
    6 Notification the watch shows the reminder notification (dumpsys notification)
    7 Done         the Done action is sent on the watch, as a tap on the notification would (watch log: ack sent)
    8 Ack          the acknowledgement reaches the phone and is stored in its history (phone log: history ack)

  The haptic cannot be read with adb, so it is a "you should feel" line, not a PASS or FAIL. The log lines are written
  by DEBUG builds only (release builds write nothing). The results (results.txt and the per-stage logs, without device
  serials or ip addresses) go to docs/demo/results/<yyyyMMdd-HHmmss>/ and ONLY that folder is committed on a new branch
  demo-results/<yyyyMMdd-HHmmss> in a separate git worktree (your branch and files are untouched), then pushed with
  your existing git credentials.

  Exit code: 0 all stages PASS, 1 at least one stage FAILED, 2 a device is not ready or a stage could not run (NOT RUN),
  3 setup problem (adb or git missing, device not found, pairing or build failed).

.EXAMPLE
  .\scripts\demo\run-demo.ps1 -WatchAddress 192.168.1.57:41203
.EXAMPLE
  .\scripts\demo\run-demo.ps1 -Phone R58W20ABCDE -Watch 192.168.1.57:41203 -SkipBuild
#>
[CmdletBinding()]
param(
    # Serials are auto-detected from `adb devices` (the one reporting a watch is the watch) when empty.
    [string]$Phone = '',
    [string]$Watch = '',
    # ip:port shown on the watch's Wireless debugging screen; the script runs `adb connect` when the watch is not listed.
    [string]$WatchAddress = '',
    # Optional one-time pairing: ip:port and code from "Pair new device" on the watch.
    [string]$PairWatch = '',
    [string]$PairCode = '',
    # Default: docs/demo/results under the repository containing this script.
    [string]$ResultsRoot = '',
    # The demo reminder is due this many minutes after the schedule is published (the phone accepts 1 to 1440).
    [int]$MinutesAhead = 2,
    [string]$ReminderTitle = 'Demo reminder',
    # The reminder must fire this long after its time at the latest (the plan's 1-minute target).
    [int]$LateToleranceSec = 60,
    [int]$PublishWaitSec = 30,
    [int]$ScheduleWaitSec = 90,
    [int]$AckWaitSec = 90,
    # Hard limit for every adb client call; a client that does not return is killed.
    [int]$AdbTimeoutSec = 30,
    [double]$PollSec = 2,
    # Scales the fixed settle delays (1 = real devices; tests use 0).
    [double]$SettleScale = 1,
    [switch]$SkipBuild,
    [switch]$SkipPush
)

Set-StrictMode -Version 2
# Native commands write to stderr; Windows PowerShell 5.1 would turn that into terminating errors under 'Stop'.
$ErrorActionPreference = 'Continue'

# --- the contract with the apps (change here only; see docs/demo.md) -----------------------------------------------------
$Package = 'com.shlomoa.myadhdpal'
$PhoneMain = "$Package/$Package.phone.MainActivity"
$WatchMain = "$Package/$Package.wear.MainActivity"
$PhonePublishAction = "$Package.debug.PUBLISH_DEMO_SCHEDULE"
$PhonePublishComponent = "$Package/$Package.phone.debug.DemoScheduleReceiver"
$PhoneHistoryAction = "$Package.debug.DUMP_HISTORY"
$PhoneHistoryComponent = "$Package/$Package.phone.debug.DemoHistoryReceiver"
$WatchActionAction = "$Package.debug.DEMO_ACTION"
$WatchActionComponent = "$Package/$Package.wear.debug.DemoActionReceiver"
$DemoReminderId = 'demo'
$LogTag = 'DEMO'

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RepoRoot = ((& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null) -join '').Trim()
if (-not $RepoRoot) { Write-Host 'This script must run from a checkout of the repository (git rev-parse failed).'; exit 3 }
if (-not $ResultsRoot) { $ResultsRoot = Join-Path $RepoRoot 'docs/demo/results' }
$RunName = $Stamp
$OutDir = Join-Path $ResultsRoot $RunName

$Results = New-Object System.Collections.Specialized.OrderedDictionary
$script:Role = @{}
# Device serials, ip addresses and ip:port pairs are removed from everything that is published.
$script:SecretWords = @()
function Sleep-Settle([double]$Seconds) { Start-Sleep -Milliseconds ([int]($Seconds * $SettleScale * 1000)) }
function Sleep-Poll { Start-Sleep -Milliseconds ([int]($PollSec * 1000)) }
function Write-Info([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) }

# Never write credentials, account names or e-mail addresses into the published results.
function Remove-Sensitive([string]$Text) {
    $t = $Text -replace '[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', '<redacted-email>'
    $t = $t -replace '(?i)(bearer|authorization|password|passwd|token|secret)[=: ]+\S+', '$1=<redacted>'
    foreach ($word in @($script:SecretWords | Where-Object { $_ } | Sort-Object { $_.Length } -Descending)) {
        $t = $t.Replace($word, '<redacted-device>')
    }
    $t = $t -replace '\b\d{1,3}(\.\d{1,3}){3}(:\d+)?\b', '<redacted-ip>'
    return $t
}

$AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if (-not $AdbCommand) { Write-Host 'adb was not found on PATH. Add the Android SDK platform-tools folder to PATH for this window (docs/dev-setup-windows.md section 6.1).'; exit 3 }
$script:AdbPath = $AdbCommand.Source
$script:AdbExit = 0
$script:AdbTimedOut = $false

function ConvertTo-ArgString([string[]]$Items) {
    $parts = foreach ($a in $Items) {
        if ($a -match '[\s"]') { '"' + ($a -replace '"', '\"') + '"' } else { $a }
    }
    return ($parts -join ' ')
}

# Runs one adb client with a hard timeout. A client that does not return is killed (only that client) and
# $script:AdbTimedOut is set; whatever it printed so far is returned. Output lines are returned without empty lines.
function Invoke-Adb {
    param([string]$Serial, [string[]]$AdbArgs, [int]$TimeoutSec = 0)
    if ($TimeoutSec -le 0) { $TimeoutSec = $AdbTimeoutSec }
    $all = @()
    if ($Serial) { $all += @('-s', $Serial) }
    $all += $AdbArgs
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $script:AdbPath
    $psi.Arguments = ConvertTo-ArgString $all
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $outTask = $proc.StandardOutput.ReadToEndAsync()
    $errTask = $proc.StandardError.ReadToEndAsync()
    $script:AdbTimedOut = $false
    if ($proc.WaitForExit($TimeoutSec * 1000)) {
        $script:AdbExit = $proc.ExitCode
    } else {
        $script:AdbTimedOut = $true
        $script:AdbExit = -1
        Write-Info ("adb call timed out after ${TimeoutSec}s, killing that adb client: adb " + $psi.Arguments)
        try { $proc.Kill() } catch { Write-Verbose "kill failed: $_" }
        [void]$proc.WaitForExit(2000)
    }
    $text = ''
    foreach ($task in @($outTask, $errTask)) {
        # a child that inherited the pipe could keep it open, so never wait for end-of-stream indefinitely
        if ($task.Wait(2000)) { $text += $task.Result + "`n" }
    }
    return @($text -split "`r?`n" | Where-Object { $_ -ne '' })
}

function Stop-Setup([string]$Message) {
    Write-Host $Message
    exit 3
}

# --- DEMO log lines -------------------------------------------------------------------------------------------------------
# `adb logcat -d -v epoch -s DEMO:I` prints "<epoch.millis> <pid> <tid> I DEMO    : <text>"; returns objects with the
# device time in milliseconds and the text, oldest first.
function Get-DemoLog {
    param([string]$Serial)
    $found = @()
    foreach ($line in (Invoke-Adb $Serial @('logcat', '-d', '-v', 'epoch', '-s', "${LogTag}:I"))) {
        if ($line -match '^\s*(\d+)\.(\d+)\s+\d+\s+\d+\s+I\s+DEMO\s*:\s*(.*)$') {
            $ms = [int64]$Matches[1] * 1000 + [int64]($Matches[2].PadRight(3, '0').Substring(0, 3))
            $found += [pscustomobject]@{ Ms = $ms; Text = $Matches[3] }
        }
    }
    return $found
}

function Clear-Logs {
    foreach ($serial in @($Phone, $Watch)) { Invoke-Adb $serial @('logcat', '-c') | Out-Null }
}

# Waits for a DEMO line matching $Pattern; $Trigger (optional) is run before each look, for history dumps.
function Wait-Demo {
    param([string]$Serial, [string]$Pattern, [int]$TimeoutSec, [scriptblock]$Trigger = $null)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
        if ($Trigger) { & $Trigger }
        foreach ($entry in (Get-DemoLog $Serial)) {
            if ($entry.Text -match $Pattern) { return $entry }
        }
        if ((Get-Date) -ge $deadline) { return $null }
        Sleep-Poll
    }
}

function Send-Broadcast {
    param([string]$Serial, [string]$Action, [string]$Component, [string[]]$Extras = @())
    $a = @('shell', 'am', 'broadcast', '-a', $Action, '-n', $Component) + $Extras
    $out = Invoke-Adb $Serial $a
    $text = ($out -join ' ')
    if ($text -notmatch 'Broadcast completed' -or $text -match 'Error|Exception|not found|unable to') {
        throw "Broadcast $Action to the $($Role[$Serial]) failed: $(Remove-Sensitive $text)"
    }
}

function Get-DeviceSeconds([string]$Serial) {
    $out = ((Invoke-Adb $Serial @('shell', 'date', '+%s')) -join '').Trim()
    $value = 0L
    if ([int64]::TryParse($out, [ref]$value)) { return $value }
    return $null
}

# --- results --------------------------------------------------------------------------------------------------------------
$Expect = [ordered]@{
    'Ready'        = 'both devices answer and are charged'
    'Install'      = 'the apps start on both devices'
    'Publish'      = 'nothing to see yet; the phone has scheduled the demo reminder'
    'Schedule'     = 'nothing to see; the schedule arrives on the watch'
    'Reminder'     = 'at the reminder time the watch buzzes once (a short tick) and shows the reminder'
    'Notification' = 'the notification "Demo reminder" with Done, Snooze, Start 2 min and Dismiss is on the watch'
    'Done'         = 'the notification closes by itself (the script did the tap)'
    'Ack'          = 'the phone history gets the Done acknowledgement of the demo reminder'
}
function Set-Result([string]$Stage, [string]$Status, [string]$Detail) {
    $Results[$Stage] = [pscustomobject]@{ Status = $Status; Detail = (Remove-Sensitive $Detail) }
    Write-Info ("{0,-13} {1,-7} {2}" -f $Stage, $Status, (Remove-Sensitive $Detail))
}
function Get-Status([string]$Stage) { if ($Results.Contains($Stage)) { return $Results[$Stage].Status } else { return 'NOT RUN' } }
function Skip-Stage([string]$Stage, [string]$Why) { Set-Result $Stage 'NOT RUN' $Why }

# --- device list and readiness gate ----------------------------------------------------------------------------------------
function Get-AdbDeviceMap {
    $map = New-Object System.Collections.Specialized.OrderedDictionary
    foreach ($line in (Invoke-Adb '' @('devices'))) {
        if ($line -match '^(List of devices|\*)') { continue }
        if ($line -match '^(\S+)\s+(\S.*?)\s*$') { $map[$Matches[1]] = $Matches[2] }
    }
    return $map
}

function Test-IsWatch([string]$Serial) {
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        $answer = ((Invoke-Adb $Serial @('shell', 'getprop', 'ro.build.characteristics')) -join ',')
        if (-not $script:AdbTimedOut) { return ($answer -match 'watch') }
    }
    return $null
}

# Readiness of one device. Returns @{ Problem = '' or a sentence; Fix = what the user does; Row = text for the table }.
# A TIMED-OUT adb call is never reported as "app not installed".
function Get-Readiness {
    param([string]$RoleName, [string]$Serial, [System.Collections.IDictionary]$DeviceMap, [bool]$CheckApp)
    $r = @{ Problem = ''; Fix = ''; Row = '' }
    $adbState = 'not listed'
    if ($Serial -and $DeviceMap.Contains($Serial)) { $adbState = [string]$DeviceMap[$Serial] }
    $boot = '-'; $app = '-'; $battery = '-'
    if (-not $Serial) {
        if ($RoleName -eq 'phone') {
            $r.Problem = 'no phone found in adb devices'
            $r.Fix = 'Connect the Galaxy A55 with the USB cable, unlock it, choose File transfer on the USB notification, accept the "Allow USB debugging" prompt, then run the script again.'
        } else {
            $r.Problem = 'no watch found in adb devices'
            $r.Fix = 'On the watch open Settings > Developer options > Wireless debugging (Wi-Fi on, same network as this PC), then pass -WatchAddress <ip:port shown on that screen>.'
        }
    } elseif ($adbState -eq 'unauthorized') {
        $r.Problem = 'adb state is unauthorized'
        $r.Fix = 'Unlock the device and tap Allow on the "Allow debugging" prompt (tick Always allow), or unplug and replug the cable.'
    } elseif ($adbState -ne 'device') {
        $r.Problem = "adb state is '$adbState'"
        if ($RoleName -eq 'watch') { $r.Fix = 'Run: adb disconnect; adb connect <ip:port> again. The port changes whenever Wireless debugging is switched off and on. Keep the watch screen on and on its charger.' }
        else { $r.Fix = 'Replug the cable, unlock the phone, and check adb devices shows it as "device".' }
    } else {
        $boot = ((Invoke-Adb $Serial @('shell', 'getprop', 'sys.boot_completed')) -join '').Trim()
        if ($script:AdbTimedOut) {
            $boot = 'timeout'
            $r.Problem = "adb calls to the $RoleName time out (no answer within ${AdbTimeoutSec}s)"
            if ($RoleName -eq 'watch') { $r.Fix = 'Wake the watch (tap the screen), keep it on its charger, check its Wi-Fi is on and on the same network as this PC, then reconnect with adb connect.' }
            else { $r.Fix = 'Unlock the phone and replug the cable.' }
        } elseif ($boot -ne '1') {
            $r.Problem = 'boot is not completed yet'
            $r.Fix = 'Wait until the device has finished starting, then run the script again.'
        } else {
            $bat = Invoke-Adb $Serial @('shell', 'dumpsys', 'battery')
            $level = $null
            foreach ($l in $bat) { if ($l -match '^\s*level:\s*(\d+)') { $level = [int]$Matches[1] } }
            if ($null -ne $level) {
                $battery = "$level%"
                if ($level -lt 20) { $r.Problem = "battery is at $level%"; $r.Fix = 'Charge the device to at least 30% and run again.' }
            }
            if ($CheckApp -and -not $r.Problem) {
                $pm = (Invoke-Adb $Serial @('shell', 'pm', 'list', 'packages', $Package)) -join ' '
                if ($script:AdbTimedOut) { $app = 'timeout'; $r.Problem = "the package manager of the $RoleName does not answer"; $r.Fix = 'Wake the device and run again.' }
                elseif ($pm -match [regex]::Escape($Package)) { $app = 'installed' }
                else { $app = 'not installed'; $r.Problem = "the app is not installed on the $RoleName"; $r.Fix = 'Run again without -SkipBuild so the script installs it.' }
            }
        }
    }
    $verdict = 'READY'; if ($r.Problem) { $verdict = 'NOT READY: ' + $r.Problem }
    $r.Row = ('{0,-6} adb:{1,-13} boot:{2,-8} app:{3,-14} {4,-6} {5}' -f $RoleName, $adbState, $boot, $app, $battery, $verdict)
    return $r
}

# Prints the readiness table and stops with exit 2 when anything is not ready.
function Assert-Ready {
    param([bool]$CheckApp, [string]$Stage)
    $map = Get-AdbDeviceMap
    $rows = @(); $problems = @()
    foreach ($pair in @(@('phone', $Phone), @('watch', $Watch))) {
        $r = Get-Readiness $pair[0] $pair[1] $map $CheckApp
        $rows += $r.Row
        if ($r.Problem) { $problems += ("{0}: {1}. Do this: {2}" -f $pair[0], $r.Problem, $r.Fix) }
    }
    Write-Host "Readiness ($Stage):"
    $rows | ForEach-Object { Write-Host "  $_" }
    if ($problems.Count -gt 0) {
        Write-Host ''
        $problems | ForEach-Object { Write-Host "  - $_" }
        Write-Host 'Fix the above and run the script again. Nothing was changed on the devices.'
        exit 2
    }
}

function Connect-Watch {
    if (-not $WatchAddress) { return }
    $map = Get-AdbDeviceMap
    if ($map.Contains($WatchAddress) -and $map[$WatchAddress] -eq 'device') { return }
    Write-Info "Connecting the watch: adb connect $WatchAddress"
    $out = (Invoke-Adb '' @('connect', $WatchAddress) 20) -join ' '
    Write-Info ("adb connect said: " + (Remove-Sensitive $out))
}

# --- devices, build, install -----------------------------------------------------------------------------------------------
if ($PairWatch) {
    if (-not $PairCode) { Stop-Setup '-PairWatch needs -PairCode (the 6-digit code shown by "Pair new device" on the watch).' }
    Write-Info 'Pairing the watch (adb pair)'
    $pairOut = (Invoke-Adb '' @('pair', $PairWatch, $PairCode) 30) -join ' '
    Write-Info ("adb pair said: " + (Remove-Sensitive $pairOut))
    if ($pairOut -notmatch 'Successfully paired') {
        Stop-Setup 'adb pair did not succeed. On the watch open Wireless debugging > Pair new device and use the ip:port and code shown NOW on that screen (they change each time).'
    }
    $script:SecretWords += $PairWatch
}
if ($WatchAddress) { $script:SecretWords += $WatchAddress }
Connect-Watch
$deviceMap = Get-AdbDeviceMap
if (-not $Phone -or -not $Watch) {
    $phones = @()
    foreach ($serial in $deviceMap.Keys) {
        if ($serial -match '^emulator-') { continue }
        if ($Phone -eq $serial -or $Watch -eq $serial) { continue }
        $state = [string]$deviceMap[$serial]
        $isWatch = $null
        if ($state -eq 'device') {
            $isWatch = Test-IsWatch $serial
            if ($null -eq $isWatch) { $isWatch = ($serial -match ':\d+$|_adb-tls-') }
        } else {
            # unauthorized or offline: the role cannot be read, so use the connection type (Wi-Fi debugging = watch)
            $isWatch = ($serial -match ':\d+$|_adb-tls-')
        }
        if ($isWatch) { if (-not $Watch) { $Watch = $serial } } else { $phones += $serial }
    }
    if (-not $Phone) {
        if ($phones.Count -gt 1) { Stop-Setup ("More than one phone is connected ($($phones -join ', ')). Unplug the others or pass -Phone <serial>.") }
        if ($phones.Count -eq 1) { $Phone = $phones[0] }
    }
}
foreach ($serial in @($Phone, $Watch)) { if ($serial) { $script:SecretWords += $serial; if ($serial -match '^(.+):\d+$') { $script:SecretWords += $Matches[1] } } }
if ($Phone) { $Role[$Phone] = 'phone' }
if ($Watch) { $Role[$Watch] = 'watch' }

Write-Host 'Devices: phone = ' -NoNewline
if ($Phone) { Write-Host $Phone -NoNewline } else { Write-Host '(none)' -NoNewline }
Write-Host ', watch = ' -NoNewline
if ($Watch) { Write-Host $Watch } else { Write-Host '(none)' }
Assert-Ready $false 'before build and install'

if (-not $SkipBuild) {
    Write-Info 'Building the debug apps (gradlew :phone:assembleDebug :wear:assembleDebug)'
    Push-Location $RepoRoot
    try {
        $gradle = Join-Path $RepoRoot 'gradlew.bat'
        if (-not (Test-Path $gradle)) { $gradle = Join-Path $RepoRoot 'gradlew' }
        & $gradle ':phone:assembleDebug' ':wear:assembleDebug' 2>&1 | ForEach-Object { if ("$_" -match '^(> Task|> Configure|BUILD|FAILURE|e: )') { Write-Host "  gradle: $_" } }
        if ($LASTEXITCODE -ne 0) { Stop-Setup "Gradle build failed (exit $LASTEXITCODE). Run it by hand to see the errors." }
    } finally { Pop-Location }
    $apks = @{ $Phone = 'phone/build/outputs/apk/debug/phone-debug.apk'; $Watch = 'wear/build/outputs/apk/debug/wear-debug.apk' }
    foreach ($serial in @($Phone, $Watch)) {
        $apk = Join-Path $RepoRoot $apks[$serial]
        $out = Invoke-Adb $serial @('install', '-r', $apk) 240
        if (($out -join ' ') -notmatch 'Success') {
            Stop-Setup ("Installing $($apks[$serial]) on the $($Role[$serial]) failed: " + (Remove-Sensitive ($out -join ' ')) + ' (INSTALL_FAILED_UPDATE_INCOMPATIBLE: run adb -s <serial> uninstall com.shlomoa.myadhdpal first.)')
        }
        Write-Info "Installed $($apks[$serial]) on the $($Role[$serial])"
    }
}
Assert-Ready $true 'after install'
Set-Result 'Ready' 'PASS' 'both devices ready'

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Info "Results folder: $OutDir"

function Save-Logs {
    foreach ($serial in @($Phone, $Watch)) {
        $lines = @(Get-DemoLog $serial | ForEach-Object { Remove-Sensitive ("{0} {1}" -f $_.Ms, $_.Text) })
        $lines | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir ("{0}.log" -f $Role[$serial]))
    }
}

$published = $null
$reminderAtMs = $null
try {
    # trap: an unexpected error in a stage is reported as a FAIL of that stage and the run goes on
    trap {
        $stageNow = 'Publish'
        foreach ($name in $Expect.Keys) { if (-not $Results.Contains($name)) { $stageNow = $name; break } }
        Set-Result $stageNow 'FAIL' "unexpected error: $($_.Exception.Message)"
        continue
    }

    # --- Install: permissions and start --------------------------------------------------------------------------------
    Invoke-Adb $Watch @('shell', 'pm', 'grant', $Package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    Invoke-Adb $Watch @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    $startPhone = (Invoke-Adb $Phone @('shell', 'am', 'start', '-n', $PhoneMain)) -join ' '
    $startWatch = (Invoke-Adb $Watch @('shell', 'am', 'start', '-n', $WatchMain)) -join ' '
    if ($startPhone -match 'Error|Exception' -or $startWatch -match 'Error|Exception') {
        Set-Result 'Install' 'FAIL' ("could not start an app: phone: $startPhone / watch: $startWatch")
    } else {
        Set-Result 'Install' 'PASS' 'debug APKs installed, both apps started'
    }
    Sleep-Settle 5
    Clear-Logs

    # --- Publish: the phone publishes a demo schedule ------------------------------------------------------------------
    $phoneBefore = Get-DeviceSeconds $Phone
    Send-Broadcast $Phone $PhonePublishAction $PhonePublishComponent @('--ei', 'minutes_ahead', "$MinutesAhead", '--es', 'title', $ReminderTitle)
    $published = Wait-Demo $Phone '^published schedule version=(\d+) reminderAt=(\d+)' $PublishWaitSec
    if ($null -eq $published) {
        Set-Result 'Publish' 'FAIL' "the phone did not log 'published schedule' within ${PublishWaitSec}s (is the debug phone app new enough? see docs/demo.md)"
    } else {
        [void]($published.Text -match 'version=(\d+) reminderAt=(\d+)')
        $version = [int64]$Matches[1]
        $reminderAtMs = [int64]$Matches[2]
        Set-Result 'Publish' 'PASS' "schedule version $version published, reminder due at device time $reminderAtMs ms (in about $MinutesAhead min)"
    }

    # --- Schedule: it reaches the watch --------------------------------------------------------------------------------
    $scheduleOk = $false
    if ($null -eq $published) {
        Skip-Stage 'Schedule' 'nothing was published'
    } else {
        $applied = Wait-Demo $Watch "^schedule applied version=$version(\s|$)" $ScheduleWaitSec
        if ($null -eq $applied) { Set-Result 'Schedule' 'FAIL' "the watch did not log 'schedule applied version=$version' within ${ScheduleWaitSec}s (the link is up? the watch app is installed and was started?)" }
        else { $scheduleOk = $true; Set-Result 'Schedule' 'PASS' "the watch applied schedule version $version" }
    }

    # --- Reminder: fires within the tolerance --------------------------------------------------------------------------
    $fired = $null
    if (-not $scheduleOk) {
        Skip-Stage 'Reminder' 'the schedule did not reach the watch'
    } else {
        $now = Get-DeviceSeconds $Phone
        $waitSec = $ScheduleWaitSec
        if ($null -ne $now) { $waitSec = [Math]::Max(1, [int](($reminderAtMs / 1000) - $now) + $LateToleranceSec + 15) }
        Write-Info "Waiting up to $waitSec s for the reminder (do not touch the watch)"
        $fired = Wait-Demo $Watch "^reminder fired id=$DemoReminderId occurrenceAt=$reminderAtMs" $waitSec
        if ($null -eq $fired) {
            Set-Result 'Reminder' 'FAIL' "the watch did not log 'reminder fired id=$DemoReminderId occurrenceAt=$reminderAtMs' (the alarm did not fire; exact alarms allowed? Doze?)"
        } else {
            $lateMs = $fired.Ms - $reminderAtMs
            if ($lateMs -gt ($LateToleranceSec * 1000)) { Set-Result 'Reminder' 'FAIL' "fired $lateMs ms after its time (limit $($LateToleranceSec * 1000) ms)" }
            else { Set-Result 'Reminder' 'PASS' "fired $lateMs ms after its time (limit $($LateToleranceSec * 1000) ms; the two device clocks can differ by a second or two)" }
        }
    }

    # --- Notification: shown on the watch ------------------------------------------------------------------------------
    if ($null -eq $fired) {
        Skip-Stage 'Notification' 'the reminder did not fire'
    } else {
        $dump = (Invoke-Adb $Watch @('shell', 'dumpsys', 'notification', '--noredact')) -join "`n"
        if ($dump -match "pkg=$([regex]::Escape($Package))") { Set-Result 'Notification' 'PASS' 'the watch has a notification from the app (look at the watch: title, 4 actions, one short tick)' }
        else { Set-Result 'Notification' 'FAIL' 'dumpsys notification on the watch shows no notification from the app (notification permission? notifications muted?)' }
    }

    # --- Done: the action is sent on the watch -------------------------------------------------------------------------
    $sent = $null
    if ($null -eq $fired) {
        Skip-Stage 'Done' 'the reminder did not fire'
    } else {
        Send-Broadcast $Watch $WatchActionAction $WatchActionComponent @('--es', 'action', 'DONE')
        $sent = Wait-Demo $Watch "^ack (sent|not sent) action=DONE reminderId=$DemoReminderId" 15
        if ($null -eq $sent) { Set-Result 'Done' 'FAIL' "the watch did not log 'ack sent' within 15s" }
        elseif ($sent.Text -match '^ack not sent') { Set-Result 'Done' 'FAIL' ("the action ran but no acknowledgement was sent: " + $sent.Text) }
        else { Set-Result 'Done' 'PASS' 'Done sent on the watch (the notification closes)' }
    }

    # --- Ack: stored in the phone history ------------------------------------------------------------------------------
    if ($null -eq $sent -or $sent.Text -match '^ack not sent') {
        Skip-Stage 'Ack' 'no acknowledgement was sent'
    } else {
        $dumpHistory = { Send-Broadcast $Phone $PhoneHistoryAction $PhoneHistoryComponent }
        $stored = Wait-Demo $Phone "^history ack id=\S+ action=DONE reminderId=$DemoReminderId occurrenceAt=$reminderAtMs" $AckWaitSec $dumpHistory
        if ($null -eq $stored) { Set-Result 'Ack' 'FAIL' "the phone history has no Done acknowledgement for the demo reminder after ${AckWaitSec}s (listener service? link?)" }
        else { Set-Result 'Ack' 'PASS' 'the Done acknowledgement is stored in the phone history' }
    }
} finally {
    foreach ($name in $Expect.Keys) { if (-not $Results.Contains($name)) { Set-Result $name 'NOT RUN' 'the run stopped before this stage' } }
}

# --- report ----------------------------------------------------------------------------------------------------------------
$report = New-Object System.Collections.ArrayList
[void]$report.Add("Demo run $Stamp")
[void]$report.Add('')
[void]$report.Add('Stage          Result   Detail')
foreach ($name in $Expect.Keys) {
    [void]$report.Add(("{0,-14} {1,-8} {2}" -f $name, $Results[$name].Status, $Results[$name].Detail))
}
[void]$report.Add('')
[void]$report.Add('What you should have seen on the watch:')
foreach ($name in $Expect.Keys) { [void]$report.Add(("  {0,-13} {1}" -f $name, $Expect[$name])) }
[void]$report.Add('  The tile "Reminders" (add it once in the watch tile carousel) shows the next reminder or "No reminders".')
$failed = @($Results.Keys | Where-Object { $Results[$_].Status -eq 'FAIL' }).Count
$notRun = @($Results.Keys | Where-Object { $Results[$_].Status -eq 'NOT RUN' }).Count
[void]$report.Add('')
if ($failed -gt 0) { [void]$report.Add("RESULT: FAIL ($failed stage(s) failed)") }
elseif ($notRun -gt 0) { [void]$report.Add("RESULT: NOT COMPLETE ($notRun stage(s) not run)") }
else { [void]$report.Add('RESULT: ALL STAGES PASS') }
$report | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'results.txt')
Save-Logs
Write-Host ''
$report | ForEach-Object { Write-Host $_ }

# --- publish: commit ONLY the results folder on a new branch (separate worktree) and push it -------------------------------
function Invoke-Git {
    param([string]$Directory, [string[]]$GitArgs)
    $out = & git -C $Directory @GitArgs 2>&1
    $script:GitExit = $LASTEXITCODE
    return @($out | ForEach-Object { ("$_").TrimEnd("`r", "`n") })
}
function Publish-Results {
    $branch = "demo-results/$RunName"
    $relative = "docs/demo/results/$RunName"
    $worktree = Join-Path ([System.IO.Path]::GetTempPath()) ("demo-results-" + [guid]::NewGuid().ToString('N'))
    $added = $false
    try {
        [void](Invoke-Git $RepoRoot @('worktree', 'add', '--detach', $worktree, 'HEAD'))
        if ($script:GitExit -ne 0) { throw 'git worktree add failed' }
        $added = $true
        $target = Join-Path $worktree $relative
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item -Path (Join-Path $OutDir '*') -Destination $target -Recurse -Force
        [void](Invoke-Git $worktree @('checkout', '-q', '-b', $branch))
        if ($script:GitExit -ne 0) { throw "git checkout -b $branch failed" }
        [void](Invoke-Git $worktree @('add', '--', $relative))
        $commit = Invoke-Git $worktree @('commit', '-q', '-m', "Demo results $Stamp")
        if ($script:GitExit -ne 0) { throw ("git commit failed: " + ($commit -join ' ')) }
        Write-Info "Results committed on local branch $branch (only $relative)"
        if ($SkipPush) { Write-Info "-SkipPush: not pushed. Push later with: git push origin $branch"; return }
        $push = Invoke-Git $worktree @('push', 'origin', $branch)
        if ($script:GitExit -ne 0) {
            Write-Host "Push failed: $((Remove-Sensitive ($push -join ' ')).Trim())"
            Write-Host "The results are saved in $OutDir and committed on the local branch $branch. Retry later with: git push origin $branch"
            return
        }
        Write-Host "PUSHED results branch: $branch"
    } catch {
        Write-Host "Could not publish the results ($($_.Exception.Message)). They are saved locally in $OutDir."
    } finally {
        if ($added) { [void](Invoke-Git $RepoRoot @('worktree', 'remove', '--force', $worktree)) }
    }
}
Publish-Results

if ($failed -gt 0) { exit 1 }
if ($notRun -gt 0) { exit 2 }
exit 0
