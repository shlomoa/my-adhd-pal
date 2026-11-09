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
# Goal 2: control checks C1 C2 C4 PASS, C3 not FAIL, and one mode check (C5 or C6) PASS
# Goal 3: p95 recorded -> phone over BLUETOOTH under 1000 ms (the Data Layer is reported, never substituted)
# A goal is MET when one route meets it in every screen state it was observed in (at least MIN_N readings),
# PARTIAL when it only meets it with the screen on, NOT MET when there is data but it never meets it, NO DATA
# without enough readings, and INVALID CLOCK BASE when latencies are negative or hours long.
set -u
export LC_ALL=C
dir="${1:?usage: verdict.sh <results-dir>}"
MIN_N="${MIN_N:-20}"
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
    awk -F, 'NF >= 8 { printf "%s|%s %.3f\n", $2, $8, ($7 - $6) / 1000000.0 }'
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
    awk -F, -v ex="$ex" '
      BEGIN {
        if (ex != "") {
          while ((getline line < ex) > 0) {
            split(line, a, ",")
            rtt = a[3] - a[1]
            if (rtt >= 0 && (best == "" || rtt < bestrtt)) { bestrtt = rtt; best = a[2] - (a[1] + a[3]) / 2 }
          }
        }
      }
      NF >= 8 {
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
      for (s in judged) states = states (states == "" ? "" : ",") s
      if (usable) print "  screen states judged: " states
      if (usable && !("doze" in judged) && !("off" in judged))
        print "  NOTE: the dim and off states were not reached (or had too few readings): this holds for the screen-on state only"
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
elif [ "$c1" = PASS ] && [ "$c2" = PASS ] && [ "$c4" = PASS ] && [ "$c3" != FAIL ] && { [ "$c5" = PASS ] || [ "$c6" = PASS ]; }; then
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
  case "$1" in
    MET*) if grep -q 'screen-on state only' "$2" 2>/dev/null; then echo "ACHIEVED (screen on only; dim/off not reached)"; else echo "ACHIEVED"; fi ;;
    PARTIAL*) echo "FAILED (only with the screen on)" ;;
    "NOT MET"*) echo "FAILED (limit missed)" ;;
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
