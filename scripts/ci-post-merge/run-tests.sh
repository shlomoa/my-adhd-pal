#!/usr/bin/env bash
# Runs the script tests after a merge to main (issue 190), in parallel groups, and writes a report of what failed.
# Each group runs its tests one after the other in its own git worktree of the checked-out commit, because some tests
# edit and restore files or run Gradle in the checkout and must not share a working tree.
# Run: scripts/ci-post-merge/run-tests.sh          Output: $POST_MERGE_OUT (default build/post-merge): <test>.log, report.md.
#      scripts/ci-post-merge/run-tests.sh --groups   prints the groups and exits.
# POST_MERGE_GROUPS (groups separated by |, tests by spaces) and POST_MERGE_TEST_DIR (run the tests of that directory in
# place, no worktrees) override the defaults; the tests use them. A test whose log shows the known lint crash is rerun once.
# A unit `test-x.sh@arg` runs test-x.sh with POST_MERGE_ARG=arg and logs to test-x@arg.log, so one slow test can be split
# over groups. The groups are balanced by measured time (longest first): each unit prints "== <unit> took Ns".
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="${POST_MERGE_OUT:-$root/build/post-merge}"
groups="${POST_MERGE_GROUPS:-test-checks.sh@scaffold test-design-style.sh|test-checks.sh@format test-checks.sh@lint \
test-doc-checks.sh test-docs-site.sh test-design-docs.sh test-protocol-spec.sh test-gates.sh test-post-merge.sh|\
test-spike.sh@test-run-0b2a test-pairing.sh|test-spike.sh@test-run-0b2b test-structure.sh test-ci.sh}"
IFS='|' read -r -a group_list <<<"$groups"
if [ "${1:-}" = "--groups" ]; then
  for i in "${!group_list[@]}"; do echo "group $((i + 1)): $(echo ${group_list[$i]})"; done
  exit 0
fi
mkdir -p "$out"
# One Gradle run in the checkout first: it installs the SDK components the builds need (Android build-tools), which the
# groups would otherwise install at the same time in a fresh container and fail.
if [ -z "${POST_MERGE_TEST_DIR:-}" ] && [ -x "$root/gradlew" ]; then
  (cd "$root" && ./gradlew --console=plain :wear:compileDebugJavaWithJavac :phone:compileDebugJavaWithJavac --dry-run >"$out/warm-up.log" 2>&1) ||
    echo "warm-up Gradle run failed (see $out/warm-up.log), the groups go on"
fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

log_of() { # unit: test-x.sh -> test-x.log, test-x.sh@arg -> test-x@arg.log
  local test="${1%%@*}"
  [ "$test" = "$1" ] && echo "$out/${test%.sh}.log" || echo "$out/${test%.sh}@${1#*@}.log"
}

run_test() { # test-dir unit
  local unit="$2" test="${2%%@*}" arg="" log start=$SECONDS rc=0
  [ "$test" = "$unit" ] || arg="${unit#*@}"
  log="$(log_of "$unit")"
  echo "== $unit"
  if ! POST_MERGE_ARG="$arg" bash "$1/$test" >"$log" 2>&1; then
    if grep -q 'Unexpected failure during lint analysis' "$log"; then
      echo "== $unit hit the known lint crash, rerun once"
      mv "$log" "${log%.log}.first.log"
      POST_MERGE_ARG="$arg" bash "$1/$test" >"$log" 2>&1 || rc=1
    else
      rc=1
    fi
  fi
  echo "== $unit took $((SECONDS - start))s"
  return $rc
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
    tail -n 3 "$(log_of "$t")"
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
    grep -E '^FAIL' "$(log_of "$t")" 2>/dev/null | sed 's/^/  - /' | head -n 20
  done
} >"$report"
cat "$report"
exit 1
