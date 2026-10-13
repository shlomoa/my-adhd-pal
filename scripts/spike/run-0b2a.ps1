<#
.SYNOPSIS
  Runs the 0b.2a Data Layer spike on a paired phone + watch emulator pair, hands-off, and publishes the results.

.DESCRIPTION
  THROWAWAY spike tooling (never merged). Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH.
  One command: it finds the two emulators, builds and installs the spike apps, drives them with broadcasts (no taps),
  reads `adb logcat -s SPIKE`, runs the cases, writes the results table and raw per-case logs to
  docs/spikes/results/0b2a-<yyyyMMdd-HHmmss>/ and commits ONLY that folder on a new branch
  spike-results/0b2a-<yyyyMMdd-HHmmss>, created in a separate git worktree (your branch and uncommitted files are
  untouched), then pushes that branch with your existing git credentials. If the push fails the folder stays local.

  Cases: C1 items cross both ways; C1b deletion symmetry (both directions, immediate and delayed deletes, repeated, then
  a settle wait and a list on both sides, plus a clock-skew measurement); C2 KEEP item stays and deleted items do not
  reappear after the reconnect; C3 phone listener wakes with the app closed (kill, force-stop); C5 stop one emulator,
  send, restart it, both directions; C4 (optional, not gating) urgent versus non-urgent round trips on one clock.

  -OnlyCases C5,C2b runs just those cases (C2b creates its own DEL and KEEP items) and publishes under a distinct
  branch name spike-results/0b2a-<timestamp>-C5-C2b.

  Exit code: 0 all gating cases PASS, 1 at least one gating case FAILED, 2 no FAIL but a gating case was NOT RUN.

.EXAMPLE
  .\scripts\spike\run-0b2a.ps1
.EXAMPLE
  .\scripts\spike\run-0b2a.ps1 -SkipBuild -SkipC4 -SkipPush
#>
[CmdletBinding()]
param(
    # Serials are auto-detected from `adb devices` (the one reporting a watch is the watch) when empty.
    [string]$Phone = '',
    [string]$Watch = '',
    # AVD names are read from the running emulators when empty (needed only for C5).
    [string]$PhoneAvd = '',
    [string]$WatchAvd = '',
    # Default: $env:ANDROID_SDK_ROOT, $env:ANDROID_HOME or $env:LOCALAPPDATA\Android\Sdk, then \emulator\emulator.exe
    [string]$EmulatorExe = '',
    # Default: docs/spikes/results under the repository containing this script.
    [string]$ResultsRoot = '',
    [int]$WaitSec = 60,
    [int]$NonUrgentWaitSec = 60,
    [int]$BootTimeoutSec = 240,
    [int]$ReconnectTimeoutSec = 180,
    [int]$DeletionRepeats = 3,
    [int]$DeletionSettleSec = 45,
    [int]$DelayedDeleteMs = 6000,
    # Hard limit for every adb client call; a client that does not return is killed (adb emu kill hangs once the emulator dies).
    [int]$AdbTimeoutSec = 30,
    [double]$PollSec = 2,
    # Scales the fixed settle delays (1 = real emulators; tests use 0).
    [double]$SettleScale = 1,
    [switch]$SkipBuild,
    [switch]$SkipC4,
    [switch]$SkipC5,
    [switch]$SkipPush,
    # Run only these cases, e.g. -OnlyCases C5,C2b. Names: C1, C1b, C2a, C2b, C3, C4, C5. Others are reported NOT SELECTED.
    [string[]]$OnlyCases = @()
)

Set-StrictMode -Version 2
# Native commands write to stderr; Windows PowerShell 5.1 would turn that into terminating errors under 'Stop'.
$ErrorActionPreference = 'Continue'

$Package = 'com.shlomoa.myadhdpal'
$ActionPrefix = 'com.shlomoa.myadhdpal.spike.'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RepoRoot = ((& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null) -join '').Trim()
if (-not $RepoRoot) { throw 'This script must run from a checkout of the repository (git rev-parse failed).' }
if (-not $ResultsRoot) { $ResultsRoot = Join-Path $RepoRoot 'docs/spikes/results' }
# `powershell -File run-0b2a.ps1 -OnlyCases C5,C2b` passes the list as ONE string, so split on commas ourselves.
$OnlyCases = @($OnlyCases | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$ValidCases = @('C1', 'C1b', 'C2a', 'C2b', 'C3', 'C4', 'C5')
foreach ($name in $OnlyCases) { if ($ValidCases -notcontains $name) { throw "Unknown case '$name' in -OnlyCases. Valid: $($ValidCases -join ', ')" } }
$RunName = "0b2a-$Stamp"
if ($OnlyCases.Count -gt 0) { $RunName = "0b2a-$Stamp-" + ($OnlyCases -join '-') }
function Test-Selected([string]$Name) { return ($OnlyCases.Count -eq 0 -or $OnlyCases -contains $Name) }
$OutDir = Join-Path $ResultsRoot $RunName
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Results = New-Object System.Collections.Specialized.OrderedDictionary
$Optional = @('C4')
$script:CaseTag = 'setup'
$script:Components = @{}
$script:Role = @{}
# Serials of emulators that are stopped on purpose (C5). adb calls with -s <serial> to a dead emulator can block, so
# nothing is sent to them while they are away.
$script:Away = @()

function Sleep-Settle([double]$Seconds) { Start-Sleep -Milliseconds ([int]($Seconds * $SettleScale * 1000)) }
function Sleep-Poll { Start-Sleep -Milliseconds ([int]($PollSec * 1000)) }
function Write-Info([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) }

# Never write credentials, account names or e-mail addresses into the published results.
function Remove-Sensitive([string]$Text) {
    $t = $Text -replace '[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', '<redacted-email>'
    $t = $t -replace '(?i)(bearer|authorization|password|passwd|token|secret)[=: ]+\S+', '$1=<redacted>'
    return $t
}

$AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if (-not $AdbCommand) { throw 'adb was not found on PATH.' }
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

function Wait-DeviceBooted {
    param([string]$Serial, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $out = (Invoke-Adb $Serial @('shell', 'getprop', 'sys.boot_completed')) -join ''
        if ($out.Trim() -eq '1') { return $true }
        Sleep-Poll
    }
    return $false
}

function Wait-DeviceGone {
    param([string]$Serial, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $devices = (Invoke-Adb '' @('devices')) -join "`n"
        if ($devices -notmatch ([regex]::Escape($Serial) + '\s+device')) { return $true }
        Sleep-Poll
    }
    return $false
}

function Resolve-EmulatorExe {
    if ($EmulatorExe) { return $EmulatorExe }
    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
    foreach ($root in $roots) {
        if (-not $root) { continue }
        foreach ($name in @('emulator.exe', 'emulator')) {
            $candidate = Join-Path (Join-Path $root 'emulator') $name
            if (Test-Path $candidate) { return $candidate }
        }
    }
    return $null
}

function Get-AvdName([string]$Serial, [string]$Given) {
    if ($Given) { return $Given }
    $out = Invoke-Adb $Serial @('emu', 'avd', 'name')
    foreach ($line in $out) { if ($line -and $line -ne 'OK' -and $line -notmatch '^\s*$') { return $line.Trim() } }
    return $null
}

# ---------------------------------------------------------------------------------------------------------------------
# Devices, build, install, pre-flight
# ---------------------------------------------------------------------------------------------------------------------
Write-Info "Results folder: $OutDir"
$deviceLines = Invoke-Adb '' @('devices')
$serials = @()
foreach ($line in $deviceLines) { if ($line -match '^(\S+)\s+device\s*$') { $serials += $Matches[1] } }
if (-not $Phone -or -not $Watch) {
    foreach ($serial in $serials) {
        if ($serial -notmatch '^emulator-') { continue }
        $traits = ((Invoke-Adb $serial @('shell', 'getprop', 'ro.build.characteristics')) -join ',')
        if ($traits -match 'watch') { if (-not $Watch) { $Watch = $serial } }
        elseif (-not $Phone) { $Phone = $serial }
    }
}
foreach ($pair in @(@('Phone', $Phone), @('Watch', $Watch))) {
    if (-not $pair[1] -or $serials -notcontains $pair[1]) {
        throw "Device for $($pair[0]) not found in 'adb devices' ($($serials -join ', ')). Start both emulators, or pass -Phone / -Watch."
    }
}
$Components[$Phone] = "$Package/$Package.phone.spike"
$Components[$Watch] = "$Package/$Package.wear.spike"
$Role[$Phone] = 'phone'
$Role[$Watch] = 'watch'
Write-Info "Phone = $Phone, Watch = $Watch"

if (-not $SkipBuild) {
    Write-Info 'Building the spike apps (gradlew :phone:assembleDebug :wear:assembleDebug)'
    Push-Location $RepoRoot
    try {
        $gradle = Join-Path $RepoRoot 'gradlew.bat'
        if (-not (Test-Path $gradle)) { $gradle = Join-Path $RepoRoot 'gradlew' }
        & $gradle ':phone:assembleDebug' ':wear:assembleDebug' 2>&1 | ForEach-Object { if ("$_" -match '^(> Task|> Configure|BUILD|FAILURE|e: )') { Write-Host "  gradle: $_" } }
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE). Run it by hand to see the errors." }
    } finally { Pop-Location }
    $apks = @{ $Phone = 'phone/build/outputs/apk/debug/phone-debug.apk'; $Watch = 'wear/build/outputs/apk/debug/wear-debug.apk' }
    foreach ($serial in @($Phone, $Watch)) {
        $apk = Join-Path $RepoRoot $apks[$serial]
        $out = Invoke-Adb $serial @('install', '-r', $apk) 240
        if (($out -join ' ') -notmatch 'Success') { throw "Installing $apk on $serial failed: $($out -join ' ')" }
        Write-Info "Installed $($apks[$serial]) on $serial"
    }
}

foreach ($serial in @($Phone, $Watch)) {
    $packages = (Invoke-Adb $serial @('shell', 'pm', 'list', 'packages', $Package)) -join ' '
    if ($packages -notmatch [regex]::Escape($Package)) {
        throw "The spike app ($Package) is not installed on $serial. Run without -SkipBuild."
    }
}
$Info = @{}
foreach ($serial in @($Phone, $Watch)) {
    $Info[$serial] = [pscustomobject]@{
        Api   = ((Invoke-Adb $serial @('shell', 'getprop', 'ro.build.version.sdk')) -join '').Trim()
        Image = ((Invoke-Adb $serial @('shell', 'getprop', 'ro.product.name')) -join '').Trim()
        Avd   = ((Invoke-Adb $serial @('shell', 'getprop', 'ro.boot.qemu.avd_name')) -join '').Trim()
    }
}
Wake-Apps
Clear-Logs
Send-Command $Phone 'LIST_NODES'
Send-Command $Watch 'LIST_NODES'
$phoneNodes = Wait-Spike $Phone 'NODES count=(\d+)' 20
$phoneCount = 0; if ($phoneNodes -and $phoneNodes -match 'NODES count=(\d+)') { $phoneCount = [int]$Matches[1] }
$watchNodes = Wait-Spike $Watch 'NODES count=(\d+)' 20
$watchCount = 0; if ($watchNodes -and $watchNodes -match 'NODES count=(\d+)') { $watchCount = [int]$Matches[1] }
if ($phoneCount -lt 1 -or $watchCount -lt 1) {
    throw "The emulators are not connected to each other (phone sees $phoneCount node(s), watch sees $watchCount). Pair them first (Device Manager > Pair Wearable)."
}
Write-Info "Link is up: phone sees $phoneCount node(s), watch sees $watchCount."
$SkewMs = Get-ClockSkewMs
if ($null -eq $SkewMs) { Write-Info 'Clock skew: could not be measured (adb shell date +%s%N not supported)' } else { Write-Info "Clock skew (watch minus phone): $SkewMs ms" }

$DelIds = New-Object System.Collections.ArrayList
$KeepId = $null

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
# C5 disconnect and reconnect: stop one emulator, send from the other, restart it, do nothing else
# ---------------------------------------------------------------------------------------------------------------------
# C2b needs a DEL item that was deleted and a KEEP item that exist BEFORE the reconnect; create them here when the cases
# that normally create them (C1, C2a) did not run in this invocation.
if ((Test-Selected 'C2b') -and ($DelIds.Count -eq 0 -or -not $KeepId)) {
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

if (Test-Selected 'C5') {
function Test-AwayCore {
    param([string]$Case, [string]$AwaySerial, [string]$SenderSerial, [string]$AvdGiven)
    $script:CaseTag = $Case
    $exe = Resolve-EmulatorExe
    $avd = Get-AvdName $AwaySerial $AvdGiven
    if (-not $exe) { Set-Result $Case 'NOT RUN' 'emulator executable not found (pass -EmulatorExe or set ANDROID_SDK_ROOT)'; return }
    if (-not $avd) { Set-Result $Case 'NOT RUN' "cannot read the AVD name of $AwaySerial (pass -PhoneAvd / -WatchAvd)"; return }
    $port = ($AwaySerial -replace '^emulator-', '')
    Write-Info "$Case : stopping $($Role[$AwaySerial]) emulator ($avd)"
    Invoke-Adb $AwaySerial @('emu', 'kill') ([math]::Min(15, $AdbTimeoutSec)) | Out-Null
    Write-Info "$Case : waiting for $AwaySerial to go offline (up to 90s)"
    if (-not (Wait-DeviceGone $AwaySerial 90)) { Set-Result $Case 'NOT RUN' "$AwaySerial did not go offline after 'adb emu kill'"; return }
    $script:Away = @($AwaySerial)
    Clear-Logs
    $ids = @{}
    foreach ($kind in @('del', 'keep')) {
        $ids[$kind] = Send-Item $SenderSerial $kind $true
        if (-not $ids[$kind]) { Set-Result $Case 'FAIL' "the $($Role[$SenderSerial]) did not log a SEND for $kind while the peer was away"; return }
    }
    Send-Command $SenderSerial 'LIST_NODES'
    $nodes = Wait-Spike $SenderSerial 'NODES count=' 15
    Write-Info "$Case : sent while away; sender reports: $nodes"
    Write-Info "$Case : starting $($Role[$AwaySerial]) emulator again (normal boot)"
    Start-Process -FilePath $exe -ArgumentList @('-avd', $avd, '-port', $port) | Out-Null
    Write-Info "$Case : waiting for $AwaySerial to boot (up to ${BootTimeoutSec}s)"
    $bootDeadline = (Get-Date).AddSeconds($BootTimeoutSec)
    $up = $false
    while ((Get-Date) -lt $bootDeadline) {
        $devices = (Invoke-Adb '' @('devices')) -join "`n"
        if ($devices -match ([regex]::Escape($AwaySerial) + '\s+device')) { $up = Wait-DeviceBooted $AwaySerial 60; if ($up) { break } }
        Sleep-Poll
    }
    if (-not $up) { Set-Result $Case 'FAIL' "$AwaySerial did not finish booting within ${BootTimeoutSec}s"; return }
    $script:Away = @()
    $bootedAt = Get-Date
    Write-Info "$Case : booted; waiting up to ${ReconnectTimeoutSec}s for the items (the script does nothing else)"
    $recvDel = Wait-Spike $AwaySerial ("RECV path=/spike/del/{0} from=" -f $ids['del']) $ReconnectTimeoutSec
    $secs = [int]((Get-Date) - $bootedAt).TotalSeconds
    $recvKeep = $null
    if ($recvDel) { $recvKeep = Wait-Spike $AwaySerial ("RECV path=/spike/keep/{0} from=" -f $ids['keep']) 30 }
    Save-CaseLogs
    if ($recvDel -and $recvKeep) {
        $wokeClosed = ''
        if ($Role[$AwaySerial] -eq 'phone') {
            if ($recvDel -match 'fg=false') { $wokeClosed = '; listener ran with the app not open (fg=false)' } else { $wokeClosed = '; fg was not false' }
        }
        Set-Result $Case 'PASS' ("both items arrived {0}s after boot completed; the link recovered on its own{1}; delayMs {2} (skew-contaminated: includes the offline time)" -f $secs, $wokeClosed, (Get-DelayMs $recvDel))
    } elseif ($recvDel -or $recvKeep) {
        Set-Result $Case 'FAIL' 'only one of the two items arrived'
    } else {
        Set-Result $Case 'FAIL' "no item arrived within ${ReconnectTimeoutSec}s after boot; the link did not recover on its own"
    }
}
function Test-Away {
    param([string]$Case, [string]$AwaySerial, [string]$SenderSerial, [string]$AvdGiven)
    try { Test-AwayCore $Case $AwaySerial $SenderSerial $AvdGiven } finally { $script:Away = @() }
}
if ($SkipC5) {
    Set-Result 'C5-watch-away' 'NOT RUN' '-SkipC5'
    Set-Result 'C5-phone-away' 'NOT RUN' '-SkipC5'
} else {
    Test-Away 'C5-watch-away' $Watch $Phone $WatchAvd
    Sleep-Settle 5
    Test-Away 'C5-phone-away' $Phone $Watch $PhoneAvd
}
}

# ---------------------------------------------------------------------------------------------------------------------
# C2b after the reconnect: the DEL items must not reappear, the KEEP item must still be there
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C2b') {
$script:CaseTag = 'C2b'
if ($SkipC5 -or -not $Results.Contains('C5-phone-away') -or $Results['C5-phone-away'].Status -eq 'NOT RUN') {
    Set-Result 'C2b' 'NOT RUN' 'needs the reconnect from C5'
} else {
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

# ---------------------------------------------------------------------------------------------------------------------
# C4 (optional, not gating) urgent versus non-urgent: round trips on the sender's own clock
# ---------------------------------------------------------------------------------------------------------------------
if (Test-Selected 'C4') {
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
$script:CaseTag = 'C4'
if ($SkipC4) {
    Set-Result 'C4' 'NOT RUN' '-SkipC4 (optional, not gating)'
} else {
    Wake-Apps
    Clear-Logs
    Write-Info 'C4 warm-up (2 urgent pings each way, not recorded)'
    foreach ($from in @($Watch, $Phone)) { for ($i = 0; $i -lt 2; $i++) { [void](Invoke-Ping $from $true $WaitSec) } }
    $c4 = @{}
    $c4Fail = @()
    foreach ($from in @($Watch, $Phone)) {
        foreach ($urgent in @($true, $false)) {
            $wait = $WaitSec; if (-not $urgent) { $wait = $NonUrgentWaitSec }
            $values = @()
            for ($i = 0; $i -lt 3; $i++) {
                $v = Invoke-Ping $from $urgent $wait
                if ($null -eq $v) { $c4Fail += "$($Role[$from]) urgent=$urgent round trip $($i + 1): no reply within ${wait}s" } else { $values += $v }
            }
            $c4["$($Role[$from])|$urgent"] = $values
        }
    }
    Save-CaseLogs
    $parts = @()
    foreach ($from in @($Watch, $Phone)) {
        foreach ($urgent in @($true, $false)) {
            $v = @($c4["$($Role[$from])|$urgent"])
            $label = 'non-urgent'; if ($urgent) { $label = 'urgent' }
            if ($v.Count -gt 0) {
                $parts += ("{0}-started {1}: {2} ms (median {3})" -f $Role[$from], $label, ($v -join ', '), (Get-Median $v))
            } else { $parts += ("{0}-started {1}: none" -f $Role[$from], $label) }
        }
    }
    $c4Text = 'round trips on the sender clock: ' + ($parts -join ' | ')
    if ($c4Fail.Count -eq 0) { Set-Result 'C4' 'PASS' $c4Text } else { Set-Result 'C4' 'FAIL' (($c4Fail -join '; ') + '; ' + $c4Text) }
}
}

# ---------------------------------------------------------------------------------------------------------------------
# Report (shape of docs/dev-setup-windows.md 6.4)
# ---------------------------------------------------------------------------------------------------------------------
function Get-Status([string]$Case) {
    if ($Results.Contains($Case)) { return $Results[$Case].Status }
    if ($OnlyCases.Count -gt 0) { return 'NOT SELECTED' }
    return 'NOT RUN'
}
function Get-Detail([string]$Case) { if ($Results.Contains($Case)) { return $Results[$Case].Detail } else { return '' } }
$c2Status = 'PASS'
foreach ($c in @('C2a', 'C2b')) { if ((Get-Status $c) -eq 'FAIL') { $c2Status = 'FAIL' } elseif (((Get-Status $c) -eq 'NOT RUN' -or (Get-Status $c) -eq 'NOT SELECTED') -and $c2Status -eq 'PASS') { $c2Status = 'PARTIAL (' + $c + ' not run)' } }
$os = [System.Environment]::OSVersion.VersionString
$report = @()
$report += "Spike 0b.2a results $Stamp (commit $((& git -C $RepoRoot rev-parse --short HEAD 2>$null) -join ''))"
$report += "Host OS / Android Studio version: $os, PowerShell $($PSVersionTable.PSVersion) / <fill in: Android Studio version, the script cannot see it>"
$report += "Phone image (name, API) / Watch image (name, API): $($Info[$Phone].Image) API $($Info[$Phone].Api) (AVD $($Info[$Phone].Avd)) / $($Info[$Watch].Image) API $($Info[$Watch].Api) (AVD $($Info[$Watch].Avd))"
$report += "C1 $(Get-Status 'C1')  $(Get-Detail 'C1')"
$report += "C1b deletion symmetry $(Get-Status 'C1b')  $(Get-Detail 'C1b')"
$report += "C2 $c2Status  C2a: $(Get-Status 'C2a') $(Get-Detail 'C2a') | C2b: $(Get-Status 'C2b') $(Get-Detail 'C2b')"
$report += "C3 swipe-away (am kill) $(Get-Status 'C3a'), force-stop $(Get-Status 'C3b')  a: $(Get-Detail 'C3a') | b: $(Get-Detail 'C3b')"
$report += "C5 watch-away $(Get-Status 'C5-watch-away') ($(Get-Detail 'C5-watch-away')), phone-away $(Get-Status 'C5-phone-away') ($(Get-Detail 'C5-phone-away'))"
$report += "C4 (optional, not gating) $(Get-Status 'C4')  $(Get-Detail 'C4')"
$report += 'Anything unexpected: see the per-case logs in this folder (phone-<case>.log, watch-<case>.log).'
$report += ''
$report += 'Not automated / differences from the manual checklist:'
$report += '  - Pairing and Google sign-in are manual prerequisites; the script only checks that each side sees the other node.'
$report += '  - C3a uses HOME + "am kill" (kills the background app process), which is equivalent to but not identical to swiping the app away in Recents.'
$report += '  - C5 restarts the emulators with the emulator executable (normal boot, same AVD, same console port); whether Android Studio started them with other options is not reproduced.'
$report += '  - delayMs values compare two emulators'' clocks and are skew-contaminated; only the C4 round trips are delay measurements.'
$report += '  - The clock skew estimate has an error of about one adb round trip.'

$reportText = Remove-Sensitive ($report -join "`n")
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
        $commit = Invoke-Git $worktree @('commit', '-q', '-m', "Spike 0b.2a results $Stamp")
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
