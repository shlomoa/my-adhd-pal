#!/usr/bin/env bash
# Lane D (issue #164): the design images follow the approved aurora style. Case ids TC-D-nnn are catalogued in
# docs/test-plans/docs.md. Needs python3 (standard library only). Run: scripts/test/test-design-style.sh
set -u
root="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$root"
fail=0
check() { # name status
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}
img=docs/design/images
src=docs/design/source
REF_SHA=74dc8655bbec681f28b572b1a8b6ed034a268168d7c9a2633b515f8656f77078

# Minimal PNG reader (8-bit RGBA or RGB, not interlaced): prints "width height colortype" or pixel alpha.
png() { python3 -I - "$@" <<'PY'
import struct, sys, zlib
mode, path = sys.argv[1], sys.argv[2]
d = open(path, 'rb').read()
assert d[:8] == b'\x89PNG\r\n\x1a\n'
pos, idat, ihdr = 8, b'', None
while pos < len(d):
    n, t = struct.unpack('>I4s', d[pos:pos+8]); body = d[pos+8:pos+8+n]; pos += 12 + n
    if t == b'IHDR': ihdr = struct.unpack('>IIBBBBB', body)
    elif t == b'IDAT': idat += body
w, h, depth, ctype, _, _, interlace = ihdr
if mode == 'info':
    print(w, h, ctype); sys.exit(0)
assert depth == 8 and interlace == 0 and ctype in (2, 6), 'unsupported png'
bpp = 4 if ctype == 6 else 3
raw = zlib.decompress(idat); stride = w * bpp
rows, prev = [], bytearray(stride)
for y in range(h):
    f = raw[y*(stride+1)]; line = bytearray(raw[y*(stride+1)+1:(y+1)*(stride+1)])
    for i in range(stride):
        a = line[i-bpp] if i >= bpp else 0; b = prev[i]; c = prev[i-bpp] if i >= bpp else 0
        if f == 1: line[i] = (line[i] + a) & 255
        elif f == 2: line[i] = (line[i] + b) & 255
        elif f == 3: line[i] = (line[i] + ((a + b) >> 1)) & 255
        elif f == 4:
            p = a + b - c; pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
            line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
    rows.append(line); prev = line
def alpha(x, y): return rows[y][x*bpp+3] if bpp == 4 else 255
if mode == 'corners':   # alpha at the four corners and the center
    print(*(alpha(x, y) for x, y in ((0, 0), (w-1, 0), (0, h-1), (w-1, h-1), (w//2, h//2))))
PY
}
shots="P1 P2 P3 P4 P5 P6 P7 P8 P9 P10 P11 P12 P13 W1 W2 W3 W4 W5 W6 W7 W8 W9"
# circle_ok <file>: alpha 0 in the four corners, (almost) opaque in the center
circle_ok() { local a; a="$(png corners "$1" 2>/dev/null)" || return 1; set -- $a; [ "$#" -eq 5 ] && [ "$1$2$3$4" = 0000 ] && [ "$5" -ge 250 ]; }
file_of() { ls $img/$1-*.png 2>/dev/null | head -1; }

# TC-D-031 W4 is the user's approved reference image: same bytes, 1254x1254 RGBA, transparent outside the circle
w4="$img/W4-choice.png"
[ "$(sha256sum "$w4" 2>/dev/null | cut -d' ' -f1)" = "$REF_SHA" ]; check "TC-D-031 W4-choice.png is the approved reference file (sha256)" $?
[ "$(png info "$w4" 2>/dev/null)" = "1254 1254 6" ]; check "TC-D-031 W4 is 1254x1254 RGBA" $?
circle_ok "$w4"; check "TC-D-031 W4 is transparent in the corners and opaque in the center" $?

# TC-D-032 every screen image has its expected pixel size
bad=0
for s in $shots; do
  f="$(file_of $s)"; [ -n "$f" ] || { echo "  no image for $s"; bad=1; continue; }
  case "$s" in
    P7) want="780 2000 *" ;;
    P*) want="780 1688 *" ;;
    W*) want="1254 1254 *" ;;
  esac
  got="$(png info "$f" 2>/dev/null)"
  case "$got" in ${want% \*}\ *) ;; *) echo "  $(basename "$f"): $got, want ${want% \*}"; bad=1;; esac
done
o="$img/overview-screens.png"; [ "$(png info "$o" 2>/dev/null | cut -d' ' -f1-2)" = "2400 1280" ] || { echo "  overview size"; bad=1; }
[ "$bad" -eq 0 ]; check "TC-D-032 every screen image and the overview have the expected pixel size" $?

# TC-D-033 watch images are circles: transparent corners, opaque center
bad=0
for s in W1 W2 W3 W4 W5 W6 W7 W8 W9; do
  f="$(file_of $s)"; circle_ok "$f" || { echo "  $s is not a transparent-cornered circle"; bad=1; }
done
[ "$bad" -eq 0 ]; check "TC-D-033 watch images W1-W9 are transparent outside the circle" $?

# TC-D-034 render manifest: every image (W4 is the reference) was rendered from the current board and stylesheet
python3 -I - "$src" "$img" <<'PY'
import hashlib, json, os, sys
src, img = sys.argv[1], sys.argv[2]
boards = {'P1':'PhoneWatch','P2':'PhoneTasks','P3':'PhoneTaskEdit','P4':'PhoneReminderEdit','P5':'PhoneSchedule','P6':'PhoneHistory','P7':'PhoneSettings','P8':'PhonePrompt','P9':'PhoneHeartRate','P10':'PhoneAlert1','P11':'PhoneAlert2','P12':'PhoneStates','P13':'PhoneLinkStates','W1':'WatchHome','W2':'WatchLinks','W3':'WatchReminder','W5':'WatchTile','W6':'WatchTileEmpty','W7':'WatchAlert1','W8':'WatchAlert2','W9':'WatchPermission','overview':'Main'}
try:
    m = json.load(open(os.path.join(src, 'render-manifest.json')))
except Exception as e:
    print('  no manifest:', e); sys.exit(1)
css = open(os.path.join(src, 'ui.css'), 'rb').read()
bad = 0
for k, b in boards.items():
    h = hashlib.sha256(open(os.path.join(src, b + '.dc.html'), 'rb').read() + css).hexdigest()
    if m.get('images', {}).get(k) != h:
        print('  %s was not rendered from the current sources' % k); bad = 1
if m.get('reference', {}).get('W4') is None:
    print('  W4 is not marked as the reference'); bad = 1
sys.exit(bad)
PY
check "TC-D-034 every image was rendered from the current board and stylesheet (render manifest)" $?

# TC-D-035 text contrast of the stylesheet palette meets WCAG AA (4.5:1)
python3 -I - "$src/ui.css" <<'PY'
import re, sys
css = open(sys.argv[1]).read()
v = dict(re.findall(r'--([a-z0-9-]+)\s*:\s*(#[0-9A-Fa-f]{6})\b', css))
def lum(h):
    c = [int(h[i:i+2], 16) / 255 for i in (1, 3, 5)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
def ratio(a, b):
    x, y = sorted((lum(a), lum(b)), reverse=True); return (x + 0.05) / (y + 0.05)
pairs = [('text','bg'),('text-soft','bg'),('text','card'),('text-soft','card'),('ink','mint-1'),('ink','mint-2'),
         ('ink','sun-1'),('ink','sun-2'),('ink','lav-1'),('ink','lav-2'),('text','alert1-bg'),('text','alert2-bg')]
bad = 0
for f, b in pairs:
    if f not in v or b not in v:
        print('  missing variable --%s or --%s' % (f, b)); bad = 1; continue
    r = ratio(v[f], v[b])
    if r < 4.5: print('  --%s on --%s is %.2f:1' % (f, b, r)); bad = 1
sys.exit(bad)
PY
check "TC-D-035 stylesheet text contrast pairs meet 4.5:1" $?

# TC-D-036 alert levels differ by text and icon, not only by color (phone P10/P11, watch W7/W8)
python3 -I - "$src" <<'PY'
import re, sys, os
src = sys.argv[1]
def parts(name):
    t = open(os.path.join(src, name + '.dc.html'), encoding='utf-8').read()
    body = re.search(r'<x-dc>(.*?)</x-dc>', t, re.S).group(1)
    body = re.sub(r'<helmet>.*?</helmet>', '', body, flags=re.S)
    icons = frozenset(re.findall(r'\bi-[a-z0-9-]+', body))
    text = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', body)).strip()
    return icons, text
bad = 0
for a, b in (('PhoneAlert1', 'PhoneAlert2'), ('WatchAlert1', 'WatchAlert2')):
    ia, ta = parts(a); ib, tb = parts(b)
    if not ia or not ib or ia == ib: print('  %s and %s do not differ by icon' % (a, b)); bad = 1
    if ta == tb: print('  %s and %s do not differ by text' % (a, b)); bad = 1
sys.exit(bad)
PY
check "TC-D-036 alert levels 1 and 2 differ by text and icon" $?

# TC-D-037 the texts describe the aurora style, keep the proposal wording and the undecided tile action
bad=0
for f in docs/design/phone-settings-prompts-alerts.md docs/design/watch-tile-alerts.md docs/design/README.md; do
  grep -qi 'aurora' "$f" || { echo "  $f does not describe the aurora style"; bad=1; }
done
grep -rqiE 'warm-coloured|stronger colour' docs/design/*.md && { echo "  old color wording is left"; bad=1; }
grep -qi 'proposed design' docs/design/README.md || bad=1
grep -qi 'undecided' docs/design/watch-tile-alerts.md || bad=1
[ "$bad" -eq 0 ]; check "TC-D-037 section texts say aurora, keep 'proposed design' and the undecided tile action, no old color wording" $?

# TC-D-038 the old palette is gone from the sources
bad=0
for hex in F3F6F9 0B6E78 FFE3B8 B24A0C 3A2A08 5CC8D2 0B1218; do
  grep -rqi "#$hex" "$src"/ui.css "$src"/*.dc.html && { echo "  legacy color #$hex is still in the sources"; bad=1; }
done
[ "$bad" -eq 0 ]; check "TC-D-038 the legacy palette is gone from ui.css and the boards" $?
exit $fail
