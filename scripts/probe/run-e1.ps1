# E1 unattended run (draft, not tested on a watch). Run it from the repository folder, with the watch connected and the
# debug build installed. About 35 minutes, one Enter in the middle. Keep the watch worn.
# It writes one result file and pushes it as the branch e1-results/<timestamp>; nothing else in the repository changes.
$S = "192.168.1.16:41613"
$env:ANDROID_SERIAL = $S
$pkg = "com.shlomoa.myadhdpal"
$P = "$pkg/.wear.debug.probe.ProbeReceiver"
$stamp = Get-Date -Format yyyyMMdd-HHmmss
$out = Join-Path $HOME "e1-$stamp.txt"

function Log($m) { $l = "$(Get-Date -Format HH:mm:ss) $m"; Write-Host $l; Add-Content $out $l }
function Rep($t) {
    adb connect $S | Out-Null
    adb logcat -c
    adb shell am broadcast -n $P --es cmd measure-report | Out-Null
    Start-Sleep 3
    Log "== report $t"
    adb logcat -d -s PROBE:I | ForEach-Object { Log $_ }
}
function Phase($t, $min) { Log "== phase $t for $min min"; Start-Sleep ($min * 60); Rep $t }

adb logcat -G 4M | Out-Null
Log "model: $(adb shell getprop ro.product.model)"
Log "battery before: $(adb shell dumpsys battery | Select-String 'level')"
$old = (adb shell settings get system screen_off_timeout).Trim()
Log "screen_off_timeout was $old"
adb shell settings put system screen_off_timeout 600000

adb shell am broadcast -n $P --es cmd measure-stop | Out-Null
foreach ($n in "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND", "android.permission.BODY_SENSORS_BACKGROUND") { adb shell pm revoke $pkg $n 2>$null }
foreach ($n in "android.permission.health.READ_HEART_RATE", "android.permission.POST_NOTIFICATIONS") { adb shell pm grant $pkg $n }
adb shell am broadcast -n $P --es cmd measure-clear | Out-Null
adb logcat -c
adb shell am start -n "$pkg/.wear.debug.probe.ProbeMeasureActivity" | Out-Null
for ($i = 0; $i -lt 60; $i++) { Start-Sleep 2; if ((adb logcat -d -s PROBE:I) -match 'availability AVAILABLE') { break } }
Log "start lines: $(adb logcat -d -s PROBE:I)"
Log "notification: $(adb shell dumpsys notification --noredact | Select-String 'probe_measure' | Select-Object -First 1)"

adb shell input keyevent KEYCODE_WAKEUP
Phase "A screen on" 10
adb shell input keyevent KEYCODE_SLEEP
Phase "B dim" 10
Read-Host "Now turn OFF Always-on display in the watch settings, then press Enter" | Out-Null
adb connect $S | Out-Null
adb shell input keyevent KEYCODE_SLEEP
Phase "C off" 10

adb shell am broadcast -n $P --es cmd measure-stop | Out-Null
adb shell settings put system screen_off_timeout $old
Log "battery after: $(adb shell dumpsys battery | Select-String 'level')"
Log "result file: $out"

# Publish the result as a branch without touching the working tree: a commit that holds only the result file.
$repo = (git rev-parse --show-toplevel).Trim()
$env:GIT_INDEX_FILE = Join-Path $env:TEMP "e1-$stamp.idx"
try {
    $blob = (git -C $repo hash-object -w $out).Trim()
    git -C $repo read-tree --empty
    git -C $repo update-index --add --cacheinfo "100644,$blob,e1-$stamp.txt"
    $tree = (git -C $repo write-tree).Trim()
} finally {
    Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue
}
$commit = (git -C $repo commit-tree $tree -m "E1 result $stamp").Trim()
git -C $repo push origin "${commit}:refs/heads/e1-results/$stamp"
if ($LASTEXITCODE -eq 0) { Write-Host "PUSHED e1-results/$stamp" } else { Write-Host "Push failed. The result is in $out; paste it to the coordinator." }
