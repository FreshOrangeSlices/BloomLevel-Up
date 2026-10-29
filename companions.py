"""Throwaway: where Iron's Spells' Ice Spider spawns in the pack (the server's registry dump: every biome's spawn list,
every structure's own spawns), and the Ice Spider Den's biomes and spacing from the jars and the pack's data.
Usage: companions.py <server.zip> <out dir> (reads ./shots)."""
import io
import json
import os
import re
import sys
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
lines = []
dump = next((os.path.join(r, f) for r, d, fs in os.walk("shots") for f in fs if f == "bloom-dump.jsonl"), None)
for line in open(dump):
    row = json.loads(line)
    text = json.dumps(row)
    if "ice_spider" not in text:
        continue
    if row.get("kind") == "biome":
        for category, spawns in (row.get("spawns") or {}).items():
            for s in spawns:
                if "ice_spider" in s.get("type", ""):
                    total = sum(x.get("weight", 0) for x in spawns)
                    lines.append(f"biome {row['id']} {category}: weight {s['weight']} of {total}, {s.get('min')}-{s.get('max')}")
    else:
        lines.append(f"{row.get('kind')} {row.get('id')}: {text[:1500]}")
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if name.endswith(".jar") and "irons" in name.lower():
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        for entry in jar.namelist():
            if entry.endswith(".json") and ("ice_spider" in entry or "biome_modifier" in entry or "has_structure" in entry):
                body = jar.read(entry).decode("utf-8", "replace")
                if "ice_spider" in entry or "ice_spider" in body or "spider_den" in body:
                    lines.append(f"== {entry}\n{body[:1500]}")
    elif name.startswith("config/openloader") and ("spider" in name or "irons" in name):
        lines.append(f"== {name}\n{server.read(name).decode('utf-8', 'replace')[:800]}")
open(os.path.join(out, "ice-spider.txt"), "w").write("\n".join(lines))
print(len(lines), "lines")
