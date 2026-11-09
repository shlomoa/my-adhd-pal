#!/usr/bin/env bash
# Work-log entry format (docs/work-log.md): every `## ` heading is `## YYYY-MM-DD - <step id> <title>` and its entry has
# the bullets Issue and PR, Changed, Validation and Open items. Usage: scripts/check-work-log.sh <log.md>...
set -u
[ "$#" -ge 1 ] || { echo "usage: $0 <log.md>..." >&2; exit 2; }
status=0
for f in "$@"; do
  awk -v file="$f" '
    function flush() {
      if (heading != "") {
        split("Issue and PR|Changed|Validation|Open items", need, "|")
        for (i = 1; i <= 4; i++) if (!(need[i] in have)) { printf "check-work-log: %s: entry \"%s\" lacks the bullet \"%s\"\n", file, heading, need[i]; bad = 1 }
      }
      delete have
    }
    /^## / {
      flush()
      heading = substr($0, 4)
      if ($0 !~ /^## [0-9]{4}-[0-9]{2}-[0-9]{2} - [^ ]+ /) { printf "check-work-log: %s: bad heading \"%s\" (want: ## YYYY-MM-DD - <step id> <title>)\n", file, $0; bad = 1 }
      next
    }
    /^- (Issue and PR|Changed|Validation|Open items):/ { sub(/^- /, ""); sub(/:.*/, ""); have[$0] = 1 }
    END { flush(); exit bad }
  ' "$f" || status=1
done
exit "$status"
