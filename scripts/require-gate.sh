#!/usr/bin/env bash
# Usage: scripts/require-gate.sh <gate-id>...
# Exit 0 if every gate is "merged" in origin/main:docs/plans/gates.json.
# Exit 3 if any gate is not merged (the calling thread must STOP), 2 on usage/unknown gate.
# GATES_FILE overrides the source (tests); BASE_REF overrides origin/main (CI uses the PR base).
set -u
[ "$#" -ge 1 ] || { echo "usage: $0 <gate-id>..." >&2; exit 2; }
if [ -n "${GATES_FILE:-}" ]; then
  json="$(cat "$GATES_FILE")" || exit 2
else
  ref="${BASE_REF:-origin/main}"
  git fetch -q origin "${ref#origin/}" 2>/dev/null || true
  json="$(git show "$ref:docs/plans/gates.json")" || { echo "cannot read gates.json from $ref" >&2; exit 2; }
fi
blocked=0
for id in "$@"; do
  row="$(printf '%s' "$json" | jq -c --arg id "$id" '.gates[] | select(.id == $id)')"
  [ -n "$row" ] || { echo "unknown gate: $id" >&2; exit 2; }
  if [ "$(printf '%s' "$row" | jq -r .status)" != "merged" ]; then
    echo "BLOCKED: gate $id ($(printf '%s' "$row" | jq -r .name)) is not merged; owner $(printf '%s' "$row" | jq -r .owner)" >&2
    blocked=1
  fi
done
[ "$blocked" -eq 0 ] || { echo "STOP: commit WIP, log BLOCKED in docs/work-log.md, push, end the turn." >&2; exit 3; }
