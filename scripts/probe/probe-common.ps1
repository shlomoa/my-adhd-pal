# Shared by run-e1.ps1, run-e3.ps1 and run-e6.ps1 (dot-sourced): adb helpers, the result file, the service start and the report.
# Expects the calling script's parameters: $Watch, $OutDir, $ReportWaitSec, $AvailableTimeoutSec, $Unattended, $AllowCharging, $SkipPush.
# Untested on the watch (see the header of each run script); tested only against scripts/probe/fake/adb.

$ErrorActionPreference = 'Continue'
$pkg = 'com.shlomoa.myadhdpal'
$receiver = "$pkg/.wear.debug.probe.ProbeReceiver"
$title = 'Probe: measuring heart rate'

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
        & adb connect $Watch 2>&1 | Out-Null
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
    & adb connect $Watch 2>&1 | Out-Null
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
        Invoke-Adb @('shell', 'pm', 'revoke', $pkg, $p) | Out-Null
    }
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

# adb connect, logcat -c, the report, then the new PROBE lines and whether the notification is listed.
# Returns the reading count of the "screen=all" line (-1 when there is none).
function Report([string]$phase) {
    & adb connect $Watch 2>&1 | Out-Null
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
    & adb connect $Watch 2>&1 | Out-Null
    Probe 'measure-stop'
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
