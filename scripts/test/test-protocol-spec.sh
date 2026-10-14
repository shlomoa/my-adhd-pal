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
exit $fail
