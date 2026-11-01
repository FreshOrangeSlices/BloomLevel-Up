"""Throwaway: the entities saved in the Champion's Graves templates: are they marked PersistenceRequired?
Usage: persistcheck.py <server.zip> <shots dir> <out dir>"""
import gzip, io, os, struct, sys, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []


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
        if t == 7: n = u("i", 4); pos[0] += n; return "<bytes>"
        if t == 8: return s()
        if t == 9:
            et = u("b", 1); n = u("i", 4); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u("b", 1)
                if tt == 0: return d
                k = s(); d[k] = payload(tt)
        if t == 11: n = u("i", 4); pos[0] += 4 * n; return "<ints>"
        if t == 12: n = u("i", 4); pos[0] += 8 * n; return "<longs>"
    t = u("b", 1); s(); return payload(t)


for n in server.namelist():
    if n.startswith("mods/") and "soulslike" in n.lower():
        jar = zipfile.ZipFile(io.BytesIO(server.read(n)))
        for e in jar.namelist():
            if e.endswith(".nbt") and "champions_graves" in e:
                data = jar.read(e)
                tag = read_nbt(gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data)
                for ent in tag.get("entities", []):
                    nbt = ent.get("nbt", {})
                    report.append(f"{e}: {nbt.get('id')} PersistenceRequired={nbt.get('PersistenceRequired')} keys={sorted(nbt.keys())[:40]}")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) or "no entities")
