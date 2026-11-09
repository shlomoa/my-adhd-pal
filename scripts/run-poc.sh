#!/usr/bin/env bash
# One-command POC run, about 7 minutes (the limit is 10). Run on the computer the watch and phone are connected to over adb.
#
#   scripts/run-poc.sh            run everything, commit and push the results to the current branch
#   scripts/run-poc.sh --no-push  run everything, keep the results local
#   scripts/run-poc.sh --smoke    about 2 minutes: only the screen-on phase of each route, with diagnostics
#
# What it does: builds the APKs, installs them, grants the watch permissions, then
#   1. latency matrix: routes r1 (MeasureClient) and r2 (wake-up sensor) x screen states on / dim / off,
#      PHASE_SECONDS each (default 40), pulse sent to the phone live;
#   2. control checks (goal 2): enable, disable, wake lock hold/release, route and batching modes;
#   3. pulls the logs and summaries from both devices into poc-results/<timestamp>/ and writes report.txt.
# Screen states are driven through adb; the state the watch actually reached is recorded, never assumed.
# Environment: WATCH=<serial> PHONE=<serial> to choose devices, PHASE_SECONDS, ADB (adb binary).
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

ADB="${ADB:-adb}"
PHASE_SECONDS="${PHASE_SECONDS:-40}"
smoke=0
PKG=com.shlomoa.myadhdpal.poc
WACT="$PKG/$PKG.wear.ControlActivity"
PACT="$PKG/$PKG.phone.MainActivity"
PREC="$PKG/$PKG.phone.SummaryReceiver"
push=1
for arg in "$@"; do
  case "$arg" in
    --no-push) push=0 ;;
    --smoke) smoke=1; PHASE_SECONDS=20 ;;
  esac
done

started=$(date +%s)
stamp=$(date +%Y%m%d-%H%M%S)
out="poc-results/$stamp"
mkdir -p "$out/watch" "$out/phone"
report="$out/report.txt"
say() { echo "$*" | tee -a "$report"; }

# ---- devices -------------------------------------------------------------------------------------------------
W="${WATCH:-}"
P="${PHONE:-}"
for s in $("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'); do
  chars=$("$ADB" -s "$s" shell getprop ro.build.characteristics | tr -d '\r')
  case "$chars" in
    *watch*) [ -n "$W" ] || W=$s ;;
    *) [ -n "$P" ] || P=$s ;;
  esac
done
[ -n "$W" ] && [ -n "$P" ] || { echo "Need one watch and one phone in state 'device' (found watch='$W' phone='$P')"; exit 2; }
w() { "$ADB" -s "$W" "$@"; }
p() { "$ADB" -s "$P" "$@"; }
say "ADHD Pal POC run $stamp"
say "watch: $W $(w shell getprop ro.product.model | tr -d '\r') Android $(w shell getprop ro.build.version.release | tr -d '\r') (API $(w shell getprop ro.build.version.sdk | tr -d '\r'))"
say "phone: $P $(p shell getprop ro.product.model | tr -d '\r') Android $(p shell getprop ro.build.version.release | tr -d '\r') (API $(p shell getprop ro.build.version.sdk | tr -d '\r'))"

# ---- build, install, permissions -----------------------------------------------------------------------------------
WAPK=wear/build/outputs/apk/debug/wear-debug.apk
PAPK=phone/build/outputs/apk/debug/phone-debug.apk
# Always build: a stale APK from an earlier project in the same folders has a different package name.
say "building APKs (incremental)"
./gradlew :wear:assembleDebug :phone:assembleDebug -q || { say "BUILD FAILED"; exit 3; }
install_app() { # install_app <serial> <apk> <name>
  local result
  result=$("$ADB" -s "$1" install -r -t "$2" 2>&1 | tr -d '\r' | tail -3)
  say "$3 install: $(echo "$result" | tr '\n' ' ')"
  "$ADB" -s "$1" shell pm list packages "$PKG" 2>/dev/null | tr -d '\r' | grep -q "package:$PKG" \
    || { say "$3: package $PKG is not installed after the install above; stopping"; exit 3; }
}
install_app "$W" "$WAPK" watch
install_app "$P" "$PAPK" phone
# Fresh logs for this run.
w shell run-as $PKG sh -c 'rm -f files/*' >/dev/null 2>&1
p shell run-as $PKG sh -c 'rm -f files/*' >/dev/null 2>&1
for perm in android.permission.BODY_SENSORS android.permission.health.READ_HEART_RATE \
  android.permission.BODY_SENSORS_BACKGROUND android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND \
  android.permission.POST_NOTIFICATIONS; do
  if res=$(w shell pm grant $PKG "$perm" 2>&1 | tr -d '\r'); [ -z "$res" ]; then
    say "granted $perm"
  else
    say "not granted: $perm: $(echo "$res" | head -1)"
  fi
done
p shell am start -n "$PACT" >/dev/null 2>&1 # the phone receiver process must exist

# ---- helpers ---------------------------------------------------------------------------------------------------------
orig_timeout=$(w shell settings get system screen_off_timeout | tr -d '\r')
restore() { w shell settings put system screen_off_timeout "${orig_timeout:-30000}" >/dev/null 2>&1; }
trap restore EXIT
wcmd() { w shell am start -n "$WACT" "$@" >/dev/null 2>&1; }
status() { w shell run-as $PKG cat files/status.txt 2>/dev/null | tr -d '\r'; }
field() { echo "$1" | tr ' ' '\n' | sed -n "s/^$2=//p" | head -1; }
observe() {
  local wake disp
  wake=$(w shell dumpsys power | tr -d '\r' | grep -m1 -o 'mWakefulness=[A-Za-z]*')
  disp=$(w shell dumpsys display | tr -d '\r' | grep -m1 -o -i 'mScreenState=[A-Z_]*\|Display State=[A-Z_]*')
  echo "${wake:-wakefulness=?} ${disp:-display=?}"
}
screen_on() { w shell settings put system screen_off_timeout 600000 >/dev/null 2>&1; w shell input keyevent KEYCODE_WAKEUP; }

# ---- collect (also used for an early stop) --------------------------------------------------------------------------------
diag() { # diag <name>: what the system says about our app and the heart-rate sensor, saved in the results
  {
    echo "## dumpsys package (permissions)"
    w shell dumpsys package $PKG | tr -d '\r' | grep -E "READ_HEART|BODY_SENSORS|READ_HEALTH|POST_NOTIF" | head -20
    echo "## dumpsys sensorservice (heart rate, our package)"
    w shell dumpsys sensorservice | tr -d '\r' | grep -i -E "heart|$PKG" | head -30
    echo "## dumpsys activity services (our service)"
    w shell dumpsys activity services $PKG | tr -d '\r' | grep -i -E "ServiceRecord|isForeground|foregroundServiceType|app=" | head -10
    echo "## display / power"
    observe
  } >"$out/diag-$1.txt" 2>&1
}
collect() {
  say ""
  say "== results"
  p shell am broadcast -n "$PREC" -a com.shlomoa.myadhdpal.poc.SUMMARIZE >/dev/null 2>&1
  sleep 3
  w logcat -d -s POC 2>/dev/null | tr -d '\r' | tail -300 >"$out/watch/logcat-poc.txt"
  for side in watch phone; do
    if [ "$side" = watch ]; then dev=$W; else dev=$P; fi
    for f in $("$ADB" -s "$dev" shell run-as $PKG ls files 2>/dev/null | tr -d '\r'); do
      "$ADB" -s "$dev" shell run-as $PKG cat "files/$f" 2>/dev/null | tr -d '\r' >"$out/$side/$f"
    done
  done
  for f in "$out"/watch/watch-summary-*.txt "$out"/phone/phone-summary-*.txt; do
    [ -f "$f" ] && { say "-- $(basename "$f")"; sed 's/^/   /' "$f" | tee -a "$report"; }
  done
  say ""
  say "total time: $(( $(date +%s) - started )) s"
  if [ "$push" = 1 ]; then
    git add "$out" && git commit -q -m "POC run $stamp" -- "$out" && git push origin HEAD
    say "results committed and pushed: $out"
  fi
}

# ---- 1. latency matrix -------------------------------------------------------------------------------------------------
say ""
say "== 1. latency matrix (${PHASE_SECONDS}s per phase; the screen state is requested, but what the watch reached is recorded per reading)"
for route in r1 r2; do
  for state in on dim off; do
    [ "$smoke" = 1 ] && [ "$state" != on ] && continue
    label="$route-$state"
    screen_on; sleep 1
    if [ "$state" = on ]; then
      wcmd --es cmd start --es route "$route" --es label "$label" --ez keep_screen true
    else
      wcmd --es cmd start --es route "$route" --es label "$label"
    fi
    sleep 3
    case "$state" in
      dim) w shell settings put system screen_off_timeout 15000 >/dev/null 2>&1 ;;
      off) w shell input keyevent KEYCODE_SLEEP ;;
    esac
    sleep $((PHASE_SECONDS / 2))
    observed=$(observe)
    [ "$label" = r1-on ] && diag "$label"
    sleep $((PHASE_SECONDS - PHASE_SECONDS / 2))
    screen_on; sleep 2
    st=$(status)
    wcmd --es cmd stop
    sleep 3
    say "$label: requested screen=$state, observed mid-phase: $observed; end status: count=$(field "$st" count) error=$(field "$st" error)"
    if [ "$label" = r1-on ] && [ "$(field "$st" count)" = 0 ]; then
      say "STOP: no readings at all in the first phase (screen on, route r1). Further phases would only repeat that."
      say "Diagnostics are in diag-r1-on.txt and watch/events.txt. Is the watch worn and the strap snug?"
      collect
      exit 4
    fi
  done
done

[ "$smoke" = 1 ] && { collect; exit 0; }

# ---- 2. control checks (goal 2) ----------------------------------------------------------------------------------------
say ""
say "== 2. control checks"
screen_on; sleep 1
check() { if [ "$2" = "1" ]; then say "PASS  $1"; else say "FAIL  $1"; fi; }
wcmd --es cmd start --es route r2 --es label ctl-enable; sleep 6
st=$(status); n=$(field "$st" count)
check "enable: sensor starts on command (count=$n, running=$(field "$st" running))" \
  "$([ "$(field "$st" running)" = true ] && [ "${n:-0}" -gt 0 ] 2>/dev/null && echo 1 || echo 0)"
wcmd --es cmd stop; sleep 4
st=$(status); check "disable: sensor stops on command (running=$(field "$st" running))" "$([ "$(field "$st" running)" = false ] && echo 1 || echo 0)"
c1=$(field "$st" count); sleep 5; c2=$(field "$(status)" count)
check "disable: no readings after stop (count $c1 -> $c2)" "$([ "$c1" = "$c2" ] && echo 1 || echo 0)"
wcmd --es cmd start --es route r2 --es label ctl-wakelock --ez hold true; sleep 3
held1=$(field "$(status)" wakeLockHeld)
wcmd --es cmd wakelock --ez hold false; sleep 3
held2=$(field "$(status)" wakeLockHeld)
check "power: wake lock hold/release (held=$held1 then $held2)" "$([ "$held1" = true ] && [ "$held2" = false ] && echo 1 || echo 0)"
wcmd --es cmd stop; sleep 3
wcmd --es cmd start --es route r2 --es label ctl-mode-live --ei batch_us 0; sleep 10
st=$(status); wcmd --es cmd stop; sleep 3
say "mode r2 live (batch 0): count=$(field "$st" count) rateUs=$(field "$st" rateUs) batchUs=$(field "$st" batchUs)"
wcmd --es cmd start --es route r2 --es label ctl-mode-batch --ei batch_us 5000000; sleep 14
st=$(status); wcmd --es cmd stop; sleep 3
say "mode r2 batched (5 s): count=$(field "$st" count) rateUs=$(field "$st" rateUs) batchUs=$(field "$st" batchUs)  (compare gaps in the watch summaries)"
wcmd --es cmd start --es route r1 --es label ctl-mode-r1; sleep 8
st=$(status); n=$(field "$st" count); wcmd --es cmd stop; sleep 3
check "mode: route r1 delivers readings (count=$n route=$(field "$st" route))" \
  "$([ "$(field "$st" route)" = r1 ] && [ "${n:-0}" -gt 0 ] 2>/dev/null && echo 1 || echo 0)"

collect
