"""Throwaway: three looks at the pack at once.
1. Zoni's Companions' recipes that use a diamond block.
2. Every mod structure's biomes and every structure set's placement (how far apart), from the jars.
3. Each big mob (boss bar, or 150+ health) in the boss mods: does its code check mobGriefing (breaks blocks), do its
   classes or animations name an intro (cutscene), and which structures place it (from their NBT templates).
Usage: survey.py <server.zip> <bloom-dump.jsonl> <out dir>"""
import gzip
import io
import json
import os
import re
import sys
import zipfile

server = zipfile.ZipFile(sys.argv[1])
rows = [json.loads(line) for line in open(sys.argv[2]) if line.strip()]
out = sys.argv[3]
os.makedirs(out, exist_ok=True)
entities = {r["id"]: r for r in rows if r.get("kind") == "entity"}
big = {i: r for i, r in entities.items() if not i.startswith("minecraft:") and r.get("category") in ("monster", "creature")
       and (r.get("bossBar") or (r.get("health") or 0) >= 150)}

jars = {}
for name in server.namelist():
    if name.endswith(".jar") and "/mods/" in "/" + name:
        jars[name.rsplit("/", 1)[-1]] = zipfile.ZipFile(io.BytesIO(server.read(name)))

recipes, structures, sets, placed = {}, {}, {}, {}
INTRO = re.compile(rb"intro|awaken|cutscene|cinematic|wake_?up|spawn_?anim|summon_?anim|emerge|roar_start|activate", re.I)
for jar_name, jar in jars.items():
    for entry in jar.namelist():
        if not entry.startswith("data/"):
            continue
        parts = entry.split("/")
        if "zonis" in jar_name.lower() and "/recipes/" in entry and entry.endswith(".json"):
            text = jar.read(entry).decode("utf-8", "replace")
            if "diamond_block" in text or "summon" in entry:
                recipes[entry] = json.loads(text)
        if entry.endswith(".json") and len(parts) >= 5 and parts[2] == "worldgen" and parts[3] in ("structure", "structure_set"):
            ident = f"{parts[1]}:{'/'.join(parts[4:])[:-5]}"
            try:
                data = json.loads(jar.read(entry).decode("utf-8", "replace"))
            except ValueError:
                continue
            if parts[3] == "structure":
                structures[ident] = {"biomes": data.get("biomes"), "type": data.get("type"), "step": data.get("step")}
            else:
                sets[ident] = {"placement": data.get("placement"), "structures": [s.get("structure") for s in data.get("structures", [])]}
        if entry.endswith(".nbt") and "/structures/" in entry:
            raw = jar.read(entry)
            try:
                raw = gzip.decompress(raw)
            except OSError:
                pass
            for ident in set(m.decode() for m in re.findall(rb"[a-z0-9_]+:[a-z0-9_/]+", raw)):
                if ident in big:
                    placed.setdefault(ident, []).append(f"{parts[1]}:{'/'.join(parts[3:])[:-4]}")

bosses = {}
for ident, row in sorted(big.items()):
    ns, path = ident.split(":", 1)
    stems = [c for c in row.get("classes", [])[:1]]
    stem = (stems[0] if stems else "").replace("Entity", "")
    griefing, intro, files = False, set(), []
    for jar in jars.values():
        names = jar.namelist()
        if not any(n.startswith(f"assets/{ns}/") or n.startswith(f"data/{ns}/") for n in names[:4000]):
            continue
        for entry in names:
            low = entry.lower()
            is_class = entry.endswith(".class") and stem and stem.lower() in low.rsplit("/", 1)[-1].lower()
            is_anim = entry.startswith(f"assets/{ns}/animations/") and path.split("/")[-1] in low
            if not (is_class or is_anim):
                continue
            data = jar.read(entry)
            files.append(entry.rsplit("/", 1)[-1])
            if is_class and b"f_46132_" in data:
                griefing = True
            intro |= {m.decode().lower() for m in INTRO.findall(data)}
    bosses[ident] = {"health": row.get("health"), "bossBar": row.get("bossBar"), "despawns": row.get("despawns"),
                     "category": row.get("category"), "breaksBlocks": griefing, "intro": sorted(intro),
                     "structures": sorted(set(placed.get(ident, []))), "files": len(files)}

strings = {}
for jar_name, jar in jars.items():
    if "structurify" in jar_name.lower():
        found = set()
        for entry in jar.namelist():
            if entry.endswith(".class") or entry.endswith(".json"):
                found |= {m.decode() for m in re.findall(rb"[a-z_]*(?:spacing|separation|overlap|flatness|biome_check|modifier)[a-z_]*", jar.read(entry))}
        strings[jar_name] = sorted(found)

for name, data in (("zonis_recipes.json", recipes), ("structures.json", structures), ("structure_sets.json", sets),
                   ("bosses.json", bosses), ("structurify_strings.json", strings)):
    json.dump(data, open(os.path.join(out, name), "w"), indent=1)
print(len(recipes), "Zoni recipes,", len(structures), "structures,", len(sets), "structure sets,", len(bosses), "big mobs")
