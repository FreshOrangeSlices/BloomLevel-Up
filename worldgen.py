"""Throwaway: Monster Expansion's worldgen files (its boss lairs) and every mod's structure sets (how far apart
structures are). Usage: worldgen.py <server.zip> <out file>"""
import io, json, sys, zipfile
server = zipfile.ZipFile(sys.argv[1])
out = {}
for name in sorted(server.namelist()):
    if not name.endswith(".jar") or "/mods/" not in "/" + name:
        continue
    jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
    for entry in jar.namelist():
        if not entry.endswith(".json"):
            continue
        if entry.startswith("data/monsterexpansion/worldgen/") or "/worldgen/structure_set/" in entry:
            try:
                out[entry] = json.loads(jar.read(entry).decode("utf-8", "replace"))
            except ValueError:
                out[entry] = "unparsable"
json.dump(out, open(sys.argv[2], "w"), indent=1)
print(len(out), "files")
