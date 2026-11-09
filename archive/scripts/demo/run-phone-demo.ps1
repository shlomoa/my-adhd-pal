<#
.SYNOPSIS
  Walks through the phone app on the Galaxy A55 5G with ONE command and no taps: installs the debug app, puts example data
  on the phone, opens every screen, takes a screenshot of each, raises the alert from the watch and dismisses it ("I'm OK"),
  records the screen, and publishes the pictures with an index page.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH. It never restarts or kills the phone and
  it never changes radios or power settings. The phone must be unlocked and its screen on for the whole run (about 1 to 2
  minutes): the script cannot unlock it. Stages:

    1 Ready       the phone answers (adb state, boot, battery) before and after the install
    2 Install     the debug APK is installed with its permissions granted, the app starts, the screen recording starts
    3 Seed        the example data (two tasks with reminders, an acknowledgement, a snooze, an answer) is on the phone
    4 Screens     Watch, Tasks, Ask, Settings and History are each opened and photographed (01 to 05)
    5 Alert       an alert is stored like one from the watch; the phone raises it and shows the full-screen alert (06);
                  "I'm OK" is sent; the screen after it is photographed (07)
    6 Recording   the screen recording (at most -RecordSec seconds, never more than 180) is saved

  The five states of the watch link cannot be forced on a real phone (the state comes from the real watch); they are in the
  rendered images (docs/demo/phone/). The results (results.txt, index.md, the PNG files, phone-demo.mp4 and phone.log,
  without the device serial) go to docs/demo/results/phone-<yyyyMMdd-HHmmss>/ and ONLY that folder is committed on a new
  branch demo-results/phone-<yyyyMMdd-HHmmss> in a separate git worktree (your branch and files are untouched), then pushed.

  Exit code: 0 all stages PASS, 1 at least one stage FAILED, 2 the phone is not ready or a stage could not run (NOT RUN),
  3 setup problem (adb or git missing, build failed).

.EXAMPLE
  .\scripts\demo\run-phone-demo.ps1
.EXAMPLE
  .\scripts\demo\run-phone-demo.ps1 -Phone R58W20ABCDE -SkipBuild -SkipPush
#>
[CmdletBinding()]
param(
    # Auto-detected from `adb devices` (the one phone) when empty.
    [string]$Phone = '',
    # Default: docs/demo/results under the repository containing this script.
    [string]$ResultsRoot = '',
    # The screen recording stops after this many seconds (the phone's own limit is 180).
    [int]$RecordSec = 150,
    # Hard limit for every adb client call; a client that does not return is killed.
    [int]$AdbTimeoutSec = 30,
    [double]$PollSec = 2,
    # Scales the fixed settle delays (1 = real device; tests use 0).
    [double]$SettleScale = 1,
    [switch]$SkipBuild,
    [switch]$SkipPush
)

Set-StrictMode -Version 2
$ErrorActionPreference = 'Continue'

# --- the contract with the app (change here only; see docs/demo.md) ------------------------------------------------------------
$Package = 'com.shlomoa.myadhdpal'
$PhoneMain = "$Package/$Package.phone.MainActivity"
$SeedAction = "$Package.debug.SEED_FIXTURES"
$SeedComponent = "$Package/$Package.phone.debug.DemoSeedReceiver"
$DismissAction = "$Package.debug.DISMISS_ALERT"
$DismissComponent = "$Package/$Package.phone.debug.DemoDismissReceiver"
$LogTag = 'DEMO'
$RemoteDir = '/sdcard'
$RemoteRecording = "$RemoteDir/demo-phone.mp4"
$MaxRecordSec = 180
# name, tab of the app, caption
$Tabs = @(
    @('01-watch', 'watch', 'Watch: link state and last sync'),
    @('02-tasks', 'tasks', 'Tasks and reminders'),
    @('03-ask', 'ask', 'Ask the watch a question'),
    @('04-settings', 'settings', 'Settings'),
    @('05-history', 'history', 'History: acknowledgements, answers, alerts')
)

if ($RecordSec -gt $MaxRecordSec) { $RecordSec = $MaxRecordSec }
if ($RecordSec -lt 1) { $RecordSec = 1 }
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RepoRoot = ((& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null) -join '').Trim()
if (-not $RepoRoot) { Write-Host 'This script must run from a checkout of the repository (git rev-parse failed).'; exit 3 }
if (-not $ResultsRoot) { $ResultsRoot = Join-Path $RepoRoot 'docs/demo/results' }
$RunName = "phone-$Stamp"
$OutDir = Join-Path $ResultsRoot $RunName

$Results = New-Object System.Collections.Specialized.OrderedDictionary
$script:SecretWords = @()
function Sleep-Settle([double]$Seconds) { Start-Sleep -Milliseconds ([int]($Seconds * $SettleScale * 1000)) }
function Sleep-Poll { Start-Sleep -Milliseconds ([int]($PollSec * 1000)) }
function Write-Info([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) }

# Never write credentials, account names, e-mail addresses or device identifiers into the published results.
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

# --- DEMO log lines ------------------------------------------------------------------------------------------------------------
function Get-DemoLog {
    param([string]$Serial)
    $found = @()
    foreach ($line in (Invoke-Adb $Serial @('logcat', '-d', '-v', 'epoch', '-s', "${LogTag}:I"))) {
        if ($line -match '^\s*(\d+)\.(\d+)\s+\d+\s+\d+\s+I\s+DEMO\s*:\s*(.*)$') {
            $ms = [int64]$Matches[1] * 1000 + [int64]($Matches[2].PadRight(3, '0').Substring(0, 3))
            # The phone app repeats the tag at the start of its messages ("DEMO seeded ..."); drop it.
            $found += [pscustomobject]@{ Ms = $ms; Text = ($Matches[3] -replace '^DEMO\s+', '') }
        }
    }
    return $found
}

function Wait-Demo {
    param([string]$Serial, [string]$Pattern, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
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
    $text = (Invoke-Adb $Serial $a) -join ' '
    if ($text -notmatch 'Broadcast completed' -or $text -match 'Error|Exception|not found|unable to') {
        throw "Broadcast $Action to the phone failed: $(Remove-Sensitive $text)"
    }
}

# --- results -------------------------------------------------------------------------------------------------------------------
$Expect = [ordered]@{
    'Ready'     = 'the phone answers and is charged'
    'Install'   = 'the app starts; the screen recording runs'
    'Seed'      = 'two tasks with reminders and three history rows are on the phone'
    'Screens'   = 'five screenshots: Watch, Tasks, Ask, Settings, History (01 to 05)'
    'Alert'     = 'the full-screen alert with its sound and vibration (06), then the app after "I''m OK" (07)'
    'Recording' = 'phone-demo.mp4 with the whole walk-through'
}
function Set-Result([string]$Stage, [string]$Status, [string]$Detail) {
    $Results[$Stage] = [pscustomobject]@{ Status = $Status; Detail = (Remove-Sensitive $Detail) }
    Write-Info ("{0,-10} {1,-7} {2}" -f $Stage, $Status, (Remove-Sensitive $Detail))
}
function Skip-Stage([string]$Stage, [string]$Why) { Set-Result $Stage 'NOT RUN' $Why }

# --- device and readiness gate -------------------------------------------------------------------------------------------------
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

# Returns @{ Problem = '' or a sentence; Fix = what the user does; Row = text for the table }.
function Get-Readiness {
    param([string]$Serial, [System.Collections.IDictionary]$DeviceMap, [bool]$CheckApp)
    $r = @{ Problem = ''; Fix = ''; Row = '' }
    $adbState = 'not listed'
    if ($Serial -and $DeviceMap.Contains($Serial)) { $adbState = [string]$DeviceMap[$Serial] }
    $boot = '-'; $app = '-'; $battery = '-'
    if (-not $Serial) {
        $r.Problem = 'no phone found in adb devices'
        $r.Fix = 'Connect the Galaxy A55 with the USB cable, unlock it, choose File transfer on the USB notification, accept the "Allow USB debugging" prompt, then run the script again.'
    } elseif ($adbState -eq 'not listed') {
        $r.Problem = 'the named phone is not listed by adb'
        $r.Fix = 'Check the serial with adb devices, replug the cable and unlock the phone.'
    } elseif ($adbState -eq 'unauthorized') {
        $r.Problem = 'adb state is unauthorized'
        $r.Fix = 'Unlock the phone and tap Allow on the "Allow USB debugging" prompt (tick Always allow), or unplug and replug the cable.'
    } elseif ($adbState -ne 'device') {
        $r.Problem = "adb state is '$adbState'"
        $r.Fix = 'Replug the cable, unlock the phone, and check adb devices shows it as "device".'
    } else {
        $boot = ((Invoke-Adb $Serial @('shell', 'getprop', 'sys.boot_completed')) -join '').Trim()
        if ($script:AdbTimedOut) {
            $boot = 'timeout'
            $r.Problem = "adb calls to the phone time out (no answer within ${AdbTimeoutSec}s)"
            $r.Fix = 'Unlock the phone and replug the cable.'
        } elseif ($boot -ne '1') {
            $r.Problem = 'boot is not completed yet'
            $r.Fix = 'Wait until the phone has finished starting, then run the script again.'
        } else {
            $level = $null
            foreach ($l in (Invoke-Adb $Serial @('shell', 'dumpsys', 'battery'))) { if ($l -match '^\s*level:\s*(\d+)') { $level = [int]$Matches[1] } }
            if ($null -ne $level) {
                $battery = "$level%"
                if ($level -lt 20) { $r.Problem = "battery is at $level%"; $r.Fix = 'Charge the phone to at least 30% and run again.' }
            }
            if ($CheckApp -and -not $r.Problem) {
                $pm = (Invoke-Adb $Serial @('shell', 'pm', 'list', 'packages', $Package)) -join ' '
                if ($script:AdbTimedOut) { $app = 'timeout'; $r.Problem = 'the package manager of the phone does not answer'; $r.Fix = 'Unlock the phone and run again.' }
                elseif ($pm -match [regex]::Escape($Package)) { $app = 'installed' }
                else { $app = 'not installed'; $r.Problem = 'the app is not installed on the phone'; $r.Fix = 'Run again without -SkipBuild so the script installs it.' }
            }
        }
    }
    $verdict = 'READY'; if ($r.Problem) { $verdict = 'NOT READY: ' + $r.Problem }
    $r.Row = ('phone  adb:{0,-13} boot:{1,-8} app:{2,-14} {3,-6} {4}' -f $adbState, $boot, $app, $battery, $verdict)
    return $r
}

function Assert-Ready {
    param([bool]$CheckApp, [string]$Stage)
    $r = Get-Readiness $Phone (Get-AdbDeviceMap) $CheckApp
    Write-Host "Readiness ($Stage):"
    Write-Host "  $($r.Row)"
    if ($r.Problem) {
        Write-Host ''
        Write-Host ("  - phone: {0}. Do this: {1}" -f $r.Problem, $r.Fix)
        Write-Host 'Fix the above and run the script again. Nothing was changed on the phone.'
        exit 2
    }
}

# --- device, build, install ----------------------------------------------------------------------------------------------------
$deviceMap = Get-AdbDeviceMap
if (-not $Phone) {
    $phones = @()
    foreach ($serial in $deviceMap.Keys) {
        if ($serial -match '^emulator-') { continue }
        $state = [string]$deviceMap[$serial]
        $isWatch = $false
        if ($state -eq 'device') {
            $isWatch = Test-IsWatch $serial
            if ($null -eq $isWatch) { $isWatch = ($serial -match ':\d+$|_adb-tls-') }
        } else {
            $isWatch = ($serial -match ':\d+$|_adb-tls-')
        }
        if (-not $isWatch) { $phones += $serial }
    }
    if ($phones.Count -gt 1) { Stop-Setup ("More than one phone is connected ($($phones -join ', ')). Unplug the others or pass -Phone <serial>.") }
    if ($phones.Count -eq 1) { $Phone = $phones[0] }
}
if ($Phone) {
    $script:SecretWords += $Phone
    if ($Phone -match '^(.+):\d+$') { $script:SecretWords += $Matches[1] }
}
Write-Host 'Phone: ' -NoNewline
if ($Phone) { Write-Host $Phone } else { Write-Host '(none)' }
Assert-Ready $false 'before build and install'

if (-not $SkipBuild) {
    Write-Info 'Building the debug app (gradlew :phone:assembleDebug)'
    Push-Location $RepoRoot
    try {
        $gradle = Join-Path $RepoRoot 'gradlew.bat'
        if (-not (Test-Path $gradle)) { $gradle = Join-Path $RepoRoot 'gradlew' }
        & $gradle ':phone:assembleDebug' 2>&1 | ForEach-Object { if ("$_" -match '^(> Task|> Configure|BUILD|FAILURE|e: )') { Write-Host "  gradle: $_" } }
        if ($LASTEXITCODE -ne 0) { Stop-Setup "Gradle build failed (exit $LASTEXITCODE). Run it by hand to see the errors." }
    } finally { Pop-Location }
    $apk = Join-Path $RepoRoot 'phone/build/outputs/apk/debug/phone-debug.apk'
    # -g grants the runtime permissions (notifications) the alert needs
    $out = Invoke-Adb $Phone @('install', '-r', '-g', $apk) 240
    if (($out -join ' ') -notmatch 'Success') {
        Stop-Setup ('Installing phone-debug.apk failed: ' + (Remove-Sensitive ($out -join ' ')) + ' (INSTALL_FAILED_UPDATE_INCOMPATIBLE: run adb -s <serial> uninstall com.shlomoa.myadhdpal first.)')
    }
    Write-Info 'Installed phone-debug.apk'
}
Assert-Ready $true 'after install'
Set-Result 'Ready' 'PASS' 'the phone is ready'

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Info "Results folder: $OutDir"

# --- helpers for the stages ----------------------------------------------------------------------------------------------------
function Get-TopActivity { return ((Invoke-Adb $Phone @('shell', 'dumpsys', 'activity', 'activities')) | Where-Object { $_ -match 'ResumedActivity' } | Select-Object -First 1) }

# Opens a tab of the app on a fresh task and says whether the app is really on top (it is not when the phone is locked).
function Open-Tab([string]$Tab) {
    Invoke-Adb $Phone @('shell', 'am', 'start', '-f', '0x10008000', '-n', $PhoneMain, '--es', 'tab', $Tab) | Out-Null
    Sleep-Settle 2
    return ([string](Get-TopActivity)) -match 'phone\.MainActivity'
}

# Takes one screenshot on the phone, pulls it into the results folder and removes it from the phone.
function Save-Screenshot([string]$Name) {
    $remote = "$RemoteDir/demo-$Name.png"
    $local = Join-Path $OutDir "$Name.png"
    Invoke-Adb $Phone @('shell', 'screencap', '-p', $remote) | Out-Null
    Invoke-Adb $Phone @('pull', $remote, $local) 60 | Out-Null
    Invoke-Adb $Phone @('shell', 'rm', '-f', $remote) | Out-Null
    return ((Test-Path $local) -and ((Get-Item $local).Length -gt 0))
}

$script:RecordProc = $null
function Start-Recording {
    Invoke-Adb $Phone @('shell', 'rm', '-f', $RemoteRecording) | Out-Null
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $script:AdbPath
    $psi.Arguments = ConvertTo-ArgString @('-s', $Phone, 'shell', 'screenrecord', '--time-limit', "$RecordSec", $RemoteRecording)
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $script:RecordProc = [System.Diagnostics.Process]::Start($psi)
}
function Stop-Recording {
    if ($null -eq $script:RecordProc) { return $false }
    # SIGINT lets screenrecord finish the MP4 file properly; the local client is killed if it does not return.
    Invoke-Adb $Phone @('shell', 'pkill', '-l', 'SIGINT', 'screenrecord') | Out-Null
    if (-not $script:RecordProc.WaitForExit(20000)) { try { $script:RecordProc.Kill() } catch { Write-Verbose "kill failed: $_" } }
    Sleep-Settle 1
    $local = Join-Path $OutDir 'phone-demo.mp4'
    Invoke-Adb $Phone @('pull', $RemoteRecording, $local) 180 | Out-Null
    Invoke-Adb $Phone @('shell', 'rm', '-f', $RemoteRecording) | Out-Null
    return ((Test-Path $local) -and ((Get-Item $local).Length -gt 0))
}

$images = New-Object System.Collections.ArrayList
$recorded = $false
try {
    trap {
        $stageNow = 'Install'
        foreach ($name in $Expect.Keys) { if (-not $Results.Contains($name)) { $stageNow = $name; break } }
        Set-Result $stageNow 'FAIL' "unexpected error: $($_.Exception.Message)"
        continue
    }

    # --- Install: permissions, start, recording ------------------------------------------------------------------------------
    Invoke-Adb $Phone @('shell', 'pm', 'grant', $Package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    Invoke-Adb $Phone @('shell', 'appops', 'set', $Package, 'USE_FULL_SCREEN_INTENT', 'allow') | Out-Null
    Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb $Phone @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    $started = (Invoke-Adb $Phone @('shell', 'am', 'start', '-n', $PhoneMain)) -join ' '
    if ($started -match 'Error|Exception') {
        Set-Result 'Install' 'FAIL' "could not start the app: $started"
    } else {
        Set-Result 'Install' 'PASS' 'debug APK installed with its permissions, the app started'
    }
    Sleep-Settle 5
    Invoke-Adb $Phone @('logcat', '-c') | Out-Null
    Start-Recording

    # --- Seed: example data -------------------------------------------------------------------------------------------------
    $seededOk = $false
    Send-Broadcast $Phone $SeedAction $SeedComponent
    $seeded = Wait-Demo $Phone '^seeded history=3 tasks=2 alert=none' 30
    if ($null -eq $seeded) { Set-Result 'Seed' 'FAIL' "the phone did not log 'seeded history=3 tasks=2' within 30s (is the debug app new enough? see docs/demo.md)" }
    else { $seededOk = $true; Set-Result 'Seed' 'PASS' 'two tasks with reminders and three history rows are on the phone' }

    # --- Screens: one screenshot per tab -------------------------------------------------------------------------------------
    $screenFail = @()
    foreach ($tab in $Tabs) {
        $onTop = Open-Tab $tab[1]
        if (-not $onTop) { $screenFail += "$($tab[0]): the app is not on top (is the phone locked? unlock it and keep the screen on)"; continue }
        if (-not (Save-Screenshot $tab[0])) { $screenFail += "$($tab[0]): no screenshot was saved"; continue }
        [void]$images.Add([pscustomobject]@{ Name = $tab[0]; Caption = $tab[2] })
    }
    if ($screenFail.Count -eq 0) { Set-Result 'Screens' 'PASS' 'Watch, Tasks, Ask, Settings and History photographed' }
    else { Set-Result 'Screens' 'FAIL' ($screenFail -join '; ') }

    # --- Alert: raised, full-screen, "I'm OK" --------------------------------------------------------------------------------
    Send-Broadcast $Phone $SeedAction $SeedComponent @('--ez', 'alert', 'true')
    $alertSeeded = Wait-Demo $Phone '^seeded history=\d+ tasks=2 alert=[0-9a-fA-F-]{8,}' 30
    $alertId = $null
    if ($null -ne $alertSeeded -and $alertSeeded.Text -match 'alert=([0-9a-fA-F-]{8,})') { $alertId = $Matches[1] }
    if ($null -eq $alertId) {
        Set-Result 'Alert' 'FAIL' "the phone did not store an alert within 30s (no 'seeded ... alert=<id>' line)"
    } else {
        $raised = Wait-Demo $Phone "^alert raised id=$([regex]::Escape($alertId)) raisedAt=\d+" 30
        if ($null -eq $raised) {
            Set-Result 'Alert' 'FAIL' "the phone stored the alert but did not raise it within 30s (notification permission? the alert raiser?)"
        } else {
            Sleep-Settle 3
            if (([string](Get-TopActivity)) -notmatch 'AlertActivity') {
                Set-Result 'Alert' 'FAIL' 'the alert was raised but the full-screen alert is not on top (full-screen alerts not allowed: Settings > Apps > this app > Notifications > Full-screen notifications; or the phone is in use and shows a heads-up notification instead)'
            } elseif (-not (Save-Screenshot '06-alert')) {
                Set-Result 'Alert' 'FAIL' 'the full-screen alert is on top but no screenshot was saved'
            } else {
                [void]$images.Add([pscustomobject]@{ Name = '06-alert'; Caption = 'The alert from the watch, full screen' })
                Send-Broadcast $Phone $DismissAction $DismissComponent
                $dismissed = Wait-Demo $Phone '^alert dismissed' 15
                if ($null -eq $dismissed) {
                    Set-Result 'Alert' 'FAIL' "the phone did not log 'alert dismissed' within 15s after I'm OK"
                } else {
                    $onTop = Open-Tab 'history'
                    if ($onTop -and (Save-Screenshot '07-after-ok')) {
                        [void]$images.Add([pscustomobject]@{ Name = '07-after-ok'; Caption = 'History after "I''m OK", with the alert in it' })
                        Set-Result 'Alert' 'PASS' 'alert raised, full-screen alert photographed, I''m OK sent, the app is back'
                    } else {
                        Set-Result 'Alert' 'FAIL' 'I''m OK was sent but the screen after it could not be photographed (the app is not on top)'
                    }
                }
            }
        }
    }
} finally {
    # --- Recording --------------------------------------------------------------------------------------------------------------
    try { $recorded = Stop-Recording } catch { $recorded = $false }
    if ($recorded) { Set-Result 'Recording' 'PASS' 'phone-demo.mp4 saved' }
    else { Set-Result 'Recording' 'FAIL' 'no screen recording was saved (screenrecord needs the screen on; a protected screen is recorded black)' }
    foreach ($name in $Expect.Keys) { if (-not $Results.Contains($name)) { Set-Result $name 'NOT RUN' 'the run stopped before this stage' } }
}

# --- report and index page -----------------------------------------------------------------------------------------------------
$report = New-Object System.Collections.ArrayList
[void]$report.Add("Phone demo run $Stamp")
[void]$report.Add('')
[void]$report.Add('Stage          Result   Detail')
foreach ($name in $Expect.Keys) {
    [void]$report.Add(("{0,-14} {1,-8} {2}" -f $name, $Results[$name].Status, $Results[$name].Detail))
}
[void]$report.Add('')
[void]$report.Add('What the pictures should show:')
foreach ($name in $Expect.Keys) { [void]$report.Add(("  {0,-10} {1}" -f $name, $Expect[$name])) }
$failed = @($Results.Keys | Where-Object { $Results[$_].Status -eq 'FAIL' }).Count
$notRun = @($Results.Keys | Where-Object { $Results[$_].Status -eq 'NOT RUN' }).Count
[void]$report.Add('')
if ($failed -gt 0) { [void]$report.Add("RESULT: FAIL ($failed stage(s) failed)") }
elseif ($notRun -gt 0) { [void]$report.Add("RESULT: NOT COMPLETE ($notRun stage(s) not run)") }
else { [void]$report.Add('RESULT: ALL STAGES PASS') }
$report | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'results.txt')

$index = New-Object System.Collections.ArrayList
[void]$index.Add("# Phone demo $Stamp")
[void]$index.Add('')
[void]$index.Add('Screenshots of the phone app on the Galaxy A55 5G, taken by `scripts/demo/run-phone-demo.ps1`.')
foreach ($image in $images) {
    [void]$index.Add('')
    [void]$index.Add("## $($image.Caption)")
    [void]$index.Add('')
    [void]$index.Add("![$($image.Caption)]($($image.Name).png)")
}
if ($recorded) {
    [void]$index.Add('')
    [void]$index.Add('## Screen recording')
    [void]$index.Add('')
    [void]$index.Add('[phone-demo.mp4](phone-demo.mp4)')
}
[void]$index.Add('')
[void]$index.Add('Results of each stage: [results.txt](results.txt).')
$index | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'index.md')

$logLines = @(Get-DemoLog $Phone | ForEach-Object { Remove-Sensitive ("{0} {1}" -f $_.Ms, $_.Text) })
$logLines | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'phone.log')
Write-Host ''
$report | ForEach-Object { Write-Host $_ }

# --- publish: commit ONLY the results folder on a new branch (separate worktree) and push it -----------------------------------
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
        $commit = Invoke-Git $worktree @('commit', '-q', '-m', "Phone demo results $Stamp")
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
