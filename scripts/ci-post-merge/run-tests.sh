#!/usr/bin/env bash
# Runs the script tests after a merge to main (issue 190), in parallel groups, and writes a report of what failed.
# Each group runs its tests one after the other in its own git worktree of the checked-out commit, because some tests
# edit and restore files or run Gradle in the checkout and must not share a working tree.
# Run: scripts/ci-post-merge/run-tests.sh          Output: $POST_MERGE_OUT (default build/post-merge): <test>.log, report.md.
#      scripts/ci-post-merge/run-tests.sh --groups   prints the groups and exits.
# POST_MERGE_GROUPS (groups separated by |, tests by spaces) and POST_MERGE_TEST_DIR (run the tests of that directory in
# place, no worktrees) override the defaults; the tests use them. A test whose log shows the known lint crash is rerun once.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="${POST_MERGE_OUT:-$root/build/post-merge}"
groups="${POST_MERGE_GROUPS:-test-structure.sh test-ci.sh|test-checks.sh test-pairing.sh|test-protocol-spec.sh test-gates.sh \
test-spike.sh test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh test-post-merge.sh}"
IFS='|' read -r -a group_list <<<"$groups"
if [ "${1:-}" = "--groups" ]; then
  for i in "${!group_list[@]}"; do echo "group $((i + 1)): $(echo ${group_list[$i]})"; done
  exit 0
fi
mkdir -p "$out"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

run_test() { # test-dir test
  local log="$out/${2%.sh}.log"
  echo "== $2"
  if ! bash "$1/$2" >"$log" 2>&1; then
    if grep -q 'Unexpected failure during lint analysis' "$log"; then
      echo "== $2 hit the known lint crash, rerun once"
      mv "$log" "$out/${2%.sh}.first.log"
      bash "$1/$2" >"$log" 2>&1 || return 1
    else
      return 1
    fi
  fi
}

run_group() { # index tests
  local dir="${POST_MERGE_TEST_DIR:-}" wt=""
  if [ -z "$dir" ]; then
    wt="$work/group-$1"
    git -C "$root" worktree add -q --detach "$wt" HEAD || { echo "group $1: no worktree" >"$work/failed-$1"; return; }
    cp "$root/local.properties" "$wt/" 2>/dev/null || true
    dir="$wt/scripts/test"
  fi
  for t in $2; do
    run_test "$dir" "$t" || echo "$t" >>"$work/failed-$1"
    tail -n 3 "$out/${t%.sh}.log"
  done
  [ -z "$wt" ] || git -C "$root" worktree remove --force "$wt"
}

for i in "${!group_list[@]}"; do
  run_group "$i" "${group_list[$i]}" &
done
wait

failed=()
for i in "${!group_list[@]}"; do
  [ -f "$work/failed-$i" ] && while read -r t; do failed+=("$t"); done <"$work/failed-$i"
done
report="$out/report.md"
if [ "${#failed[@]}" -eq 0 ]; then
  echo "All script tests passed." >"$report"
  exit 0
fi
{
  echo "# Script tests failed on main"
  echo
  for t in "${failed[@]}"; do
    echo "- $t"
    grep -E '^FAIL' "$out/${t%.sh}.log" 2>/dev/null | sed 's/^/  - /' | head -n 20
  done
} >"$report"
cat "$report"
exit 1
