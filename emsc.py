"""Throwaway: Easy Mob Spawn Control's rule classes (their fields are its JSON keys) and its structure spawner,
disassembled. Usage: emsc.py <server.zip> <out file>"""
import io, os, re, subprocess, sys, tempfile, zipfile
server = zipfile.ZipFile(sys.argv[1])
jar = next(zipfile.ZipFile(io.BytesIO(server.read(n))) for n in server.namelist() if n.endswith(".jar") and "easy_mob_spawn_control" in n)
out = []
with tempfile.TemporaryDirectory() as tmp:
    for entry in sorted(jar.namelist()):
        if not entry.endswith(".class") or "/gui/" in entry or "/client/" in entry:
            continue
        data = jar.read(entry)
        inner = "$" in entry and re.search(r"(Rule|Spawn|Cap|Entry|Assignment|Data)[^/]*\.class$", entry)
        spawner = b"MAX_SPAWNS_PER_STRUCTURE_PER_TICK" in data or b"structure_spawn_cap" in data or b"StructureSpawn" in data
        if inner or spawner:
            path = os.path.join(tmp, entry)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "wb").write(data)
            flags = ["-p", "-constants"] if inner else ["-c", "-p", "-constants"]
            text = subprocess.run(["javap", *flags, path], capture_output=True, text=True).stdout
            out.append(f"## javap {entry}\n{text[:30000]}")
open(sys.argv[2], "w").write("\n".join(out))
print(len(out), "classes")
