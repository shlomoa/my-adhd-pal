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
nap() { sleep "$(awk -v s="$1" -v k="${TIME_SCALE:-1}" 'BEGIN { print s * k }')"; } # TIME_SCALE < 1 only in the script tests
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
out="${RESULTS_ROOT:-poc-results}/$stamp"
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
[ "${SKIP_BUILD:-0}" = 1 ] || ./gradlew :wear:assembleDebug :phone:assembleDebug -q || { say "BUILD FAILED"; exit 3; }
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
clear_files() { # clear_files <serial> <name>: remove the app's old result files one by one (a glob would be lost in adb's quoting)
  local f left
  for f in $("$ADB" -s "$1" shell run-as $PKG ls files 2>/dev/null | tr -d '\r'); do
    "$ADB" -s "$1" shell run-as $PKG rm -f "files/$f" >/dev/null 2>&1
  done
  left=$("$ADB" -s "$1" shell run-as $PKG ls files 2>/dev/null | tr -d '\r' | grep -c .)
  if [ "${left:-0}" = 0 ]; then say "$2 old result files: cleared"; else say "WARNING: $2 still has $left old result files; this run's numbers may include old data"; fi
}
clear_files "$W" watch
clear_files "$P" phone
grant() { # grant <name> <serial> <permission>...
  local name=$1 serial=$2 res perm
  shift 2
  for perm in "$@"; do
    if res=$("$ADB" -s "$serial" shell pm grant $PKG "$perm" 2>&1 | tr -d '\r'); [ -z "$res" ]; then
      say "$name granted $perm"
    else
      say "$name NOT granted $perm: $(echo "$res" | head -1)"
    fi
  done
}
grant watch "$W" android.permission.BODY_SENSORS android.permission.health.READ_HEART_RATE \
  android.permission.BODY_SENSORS_BACKGROUND android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND \
  android.permission.POST_NOTIFICATIONS android.permission.BLUETOOTH_CONNECT android.permission.BLUETOOTH_SCAN
grant phone "$P" android.permission.BLUETOOTH_CONNECT android.permission.POST_NOTIFICATIONS

# The phone app starts its Bluetooth server from its screen, so the phone must be awake and unlocked.
p shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
p shell wm dismiss-keyguard >/dev/null 2>&1
p shell am start -n "$PACT" >/dev/null 2>&1
listening=0
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if p shell run-as $PKG cat files/events.txt 2>/dev/null | grep -q listening; then listening=1; break; fi
  nap 1
done
if [ "$listening" = 1 ]; then say "phone Bluetooth server: listening"; else say "phone Bluetooth server: NOT listening after 10 s (unlock the phone and check its screen); the Bluetooth path will have no data"; fi

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
  nap 3
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
  scripts/verdict.sh "$out" | tee -a "$report" >/dev/null
  sed '/^######/,$d' "$out/verdict.txt" # the details; the full report is printed last
  say ""
  say "total time: $(( $(date +%s) - started )) s"
  if [ "$push" = 1 ]; then
    git add "$out" && git commit -q -m "POC run $stamp" -- "$out" && git push origin HEAD
    say "results committed and pushed: $out"
  fi
  sed -n '/^# *FULL REPORT/,$p' "$out/verdict.txt" | sed '1s/^/######################################################################\n/'
}

# ---- 1. latency matrix -------------------------------------------------------------------------------------------------
say ""
say "== 1. latency matrix (${PHASE_SECONDS}s per phase; the screen state is requested, but what the watch reached is recorded per reading)"
for route in r1 r2; do
  for state in on dim off; do
    [ "$smoke" = 1 ] && [ "$state" != on ] && continue
    label="$route-$state"
    screen_on; nap 1
    if [ "$state" = on ]; then
      wcmd --es cmd start --es route "$route" --es label "$label" --ez keep_screen true
    else
      wcmd --es cmd start --es route "$route" --es label "$label"
    fi
    nap 3
    case "$state" in
      dim) w shell settings put system screen_off_timeout 15000 >/dev/null 2>&1 ;;
      off) w shell input keyevent KEYCODE_SLEEP ;;
    esac
    nap $((PHASE_SECONDS / 2))
    observed=$(observe)
    [ "$label" = r1-on ] && diag "$label"
    nap $((PHASE_SECONDS - PHASE_SECONDS / 2))
    screen_on; nap 2
    st=$(status)
    wcmd --es cmd stop
    nap 3
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
say "== 2. control checks (each is PASS, FAIL or INCONCLUSIVE; rules in docs/poc/requirements.md)"
screen_on; nap 1
chk() { say "CHECK $1 $2: $3"; }
# Newest registration line of our app in `dumpsys sensorservice`, seen on the real watch as
#   15:54:46 + 0x0000004d pid=14498 uid=10004 samplingPeriod= 1000000us batchingPeriod= 0us result=OK (sensor, package): (Samsung HR Batch Sensor , <pkg>)
# "+" = sensor activated by our app, "-" = deactivated. The log lists the newest line first.
sensor_state() { w shell dumpsys sensorservice | tr -d '\r' | grep -E "^[0-9]{2}:[0-9]{2}:[0-9]{2} [+-] 0x[0-9a-fA-F]+ .*$PKG" | head -1 | awk '{ print $2 }'; }
capture() { # capture <name> <dumpsys service> <regex>: the raw lines (with context) the checks rely on, kept in the results
  local text
  text=$(w shell dumpsys "$2" 2>&1 | tr -d '\r' | grep -n -i -B2 -A4 -E "$3" | head -150)
  if [ -z "$text" ]; then
    { echo "no line matched /$3/ in dumpsys $2; first 60 lines of the dump, to show its format:"; w shell dumpsys "$2" 2>&1 | tr -d '\r' | head -60; } >"$out/dumpsys-$1.txt"
  else
    echo "$text" >"$out/dumpsys-$1.txt"
  fi
}
wl_count() { w shell dumpsys power | tr -d '\r' | grep -c "poc:pulse"; }
summary_field() { # summary_field <label> <sed-expression>
  w shell run-as $PKG cat "files/watch-summary-$1.txt" 2>/dev/null | tr -d '\r' | sed -n "$2" | head -1
}

# C1 enable and C3 system-level evidence that the sensor is switched on
wcmd --es cmd start --es route r2 --es label ctl-enable --ez hold true; nap 6
st=$(status); n1=$(field "$st" count); running=$(field "$st" running); conn_run=$(sensor_state)
capture sensorservice-running sensorservice "heart|$PKG"
if [ "$running" = true ] && [ "${n1:-0}" -gt 0 ] 2>/dev/null; then r=PASS; else r=FAIL; fi
chk C1 $r "enable: readings arrive after the start command (running=$running, count=$n1, error=$(field "$st" error))"

# C2 disable, and C3 the system no longer shows our sensor connection
wcmd --es cmd stop; nap 4
st=$(status); running=$(field "$st" running); k1=$(field "$st" count); conn_stop=$(sensor_state)
capture sensorservice-stopped sensorservice "heart|$PKG"
nap 5; k2=$(field "$(status)" count)
if [ "$running" = false ] && [ "$k1" = "$k2" ]; then r=PASS; else r=FAIL; fi
chk C2 $r "disable: sensor stops on command and no reading arrives afterwards (running=$running, count $k1 -> $k2)"
if [ "$conn_run" = "+" ] && [ "$conn_stop" = "-" ]; then r=PASS
elif [ -z "$conn_run" ]; then r=INCONCLUSIVE
else r=FAIL; fi
chk C3 $r "sensor power at system level: newest dumpsys sensorservice registration of our app while running='${conn_run:-none}' (+ = activated), after stop='${conn_stop:-none}' (- = deactivated); raw text in dumpsys-sensorservice-running.txt and -stopped.txt"

# C4 wake lock (power): our status and the system's wake lock list must agree
wcmd --es cmd start --es route r2 --es label ctl-wakelock --ez hold true; nap 3
held1=$(field "$(status)" wakeLockHeld); wl1=$(wl_count)
capture power-held power "wake ?lock|poc:pulse"
wcmd --es cmd wakelock --ez hold false; nap 3
held2=$(field "$(status)" wakeLockHeld); wl2=$(wl_count)
capture power-released power "wake ?lock|poc:pulse"
if [ "$held1" != true ] || [ "$held2" != false ]; then r=FAIL
elif [ "${wl1:-0}" -ge 1 ] && [ "${wl2:-0}" -eq 0 ]; then r=PASS
elif [ "${wl1:-0}" -eq 0 ]; then r=INCONCLUSIVE
else r=FAIL; fi
chk C4 $r "power: wake lock held then released (status $held1 -> $held2; system wake locks $wl1 -> $wl2; INCONCLUSIVE = not visible; raw text saved in dumpsys-power-held.txt and -released.txt)"
wcmd --es cmd stop; nap 3

# C5 batching mode: with a 5 s report latency the readings must arrive in bursts (longer gaps) than with 0
gapre='s/^gap_between_arrivals:.* max=\([0-9.]*\)ms.*/\1/p'
cntre='s/^readings=\([0-9]*\).*/\1/p'
wcmd --es cmd start --es route r2 --es label ctl-mode-live --ei batch_us 0; nap 10
wcmd --es cmd stop; nap 3
wcmd --es cmd start --es route r2 --es label ctl-mode-batch --ei batch_us 5000000; nap 14
wcmd --es cmd stop; nap 3
live_n=$(summary_field ctl-mode-live "$cntre"); live_gap=$(summary_field ctl-mode-live "$gapre")
batch_n=$(summary_field ctl-mode-batch "$cntre"); batch_gap=$(summary_field ctl-mode-batch "$gapre")
if [ "${live_n:-0}" -ge 3 ] && [ "${batch_n:-0}" -ge 3 ] && awk -v a="${batch_gap:-0}" -v b="${live_gap:-0}" 'BEGIN { exit !(b > 0 && a >= 2 * b) }'; then r=PASS
elif [ "${live_n:-0}" -lt 3 ] || [ "${batch_n:-0}" -lt 3 ]; then r=FAIL
else r=FAIL; fi
chk C5 $r "mode batching: live max gap ${live_gap:-?} ms (n=${live_n:-0}) vs batched(5 s) max gap ${batch_gap:-?} ms (n=${batch_n:-0}); PASS needs at least 2x"

# C6 route mode: both sensor routes can be selected and each delivers readings
wcmd --es cmd start --es route r1 --es label ctl-mode-r1; nap 8
st=$(status); n6=$(field "$st" count); route6=$(field "$st" route)
wcmd --es cmd stop; nap 3
if [ "$route6" = r1 ] && [ "${n6:-0}" -gt 0 ] && [ "${n1:-0}" -gt 0 ] 2>/dev/null; then r=PASS; else r=FAIL; fi
chk C6 $r "mode route: r2 gave $n1 readings, r1 gave $n6 readings (route=$route6)"

collect
