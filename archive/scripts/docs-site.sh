#!/usr/bin/env bash
# Builds the documentation site (mkdocs.yml) in strict mode: broken links, missing files and bad configuration fail it.
# Usage: scripts/docs-site.sh [output dir]   (default build/docs-site, not committed, not published)
# Needs python3 with venv and network access to install the tools pinned in tools/docs-site/requirements.txt.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
out="${1:-build/docs-site}"
venv="build/docs-site-venv"
command -v python3 >/dev/null 2>&1 || { echo "docs-site: python3 is required" >&2; exit 1; }
if [ ! -x "$venv/bin/mkdocs" ] || [ tools/docs-site/requirements.txt -nt "$venv/.installed" ]; then
  python3 -m venv "$venv"
  "$venv/bin/pip" install --quiet --disable-pip-version-check -r tools/docs-site/requirements.txt
  touch "$venv/.installed"
fi
# The Material theme prints a banner about MkDocs 2.0 on every build; it is advice for the maintainers, not a warning.
NO_MKDOCS_2_WARNING=true "$venv/bin/mkdocs" build --strict --site-dir "$out"
echo "docs-site: built $out"
