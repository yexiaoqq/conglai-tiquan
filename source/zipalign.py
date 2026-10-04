#!/usr/bin/env python3
# Minimal pure-python zipalign: keeps per-entry compress method, aligns each
# entry's data offset to ALIGN bytes using a TLV padding extra field.
# Usage: python3 zipalign.py <in.apk> <out.apk> [align]
import sys, zlib, struct, zipfile

src = sys.argv[1]
dst = sys.argv[2]
ALIGN = int(sys.argv[3]) if len(sys.argv) > 3 else 4

z = zipfile.ZipFile(src, 'r')
infos = z.infolist()

out = open(dst, 'wb')
cur = 0
central = []

def align_extra(base):
    pad = (ALIGN - (base % ALIGN)) % ALIGN
    if pad == 0:
        return b''
    if pad < 4:
        pad += ALIGN
    return struct.pack('<HH', 0xFFFF, pad - 4) + b'\x00' * (pad - 4)

for zi in infos:
    name = zi.filename.encode('utf-8')
    raw = z.read(zi.filename)
    method = zi.compress_type
    if method == zipfile.ZIP_STORED:
        cdata = raw
    else:
        co = zlib.compressobj(9, zlib.DEFLATED, -15)
        cdata = co.compress(raw) + co.flush()
        if len(cdata) >= len(raw):
            cdata = raw
            method = zipfile.ZIP_STORED
    crc = zlib.crc32(raw) & 0xffffffff
    usize = len(raw) & 0xffffffff
    csize = len(cdata) & 0xffffffff
    flag = zi.flag_bits & ~0x0008
    t = zi.date_time
    dosdate = (((t[0] - 1980) & 0x7f) << 9) | ((t[1] & 0xf) << 5) | (t[2] & 0x1f)
    dostime = ((t[3] & 0x1f) << 11) | ((t[4] & 0x3f) << 5) | ((t[5] // 2) & 0x1f)
    base = cur + 30 + len(name)
    extra = align_extra(base)
    extralen = len(extra)
    data_off = base + extralen
    off = cur
    lh = struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, flag, method,
                     dostime, dosdate, crc, csize, usize, len(name), extralen)
    out.write(lh); out.write(name); out.write(extra); out.write(cdata)
    cur = data_off + csize
    central.append((name, flag, method, dostime, dosdate, crc, csize, usize,
                    zi.external_attr, off))

cd_off = cur
for (name, flag, method, dostime, dosdate, crc, csize, usize, extattr, off) in central:
    ch = struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, flag, method,
                     dostime, dosdate, crc, csize, usize, len(name), 0, 0, 0, 0,
                     extattr, off)
    out.write(ch); out.write(name)
    cur += len(ch) + len(name)
cd_size = cur - cd_off
out.write(struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(central), len(central),
                      cd_size, cd_off, 0))
out.close()
print('zipalign done ->', dst, 'entries=', len(central), 'align=', ALIGN)
