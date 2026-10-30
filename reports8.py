"""Throwaway: the leftover candidates: which jar recipes (any type) mention them, whether the pack takes those out,
and the dump's row for each. Usage: reports8.py <server.zip> <shots dir> <out dir>"""
import io, json, os, re, sys, zipfile
server = zipfile.ZipFile(sys.argv[1]); SHOTS = sys.argv[2]; OUT = sys.argv[3]; os.makedirs(OUT, exist_ok=True)
ids = ["cataclysm:enderite_ingot", "cataclysm:void_crystal", "cataclysm:void_shard", "legendary_monsters:void_gem",
       "skarrier_mobs:ascendant_ingot", "skarrier_mobs:contagium_ingot", "skarrier_mobs:decayerite_ingot",
       "skarrier_mobs:eldritch_plate", "skarrier_mobs:pristinium_ingot", "skarrier_mobs:raw_krill",
       "waystones:attuned_shard", "waystones:crumbling_attuned_shard", "waystones:warp_plate"]
removed = {n.split("/data/", 1)[1] for n in server.namelist() if "openloader/data/" in n and "/recipes/" in n}
lines = []
hits = {i: [] for i in ids}
loot = {i: [] for i in ids}
for n in server.namelist():
    if not (n.endswith(".jar") and "/mods/" in "/" + n):
        continue
    z = zipfile.ZipFile(io.BytesIO(server.read(n)))
    for e in z.namelist():
        if e.startswith("data/") and e.endswith(".json") and ("/recipes/" in e or "/loot_tables/" in e):
            t = z.read(e).decode("utf-8", "replace")
            for i in ids:
                if f'"{i}"' in t:
                    rel = e[len("data/"):]
                    if "/recipes/" in e:
                        typ = re.search(r'"type"\s*:\s*"([^"]+)"', t)
                        hits[i].append(f"{rel} [{typ.group(1) if typ else '?'}]{' REMOVED BY PACK' if rel in removed else ''}")
                    else:
                        loot[i].append(rel)
rows = {}
dump = os.path.join(SHOTS, "server", "bloom-dump.jsonl")
if os.path.exists(dump):
    for l in open(dump):
        try:
            d = json.loads(l)
        except ValueError:
            continue
        if d.get("kind") == "item" and d.get("id") in ids:
            rows[d["id"]] = {k: d.get(k) for k in ("name", "use", "stack", "classes")}
for i in ids:
    lines.append(f"{i} {rows.get(i)}\n   recipes: {hits[i]}\n   loot: {loot[i][:8]}")
open(os.path.join(OUT, "summary.txt"), "w").write("\n".join(lines)); print("\n".join(lines))
