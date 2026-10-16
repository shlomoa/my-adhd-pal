#!/usr/bin/env bash
# Lane D (documentation) checks for docs/design (issue #154): images, references, sections, solution diagrams, links
# and the lane setup. Case ids TC-D-nnn are catalogued in docs/test-plans/docs.md.
# REPO_ROOT overrides the repository root (tests of the test).
set -u
root="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
d="$root/docs/design"
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}

# screen id -> section file (assignment from issue #154)
declare -A section=(
  [P1]=phone-main-flow.md [P2]=phone-main-flow.md [P3]=phone-main-flow.md [P4]=phone-main-flow.md
  [P5]=phone-main-flow.md [P6]=phone-main-flow.md
  [P7]=phone-settings-prompts-alerts.md [P8]=phone-settings-prompts-alerts.md [P9]=phone-settings-prompts-alerts.md
  [P10]=phone-settings-prompts-alerts.md [P11]=phone-settings-prompts-alerts.md [P12]=phone-settings-prompts-alerts.md
  [P13]=phone-settings-prompts-alerts.md
  [W1]=watch-main-flow.md [W2]=watch-main-flow.md [W3]=watch-main-flow.md [W4]=watch-main-flow.md
  [W5]=watch-tile-alerts.md [W6]=watch-tile-alerts.md [W7]=watch-tile-alerts.md [W8]=watch-tile-alerts.md
  [W9]=watch-tile-alerts.md
)
screens="P1 P2 P3 P4 P5 P6 P7 P8 P9 P10 P11 P12 P13 W1 W2 W3 W4 W5 W6 W7 W8 W9"
sections="phone-main-flow.md phone-settings-prompts-alerts.md watch-main-flow.md watch-tile-alerts.md"

pngs() { find "$d/images" -maxdepth 1 -name '*.png' 2>/dev/null | sort; }
# image targets referenced by a markdown file: ![alt](images/x.png)
refs() { grep -oE '!\[[^]]*\]\([^)]+\)' "$1" 2>/dev/null | sed -E 's/^.*\(([^)]+)\)$/\1/'; }
sectionfiles() { for s in $sections solution.md; do [ -f "$d/$s" ] && echo "$d/$s"; done; }

# TC-D-001 every PNG is referenced by exactly one section file (README references only overview-* images)
bad=0; n=0
for p in $(pngs); do
  n=$((n + 1)); b="$(basename "$p")"
  case "$b" in
    overview*) c="$(refs "$d/README.md" | grep -cxF "images/$b")" ;;
    *) c=0; for f in $(sectionfiles); do c=$((c + $(refs "$f" | grep -cxF "images/$b"))); done ;;
  esac
  [ "$c" -eq 1 ] || { echo "  $b referenced $c times"; bad=1; }
done
[ "$n" -gt 0 ] && [ "$bad" -eq 0 ]; check "TC-D-001 every PNG in docs/design/images is referenced exactly once (found $n)" $?

# TC-D-002 every image reference resolves
bad=0; n=0
for f in "$d"/*.md; do
  [ -f "$f" ] || continue
  for r in $(refs "$f"); do n=$((n + 1)); [ -f "$d/$r" ] || { echo "  $(basename "$f"): $r missing"; bad=1; }; done
done
[ "$n" -gt 0 ] && [ "$bad" -eq 0 ]; check "TC-D-002 every image reference resolves ($n references)" $?

# TC-D-003 every screen has an image named <id>-*.png
bad=0
for s in $screens; do ls "$d"/images/"$s"-*.png >/dev/null 2>&1 || { echo "  no image for $s"; bad=1; }; done
[ "$bad" -eq 0 ]; check "TC-D-003 screens P1-P13 and W1-W9 each have an image" $?

# TC-D-004 README lists every section
bad=0; [ -f "$d/README.md" ] || bad=1
for s in $sections solution.md; do grep -qF "]($s)" "$d/README.md" 2>/dev/null || { echo "  README does not link $s"; bad=1; }; done
[ "$bad" -eq 0 ]; check "TC-D-004 docs/design/README.md links every section and the solution" $?

# TC-D-005 solution.md: data flow and event flow, each a Mermaid diagram followed by a description
solution_ok() { # heading regex
  awk -v h="$1" '
    $0 ~ "^##+ " h { insec = 1; next }
    insec && /^##+ / { insec = 0 }
    insec && /^```mermaid/ { inblock = 1; seen = 1; next }
    insec && inblock && /^```/ { inblock = 0; after = 1; next }
    insec && after && /[A-Za-z]/ && !inblock { text = 1 }
    END { exit !(seen && text) }' "$d/solution.md" 2>/dev/null
}
solution_ok "Data flow"; a=$?
solution_ok "Event flow"; b=$?
[ "$a" -eq 0 ] && [ "$b" -eq 0 ]; check "TC-D-005 solution.md has a data flow and an event flow Mermaid diagram, each described" $?

# TC-D-006 no broken relative links in docs/design markdown
bad=0; n=0
for f in "$d"/*.md; do
  [ -f "$f" ] || continue
  for l in $(grep -oE '\]\([^)#]+(#[^)]*)?\)' "$f" | sed -E 's/^\]\(([^)#]+).*$/\1/' | grep -vE '^(https?:|mailto:)'); do
    n=$((n + 1)); [ -e "$d/$l" ] || { echo "  $(basename "$f"): $l missing"; bad=1; }
  done
done
[ "$n" -gt 0 ] && [ "$bad" -eq 0 ]; check "TC-D-006 relative links in docs/design resolve ($n links)" $?

# TC-D-007 each screen image sits in the section file the issue assigns
bad=0
for s in $screens; do
  f="$d/${section[$s]}"
  for p in "$d"/images/"$s"-*.png; do
    [ -f "$p" ] || continue
    refs "$f" | grep -qxF "images/$(basename "$p")" || { echo "  $s not in ${section[$s]}"; bad=1; }
  done
done
for s in $sections; do [ -f "$d/$s" ] || { echo "  missing $s"; bad=1; }; done
[ "$bad" -eq 0 ]; check "TC-D-007 four section files exist and show their assigned screens" $?

# TC-D-008 board sources exist for every screen image
bad=0
[ -d "$d/source" ] && [ -n "$(ls "$d"/source 2>/dev/null)" ] || bad=1
[ "$bad" -eq 0 ]; check "TC-D-008 docs/design/source holds the board sources" $?

# TC-D-009 wording: proposed design; tile primary action undecided
grep -qi 'proposed design' "$d/README.md" 2>/dev/null && grep -qi 'undecided' "$d/watch-tile-alerts.md" 2>/dev/null
check "TC-D-009 docs say 'proposed design' and mark the tile primary action undecided" $?

# TC-D-010 lane setup: plan, gate, work log, catalogue, ownership
bad=0
[ -f "$root/docs/plans/docs.md" ] || bad=1
jq -e '.gates[] | select(.id == "G-DOC" and .owner == "D" and (.requires == ["G-BOOT"]))' "$root/docs/plans/gates.json" >/dev/null 2>&1 || bad=1
grep -qF 'docs/design/**' "$root/docs/plans/README.md" || bad=1
grep -qF '[Documentation](docs.md)' "$root/docs/plans/README.md" || bad=1
"$root/scripts/check-work-log.sh" "$root/docs/work-log/docs.md" >/dev/null 2>&1 || bad=1
grep -qF 'work-log/docs.md' "$root/docs/work-log.md" || bad=1
[ "$bad" -eq 0 ]; check "TC-D-010 lane D is set up (plan, gate G-DOC, ownership, work log)" $?

# TC-D-012 (docs lint and link check) is run by CI (`conventions`/`build` jobs, scripts/docs-lint.sh); TC-D-013 (design
# approval) is manual and reported by the user in docs/work-log/docs.md.
exit $fail
