<#
.SYNOPSIS
  Runs the 0b.2a Data Layer spike cases C1-C5 on a paired phone + watch emulator pair and prints a results table.

.DESCRIPTION
  THROWAWAY spike tooling (never merged). Works with Windows PowerShell 5.1 and PowerShell 7, adb on PATH.
  Prerequisites: both emulators are running and paired, and the spike builds from branch claude/shared-0b2a-spike-rtt are
  installed on both (adb -s <serial> install -r ...). The script drives the apps with explicit broadcasts
  (SpikeCommandReceiver), reads `adb logcat -s SPIKE`, and measures delays as ROUND TRIPS on the sender's own clock.

  Exit code: 0 all cases PASS, 1 at least one FAIL, 2 no FAIL but at least one case NOT RUN.

.EXAMPLE
  .\scripts\spike\run-0b2a.ps1
.EXAMPLE
  .\scripts\spike\run-0b2a.ps1 -WatchAvd Wear_OS_Large_Round -PhoneAvd Pixel_9 -SkipC5
#>
[CmdletBinding()]
param(
    [string]$Phone = 'emulator-5554',
    [string]$Watch = 'emulator-5556',
    # AVD names are read from the running emulators when empty (needed only for C5).
    [string]$PhoneAvd = '',
    [string]$WatchAvd = '',
    # Default: $env:ANDROID_SDK_ROOT, $env:ANDROID_HOME or $env:LOCALAPPDATA\Android\Sdk, then \emulator\emulator.exe
    [string]$EmulatorExe = '',
    [string]$OutDir = '',
    [int]$WaitSec = 60,
    [int]$NonUrgentWaitSec = 180,
    [int]$BootTimeoutSec = 240,
    [int]$ReconnectTimeoutSec = 180,
    [double]$PollSec = 2,
    # Scales the fixed settle delays (1 = real emulators; tests use 0).
    [double]$SettleScale = 1,
    [switch]$SkipC5
)

Set-StrictMode -Version 2
# Native commands write to stderr; Windows PowerShell 5.1 would turn that into terminating errors under 'Stop'.
$ErrorActionPreference = 'Continue'

$Package = 'com.shlomoa.myadhdpal'
$Components = @{
    $Phone = "$Package/$Package.phone.spike"
    $Watch = "$Package/$Package.wear.spike"
}
$Role = @{ $Phone = 'phone'; $Watch = 'watch' }
$ActionPrefix = 'com.shlomoa.myadhdpal.spike.'

if (-not $OutDir) { $OutDir = Join-Path (Get-Location) ("spike-0b2a-" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Results = New-Object System.Collections.Specialized.OrderedDictionary
$Notes = New-Object System.Collections.ArrayList
$RawLog = @{}
$script:CaseTag = 'setup'

function Sleep-Settle([double]$Seconds) { Start-Sleep -Milliseconds ([int]($Seconds * $SettleScale * 1000)) }
function Sleep-Poll { Start-Sleep -Milliseconds ([int]($PollSec * 1000)) }

function Write-Info([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) }

function Invoke-Adb {
    param([string]$Serial, [string[]]$AdbArgs)
    $all = @()
    if ($Serial) { $all += @('-s', $Serial) }
    $all += $AdbArgs
    $out = & adb @all 2>&1
    $script:AdbExit = $LASTEXITCODE
    return @($out | ForEach-Object { ("$_").TrimEnd("`r", "`n") })
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
        $name = "{0}-{1}.log" -f $Role[$serial], $script:CaseTag
        $lines = Get-SpikeLog $serial
        $lines | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir $name)
        $RawLog["$($Role[$serial]):$($script:CaseTag)"] = $lines
    }
}

function Clear-Logs {
    foreach ($serial in @($Phone, $Watch)) { Invoke-Adb $serial @('logcat', '-c') | Out-Null }
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
        $found = Get-SpikeMatches $Serial $Pattern
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
    param([string]$Serial, [string]$Kind, [bool]$Urgent)
    $pattern = "SEND path=/spike/{0}/(\S+) urgent" -f $Kind
    $before = (Get-SpikeMatches $Serial $pattern).Count
    Send-Command $Serial 'SEND' @{ kind = $Kind; urgent = $Urgent }
    $line = Wait-NewSpike $Serial $pattern $before 15
    # $Matches is scoped to the function that ran -match, so match again here.
    if ($line -and $line -match ("SEND path=/spike/{0}/(\S+) urgent" -f $Kind)) { return $Matches[1] }
    return $null
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
# Pre-flight
# ---------------------------------------------------------------------------------------------------------------------
Write-Info "Output folder: $OutDir"
$deviceList = (Invoke-Adb '' @('devices')) -join "`n"
foreach ($serial in @($Phone, $Watch)) {
    if ($deviceList -notmatch ([regex]::Escape($serial) + '\s+device')) {
        throw "Device $serial is not listed by 'adb devices'. Start the emulator, or pass -Phone / -Watch."
    }
    $packages = (Invoke-Adb $serial @('shell', 'pm', 'list', 'packages', $Package)) -join ' '
    if ($packages -notmatch [regex]::Escape($Package)) {
        throw "The spike app ($Package) is not installed on $serial. Install the APKs first (see docs/dev-setup-windows.md)."
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

$DelIds = New-Object System.Collections.ArrayList
$KeepId = $null

# ---------------------------------------------------------------------------------------------------------------------
# C1 items cross the pairing in both directions; DEL items are deleted and the deletion reaches the sender
# ---------------------------------------------------------------------------------------------------------------------
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
    if (-not (Wait-Spike $leg.From ("EVENT-DELETED path=/spike/del/{0}" -f $id) $WaitSec)) { $c1Fail += "$($leg.Name): deletion did not reach the sender (no EVENT-DELETED)" }
}
Send-Command $Phone 'LIST_ITEMS'
Send-Command $Watch 'LIST_ITEMS'
Sleep-Settle 3
foreach ($serial in @($Phone, $Watch)) {
    $items = Wait-Spike $serial 'ITEMS count=' 15
    if (-not $items) { $c1Fail += "$($Role[$serial]): no ITEMS line"; continue }
    foreach ($id in $DelIds) { if ($items -match [regex]::Escape($id)) { $c1Fail += "$($Role[$serial]): deleted item $id is still listed" } }
}
Save-CaseLogs
$delays = "delayMs watch->phone {0} / phone->watch {1} (clock-skew contaminated, NOT delivery delays)" -f $c1['watch->phone'], $c1['phone->watch']
if ($c1Fail.Count -eq 0) { Set-Result 'C1' 'PASS' $delays } else { Set-Result 'C1' 'FAIL' (($c1Fail -join '; ') + '; ' + $delays) }

# ---------------------------------------------------------------------------------------------------------------------
# C2a a KEEP item stays on both devices (C2b, after the reconnect in C5, checks the DEL items did not reappear)
# ---------------------------------------------------------------------------------------------------------------------
$script:CaseTag = 'C2a'
Clear-Logs
$KeepId = Send-Item $Watch 'keep' $true
$c2Fail = @()
if (-not $KeepId) { $c2Fail += 'no SEND logged' }
elseif (-not (Wait-Spike $Phone ("RECV path=/spike/keep/{0} from=" -f $KeepId) $WaitSec)) { $c2Fail += 'KEEP item did not arrive on the phone' }
else {
    Send-Command $Phone 'LIST_ITEMS'
    Send-Command $Watch 'LIST_ITEMS'
    Sleep-Settle 3
    foreach ($serial in @($Phone, $Watch)) {
        $items = Wait-Spike $serial 'ITEMS count=' 15
        if (-not $items -or $items -notmatch [regex]::Escape($KeepId)) { $c2Fail += "KEEP item not listed on the $($Role[$serial])" }
    }
}
Save-CaseLogs
if ($c2Fail.Count -eq 0) { Set-Result 'C2a' 'PASS' 'KEEP item listed on both devices' } else { Set-Result 'C2a' 'FAIL' ($c2Fail -join '; ') }

# ---------------------------------------------------------------------------------------------------------------------
# C3 the phone's listener wakes with its app closed (a: background process killed, b: force-stopped)
# ---------------------------------------------------------------------------------------------------------------------
function Test-ClosedApp {
    param([string]$Case, [string]$How)
    $script:CaseTag = $Case
    Invoke-Adb $Phone @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    Sleep-Settle 2
    if ($How -eq 'kill') { Invoke-Adb $Phone @('shell', 'am', 'kill', $Package) | Out-Null }
    else { Invoke-Adb $Phone @('shell', 'am', 'force-stop', $Package) | Out-Null }
    Sleep-Settle 3
    $pids = Get-ProcessIds $Phone
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

# ---------------------------------------------------------------------------------------------------------------------
# C4 urgent versus non-urgent: round trips on the sender's own clock (3 each, both directions, after a warm-up)
# ---------------------------------------------------------------------------------------------------------------------
$script:CaseTag = 'C4'
Clear-Logs
function Invoke-Ping {
    param([string]$From, [bool]$Urgent, [int]$TimeoutSec)
    $pingPattern = 'PING path=/spike/ping/(\S+) urgent='
    $before = (Get-SpikeMatches $From $pingPattern).Count
    Send-Command $From 'PING' @{ urgent = $Urgent }
    $sent = Wait-NewSpike $From $pingPattern $before 15
    if (-not $sent -or $sent -notmatch 'PING path=/spike/ping/(\S+) urgent=') { return $null }
    $id = $Matches[1]
    $flag = 'false'; if ($Urgent) { $flag = 'true' }
    $rtt = Wait-Spike $From ("RTT id={0} urgent={1} rttMs=(\d+)" -f $id, $flag) $TimeoutSec
    if ($rtt -and $rtt -match 'rttMs=(\d+)') { return [int]$Matches[1] }
    return $null
}
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

# ---------------------------------------------------------------------------------------------------------------------
# C5 disconnect and reconnect: stop one emulator, send from the other, restart it, do nothing else
# ---------------------------------------------------------------------------------------------------------------------
function Test-Away {
    param([string]$Case, [string]$AwaySerial, [string]$SenderSerial, [string]$AvdGiven)
    $script:CaseTag = $Case
    $exe = Resolve-EmulatorExe
    $avd = Get-AvdName $AwaySerial $AvdGiven
    if (-not $exe) { Set-Result $Case 'NOT RUN' 'emulator executable not found (pass -EmulatorExe or set ANDROID_SDK_ROOT)'; return }
    if (-not $avd) { Set-Result $Case 'NOT RUN' "cannot read the AVD name of $AwaySerial (pass -PhoneAvd / -WatchAvd)"; return }
    $port = ($AwaySerial -replace '^emulator-', '')
    Write-Info "$Case : stopping $($Role[$AwaySerial]) emulator ($avd)"
    Invoke-Adb $AwaySerial @('emu', 'kill') | Out-Null
    if (-not (Wait-DeviceGone $AwaySerial 90)) { Set-Result $Case 'NOT RUN' "$AwaySerial did not go offline after 'adb emu kill'"; return }
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
    $bootDeadline = (Get-Date).AddSeconds($BootTimeoutSec)
    $up = $false
    while ((Get-Date) -lt $bootDeadline) {
        $devices = (Invoke-Adb '' @('devices')) -join "`n"
        if ($devices -match ([regex]::Escape($AwaySerial) + '\s+device')) { $up = Wait-DeviceBooted $AwaySerial 60; if ($up) { break } }
        Sleep-Poll
    }
    if (-not $up) { Set-Result $Case 'FAIL' "$AwaySerial did not finish booting within ${BootTimeoutSec}s"; return }
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
        Set-Result $Case 'FAIL' "no item arrived within ${ReconnectTimeoutSec}s after boot; the link did not recover on its own (what did you have to do? note it)"
    }
}
if ($SkipC5) {
    Set-Result 'C5-watch-away' 'NOT RUN' '-SkipC5'
    Set-Result 'C5-phone-away' 'NOT RUN' '-SkipC5'
} else {
    Test-Away 'C5-watch-away' $Watch $Phone $WatchAvd
    Sleep-Settle 5
    Test-Away 'C5-phone-away' $Phone $Watch $PhoneAvd
}

# ---------------------------------------------------------------------------------------------------------------------
# C2b after the reconnect: the DEL items from C1 must not reappear, the KEEP item must still be there
# ---------------------------------------------------------------------------------------------------------------------
$script:CaseTag = 'C2b'
if ($SkipC5 -or -not $Results.Contains('C5-phone-away') -or $Results['C5-phone-away'].Status -eq 'NOT RUN') {
    Set-Result 'C2b' 'NOT RUN' 'needs the reconnect from C5'
} else {
    Wake-Apps
    Clear-Logs
    Send-Command $Phone 'LIST_ITEMS'
    Send-Command $Watch 'LIST_ITEMS'
    Sleep-Settle 3
    $c2bFail = @()
    foreach ($serial in @($Phone, $Watch)) {
        $items = Wait-Spike $serial 'ITEMS count=' 20
        if (-not $items) { $c2bFail += "$($Role[$serial]): no ITEMS line"; continue }
        foreach ($id in $DelIds) { if ($items -match [regex]::Escape($id)) { $c2bFail += "$($Role[$serial]): deleted item $id reappeared" } }
        if ($KeepId -and $items -notmatch [regex]::Escape($KeepId)) { $c2bFail += "$($Role[$serial]): the KEEP item is gone" }
    }
    Save-CaseLogs
    if ($c2bFail.Count -eq 0) { Set-Result 'C2b' 'PASS' 'DEL items did not reappear; the KEEP item is still stored on both devices' } else { Set-Result 'C2b' 'FAIL' ($c2bFail -join '; ') }
}

# ---------------------------------------------------------------------------------------------------------------------
# Report (shape of docs/dev-setup-windows.md 6.4)
# ---------------------------------------------------------------------------------------------------------------------
function Get-Status([string]$Case) { if ($Results.Contains($Case)) { return $Results[$Case].Status } else { return 'NOT RUN' } }
function Get-Detail([string]$Case) { if ($Results.Contains($Case)) { return $Results[$Case].Detail } else { return '' } }
$c2Status = 'PASS'
foreach ($c in @('C2a', 'C2b')) { if ((Get-Status $c) -eq 'FAIL') { $c2Status = 'FAIL' } elseif ((Get-Status $c) -eq 'NOT RUN' -and $c2Status -eq 'PASS') { $c2Status = 'PARTIAL (' + $c + ' not run)' } }
$os = [System.Environment]::OSVersion.VersionString
$report = @()
$report += "Host OS / Android Studio version: $os, PowerShell $($PSVersionTable.PSVersion) / <fill in: Android Studio version, the script cannot see it>"
$report += "Phone image (name, API) / Watch image (name, API): $($Info[$Phone].Image) API $($Info[$Phone].Api) (AVD $($Info[$Phone].Avd)) / $($Info[$Watch].Image) API $($Info[$Watch].Api) (AVD $($Info[$Watch].Avd))"
$report += "C1 $(Get-Status 'C1')  $(Get-Detail 'C1')"
$report += "C2 $c2Status  C2a: $(Get-Status 'C2a') $(Get-Detail 'C2a') | C2b: $(Get-Status 'C2b') $(Get-Detail 'C2b')"
$report += "C3 swipe-away (am kill) $(Get-Status 'C3a'), force-stop $(Get-Status 'C3b')  a: $(Get-Detail 'C3a') | b: $(Get-Detail 'C3b')"
$report += "C4 $(Get-Status 'C4')  $(Get-Detail 'C4')"
$report += "C5 watch-away $(Get-Status 'C5-watch-away') ($(Get-Detail 'C5-watch-away')), phone-away $(Get-Status 'C5-phone-away') ($(Get-Detail 'C5-phone-away'))"
$report += 'Anything unexpected: see the per-case logs in the output folder (phone-<case>.log, watch-<case>.log).'
$report += ''
$report += 'Not automated / differences from the manual checklist:'
$report += '  - Pairing and Google sign-in are manual prerequisites; the script only checks that each side sees the other node.'
$report += '  - C3a uses HOME + "am kill" (kills the background app process), which is equivalent to but not identical to swiping the app away in Recents.'
$report += '  - C5 restarts the emulators with the emulator executable (normal boot, same AVD, same console port); whether Android Studio started them with other options is not reproduced.'
$report += '  - delayMs values compare two emulators'' clocks and are skew-contaminated; only the C4 round trips are delay measurements.'

$report | ForEach-Object { Write-Host $_ }
$report | Out-File -Encoding utf8 -FilePath (Join-Path $OutDir 'results.txt')
Write-Info "Results saved to $(Join-Path $OutDir 'results.txt')"

$failed = @($Results.Values | Where-Object { $_.Status -eq 'FAIL' }).Count
$notRun = @($Results.Values | Where-Object { $_.Status -eq 'NOT RUN' }).Count
if ($failed -gt 0) { exit 1 }
if ($notRun -gt 0) { exit 2 }
exit 0
