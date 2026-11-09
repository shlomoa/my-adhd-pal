#!/usr/bin/env bash
# Documentation checks, one entry point for Gradle (`lintDocs`, `checkExternalLinks`) and for developers.
#   scripts/docs-lint.sh markdown   lint and format together (what Gradle `check` and CI run)
#   scripts/docs-lint.sh lint       markdownlint content rules (structure, headings, links syntax)
#   scripts/docs-lint.sh format     markdownlint formatting rules (whitespace, blank lines, list and code style)
#   scripts/docs-lint.sh fix        applies the fixes markdownlint can make (formatting) to the documents
#   (one markdownlint-cli2 run with the config in .markdownlint-cli2.jsonc; lint and format split its findings by rule)
#   scripts/docs-lint.sh links      internal links: files and headings (remark-validate-links); blocking
#   scripts/docs-lint.sh external   external URLs (markdown-link-check); CI runs it as a non-blocking job
# Tools are pinned in tools/docs/package.json and package-lock.json and installed on first use (needs Node and npm).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$here/.."
tools="$root/tools/docs"
mode="${1:-}"
case "$mode" in markdown|lint|format|fix|links|external) ;; *) echo "usage: $0 markdown|lint|format|fix|links|external" >&2; exit 2;; esac
command -v npm >/dev/null 2>&1 || { echo "docs-lint: Node.js and npm are required (install Node 20 or newer)" >&2; exit 1; }
if [ ! -d "$tools/node_modules" ] || [ "$tools/package-lock.json" -nt "$tools/node_modules/.package-lock.json" ]; then
  (cd "$tools" && npm ci --no-audit --no-fund --loglevel=error)
fi
cd "$root"
files() { git ls-files -z --cached --others --exclude-standard -- 'README.md' 'docs/*.md' 'docs/**/*.md' | sort -z; }
# Rules that are about formatting; every other markdownlint rule is a lint rule.
format_rules='MD004|MD005|MD007|MD009|MD010|MD012|MD018|MD019|MD020|MD021|MD023|MD027|MD030|MD031|MD032|MD035|MD037|MD038|MD039|MD046|MD047|MD048|MD049|MD050|MD058'
# md_check all|lint|format: one markdownlint run, print the findings of the requested kind, exit 1 if there are any
md_check() {
  local out hits rc=0
  out="$("$tools/node_modules/.bin/markdownlint-cli2" 2>&1)" || rc=$?
  if [ "$rc" -gt 1 ]; then printf '%s\n' "$out"; return "$rc"; fi
  hits="$(printf '%s\n' "$out" | grep -E ' error MD[0-9]+/' || true)"
  case "$1" in
    format) hits="$(printf '%s\n' "$hits" | grep -E " error ($format_rules)/" || true)" ;;
    lint) hits="$(printf '%s\n' "$hits" | grep -vE " error ($format_rules)/" || true)" ;;
  esac
  if [ -n "$hits" ]; then printf '%s\n' "$hits"; echo "docs-lint: $1 check failed" >&2; return 1; fi
  echo "docs-lint: $1 check passed"
}
case "$mode" in
  markdown) md_check all ;;
  lint) md_check lint ;;
  format) md_check format ;;
  fix) "$tools/node_modules/.bin/markdownlint-cli2" --fix >/dev/null 2>&1 || true; echo "docs-lint: applied markdownlint fixes; run '$0 markdown' to see what is left" ;;
  links)
    files | xargs -0 "$tools/node_modules/.bin/remark" --rc-path "$tools/remarkrc.mjs" --frail --quiet --no-stdout ;;
  external)
    status=0
    while IFS= read -r -d '' f; do
      "$tools/node_modules/.bin/markdown-link-check" --quiet --config "$tools/link-check-external.json" "$f" || status=1
    done < <(files)
    exit "$status" ;;
esac
