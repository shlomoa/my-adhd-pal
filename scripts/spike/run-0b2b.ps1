<#
.SYNOPSIS
  Runs the 0b.2b Data Layer spike on REAL devices (Galaxy A55 5G over USB, Galaxy Watch6 Classic over Wi-Fi debugging)
  and publishes the results.

.DESCRIPTION
  THROWAWAY spike tooling (never merged to main; discarded in step 0b.3). Works with Windows PowerShell 5.1 and
  PowerShell 7; needs adb and git on PATH. It never restarts or kills a device. One command: it checks that both devices
  are ready (and says which one is not and why), builds and installs the spike apps, drives them with broadcasts (no
  taps), switches the phone's radios with adb to produce the link states, reads `adb logcat -s SPIKE`, writes the
  results table and raw per-case logs to docs/spikes/results/0b2b-<yyyyMMdd-HHmmss>/ and commits ONLY that folder on a
  new branch spike-results/0b2b-<yyyyMMdd-HHmmss> (in a separate git worktree, your branch and files are untouched),
  then pushes it with your existing git credentials.

  Cases (the same names as 0b.2a where they are the same test):
    C1, C1b, C2a   items cross both ways; deletion; a KEEP item stays
    C3a, C3b       the phone's listener wakes with its app closed (killed, force-stopped)
    C3c            the same while the phone is in forced Doze (screen off, unplugged state simulated)
    C3d            the same with the app in the 'rare' and 'restricted' standby buckets
    L1 Nearby      urgent and non-urgent round trips with Bluetooth connected (checked against -NearbyMaxRttMs)
    L2 Relayed     phone Bluetooth off, both devices online: round trips (checked against -RelayedMaxRttMs)
    L3 Away        phone Bluetooth, Wi-Fi and mobile data off: the watch must see no phone; items are sent from both
                   sides; the radios come back; both items must arrive on their own (within -AwayRecoverSec);
                   then C2b: deleted items did not reappear, the KEEP item is still there
  The phone's radios are restored at the end, also when the run fails. If the run is killed: -RestorePhone.

  Exit code: 0 all cases PASS, 1 at least one case FAILED, 2 a device is not ready or a case could not run (NOT RUN),
  3 setup problem (adb or git missing, device not found, pairing or build failed).

.EXAMPLE
  .\scripts\spike\run-0b2b.ps1 -WatchAddress 192.168.1.57:41203
.EXAMPLE
  .\scripts\spike\run-0b2b.ps1 -PairWatch 192.168.1.57:37099 -PairCode 123456 -WatchAddress 192.168.1.57:41203
.EXAMPLE
  .\scripts\spike\run-0b2b.ps1 -RestorePhone
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
    # Default: docs/spikes/results under the repository containing this script.
    [string]$ResultsRoot = '',
    [int]$WaitSec = 60,
    [int]$NonUrgentWaitSec = 90,
    [int]$DeletionRepeats = 3,
    [int]$DeletionSettleSec = 30,
    [int]$DelayedDeleteMs = 6000,
    # Delay targets (proposal, to be agreed with the user BEFORE the run): median urgent round trip, both directions.
    [int]$NearbyMaxRttMs = 10000,
    [int]$RelayedMaxRttMs = 60000,
    [int]$RelayedDetectSec = 90,
    [int]$AwayDetectSec = 180,
    [int]$AwayRecoverSec = 180,
    [int]$DozeWaitSec = 120,
    # Hard limit for every adb client call; a client that does not return is killed.
    [int]$AdbTimeoutSec = 30,
    [double]$PollSec = 2,
    # Scales the fixed settle delays (1 = real devices; tests use 0).
    [double]$SettleScale = 1,
    [switch]$SkipBuild,
    [switch]$SkipPush,
    # Never ask the user to do anything: a step that adb cannot do is reported NOT RUN instead.
    [switch]$NonInteractive,
    # Only put the phone's radios, power settings and standby bucket back, then exit.
    [switch]$RestorePhone,
    # Run only these cases, e.g. -OnlyCases L2,L3. Names: C1, C1b, C2a, C3, C3c, C3d, L1, L2, L3. Others are reported NOT SELECTED.
    [string[]]$OnlyCases = @()
)

Set-StrictMode -Version 2
# Native commands write to stderr; Windows PowerShell 5.1 would turn that into terminating errors under 'Stop'.
$ErrorActionPreference = 'Continue'

$Package = 'com.shlomoa.myadhdpal'
$ActionPrefix = 'com.shlomoa.myadhdpal.spike.'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RepoRoot = ((& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null) -join '').Trim()
if (-not $RepoRoot) { Write-Host 'This script must run from a checkout of the repository (git rev-parse failed).'; exit 3 }
if (-not $ResultsRoot) { $ResultsRoot = Join-Path $RepoRoot 'docs/spikes/results' }
# `powershell -File run-0b2b.ps1 -OnlyCases L2,L3` passes the list as ONE string, so split on commas ourselves.
$OnlyCases = @($OnlyCases | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$ValidCases = @('C1', 'C1b', 'C2a', 'C3', 'C3c', 'C3d', 'L1', 'L2', 'L3')
foreach ($name in $OnlyCases) { if ($ValidCases -notcontains $name) { Write-Host "Unknown case '$name' in -OnlyCases. Valid: $($ValidCases -join ', ')"; exit 3 } }
$RunName = "0b2b-$Stamp"
if ($OnlyCases.Count -gt 0) { $RunName = "0b2b-$Stamp-" + ($OnlyCases -join '-') }
function Test-Selected([string]$Name) { return ($OnlyCases.Count -eq 0 -or $OnlyCases -contains $Name) }
$OutDir = Join-Path $ResultsRoot $RunName

$Results = New-Object System.Collections.Specialized.OrderedDictionary
$script:CaseTag = 'setup'
$script:Components = @{}
$script:Role = @{}
# Device serials, ip addresses and ip:port pairs are removed from everything that is published.
$script:SecretWords = @()
# Serials of devices that must not be called (none on real hardware; kept because the shared helpers check it).
$script:Away = @()
$Optional = @()
function Sleep-Settle([double]$Seconds) { Start-Sleep -Milliseconds ([int]($Seconds * $SettleScale * 1000)) }
function Sleep-Poll { Start-Sleep -Milliseconds ([int]($PollSec * 1000)) }
function Write-Info([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) }

# Never write credentials, account names or e-mail addresses into the published results.
function Remove-Sensitive([string]$Text) {
    $t = $Text -replace '[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', '<redacted-email>'
    $t = $t -replace '(?i)(bearer|authorization|password|passwd|token|secret)[=: ]+\S+', '$1=<redacted>'
    # device serials, ip addresses and ip:port pairs (longest first, so ip:port goes before the bare ip)
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

function Get-SpikeLog {
    param([string]$Serial)
    $lines = Invoke-Adb $Serial @('logcat', '-d', '-s', 'SPIKE')
    $text = @()
    foreach ($line in $lines) {
        if ($line -match ' SPIKE\s*: (.*)$') { $text += $Matches[1] }
    }
    return $text
}

function Save-CaseLogs {
    foreach ($serial in @($Phone, $Watch)) {
        if ($script:Away -contains $serial) { continue }
        $name = "{0}-{1}.log" -f $Role[$serial], $script:CaseTag
        $lines = @(Get-SpikeLog $serial | ForEach-Object { Remove-Sensitive $_ })
        $lines | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir $name)
    }
}

function Clear-Logs {
    foreach ($serial in @($Phone, $Watch)) {
        if ($script:Away -contains $serial) { continue }
        Invoke-Adb $serial @('logcat', '-c') | Out-Null
    }
}

function Wait-Spike {
    param([string]$Serial, [string]$Pattern, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
        foreach ($line in (Get-SpikeLog $Serial)) {
            if ($line -match $Pattern) { return $line }
        }
        if ((Get-Date) -ge $deadline) { return $null }
        Sleep-Poll
    }
}

function Get-SpikeMatches {
    param([string]$Serial, [string]$Pattern)
    return @(Get-SpikeLog $Serial | Where-Object { $_ -match $Pattern })
}

# Waits for a line matching $Pattern that appeared AFTER $Before earlier matches existed (so old lines are never reused).
function Wait-NewSpike {
    param([string]$Serial, [string]$Pattern, [int]$Before, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
        $found = @(Get-SpikeMatches $Serial $Pattern)
        if ($found.Count -gt $Before) { return $found[$Before] }
        if ((Get-Date) -ge $deadline) { return $null }
        Sleep-Poll
    }
}

function Send-Command {
    param([string]$Serial, [string]$Action, [hashtable]$Extras = @{})
    $a = @('shell', 'am', 'broadcast', '-n', ($Components[$Serial] + '.SpikeCommandReceiver'), '-a', ($ActionPrefix + $Action))
    foreach ($key in $Extras.Keys) {
        $value = $Extras[$key]
        if ($value -is [bool]) {
            $text = 'false'
            if ($value) { $text = 'true' }
            $a += @('--ez', $key, $text)
        } else {
            $a += @('--es', $key, [string]$value)
        }
    }
    $out = Invoke-Adb $Serial $a
    if (($out -join ' ') -notmatch 'Broadcast completed') { throw "Broadcast to $($Role[$Serial]) failed: $($out -join ' ')" }
}

function Send-Item {
    # Sends a DEL/KEEP item from $Serial; returns the item id (read back from the sender's own log), or $null.
    param([string]$Serial, [string]$Kind, [bool]$Urgent, [int]$DeleteDelayMs = 0)
    $pattern = "SEND path=/spike/{0}/(\S+) urgent" -f $Kind
    $before = @(Get-SpikeMatches $Serial $pattern).Count
    $extras = @{ kind = $Kind; urgent = $Urgent }
    if ($DeleteDelayMs -gt 0) { $extras['delayMs'] = $DeleteDelayMs }
    Send-Command $Serial 'SEND' $extras
    $line = Wait-NewSpike $Serial $pattern $before 15
    # $Matches is scoped to the function that ran -match, so match again here.
    if ($line -and $line -match $pattern) { return $Matches[1] }
    return $null
}

function Get-ItemsLine {
    # Asks $Serial to list its data items and returns the new ITEMS line.
    param([string]$Serial)
    $before = @(Get-SpikeMatches $Serial 'ITEMS count=').Count
    Send-Command $Serial 'LIST_ITEMS'
    return (Wait-NewSpike $Serial 'ITEMS count=' $before 20)
}

function Get-ProcessIds([string]$Serial) {
    $out = Invoke-Adb $Serial @('shell', 'pidof', $Package)
    return @($out | Where-Object { $_ -match '^\d+(\s+\d+)*$' })
}

function Wake-Apps {
    Invoke-Adb $Watch @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    foreach ($serial in @($Phone, $Watch)) {
        Invoke-Adb $serial @('shell', 'am', 'start', '-n', ($Components[$serial] + '.SpikeActivity')) | Out-Null
    }
    Sleep-Settle 3
}

function Set-Result {
    param([string]$Case, [string]$Status, [string]$Detail)
    $Results[$Case] = [pscustomobject]@{ Status = $Status; Detail = $Detail }
    Write-Info ("{0}: {1}  {2}" -f $Case, $Status, $Detail)
}

function Get-DelayMs([string]$Line) {
    if ($Line -match 'delayMs=(-?\d+)') { return [int]$Matches[1] }
    return $null
}

function Get-Median([int[]]$Values) {
    $sorted = @($Values | Sort-Object)
    return $sorted[[int][math]::Floor(($sorted.Count - 1) / 2)]
}

function Get-DeviceNowMs {
    # Returns @(deviceMs, hostMidpointMs), or $null when the device clock cannot be read.
    param([string]$Serial)
    $t0 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $out = ((Invoke-Adb $Serial @('shell', 'date', '+%s%N')) -join '').Trim()
    $t1 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    if ($out -notmatch '^\d{16,19}$') { return $null }
    $ms = [long]$out.Substring(0, $out.Length - 6)
    return @($ms, (($t0 + $t1) / 2))
}

function Get-ClockSkewMs {
    # Estimated watch clock minus phone clock, in ms (median of 5 samples; error is about the adb round trip).
    $samples = @()
    for ($i = 0; $i -lt 5; $i++) {
        $p = Get-DeviceNowMs $Phone
        $w = Get-DeviceNowMs $Watch
        if ($null -eq $p -or $null -eq $w) { return $null }
        $samples += [int](($w[0] - $p[0]) - ($w[1] - $p[1]))
    }
    return (Get-Median $samples)
}


# ---------------------------------------------------------------------------------------------------------------------
# Real-device helpers: device list, readiness gate, phone radio control, restore
# ---------------------------------------------------------------------------------------------------------------------
function Stop-Setup([string]$Message) {
    Write-Host $Message
    exit 3
}

# Maps serial -> adb state ('device', 'unauthorized', 'offline', ...) from `adb devices`.
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

function Get-DeviceProp([string]$Serial, [string]$Name) {
    return ((Invoke-Adb $Serial @('shell', 'getprop', $Name)) -join '').Trim()
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
            $r.Fix = 'On the watch open Settings > Developer options > Wireless debugging (Wi-Fi on, same network as this PC), then run: adb connect <ip:port shown on that screen>  (or pass -WatchAddress).'
        }
    } elseif ($adbState -eq 'unauthorized') {
        $r.Problem = 'adb state is unauthorized'
        $r.Fix = 'Unlock the device and tap Allow on the "Allow debugging" prompt (tick Always allow), or unplug and replug the cable.'
    } elseif ($adbState -eq 'offline' -or $adbState -eq 'not listed' -or $adbState -ne 'device') {
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
                if ($level -lt 20) { $r.Problem = "battery is at $level% (the run takes about 30 minutes)"; $r.Fix = 'Charge the device to at least 30% and run again.' }
            }
            if ($CheckApp -and -not $r.Problem) {
                $pm = (Invoke-Adb $Serial @('shell', 'pm', 'list', 'packages', $Package)) -join ' '
                if ($script:AdbTimedOut) { $app = 'timeout'; $r.Problem = "the package manager of the $RoleName does not answer"; $r.Fix = 'Wake the device and run again.' }
                elseif ($pm -match [regex]::Escape($Package)) { $app = 'installed' }
                elseif ($pm -match 'Error|Exception|not running|Could not access|cmd: Failure') { $app = 'unknown'; $r.Problem = "the package manager of the $RoleName is not ready"; $r.Fix = 'Wait a minute and run again.' }
                else { $app = 'not installed' }
            }
        }
    }
    $verdict = 'READY'; if ($r.Problem) { $verdict = 'NOT READY: ' + $r.Problem }
    $r.Row = ('{0,-6} {1,-10} {2,-13} {3,-8} {4,-14} {5,-6} {6}' -f $RoleName, 'adb:', $adbState, "boot:$boot", "app:$app", $battery, $verdict)
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

# Repairs a watch that dropped off Wi-Fi debugging.
function Connect-Watch {
    if (-not $WatchAddress) { return }
    $map = Get-AdbDeviceMap
    if ($map.Contains($WatchAddress) -and $map[$WatchAddress] -eq 'device') { return }
    Write-Info "Connecting the watch: adb connect $WatchAddress"
    $out = (Invoke-Adb '' @('connect', $WatchAddress) 20) -join ' '
    Write-Info ("adb connect said: " + (Remove-Sensitive $out))
}

# Waits for a line, asking LIST_NODES again each round (NODES is a snapshot, not a stream).
function Get-NodesLine {
    param([string]$Serial)
    $before = @(Get-SpikeMatches $Serial 'NODES (count=|-FAILED)').Count
    Send-Command $Serial 'LIST_NODES'
    return (Wait-NewSpike $Serial 'NODES (count=|-FAILED)' $before 15)
}

function Get-NodeCount([string]$Line) {
    if ($Line -and $Line -match 'NODES count=(\d+)') { return [int]$Matches[1] }
    return -1
}

function Wait-NodesState {
    # Polls $Serial's node list until $Predicate (scriptblock taking the line) is true. Returns @(seconds, line) or $null.
    param([string]$Serial, [scriptblock]$Predicate, [int]$TimeoutSec)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $last = $null
    while ($true) {
        $last = Get-NodesLine $Serial
        if ($last -and (& $Predicate $last)) { return @([int]$watch.Elapsed.TotalSeconds, $last) }
        if ($watch.Elapsed.TotalSeconds -ge $TimeoutSec) { return $null }
        Sleep-Poll
    }
}

# --- phone radio control -------------------------------------------------------------------------------------------
$script:OriginalPhone = @{}
$script:PhoneChanged = $false

function Get-PhoneSetting([string]$Key) {
    return ((Invoke-Adb $Phone @('shell', 'settings', 'get', 'global', $Key)) -join '').Trim()
}

function Wait-PhoneSetting([string]$Key, [string]$Want, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ($true) {
        if ((Get-PhoneSetting $Key) -eq $Want) { return $true }
        if ((Get-Date) -ge $deadline) { return $false }
        Sleep-Poll
    }
}

function Request-Manual([string]$Message) {
    if ($NonInteractive) { Write-Info "Needs a manual step, but -NonInteractive is set: $Message"; return $false }
    Write-Host ''
    Write-Host "MANUAL STEP: $Message"
    $answer = Read-Host 'Press Enter when done, or type s to skip this case'
    return ($answer.Trim().ToLowerInvariant() -ne 's')
}

# Switches one phone radio with adb and checks the setting really changed. Falls back to a manual step.
function Set-PhoneRadio {
    param([string]$Name, [bool]$On)
    $map = @{ bluetooth = 'bluetooth_on'; wifi = 'wifi_on'; data = 'mobile_data' }
    $key = $map[$Name]
    $want = '0'; $verb = 'disable'; $human = 'OFF'
    if ($On) { $want = '1'; $verb = 'enable'; $human = 'ON' }
    if ((Get-PhoneSetting $key) -eq $want) { return $true }
    $script:PhoneChanged = $true
    Invoke-Adb $Phone @('shell', 'svc', $Name, $verb) | Out-Null
    if (Wait-PhoneSetting $key $want 15) { Write-Info "phone $Name is $human (svc $Name $verb)"; return $true }
    if ($Name -eq 'bluetooth') {
        Invoke-Adb $Phone @('shell', 'cmd', 'bluetooth_manager', $verb) | Out-Null
        if (Wait-PhoneSetting $key $want 15) { Write-Info "phone bluetooth is $human (cmd bluetooth_manager $verb)"; return $true }
    }
    $label = @{ bluetooth = 'Bluetooth'; wifi = 'Wi-Fi'; data = 'Mobile data' }[$Name]
    if (Request-Manual "adb could not switch the phone's $label $human. Swipe down on the phone and set $label to $human.") {
        return (Wait-PhoneSetting $key $want 20)
    }
    return $false
}

function Save-OriginalPhoneState {
    foreach ($key in @('bluetooth_on', 'wifi_on', 'mobile_data', 'airplane_mode_on', 'stay_on_while_plugged_in')) {
        $script:OriginalPhone[$key] = Get-PhoneSetting $key
    }
}

function Restore-Phone {
    Write-Info 'Restoring the phone (radios, power settings, standby bucket)'
    Invoke-Adb $Phone @('shell', 'dumpsys', 'battery', 'reset') | Out-Null
    Invoke-Adb $Phone @('shell', 'dumpsys', 'deviceidle', 'unforce') | Out-Null
    Invoke-Adb $Phone @('shell', 'am', 'set-standby-bucket', $Package, 'active') | Out-Null
    Invoke-Adb $Phone @('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable') | Out-Null
    # Without a saved state (-RestorePhone) everything is switched on, which is the normal state of the phone.
    foreach ($pair in @(@('bluetooth', 'bluetooth_on'), @('wifi', 'wifi_on'), @('data', 'mobile_data'))) {
        $wasOn = $true
        if ($script:OriginalPhone.ContainsKey($pair[1]) -and $script:OriginalPhone[$pair[1]] -eq '0') { $wasOn = $false }
        if ($wasOn) { Invoke-Adb $Phone @('shell', 'svc', $pair[0], 'enable') | Out-Null }
    }
    $stay = 'false'
    if ($script:OriginalPhone.ContainsKey('stay_on_while_plugged_in') -and $script:OriginalPhone['stay_on_while_plugged_in'] -notin @('0', 'null', '')) { $stay = 'true' }
    Invoke-Adb $Phone @('shell', 'svc', 'power', 'stayon', $stay) | Out-Null
    Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
}

function Wake-Watch { Invoke-Adb $Watch @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null }

function Get-HostSecondsUntil {
    # Seconds on the HOST clock between now and the first new match of $Pattern on $Serial (polling granularity applies).
    param([string]$Serial, [string]$Pattern, [int]$Before, [int]$TimeoutSec, [System.Diagnostics.Stopwatch]$Clock)
    $line = Wait-NewSpike $Serial $Pattern $Before $TimeoutSec
    if (-not $line) { return $null }
    return @([math]::Round($Clock.Elapsed.TotalSeconds, 1), $line)
}

# Round trips on the sender's own clock; $rounds urgent then $rounds non-urgent from each side (after two warm-up pings).
function Invoke-PingSet {
    param([string]$Label, [int]$Rounds = 3)
    Wake-Apps
    Clear-Logs
    foreach ($from in @($Watch, $Phone)) { for ($i = 0; $i -lt 2; $i++) { [void](Invoke-Ping $from $true $WaitSec) } }
    $out = @{ Fail = @(); Parts = @(); Median = @{} }
    foreach ($from in @($Watch, $Phone)) {
        foreach ($urgent in @($true, $false)) {
            $wait = $WaitSec; if (-not $urgent) { $wait = $NonUrgentWaitSec }
            $values = @()
            for ($i = 0; $i -lt $Rounds; $i++) {
                $v = Invoke-Ping $from $urgent $wait
                if ($null -eq $v) { $out.Fail += "$Label $($Role[$from]) urgent=$urgent round trip $($i + 1): no reply within ${wait}s" } else { $values += $v }
            }
            $kind = 'non-urgent'; if ($urgent) { $kind = 'urgent' }
            if ($values.Count -gt 0) {
                $med = Get-Median $values
                $out.Median["$($Role[$from])|$urgent"] = $med
                $out.Parts += ("{0}-started {1}: {2} ms (median {3})" -f $Role[$from], $kind, ($values -join ', '), $med)
            } else { $out.Parts += ("{0}-started {1}: none" -f $Role[$from], $kind) }
        }
    }
    return $out
}

function Invoke-Ping {
    param([string]$From, [bool]$Urgent, [int]$TimeoutSec)
    $pingPattern = 'PING path=/spike/ping/(\S+) urgent='
    $before = @(Get-SpikeMatches $From $pingPattern).Count
    Send-Command $From 'PING' @{ urgent = $Urgent }
    $sent = Wait-NewSpike $From $pingPattern $before 15
    if (-not $sent -or $sent -notmatch 'PING path=/spike/ping/(\S+) urgent=') { return $null }
    $id = $Matches[1]
    $flag = 'false'; if ($Urgent) { $flag = 'true' }
    $rtt = Wait-Spike $From ("RTT id={0} urgent={1} rttMs=(\d+)" -f $id, $flag) $TimeoutSec
    if ($rtt -and $rtt -match 'rttMs=(\d+)') { return [int]$Matches[1] }
    return $null
}

function Get-FirstLine([string[]]$Lines, [string]$Pattern) {
    foreach ($l in $Lines) { if ($l -match $Pattern) { return $l.Trim() } }
    return ''
}

function Get-DeviceInfo([string]$Serial) {
    $gms = Get-FirstLine (Invoke-Adb $Serial @('shell', 'dumpsys', 'package', 'com.google.android.gms') 60) 'versionName='
    $wearable = Get-FirstLine (Invoke-Adb $Serial @('shell', 'dumpsys', 'package', 'com.samsung.android.app.watchmanager') 60) 'versionName='
    return [pscustomobject]@{
        Model   = ((Get-DeviceProp $Serial 'ro.product.manufacturer') + ' ' + (Get-DeviceProp $Serial 'ro.product.model')).Trim()
        Android = (Get-DeviceProp $Serial 'ro.build.version.release') + ' (API ' + (Get-DeviceProp $Serial 'ro.build.version.sdk') + ')'
        OneUi   = Get-DeviceProp $Serial 'ro.build.version.oneui'
        Build   = Get-DeviceProp $Serial 'ro.build.display.id'
        Gms     = $gms
        Wearable = $wearable
    }
}

# ---------------------------------------------------------------------------------------------------------------------
# Devices, build, install, pre-flight
# ---------------------------------------------------------------------------------------------------------------------
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
    $phones = @(); $unresponsive = @()
    foreach ($serial in $deviceMap.Keys) {
        if ($serial -match '^emulator-') { continue }
        if ($Phone -eq $serial -or $Watch -eq $serial) { continue }
        $state = [string]$deviceMap[$serial]
        $isWatch = $null
        if ($state -eq 'device') {
            $isWatch = Test-IsWatch $serial
            if ($null -eq $isWatch) { $unresponsive += $serial; $isWatch = ($serial -match ':\d+$|_adb-tls-') }
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
$Role[$Phone] = 'phone'; $Role[$Watch] = 'watch'
$Components[$Phone] = "$Package/$Package.phone.spike"
$Components[$Watch] = "$Package/$Package.wear.spike"

if ($RestorePhone) {
    if (-not $Phone) { Stop-Setup 'No phone found in adb devices; connect it and run -RestorePhone again.' }
    Restore-Phone
    Write-Host 'Phone restored: Bluetooth, Wi-Fi and mobile data on, stay-awake off, standby bucket active.'
    exit 0
}

Write-Host 'Devices: phone = ' -NoNewline
if ($Phone) { Write-Host $Phone -NoNewline } else { Write-Host '(none)' -NoNewline }
Write-Host ', watch = ' -NoNewline
if ($Watch) { Write-Host $Watch } else { Write-Host '(none)' }
Assert-Ready $false 'before build and install'

if (-not $SkipBuild) {
    Write-Info 'Building the spike apps (gradlew :phone:assembleDebug :wear:assembleDebug)'
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

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Info "Results folder: $OutDir"
$Info = @{ $Phone = (Get-DeviceInfo $Phone); $Watch = (Get-DeviceInfo $Watch) }
Save-OriginalPhoneState
$bucket = ((Invoke-Adb $Phone @('shell', 'am', 'get-standby-bucket', $Package)) -join ' ').Trim()
$idleWhitelist = @(@(Invoke-Adb $Phone @('shell', 'dumpsys', 'deviceidle', 'whitelist')) | Where-Object { $_ -match [regex]::Escape($Package) }).Count -gt 0
Wake-Apps
Wake-Watch
Clear-Logs
$phoneNodes = Get-NodesLine $Phone
$watchNodes = Get-NodesLine $Watch
if ((Get-NodeCount $phoneNodes) -lt 1 -or (Get-NodeCount $watchNodes) -lt 1) {
    Write-Host ("The devices are not connected to each other (phone sees $(Get-NodeCount $phoneNodes) node(s), watch sees $(Get-NodeCount $watchNodes)). " +
        'Open the Galaxy Wearable app on the phone and check that the watch shows Connected, then run again. Nothing was changed.')
    exit 2
}
$StartNearby = ($phoneNodes -match 'nearby=true') -and ($watchNodes -match 'nearby=true')
Write-Info ("Link is up: phone sees {0} node(s), watch sees {1}; nearby = {2}" -f (Get-NodeCount $phoneNodes), (Get-NodeCount $watchNodes), $StartNearby)
$SkewMs = Get-ClockSkewMs
if ($null -eq $SkewMs) { Write-Info 'Clock skew: could not be measured' } else { Write-Info "Clock skew (watch minus phone): $SkewMs ms" }
Invoke-Adb $Phone @('shell', 'svc', 'power', 'stayon', 'true') | Out-Null

$DelIds = New-Object System.Collections.ArrayList
$KeepId = $null

try {
# An unexpected error in a case is reported as a FAIL of that case (never as NOT SELECTED) and the run goes on.
trap {
    Set-Result $script:CaseTag 'FAIL' "unexpected error: $($_.Exception.Message)"
    continue
}
# ---------------------------------------------------------------------------------------------------------------------
# C1 items cross the pairing in both directions; DEL items are deleted and the deletion reaches the sender
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C1') {
$script:CaseTag = 'C1'
Clear-Logs
$c1 = @{}
$c1Fail = @()
foreach ($leg in @(@{ From = $Watch; To = $Phone; Name = 'watch->phone' }, @{ From = $Phone; To = $Watch; Name = 'phone->watch' })) {
    $id = Send-Item $leg.From 'del' $true
    if (-not $id) { $c1Fail += "$($leg.Name): no SEND logged"; continue }
    [void]$DelIds.Add($id)
    $recv = Wait-Spike $leg.To ("RECV path=/spike/del/{0} from=" -f $id) $WaitSec
    if (-not $recv) { $c1Fail += "$($leg.Name): no RECV"; continue }
    $c1[$leg.Name] = Get-DelayMs $recv
    if (-not (Wait-Spike $leg.To ("DELETE-OK path=/spike/del/{0}" -f $id) $WaitSec)) { $c1Fail += "$($leg.Name): receiver did not log DELETE-OK" }
    if (-not (Wait-Spike $leg.From ("EVENT-DELETED path=/spike/del/{0}" -f $id) 10)) { $c1Fail += "$($leg.Name): deletion did not reach the sender within 10s (no EVENT-DELETED); see C1b" }
}
Save-CaseLogs
$delays = "delayMs watch->phone {0} / phone->watch {1} (clock-skew contaminated, NOT delivery delays)" -f $c1['watch->phone'], $c1['phone->watch']
if ($c1Fail.Count -eq 0) { Set-Result 'C1' 'PASS' $delays } else { Set-Result 'C1' 'FAIL' (($c1Fail -join '; ') + '; ' + $delays) }
}

# ---------------------------------------------------------------------------------------------------------------------
# C1b deletion symmetry: both directions, immediate and delayed deletes, repeated; settle; list on both sides
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C1b') {
$script:CaseTag = 'C1b'
Clear-Logs
$groups = @()
foreach ($leg in @(@{ From = $Watch; To = $Phone; Name = 'watch->phone' }, @{ From = $Phone; To = $Watch; Name = 'phone->watch' })) {
    foreach ($mode in @(@{ Name = 'immediate'; Delay = 0 }, @{ Name = 'delayed'; Delay = $DelayedDeleteMs })) {
        $groups += [pscustomobject]@{ Leg = $leg; Mode = $mode; Name = "$($leg.Name) $($mode.Name)"; Ids = (New-Object System.Collections.ArrayList); Problems = @() }
    }
}
foreach ($group in $groups) {
    for ($i = 0; $i -lt $DeletionRepeats; $i++) {
        $id = Send-Item $group.Leg.From 'del' $true $group.Mode.Delay
        if (-not $id) { $group.Problems += 'no SEND logged'; continue }
        [void]$group.Ids.Add($id)
        if (-not (Wait-Spike $group.Leg.To ("RECV path=/spike/del/{0} from=" -f $id) $WaitSec)) { $group.Problems += "$id not received"; continue }
        $deleteWait = $WaitSec + [int]($group.Mode.Delay / 1000)
        if (-not (Wait-Spike $group.Leg.To ("DELETE-OK path=/spike/del/{0}" -f $id) $deleteWait)) { $group.Problems += "${id}: receiver never logged DELETE-OK" }
    }
}
Write-Info "C1b: waiting ${DeletionSettleSec}s for deletions to settle, then listing items on both devices"
Sleep-Settle $DeletionSettleSec
$phoneItems = Get-ItemsLine $Phone
$watchItems = Get-ItemsLine $Watch
$c1bFail = @()
$rows = @()
foreach ($group in $groups) {
    $leftSender = 0; $leftReceiver = 0; $senderEvents = 0
    $senderItems = $watchItems; $receiverItems = $phoneItems
    if ($group.Leg.From -eq $Phone) { $senderItems = $phoneItems; $receiverItems = $watchItems }
    foreach ($id in $group.Ids) {
        if ($senderItems -and $senderItems -match [regex]::Escape($id)) { $leftSender++ }
        if ($receiverItems -and $receiverItems -match [regex]::Escape($id)) { $leftReceiver++ }
        if (@(Get-SpikeMatches $group.Leg.From ("EVENT-DELETED path=/spike/del/{0}" -f $id)).Count -gt 0) { $senderEvents++ }
    }
    $rows += ("{0}: {1} items, still listed on the writer {2}, on the deleter {3}, writer saw EVENT-DELETED for {4}" -f $group.Name, $group.Ids.Count, $leftSender, $leftReceiver, $senderEvents)
    if ($leftSender -gt 0 -or $leftReceiver -gt 0) { $c1bFail += ("{0}: {1} item(s) still listed on the writer and {2} on the deleter after {3}s" -f $group.Name, $leftSender, $leftReceiver, $DeletionSettleSec) }
    foreach ($problem in $group.Problems) { $c1bFail += "$($group.Name): $problem" }
}
if (-not $phoneItems -or -not $watchItems) { $c1bFail += 'could not list the items on both devices' }
Save-CaseLogs
$skewText = 'not measured'; if ($null -ne $SkewMs) { $skewText = "$SkewMs ms" }
$c1bText = "clock skew watch-minus-phone $skewText | " + ($rows -join ' | ')
if ($c1bFail.Count -eq 0) { Set-Result 'C1b' 'PASS' $c1bText } else { Set-Result 'C1b' 'FAIL' (($c1bFail -join '; ') + ' | ' + $c1bText) }
}

# ---------------------------------------------------------------------------------------------------------------------
# C2a a KEEP item stays on both devices (C2b, after the reconnect in C5, checks the DEL items did not reappear)
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C2a') {
$script:CaseTag = 'C2a'
Clear-Logs
$KeepId = Send-Item $Watch 'keep' $true
$c2Fail = @()
if (-not $KeepId) { $c2Fail += 'no SEND logged' }
elseif (-not (Wait-Spike $Phone ("RECV path=/spike/keep/{0} from=" -f $KeepId) $WaitSec)) { $c2Fail += 'KEEP item did not arrive on the phone' }
else {
    Sleep-Settle 3
    foreach ($serial in @($Phone, $Watch)) {
        $items = Get-ItemsLine $serial
        if (-not $items -or $items -notmatch [regex]::Escape($KeepId)) { $c2Fail += "KEEP item not listed on the $($Role[$serial])" }
    }
}
Save-CaseLogs
if ($c2Fail.Count -eq 0) { Set-Result 'C2a' 'PASS' 'KEEP item listed on both devices' } else { Set-Result 'C2a' 'FAIL' ($c2Fail -join '; ') }
}

# ---------------------------------------------------------------------------------------------------------------------
# C3 the phone's listener wakes with its app closed (a: background process killed, b: force-stopped)
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C3') {
function Test-ClosedApp {
    param([string]$Case, [string]$How)
    $script:CaseTag = $Case
    Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    Sleep-Settle 2
    if ($How -eq 'kill') { Invoke-Adb $Phone @('shell', 'am', 'kill', $Package) | Out-Null }
    else { Invoke-Adb $Phone @('shell', 'am', 'force-stop', $Package) | Out-Null }
    Sleep-Settle 3
    $pids = @(Get-ProcessIds $Phone)
    if ($pids.Count -gt 0) {
        Set-Result $Case 'NOT RUN' "the phone app process is still running after '$How' (pid $($pids -join ','))"
        return
    }
    Clear-Logs
    $id = Send-Item $Watch 'del' $true
    if (-not $id) { Set-Result $Case 'FAIL' 'the watch did not log a SEND'; Save-CaseLogs; return }
    $recv = Wait-Spike $Phone ("fg=false RECV path=/spike/del/{0} from=watch" -f $id) $WaitSec
    $created = Wait-Spike $Phone 'SERVICE-CREATED' 5
    if ($recv) {
        $uptime = ''
        if ($recv -match 'processUptimeMs=(\d+)') { $uptime = "processUptimeMs=$($Matches[1])" }
        Save-CaseLogs
        $detail = "RECV without opening the app, fg=false, $uptime"
        if (-not $created) { $detail += ' (no SERVICE-CREATED line seen)' }
        Set-Result $Case 'PASS' $detail
        return
    }
    # not delivered while closed: does it arrive once the app is opened?
    Invoke-Adb $Phone @('shell', 'am', 'start', '-n', ($Components[$Phone] + '.SpikeActivity')) | Out-Null
    $late = Wait-Spike $Phone ("RECV path=/spike/del/{0} from=watch" -f $id) 30
    Save-CaseLogs
    if ($late) { Set-Result $Case 'FAIL' "no RECV while the app was closed ($How); it arrived only after the app was opened" }
    else { Set-Result $Case 'FAIL' "no RECV while the app was closed ($How), and none after opening it" }
}
Test-ClosedApp 'C3a' 'kill'
Wake-Apps
Test-ClosedApp 'C3b' 'force-stop'
Wake-Apps
}


# ---------------------------------------------------------------------------------------------------------------------
# C3c / C3d the phone's listener wakes with its app closed under Doze and under restricted standby buckets
# ---------------------------------------------------------------------------------------------------------------------
function Test-ClosedUnder {
    param([string]$Case, [scriptblock]$Prepare, [scriptblock]$Cleanup)
    $script:CaseTag = $Case
    try {
        Invoke-Adb $Phone @('shell', 'am', 'force-stop', $Package) | Out-Null
        Sleep-Settle 2
        $problem = & $Prepare
        if ($problem) { Set-Result $Case 'NOT RUN' $problem; return }
        $pids = @(Get-ProcessIds $Phone)
        if ($pids.Count -gt 0) { Set-Result $Case 'NOT RUN' "the phone app process is still running (pid $($pids -join ','))"; return }
        Clear-Logs
        Wake-Watch
        $times = @{}
        foreach ($urgent in @($true, $false)) {
            $clock = [System.Diagnostics.Stopwatch]::StartNew()
            $id = Send-Item $Watch 'del' $urgent
            if (-not $id) { Set-Result $Case 'FAIL' 'the watch did not log a SEND'; Save-CaseLogs; return }
            $wait = $DozeWaitSec
            $recv = Wait-Spike $Phone ("fg=false RECV path=/spike/del/{0} from=watch" -f $id) $wait
            $label = 'non-urgent'; if ($urgent) { $label = 'urgent' }
            if ($recv) { $times[$label] = ("{0:N0} s" -f $clock.Elapsed.TotalSeconds) } else { $times[$label] = "not received within ${wait}s" }
        }
        Save-CaseLogs
        $detail = "urgent item with the app closed: $($times['urgent']); non-urgent: $($times['non-urgent']) (host clock, includes up to one poll of $PollSec s)"
        if ($times['urgent'] -like 'not received*') { Set-Result $Case 'FAIL' $detail } else { Set-Result $Case 'PASS' $detail }
    } catch {
        Set-Result $Case 'FAIL' "unexpected error: $($_.Exception.Message)"
    } finally {
        & $Cleanup
    }
}

if (Test-Selected 'C3c') {
    Test-ClosedUnder 'C3c' {
        Invoke-Adb $Phone @('shell', 'svc', 'power', 'stayon', 'false') | Out-Null
        Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP') | Out-Null
        Sleep-Settle 2
        Invoke-Adb $Phone @('shell', 'dumpsys', 'battery', 'unplug') | Out-Null
        Invoke-Adb $Phone @('shell', 'dumpsys', 'deviceidle', 'force-idle') | Out-Null
        $deep = ((Invoke-Adb $Phone @('shell', 'dumpsys', 'deviceidle', 'get', 'deep')) -join ' ').Trim()
        if ($deep -notmatch '^IDLE$') { return "could not force Doze (dumpsys deviceidle get deep said: '$deep')" }
        return $null
    } {
        Invoke-Adb $Phone @('shell', 'dumpsys', 'deviceidle', 'unforce') | Out-Null
        Invoke-Adb $Phone @('shell', 'dumpsys', 'battery', 'reset') | Out-Null
        Invoke-Adb $Phone @('shell', 'svc', 'power', 'stayon', 'true') | Out-Null
        Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
        Wake-Apps
    }
}

if (Test-Selected 'C3d') {
    foreach ($b in @(@('rare', 40), @('restricted', 45))) {
        # Plain scriptblocks that read script-scope variables: a GetNewClosure() block runs in a dynamic module that
        # cannot see this script's functions (Invoke-Adb) on some hosts.
        $script:StandbyBucketName = $b[0]
        $script:StandbyBucketNumber = [string]$b[1]
        if ($env:SPIKE_TEST_THROW -eq 'C3d') {
            $script:CaseTag = "C3d-$($b[0])"
            throw 'simulated failure (SPIKE_TEST_THROW)'
        } else {
        Test-ClosedUnder "C3d-$($b[0])" {
            Invoke-Adb $Phone @('shell', 'am', 'set-standby-bucket', $Package, $script:StandbyBucketName) | Out-Null
            $now = ((Invoke-Adb $Phone @('shell', 'am', 'get-standby-bucket', $Package)) -join ' ').Trim()
            if ($now -notmatch "^($($script:StandbyBucketName)|$($script:StandbyBucketNumber))$") {
                return "could not set the standby bucket to $($script:StandbyBucketName) (am get-standby-bucket said: '$now')"
            }
            return $null
        } {
            Invoke-Adb $Phone @('shell', 'am', 'set-standby-bucket', $Package, 'active') | Out-Null
            Wake-Apps
        }
        }
    }
}

# ---------------------------------------------------------------------------------------------------------------------
# L1 Nearby: round trips with Bluetooth connected
# ---------------------------------------------------------------------------------------------------------------------
function Test-RoundTrips {
    param([string]$Case, [string]$State, [int]$MaxRttMs)
    $script:CaseTag = $Case
    $set = Invoke-PingSet $Case
    Save-CaseLogs
    $fail = @($set.Fail)
    foreach ($from in @('watch', 'phone')) {
        $key = "$from|True"
        if (-not $set.Median.ContainsKey($key)) { continue }
        if ($set.Median[$key] -gt $MaxRttMs) { $fail += ("{0}-started urgent median {1} ms is above the target {2} ms" -f $from, $set.Median[$key], $MaxRttMs) }
    }
    $text = "$State; target: median urgent round trip <= $MaxRttMs ms | " + ($set.Parts -join ' | ')
    if ($fail.Count -eq 0) { Set-Result $Case 'PASS' $text } else { Set-Result $Case 'FAIL' (($fail -join '; ') + ' | ' + $text) }
}

if (Test-Selected 'L1') {
    $script:CaseTag = 'L1'
    $now = Get-NodesLine $Watch
    if ($now -and $now -match 'nearby=true') { Test-RoundTrips 'L1' 'Nearby (watch reports nearby=true)' $NearbyMaxRttMs }
    else { Set-Result 'L1' 'NOT RUN' "the watch does not report the phone as nearby (NODES line: $now). Check Bluetooth is on and the watch shows Connected in Galaxy Wearable." }
}

# ---------------------------------------------------------------------------------------------------------------------
# L2 Relayed: phone Bluetooth off, both devices still online
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'L2') {
    $script:CaseTag = 'L2'
    if (-not (Set-PhoneRadio 'bluetooth' $false)) { Set-Result 'L2' 'NOT RUN' 'the phone Bluetooth could not be switched off (adb and the manual step both failed)' }
    else {
        $seen = Wait-NodesState $Watch { param($l) ($l -match 'NODES count=[1-9]') -and ($l -notmatch 'nearby=true') } $RelayedDetectSec
        if (-not $seen) {
            Save-CaseLogs
            Set-Result 'L2' 'FAIL' ("the watch did not report the phone as a connected, not-nearby node within ${RelayedDetectSec}s after Bluetooth went off (last NODES: " + (Get-NodesLine $Watch) + '). Relayed is not observable this way; see the work log.')
        } else {
            Write-Info "L2: Relayed state seen on the watch after $($seen[0]) s"
            Test-RoundTrips 'L2' ("Relayed (watch reports the phone not nearby after $($seen[0]) s)") $RelayedMaxRttMs
        }
    }
}

# ---------------------------------------------------------------------------------------------------------------------
# L3 Away: phone Bluetooth, Wi-Fi and data off; send from both sides; restore; the items must arrive on their own
# ---------------------------------------------------------------------------------------------------------------------
# C2b needs a DEL item that was deleted and a KEEP item that exist BEFORE the reconnect.
if ((Test-Selected 'L3') -and ($DelIds.Count -eq 0 -or -not $KeepId)) {
    $script:CaseTag = 'C2prep'
    Write-Info 'C2b: creating the DEL and KEEP items it needs before the reconnect'
    Clear-Logs
    if ($DelIds.Count -eq 0) {
        $prepDel = Send-Item $Watch 'del' $true
        if ($prepDel) {
            [void]$DelIds.Add($prepDel)
            [void](Wait-Spike $Phone ("DELETE-OK path=/spike/del/{0}" -f $prepDel) $WaitSec)
        }
    }
    if (-not $KeepId) {
        $KeepId = Send-Item $Watch 'keep' $true
        if ($KeepId) { [void](Wait-Spike $Phone ("RECV path=/spike/keep/{0} from=" -f $KeepId) $WaitSec) }
    }
    Save-CaseLogs
}

if (Test-Selected 'L3') {
    $script:CaseTag = 'L3'
    Wake-Apps
    Clear-Logs
    $isolated = (Set-PhoneRadio 'bluetooth' $false) -and (Set-PhoneRadio 'wifi' $false) -and (Set-PhoneRadio 'data' $false)
    if (-not $isolated) { Set-Result 'L3' 'NOT RUN' 'the phone could not be isolated (Bluetooth, Wi-Fi and mobile data off); adb and the manual step both failed' }
    else {
        $gone = Wait-NodesState $Watch { param($l) ($l -match 'NODES count=0') } $AwayDetectSec
        $awayText = "the watch still listed the phone after ${AwayDetectSec}s"
        if ($gone) { $awayText = "the watch saw no phone after $($gone[0]) s" }
        Write-Info "L3: $awayText"
        $ids = @{}
        # the phone sends while it is offline (its app is open for this), then its app is closed
        foreach ($kind in @('del', 'keep')) { $ids["phone-$kind"] = Send-Item $Phone $kind $true }
        Invoke-Adb $Phone @('shell', 'am', 'force-stop', $Package) | Out-Null
        Sleep-Settle 2
        foreach ($kind in @('del', 'keep')) { $ids["watch-$kind"] = Send-Item $Watch $kind $true }
        $missing = @($ids.Keys | Where-Object { -not $ids[$_] })
        if ($missing.Count -gt 0) {
            Save-CaseLogs
            [void](Set-PhoneRadio 'bluetooth' $true); [void](Set-PhoneRadio 'wifi' $true); [void](Set-PhoneRadio 'data' $true)
            Set-Result 'L3' 'FAIL' ("no SEND was logged for: " + ($missing -join ', '))
        } else {
            Write-Info 'L3: restoring Bluetooth, Wi-Fi and mobile data; from now on the script does nothing else'
            $clock = [System.Diagnostics.Stopwatch]::StartNew()
            $back = (Set-PhoneRadio 'bluetooth' $true) -and (Set-PhoneRadio 'wifi' $true) -and (Set-PhoneRadio 'data' $true)
            $clock.Restart()
            $arrive = @{}
            $deadline = (Get-Date).AddSeconds($AwayRecoverSec)
            $wanted = @(
                @{ Name = 'watch-del'; Serial = $Phone; Pattern = "RECV path=/spike/del/{0} from=watch" -f $ids['watch-del'] },
                @{ Name = 'watch-keep'; Serial = $Phone; Pattern = "RECV path=/spike/keep/{0} from=watch" -f $ids['watch-keep'] },
                @{ Name = 'phone-del'; Serial = $Watch; Pattern = "RECV path=/spike/del/{0} from=phone" -f $ids['phone-del'] },
                @{ Name = 'phone-keep'; Serial = $Watch; Pattern = "RECV path=/spike/keep/{0} from=phone" -f $ids['phone-keep'] }
            )
            while ($arrive.Count -lt $wanted.Count -and (Get-Date) -lt $deadline) {
                foreach ($w in $wanted) {
                    if ($arrive.ContainsKey($w.Name)) { continue }
                    $line = @(Get-SpikeMatches $w.Serial $w.Pattern)
                    if ($line.Count -gt 0) { $arrive[$w.Name] = [pscustomobject]@{ Seconds = [int]$clock.Elapsed.TotalSeconds; Line = $line[0] } }
                }
                if ($arrive.Count -lt $wanted.Count) { Sleep-Poll }
            }
            $nearbyAgain = Wait-NodesState $Watch { param($l) ($l -match 'nearby=true') } 120
            Save-CaseLogs
            $parts = @()
            foreach ($w in $wanted) {
                if ($arrive.ContainsKey($w.Name)) { $parts += ("{0} after {1} s" -f $w.Name, $arrive[$w.Name].Seconds) } else { $parts += ("{0} NOT received within {1}s" -f $w.Name, $AwayRecoverSec) }
            }
            $wake = ''
            if ($arrive.ContainsKey('watch-del')) {
                if ($arrive['watch-del'].Line -match 'fg=false') { $wake = '; the phone listener ran with the app closed (fg=false)' } else { $wake = '; fg was not false on the phone' }
            }
            $nearbyText = 'Nearby not seen again within 120 s'
            if ($nearbyAgain) { $nearbyText = "Nearby again $($nearbyAgain[0]) s after the radios were restored" }
            $text = "$awayText | " + ($parts -join ' | ') + $wake + " | $nearbyText | seconds are measured from the moment the radios were restored (host clock)"
            $fail = @()
            if (-not $gone) { $fail += "Away was not observable: $awayText" }
            if ($arrive.Count -lt $wanted.Count) { $fail += 'not all items arrived on their own' }
            if (-not $back) { $fail += 'a radio could not be restored by adb' }
            if ($fail.Count -eq 0) { Set-Result 'L3' 'PASS' $text } else { Set-Result 'L3' 'FAIL' (($fail -join '; ') + ' | ' + $text) }
        }
    }
}

# C2b after the reconnect: the DEL items must not reappear, the KEEP item must still be there
if (Test-Selected 'L3') {
    $script:CaseTag = 'C2b'
    if (-not $Results.Contains('L3') -or $Results['L3'].Status -eq 'NOT RUN') { Set-Result 'C2b' 'NOT RUN' 'needs the reconnect from L3' }
    else {
        Wake-Apps
        Clear-Logs
        $c2bFail = @()
        foreach ($serial in @($Phone, $Watch)) {
            $items = Get-ItemsLine $serial
            if (-not $items) { $c2bFail += "$($Role[$serial]): no ITEMS line"; continue }
            foreach ($id in $DelIds) { if ($items -match [regex]::Escape($id)) { $c2bFail += "$($Role[$serial]): deleted item $id reappeared" } }
            if ($KeepId -and $items -notmatch [regex]::Escape($KeepId)) { $c2bFail += "$($Role[$serial]): the KEEP item is gone" }
        }
        Save-CaseLogs
        if ($c2bFail.Count -eq 0) { Set-Result 'C2b' 'PASS' 'DEL items did not reappear; the KEEP item is still stored on both devices' } else { Set-Result 'C2b' 'FAIL' ($c2bFail -join '; ') }
    }
}
} finally {
    Restore-Phone
}

# ---------------------------------------------------------------------------------------------------------------------
# Report (shape of docs/spikes/0b2b-plan.md, "Results")
# ---------------------------------------------------------------------------------------------------------------------
function Get-Status([string]$Case) {
    if ($Results.Contains($Case)) { return $Results[$Case].Status }
    if ($OnlyCases.Count -gt 0) { return 'NOT SELECTED' }
    return 'NOT RUN'
}
function Get-Detail([string]$Case) { if ($Results.Contains($Case)) { return $Results[$Case].Detail } else { return '' } }
function Get-InfoLine($Device) {
    return ("{0}, Android {1}, One UI {2}, build {3}, Google Play services {4}, Galaxy Wearable {5}" -f $Device.Model, $Device.Android, $Device.OneUi, $Device.Build, $Device.Gms, $Device.Wearable)
}
$c2Status = 'PASS'
foreach ($c in @('C2a', 'C2b')) { if ((Get-Status $c) -eq 'FAIL') { $c2Status = 'FAIL' } elseif (((Get-Status $c) -eq 'NOT RUN' -or (Get-Status $c) -eq 'NOT SELECTED') -and $c2Status -eq 'PASS') { $c2Status = 'PARTIAL (' + $c + ' not run)' } }
$os = [System.Environment]::OSVersion.VersionString
$skewText = 'not measured'; if ($null -ne $SkewMs) { $skewText = "$SkewMs ms (watch minus phone)" }
$report = @()
$report += "Spike 0b.2b results $Stamp (commit $((& git -C $RepoRoot rev-parse --short HEAD 2>$null) -join ''))"
$report += "Host: $os, PowerShell $($PSVersionTable.PSVersion)"
$report += "Phone: $(Get-InfoLine $Info[$Phone])"
$report += "Watch: $(Get-InfoLine $Info[$Watch])"
$report += "Phone battery settings used: standby bucket at start '$bucket'; in the Doze allow-list (battery optimisation exemption): $idleWhitelist; Samsung 'sleeping apps' and 'deep sleeping apps' state: <fill in from checklist item 9, the script cannot read it>"
$report += "Clock skew: $skewText. Delay targets used: median urgent round trip <= $NearbyMaxRttMs ms nearby, <= $RelayedMaxRttMs ms relayed; away: all items within $AwayRecoverSec s of restoring the radios"
$report += "C1 $(Get-Status 'C1')  $(Get-Detail 'C1')"
$report += "C1b deletion symmetry $(Get-Status 'C1b')  $(Get-Detail 'C1b')"
$report += "C2 $c2Status  C2a: $(Get-Status 'C2a') $(Get-Detail 'C2a') | C2b: $(Get-Status 'C2b') $(Get-Detail 'C2b')"
$report += "C3 app closed: kill $(Get-Status 'C3a'), force-stop $(Get-Status 'C3b'), Doze $(Get-Status 'C3c'), rare bucket $(Get-Status 'C3d-rare'), restricted bucket $(Get-Status 'C3d-restricted')  a: $(Get-Detail 'C3a') | b: $(Get-Detail 'C3b') | c: $(Get-Detail 'C3c') | d-rare: $(Get-Detail 'C3d-rare') | d-restricted: $(Get-Detail 'C3d-restricted')"
$report += "L1 Nearby $(Get-Status 'L1')  $(Get-Detail 'L1')"
$report += "L2 Relayed $(Get-Status 'L2')  $(Get-Detail 'L2')"
$report += "L3 Away $(Get-Status 'L3')  $(Get-Detail 'L3')"
$report += 'Anything unexpected: see the per-case logs in this folder (phone-<case>.log, watch-<case>.log).'
$report += ''
$report += 'Not automated / differences from a real-world day:'
$report += '  - Pairing through the Galaxy Wearable app, and the Samsung battery-management screens, are manual checklist items; the script only checks that each side sees the other node.'
$report += '  - The phone radios are switched with adb (svc / settings); airplane mode, a watch out of range and a phone out of range are not the same radio state.'
$report += '  - Doze and standby buckets are forced with dumpsys / am; Samsung''s own sleeping-apps list cannot be forced and needs days of non-use.'
$report += '  - The watch was kept on Wi-Fi (for adb) and, advised, on its charger; the watch''s own battery behaviour is not tested here.'
$report += '  - delayMs values compare two clocks and are skew-contaminated; only round trips (sender clock) and host-clock seconds are delay measurements.'

# the host OS version looks like an ip address, so that line is not filtered
$reportText = (@($report | ForEach-Object { if ($_ -like 'Host:*') { $_ } else { Remove-Sensitive $_ } }) -join "`n")
$reportText -split "`n" | ForEach-Object { Write-Host $_ }
$reportText | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'results.txt')
Write-Info "Results saved to $(Join-Path $OutDir 'results.txt')"

# ---------------------------------------------------------------------------------------------------------------------
# Publish: commit ONLY the results folder on a new branch (separate worktree) and push it
# ---------------------------------------------------------------------------------------------------------------------
function Invoke-Git {
    param([string]$Directory, [string[]]$GitArgs)
    $out = & git -C $Directory @GitArgs 2>&1
    $script:GitExit = $LASTEXITCODE
    return @($out | ForEach-Object { ("$_").TrimEnd("`r", "`n") })
}
function Publish-Results {
    $branch = "spike-results/$RunName"
    $relative = "docs/spikes/results/$RunName"
    $worktree = Join-Path ([System.IO.Path]::GetTempPath()) ("spike-results-" + [guid]::NewGuid().ToString('N'))
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
        $commit = Invoke-Git $worktree @('commit', '-q', '-m', "Spike 0b.2b results $Stamp")
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

$failed = @($Results.Keys | Where-Object { $Optional -notcontains $_ -and $Results[$_].Status -eq 'FAIL' }).Count
$notRun = @($Results.Keys | Where-Object { $Optional -notcontains $_ -and $Results[$_].Status -eq 'NOT RUN' }).Count
if ($failed -gt 0) { exit 1 }
if ($notRun -gt 0) { exit 2 }
exit 0

