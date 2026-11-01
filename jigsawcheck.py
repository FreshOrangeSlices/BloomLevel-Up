"""Throwaway: which structure pieces have jigsaw blocks pointing at an empty pool ("" or "minecraft:"), the cause of
"Empty or non-existent pool: minecraft:". Usage: jigsawcheck.py <server.zip> <shots dir> <out dir>"""
import collections, gzip, io, os, struct, sys, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)


def read_nbt(buf):
    pos = [0]
    def u(fmt, n):
        v = struct.unpack(">" + fmt, buf[pos[0]:pos[0] + n])[0]; pos[0] += n; return v
    def s():
        n = u("H", 2); v = buf[pos[0]:pos[0] + n].decode("utf-8", "replace"); pos[0] += n; return v
    def payload(t):
        if t == 1: return u("b", 1)
        if t == 2: return u("h", 2)
        if t == 3: return u("i", 4)
        if t == 4: return u("q", 8)
        if t == 5: return u("f", 4)
        if t == 6: return u("d", 8)
        if t == 7: n = u("i", 4); pos[0] += n; return None
        if t == 8: return s()
        if t == 9:
            et = u("b", 1); n = u("i", 4); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u("b", 1)
                if tt == 0: return d
                k = s(); d[k] = payload(tt)
        if t == 11: n = u("i", 4); pos[0] += 4 * n; return None
        if t == 12: n = u("i", 4); pos[0] += 8 * n; return None
    t = u("b", 1); s(); return payload(t)


hits = collections.defaultdict(list)
scanned = 0
for n in server.namelist():
    if not (n.startswith("mods/") and n.endswith(".jar")):
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(n)))
    for e in jar.namelist():
        if not (e.endswith(".nbt") and "/structure" in e):
            continue
        try:
            data = jar.read(e)
            tag = read_nbt(gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data)
        except Exception:
            continue
        scanned += 1
        for block in tag.get("blocks", []) or []:
            nbt = block.get("nbt") or {}
            if nbt.get("id") in ("minecraft:jigsaw", "jigsaw") or "pool" in nbt:
                pool = nbt.get("pool")
                if pool in ("", "minecraft:"):
                    hits[os.path.basename(n)].append(e)
                    break
report = [f"scanned {scanned} structure pieces"]
for jar, pieces in sorted(hits.items(), key=lambda kv: -len(kv[1])):
    report.append(f"## {jar}: {len(pieces)} pieces")
    report += ["  " + p for p in pieces[:25]]
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report))
