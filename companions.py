"""Throwaway: Book of Dragons 1.4 (Forge 1.20.1): is it on Modrinth, what it depends on, and what its data looks like
next to 1.31's (roost structures, biome tags, spawns, configs), for updating the pack. Usage: companions.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

out = sys.argv[2]
os.makedirs(out, exist_ok=True)
lines = []
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FreshOrangeSlices/BloomLevel-Up pack check"})
    return urllib.request.urlopen(req, timeout=120).read()
try:
    versions = json.loads(get("https://api.modrinth.com/v2/project/fJee0CPJ/version"))
    for v in versions[:8]:
        lines.append(f"MODRINTH {v['version_number']} {v['date_published'][:10]} {v['game_versions']} {v['loaders']} id={v['id']} "
                     f"files={[f['filename'] for f in v['files']]} deps={[(d.get('project_id'), d.get('version_id'), d.get('dependency_type')) for d in v['dependencies']]}")
except Exception as error:
    lines.append(f"MODRINTH failed: {error}")
new = None
for url in ["https://mediafilez.forgecdn.net/files/9339/901/bookofdragons-1.4-1.20.1.jar", "https://edge.forgecdn.net/files/9339/901/bookofdragons-1.4-1.20.1.jar"]:
    try:
        new = get(url)
        lines.append(f"CF download ok {url} {len(new)} bytes")
        break
    except Exception as error:
        lines.append(f"CF download failed {url}: {error}")
old = None
server = zipfile.ZipFile(sys.argv[1])
for name in server.namelist():
    if "bookofdragons" in name.lower() and name.endswith(".jar"):
        old = server.read(name)
        lines.append(f"OLD {name} {len(old)} bytes")
def describe(label, data):
    jar = zipfile.ZipFile(io.BytesIO(data))
    names = jar.namelist()
    toml = jar.read("META-INF/mods.toml").decode("utf-8", "replace") if "META-INF/mods.toml" in names else ""
    lines.append(f"== {label} mods.toml")
    lines.extend(l for l in toml.splitlines() if re.search(r"modId|versionRange|mandatory|version=|side", l) and not l.strip().startswith("#"))
    lines.append(f"== {label} jarjar: " + ", ".join(n for n in names if n.startswith("META-INF/jarjar/")))
    data_files = sorted(n for n in names if n.startswith("data/") and n.endswith(".json"))
    lines.append(f"== {label} data ({len(data_files)} files)")
    for n in data_files:
        if re.search(r"structure_set|tags/worldgen|biome_modifier|/spawn|roost|territor|config", n):
            body = jar.read(n).decode("utf-8", "replace")
            lines.append(f"{label} {n} :: {re.sub(r'\s+', ' ', body)[:400]}")
    lines.append(f"== {label} other data dirs: " + ", ".join(sorted(set("/".join(n.split("/")[:3]) for n in data_files))))
    lines.append(f"== {label} assets sounds/lang: " + ", ".join(n for n in names if n.endswith("en_us.json")))
    return jar
if old:
    describe("OLD", old)
if new:
    open(os.path.join(out, "bookofdragons-1.4.jar.bin"), "wb").write(new)
    jar = describe("NEW", new)
    lang = next((n for n in jar.namelist() if n.endswith("en_us.json") and "bookofdragons" in n), None)
    if lang:
        data = json.loads(jar.read(lang).decode("utf-8", "replace"))
        lines.append("== NEW entities and items: " + ", ".join(sorted(k for k in data if k.startswith("entity.") or k.startswith("item.bookofdragons"))[:300]))
open(os.path.join(out, "bookofdragons.txt"), "w").write("\n".join(lines))
print(len(lines), "lines")
