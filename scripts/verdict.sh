#!/usr/bin/env bash
# Verdict for one results directory (poc-results/<stamp>/), computed from the raw logs. Rules: docs/poc/requirements.md.
#
#   scripts/verdict.sh <results-dir>      prints the verdict and writes <results-dir>/verdict.txt
#
# Inputs: watch/watch-r[12]-*.csv   one line per reading on the watch
#           label,route,seq,bpm,accuracy,recordedNanos,watchArrivalNanos,screen
#         phone/phone-r[12]-*-{dl,bt}.csv   one line per reading on the phone (dl = Data Layer, bt = Bluetooth)
#           label,seq,bpm,recordedNanos,sendNanos,offsetNanos,phoneArrivalNanos,screen
#         report.txt            "CHECK <id> <PASS|FAIL|INCONCLUSIVE>: ..." lines from the control checks
#         watch/events.txt, phone/events.txt, diag-*.txt   used only to explain missing data
#
# Goal 1: p95 recorded -> watch app at most 500 ms        (per route and screen state, at least MIN_N readings)
# Goal 2: control checks C1 C2 PASS, C3 and C4 not FAIL with at least one of them PASS (system-level evidence),
#         and one mode check (C5 or C6) PASS
# Goal 3: p95 recorded -> phone over BLUETOOTH under 1000 ms (the Data Layer is reported, never substituted)
# A goal is MET when one route meets it in every screen state it was observed in (at least MIN_N readings),
# PARTIAL when it only meets it with the screen on, NOT MET when there is data but it never meets it, NO DATA
# without enough readings, and INVALID CLOCK BASE when latencies are negative or hours long.
set -u
export LC_ALL=C
dir="${1:?usage: verdict.sh <results-dir>}"
MIN_N="${MIN_N:-20}"
MIN_SOURCE_NANOS="${MIN_SOURCE_NANOS:-0}" # ignore readings recorded before this elapsed-realtime (rows left over from an earlier run)
out="$dir/verdict.txt"
: >"$out"
say() { echo "$*" | tee -a "$out"; }
saylines() { while IFS= read -r l; do say "$l"; done; }

# Reads "key value" lines; prints "route|screen n min p50 p95 max" using nearest-rank percentiles.
groups() {
  sort -k1,1 -k2,2n | awk '
    function flush(  i50, i95) {
      if (n > 0) {
        i50 = int((50 * n + 99) / 100); if (i50 < 1) i50 = 1
        i95 = int((95 * n + 99) / 100); if (i95 < 1) i95 = 1
        printf "%s %d %.1f %.1f %.1f %.1f\n", key, n, v[1], v[i50], v[i95], v[n]
      }
    }
    { if ($1 != key) { flush(); key = $1; n = 0 } n++; v[n] = $2 }
    END { flush() }'
}

watch_rows() {
  for f in "$dir"/watch/watch-r[12]-*.csv; do [ -f "$f" ] && cat "$f"; done |
    awk -F, -v min="$MIN_SOURCE_NANOS" 'NF >= 8 && $6 >= min { printf "%s|%s %.3f\n", $2, $8, ($7 - $6) / 1000000.0 }'
}

# $1 = path (dl or bt); keys become route|screen.
# The clock offset (phone minus watch) is taken from the exchange with the shortest round trip of the whole run
# (watch/exchanges-<label>-<path>.csv: t0,phoneNanos,t3), which is more accurate than the offset that was current
# when each reading was sent. Without an exchange file the offset carried in the reading is used; readings sent
# before the first exchange have none and are left out.
phone_rows() {
  for f in "$dir"/phone/phone-r[12]-*-"$1".csv; do
    [ -f "$f" ] || continue
    ex="$dir/watch/exchanges-$(basename "${f%-"$1".csv}" | sed 's/^phone-//')-$1.csv"
    [ -f "$ex" ] || ex=""
    awk -F, -v ex="$ex" -v min="$MIN_SOURCE_NANOS" '
      BEGIN {
        if (ex != "") {
          while ((getline line < ex) > 0) {
            split(line, a, ",")
            rtt = a[3] - a[1]
            if (rtt >= 0 && (best == "" || rtt < bestrtt)) { bestrtt = rtt; best = a[2] - (a[1] + a[3]) / 2 }
          }
        }
      }
      NF >= 8 && $4 >= min {
        if (best != "") off = best; else if ($6 > -9.0e18) off = $6; else next
        printf "%s|%s %.3f\n", substr($1, 1, 2), $8, ($7 - ($4 + off)) / 1000000.0
      }' "$f"
  done
}

# Clock uncertainty of a path in ms: half the shortest round trip, worst over the run's labels (the offset assumes
# equal travel time each way, so it can be wrong by up to half the round trip). 100 when there are no exchange files.
uncertainty() {
  for f in "$dir"/watch/exchanges-r[12]-*-"$1".csv; do
    [ -f "$f" ] && awk -F, 'NR == 1 || $3 - $1 < m { m = $3 - $1 } END { printf "%.1f\n", m / 2000000.0 }' "$f"
  done | sort -n | tail -1
}

# judge <table-file> <threshold-ms> <strict 0|1> <clock-min-ms>; prints detail lines, then "VERDICT: <x>".
judge() {
  awk -v MINN="$MIN_N" -v THR="$2" -v STRICT="$3" -v CLOCKMIN="$4" '
    BEGIN { FS = "[| ]" }
    {
      route = $1; screen = $2; n = $3; mn = $4; p50 = $5; p95 = $6; mx = $7
      line = sprintf("  %s screen=%-4s n=%-4d min=%8.1f p50=%8.1f p95=%8.1f max=%8.1f ms", route, screen, n, mn, p50, p95, mx)
      if (n < MINN) { print line "   too few readings, not judged"; seen = 1; next }
      if (mn < CLOCKMIN || p50 > 3600000) { print line "   INVALID CLOCK BASE"; invalid = 1; next }
      usable = 1; total[route]++; judged[screen] = 1
      ok = STRICT ? (p95 < THR) : (p95 <= THR)
      if (ok) { print line "   ok"; good[route]++; if (screen == "on") onok[route] = 1 }
      else print line "   misses " THR " ms"
    }
    END {
      for (r in total) { if (good[r] == total[r]) met = 1; else if (onok[r]) partial = 1 }
      n = split("on doze off", order, " ")
      for (i = 1; i <= n; i++) if (order[i] in judged) states = states (states == "" ? "" : ",") order[i]; else missing = missing (missing == "" ? "" : ",") order[i]
      if (usable) print "  screen states judged: " states
      if (usable && missing != "") print "  NOTE: screen states not reached (or too few readings): " missing "; the result holds for the states judged only"
      m = split("r1 r2", rts, " ")
      for (i = 1; i <= m; i++) { r = rts[i]; if (total[r] > 0) routes = routes " " r "=" (good[r] == total[r] ? "ok" : "missed") }
      if (usable) print "  ROUTES:" routes
      if (invalid) v = "INVALID CLOCK BASE"
      else if (!usable) v = "NO DATA"
      else if (met) v = "MET"
      else if (partial) v = "PARTIAL (only with the screen on)"
      else v = "NOT MET"
      print "VERDICT: " v
    }' "$1"
}

verdict_of() { sed -n 's/^VERDICT: //p' | tail -1; }
detail_of() { grep -v '^VERDICT: '; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

say "== Verdict for $dir"
say ""

# ---- Goal 1 ----
watch_rows | groups >"$tmp/w.tbl"
say "Goal 1: pulse data at most 500 ms from recording (recorded -> watch app, p95)"
judge "$tmp/w.tbl" 500 0 -5 >"$tmp/g1.txt"
detail_of <"$tmp/g1.txt" | tee -a "$out"
g1="$(verdict_of <"$tmp/g1.txt")"
say "  => GOAL 1: $g1"
say ""

# ---- Goal 2 ----
say "Goal 2: control of power, sensor enabling and modes"
check() { grep -m1 "^CHECK $1 " "$dir/report.txt" 2>/dev/null | awk '{print $3}' | tr -d ':'; }
grep '^CHECK ' "$dir/report.txt" 2>/dev/null | sed 's/^/  /' | tee -a "$out"
c1="$(check C1)"; c2="$(check C2)"; c3="$(check C3)"; c4="$(check C4)"; c5="$(check C5)"; c6="$(check C6)"
if [ -z "$c1$c2$c3$c4$c5$c6" ]; then
  g2="NO DATA"
elif [ "$c1" = PASS ] && [ "$c2" = PASS ] && [ "$c3" != FAIL ] && [ "$c4" != FAIL ] && { [ "$c3" = PASS ] || [ "$c4" = PASS ]; } &&
  { [ "$c5" = PASS ] || [ "$c6" = PASS ]; }; then
  g2="MET"
elif [ "$c1" != PASS ] && [ ! -s "$tmp/w.tbl" ]; then
  g2="NO DATA (no readings arrived after start, so nothing could be controlled and observed)"
else
  g2="NOT MET (C1=$c1 C2=$c2 C3=$c3 C4=$c4 C5=$c5 C6=$c6)"
fi
say "  => GOAL 2: $g2"
say ""

# ---- Goal 3 ----
phone_rows bt | groups >"$tmp/bt.tbl"
phone_rows dl | groups >"$tmp/dl.tbl"
unc_bt="$(uncertainty bt)"; unc_dl="$(uncertainty dl)"
say "Goal 3: live pulse over Bluetooth to the phone in under 1000 ms (recorded -> phone, p95)"
say " Direct Bluetooth path (RFCOMM), clock uncertainty +-${unc_bt:-?} ms (half the shortest ping round trip):"
clock_bt=$(awk -v u="${unc_bt:-100}" 'BEGIN { printf "%.1f", -(u + 20) }')
clock_dl=$(awk -v u="${unc_dl:-100}" 'BEGIN { printf "%.1f", -(u + 20) }')
judge "$tmp/bt.tbl" 1000 1 "$clock_bt" >"$tmp/g3.txt"
detail_of <"$tmp/g3.txt" | tee -a "$out"
g3="$(verdict_of <"$tmp/g3.txt")"
say " Data Layer path (reported for comparison; Google says it prefers Bluetooth but may use Wi-Fi), clock uncertainty +-${unc_dl:-?} ms:"
judge "$tmp/dl.tbl" 1000 1 "$clock_dl" >"$tmp/g3dl.txt"
detail_of <"$tmp/g3dl.txt" | tee -a "$out"
say "  => GOAL 3 (Bluetooth): $g3   [Data Layer: $(verdict_of <"$tmp/g3dl.txt")]"
say ""

# ---- Explanations for missing data ----
if [ ! -s "$tmp/w.tbl" ]; then
  say "No readings reached the watch app. Likely cause(s), from the logs:"
  ev="$dir/watch/events.txt"
  if [ ! -f "$ev" ]; then
    say "  - the watch app wrote no events: it may not have started (check the install and permission lines in report.txt)"
  else
    grep -qi 'OFF_BODY' "$ev" && say "  - the system reports the watch OFF-BODY: wear it snugly on the wrist and run again"
    grep -E 'READ_HEART_RATE=false|BODY_SENSORS=false' "$ev" | head -1 | sed 's/^/  - heart-rate permission not granted: /' | saylines
    grep -q 'no TYPE_HEART_RATE sensor' "$ev" && say "  - the watch has no heart-rate sensor visible to apps"
    grep -E 'registerListener .* -> false' "$ev" | head -1 | sed 's/^/  - sensor registration refused: /' | saylines
    grep -E 'startForeground:|registerMeasureCallback:|registerListener:' "$ev" | head -1 | sed 's/^/  - /' | saylines
    grep -i 'availability' "$ev" | tail -1 | sed 's/^/  - last availability report: /' | saylines
    grep -qi 'supported=false' "$ev" && say "  - Health Services says heart rate is not supported on this watch"
    grep -qiE 'OFF_BODY|=false|no TYPE_HEART|-> false|startForeground:|registerMeasureCallback:|registerListener:|supported=false' "$ev" ||
      say "  - nothing conclusive in watch/events.txt; see diag-*.txt (dumpsys of sensors and permissions)"
  fi
  say ""
elif [ "$g3" = "NO DATA" ]; then
  say "Readings reached the watch app, but none arrived over Bluetooth with a clock offset. From the logs:"
  grep -E 'bt:' "$dir/watch/events.txt" 2>/dev/null | tail -3 | sed 's/^/  - watch: /' | saylines
  grep -E 'listening|connected|failed|permission' "$dir/phone/events.txt" 2>/dev/null | tail -3 | sed 's/^/  - phone: /' | saylines
  say ""
fi

# ---- Overall ----
case "$g1|$g2|$g3" in
  "MET|MET|MET") overall="SUCCESS: all three goals measured as met" ;;
  *"NO DATA"*) overall="INCOMPLETE: at least one goal has no data (see the causes above)" ;;
  *"INVALID CLOCK"*) overall="INCOMPLETE: latencies are not comparable (clock base), see above" ;;
  *"NOT MET"*) overall="FAILURE (at least one goal not met with data): $g1 | $g2 | $g3" ;;
  *) overall="PARTIAL: $g1 | $g2 | $g3" ;;
esac
say "OVERALL: $overall"

# ---- The three lines the user reads ----
# MET is ACHIEVED. Everything else is FAILED with the short reason. When the dim/off states were not reached the
# ACHIEVED line says it holds for the screen-on state only.
verdict_line() { # verdict_line <verdict> <detail-file>
  local extra="" routes missing
  routes="$(sed -n 's/^  ROUTES://p' "$2" 2>/dev/null | head -1)"
  missing="$(sed -n 's/^  NOTE: screen states not reached (or too few readings): \([a-z,]*\);.*/\1/p' "$2" 2>/dev/null | head -1 | sed 's/doze/dim/')"
  [ -n "$routes" ] && extra="routes:$routes"
  [ -n "$missing" ] && extra="${extra:+$extra; }not reached: $missing"
  case "$1" in
    MET*) if [ -n "$extra" ]; then echo "ACHIEVED ($extra)"; else echo "ACHIEVED"; fi ;;
    PARTIAL*) echo "FAILED (only with the screen on${extra:+; $extra})" ;;
    "NOT MET"*) echo "FAILED (limit missed${extra:+; $extra})" ;;
    "NO DATA"*) echo "FAILED (no data)" ;;
    INVALID*) echo "FAILED (clock base invalid)" ;;
    *) echo "FAILED" ;;
  esac
}
case "$g2" in
  MET) line2="ACHIEVED" ;;
  "NOT MET"*) line2="FAILED ${g2#NOT MET }" ;;
  *) line2="FAILED (no data)" ;;
esac
say ""
say "=============== RESULT ==============="
say "Goal 1 (pulse data within 0.5 s of recording): $(verdict_line "$g1" "$tmp/g1.txt")"
say "Goal 2 (control of power, sensor enabling, modes): $line2"
say "Goal 3 (live pulse to the phone over Bluetooth, under 1 s): $(verdict_line "$g3" "$tmp/g3.txt")"
say "======================================="

# ======================================================================================================================
# Full report: everything the person who ran the test needs, built from this run's own data.
# ======================================================================================================================
table() { # table <file>: "route|screen n min p50 p95 max" -> readable lines
  awk 'BEGIN { FS = "[| ]" } { printf "    %-3s screen=%-4s n=%-4d p50=%8.1f  p95=%8.1f  max=%8.1f ms\n", $1, $2, $3, $5, $6, $7 }' "$1"
}
count_lines() { cat "$@" 2>/dev/null | grep -c . ; }
sum_n() { awk 'BEGIN { FS = "[| ]" } { s += $3 } END { print s + 0 }' "$1"; }

w_total="$(sum_n "$tmp/w.tbl")"
bt_total="$(sum_n "$tmp/bt.tbl")"
dl_total="$(sum_n "$tmp/dl.tbl")"
bt_recv="$(count_lines "$dir"/phone/phone-r[12]-*-bt.csv)"
dl_recv="$(count_lines "$dir"/phone/phone-r[12]-*-dl.csv)"
ctl_n="$(count_lines "$dir"/watch/watch-ctl-*.csv)"
phases="$(ls "$dir"/watch/watch-r[12]-*.csv 2>/dev/null | wc -l | tr -d ' ')"
interval="$(for f in "$dir"/watch/watch-r[12]-*.csv; do
    [ -f "$f" ] && awk -F, '{ print $7 }' "$f" | sort -n | awk 'NR > 1 { printf "%.1f\n", ($1 - p) / 1000000.0 } { p = $1 }'
  done | sort -n | awk '{ a[NR] = $1 } END { if (NR) printf "%.0f", a[int((NR + 1) / 2)] }')"
states1="$(sed -n 's/^  screen states judged: //p' "$tmp/g1.txt" | head -1)"
states3="$(sed -n 's/^  screen states judged: //p' "$tmp/g3.txt" | head -1)"

say ""
say "######################################################################"
say "#                         FULL REPORT                                #"
say "######################################################################"
say "Goal 1: $(verdict_line "$g1" "$tmp/g1.txt")"
say "Goal 2: $line2"
say "Goal 3: $(verdict_line "$g3" "$tmp/g3.txt")"
say ""
say "1. WHAT IS REQUIRED, AND WHAT WAS COVERED"
say "   Required 1: direct access to pulse data, at most 0.5 s from recording."
say "     Covered: sensor routes r1 (Health Services MeasureClient) and r2 (wake-up heart-rate sensor), measured from the"
say "     sensor's own timestamp to the watch app. Phases run: $phases. Screen states with enough readings: ${states1:-none}."
say "     Pass rule: p95 at most 500 ms in every state reached (at least $MIN_N readings each)."
say "   Required 2: control of power, sensor enabling and modes."
say "     Covered by direct commands to the watch (no phone-app control); result of each check:"
grep '^CHECK ' "$dir/report.txt" 2>/dev/null | cut -c1-170 | sed 's/^/       /' | saylines
[ -n "$(grep '^CHECK ' "$dir/report.txt" 2>/dev/null)" ] || say "       (the control checks did not run)"
say "   Required 3: live pulse data to an Android app over Bluetooth, sub-second."
say "     Covered: the same readings sent over direct Bluetooth (RFCOMM) and over the Data Layer; the phone app shows the live"
say "     value and logs every arrival. Judged only on Bluetooth: states ${states3:-none}; pass rule: p95 under 1000 ms."
say ""
say "2. WHAT REMAINS UNKNOWN"
[ -n "$states1" ] && ! echo "$states1" | grep -qE 'doze|off' &&
  say "   - Dim (always-on) and screen-off behaviour: adb did not get the watch into those states in this run."
[ -z "$states1" ] && say "   - Goal 1 latency: no state had enough readings."
[ "$c3" = INCONCLUSIVE ] && say "   - Whether the OS shows our sensor connection while running: not recognised in the dumpsys text; the raw text is saved in dumpsys-sensorservice-running.txt and -stopped.txt in the results."
[ "$c4" = INCONCLUSIVE ] && say "   - Whether the OS lists our wake lock: not recognised in the dumpsys text; the raw text is saved in dumpsys-power-held.txt and -released.txt in the results."
[ "$g3" = "NO DATA" ] && say "   - Whether the watch can open a Bluetooth link to the phone: it did not connect or send in this run (see the causes above)."
say "   - Clock uncertainty of the phone-side numbers: +-${unc_bt:-?} ms on Bluetooth, +-${unc_dl:-?} ms on the Data Layer (half the shortest round trip)."
say "   - That Health Services' time base equals elapsed realtime for route r1: the documentation shows it by example only."
say "   - Which transport (Bluetooth or Wi-Fi) the Data Layer used; only the direct Bluetooth path is known to be Bluetooth."
say "   - Behaviour over a long time, on another wrist or arm position, during motion, or with a low battery."
say ""
say "3. DECISIONS MADE"
say "   - Hardware: the repository's own watch (Galaxy Watch6 Classic) and phone (Galaxy A55); built-in sensor, no extra hardware."
say "   - Two sensor routes compared: r1 Health Services, r2 SensorManager wake-up sensor with foreground service and wake lock."
say "   - Two phone paths sent in parallel for the same readings: direct Bluetooth RFCOMM (proves Bluetooth) and the Data Layer."
say "   - Latency measured as recorded time to arrival; the statistic is p95; a state is judged from at least $MIN_N readings."
say "   - Limits: goal 1 at most 500 ms, goal 3 under 1000 ms on the Bluetooth path only; the Data Layer is never substituted."
say "   - Clock offset between watch and phone: from the shortest-round-trip ping/pong of the whole run, uncertainty printed."
say "   - Control is tested by direct commands to the watch; 'power' means the sensor connection and the CPU wake lock."
say "   - Run length 40 s per phase (about 7 minutes in total) to respect the 10-minute limit."
say ""
say "4. CONSIDERATIONS NOT TAKEN"
say "   - Samsung Health Sensor SDK (raw PPG, inter-beat intervals): not needed for pulse data, needs a Samsung login."
say "   - Bluetooth Low Energy (GATT) instead of classic RFCOMM; Data Layer ChannelClient."
say "   - Real hardware power draw (battery current): out of scope by decision."
say "   - Long-duration, motion, multi-person or multi-watch testing; battery-saver and always-on-display settings."
say "   - Control from the phone app (control is by direct commands on the watch, as decided)."
say ""
say "5. WHAT WAS MEASURED WITH REAL DATA (all numbers below come from this run's files on the watch and phone)"
say "   Recorded -> watch app (goal 1):"
if [ -s "$tmp/w.tbl" ]; then table "$tmp/w.tbl" | saylines; else say "    no readings"; fi
say "   Recorded -> phone over direct Bluetooth (goal 3), clock uncertainty +-${unc_bt:-?} ms:"
if [ -s "$tmp/bt.tbl" ]; then table "$tmp/bt.tbl" | saylines; else say "    no readings arrived over Bluetooth"; fi
say "   Recorded -> phone over the Data Layer (comparison), clock uncertainty +-${unc_dl:-?} ms:"
if [ -s "$tmp/dl.tbl" ]; then table "$tmp/dl.tbl" | saylines; else say "    no readings arrived over the Data Layer"; fi
say ""
say "6. THE LATENCY, IN SHORT (per route; typical = reading-weighted p50 over the states judged, worst = highest p95 / single reading)"
for spec in "Recorded -> watch app (limit 500 ms):$tmp/w.tbl" "Recorded -> phone, Bluetooth (limit 1000 ms):$tmp/bt.tbl" "Recorded -> phone, Data Layer (no limit):$tmp/dl.tbl"; do
  name="${spec%%:*}"; file="${spec##*:}"
  say "   $name"
  if [ -s "$file" ]; then
    awk 'BEGIN { FS = "[| ]" }
      { r = $1; n[r] += $3; ws[r] += $5 * $3; if ($6 > p95[r]) p95[r] = $6; if ($7 > mx[r]) mx[r] = $7 }
      END { for (r in n) printf "     route %s: typical %.0f ms, worst p95 %.0f ms, worst single reading %.0f ms (%d readings)\n", r, ws[r] / n[r], p95[r], mx[r], n[r] }' "$file" | sort | saylines
  else
    say "     no data"
  fi
done
say "   The screen state in these tables is the state when the reading reached the app: a reading recorded while the display"
say "   was dim or off but delivered after it woke counts under 'on' (this is how route r1's late deliveries show up)."
[ -n "$interval" ] && say "   Readings arrive about every $interval ms (median interval between readings on the watch)."
say ""
say "7. HOW MANY SAMPLES"
say "   Watch readings judged for goal 1: $w_total (in $phases phases), plus $ctl_n readings during the control checks."
say "   Phone, Bluetooth: $bt_recv received, $bt_total with a usable clock offset (used for goal 3)."
say "   Phone, Data Layer: $dl_recv received, $dl_total with a usable clock offset."
say "   Each sample is one heart-rate reading; one run, one wrist, about 40 s per phase, so these are samples of the latency, not a long-term guarantee."
say ""
say "8. RUNS RECORDED IN THIS REPOSITORY (readings that reached the watch app, and the phone over Bluetooth / Data Layer)"
for rd in "$(dirname "$dir")"/*/; do
  [ -d "$rd" ] || continue
  rn="$(basename "$rd")"
  wn="$(count_lines "$rd"watch/watch-r[12]-*.csv)"; bn="$(count_lines "$rd"phone/phone-r[12]-*-bt.csv)"; dn="$(count_lines "$rd"phone/phone-r[12]-*-dl.csv)"
  mark=""; [ "$(cd "$rd" && pwd)" = "$(cd "$dir" && pwd)" ] && mark="   <- this run"
  say "   $rn: watch $wn, Bluetooth $bn, Data Layer $dn$mark"
done
say ""
say "9. WHAT THE LIVE INDICATOR ON THE PHONE SHOWED (one line every 5 s, from phone/live-log.txt)"
if [ -s "$dir/phone/live-log.txt" ]; then
  total_lines="$(wc -l <"$dir/phone/live-log.txt" | tr -d ' ')"
  say "   $total_lines snapshots; first, middle and last:"
  sed -n "1p;$(( (total_lines + 1) / 2 ))p;${total_lines}p" "$dir/phone/live-log.txt" | cut -c1-170 | sed 's/^/     /' | saylines
  say "   (the screen also shows accuracy, route, screen state, latency per path, min/avg/max, trend, mean beat interval and a"
  say "   variability proxy; the indicator turns green when a reading is under 3 s old, amber under 10 s, red otherwise)"
else
  say "   no snapshot was logged (no reading reached the phone, or the phone app was not running)"
fi
say ""
say "10. PPG, IBI AND HRV: CAN THEY BE CALCULATED FROM THE DATA?"
bpm_stats="$(for f in "$dir"/watch/watch-r2-*.csv; do [ -f "$f" ] && cat "$f"; done | awk -F, -v min="$MIN_SOURCE_NANOS" '
  NF >= 8 && $6 >= min && $4 > 0 { n++; v[$4] = 1; s += $4; iv = 60000 / $4; si += iv; sq += iv * iv; vals[n] = iv }
  END { if (n > 1) { m = si / n; sd = sqrt((sq - n * m * m) / (n - 1)); d = 0; for (k in v) d++; printf "%d %d %.1f %.0f %.1f", n, d, s / n, m, sd } }')"
say " 1. From the beats-per-minute stream that reaches the phone:"
if [ -n "$bpm_stats" ]; then
  set -- $bpm_stats
  say "    $1 readings (route r2), $2 different values (whole beats per minute), about one reading every ${interval:-?} ms, mean $3 bpm."
  say "    Mean beat interval 60000/bpm: $4 ms. Spread of that series (SDNN of the bpm series): $5 ms."
else
  say "    no readings to analyse"
fi
say "    PPG: no. The stream carries one number per second, not a waveform."
say "    IBI (beat-to-beat interval): no. Only the average interval 60000/bpm can be derived, not the time between two beats."
say "    HRV: no. The spread above is the pulse changing from one reading to the next (and the readings are rounded and already"
say "    averaged by the watch), not variability between beats; it must not be reported as HRV."
say " 2. From the raw heart sensor probe (the watch lists a 'Samsung HR Raw Sensor' that apps can ask for):"
rawsum="$dir/watch/watch-summary-raw-on.txt"
if [ -f "$rawsum" ]; then
  sed -n '/^raw/p' "$rawsum" | cut -c1-230 | sed 's/^/    /' | saylines
  rev="$(sed -n 's/^raw events=\([0-9]*\).*/\1/p' "$rawsum" | head -1)"
  rrate="$(sed -n 's/.* rate=\([0-9.]*\) Hz.*/\1/p' "$rawsum" | head -1)"
  if [ "${rev:-0}" -eq 0 ]; then
    say "    Result: the raw sensor delivered NO events to this app. PPG, IBI and HRV are not available to an ordinary app on this"
    say "    watch; they would need Samsung's Health Sensor SDK (developer mode, Samsung login), which this POC did not use."
  elif awk -v r="${rrate:-0}" 'BEGIN { exit !(r >= 20) }'; then
    say "    Result: a raw waveform IS available: $rev events at ${rrate} Hz. That is enough to resolve single beats, so PPG is"
    say "    available and beat intervals and HRV can be computed from it; the numbers computed on the watch are in the channel lines"
    say "    above (the channel that carries the pulse shows a believable beat count and mean heart rate close to the bpm above)."
  else
    say "    Result: the raw sensor delivered $rev events at only ${rrate} Hz: too slow to resolve single beats, so no reliable IBI/HRV."
  fi
else
  say "    the raw probe did not run in this run"
fi
say ""
say "=============== RESULT ==============="
say "Goal 1 (pulse data within 0.5 s of recording): $(verdict_line "$g1" "$tmp/g1.txt")"
say "Goal 2 (control of power, sensor enabling, modes): $line2"
say "Goal 3 (live pulse to the phone over Bluetooth, under 1 s): $(verdict_line "$g3" "$tmp/g3.txt")"
say "======================================="
