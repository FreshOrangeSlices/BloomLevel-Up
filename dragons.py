"""Throwaway: how Book of Dragons' dragons get into the world: its worldgen files and biome tags, and its roost
classes disassembled (how many dragons a roost holds, whether it brings them back). Usage: dragons.py <server.zip> <out>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
jar = next(zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.endswith(".jar") and "bookofdragons" in n.lower())
out = []
for entry in sorted(jar.namelist()):
    if entry.startswith("data/") and entry.endswith(".json") and ("worldgen" in entry or "/tags/worldgen/" in entry or "biome_modifier" in entry):
        out.append(f"## json {entry}\n{jar.read(entry).decode('utf-8', 'replace')[:2500]}")
with tempfile.TemporaryDirectory() as tmp:
    for entry in sorted(jar.namelist()):
        if entry.endswith(".class") and re.search(r"[Rr]oost|[Ss]pawn", entry) and "/client/" not in entry:
            path = os.path.join(tmp, entry)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "wb").write(jar.read(entry))
            text = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
            out.append(f"## javap {entry}\n{text[:25000]}")
open(sys.argv[2], "w").write("\n".join(out))
print(len(out), "sections")
