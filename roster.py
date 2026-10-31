"""Throwaway: Born in Chaos's and Dungeon Denizens' mobs for a spawn roster: each mob's dump row (category, spawn rules,
daylight, despawning, health, attack, classes), their own biome modifiers, what the pack's biomes spawn now, and
DD's config. Usage: roster.py <server.zip> <shots dir> <out dir>"""
import glob, io, json, os, re, sys, zipfile
server_zip, shots, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
server = zipfile.ZipFile(server_zip)
report = []
NS = ("born_in_chaos_v1:", "ddenizens:", "gmm:")
dump = next(iter(glob.glob(os.path.join(shots, "**", "bloom-dump.jsonl"), recursive=True)), None)
rows = [json.loads(l) for l in open(dump)] if dump else []
report.append("# entities")
for r in rows:
    if r.get("kind") == "entity" and r["id"].startswith(NS):
        r = dict(r); r.pop("tags", None)
        report.append(json.dumps(r))
report.append("# biome modifiers in the jars")
for name in server.namelist():
    if name.startswith("mods/") and re.search(r"born_in_chaos|ddenizens|monster-manual", name):
        jar = zipfile.ZipFile(io.BytesIO(server.read(name)))
        for n in sorted(jar.namelist()):
            if "biome_modifier" in n and n.endswith(".json"):
                report.append(f"## {n}: " + re.sub(r"\s+", " ", jar.read(n).decode("utf-8", "replace")))
            elif re.search(r"(config|spawn)[^/]*\.(json|toml)$", n) and not n.startswith("assets/"):
                report.append(f"## file {n}: " + re.sub(r"\s+", " ", jar.read(n).decode("utf-8", "replace"))[:1500])
report.append("# generated configs of Dungeon Denizens / Monster Manual / Born in Chaos")
for path in glob.glob(os.path.join(shots, "**", "*"), recursive=True):
    if os.path.isfile(path) and "config" in path and re.search(r"ddenizens|gmm|monster|born_?in_?chaos|denizen", path, re.I):
        report.append(f"## {os.path.relpath(path, shots)}\n" + open(path, encoding="utf-8", errors="replace").read()[:6000])
report.append("# monster spawns of a few biomes now")
for r in rows:
    if r.get("kind") == "biome" and r["id"] in ("minecraft:plains", "minecraft:forest", "minecraft:dark_forest", "minecraft:desert", "minecraft:snowy_plains", "minecraft:swamp", "minecraft:taiga", "biomesoplenty:redwood_forest"):
        report.append(r["id"] + ": " + json.dumps(r["spawns"].get("monster")))
open(os.path.join(out, "summary.txt"), "w").write("\n".join(report) + "\n")
print(len(report), "lines")
