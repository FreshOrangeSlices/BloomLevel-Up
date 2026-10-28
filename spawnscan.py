"""Throwaway: every natural spawn the pack's mods add (biome modifiers, structure spawn overrides), the biome tags
they use, and the spawn-related strings in each mod's code and default configs (config options like "only in
mountains"). From the LegionWareX server zip. Usage: spawnscan.py <server.zip> <out dir>"""
import io
import json
import os
import re
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
out = sys.argv[2]
os.makedirs(out, exist_ok=True)

modifiers, structures, tags, strings, configs = [], [], {}, {}, {}
STRING = re.compile(rb"[\x20-\x7e]{5,160}")
SPAWNISH = re.compile(rb"spawn|biome|mountain|peak|rarity|weight", re.I)


def load(data):
    try:
        return json.loads(data.decode("utf-8", "replace"))
    except Exception:
        return None


def scan(jar_name, jar):
    found = set()
    for entry in jar.namelist():
        if entry.startswith("META-INF/jarjar/") and entry.endswith(".jar"):
            try:
                scan(jar_name + "!" + entry.rsplit("/", 1)[-1], zipfile.ZipFile(io.BytesIO(jar.read(entry))))
            except Exception:
                pass
            continue
        if entry.endswith(".json") and entry.startswith("data/"):
            data = jar.read(entry)
            if "/tags/worldgen/biome/" in entry:
                parsed = load(data)
                if parsed is not None:
                    tags.setdefault(entry, []).append({"jar": jar_name, "tag": parsed})
            elif b'"spawners"' in data or b"add_spawns" in data or "biome_modifier" in entry:
                modifiers.append({"jar": jar_name, "path": entry, "json": load(data)})
            elif "/worldgen/structure/" in entry and b"spawn_overrides" in data:
                parsed = load(data) or {}
                overrides = parsed.get("spawn_overrides") or {}
                if any((v or {}).get("spawns") for v in overrides.values()):
                    structures.append({"jar": jar_name, "path": entry, "spawn_overrides": overrides,
                                       "biomes": parsed.get("biomes")})
        elif entry.endswith(".toml") or entry.endswith(".json5") or (entry.endswith(".json") and entry.startswith("config")):
            data = jar.read(entry)
            if SPAWNISH.search(data):
                configs[jar_name + "!" + entry] = data.decode("utf-8", "replace")[:6000]
        elif entry.endswith(".class"):
            for s in STRING.findall(jar.read(entry)):
                if SPAWNISH.search(s) and not s.startswith(b"(") and b"/" not in s[:1] and not s.startswith(b"L"):
                    found.add(s.decode())
    if found:
        strings.setdefault(jar_name, [])
        strings[jar_name] = sorted(set(strings[jar_name]) | found)[:600]


for name in sorted(server.namelist()):
    if name.endswith(".jar") and "/mods/" in "/" + name:
        scan(name.rsplit("/", 1)[-1], zipfile.ZipFile(io.BytesIO(server.read(name))))
    elif name.startswith("config/") or "/config/" in name or "defaultconfigs/" in name:
        data = server.read(name)
        if SPAWNISH.search(data):
            configs["pack!" + name] = data.decode("utf-8", "replace")[:6000]

for file, data in (("modifiers.json", modifiers), ("structures.json", structures), ("tags.json", tags),
                   ("strings.json", strings), ("configs.json", configs)):
    json.dump(data, open(os.path.join(out, file), "w"), indent=1)

lines = []
for m in modifiers:
    j = m["json"] or {}
    spawners = j.get("spawners")
    if isinstance(spawners, dict):
        spawners = [spawners]
    who = ", ".join(f"{s.get('type')} w{s.get('weight')} {s.get('minCount')}-{s.get('maxCount')}" for s in spawners or [] if isinstance(s, dict))
    lines.append(f"{m['jar']} | {m['path']} | {j.get('type')} | {json.dumps(j.get('biomes'))[:120]} | {who[:300]}")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(lines) + "\n")
print(len(modifiers), "biome modifiers,", len(structures), "structures with spawn overrides,", len(tags), "biome tags,",
      len(strings), "jars with spawn strings,", len(configs), "configs")
