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
for f in "$root"/docs/plans/shared.md "$root"/docs/plans/android.md "$root"/docs/plans/watch.md; do
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
exit $fail
