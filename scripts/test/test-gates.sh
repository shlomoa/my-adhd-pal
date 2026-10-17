#!/usr/bin/env bash
# TC-S-143: the gate graph in docs/plans/gates.json is consistent with docs/plans/README.md and the lane plans, and G2 is
# split into G2a (transport-neutral protocol logic, needs G1) and G2b (link-state function, needs G-DEC).
# REPO_ROOT overrides the repository root (tests of the test). Needs jq.
set -u
root="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
gates="$root/docs/plans/gates.json"
readme="$root/docs/plans/README.md"
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}
req() { jq -r --arg id "$1" '[.gates[] | select(.id == $id) | .requires[]] | sort | join(",")' "$gates"; }
ids="$(jq -r '.gates[].id' "$gates")"
human="H1 H2"

# --- graph shape
[ "$(printf '%s\n' "$ids" | sort | uniq -d | wc -l)" -eq 0 ]; check "TC-S-143 gate ids are unique" $?
bad=""
for id in $ids; do
  for r in $(jq -r --arg id "$id" '.gates[] | select(.id == $id) | .requires[]' "$gates"); do
    printf '%s\n%s\n' "$ids" "$human" | grep -qx "$r" || bad="$bad $id->$r"
  done
done
[ -z "$bad" ]; check "TC-S-143 every required gate exists${bad:+ (unknown:$bad)}" $?
bad="$(jq -r '.gates[] | select(.status != "open" and .status != "merged") | .id' "$gates")"
[ -z "$bad" ]; check "TC-S-143 every status is open or merged${bad:+ ($bad)}" $?
# acyclic: repeatedly drop gates whose requirements are all dropped
left="$(printf '%s\n' "$ids")"; progress=1
while [ -n "$left" ] && [ "$progress" -eq 1 ]; do
  progress=0; next=""
  for id in $left; do
    ok=1
    for r in $(jq -r --arg id "$id" '.gates[] | select(.id == $id) | .requires[]' "$gates"); do
      printf '%s\n' "$left" | grep -qx "$r" && ok=0
    done
    if [ "$ok" -eq 1 ]; then progress=1; else next="$next$id"$'\n'; fi
  done
  left="$(printf '%s' "$next" | sed '/^$/d')"
done
[ -z "$left" ]; check "TC-S-143 the gate graph has no cycle" $?

# --- README table equals gates.json (columns: Gate | Exit criteria | Owner | Requires)
for id in $ids; do
  row="$(grep -E "^\| \*\*$id\*\*[^|]* \|" "$readme" | head -1)"
  if [ -z "$row" ]; then check "TC-S-143 README has a row for $id" 1; continue; fi
  cell="$(printf '%s' "$row" | awk -F'|' '{gsub(/^ +| +$/, "", $5); print $5}')"
  want="$(req "$id" | tr ',' '\n' | sed '/^$/d' | sort | paste -sd, -)"
  have="$(printf '%s' "$cell" | tr ',' '\n' | sed 's/^ *//; s/ *$//' | sed '/^none$/d;/^$/d' | sort | paste -sd, -)"
  [ "$want" = "$have" ]; check "TC-S-143 README Requires of $id equals gates.json (${want:-none})" $?
done
for id in $(grep -oE '^\| \*\*(G[-A-Z0-9]+)\*\* \|' "$readme" | grep -oE 'G[-A-Z0-9]+'); do
  printf '%s\n' "$ids" | grep -qx "$id"; check "TC-S-143 README gate $id exists in gates.json" $?
done

# --- lane plans only name gates that exist
bad=""
for f in "$root"/docs/plans/shared.md "$root"/docs/plans/android.md "$root"/docs/plans/watch.md "$root"/docs/plans/docs.md; do
  for g in $(grep -oE '\bG(-[A-Z]+[0-9]*|[0-9]+[ab]?)\b' "$f" | sort -u); do
    printf '%s\n' "$ids" | grep -qx "$g" || bad="$bad $(basename "$f"):$g"
  done
done
[ -z "$bad" ]; check "TC-S-143 lane plans name only existing gates${bad:+ (unknown:$bad)}" $?

# --- the G2 split
printf '%s\n' "$ids" | grep -qx G2; r=$?
[ "$r" -ne 0 ]; check "TC-S-143 gate G2 is replaced by G2a and G2b" $?
expect() { # name want have
  if [ "$2" = "$3" ]; then echo "PASS TC-S-143 $1 ($3)"; else echo "FAIL TC-S-143 $1 (expected '$2', got '$3')"; fail=1; fi
}
expect "G2a requires G1 only" "G1" "$(req G2a)"
expect "G2b requires G2a and G-DEC" "G-DEC,G2a" "$(req G2b)"
expect "G3a requires G2a" "G2a" "$(req G3a)"
expect "G3 (real Data Layer) requires G3a, G-LIB and G2b" "G-LIB,G2b,G3a" "$(req G3)"
expect "G4 requires G3a and G-LIB" "G-LIB,G3a" "$(req G4)"
expect "G5 requires G2a" "G2a" "$(req G5)"

# --- deferred steps (user decision 2026-10-17, wave 0 row C3): schedule, scheduler, schedule builder, tasks and tile
# work is deferred; the gate keeps its id, and its exit criteria are its steps minus the deferred ones, so G6 can close
# on 4.3, 4.4 and 4.6. gates.json lists them in a top-level "deferred" array of {step, gate, reason}.
deferred() { jq -r --arg g "$1" '[(.deferred // [])[] | select(.gate == $g) | .step] | sort | join(",")' "$gates"; }
jq -e '(.deferred | type) == "array"' "$gates" >/dev/null 2>&1; check "TC-S-183 gates.json has a deferred array" $?
jq -e '[.deferred[]? | select((.step | type) != "string" or ((.reason // "") | length) == 0)] | length == 0' "$gates" >/dev/null 2>&1
check "TC-S-183 every deferred entry has a step and a reason" $?
bad="$(jq -r --argjson ids "$(jq -c '[.gates[].id]' "$gates")" '.deferred[]? | select(.gate != null and (.gate as $g | $ids | index($g) | not)) | .step' "$gates" 2>/dev/null)"
[ -z "$bad" ] && jq -e '.deferred | type == "array"' "$gates" >/dev/null 2>&1; check "TC-S-183 every deferred gate exists${bad:+ (unknown for steps: $bad)}" $?
all="$(jq -r '[(.deferred // [])[] | .step] | sort | join(",")' "$gates")"
expect "deferred steps are 3.3, 3.4, 3.9, 4.1, 4.2, 5.2 and 5.3" "3.3,3.4,3.9,4.1,4.2,5.2,5.3" "$all"
expect "G5 defers 3.3 and 3.4" "3.3,3.4" "$(deferred G5)"
expect "G6 defers 4.1 and 4.2 only, so 4.3, 4.4 and 4.6 can close it" "4.1,4.2" "$(deferred G6)"
expect "G7 defers 3.9" "3.9" "$(deferred G7)"
expect "the deferral changes no requirement: G6 requires G3 and G4" "G3,G4" "$(req G6)"
expect "the deferral changes no requirement: G7 requires G3, G4, G5 and H2" "G3,G4,G5,H2" "$(req G7)"

# --- gates for the live route (wave 0 row C3, issue 223): E2 verifies the live route against the user's definition
# (issue 229, lane D with W); the live measure service (6.4, issue 57) comes after it. Both start open.
owner() { jq -r --arg id "$1" '[.gates[] | select(.id == $id) | .owner] | first // ""' "$gates"; }
status() { jq -r --arg id "$1" '[.gates[] | select(.id == $id) | .status] | first // ""' "$gates"; }
expect "TC-S-184 G-E2 (live route verified against the user's definition) requires G5 and H2" "G5,H2" "$(req G-E2)"
expect "TC-S-184 G-E2 is owned by D + W" "D + W" "$(owner G-E2)"
expect "TC-S-184 G-E2 starts open" "open" "$(status G-E2)"
expect "TC-S-184 G-LIVE (live measure service, 6.4) requires G-E2 and G5" "G-E2,G5" "$(req G-LIVE)"
expect "TC-S-184 G-LIVE is owned by W" "W" "$(owner G-LIVE)"
expect "TC-S-184 G-LIVE starts open" "open" "$(status G-LIVE)"
expect "TC-S-184 the deferred steps are unchanged by the new gates" "3.3,3.4,3.9,4.1,4.2,5.2,5.3" "$(jq -r '[.deferred[].step] | sort | join(",")' "$gates")"
exit $fail
