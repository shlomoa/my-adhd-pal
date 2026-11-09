#!/usr/bin/env bash
# Conventions (step 0a.7): issue template, issues per plan.md step, labels, work-log entry format.
# Run: scripts/test/test-conventions.sh
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}
run() { "$@" >"$tmp/out" 2>&1; }
says() { grep -qiF -- "$1" "$tmp/out"; }

# TC-S-022 issue template with a Validation plan section (rendering on GitHub is a manual check)
t=.github/ISSUE_TEMPLATE/step.md
[ -f "$t" ] && head -1 "$t" | grep -q '^---$' && grep -q '^## Validation plan' "$t"; check "TC-S-022 issue template has front matter and a Validation plan section" 0 $?
grep -q 'Pass' "$t" 2>/dev/null && grep -q 'How' "$t" 2>/dev/null; check "TC-S-022 template asks what, how and pass criteria" 0 $?

# TC-S-023 and TC-S-024: one issue per plan.md step, labelled with milestone and lane, starting with a Validation plan
cat > "$tmp/plan.md" <<'MD'
- **0a.1 Baseline.** text
- **0b.2a Spike (required, first).** text
- **4.1** Create. **4.2** Build.
MD
issue() { # number title labels(csv) body [state] [reason]
  jq -n --argjson n "$1" --arg t "$2" --arg l "$3" --arg b "$4" --arg s "${5:-OPEN}" --arg r "${6:-}" \
    '{number:$n,title:$t,state:$s,stateReason:$r,labels:($l|split(",")|map({name:.})),body:$b}'
}
VP=$'## Validation plan\n- What: x'
good() { jq -s '.' <(issue 1 "0a.1 Baseline" "milestone:0a,lane:S" "$VP") <(issue 2 "0b.2a Spike" "milestone:0b,lane:S" "$VP") \
  <(issue 3 "4.1 Create" "milestone:4,lane:A" "$VP") <(issue 4 "4.2 Build" "milestone:4,lane:A" "$VP"); }
good > "$tmp/good.json"
run scripts/check-issues.sh "$tmp/good.json" "$tmp/plan.md"; check "TC-S-023 complete, labelled issues pass" 0 $?

jq '. + [{"number":9,"title":"0a.1 Baseline again","state":"OPEN","stateReason":"","labels":[{"name":"milestone:0a"},{"name":"lane:S"}],"body":"## Validation plan\nx"}]' "$tmp/good.json" > "$tmp/dup.json"
run scripts/check-issues.sh "$tmp/dup.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says duplicate && says 0a.1; check "TC-S-023 duplicate issue for a step fails and names the step" 0 $?

jq '. + [{"number":9,"title":"0a.1 Baseline again","state":"CLOSED","stateReason":"NOT_PLANNED","labels":[],"body":""}]' "$tmp/good.json" > "$tmp/closed-dup.json"
run scripts/check-issues.sh "$tmp/closed-dup.json" "$tmp/plan.md"; check "TC-S-023 a closed not-planned duplicate is ignored" 0 $?

jq 'map(select(.number != 2))' "$tmp/good.json" > "$tmp/missing.json"
run scripts/check-issues.sh "$tmp/missing.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says missing && says 0b.2a; check "TC-S-023 missing issue fails and names the step" 0 $?

jq 'map(if .number == 3 then .labels = [{"name":"lane:A"}] else . end)' "$tmp/good.json" > "$tmp/nomilestone.json"
run scripts/check-issues.sh "$tmp/nomilestone.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says milestone && says 4.1; check "TC-S-024 missing milestone label fails" 0 $?

jq 'map(if .number == 3 then .labels = [{"name":"milestone:4"}] else . end)' "$tmp/good.json" > "$tmp/nolane.json"
run scripts/check-issues.sh "$tmp/nolane.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says lane && says 4.1; check "TC-S-024 missing lane label fails" 0 $?

jq 'map(if .number == 3 then .labels = [{"name":"milestone:5"},{"name":"lane:A"}] else . end)' "$tmp/good.json" > "$tmp/wrongms.json"
run scripts/check-issues.sh "$tmp/wrongms.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says milestone && says 4.1; check "TC-S-024 wrong milestone label fails" 0 $?

jq 'map(if .number == 4 then .body = "No plan here" else . end)' "$tmp/good.json" > "$tmp/novp.json"
run scripts/check-issues.sh "$tmp/novp.json" "$tmp/plan.md"; rc=$?
[ "$rc" -eq 1 ] && says "validation plan" && says 4.2; check "TC-S-023 an issue not starting with a Validation plan fails" 0 $?

run scripts/check-issues.sh --list-ids docs/plan.md; rc=$?
[ "$rc" -eq 0 ] && [ "$(wc -l < "$tmp/out")" -eq 59 ] && says 0b.2a && says 7.6 && says 6.5; check "TC-S-023 plan.md yields 59 step ids" 0 $?

# TC-S-025 work-log entry format
cat > "$tmp/log-good.md" <<'MD'
# Work log: x

## 2026-10-11 - 0a.8 Title

- Issue and PR: #1
- Changed: a
- Validation: b
- Open items: c
MD
run scripts/check-work-log.sh "$tmp/log-good.md"; check "TC-S-025 a complete entry passes" 0 $?
sed '/^- Validation/d' "$tmp/log-good.md" > "$tmp/log-nov.md"
run scripts/check-work-log.sh "$tmp/log-nov.md"; rc=$?
[ "$rc" -eq 1 ] && says Validation; check "TC-S-025 an entry without Validation fails" 0 $?
sed 's/^## 2026-10-11 - /## /' "$tmp/log-good.md" > "$tmp/log-nohead.md"
run scripts/check-work-log.sh "$tmp/log-nohead.md"; rc=$?
[ "$rc" -eq 1 ] && says heading; check "TC-S-025 an entry heading without date and step fails" 0 $?
run scripts/check-work-log.sh docs/work-log/shared.md; check "TC-S-025 the shared work log follows the format" 0 $?
run scripts/check-work-log.sh docs/work-log/docs.md; check "TC-D-010 the docs work log follows the format" 0 $?

# TC-D-011 lane D (documentation) label is accepted
jq 'map(if .number == 3 then .labels = [{"name":"milestone:4"},{"name":"lane:D"}] else . end)' "$tmp/good.json" > "$tmp/laned.json"
run scripts/check-issues.sh "$tmp/laned.json" "$tmp/plan.md"; check "TC-D-011 a lane:D label is accepted" 0 $?

# TC-V-002 lane V (validation) label is accepted and its work log follows the format
jq 'map(if .number == 3 then .labels = [{"name":"milestone:4"},{"name":"lane:V"}] else . end)' "$tmp/good.json" > "$tmp/lanev.json"
run scripts/check-issues.sh "$tmp/lanev.json" "$tmp/plan.md"; check "TC-V-002 a lane:V label is accepted" 0 $?
run scripts/check-work-log.sh docs/work-log/validation.md; check "TC-V-002 the validation work log follows the format" 0 $?
exit $fail
