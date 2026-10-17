#!/usr/bin/env bash
# Runs the slow script tests after a merge to main (issue 190) and writes a report of what failed.
# Run: scripts/ci-post-merge/run-tests.sh   Output: $POST_MERGE_OUT (default build/post-merge): <test>.log and report.md.
# POST_MERGE_TEST_DIR and POST_MERGE_TESTS override the directory and the list (used by the tests).
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
dir="${POST_MERGE_TEST_DIR:-$root/scripts/test}"
out="${POST_MERGE_OUT:-$root/build/post-merge}"
tests="${POST_MERGE_TESTS:-test-structure.sh test-pairing.sh test-checks.sh test-ci.sh \
test-spike.sh test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh}"
mkdir -p "$out"
report="$out/report.md"
failed=()
for t in $tests; do
  log="$out/${t%.sh}.log"
  echo "== $t"
  "$dir/$t" >"$log" 2>&1 || failed+=("$t")
  tail -n 3 "$log"
done
if [ "${#failed[@]}" -eq 0 ]; then
  echo "All script tests passed." >"$report"
  exit 0
fi
{
  echo "# Script tests failed on main"
  echo
  for t in "${failed[@]}"; do
    echo "- $t"
    grep -E '^FAIL' "$out/${t%.sh}.log" | sed 's/^/  - /' | head -n 20
  done
} >"$report"
cat "$report"
exit 1
