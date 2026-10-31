"""Throwaway: which mod jars (and classes) contain a string. Usage: findstr.py <server.zip> <out dir> <string>..."""
import io, os, sys, zipfile
server_zip, out = sys.argv[1:3]
needles = [s.encode() for s in sys.argv[3:]]
os.makedirs(out, exist_ok=True)
lines = []
server = zipfile.ZipFile(server_zip)
def scan(label, data, depth=0):
    try:
        jar = zipfile.ZipFile(io.BytesIO(data))
    except Exception:
        return
    for n in jar.namelist():
        if n.endswith(".jar") and depth < 2:
            scan(f"{label}!{n}", jar.read(n), depth + 1)
        elif n.endswith((".class", ".json", ".toml")):
            b = jar.read(n)
            for s in needles:
                if s in b:
                    lines.append(f"{label} :: {n} :: {s.decode()}")
for name in server.namelist():
    if name.startswith("mods/") and name.endswith(".jar"):
        scan(name, server.read(name))
open(os.path.join(out, "found.txt"), "w").write("\n".join(lines[:400]) + "\n")
print(len(lines), "hits")
