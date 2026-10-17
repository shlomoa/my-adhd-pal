#!/usr/bin/env bash
# The script tests run after each merge to main, not on every pull request (issue 190).
# Fast: no Gradle. Run: bash scripts/test/test-post-merge.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
root="$here/../.."
cd "$root"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi
}
has() { grep -qE -- "$1" "$2" 2>/dev/null; }
build=.github/workflows/build.yml
runner=scripts/ci-post-merge/run-tests.sh

# TC-S-170 the pull request workflow has no script-tests job and keeps the fast jobs
! has '^  script-tests:' "$build"; check "TC-S-170 build.yml has no script-tests job" 0 $?
has '^  build:' "$build" && has '^  external-links:' "$build" && has '^  docs-site:' "$build"
check "TC-S-170 build.yml keeps build, external-links and docs-site" 0 $?

# TC-S-172 no script test is lost: each scripts/test/test-*.sh runs per pull request or in the validation agent's run
lost=""
for f in scripts/test/test-*.sh; do
  n="$(basename "$f")"
  grep -qr -- "$n" .github/workflows "$runner" 2>/dev/null || lost="$lost $n"
done
[ -z "$lost" ]; check "TC-S-172 every script test runs somewhere (not run:$lost)" 0 $?

# TC-V-006 the whole former script-tests job runs after the merge and no part of it runs per pull request
gates=.github/workflows/gates.yml
missing=""
for n in test-structure.sh test-pairing.sh test-checks.sh test-ci.sh test-protocol-spec.sh test-gates.sh test-spike.sh \
  test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh test-post-merge.sh; do
  has "$n" "$runner" || missing="$missing $n"
done
[ -z "$missing" ]; check "TC-V-006 the runner lists all former script-tests and this test (missing:$missing)" 0 $?
! grep -qrE 'scripts/test/test-(structure|pairing|checks|ci|protocol-spec|gates|spike|design-docs|doc-checks|docs-site|design-style|post-merge)\.sh' .github/workflows
check "TC-V-006 no workflow runs any of them per pull request" 0 $?

# TC-S-173 the runner runs every listed test, keeps going after a failure, and names what failed
dir="$tmp/tests"; mkdir -p "$dir"
printf '#!/usr/bin/env bash\necho "PASS TC-X-001 fine"\n' > "$dir/test-good.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-002 fine"\necho "FAIL TC-X-003 broken thing"\nexit 1\n' > "$dir/test-bad.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-004 fine"\n' > "$dir/test-last.sh"
POST_MERGE_TEST_DIR="$dir" POST_MERGE_GROUPS="test-bad.sh test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out1" \
  bash "$runner" >"$tmp/run1.txt" 2>&1; rc=$?
check "TC-S-173 a failing test makes the runner exit 1" 1 $rc
grep -q 'TC-X-004' "$tmp/out1/test-last.log" 2>/dev/null; check "TC-S-173 tests after the failure still ran" 0 $?
grep -q 'test-bad.sh' "$tmp/out1/report.md" && grep -q 'FAIL TC-X-003 broken thing' "$tmp/out1/report.md"
check "TC-S-173 the report names the failing script and the failing case" 0 $?
! grep -q 'test-good.sh' "$tmp/out1/report.md"; check "TC-S-173 the report does not list passing scripts" 0 $?
POST_MERGE_TEST_DIR="$dir" POST_MERGE_GROUPS="test-good.sh test-last.sh" POST_MERGE_OUT="$tmp/out2" \
  bash "$runner" >"$tmp/run2.txt" 2>&1; check "TC-S-173 all passing makes the runner exit 0" 0 $?

# TC-V-001 lane V is set up: plan, catalogue, work log, gate G-VAL (owner V, requires G-BOOT) and ownership
bad=0
for f in docs/plans/validation.md docs/test-plans/validation.md docs/work-log/validation.md; do [ -f "$f" ] || bad=1; done
jq -e '.gates[] | select(.id == "G-VAL" and .owner == "V" and (.requires == ["G-BOOT"]))' docs/plans/gates.json >/dev/null 2>&1 || bad=1
has 'G-VAL' docs/plans/README.md && has 'validation\.md' docs/plans/README.md && has 'ci-post-merge' docs/plans/README.md || bad=1
[ "$bad" -eq 0 ]; check "TC-V-001 lane V is set up (plan, catalogue, work log, gate G-VAL, ownership)" 0 $?

# TC-V-003 the main-red procedure (one sub-agent per issue, archived when resolved) is written down
has 'sub-agent' docs/plans/validation.md && has 'archive' docs/plans/validation.md && has 'sub-agent' docs/conventions.md
check "TC-V-003 validation plan and conventions describe the main-red sub-agent procedure" 0 $?

# TC-V-004 the validation lane agent runs the script tests itself: no workflow does, and nothing only served one
[ ! -e .github/workflows/post-merge.yml ]; check "TC-V-004 there is no post-merge workflow" 0 $?
[ ! -e scripts/ci-post-merge/main-red-issue.sh ]; check "TC-V-004 the workflow-only issue handler is gone" 0 $?

# TC-V-005 lane V opens the main-red issue itself with its issue tool and the documents say what the issue names
has 'main-red' docs/conventions.md && has 'issue tool' docs/plans/validation.md && has 'issue tool' docs/conventions.md
check "TC-V-005 documents say lane V opens the main-red issue with its issue tool" 0 $?
has 'failing test ids' docs/plans/validation.md && has 'owning lane' docs/plans/validation.md && has 'sha' docs/plans/validation.md
check "TC-V-005 the issue names the failing test ids, the sha and the owning lane" 0 $?

# TC-V-007 the default groups cover every post-merge test exactly once, in at least three groups
bash "$runner" --groups >"$tmp/groups.txt" 2>&1
bad=0
for n in test-structure.sh test-pairing.sh test-checks.sh test-ci.sh test-protocol-spec.sh test-gates.sh test-spike.sh \
  test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh test-post-merge.sh; do
  [ "$(grep -oE -- "(^| )$n(@[A-Za-z0-9._-]+)?( |$)" "$tmp/groups.txt" | wc -l)" -ge 1 ] || { echo "  $n is in no group"; bad=1; }
done
# every test runs once, except test-spike.sh (its two suites) and test-checks.sh (scaffold and fixtures), each part in a group of its own
for n in test-structure.sh test-pairing.sh test-ci.sh test-protocol-spec.sh test-gates.sh \
  test-design-docs.sh test-doc-checks.sh test-docs-site.sh test-design-style.sh test-post-merge.sh; do
  [ "$(grep -ow -- "$n" "$tmp/groups.txt" | wc -l)" -eq 1 ] || { echo "  $n is not in exactly one group"; bad=1; }
done
[ "$(grep -ow -- 'test-spike.sh@test-run-0b2a' "$tmp/groups.txt" | wc -l)" -eq 1 ] &&
  [ "$(grep -ow -- 'test-spike.sh@test-run-0b2b' "$tmp/groups.txt" | wc -l)" -eq 1 ] &&
  [ "$(grep -c 'test-spike.sh' "$tmp/groups.txt")" -eq 2 ] || { echo "  test-spike.sh is not split into its two suites"; bad=1; }
[ "$(grep -ow -- 'test-checks.sh@scaffold' "$tmp/groups.txt" | wc -l)" -eq 1 ] &&
  [ "$(grep -ow -- 'test-checks.sh@fixtures' "$tmp/groups.txt" | wc -l)" -eq 1 ] &&
  [ "$(grep -c 'test-checks.sh' "$tmp/groups.txt")" -eq 2 ] || { echo "  test-checks.sh is not split into scaffold and fixtures"; bad=1; }
[ "$bad" -eq 0 ]; check "TC-V-007 every post-merge test is in exactly one group (spike and checks as their parts)" 0 $?
[ "$(grep -c '^group [0-9]*: ' "$tmp/groups.txt")" -ge 5 ]; check "TC-V-007 there are at least five groups" 0 $?
[ "$(grep -oE -- 'test-[a-z-]*\.sh' "$tmp/groups.txt" | wc -l)" -eq 14 ]; check "TC-V-007 the groups hold exactly the fourteen units" 0 $?

# TC-V-008 groups run at the same time, a failure in any group fails the run and shows in the report, the other groups finish
pdir="$tmp/par"; mkdir -p "$pdir"
printf '#!/usr/bin/env bash\nsleep 3\necho "PASS TC-X-010 slept"\n' > "$pdir/test-sleep-a.sh"
printf '#!/usr/bin/env bash\nsleep 3\necho "FAIL TC-X-011 slept and broke"\nexit 1\n' > "$pdir/test-sleep-b.sh"
printf '#!/usr/bin/env bash\necho "PASS TC-X-012 quick"\n' > "$pdir/test-quick.sh"
start=$SECONDS
POST_MERGE_TEST_DIR="$pdir" POST_MERGE_GROUPS="test-sleep-a.sh test-quick.sh|test-sleep-b.sh" POST_MERGE_OUT="$tmp/out3" \
  bash "$runner" >"$tmp/run3.txt" 2>&1; rc=$?
elapsed=$((SECONDS - start))
check "TC-V-008 a failure in one group makes the run exit 1" 1 $rc
[ "$elapsed" -le 5 ]; check "TC-V-008 two groups of three seconds finish in at most five (took ${elapsed}s)" 0 $?
grep -q 'test-sleep-b.sh' "$tmp/out3/report.md" && grep -q 'FAIL TC-X-011 slept and broke' "$tmp/out3/report.md"
check "TC-V-008 the report names the failing script and case from the other group" 0 $?
grep -q 'TC-X-012' "$tmp/out3/test-quick.log" 2>/dev/null && ! grep -q 'test-sleep-a.sh' "$tmp/out3/report.md"
check "TC-V-008 the tests of the passing group ran and are not in the report" 0 $?

# TC-V-009 each group runs in its own git worktree of the checked-out commit, and the worktrees are removed afterwards
wrepo="$tmp/wrepo"; mkdir -p "$wrepo/scripts/ci-post-merge" "$wrepo/scripts/test"
cp "$runner" "$wrepo/scripts/ci-post-merge/run-tests.sh"
for g in a b; do
  printf '#!/usr/bin/env bash\n(cd "$(dirname "$0")/../.." && pwd) > "$ROOTS/root-%s.txt"\n' "$g" > "$wrepo/scripts/test/test-root-$g.sh"
done
git -C "$wrepo" init -q . && git -C "$wrepo" add -A && git -C "$wrepo" -c user.name=t -c user.email=t@t commit -q -m fixture
mkdir -p "$tmp/roots"
ROOTS="$tmp/roots" POST_MERGE_GROUPS="test-root-a.sh|test-root-b.sh" POST_MERGE_OUT="$tmp/out4" \
  bash "$wrepo/scripts/ci-post-merge/run-tests.sh" >"$tmp/run4.txt" 2>&1; rc=$?
check "TC-V-009 the worktree run succeeds" 0 $rc
ra="$(cat "$tmp/roots/root-a.txt" 2>/dev/null)"; rb="$(cat "$tmp/roots/root-b.txt" 2>/dev/null)"
[ -n "$ra" ] && [ -n "$rb" ] && [ "$ra" != "$rb" ] && [ "$ra" != "$(cd "$wrepo" && pwd)" ] && [ "$rb" != "$(cd "$wrepo" && pwd)" ]
check "TC-V-009 the two groups ran in two different directories, none of them the checkout" 0 $?
[ "$(git -C "$wrepo" worktree list | wc -l)" -eq 1 ]; check "TC-V-009 no worktree is left behind" 0 $?

# TC-V-010 the known lint crash is rerun once and a second failure counts
ldir="$tmp/lint"; mkdir -p "$ldir"
printf '#!/usr/bin/env bash\nif [ ! -e "$MARK" ]; then touch "$MARK"; echo "Unexpected failure during lint analysis of A.kt"; echo "FAIL TC-X-020 crash"; exit 1; fi\necho "PASS TC-X-020 second run"\n' > "$ldir/test-crash-once.sh"
printf '#!/usr/bin/env bash\necho "Unexpected failure during lint analysis of B.kt"\necho "FAIL TC-X-021 crash again"\nexit 1\n' > "$ldir/test-crash-always.sh"
MARK="$tmp/mark" POST_MERGE_TEST_DIR="$ldir" POST_MERGE_GROUPS="test-crash-once.sh" POST_MERGE_OUT="$tmp/out5" \
  bash "$runner" >"$tmp/run5.txt" 2>&1; check "TC-V-010 a lint crash on the first run and a pass on the rerun is green" 0 $?
POST_MERGE_TEST_DIR="$ldir" POST_MERGE_GROUPS="test-crash-always.sh" POST_MERGE_OUT="$tmp/out6" \
  bash "$runner" >"$tmp/run6.txt" 2>&1; check "TC-V-010 a lint crash on both runs fails" 1 $?
# TC-V-011 a unit `test.sh@arg` passes the argument as POST_MERGE_ARG and logs to test@arg.log; every unit prints its duration
adir="$tmp/arg"; mkdir -p "$adir"
printf '#!/usr/bin/env bash\necho "PASS TC-X-030 arg=${POST_MERGE_ARG:-none}"\n' > "$adir/test-arg.sh"
POST_MERGE_TEST_DIR="$adir" POST_MERGE_GROUPS="test-arg.sh@one|test-arg.sh" POST_MERGE_OUT="$tmp/out7" \
  bash "$runner" >"$tmp/run7.txt" 2>&1; check "TC-V-011 units with and without an argument pass" 0 $?
grep -q 'arg=one' "$tmp/out7/test-arg@one.log" 2>/dev/null && grep -q 'arg=none' "$tmp/out7/test-arg.log" 2>/dev/null
check "TC-V-011 the argument reaches the test as POST_MERGE_ARG and each unit has its own log" 0 $?
grep -qE '^== test-arg\.sh@one took [0-9]+s$' "$tmp/run7.txt" && grep -qE '^== test-arg\.sh took [0-9]+s$' "$tmp/run7.txt"
check "TC-V-011 the run output names each unit and its duration" 0 $?

# TC-V-012 test-spike.sh runs only the suite named in POST_MERGE_ARG, both suites without it
sroot="$tmp/sroot"; mkdir -p "$sroot/scripts/spike"
for s in test-run-0b2a test-run-0b2b; do
  printf '#!/usr/bin/env bash\necho "PASS ran %s"\n' "$s" > "$sroot/scripts/spike/$s.sh"; chmod +x "$sroot/scripts/spike/$s.sh"
done
REPO_ROOT="$sroot" POST_MERGE_ARG=test-run-0b2b bash scripts/test/test-spike.sh >"$tmp/spike1.txt" 2>&1
grep -q 'PASS TC-S-146 test-run-0b2b.sh passes' "$tmp/spike1.txt" && ! grep -q 'test-run-0b2a.sh passes' "$tmp/spike1.txt"
check "TC-V-012 with POST_MERGE_ARG only that spike suite runs" 0 $?
REPO_ROOT="$sroot" bash scripts/test/test-spike.sh >"$tmp/spike2.txt" 2>&1
grep -q 'test-run-0b2a.sh passes' "$tmp/spike2.txt" && grep -q 'test-run-0b2b.sh passes' "$tmp/spike2.txt"
check "TC-V-012 without it both spike suites run" 0 $?
# TC-V-013 test-checks.sh runs only the part named in POST_MERGE_ARG (scaffold: the full check run; fixtures: the negative fixtures)
croot="$tmp/croot"; mkdir -p "$croot/scripts/test" "$croot/docs"
cp scripts/test/test-checks.sh "$croot/scripts/test/"
printf '#!/usr/bin/env bash\necho "$*" >> "$GRADLE_LOG"\n' > "$croot/gradlew"; chmod +x "$croot/gradlew"
git -C "$croot" init -q . && git -C "$croot" add -A && git -C "$croot" -c user.name=t -c user.email=t@t commit -q -m fixture
for part in scaffold fixtures all; do
  arg="$part"; [ "$part" != all ] || arg=""
  GRADLE_LOG="$tmp/gradle-$part.log" POST_MERGE_ARG="$arg" bash "$croot/scripts/test/test-checks.sh" >/dev/null 2>&1
done
grep -q -- 'check --rerun-tasks' "$tmp/gradle-scaffold.log" && ! grep -q 'spotlessApply\|detekt\|lintDocs' "$tmp/gradle-scaffold.log"
check "TC-V-013 POST_MERGE_ARG=scaffold runs the full check and none of the fixtures" 0 $?
grep -q 'spotlessApply' "$tmp/gradle-fixtures.log" && grep -q 'lintDocs' "$tmp/gradle-fixtures.log" && ! grep -q -- 'check --rerun-tasks' "$tmp/gradle-fixtures.log"
check "TC-V-013 POST_MERGE_ARG=fixtures runs the fixtures and not the full check" 0 $?
grep -q -- 'check --rerun-tasks' "$tmp/gradle-all.log" && grep -q 'lintDocs' "$tmp/gradle-all.log"
check "TC-V-013 without POST_MERGE_ARG both parts run" 0 $?
grep -qE '^test-checks\.sh@scaffold|[ |]test-checks\.sh@scaffold' "$tmp/groups.txt" && grep -q 'test-checks.sh@fixtures' "$tmp/groups.txt"
check "TC-V-013 the default groups run the two parts of test-checks.sh in separate groups" 0 $?
exit $fail
