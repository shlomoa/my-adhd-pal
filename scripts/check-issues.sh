#!/usr/bin/env bash
# Every plan.md step has exactly one issue, labelled with its milestone and one lane, starting with a Validation plan.
#   scripts/check-issues.sh <issues.json> [plan.md]   issues.json is `gh issue list --state all --limit 300
#                                                     --json number,title,state,stateReason,labels,body`
#   scripts/check-issues.sh --list-ids [plan.md]      print the step ids found in plan.md, one per line
# Closed issues with reason NOT_PLANNED (duplicates) are ignored.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ids() { # plan.md -> step ids in order, without repeats
  grep -oE '\*\*[0-9]+[ab]?\.[0-9]+[ab]?[ *.:]' "$1" | sed -E 's/^\*\*([0-9]+[ab]?\.[0-9]+[ab]?).*/\1/' | awk '!seen[$0]++'
}
if [ "${1:-}" = "--list-ids" ]; then ids "${2:-$here/../docs/plan.md}"; exit 0; fi
[ "$#" -ge 1 ] || { echo "usage: $0 <issues.json> [plan.md] | --list-ids [plan.md]" >&2; exit 2; }
issues="$1"; plan="${2:-$here/../docs/plan.md}"
status=0
problem() { echo "check-issues: $*"; status=1; }
while read -r id; do
  milestone="${id%%.*}"
  matches="$(jq -c --arg id "$id" '[.[] | select(.stateReason != "NOT_PLANNED") | select(.title == $id or (.title | startswith($id + " ")))]' "$issues")"
  count="$(jq 'length' <<<"$matches")"
  if [ "$count" -eq 0 ]; then problem "missing issue for step $id"; continue; fi
  if [ "$count" -gt 1 ]; then problem "duplicate issues for step $id: $(jq -r 'map("#" + (.number | tostring)) | join(", ")' <<<"$matches")"; continue; fi
  n="$(jq -r '.[0].number' <<<"$matches")"
  jq -e --arg m "milestone:$milestone" '.[0].labels | map(.name) | index($m)' <<<"$matches" >/dev/null \
    || problem "issue #$n (step $id) lacks the milestone label milestone:$milestone"
  [ "$(jq '[.[0].labels[].name | select(test("^lane:[SAWDV]$"))] | length' <<<"$matches")" -eq 1 ] \
    || problem "issue #$n (step $id) needs exactly one lane label (lane:S, lane:A, lane:W, lane:D or lane:V)"
  jq -e '.[0].body | split("\n") | map(select(test("\\S"))) | .[0] // "" | test("^## Validation plan")' <<<"$matches" >/dev/null \
    || problem "issue #$n (step $id) does not start with a Validation plan section"
done < <(ids "$plan")
exit "$status"
