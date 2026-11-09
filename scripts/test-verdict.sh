#!/usr/bin/env bash
# Tests for scripts/verdict.sh with synthetic results: good data, slow data, missing data, bad clocks, failing checks.
# Run: scripts/test-verdict.sh   (exit 0 = all cases behave as specified)
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
NOOFF=-9223372036854775808

# watch <dir> <label> <route> <screen> <n> <latency_ms>   (latency grows by 0..4 ms to give a spread)
watch() {
  mkdir -p "$1/watch"
  for ((i = 1; i <= $5; i++)); do
    src=$((1000000000 * i)); lat=$(($6 * 1000000 + (i % 5) * 1000000))
    echo "$2,$3,$i,70,3,$src,$((src + lat)),$4" >>"$1/watch/watch-$2.csv"
  done
}
# phone <dir> <label> <path> <screen> <n> <e2e_ms> [offset_ns]
phone() {
  mkdir -p "$1/phone"
  off="${7:-0}"
  for ((i = 1; i <= $5; i++)); do
    src=$((1000000000 * i)); e2e=$(($6 * 1000000 + (i % 5) * 1000000))
    echo "$2,$i,70,$src,$src,$off,$((src + off + e2e)),$4" >>"$1/phone/phone-$2-$3.csv"
  done
}
# exchange <dir> <label> <path> <rtt_ms> <offset_ns>: one ping/pong exchange file with the given round trip and offset
exchange() {
  mkdir -p "$1/watch"
  t0=1000000000; rtt=$(($4 * 1000000))
  echo "$t0,$((t0 + rtt / 2 + $5)),$((t0 + rtt))" >>"$1/watch/exchanges-$2-$3.csv"
  echo "$((t0 * 2)),$((t0 * 2 + rtt + $5 + 7000000)),$((t0 * 2 + rtt * 2 + 9000000))" >>"$1/watch/exchanges-$2-$3.csv" # a worse one
}
checks() { # checks <dir> C1 C2 C3 C4 C5 C6 results
  shift 0
  d="$1"; shift
  mkdir -p "$d"
  n=1
  for r in "$@"; do echo "CHECK C$n $r: test" >>"$d/report.txt"; n=$((n + 1)); done
}
expect() { # expect <case> <dir> <pattern>...
  name="$1"; d="$2"; shift 2
  out="$("$here/verdict.sh" "$d" 2>&1)"
  for pat in "$@"; do
    if ! echo "$out" | grep -qF -- "$pat"; then
      echo "FAIL [$name]: expected '$pat'"; echo "$out" | sed 's/^/    /'; fail=1; return
    fi
  done
  echo "ok   [$name]"
}
allpass() { checks "$1" PASS PASS PASS PASS PASS PASS; }

# A. Everything good: three screen states, both routes, fast Bluetooth.
d="$tmp/a"; allpass "$d"
for r in r1 r2; do for s in on doze off; do
  watch "$d" "$r-$s" "$r" "$s" 30 80; phone "$d" "$r-$s" bt "$s" 30 300; phone "$d" "$r-$s" dl "$s" 30 200
done; done
expect A-success "$d" "GOAL 1: MET" "GOAL 2: MET" "GOAL 3 (Bluetooth): MET" "OVERALL: SUCCESS" \
  "Goal 1 (pulse data within 0.5 s of recording): ACHIEVED" "Goal 2 (control of power, sensor enabling, modes): ACHIEVED" \
  "Goal 3 (live pulse to the phone over Bluetooth, under 1 s): ACHIEVED"

# A2. Only the screen-on state was reached: still judged, but the limitation is stated.
d="$tmp/a2"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 300
expect A2-onlyon "$d" "GOAL 1: MET" "holds for the screen-on state only" "screen states judged: on" \
  "Goal 1 (pulse data within 0.5 s of recording): ACHIEVED (screen on only; dim/off not reached)"

# B. Slow when the display dozes: only screen-on meets goal 1 -> PARTIAL, not success.
d="$tmp/b"; allpass "$d"
watch "$d" r2-on r2 on 30 80; watch "$d" r2-dim r2 doze 30 3000
phone "$d" r2-on bt on 30 300; phone "$d" r2-dim bt doze 30 300
expect B-partial "$d" "GOAL 1: PARTIAL" "misses 500 ms" "OVERALL: PARTIAL" "recording): FAILED (only with the screen on)"

# C. Too slow everywhere: goal 1 NOT MET -> FAILURE with data.
d="$tmp/c"; allpass "$d"
watch "$d" r1-on r1 on 30 2000; watch "$d" r2-on r2 on 30 2500
phone "$d" r2-on bt on 30 300
expect C-failure "$d" "GOAL 1: NOT MET" "OVERALL: FAILURE" "recording): FAILED (limit missed)"

# D. No readings at all, watch says off-body.
d="$tmp/d"; checks "$d" FAIL PASS INCONCLUSIVE PASS FAIL FAIL
mkdir -p "$d/watch"; echo "12s availability UNAVAILABLE_DEVICE_OFF_BODY" >"$d/watch/events.txt"
expect D-offbody "$d" "GOAL 1: NO DATA" "GOAL 2: NO DATA" "OFF-BODY" "OVERALL: INCOMPLETE" "recording): FAILED (no data)" "modes): FAILED (no data)"

# E. No readings, permission false.
d="$tmp/e"; checks "$d" FAIL PASS INCONCLUSIVE PASS FAIL FAIL
mkdir -p "$d/watch"; echo "1s BODY_SENSORS=false; READ_HEART_RATE=false; BLUETOOTH_CONNECT=true" >"$d/watch/events.txt"
expect E-permission "$d" "heart-rate permission not granted" "OVERALL: INCOMPLETE"

# F. No readings and no events file: say the app may not have started.
d="$tmp/f"; mkdir -p "$d"
expect F-noevents "$d" "may not have started" "OVERALL: INCOMPLETE"

# G. Clock base wrong (negative latencies): never a pass.
d="$tmp/g"; allpass "$d"
watch "$d" r2-on r2 on 30 -500; phone "$d" r2-on bt on 30 300
expect G-clock "$d" "INVALID CLOCK BASE" "OVERALL: INCOMPLETE"

# H. Bluetooth too slow while the Data Layer is fast: goal 3 uses Bluetooth only.
d="$tmp/h"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 1500; phone "$d" r2-on dl on 30 200
expect H-bt-slow "$d" "GOAL 3 (Bluetooth): NOT MET" "Data Layer: MET" "OVERALL: FAILURE" "under 1 s): FAILED (limit missed)"

# I. Bluetooth never connected: watch readings exist, no Bluetooth rows; cause printed.
d="$tmp/i"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on dl on 30 200
mkdir -p "$d/watch"; echo "5s bt: connect Pixel: read failed, socket might closed" >"$d/watch/events.txt"
expect I-nobt "$d" "GOAL 3 (Bluetooth): NO DATA" "bt: connect" "OVERALL: INCOMPLETE"

# J. Too few readings are not judged.
d="$tmp/j"; allpass "$d"
watch "$d" r2-on r2 on 5 80; phone "$d" r2-on bt on 5 300
expect J-few "$d" "too few readings" "GOAL 1: NO DATA"

# K. Control checks failing -> goal 2 NOT MET even when latency is good.
d="$tmp/k"; checks "$d" PASS FAIL PASS PASS PASS PASS
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 300
expect K-control "$d" "GOAL 2: NOT MET" "C2=FAIL" "OVERALL: FAILURE" "modes): FAILED (C1=PASS C2=FAIL"

# L. Readings without a clock offset are left out of the end-to-end numbers.
d="$tmp/l"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 300 "$NOOFF"
expect L-nooffset "$d" "GOAL 3 (Bluetooth): NO DATA"

# N. The offset that was current when each reading was sent is wrong; the whole-run exchange file corrects it.
d="$tmp/n"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 0 300000000; exchange "$d" r2-on bt 40 0
expect N-wholerun-offset "$d" "GOAL 3 (Bluetooth): MET" "p50=   302.0" "clock uncertainty +-20.0 ms"

# O. A long round trip widens the clock uncertainty: a slightly negative end-to-end value is then not "invalid".
d="$tmp/o"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 -150; exchange "$d" r2-on bt 400 0
expect O-uncertainty "$d" "clock uncertainty +-200.0 ms" "GOAL 3 (Bluetooth): MET"

# P. Without an exchange file a hugely negative end-to-end value is still an invalid clock base.
d="$tmp/p"; allpass "$d"
watch "$d" r2-on r2 on 30 80; phone "$d" r2-on bt on 30 -500
expect P-invalid-noexchange "$d" "INVALID CLOCK BASE"

# Q. Both system-level power checks inconclusive: not enough evidence, goal 2 is not claimed.
d="$tmp/q"; checks "$d" PASS PASS INCONCLUSIVE INCONCLUSIVE PASS PASS
for r in r1 r2; do watch "$d" "$r-on" "$r" on 30 80; phone "$d" "$r-on" bt on 30 300; done
expect Q-no-power-evidence "$d" "GOAL 2: NOT MET" "C3=INCONCLUSIVE C4=INCONCLUSIVE"

# R. Wake lock check inconclusive but the sensor connection is seen: goal 2 still met.
d="$tmp/r"; checks "$d" PASS PASS PASS INCONCLUSIVE PASS PASS
for r in r1 r2; do watch "$d" "$r-on" "$r" on 30 80; phone "$d" "$r-on" bt on 30 300; done
expect R-wakelock-inconclusive "$d" "GOAL 2: MET"

# M. System-level check inconclusive (dumpsys format unknown) does not block goal 2.
d="$tmp/m"; checks "$d" PASS PASS INCONCLUSIVE PASS FAIL PASS
for r in r1 r2; do watch "$d" "$r-on" "$r" on 30 80; phone "$d" "$r-on" bt on 30 300; done
expect M-inconclusive "$d" "GOAL 2: MET" "OVERALL: SUCCESS"

exit $fail
