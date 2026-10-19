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
    if ($text -like '== *') { $script:CurrentStep = $text.Substring(3) }
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $text
    Write-Host $line
    Add-Content -Path $script:LogFile -Value $line
}

# Runs adb; when the connection dropped (device offline, not found) reconnects and tries again, up to 3 times.
function Invoke-Adb([string[]]$AdbArgs) {
    for ($try = 1; $try -le 3; $try++) {
        $text = @(Run-Adb $AdbArgs)
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
    if ($m) { $script:LastBattery = [int]$m.Matches[0].Groups[1].Value; return $script:LastBattery }
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
    $script:Steps = @()
    $script:Prefix = $Prefix
    $script:RunId = [IO.Path]::GetFileNameWithoutExtension($script:LogFile) -replace "^$Prefix-", ''
    $script:CurrentStep = 'start'
    $script:Waiting = $false
    $script:LastReadings = -1
    $script:LastBattery = -1
    $script:LastReachable = $true
    $script:LastHeartbeat = $null
    $script:Asked = 0
    $script:RunStart = Get-Date
    $script:RunDone = $false
    $script:PendingRead = $null
    $script:WatchIp = if (Test-MdnsName $Watch) { $null } else { ($Watch -split ':')[0] }
    if (-not (Test-MdnsName $Watch)) { Run-Adb @('connect', $Watch) | Out-Null }
    # the device serial number is in the mDNS name; it is kept in memory only (never written to the result file)
    $sn = ((Run-Adb @('shell', 'getprop', 'ro.serialno')) -join '').Trim()
    $script:WatchSerialNo = if ($sn -match '^[A-Za-z0-9]{6,}$') { $sn } else { $null }
}

# Runs a program with a hard time limit, no terminal prompt and its output captured. Returns @{ Code; Text }; Code -1 means it timed out
# and the process was killed. A hung adb.exe or git must never hang a run.
function Invoke-Native([string]$Exe, [string[]]$NativeArgs, [double]$TimeoutSec) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $Exe
    $psi.Arguments = ($NativeArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.EnvironmentVariables['GIT_TERMINAL_PROMPT'] = '0'
    try { $p = [System.Diagnostics.Process]::Start($psi) } catch { return @{ Code = 127; Text = "$Exe could not be started: $_" } }
    $out = $p.StandardOutput.ReadToEndAsync()
    $err = $p.StandardError.ReadToEndAsync()
    if (-not $p.WaitForExit([int]($TimeoutSec * 1000))) {
        try { $p.Kill() } catch { }
        return @{ Code = -1; Text = "timed out after $TimeoutSec s" }
    }
    $p.WaitForExit()
    return @{ Code = $p.ExitCode; Text = ($out.Result + $err.Result) }
}

function Invoke-GitTimeout([string[]]$GitArgs, [int]$TimeoutSec = 0) {
    if (-not $TimeoutSec) { $TimeoutSec = if ($env:PROBE_GIT_TIMEOUT_SEC) { [int]$env:PROBE_GIT_TIMEOUT_SEC } else { 30 } }
    return (Invoke-Native 'git' $GitArgs $TimeoutSec)
}

# adb with a hard time limit (PROBE_ADB_TIMEOUT_SEC, default 30). Returns the output lines and sets $LASTEXITCODE (-1 = timed out; logged,
# with the first words of the command only, so no address reaches the file).
function Run-Adb([string[]]$AdbArgs) {
    $sec = if ($env:PROBE_ADB_TIMEOUT_SEC) { [double]$env:PROBE_ADB_TIMEOUT_SEC } else { 30 }
    $r = Invoke-Native 'adb' $AdbArgs $sec
    $global:LASTEXITCODE = $r.Code
    if ($r.Code -eq -1) { Log ("adb timed out after $sec s (" + (($AdbArgs | Where-Object { $_ -notmatch '[:.]\d' } | Select-Object -First 3) -join ' ') + ")") }
    return @($r.Text -split "`r?`n" | Where-Object { $_ -ne '' })
}

# One commit without a parent that holds only $file (entry name $entry), built with git plumbing and a temporary index, so the working
# tree, the index and HEAD of the checkout are not touched. Returns the commit id.
function New-FileCommit([string]$repo, [string]$file, [string]$entry, [string]$message) {
    if (-not (& git -C $repo config user.name)) {
        $env:GIT_AUTHOR_NAME = 'probe'; $env:GIT_AUTHOR_EMAIL = 'probe@invalid'
        $env:GIT_COMMITTER_NAME = 'probe'; $env:GIT_COMMITTER_EMAIL = 'probe@invalid'
    }
    $index = Join-Path ([IO.Path]::GetTempPath()) ("probe-" + [Guid]::NewGuid().ToString('N') + '.idx')
    $env:GIT_INDEX_FILE = $index
    try {
        $blob = (& git -C $repo hash-object -w $file | Out-String).Trim()
        & git -C $repo update-index --add --cacheinfo "100644,$blob,$entry" | Out-Null
        $tree = (& git -C $repo write-tree | Out-String).Trim()
    } finally {
        Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue
        Remove-Item $index -ErrorAction SilentlyContinue
    }
    return (& git -C $repo commit-tree $tree -m $message | Out-String).Trim()
}

function Get-RepoRoot { return (& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Out-String).Trim() }

# Before a long run, everything that must be true is checked and ALL problems are reported in ONE message (exit 3, no step ran):
# watch reachable, not on the charger, battery at least PROBE_MIN_BATTERY (default 40) percent, the debug probe build installed,
# adb stable for PROBE_STABLE_SEC (default 30) seconds, the log buffer set to 4 MiB, a git push possible (dry run; not with -SkipPush).
function Test-Preflight {
    $problems = @()
    if (-not (Wait-Adb -NoAsk)) {
        $problems += 'the watch cannot be reached over adb (wireless debugging on? same Wi-Fi? port changed?)'
    } else {
        $text = BatteryText
        $level = BatteryLevel $text
        Log "battery before: $level"
        if (-not $AllowCharging -and (($text -join "`n") -match 'powered:\s*true')) {
            $problems += 'the watch is on its charger (take it off, the battery drop would be hidden, or use -AllowCharging)'
        }
        $min = if ($env:PROBE_MIN_BATTERY) { [int]$env:PROBE_MIN_BATTERY } else { 40 }
        if ($level -ge 0 -and $level -lt $min) { $problems += "the battery is $level % (this run needs $min % or more)" }
        $installed = (Invoke-Adb @('shell', 'dumpsys', 'package', $pkg)) -join "`n"
        $missing = @('ProbeReceiver', 'ProbeMeasureService', 'ProbeFrontActivity') | Where-Object { $installed -notmatch $_ }
        if ($missing) { $problems += ('the debug build of the probe is not installed or is older than this script (missing: ' + ($missing -join ', ') + ')') }
        $stable = if ($env:PROBE_STABLE_SEC) { [double]$env:PROBE_STABLE_SEC } else { 30 }
        $every = if ($env:PROBE_STABLE_EVERY_SEC) { [double]$env:PROBE_STABLE_EVERY_SEC } else { 5 }
        $until = (Get-Date).AddSeconds($stable)
        $checks = 0; $bad = 0
        do {
            $checks++
            $answer = (Run-Adb @('shell', 'echo', 'ok')) -join ' '
            if ($LASTEXITCODE -ne 0 -or $answer -notmatch 'ok') { $bad++ }
            if ((Get-Date) -lt $until) { Start-Sleep -Seconds $every }
        } while ((Get-Date) -lt $until)
        if ($bad -gt 0) { $problems += "adb is not stable ($bad of $checks checks failed in $stable s)" }
        Invoke-Adb @('logcat', '-G', '4M') | Out-Null
        $buffer = (Invoke-Adb @('logcat', '-g')) -join ' '
        if ($buffer -match '(\d+) MiB') { Log ("log buffer: " + $Matches[1] + " MiB") } else { $problems += 'the log buffer could not be set to 4 MiB' }
    }
    if (-not $SkipPush) {
        $repo = Get-RepoRoot
        if (-not $repo) { $problems += 'git push is not possible (the scripts are not inside a git checkout)' }
        else {
            $push = Invoke-GitTimeout @('-C', $repo, 'push', '--dry-run', 'origin', 'HEAD:refs/heads/probe-status/preflight')
            if ($push.Code -ne 0) { $problems += ('git push is not possible: ' + (($push.Text -split "`n" | Where-Object { $_ } | Select-Object -First 1))) }
        }
    }
    if ($problems.Count -gt 0) {
        Log ('PREFLIGHT FAILED: ' + ($problems -join '; '))
        exit 3
    }
    Log 'preflight: ok'
}

# --- Heartbeat: a tiny status file for remote monitoring -------------------------------------------------------------------------------
# Every PROBE_HEARTBEAT_SEC (default 300) seconds, and at the end with state finished, the file status.txt is pushed (force-updated, one
# commit, no history) to the branch probe-status/<script>-<run-id> with git plumbing, so the checkout is not touched. Never fails the run.
function Send-Heartbeat([string]$state = 'running') {
    if ($SkipPush -or -not $script:RunId) { return }
    $every = if ($env:PROBE_HEARTBEAT_SEC) { [double]$env:PROBE_HEARTBEAT_SEC } else { 300 }
    if ($state -eq 'running' -and $script:LastHeartbeat -and ((Get-Date) - $script:LastHeartbeat).TotalSeconds -lt $every) { return }
    $script:LastHeartbeat = Get-Date
    try {
        if ($state -eq 'running') {
            $script:LastReachable = (Test-Watch $script:Watch)
            if ($script:LastReachable) { BatteryLevel (BatteryText) | Out-Null }
        }
        $prompt = if ($script:Waiting) { 'yes' } else { 'no' }
        $reach = if ($script:LastReachable) { 'yes' } else { 'no' }
        $elapsed = [int]((Get-Date) - $script:RunStart).TotalSeconds
        $file = Join-Path ([IO.Path]::GetTempPath()) ("probe-status-$($script:Prefix)-$($script:RunId).txt")
        $lines = @("script=$($script:Prefix)", "run=$($script:RunId)", "step=$($script:CurrentStep)", "elapsed_sec=$elapsed",
            "waiting_on_prompt=$prompt", "watch_reachable=$reach", "last_reading_count=$($script:LastReadings)",
            "battery=$($script:LastBattery)", "state=$state", ("updated=" + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')))
        Set-Content -Path $file -Value $lines
        $repo = Get-RepoRoot
        if (-not $repo) { throw 'the script is not inside a git checkout' }
        $commit = New-FileCommit $repo $file 'status.txt' "probe status $($script:Prefix)-$($script:RunId) $state"
        Remove-Item $file -ErrorAction SilentlyContinue
        $branch = "probe-status/$($script:Prefix)-$($script:RunId)"
        $push = Invoke-GitTimeout @('-C', $repo, 'push', '--force', 'origin', "${commit}:refs/heads/$branch")
        if ($push.Code -ne 0) { throw ($push.Text -split "`n" | Where-Object { $_ } | Select-Object -First 1) }
        Log "heartbeat pushed: state=$state step=$($script:CurrentStep) waiting_on_prompt=$prompt watch_reachable=$reach readings=$($script:LastReadings) battery=$($script:LastBattery)"
    } catch {
        Log "heartbeat push failed: $_"
    }
}

# --- Always On Display, switched by adb when a candidate setting key exists --------------------------------------------------------
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

# --- Questions to the user: a time limit, a banner and a beep; a step that is not confirmed is marked UNCONFIRMED -------------------

function Beep {
    try { [Console]::Beep(880, 250) } catch { }
    Write-Host "`a" -NoNewline
}

# The run and wait time limits (seconds): PROBE_MAX_RUN_SEC for the whole run (default 10800), PROBE_MAX_STEP_SEC for one wait or
# question (default 3600). Throws when exceeded; the run script's catch and finally still stop the service and publish the file.
function Test-Limits {
    $maxRun = if ($env:PROBE_MAX_RUN_SEC) { [double]$env:PROBE_MAX_RUN_SEC } else { 10800 }
    if ($script:RunStart -and ((Get-Date) - $script:RunStart).TotalSeconds -gt $maxRun) { throw "run time limit of $maxRun s exceeded" }
}

function Test-StepLimit([datetime]$since) {
    $maxStep = if ($env:PROBE_MAX_STEP_SEC) { [double]$env:PROBE_MAX_STEP_SEC } else { 3600 }
    if (((Get-Date) - $since).TotalSeconds -gt $maxStep) { throw "phase time limit of $maxStep s exceeded" }
}

# Records how a step ended; the summary at the end of the file lists them.
function Set-Step([string]$name, [string]$state) {
    $script:Steps += [pscustomobject]@{ Name = $name; State = $state }
    Log "step ${name}: $state"
}

# One line typed by the user, or $null when nobody answered within PROBE_ASK_TIMEOUT_SEC (default 180) or the input is closed.
# While waiting: a banner, and a beep every PROBE_BEEP_SEC (default 30). A read that timed out is kept and answers the next question.
function Read-Answer {
    $timeout = if ($env:PROBE_ASK_TIMEOUT_SEC) { [double]$env:PROBE_ASK_TIMEOUT_SEC } else { 180 }
    $beepEvery = if ($env:PROBE_BEEP_SEC) { [double]$env:PROBE_BEEP_SEC } else { 30 }
    Write-Host ''
    Write-Host ("==== ACTION NEEDED: answer or press Enter within " + [int][Math]::Round($timeout) + " s, then the run continues without you ====")
    Beep
    $script:Waiting = $true
    # Console.In.ReadLineAsync blocks the caller on a redirected input, so the read runs in its own runspace.
    if (-not $script:PendingRead) {
        $script:PendingRead = [powershell]::Create()
        $null = $script:PendingRead.AddScript('[Console]::In.ReadLine()')
        $script:PendingAsync = $script:PendingRead.BeginInvoke()
    }
    $started = Get-Date
    $nextBeep = $started.AddSeconds($beepEvery)
    while (-not $script:PendingAsync.IsCompleted) {
        Start-Sleep -Milliseconds 200
        Test-Limits
        $now = Get-Date
        if ($now -ge $started.AddSeconds($timeout)) { $script:Waiting = $false; return $null }
        Send-Heartbeat
        if ($now -ge $nextBeep) { Beep; Write-Host '==== still waiting for you (see the question above) ===='; $nextBeep = $now.AddSeconds($beepEvery) }
    }
    $result = $script:PendingRead.EndInvoke($script:PendingAsync)
    $script:PendingRead.Dispose()
    $script:PendingRead = $null
    $line = if ($result.Count -gt 0) { [string]$result[0] } else { $null }
    $script:Waiting = $false
    return $line
}

# Asks the user to do something and press Enter. -Step names it in the summary. -Verify is a script block that checks by adb that the
# user really did it (true = done); when it fails the user is asked again, twice at most, with -VerifyHint. Never blocks for ever.
function Ask([string]$question, [string]$Step = '', [scriptblock]$Verify = $null, [string]$VerifyHint = '') {
    if (-not $Step) { $script:Asked++; $Step = "question-$($script:Asked)" }
    if ($Unattended) { Log "(unattended) $question"; Set-Step $Step 'UNCONFIRMED (unattended, not asked)'; return }
    $tries = if ($Verify) { 3 } else { 1 }
    for ($t = 1; $t -le $tries; $t++) {
        Log $question
        $line = Read-Answer
        if ($null -eq $line) { Log 'no answer, continued'; Set-Step $Step 'UNCONFIRMED (no answer)'; return }
        if (-not $Verify) { Set-Step $Step 'ANSWERED (not verifiable by adb)'; return }
        if (& $Verify) { Set-Step $Step 'CONFIRMED (the watch is reachable)'; return }
        $question = "adb cannot reach the watch yet: $VerifyHint Do it, wait until Wi-Fi is back, then press Enter."
    }
    Log 'no confirmation by adb, continued'
    Set-Step $Step 'UNCONFIRMED (adb cannot reach the watch after the answer)'
}

# The end of the file: how each step ended.
function Write-Summary([bool]$failed) {
    foreach ($st in $script:Steps) { Log ("summary: step " + $st.Name + ": " + $st.State) }
    $open = @($script:Steps | Where-Object { $_.State -like 'UNCONFIRMED*' }).Count
    Log "summary: unconfirmed steps: $open"
    $result = if ($failed) { 'FAILED' } elseif (-not $script:RunDone) { 'INTERRUPTED' } else { 'completed' }
    Log "summary: run $result"
}

# The last thing a run does, in its finally: the summary, then the result file is published (also after an error, a time limit or Ctrl+C).
function Finish-Run([string]$Prefix, [bool]$failed) {
    if (-not $script:RunDone -and -not $failed) { Log 'the run was interrupted (Ctrl+C) before it was complete' }
    Write-Summary $failed
    Publish-Result $Prefix
    Send-Heartbeat 'finished'
}


# The mDNS service name of a watch in `adb devices` (adb-<serial>-<id>._adb-tls-connect._tcp): it does not change with the port.
function Test-MdnsName([string]$address) { return ($address -match '^adb-.+_adb-tls-connect\._tcp$') }

# adb connect to the current watch address (not for an mDNS name: that is not connectable).
function Connect-Watch { if (-not (Test-MdnsName $script:Watch)) { Run-Adb @('connect', $script:Watch) | Out-Null } }

# One try to reach the watch at $address: adb connect (not for an mDNS name) and a trivial shell command. True when the watch answered.
function Test-Watch([string]$address) {
    if (-not (Test-MdnsName $address)) { Run-Adb @('connect', $address) | Out-Null }
    $answer = (Run-Adb @('-s', $address, 'shell', 'echo', 'ok')) -join ' '
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
    $seen = ((Run-Adb @('mdns', 'services')) -join "`n") + "`n" + ((Run-Adb @('devices')) -join "`n")
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
    Write-Host 'Which address does the watch show now (Settings > Developer options > Wireless debugging, ip:port)? Enter gives up'
    $answer = Read-Answer
    if ($null -eq $answer -or $answer -notmatch '^\d+\.\d+\.\d+\.\d+:\d+$') { Log 'no valid address given'; return $false }
    if (-not (Test-Watch $answer)) { Log 'the watch did not answer at the given address'; return $false }
    Use-Watch $answer
    return $true
}

# Tries to reach the watch over wireless adb: adb connect and a trivial shell command, every $ReconnectRetrySec seconds, up to
# $ReconnectSec seconds (defaults 5 and 120). When that fails it looks for the watch on another port (Find-Watch) and then asks the
# user once for the address (Ask-Watch). Returns true when the watch answered.
function Wait-Adb([switch]$NoAsk) {
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
    if (-not $found -and -not $NoAsk) { $found = (Ask-Watch) }
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
    $since = Get-Date
    while ($true) {
        $left = ($end - (Get-Date)).TotalSeconds
        if ($left -le 0) { break }
        $began = Get-Date
        Start-Sleep -Seconds ([Math]::Min($left, $slice))
        Test-Limits
        Test-StepLimit $since
        Send-Heartbeat
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

# Asks a question and returns the typed answer (E6, E4); '' when nobody answered. The answer is logged.
function Ask-Answer([string]$question, [string]$Step = '') {
    if (-not $Step) { $script:Asked++; $Step = "question-$($script:Asked)" }
    if ($Unattended) { Log "(unattended) $question"; Set-Step $Step 'UNCONFIRMED (unattended, not asked)'; return '' }
    Log $question
    $answer = Read-Answer
    if ($null -eq $answer) { Log 'no answer, continued'; Set-Step $Step 'UNCONFIRMED (no answer)'; return '' }
    Log "answer: $answer"
    Set-Step $Step 'ANSWERED'
    return $answer
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

# The window that has the focus (dumpsys window), one line, or unknown.
function Get-WindowFocus {
    $line = (Invoke-Adb @('shell', 'dumpsys', 'window')) | Where-Object { $_ -match 'mCurrentFocus' } | Select-Object -First 1
    if ($line) { return $line.Trim() }
    return 'unknown'
}

# The vibrator service's lines (dumpsys vibrator_manager) that mention vibrations; a new line after a command means it vibrated.
function Get-VibratorLines {
    return @(Invoke-Adb @('shell', 'dumpsys', 'vibrator_manager') | Where-Object { $_ -match 'ibrat' } | ForEach-Object { $_.Trim() })
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
    if ($m) { $script:LastReadings = [int]$m.Matches[0].Groups[1].Value; return $script:LastReadings }
    Log "report $phase failed: no reading count in the report"
    return -1
}

function Log-Permissions {
    Invoke-Adb @('shell', 'dumpsys', 'package', $pkg) | Where-Object { $_ -match 'READ_HEART_RATE|READ_HEALTH_DATA_IN_BACKGROUND|BODY_SENSORS_BACKGROUND' } |
        ForEach-Object { Log ('permission: ' + $_.Trim()) }
}

# The end of a run: stop the service, log the battery after.
function Stop-ProbeService {
    if (-not (Wait-Adb -NoAsk)) { Log 'the watch could not be reached over adb: the service may still be running; stop it on the watch' }
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
    $commit = ''
    try {
        $repo = Get-RepoRoot
        if (-not $repo) { throw 'the script is not inside a git checkout' }
        $commit = New-FileCommit $repo $script:LogFile $name "$Prefix result $stamp"
        $push = Invoke-GitTimeout @('-C', $repo, 'push', 'origin', "${commit}:refs/heads/$branch")
        if ($push.Code -ne 0) { throw "git push said: $($push.Text)" }
        Write-Host "PUSHED $branch"
    } catch {
        Write-Host "Push failed ($_). The result file is $script:LogFile (branch $branch)."
        if ($commit) { Write-Host "Retry in the repository folder: git push origin ${commit}:refs/heads/$branch" }
        else { Write-Host "Retry by running the script again or tell the coordinator; send the result file." }
    }
}
