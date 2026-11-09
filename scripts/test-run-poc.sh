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
  log="$tmp/$name.log"
  (cd "$root" && ADB="$here/testdata/fake-adb.py" SKIP_BUILD=1 TIME_SCALE=0.002 PHASE_SECONDS=40 RESULTS_ROOT="$tmp/$name-results" \
    scripts/run-poc.sh --no-push "${extra[@]}") >"$log" 2>&1
  got=$?
  ok=1
  [ "$got" = "$want" ] || { ok=0; echo "FAIL [$name]: exit $got, expected $want"; }
  for pat in "${pats[@]}"; do
    grep -qF -- "$pat" "$log" || { ok=0; echo "FAIL [$name]: output lacks '$pat'"; }
  done
  if [ $ok = 1 ]; then echo "ok   [$name]"; else fail=1; sed 's/^/    /' "$log" | tail -40; fi
}

run_case good 0 -- "phone Bluetooth server: listening" "CHECK C1 PASS" "CHECK C2 PASS" "CHECK C3 PASS" "CHECK C4 PASS" \
  "CHECK C5 PASS" "CHECK C6 PASS" "GOAL 1: MET" "GOAL 2: MET" "GOAL 3 (Bluetooth): MET" "OVERALL: SUCCESS" \
  "Goal 1 (pulse data within 0.5 s of recording): ACHIEVED (screen on only" "modes): ACHIEVED" "under 1 s): ACHIEVED (screen on only"
run_case slow 0 -- "GOAL 1: NOT MET" "OVERALL: FAILURE"
run_case zero 4 -- "STOP: no readings at all" "OFF-BODY" "GOAL 1: NO DATA" "OVERALL: INCOMPLETE"
run_case nobt 0 -- "GOAL 3 (Bluetooth): NO DATA" "Data Layer: MET" "OVERALL: INCOMPLETE"
run_case installfail 3 -- "watch install: Failure [INSTALL_FAILED_FAKE]" "is not installed after the install"
run_case noperm 0 -- "watch NOT granted android.permission.health.READ_HEART_RATE"
run_case nophone 0 -- "phone Bluetooth server: NOT listening"
run_case smoke 0 --smoke -- "r1-on:" "r2-on:" "OVERALL"
exit $fail
