#!/usr/bin/env python3
"""Make a round watch image fully transparent outside its circle, without touching the artwork inside.

Usage: clear-outside-circle.py <image.png> [...]   (8-bit RGBA PNG, standard library only)
Pixels farther from the center than the image radius plus 1.5 px get alpha 0 (the soft edge of the circle is kept).
Generated artwork can leave faint stray pixels in the corners; the design test (TC-D-033) wants clean corners.
"""
import math
import struct
import sys
import zlib


def read(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, ihdr, extra = 8, b'', None, []
    while pos < len(data):
        n, t = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if t == b'IHDR':
            ihdr = struct.unpack('>IIBBBBB', body)
        elif t == b'IDAT':
            idat += body
        elif t != b'IEND':
            extra.append((t, body))
    w, h, depth, ctype, _, _, interlace = ihdr
    assert depth == 8 and ctype == 6 and interlace == 0, 'needs an 8-bit RGBA PNG that is not interlaced'
    stride, bpp = w * 4, 4
    raw, rows, prev = zlib.decompress(idat), [], bytearray(w * 4)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    return w, h, rows, extra


def write(path, w, h, rows, extra):
    def chunk(t, b):
        return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
    out = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    for t, b in extra:
        out += chunk(t, b)
    out += chunk(b'IDAT', zlib.compress(b''.join(b'\x00' + bytes(r) for r in rows), 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(out)


def clear(path):
    w, h, rows, extra = read(path)
    cx, cy, limit = (w - 1) / 2, (h - 1) / 2, w / 2 + 1.5
    cleared = 0
    for y in range(h):
        for x in range(w):
            if rows[y][x * 4 + 3] and math.hypot(x - cx, y - cy) > limit:
                rows[y][x * 4 + 3] = 0
                cleared += 1
    write(path, w, h, rows, extra)
    print('%s: %d pixels outside the circle made transparent' % (path, cleared))


if __name__ == '__main__':
    for p in sys.argv[1:]:
        clear(p)
