#!/usr/bin/env bash
# Usage: scripts/check-test-cases.sh <lane> baseline|final
# Catalogue: docs/test-plans/<lane>.md lists case ids (TC-<letter>-nnn).
# baseline: every id appears in a test source file.
# final:    baseline + docs/test-reports/<lane>-final.md has "<id> PASS" for every id,
#           and no @Ignore/@Disabled/assumeTrue(false) appears in test sources.
# REPO_ROOT overrides the repository root (tests).
set -u
[ "$#" -eq 2 ] || { echo "usage: $0 <lane> baseline|final" >&2; exit 2; }
lane="$1"; mode="$2"
root="${REPO_ROOT:-$(git rev-parse --show-toplevel)}"
catalogue="$root/docs/test-plans/$lane.md"
[ -f "$catalogue" ] || { echo "missing catalogue $catalogue" >&2; exit 1; }
ids="$(grep -oE 'TC-[A-Z]-[0-9]{3}' "$catalogue" | sort -u)"
[ -n "$ids" ] || { echo "no case ids in $catalogue" >&2; exit 1; }
tests="$(grep -rlE --include='*.kt' --include='*.java' --include='*.sh' 'TC-[A-Z]-[0-9]{3}' "$root" --exclude-dir=.git --exclude-dir=build 2>/dev/null)"
rc=0
for id in $ids; do
  # shellcheck disable=SC2086
  if [ -z "$tests" ] || ! grep -qF "$id" $tests; then echo "no test for $id"; rc=1; fi
done
if [ "$mode" = final ]; then
  report="$root/docs/test-reports/$lane-final.md"
  [ -f "$report" ] || { echo "missing $report"; exit 1; }
  for id in $ids; do grep -qE "$id +PASS" "$report" || { echo "$id not PASS in report"; rc=1; }; done
  # skipped-test markers are only meaningful in Kotlin/Java sources (shell fixtures may mention them)
  jvm="$(printf '%s\n' "$tests" | grep -E '\.(kt|java)$')"
  # shellcheck disable=SC2086
  if [ -n "$jvm" ] && grep -nE '@Ignore|@Disabled|assumeTrue\(false\)' $jvm; then echo "skipped tests found"; rc=1; fi
fi
exit $rc
