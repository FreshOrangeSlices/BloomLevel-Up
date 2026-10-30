"""Throwaway: the dump's rows for the lair structures (their biomes and dimension), and Monster Expansion's structure files.
Usage: lairinfo.py <shots dir> <server.zip> <out dir>"""
import glob, io, json, os, sys, zipfile
shots, server_zip, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)), None)
rows = []
if dump:
    for line in open(dump):
        if '"kind": "structure"' in line or '"kind":"structure"' in line:
            rows.append(json.loads(line))
json.dump(rows, open(os.path.join(out, "structures.json"), "w"), indent=1)
server = zipfile.ZipFile(server_zip)
found = {}
for name in server.namelist():
    if name.startswith("mods/") and "monsterexpansion" in name:
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        for n in jar.namelist():
            if "/worldgen/" in n or "/tags/worldgen/" in n:
                found[n] = jar.read(n).decode("utf-8", "replace")
json.dump(found, open(os.path.join(out, "monsterexpansion_worldgen.json"), "w"), indent=1)
print(f"{len(rows)} structure rows from {dump}; {len(found)} Monster Expansion worldgen files")
