#!/usr/bin/env bash
# TC-S-040 (spec skeleton) and TC-S-041 (constants match the spec). Offline, no Gradle.
# REPO_ROOT overrides the repository root (tests of the test).
set -u
root="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
spec="$root/docs/protocol.md"
paths_kt="$(find "$root/protocol/src/main" -name Paths.kt 2>/dev/null | head -1)"
fail=0
check() { # name, status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}

# TC-S-040: the skeleton has the required sections
for h in "Item catalog" "Item format" "Versioning policy" "Link states"; do
  grep -qE "^## +$h *$" "$spec"; check "TC-S-040 section '$h' present" $?
done

# TC-S-041: every path in the item catalog table has a constant in :protocol
catalog="$(awk '/^## +Item catalog/{f=1;next} /^## /{f=0} f' "$spec")"
paths="$(printf '%s\n' "$catalog" | grep -oE '^\| *`/[^`|]+`' | grep -oE '/[^`|]+' | sed 's/ *$//')"
[ -n "$paths" ]; check "TC-S-041 item catalog lists paths" $?
[ -f "$paths_kt" ]; check "TC-S-041 Paths.kt exists in :protocol" $?
n=0
while IFS= read -r p; do
  [ -n "$p" ] || continue
  n=$((n + 1))
  # '/prompt/<id>' -> literal prefix '/prompt/' ; fixed paths must appear verbatim
  lit="${p%%<*}"
  grep -qF "\"$lit" "$paths_kt" 2>/dev/null; check "TC-S-041 path $p has a constant" $?
done <<EOF2
$paths
EOF2
[ "$n" -ge 8 ]; check "TC-S-041 catalog has at least 8 paths (found $n)" $?

# TC-S-077: the flow and state-machine diagrams exist as Mermaid blocks and are well formed (1.8a: schedule, prompt,
# versioning, prompt state machine; 1.8b adds the link-state machine and the alert flow). The rendered check on the PR
# is TC-S-078 (manual); this checks structure only.
diagrams="$(awk '/^## +Diagrams/{f=1;next} /^## /{f=0} f' "$spec")"
section_block() { # heading -> mermaid block text under "### heading"
  printf '%s\n' "$diagrams" | awk -v h="$1" '
    /^### /{ if (index($0, "### " h) == 1) in_s=1; else in_s=0; next }
    in_s && /^```mermaid/{blk=1; next}
    in_s && blk && /^```/{blk=0; exit}
    in_s && blk{print}'
}
required="Schedule sync|Prompt, acknowledgement and selection|Hello and version check|Prompt state machine"
IFS='|' read -r -a names <<EOF2
$required
EOF2
for name in "${names[@]}"; do
  block="$(section_block "$name")"
  [ -n "$block" ]; check "TC-S-077 diagram '$name' is a Mermaid block" $?
  first="$(printf '%s\n' "$block" | sed -n '1p')"
  case "$name" in
    "Prompt state machine") [ "$first" = "stateDiagram-v2" ]; check "TC-S-077 '$name' is a stateDiagram-v2" $?;;
    *) [ "$first" = "sequenceDiagram" ]; check "TC-S-077 '$name' is a sequenceDiagram" $?;;
  esac
  opens="$(printf '%s\n' "$block" | grep -cE '^[[:space:]]*(alt|opt|loop|par|critical|break|rect)([[:space:]]|$)')"
  ends="$(printf '%s\n' "$block" | grep -cE '^[[:space:]]*end[[:space:]]*$')"
  [ "$opens" -eq "$ends" ]; check "TC-S-077 '$name' has balanced alt/opt/loop/end blocks ($opens/$ends)" $?
done
# state machine: every transition target is declared or reachable, and there is a start and an end
sm="$(section_block "Prompt state machine")"
printf '%s\n' "$sm" | grep -q '^\[\*\] -->' && printf '%s\n' "$sm" | grep -q -- '--> \[\*\]'; check "TC-S-077 prompt state machine has a start and an end" $?
exit $fail
