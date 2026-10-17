#!/usr/bin/env bash
# Keeps one open issue labelled main-red in step with the post-merge script tests (issue 190).
# Run: scripts/ci-post-merge/main-red-issue.sh red|green <report.md> <run-url>   Needs gh and GH_TOKEN with issues: write.
set -eu
state="${1:?red or green}"
report="${2:?report file}"
run_url="${3:?run url}"
label=main-red
title="main is red: script tests failed after a merge"
open="$(gh issue list --label "$label" --state open --json number | grep -o '"number": *[0-9]*' | head -n 1 | grep -o '[0-9]*$' || true)"
if [ "$state" = green ]; then
  [ -z "$open" ] || gh issue close "$open" --comment "The script tests are green again: $run_url"
  exit 0
fi
body="$(cat "$report")

Run: $run_url"
if [ -n "$open" ]; then
  gh issue edit "$open" --body "$body"
else
  gh label create "$label" --color B60205 --description "The script tests failed on main" >/dev/null 2>&1 || true
  gh issue create --label "$label" --title "$title" --body "$body"
fi
