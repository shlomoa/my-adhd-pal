#!/usr/bin/env bash
# CI gate check for a PR. Inputs (env): PR_BODY with "Requires: G0,G1|none" and optional "Produces: G2".
# Exit 0 ok, 2 body malformed, 3 a required gate is not merged on the base, 4 a gate other than Produces changed.
# BASE_GATES_FILE / HEAD_GATES_FILE override the gates.json of the base and the PR head (tests).
set -u
here="$(cd "$(dirname "$0")" && pwd)"
req_line="$(printf '%s\n' "${PR_BODY:-}" | grep -iE '^Requires:' | head -1)"
[ -n "$req_line" ] || { echo "PR body needs a 'Requires: G0,G1' (or 'Requires: none') line" >&2; exit 2; }
req="$(printf '%s' "${req_line#*:}" | tr -d ' ' | tr ',' ' ')"
prod="$(printf '%s\n' "${PR_BODY:-}" | grep -iE '^Produces:' | head -1)"
prod="$(printf '%s' "${prod#*:}" | tr -d ' ' | tr ',' ' ')"
if [ -n "$req" ] && [ "$req" != "none" ]; then
  # shellcheck disable=SC2086
  GATES_FILE="${BASE_GATES_FILE:-}" "$here/require-gate.sh" $req || exit 3
fi
# A base without gates.json (the bootstrap PR) counts as "every gate open".
if [ -n "${BASE_GATES_FILE:-}" ]; then base_json="$(cat "$BASE_GATES_FILE" 2>/dev/null)"
else base_json="$(git show "${BASE_REF:-origin/main}:docs/plans/gates.json" 2>/dev/null)"; fi
[ -n "$base_json" ] || base_json='{"gates":[]}'
head_json="$(cat "${HEAD_GATES_FILE:-docs/plans/gates.json}")"
changed="$(jq -rn --argjson b "$base_json" --argjson h "$head_json" \
  '[$h.gates[] as $g | select((first($b.gates[] | select(.id == $g.id) | .status) // "open") != $g.status) | $g.id] | .[]' 2>/dev/null)"
for id in $changed; do
  case " $prod " in *" $id "*) ;; *) echo "gate $id changed but is not in Produces" >&2; exit 4;; esac
done
exit 0
