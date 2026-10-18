# Shared by run-e1.ps1, run-e3.ps1 and run-e6.ps1 (dot-sourced): adb helpers, the result file, the service start and the report.
# Expects the calling script's parameters: $Watch, $OutDir, $ReportWaitSec, $AvailableTimeoutSec, $Unattended, $AllowCharging, $SkipPush.
# Untested on the watch (see the header of each run script); tested only against scripts/probe/fake/adb.

$ErrorActionPreference = 'Continue'
$pkg = 'com.shlomoa.myadhdpal'
$receiver = "$pkg/.wear.debug.probe.ProbeReceiver"
$title = 'Probe: measuring heart rate'

# Watch menu paths, verified by the coordinator against Samsung's support pages (names vary by firmware; samsung.com/us/support
# answers ANS10002951, ANS10002905, ANS10003398, ANS10002901). Shown in the prompts.
$PathSleep = 'Sleep mode (called Bedtime on older firmware): swipe down from the top of the watch face for the Quick panel, tap Modes, then Sleep; or Settings > Modes and Routines > Sleep > Turn on'
$PathPowerSaving = 'Power saving: the Power saving icon in the Quick panel (swipe down from the top of the watch face), or Settings > Battery > Power saving. It turns Wi-Fi off, Always On Display off and the wake-up gesture off, so wireless adb stops while it is on'
$PathTheater = 'Theater mode: the clapperboard icon in the Quick panel (swipe down from the top of the watch face), then pick a duration. It turns off alerts, Always On Display and wake gestures by itself'
$PathVibration = 'Settings > Sounds and vibration (strength Light/Strong, length Short/Long)'
$PathAod = 'Settings > Display > Always On Display'

function Log([string]$text) {
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $text
    Write-Host $line
    Add-Content -Path $script:LogFile -Value $line
}

# Runs adb; when the connection dropped (device offline, not found) reconnects and tries again, up to 3 times.
function Invoke-Adb([string[]]$AdbArgs) {
    for ($try = 1; $try -le 3; $try++) {
        $text = @(& adb @AdbArgs 2>&1 | ForEach-Object { "$_" })
        if ($LASTEXITCODE -eq 0 -or ($text -join ' ') -notmatch 'offline|no devices|not found|cannot connect|closed|unauthorized') { return $text }
        Log "adb dropped ($($AdbArgs -join ' ')): reconnecting, try $try"
        Connect-Watch
        Start-Sleep -Seconds 1
    }
    return $text
}

function Probe([string]$command) { Invoke-Adb @('shell', 'am', 'broadcast', '-n', $receiver, '--es', 'cmd', $command) | Out-Null }

function BatteryText { Invoke-Adb @('shell', 'dumpsys', 'battery') }

function BatteryLevel([string[]]$text) {
    $m = ($text -join "`n") | Select-String -Pattern 'level:\s*(\d+)'
    if ($m) { return [int]$m.Matches[0].Groups[1].Value }
    return -1
}

# Checks adb and the address, opens the result file <prefix>-<time>.txt and connects. Exits 3 on a setup problem.
function Initialize-Probe([string]$Prefix) {
    if (-not $Watch) { Write-Host 'Give the watch address: -Watch <ip:port>'; exit 3 }
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Write-Host 'adb is not on PATH'; exit 3 }
    $env:ANDROID_SERIAL = $Watch
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $script:LogFile = Join-Path $OutDir ("$Prefix-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
    Log "$Prefix result file: $(Split-Path -Leaf $script:LogFile)"
    Write-Host "result file: $script:LogFile"
    $script:UnreachableSec = 0
    $script:WatchIp = if (Test-MdnsName $Watch) { $null } else { ($Watch -split ':')[0] }
    if (-not (Test-MdnsName $Watch)) { & adb connect $Watch 2>&1 | Out-Null }
    # the device serial number is in the mDNS name; it is kept in memory only (never written to the result file)
    $sn = (& adb shell getprop ro.serialno 2>&1 | Out-String).Trim()
    $script:WatchSerialNo = if ($sn -match '^[A-Za-z0-9]{6,}$') { $sn } else { $null }
}

# Refuses a watch that is charging (the battery drop would be hidden); logs the battery level before. Exits 3 when refused.
function Test-NotCharging {
    $text = BatteryText
    if (-not $AllowCharging -and (($text -join "`n") -match 'powered:\s*true')) {
        Log 'The watch is on its charger. Take it off the charger (the battery drop would be hidden) or use -AllowCharging.'
        exit 3
    }
    Log ("battery before: " + (BatteryLevel $text))
}

function Ask([string]$question) {
    if ($Unattended) { Log "(unattended) $question"; return }
    Log $question
    Read-Host 'Press Enter when done' | Out-Null
}

# The mDNS service name of a watch in `adb devices` (adb-<serial>-<id>._adb-tls-connect._tcp): it does not change with the port.
function Test-MdnsName([string]$address) { return ($address -match '^adb-.+_adb-tls-connect\._tcp$') }

# adb connect to the current watch address (not for an mDNS name: that is not connectable).
function Connect-Watch { if (-not (Test-MdnsName $script:Watch)) { & adb connect $script:Watch 2>&1 | Out-Null } }

# One try to reach the watch at $address: adb connect (not for an mDNS name) and a trivial shell command. True when the watch answered.
function Test-Watch([string]$address) {
    if (-not (Test-MdnsName $address)) { & adb connect $address 2>&1 | Out-Null }
    $answer = (& adb -s $address shell echo ok 2>&1 | Out-String)
    return ($LASTEXITCODE -eq 0 -and $answer -match 'ok')
}

# Uses $address from now on: the watch address of this script, ANDROID_SERIAL for every adb call. Logs the change without the ip.
function Use-Watch([string]$address) {
    $old = $script:Watch
    $script:Watch = $address
    $env:ANDROID_SERIAL = $address
    if (Test-MdnsName $address) { Log 'address changed: now by mDNS name'; return }
    $newPort = ($address -split ':')[-1]
    if (Test-MdnsName $old) { Log "address changed: now on port $newPort" } else { Log ("address changed: port " + ($old -split ':')[-1] + " -> $newPort") }
}

# The wireless-debugging port can change when Wi-Fi goes off and on. Looks for the same ip with another port in `adb mdns services`
# and `adb devices`, then for the mDNS name of this watch (its serial number) in `adb devices`, and tries each. Returns true when the
# watch answered (it is then used for the rest of the run).
function Find-Watch {
    $seen = (& adb mdns services 2>&1 | Out-String) + (& adb devices 2>&1 | Out-String)
    if ($script:WatchIp) {
        $ports = [regex]::Matches($seen, [regex]::Escape($script:WatchIp) + ':(\d+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
        foreach ($port in $ports) {
            if ("$($script:WatchIp):$port" -eq $script:Watch) { continue }
            if (Test-Watch "$($script:WatchIp):$port") { Use-Watch "$($script:WatchIp):$port"; return $true }
        }
    }
    if ($script:WatchSerialNo) {
        $names = [regex]::Matches($seen, 'adb-' + [regex]::Escape($script:WatchSerialNo) + '-\S+_adb-tls-connect\._tcp') | ForEach-Object { $_.Value } | Select-Object -Unique
        foreach ($name in $names) {
            if ($name -eq $script:Watch) { continue }
            if (Test-Watch $name) { Use-Watch $name; return $true }
        }
    }
    return $false
}

# Asks ONCE per run for the new ip:port (not with -Unattended). A valid answer is tried; the typed text is never logged
# (it holds the ip address). Returns true when the watch answered at the typed address.
function Ask-Watch {
    if ($Unattended -or $script:AskedWatch) { return $false }
    $script:AskedWatch = $true
    Log 'The watch cannot be reached and was not found on another port.'
    $answer = Read-Host 'Which address does the watch show now (Settings > Developer options > Wireless debugging, ip:port)? Enter gives up'
    if ($answer -notmatch '^\d+\.\d+\.\d+\.\d+:\d+$') { Log 'no valid address given'; return $false }
    if (-not (Test-Watch $answer)) { Log 'the watch did not answer at the given address'; return $false }
    Use-Watch $answer
    return $true
}

# Tries to reach the watch over wireless adb: adb connect and a trivial shell command, every $ReconnectRetrySec seconds, up to
# $ReconnectSec seconds (defaults 5 and 120). When that fails it looks for the watch on another port (Find-Watch) and then asks the
# user once for the address (Ask-Watch). Returns true when the watch answered.
function Wait-Adb {
    $limit = if ($null -ne $ReconnectSec) { $ReconnectSec } else { 120 }
    $every = if ($null -ne $ReconnectRetrySec) { $ReconnectRetrySec } else { 5 }
    $attempts = [Math]::Max(1, [int][Math]::Ceiling($limit / [Math]::Max($every, 0.01)))
    if (Test-Watch $script:Watch) { return $true }
    $lost = Get-Date
    $found = $false
    for ($i = 2; $i -le $attempts -and -not $found; $i++) {
        Start-Sleep -Seconds $every
        $found = Test-Watch $script:Watch
    }
    if (-not $found) { $found = (Find-Watch) }
    if (-not $found) { $found = (Ask-Watch) }
    Add-Unreachable ((Get-Date) - $lost).TotalSeconds
    return $found
}

# Adds seconds to the time the watch was unreachable and logs the episode.
function Add-Unreachable([double]$seconds) {
    $script:UnreachableSec += $seconds
    Log ("watch was unreachable for " + [int][Math]::Round($seconds) + " s")
}

# A wait of $sec seconds cut into slices of $env:PROBE_KEEPALIVE_SEC (default 30): after each slice adb connect keeps the connection
# alive, and a watch that was lost is looked for (no question is asked here).
function Wait-Phase([double]$sec) {
    $slice = if ($env:PROBE_KEEPALIVE_SEC) { [double]$env:PROBE_KEEPALIVE_SEC } else { 30 }
    $end = (Get-Date).AddSeconds($sec)
    while ($true) {
        $left = ($end - (Get-Date)).TotalSeconds
        if ($left -le 0) { break }
        $began = Get-Date
        Start-Sleep -Seconds ([Math]::Min($left, $slice))
        if (((Get-Date) -ge $end)) { break }
        if (-not (Test-Watch $script:Watch)) {
            if (-not (Find-Watch)) { $script:UnreachableSec += ((Get-Date) - $began).TotalSeconds }
        }
    }
}

# Report when the watch may be unreachable (Wi-Fi off in a mode): waits for it, and logs that no report was possible instead of failing.
# Returns the reading count, or -1 when no report was possible.
function Report-Safe([string]$phase) {
    if (Wait-Adb) { return (Report $phase) }
    Log "no report possible during ${phase}: the watch could not be reached over adb"
    return -1
}

# Asks a question and returns the typed answer (E6). The answer is logged.
function Ask-Answer([string]$question) {
    Log $question
    $answer = Read-Host 'Your answer'
    Log "answer: $answer"
    return $answer
}

# Background permissions off, heart rate and notification on, a clean start of the service from the visible app.
# Returns when the service reported AVAILABLE; throws when it did not.
function Start-ProbeService {
    foreach ($p in 'android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND', 'android.permission.BODY_SENSORS_BACKGROUND') {
        if ($GrantBackgroundPermission) {
            Invoke-Adb @('shell', 'pm', 'grant', $pkg, $p) | Out-Null
        } else {
            Invoke-Adb @('shell', 'pm', 'revoke', $pkg, $p) | Out-Null
        }
    }
    if ($GrantBackgroundPermission) { Log 'background permission GRANTED by -GrantBackgroundPermission' }
    foreach ($p in 'android.permission.health.READ_HEART_RATE', 'android.permission.POST_NOTIFICATIONS') {
        Invoke-Adb @('shell', 'pm', 'grant', $pkg, $p) | Out-Null
    }
    Probe 'measure-stop'
    Probe 'measure-clear'
    Probe 'measure-capabilities'
    Start-Sleep -Seconds 1
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | Where-Object { $_ -match 'capabilities' } | ForEach-Object { Log $_ }
}

function Wait-ServiceAvailable {
    Invoke-Adb @('shell', 'am', 'start', '-n', "$pkg/.wear.debug.probe.ProbeMeasureActivity") | Out-Null
    $deadline = (Get-Date).AddSeconds($AvailableTimeoutSec)
    $available = $false
    while ((Get-Date) -lt $deadline -and -not $available) {
        $available = ((Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I')) -join "`n") -match 'measure availability .*\bAVAILABLE\b'
        if (-not $available) { Start-Sleep -Seconds 1 }
    }
    if (-not $available) { throw "the service did not report AVAILABLE within $AvailableTimeoutSec s (is the watch worn, is the permission granted?)" }
    Log 'service registered and AVAILABLE'
}

# Copies the live per-minute lines the service wrote since the last copy into the result file; call before logcat -c.
function Save-LiveLines {
    Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I') | Where-Object { $_ -match 'PROBE measure live' } |
        ForEach-Object { Log ('live: ' + $_) }
}

# adb connect, logcat -c, the report, then the new PROBE lines and whether the notification is listed.
# Returns the reading count of the "screen=all" line (-1 when there is none).
function Report([string]$phase) {
    Connect-Watch
    Save-LiveLines
    Invoke-Adb @('logcat', '-c') | Out-Null
    Probe 'measure-report'
    Start-Sleep -Seconds $ReportWaitSec
    Log "== report $phase"
    $lines = Invoke-Adb @('logcat', '-d', '-s', 'PROBE:I')
    $lines | ForEach-Object { Log $_ }
    $listed = (Invoke-Adb @('shell', 'dumpsys', 'notification', '--noredact')) -join "`n"
    Log ("notification visible: " + ($listed -match [regex]::Escape($title)))
    $m = ($lines -join "`n") | Select-String -Pattern 'screen=all readings=(\d+)'
    if ($m) { return [int]$m.Matches[0].Groups[1].Value }
    return -1
}

function Log-Permissions {
    Invoke-Adb @('shell', 'dumpsys', 'package', $pkg) | Where-Object { $_ -match 'READ_HEART_RATE|READ_HEALTH_DATA_IN_BACKGROUND|BODY_SENSORS_BACKGROUND' } |
        ForEach-Object { Log ('permission: ' + $_.Trim()) }
}

# The end of a run: stop the service, log the battery after.
function Stop-ProbeService {
    if (-not (Wait-Adb)) { Log 'the watch could not be reached over adb: the service may still be running; stop it on the watch' }
    Save-LiveLines
    Probe 'measure-stop'
    Log ("watch unreachable in total: " + [int][Math]::Round($script:UnreachableSec) + " s")
    Log ("battery after: " + (BatteryLevel (BatteryText)))
}

# Publishes the result file as the branch <prefix>-results/<stamp> holding ONLY that file (one commit, no parent), built with
# git plumbing and a temporary index, so the working tree, the index and HEAD of this checkout are not touched. On a failed
# push it prints the branch, the file and the command to retry; it never changes the exit code of the run.
function Publish-Result([string]$Prefix) {
    if ($SkipPush) { Write-Host "Not pushed (-SkipPush). Result file: $script:LogFile"; return }
    $name = Split-Path -Leaf $script:LogFile
    $stamp = $name -replace "^$Prefix-", '' -replace '\.txt$', ''
    $branch = "$Prefix-results/$stamp"
    $index = Join-Path ([IO.Path]::GetTempPath()) "$Prefix-$stamp.idx"
    $commit = ''
    try {
        $repo = (& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Out-String).Trim()
        if (-not $repo) { throw 'the script is not inside a git checkout' }
        if (-not (& git -C $repo config user.name)) {
            $env:GIT_AUTHOR_NAME = 'probe'; $env:GIT_AUTHOR_EMAIL = 'probe@invalid'
            $env:GIT_COMMITTER_NAME = 'probe'; $env:GIT_COMMITTER_EMAIL = 'probe@invalid'
        }
        $env:GIT_INDEX_FILE = $index
        try {
            $blob = (& git -C $repo hash-object -w $script:LogFile | Out-String).Trim()
            & git -C $repo update-index --add --cacheinfo "100644,$blob,$name" | Out-Null
            $tree = (& git -C $repo write-tree | Out-String).Trim()
        } finally {
            Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue
            Remove-Item $index -ErrorAction SilentlyContinue
        }
        $commit = (& git -C $repo commit-tree $tree -m "$Prefix result $stamp" | Out-String).Trim()
        $pushOutput = (& git -C $repo push origin "${commit}:refs/heads/$branch" 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) { throw "git push said: $pushOutput" }
        Write-Host "PUSHED $branch"
    } catch {
        Write-Host "Push failed ($_). The result file is $script:LogFile (branch $branch)."
        if ($commit) { Write-Host "Retry in the repository folder: git push origin ${commit}:refs/heads/$branch" }
        else { Write-Host "Retry by running the script again or tell the coordinator; send the result file." }
    }
}
