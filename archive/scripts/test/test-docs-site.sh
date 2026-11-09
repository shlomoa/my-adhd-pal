#!/usr/bin/env bash
# Lane D (issue #156): docs/index.md is the single entry point and the documentation site builds in strict mode.
# Case ids TC-D-nnn are catalogued in docs/test-plans/docs.md. Run: scripts/test/test-docs-site.sh
# The build needs Python 3 and network access to install the pinned tools (tools/docs-site/requirements.txt).
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
tmp="$(mktemp -d)"
fixtures=()
cleanup() { rm -f "${fixtures[@]}"; rm -rf "$tmp"; }
trap cleanup EXIT
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}
site="build/docs-site"

# TC-D-021 every markdown file under docs/ is reachable from docs/index.md by relative links
scripts/check-docs-index.sh >"$tmp/out" 2>&1; check "TC-D-021 every docs/**/*.md is reachable from docs/index.md" $?

# TC-D-022 the index has the planned sections
bad=0
[ -f docs/index.md ] || bad=1
for h in "Plan and lanes" "Protocol and data layer" "Test plans and reports" "Spikes and research" "Setup and demo"; do
  grep -qE "^## $h\$" docs/index.md 2>/dev/null || { echo "  index lacks section: $h"; bad=1; }
done
[ "$bad" -eq 0 ]; check "TC-D-022 docs/index.md has the planned sections" $?

# TC-D-023 generator configuration
bad=0
for pat in '^docs_dir: docs$' '^strict: true$' 'name: material' 'name: mermaid'; do
  grep -qE "$pat" mkdocs.yml 2>/dev/null || { echo "  mkdocs.yml lacks: $pat"; bad=1; }
done
[ "$bad" -eq 0 ]; check "TC-D-023 mkdocs.yml: docs_dir, strict, Material theme and Mermaid fence" $?

# TC-D-024 tool versions are pinned exactly
req=tools/docs-site/requirements.txt
bad=0
grep -qE '^mkdocs==[0-9.]+$' "$req" 2>/dev/null || bad=1
grep -qE '^mkdocs-material==[0-9.]+$' "$req" 2>/dev/null || bad=1
[ -f "$req" ] && [ -z "$(grep -vE '^(#.*|[A-Za-z0-9._-]+==[0-9][0-9A-Za-z.+-]*|)$' "$req")" ] || bad=1
[ "$bad" -eq 0 ]; check "TC-D-024 requirements pin every tool with ==" $?

# TC-D-025 the strict build passes and produces the site
rm -rf "$site"
scripts/docs-site.sh >"$tmp/build.out" 2>&1; check "TC-D-025 scripts/docs-site.sh builds the site in strict mode" $?
[ -f "$site/index.html" ]; check "TC-D-025 the site has an index page" $?

# TC-D-026 a broken link fails the strict build and names the file; removing it is green
printf '# Fixture\n\n[missing](zz-does-not-exist.md)\n' > docs/zz-fixture-broken.md; fixtures+=(docs/zz-fixture-broken.md)
scripts/docs-site.sh >"$tmp/broken.out" 2>&1; rc=$?
[ "$rc" -ne 0 ] && grep -q 'zz-fixture-broken.md' "$tmp/broken.out"; check "TC-D-026 a broken relative link fails the strict build and names the file" $?
rm -f docs/zz-fixture-broken.md; fixtures=()
scripts/docs-site.sh >"$tmp/again.out" 2>&1; check "TC-D-026 removing the broken link is green" $?

# TC-D-027 the built site shows the index sections and renders Mermaid
grep -q 'Plan and lanes' "$site/index.html" 2>/dev/null; check "TC-D-027 the built index page shows the sections" $?
grep -q 'class="mermaid"' "$site/protocol/index.html" 2>/dev/null; check "TC-D-027 protocol.md Mermaid diagrams are rendered as diagrams" $?

# TC-D-028 an orphan document is detected
printf '# Orphan\n' > docs/zz-fixture-orphan.md; fixtures+=(docs/zz-fixture-orphan.md)
scripts/check-docs-index.sh >"$tmp/orphan.out" 2>&1; rc=$?
[ "$rc" -eq 1 ] && grep -q 'zz-fixture-orphan.md' "$tmp/orphan.out"; check "TC-D-028 a document not linked from the index is reported by name" $?
rm -f docs/zz-fixture-orphan.md; fixtures=()

# TC-D-029 CI builds the site and the output is not committed
grep -q 'scripts/docs-site.sh' .github/workflows/build.yml; check "TC-D-029 the build workflow runs scripts/docs-site.sh" $?
grep -q 'test-docs-site.sh' scripts/ci-post-merge/run-tests.sh; check "TC-D-029 the post-merge runner runs test-docs-site.sh" $?
git check-ignore -q "$site/index.html"; check "TC-D-029 the generated site is ignored by git" $?
exit $fail
