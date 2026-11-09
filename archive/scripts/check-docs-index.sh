#!/usr/bin/env bash
# Every markdown file under docs/ is reachable from docs/index.md through relative links (no orphan).
# Usage: scripts/check-docs-index.sh [repo root]   Exit 0 ok, 1 orphans (named) or missing index.
set -u
root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
python3 -I - "$root" <<'PY'
import os, re, sys
root = sys.argv[1]
docs = os.path.join(root, "docs")
index = os.path.join(docs, "index.md")
if not os.path.isfile(index):
    print("check-docs-index: docs/index.md is missing"); sys.exit(1)
all_md = set()
for d, _, files in os.walk(docs):
    for f in files:
        if f.endswith(".md"):
            all_md.add(os.path.normpath(os.path.join(d, f)))
link = re.compile(r"\]\(([^)\s#]+)(?:#[^)]*)?\)")
seen, todo = set(), [os.path.normpath(index)]
while todo:
    cur = todo.pop()
    if cur in seen:
        continue
    seen.add(cur)
    text = open(cur, encoding="utf-8").read()
    for target in link.findall(text):
        if re.match(r"^[a-z]+:", target) or target.startswith("/"):
            continue
        path = os.path.normpath(os.path.join(os.path.dirname(cur), target))
        if path in all_md and path not in seen:
            todo.append(path)
orphans = sorted(all_md - seen)
for o in orphans:
    print("check-docs-index: not reachable from docs/index.md: " + os.path.relpath(o, root))
sys.exit(1 if orphans else 0)
PY
