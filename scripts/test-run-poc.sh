#!/usr/bin/env bash
# Runs the real scripts/run-poc.sh against a stateful fake adb (scripts/testdata/fake-adb.py) in several scenarios
# and checks the exit code and verdict of each. Run: scripts/test-run-poc.sh   (exit 0 = all scenarios behave)
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0

# run <scenario> <expected-exit> <extra args...> -- <pattern>...
run_case() {
  name="$1"; want="$2"; shift 2
  extra=(); pats=()
  seen=0
  for a in "$@"; do
    if [ "$a" = -- ]; then seen=1; elif [ $seen = 0 ]; then extra+=("$a"); else pats+=("$a"); fi
  done
  export FAKE_DIR="$tmp/$name-state" FAKE_SCENARIO="$name"
  mkdir -p "$FAKE_DIR"
  if [ "$name" = stale ]; then # result files left on both devices by an earlier run: the script must clear them
    mkdir -p "$FAKE_DIR/WATCH1/files" "$FAKE_DIR/PHONE1/files"
    echo "r1-on,r1,1,70,3,1000,9000000000000,on" >"$FAKE_DIR/WATCH1/files/watch-r1-on.csv"
    echo "old event" >"$FAKE_DIR/WATCH1/files/events.txt"
    echo "r1-on,1,70,1000,1000,0,9000000000000,on" >"$FAKE_DIR/PHONE1/files/phone-r1-on-dl.csv"
  fi
  log="$tmp/$name.log"
  (cd "$root" && ADB="$here/testdata/fake-adb.py" SKIP_BUILD=1 TIME_SCALE=0.002 PHASE_SECONDS=40 RESULTS_ROOT="$tmp/$name-results" \
    scripts/run-poc.sh --no-push "${extra[@]}") >"$log" 2>&1
  got=$?
  ok=1
  [ "$got" = "$want" ] || { ok=0; echo "FAIL [$name]: exit $got, expected $want"; }
  for pat in "${pats[@]}"; do
    grep -qF -- "$pat" "$log" || { ok=0; echo "FAIL [$name]: output lacks '$pat'"; }
  done
  if [ "$name" = good ]; then
    for f in dumpsys-sensorservice-running dumpsys-sensorservice-stopped dumpsys-power-held dumpsys-power-released; do
      ls "$tmp/$name-results"/*/"$f.txt" >/dev/null 2>&1 || { ok=0; echo "FAIL [$name]: results lack $f.txt"; }
    done
    grep -q "poc:pulse" "$tmp/$name-results"/*/dumpsys-power-held.txt 2>/dev/null || { ok=0; echo "FAIL [$name]: dumpsys-power-held.txt lacks the wake lock line"; }
  fi
  if grep -q "FULL REPORT" "$log"; then
    [ "$(grep -c "FULL REPORT" "$log")" = 1 ] || { ok=0; echo "FAIL [$name]: the full report is printed more than once"; }
    # the report must be the last thing printed: nothing but the report and the result block after its header
    after="$(sed -n '/FULL REPORT/,$p' "$log" | grep -c "total time")"
    [ "$after" = 0 ] || { ok=0; echo "FAIL [$name]: output continues after the full report"; }
  fi
  if [ $ok = 1 ]; then echo "ok   [$name]"; else fail=1; sed 's/^/    /' "$log" | tail -40; fi
}

run_case good 0 -- "phone Bluetooth server: listening" "CHECK C1 PASS" "CHECK C2 PASS" "CHECK C3 PASS" "CHECK C4 PASS" \
  "CHECK C5 PASS" "CHECK C6 PASS" "9. WHAT THE LIVE INDICATOR ON THE PHONE SHOWED" "LIVE 72 bpm age 0.4 s" "10. PPG, IBI AND HRV" "a raw waveform IS available: 1500 events at 60.0 Hz" "PPG: no. The stream carries one number per second" "FULL REPORT" "1. WHAT IS REQUIRED, AND WHAT WAS COVERED" "2. WHAT REMAINS UNKNOWN" "3. DECISIONS MADE" "4. CONSIDERATIONS NOT TAKEN" "5. WHAT WAS MEASURED WITH REAL DATA" "6. THE LATENCY, IN SHORT" "7. HOW MANY SAMPLES" "Watch readings judged for goal 1: 150 (in 6 phases)" "Phone, Bluetooth: 150 received" "GOAL 1: MET" "GOAL 2: MET" "GOAL 3 (Bluetooth): MET" "OVERALL: SUCCESS" \
  "Goal 1 (pulse data within 0.5 s of recording): ACHIEVED (routes: r1=ok r2=ok; not reached: dim,off)" "modes): ACHIEVED" "under 1 s): ACHIEVED (routes: r1=ok r2=ok; not reached: dim,off)"
run_case stale 0 -- "watch old result files: cleared" "phone old result files: cleared" "Watch readings judged for goal 1: 150 (in 6 phases)" "GOAL 1: MET"
run_case rawnone 0 -- "delivered NO events to this app" "Samsung's Health Sensor SDK" "GOAL 1: MET"
run_case slow 0 -- "GOAL 1: NOT MET" "OVERALL: FAILURE"
run_case zero 4 -- "STOP: no readings at all" "OFF-BODY" "GOAL 1: NO DATA" "OVERALL: INCOMPLETE"
run_case nobt 0 -- "GOAL 3 (Bluetooth): NO DATA" "Data Layer: MET" "OVERALL: INCOMPLETE"
run_case installfail 3 -- "watch install: Failure [INSTALL_FAILED_FAKE]" "is not installed after the install"
run_case noperm 0 -- "watch NOT granted android.permission.health.READ_HEART_RATE"
run_case nophone 0 -- "phone Bluetooth server: NOT listening"
run_case smoke 0 --smoke -- "r1-on:" "r2-on:" "OVERALL"
exit $fail
