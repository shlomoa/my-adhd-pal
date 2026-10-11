#!/usr/bin/env bash
# Documentation checks, one entry point for Gradle (`lintDocs`, `checkExternalLinks`) and for developers.
#   scripts/docs-lint.sh markdown   style (markdownlint-cli2; config in .markdownlint-cli2.jsonc)
#   scripts/docs-lint.sh links      internal links: files and headings (remark-validate-links); blocking
#   scripts/docs-lint.sh external   external URLs (markdown-link-check); CI runs it as a non-blocking job
# Tools are pinned in tools/docs/package.json and package-lock.json and installed on first use (needs Node and npm).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$here/.."
tools="$root/tools/docs"
mode="${1:-}"
case "$mode" in markdown|links|external) ;; *) echo "usage: $0 markdown|links|external" >&2; exit 2;; esac
command -v npm >/dev/null 2>&1 || { echo "docs-lint: Node.js and npm are required (install Node 20 or newer)" >&2; exit 1; }
if [ ! -d "$tools/node_modules" ] || [ "$tools/package-lock.json" -nt "$tools/node_modules/.package-lock.json" ]; then
  (cd "$tools" && npm ci --no-audit --no-fund --loglevel=error)
fi
cd "$root"
files() { git ls-files -z --cached --others --exclude-standard -- 'README.md' 'docs/*.md' 'docs/**/*.md' | sort -z; }
case "$mode" in
  markdown)
    "$tools/node_modules/.bin/markdownlint-cli2" ;;
  links)
    files | xargs -0 "$tools/node_modules/.bin/remark" --rc-path "$tools/remarkrc.mjs" --frail --quiet --no-stdout ;;
  external)
    status=0
    while IFS= read -r -d '' f; do
      "$tools/node_modules/.bin/markdown-link-check" --quiet --config "$tools/link-check-external.json" "$f" || status=1
    done < <(files)
    exit "$status" ;;
esac
