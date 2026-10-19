<#
.SYNOPSIS
  The stress demo (milestone M1) with ONE command and no prompts: heart rate -> baseline comparison -> stress event -> local response on
  the watch -> alert on the phone, as a PASS/FAIL timeline with the time of each stage, published as demo-results/<stamp>.

.DESCRIPTION
  Works with Windows PowerShell 5.1 and PowerShell 7; needs adb and git on PATH, the debug apps built (.\gradlew.bat :wear:assembleDebug
  :phone:assembleDebug) and the watch worn, awake and in front of you with the screen on (the Monitoring screen keeps it on). The
  phone must be unlocked. Nothing is asked and nothing has to be tapped. Stages (each PASS or FAIL, with its time):

    preflight     the watch is reachable, not on the charger, battery at least 40 %, adb stable; the phone answers
    grants        heart-rate and notification permission on the watch, notification and full-screen alert on the phone (by adb)
    monitoring    the Monitoring screen is started on the watch; the foreground health service registers the heart-rate callback
    baseline      a resting history is fed to the same evaluator, so the baseline is known (the debug injector; see below)
    reading       a reading reaches the evaluator
    event         a reading above baseline + offset starts a stress episode (the injected rising series; with -Real: your own heart rate,
                  the demo waits up to -WaitSec seconds for it and injects nothing)
    response      the local response on the watch (vibration; the screen shows the state) follows the event
    watch-alert   the watch sends the urgent alert
    phone-alert   the phone stored the alert and raised it (full-screen alarm and sound), found by the time of the reading
    episode-end   "I'm OK" on the watch ends the episode

  The injector feeds synthetic readings into the SAME code path as the real heart-rate callback (the debug build's broadcast receiver
  .debug.monitor.MonitorDemoReceiver); it is not a mock. Honest limits: it works with the screen ON (experiment E2: with the screen dim or
  off the readings arrive in bursts, minutes late; that is a separate follow-up); the baseline is injected (a real one needs 24 hours of
  wear); the two phone and watch clocks are compared to the second only, so the phone latency is good to about one second.

  Hard time limit on every adb and git call; the heartbeat, the summary and the publishing work as for the probe runs. The file holds no
  ip address, no device serial and no local path, and no heart-rate value. Exit code: 0 all stages PASS, 1 a stage FAILED, 3 setup problem.

  Untested on the real devices: the script is tested only against a simulated adb (scripts/demo/test-stress-demo.sh).

.EXAMPLE
  .\scripts\demo\run-stress-demo.ps1 -Phone R58W20ABCDE -Watch adb-RFAX40W9RMN-h37Tsh._adb-tls-connect._tcp
.EXAMPLE
  .\scripts\demo\run-stress-demo.ps1 -Phone R58W20ABCDE -Watch 192.168.1.16:41613 -Real -WaitSec 300
#>
[CmdletBinding()]
param(
    # adb serial of the phone (adb devices).
    [Parameter(Mandatory = $true)][string]$Phone,
    # ip:port of the watch, or its mDNS name (adb-<serial>-<id>._adb-tls-connect._tcp).
    [string]$Watch = $env:ANDROID_SERIAL,
    [string]$OutDir = (Join-Path $HOME 'probe-results'),
    # Your own heart rate instead of the injected series: wait for a real stress event.
    [switch]$Real,
    # How long to wait for the event (injected series: after it ended; -Real: from the start).
    [double]$WaitSec = 20,
    [int]$BaselineBpm = 62,
    [int]$FromBpm = 62,
    [int]$ToBpm = 125,
    [int]$RiseSeconds = 20,
    [double]$AvailableTimeoutSec = 30,
    [double]$ReportWaitSec = 1,
    [double]$ReconnectSec = 60,
    [double]$ReconnectRetrySec = 5,
    [switch]$SkipInstall,
    [switch]$AllowCharging,
    [switch]$SkipPush
)

$Unattended = $true
. (Join-Path $PSScriptRoot '..\probe\probe-common.ps1')

$wearApk = Join-Path $PSScriptRoot '..\..\wear\build\outputs\apk\debug\wear-debug.apk'
$phoneApk = Join-Path $PSScriptRoot '..\..\phone\build\outputs\apk\debug\phone-debug.apk'
$monitorActivity = "$pkg/.wear.monitor.MonitoringActivity"
$injector = "$pkg/.debug.monitor.MonitorDemoReceiver"
$poll = if ($env:PROBE_POLL_SEC) { [double]$env:PROBE_POLL_SEC } else { 1 }
$script:StageAt = Get-Date
$script:StageFailed = $false

# One stage: PASS or FAIL (or NOT RUN when an earlier stage failed), the time it was decided and the time since the previous stage.
function Stage([string]$name, [bool]$ok, [string]$detail = '') {
    $now = Get-Date
    $ms = [int]($now - $script:StageAt).TotalMilliseconds
    $script:StageAt = $now
    $state = if ($ok) { 'PASS' } else { 'FAIL' }
    if (-not $ok) { $script:StageFailed = $true }
    Log ("stage ${name}: $state (at " + $now.ToString('HH:mm:ss.fff') + ", +$ms ms) $detail".TrimEnd())
    Set-Step $name $state
}

function Stage-NotRun([string]$name, [string]$why) {
    Log "stage ${name}: NOT RUN ($why)"
    Set-Step $name "NOT RUN ($why)"
}

# The first line of the DEMO log of $serial (the watch when empty) that matches $pattern, waiting up to $sec seconds; $null when none.
function Wait-Demo([string]$pattern, [double]$sec, [string]$serial = '') {
    $until = (Get-Date).AddSeconds($sec)
    do {
        $lines = if ($serial) { Run-Adb @('-s', $serial, 'logcat', '-d', '-s', 'DEMO:I') } else { Invoke-Adb @('logcat', '-d', '-s', 'DEMO:I') }
        $hit = $lines | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($hit) { return $hit }
        Start-Sleep -Seconds $poll
    } while ((Get-Date) -lt $until)
    return $null
}

function Field([string]$line, [string]$name) {
    if ($line -match "\b$name=(\S+)") { return $Matches[1] }
    return $null
}

function Inject([string[]]$more) {
    Invoke-Adb (@('shell', 'am', 'broadcast', '-n', $injector) + $more) | Out-Null
}

# ---------------------------------------------------------------- run
Initialize-Probe 'demo'
Test-Preflight
$phoneAnswer = (Run-Adb @('-s', $Phone, 'shell', 'echo', 'ok')) -join ' '
Stage 'preflight' ($phoneAnswer -match 'ok') 'watch and phone answer'
$completed = $false
try {
    if ($phoneAnswer -notmatch 'ok') { throw 'the phone does not answer over adb' }
    if (-not $SkipInstall) {
        foreach ($t in @(@($null, $wearApk), @($Phone, $phoneApk))) {
            if (-not (Test-Path $t[1])) { throw "build the apps first: .\gradlew.bat :wear:assembleDebug :phone:assembleDebug ($(Split-Path -Leaf $t[1]) is missing)" }
            $a = @('install', '-r', '-g', $t[1])
            if ($t[0]) { Run-Adb (@('-s', $t[0]) + $a) | Out-Null } else { Invoke-Adb $a | Out-Null }
        }
        Log 'apps installed'
    }

    # grants: the watch by adb; the phone's two grants are standard Android 13/14 commands that are not yet confirmed on the A55
    foreach ($p in 'android.permission.health.READ_HEART_RATE', 'android.permission.POST_NOTIFICATIONS') {
        Invoke-Adb @('shell', 'pm', 'grant', $pkg, $p) | Out-Null
    }
    Run-Adb @('-s', $Phone, 'shell', 'pm', 'grant', $pkg, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    Run-Adb @('-s', $Phone, 'shell', 'appops', 'set', $pkg, 'USE_FULL_SCREEN_INTENT', 'allow') | Out-Null
    $check = (Run-Adb @('-s', $Phone, 'shell', 'appops', 'get', $pkg, 'USE_FULL_SCREEN_INTENT')) -join ' '
    Log ('phone full-screen alert: ' + $check.Trim())
    Log ('phone notification permission: ' + ((Run-Adb @('-s', $Phone, 'shell', 'dumpsys', 'package', $pkg)) | Where-Object { $_ -match 'POST_NOTIFICATIONS' } | Select-Object -First 1))
    Stage 'grants' $true 'by adb (the phone checks are printed, not enforced)'

    # monitoring: wake the watch, start the Monitoring screen (it starts the health service), clean logs on both
    Invoke-Adb @('logcat', '-c') | Out-Null
    Run-Adb @('-s', $Phone, 'logcat', '-c') | Out-Null
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-n', $monitorActivity) | Out-Null
    $started = Wait-Demo 'monitor started registered=true' $AvailableTimeoutSec
    Stage 'monitoring' ($null -ne $started) 'the health service registered the heart-rate callback'
    if ($null -eq $started) { throw 'the Monitoring service did not register (heart-rate permission? the watch worn?)' }

    if (-not $Real) {
        Inject @('--es', 'cmd', 'baseline', '--ei', 'bpm', "$BaselineBpm")
        $known = Wait-Demo 'inject baseline fed' $AvailableTimeoutSec
        Inject @('--es', 'cmd', 'status')
        $status = Wait-Demo 'monitor status .*baselineKnown=true' $AvailableTimeoutSec
        Stage 'baseline' (($null -ne $known) -and ($null -ne $status)) 'resting history fed; the baseline is known'
    } else {
        Log 'stage baseline: SKIPPED with -Real (the real baseline needs 24 hours of wear)'
        Set-Step 'baseline' 'SKIPPED (-Real)'
    }

    $first = Wait-Demo 'monitor first reading at=' $AvailableTimeoutSec
    if (-not $Real) {
        Inject @('--es', 'cmd', 'series', '--ei', 'from', "$FromBpm", '--ei', 'to', "$ToBpm", '--ei', 'seconds', "$RiseSeconds")
    }
    Stage 'reading' ($null -ne $first) 'a reading reached the evaluator'

    $limit = if ($Real) { $WaitSec } else { $RiseSeconds + $WaitSec }
    $event = Wait-Demo 'monitor event level=' $limit
    Stage 'event' ($null -ne $event) 'a reading above baseline + offset started an episode'
    if ($null -eq $event) {
        foreach ($n in 'response', 'watch-alert', 'phone-alert', 'episode-end') { Stage-NotRun $n 'no stress event' }
    } else {
        $readingAt = [int64](Field $event 'at')
        $response = Wait-Demo 'monitor response level=' $AvailableTimeoutSec
        Stage 'response' ($null -ne $response) 'vibration and the Stress state on the screen'
        $sent = Wait-Demo 'monitor alert sent level=' $AvailableTimeoutSec
        Stage 'watch-alert' ($null -ne $sent) 'the urgent alert was handed to the link'

        # the phone: ask for the dump, then find the alert by the time of the reading (the alert carries the reading's time)
        $watchSec = [int64](((Invoke-Adb @('shell', 'date', '+%s')) -join '').Trim())
        $phoneSec = [int64](((Run-Adb @('-s', $Phone, 'shell', 'date', '+%s')) -join '').Trim())
        $offsetMs = ($phoneSec - $watchSec) * 1000
        $raised = $null
        $until = (Get-Date).AddSeconds($AvailableTimeoutSec)
        do {
            Run-Adb @('-s', $Phone, 'shell', 'am', 'broadcast', '-a', 'com.shlomoa.myadhdpal.debug.DUMP_ALERTS',
                '-n', "$pkg/.phone.debug.DemoAlertsReceiver") | Out-Null
            $lines = Run-Adb @('-s', $Phone, 'logcat', '-d', '-s', 'DEMO:I')
            $stored = $lines | Where-Object { $_ -match "alert stored .*watchAt=$readingAt\b" } | Select-Object -First 1
            if ($stored) {
                $id = Field $stored 'id'
                $raised = $lines | Where-Object { $_ -match "alert raised id=$([regex]::Escape($id)) .*raisedAt=\d+" } | Select-Object -First 1
            }
            if (-not $raised) { Start-Sleep -Seconds $poll }
        } while (-not $raised -and (Get-Date) -lt $until)
        Stage 'phone-alert' ($null -ne $raised) 'the phone stored the alert and raised it'

        Inject @('--es', 'cmd', 'im-ok')
        $ended = Wait-Demo 'monitor episode ended' $AvailableTimeoutSec
        Stage 'episode-end' ($null -ne $ended) '"I''m OK" on the watch ended the episode'

        $respAt = if ($response) { [int64](Field $response 'at') } else { $null }
        $sentAt = if ($sent) { [int64](Field $sent 'at') } else { $null }
        if ($respAt) { Log "timeline: reading to response $($respAt - $readingAt) ms" }
        if ($sentAt) { Log "timeline: reading to alert sent $($sentAt - $readingAt) ms" }
        if ($raised) {
            $raisedAt = [int64](Field $raised 'raisedAt')
            Log "timeline: phone latency (alert raised minus reading time, clocks compared to the second) $($raisedAt - $readingAt - $offsetMs) ms"
        }
    }
    $completed = $true
    $script:RunDone = $true
} catch {
    Log "FAILED: $_"
    $script:StageFailed = $true
} finally {
    try { Invoke-Adb @('shell', 'am', 'force-stop', $pkg) | Out-Null } catch { }
    Log "result file: $(Split-Path -Leaf $script:LogFile)"
    Finish-Run 'demo' ($script:StageFailed -or -not $completed)
}
if ($script:StageFailed -or -not $completed) { exit 1 }
exit 0
